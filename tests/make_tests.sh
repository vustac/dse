#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit
set -o pipefail
set -e

# this allows you to build a single test or build them all. It will create the test build
# directories wherever this script file is run from. A seperate directory is created for each
# test object so they can each have independent danfig files for them.

DSE_DIR=../
DANALYZER_DIR=`realpath ${DSE_DIR}danalyzer`
DANHELPER_DIR=`realpath ${DSE_DIR}danhelper`
DANPATCH_DIR=`realpath ${DSE_DIR}danpatch/build`

if [[ ! -f $DANHELPER_DIR/libdanhelper.so ]]; then
  DANHELPER_DIR="${DANHELPER_DIR}/build/src"
fi

DANHELPER_FILE=libdanhelper.so
if [[ "`uname`" == "Darwin" ]]; then
  DANHELPER_FILE=libdanhelper.dylib
fi

function classpath_init
{
  CLASSPATH=$1
}

function classpath_add
{
  CLASSPATH=${CLASSPATH}:$1
}

# create the instrumented jar file
# NOTE: this is performed from the subdirectory created for the specified test within the "results" dir.
function instrument_test
{
  if [[ ${FAILURE} -ne 0 ]]; then
    return
  fi

  # exit if uninstrumented file not found
  if [ ! -f ${test}.jar ]; then
    echo "Test jar file '${test}.jar' not found!"
    FAILURE=1
    return
  fi
  
  # strip debug info from jar file
  echo "==> Stripping debug info from jar file"
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "pack200 -r -G ${test}-strip.jar ${test}.jar"
    echo
  else
    pack200 -r -G ${test}-strip.jar ${test}.jar

    if [[ ! -f ${test}-strip.jar ]]; then
      echo "FAILURE: stripped file not produced!"
      FAILURE=1
      return
    fi
  fi

  # setup the classpath for instrumenting the test
  classpath_init $DANALYZER_DIR/lib/asm-tree-7.2.jar
  classpath_add $DANALYZER_DIR/lib/asm-7.2.jar
  classpath_add $DANALYZER_DIR/lib/com.microsoft.z3.jar
  classpath_add $DANALYZER_DIR/lib/commons-io-2.5.jar
  classpath_add $DANALYZER_DIR/dist/danalyzer.jar

  # instrument jar file
  echo "==> Building instrumented jar file for '${test}'"
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "java -cp ${CLASSPATH} danalyzer.instrumenter.Instrumenter ${test}-strip.jar"
    echo
  else
    java -cp ${CLASSPATH} danalyzer.instrumenter.Instrumenter ${test}-strip.jar

    if [[ -f ${test}-strip-dan-ed.jar ]]; then
      mv ${test}-strip-dan-ed.jar ${test}-dan-ed.jar
    else
      echo "FAILURE: instrumented file not produced: ${test}-dan-ed.jar"
      FAILURE=1
    fi
  fi
}

