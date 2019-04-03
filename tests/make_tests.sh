#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit
set -e

# this allows you to build a single test or build them all. It will create the test build
# directories wherever this script file is run from. A seperate directory is created for each
# test object so they can each have independent danfig files for them.

DSE_DIR=../
DANALYZER_DIR=`realpath ${DSE_DIR}danalyzer`
DANHELPER_DIR=`realpath ${DSE_DIR}danhelper`

if [[ ! -f $DANHELPER_DIR/libdanhelper.so ]]; then
  DANHELPER_DIR="${DANHELPER_DIR}/build/src"
fi

DANHELPER_FILE=libdanhelper.so
if [[ "`uname`" == "Darwin" ]]; then
  DANHELPER_FILE=libdanhelper.dylib
fi

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
      # (need to use $path instead of $class/.. because the latter will not keep path structure of lib within jar file)
      path="edu/vanderbilt/isis/uninstrumented_return"
      LIBFILE="LibReturnObject"
      if [ ! -d ${LIBPATH} ]; then
        mkdir -p ${LIBPATH}
      fi
      echo "Building ${LIBFILE} lib file for ${test}..."
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
  echo "Building ${test}..."
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "javac -g ${libs} ${class}/${test}.java"
    echo "jar cvf results/${test}/${test}.jar ${class}/*.class ${class}/${test}.java"
  else
    javac -g ${libs} ${class}/${test}.java
    jar cvf results/${test}/${test}.jar ${class}/*.class ${class}/${test}.java
    if [[ ! -f results/${test}/${test}.jar ]]; then
      echo "FAILURE: instrumented file not produced!"
      FAILURE=1
    fi
  fi
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
  
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "Stripping debug info from jar file"
    echo "pack200 -r -G ${test}-strip.jar ${test}.jar"
    echo
    echo "Instrumenting jar file"
    echo "java -cp $DANALYZER_DIR/lib/asm-tree-7.2.jar:$DANALYZER_DIR/lib/asm-7.2.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/dist/danalyzer.jar danalyzer.instrumenter.Instrumenter ${test}-strip.jar"
    echo
    echo "Rename instrumented file"
    echo "mv ${test}-strip-dan-ed.jar ${test}-dan-ed.jar"
    echo
  else
    # strip debug info from jar file
    pack200 -r -G ${test}-strip.jar ${test}.jar
    if [[ ! -f ${test}-strip.jar ]]; then
      echo "FAILURE: stripped file not produced!"
      FAILURE=1
      return
    fi

    # instrument jar file
    echo "Instrumenting jar file"
    java -cp $DANALYZER_DIR/lib/asm-tree-7.2.jar:$DANALYZER_DIR/lib/asm-7.2.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/dist/danalyzer.jar danalyzer.instrumenter.Instrumenter ${test}-strip.jar

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
      echo "Skipping test ${test} on macOS..."
      return
    fi
  fi
  
  # setup the classpath for the test
  CLASSPATH=${test}-dan-ed.jar:$DANALYZER_DIR/dist/danalyzer.jar
  if [[ -d lib ]]; then
    CLASSPATH=${CLASSPATH}:lib/*
  fi

  # now run the test in background mode in case verification process needs to issue message to it
  echo "Running instrumented jar file (in background)"
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib:/usr/local/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${class}/${test}"
  else
    # use a pipe to handle redirecting stdin to the application, since it runs as background process
    if [ ! -p inpipe ]; then
      mkfifo inpipe
    fi
    tail -f inpipe | java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib:/usr/local/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${class}/${test} &
    pid=$!

    # delay just a bit to make sure app is running before starting checker
    sleep 2

    # run the script to check correctness
    # (NOTE: a failure in this call will perform an exit, which will terminate the script)
    echo "Checking test results"
    ./check_result.sh ${pid}

    # kill the tail process
    pkill tail > /dev/null 2>&1

    # delete the pipe we created
    if [ -p inpipe ]; then
      rm -f inpipe
    fi
  fi
}

function clear_database
{
  echo "Clearing the database"
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'"
    echo
  else
    # clear the database
    mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
  fi
}

# determine if the test is valid to run.
# the jar file for the test was found in order to get here. now we make sure that a danfig
# and check_results.sh file are also found with the source code. If not, we skip the test.
function check_if_viable
{
  VALID=1
  MISSING=""
  
  # check for the danfig file
  if [[ ! -f ${class}/danfig ]]; then
    MISSING="${MISSING} DANFIG"
    VALID=0
  fi

  # check for the correctness checking script
  if [[ ! -f ${class}/check_result.sh ]]; then
    MISSING="${MISSING} CHECK_RESULT.SH"
    VALID=0
  fi

  if [[ ${TESTMODE} -ne 0 ]]; then
    if [[ ${VALID} -eq 0 ]]; then
      echo "SKIPPING ${test}: MISSING: ${MISSING}"
    fi
    return
  fi
  
  # if files present, copy them to build directory
  if [[ ${VALID} -eq 1 ]]; then
    # make the directory for the selected test (if already exists, delete it first)
    builddir="results/${test}"
    if [[ -d ${builddir} ]]; then
      rm -Rf ${builddir}
    fi
    mkdir -p ${builddir}
  
    cp ${class}/danfig ${builddir}
    cat base_check.sh ${class}/check_result.sh > ${builddir}/check_result.sh
    chmod +x ${builddir}/check_result.sh
  else
    echo "SKIPPING ${test}: MISSING: ${MISSING}"
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

# NOTE: 'file' is assumed to be defined on entry as the name of the source (java) file in the
#       edu subdirectory structure. It is also assumed that there is only 1 java file for each
#       test (excluding libraries) and they are all unique names. This base name (excluding path
#       and '.java' extension) will be used as the 'test' name.
#
# this checks if the required source files are present, and if so:
# 1. builds the jar file with full debugging info (for extracting info with danlauncher)
# 2. create a results/<testname> directory to perform all builds in
# 3. creates a debug-stripped jar from the original and instruments this jar file
#
# if the run option specified (ie. 'RUNTEST' is set to 1), then also do:
#    4. clear the database of solutions
#    5. run the instrumented jar file as background process
#    6. verify that expected solution (parameter name and value) are found in the solution set.
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

  # verify the source code, danfig, and check_results.sh script files are all present.
  check_if_viable
  if [[ ${VALID} -eq 1 ]]; then
    echo "Building test '${test}' from path '${class}'"
    # create the jar file from the source code (includes full debug info)
    build_test
    # these commands must be executed from the build directory of the specified test
    cd results/${test}
    # create instrumented jar (from debug-stripped version of jar)
    instrument_test
    if [[ ${RUNTEST} -eq 1 ]]; then
      # clear the database before we run
      clear_database
      # run instrumented jar and and verify results
      run_test
      # clear the database for next test
      #clear_database
    fi

    COUNT_TOTAL=`expr ${COUNT_TOTAL} + 1`
  fi
}

#========================================= START HERE ============================================
# read options
FAILURE=0
TESTMODE=0
RUNTEST=0
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
