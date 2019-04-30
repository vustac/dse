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

# this checks if the required source files are present, and if so:
# 1. clear the database of solutions
# 2. run the instrumented jar file as background process
# 3. verify that expected solution (parameter name and value) are found in the solution set.
#
function build_chain
{
  builddir="results/${test}"

  # make sure we have a directory for the specified test
  if [[ ! -d ${builddir} ]]; then
    echo "FAILURE: results directory ${test} not found!"
    exit 1
  fi

  # now get the mainclass definition file (exit if not found)
  if [[ ! -f "${builddir}/mainclass.txt" ]]; then
    echo "FAILURE: mainclass.txt file not found in results directory!"
    exit 1
  fi

  MAINCLASS=`cat ${builddir}/mainclass.txt`

  # check for the optional JSON config file
  # (required if we are going to perform a check of the test results)
  runargs=""
  RUNCHECK=0
  jsonfile="${builddir}/testcfg.json"
  if [[ -f ${jsonfile} ]]; then
    # get the args from the JSON config file (if it exists)
    runargs=`cat ${jsonfile} | jq -r '.runargs'`
    if [[ ${VERIFY} -eq 1 ]]; then
      RUNCHECK=1
    fi
  fi

  cd ${builddir}

  # if instrumented file is not found, create it
  if [ ! -f ${test}-dan-ed.jar ]; then
    instrument_test
  fi
  
  # clear the database before we run
  clear_database

  # run instrumented jar and and verify results
  run_test
}

#========================================= START HERE ============================================
# read options
FAILURE=0
TESTMODE=0
VERIFY=0
COMMAND=()
while [[ $# -gt 0 ]]; do
  key="$1"
  case ${key} in
    -t|--test)
      TESTMODE=1
      shift
      ;;
    -v|--verify)
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

if [[ ${ALLTESTS} -eq 1 ]]; then
  echo "FAILURE: Must specify a test to run"
  exit 0
fi
test=${COMMAND}

build_chain