# run the instrumented jar file
# NOTE: this is performed from the subdirectory created for the specified test within the "results" dir.
function run_test
{
  if [[ ${FAILURE} -ne 0 ]]; then
    return
  fi

  # some tests don't run on macOS yet - just ignore these for now...
  if [[ "`uname`" == "Darwin" ]]; then
    # (these are the tests that require sending command to STDIN of application)
    if [[ "${test}" == "SimpleCWE129" || "${test}" == "SimpleCWE606" ]]; then
      echo "==> Skipping test ${test} on macOS..."
      return
    fi
  fi
  
  # setup the library path for running the test
  LIBPATH=$JAVA_HOME/bin:/usr/lib:/usr/local/lib:${DANPATCH_DIR}
    
  # setup the boot classpath for running the test
  classpath_init /a
  classpath_add $DANALYZER_DIR/dist/danalyzer.jar
  classpath_add $DANALYZER_DIR/lib/com.microsoft.z3.jar
  classpath_add $DANALYZER_DIR/lib/guava-27.1-jre.jar
  BOOTCPATH=${CLASSPATH}

  # setup the classpath for running the test
  classpath_init ${test}-dan-ed.jar
  classpath_add $DANALYZER_DIR/dist/danalyzer.jar
  if [[ -d lib ]]; then
    classpath_add lib/*
  fi

  # if verification requested & the danfig or check_results.sh script files are missing, skip the verification
  if [[ ${VERIFY} -eq 1 ]]; then
    if [[ ! -f danfig ]]; then
      echo "==> SKIPPING verify of ${test}: MISSING: danfig"
      VERIFY=0
    elif [[ ! -f check_result.sh ]]; then
      echo "==> SKIPPING verify of ${test}: MISSING: check_result.sh"
      VERIFY=0
    fi
  fi

  # now run the test
  if [[ ${TESTMODE} -eq 1 ]]; then
    echo "==> Running instrumented jar file"
    echo "java -Xverify:none -Dsun.boot.library.path=${LIBPATH} -Xbootclasspath${BOOTCPATH} -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${MAINCLASS} ${runargs}"
    return
  fi
  
  # no verification - run test in foreground and wait for it to finish
  if [[ ${VERIFY} -eq 0 ]]; then
    echo "==> Running instrumented jar file"
    java -Xverify:none -Dsun.boot.library.path=${LIBPATH} -Xbootclasspath${BOOTCPATH} -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${MAINCLASS} ${runargs}
    return
  fi
    
  # else run the test in background mode so verification process can issue message to it
  # use a pipe to handle redirecting stdin to the application, since it runs as background process
  echo "==> Running instrumented jar file (in background)"
  if [ ! -p inpipe ]; then
    mkfifo inpipe
  fi
  tail -f inpipe | java -Xverify:none -Dsun.boot.library.path=${LIBPATH} -Xbootclasspath${BOOTCPATH} -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${MAINCLASS} ${runargs} &
  pid=$!

  # delay just a bit to make sure app is running before starting checker
  sleep 2

  # run the script to check correctness
  verify_test

  # delete the pipe we created
  if [ -p inpipe ]; then
    rm -f inpipe > /dev/null 2>&1
  fi

  # kill the tail process
  pkill tail > /dev/null 2>&1
}

function verify_test
{
  if [[ ${VERIFY} -eq 1 && ${TESTMODE} -eq 0 ]]; then
    # (NOTE: a failure in this call will perform an exit, which will terminate the script)
    echo "==> Checking test results"
    ./check_result.sh ${pid}
  fi
}

function clear_database
{
  echo "==> Clearing the database"
  if [[ ${TESTMODE} -eq 1 ]]; then
    echo "mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'"
    echo
  else
    # clear the database
    mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
  fi
}

# create the test script from the test config file
function create_test_script
{
  # exit if in test mode
  if [[ ${TESTMODE} -eq 1 ]]; then
    return
  fi
  
  echo "==> Creating test script"
  java -cp ${DSE_DIR}GenerateTestScript/dist/GenerateTestScript.jar:${DSE_DIR}GenerateTestScript/lib/gson-2.8.1.jar main.GenerateTestScript ${class}/testcfg.json ${builddir}/test_script.sh > /dev/null 2>&1
  cat base_check.sh ${builddir}/test_script.sh > ${builddir}/check_result.sh
  chmod +x ${builddir}/check_result.sh
}

function create_danfig
{
  # exit if in test mode
  if [[ ${TESTMODE} -eq 1 ]]; then
    return
  fi
  
  echo "==> Creating danfig file"
  echo "#! DANALYZER SYMBOLIC EXPRESSION LIST" > ${builddir}/danfig
  echo "#" >> ${builddir}/danfig
  echo "# DEBUG SETUP" >> ${builddir}/danfig
  echo "DebugFlags: " >> ${builddir}/danfig
  echo "DebugMode: TCPPORT" >> ${builddir}/danfig
  echo "DebugPort: 5000" >> ${builddir}/danfig
  echo "IPAddress: localhost" >> ${builddir}/danfig
  echo "#" >> ${builddir}/danfig
  echo "# SOLVER INTERFACE" >> ${builddir}/danfig
  echo "SolverPort: 4000" >> ${builddir}/danfig
  echo "SolverAddress: localhost" >> ${builddir}/danfig
  echo "SolverMethod: NONE" >> ${builddir}/danfig
  echo "#" >> ${builddir}/danfig
  echo "# SYMBOLIC_PARAMETERS" >> ${builddir}/danfig
  count=`cat ${jsonfile} | jq -r '.symbolicList' | jq length`
  if [ ${count} -eq 0 ]; then
    echo "# <none defined>" >> ${builddir}/danfig
  else
    for ((index=0; index < ${count}; index++)) do
      local name=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].name'`
      local meth=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].method'`
      local type=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].type'`
      local slot=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].slot'`
      local strt=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].start'`
      local end=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].end'`
      echo "Symbolic: ${name} ${meth} ${slot} ${strt} ${end} ${type}" >> ${builddir}/danfig
    done
  fi
}

# build the jar file with debug info enabled (so danlauncher can access local parameters)
function build_test
{
  libs=""

  # handle special cases (such as if a library file or other files are necessary)
  LIBPATH="results/${test}/lib"
  case ${test} in
    SimpleNano)
      ;&   # fall through...
    SimpleServer)
      if [ ! -d ${LIBPATH} ]; then
        mkdir -p ${LIBPATH}
      fi
      cp lib/nanohttpd-2.2.0.jar ${LIBPATH}
      libs="-cp .:lib/nanohttpd-2.2.0.jar"
      ;;
    RefReturnArray)
      ;&   # fall through...
    RefReturnMultiArray)
      ;&   # fall through...
    RefReturnDouble)
      # build the library (uninstrumented code) jar file
      # (need to use $path instead of $class/.. because the latter will not keep path structure
      # of lib within jar file)
      path="edu/vanderbilt/isis/uninstrumented_return"
      LIBFILE="LibReturnObject"
      if [ ! -d ${LIBPATH} ]; then
        mkdir -p ${LIBPATH}
      fi
      echo "==> Building ${LIBFILE} lib file for ${test}..."
      if [[ ${TESTMODE} -ne 0 ]]; then
        echo "javac ${path}/lib/${LIBFILE}.java"
        echo "jar cvf ${LIBPATH}/${LIBFILE}.jar ${path}/lib/${LIBFILE}.class ${path}/lib/${LIBFILE}.java"
      else
        javac ${path}/lib/${LIBFILE}.java
        jar cvf ${LIBPATH}/${LIBFILE}.jar ${path}/lib/${LIBFILE}.class ${path}/lib/${LIBFILE}.java
      fi
      libs="-cp .:lib/LibArrayObject.jar"
      ;;
    *)
      ;;
  esac

  # now build the file(s) and jar them up (include source and class files in jar)
  echo "==> Building initial un-instrumented '${test}'"
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "javac -g ${libs} ${MAINCLASS}.java"
    echo "jar cvf results/${test}/${test}.jar ${class}/*.class ${MAINCLASS}.java"
  else
    javac -g ${libs} ${MAINCLASS}.java
    jar cvf results/${test}/${test}.jar ${class}/*.class ${MAINCLASS}.java
    if [[ ! -f results/${test}/${test}.jar ]]; then
      echo "FAILURE: instrumented file not produced!"
      FAILURE=1
    fi
  fi
}

# this takes the full pathname of the source file and converts it into a 'test' name (the name
# of the source file without the path or the ".java" extension) and a 'class' name (the path).
function extract_test
{
  cmd=$1
  # if running in loop, each file entry will begin with './', which we must eliminate
  if [[ ${cmd} == "./"* ]]; then
    cmd=${cmd:2}
  fi
  if [[ ${cmd} == "" ]]; then
    echo "FAILURE: File '${cmd}' not found!"
    exit 1
  fi
  # extract the java filename only & get its length
  test=`echo ${cmd} | sed 's#.*/##g'`
  local namelen=${#test}
  if [[ ${namelen} -eq 0 ]]; then
    echo "FAILURE: extraction of filename failed!"
    exit 1
  fi
  # now remove the filename from the path
  local pathlen=`echo "${#cmd} - ${namelen} -1" | bc`
  class=${cmd:0:${pathlen}}
  # remove ".java" from the filename
  namelen=`echo "${namelen} - 5" | bc`
  test=${test:0:${namelen}}
}

# this checks if the required source files are present, and if so:
# 1. builds the jar file with full debugging info (for extracting info with danlauncher)
# 2. create a results/<testname> directory to perform all builds in
# 3. creates a debug-stripped jar from the original and instruments this jar file
#
# if the run option specified (ie. 'RUNTEST' is set to 1), then also do:
#
# 4. clear the database of solutions
# 5. run the instrumented jar file as background process
# 6. verify that expected solution (parameter name and value) are found in the solution set.
#
# NOTE: 'file' is assumed to be defined on entry as the name of the source (java) file in the
#       edu subdirectory structure. It is also assumed that there is only 1 java file for each
#       test (excluding libraries) and they are all unique names. This base name (excluding path
#       and '.java' extension) will be used as the 'test' name.
#
function build_chain
{
  # these commands must be executed from the directory this script is in
  cd ${BASEDIR}
  extract_test ${file}
  if [[ ${test} == "LibReturnObject" ]]; then
    # skip this, it is just a lib file used by other tests
    return
  fi

  # make the directory for the selected test (if already exists, delete it first)
  builddir="results/${test}"
  if [[ ${TESTMODE} -eq 0 ]]; then
    if [[ -d ${builddir} ]]; then
      rm -Rf ${builddir}
    fi
    mkdir -p ${builddir}
  fi
  
  # create a mainclass.txt file that contains the main class for the test (for the run_tests.sh)
  MAINCLASS=${class}/${test}
  echo "${MAINCLASS}" > ${builddir}/mainclass.txt
  
  # check for the required JSON config file
  # (required if we are going to perform a check of the test results)
  runargs=""
  RUNCHECK=0
  jsonfile="${builddir}/testcfg.json"
  if [[ -f "${class}/testcfg.json" ]]; then
    # copy the file to the build directory
    cp ${class}/testcfg.json ${jsonfile}
    # get the args from the JSON config file (if it exists)
    runargs=`cat ${jsonfile} | jq -r '.runargs'`
    if [[ ${VERIFY} -eq 1 ]]; then
      RUNCHECK=1
    fi
  fi

  # if we are running the test and we have a valid JSON file deined for it,
  # create the danfig and check_results.sh script files.
  if [[ ${RUNTEST} -eq 1 && ${RUNCHECK} -eq 1 ]]; then
    # create the danfig and test script files from the test config file
    create_danfig
    create_test_script
  fi
  
  # create the jar file from the source code (includes full debug info)
  build_test

  # the rest of the commands must be executed from the build directory of the specified test
  cd ${builddir}

  # create instrumented jar (from debug-stripped version of jar)
  instrument_test

  # don't run test unless we have the test script to verify it
  if [[ ${RUNTEST} -eq 0 || ${RUNCHECK} -eq 0 ]]; then
    if [[ ${RUNTEST} -eq 1 ]]; then
      echo "SKIPPING ${test}: MISSING: testcfg.json"
    fi
    return
  fi
  
  # clear the database before we run
  clear_database

  # run instrumented jar and and verify results
  run_test

  # count number of tests run
  COUNT_TOTAL=`expr ${COUNT_TOTAL} + 1`
}

#========================================= START HERE ============================================
# read options
FAILURE=0
TESTMODE=0
RUNTEST=0
VERIFY=0
COMMAND=()
while [[ $# -gt 0 ]]; do
  key="$1"
  case ${key} in
    -t|--test)
      TESTMODE=1
      shift
      ;;
    -r|--run)
      RUNTEST=1
      VERIFY=1
      shift
      ;;
    *)
      COMMAND+=("$1")
      shift
      ;;
  esac
