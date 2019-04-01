#!/bin/bash

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
    echo "Checking test results"
    ./check_result.sh ${pid}

    # kill the tail process
    pkill tail > /dev/null 2>&1
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
    cp ${class}/check_result.sh ${builddir}
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
  namelen=${#test}
  if [[ ${namelen} -eq 0 ]]; then
    echo "FAILURE: extraction of filename failed!"
    exit 1
  fi
  # now remove the filename from the path
  pathlen=`echo "${#cmd} - ${namelen} -1" | bc`
  class=${cmd:0:${pathlen}}
  # remove ".java" from the filename
  namelen=`echo "${namelen} - 5" | bc`
  test=${test:0:${namelen}}
}

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
if [ -z ${COMMAND} ]; then
  ALLTESTS=1
fi

CURDIR=`pwd`

# these options help catch errors. (NOTE: nounset must be set after testing for ${COMMAND})
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit
set -e

# all tests will be kept in a sub-folder of the current location called "results"
if [[ ! -d "results" ]]; then
  mkdir -p results
fi

# this is assumed to be running from the tests directory
if [ ${ALLTESTS} -eq 0 ]; then
  # if a single test is requested, just make that one
  file=`find edu -name "${COMMAND}.java"`
  if [[ ${file} == "" ]]; then
    echo "test not found for: ${COMMAND}"
    exit 0
  fi
  extract_test ${file}
  echo "Building single test '${test}' from path '${class}'"
  check_if_viable
  if [[ ${VALID} -eq 1 ]]; then
    build_test
    if [[ ${RUNTEST} -eq 1 ]]; then
      cd results/${test}
      # create instrumented jar
      instrument_test
      # clear the database before we run
      clear_database
      # run instrumented jar and and verify results
      run_test
      cd ${CURDIR}
    fi
  fi
else
  # else, we are going to generate them all...
  # copy the source and library files from the dse tests project
  echo "Building all tests..."

  # search dse test folders recursively for source files to build
  testlist=`find . -name "*.java" | sort`
  for file in ${testlist}; do
    extract_test ${file}
    if [[ ${test} == "LibReturnObject" ]]; then
      # skip this, it is just a lib file used by other tests
      continue
    fi
    check_if_viable
    if [[ ${VALID} -eq 1 ]]; then
      build_test
      if [[ ${RUNTEST} -eq 1 ]]; then
        # create instrumented jar and run jar file from the test build dir
        cd results/${test}
        # create instrumented jar
        instrument_test
        # clear the database before we run
        clear_database
        # run instrumented jar and and verify results
        run_test
        cd ${CURDIR}
      fi
      # exit loop on any failures
      if [[ ${FAILURE} -ne 0 ]]; then
        exit 1
      fi
      echo "------------------------------------------"
    fi
  done
fi