done

# check if a specific test was mentioned
ALLTESTS=0
set +o nounset
if [ -z ${COMMAND} ]; then
  ALLTESTS=1
fi
set -o nounset

BASEDIR=`pwd`

# all tests will be kept in a sub-folder of the current location called "results"
if [[ ! -d "results" ]]; then
  mkdir -p results
fi

COUNT_TOTAL=0

# make sure the generate tool has been built
if [[ ! -f ${DSE_DIR}GenerateTestScript/dist/GenerateTestScript.jar ]]; then
  cd ../GenerateTestScript
  ant
  cd ${BASEDIR}
fi

# this is assumed to be running from the tests directory
if [ ${ALLTESTS} -eq 0 ]; then
  # if a single test is requested, just make that one
  file=`find edu -name "${COMMAND}.java"`
  if [[ ${file} == "" ]]; then
    echo "test not found for: ${COMMAND}"
    exit 0
  fi
  build_chain
else
  # else, we are going to generate them all...
  # copy the source and library files from the dse tests project
  echo "Building all tests..."

  # search dse test folders recursively for source files to build
  testlist=`find . -name "*.java" | sort`
  for file in ${testlist}; do
    build_chain
    echo "------------------------------------------"

    # exit on any failures
    if [[ ${FAILURE} -ne 0 ]]; then
      echo "${COUNT_TOTAL} tests completed: 1 failure"
      exit 1
    fi
  done
  echo "All ${COUNT_TOTAL} tests completed: no failures"
fi
