#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit
set -o pipefail
set -e

# set the java home path variable
OSTYPE=`uname`
if [[ "${OSTYPE}" == "Linux" ]]; then
  export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
else
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home
fi

# this allows you to build a single test or build them all. It will create the test build
# directories wherever this script file is run from. A seperate directory is created for each
# test object so they can each have independent danfig files for them.

# this generates a clean path to the directory the script is contained in.
# we do this in order to locate the DSE tools that are identified by the script's location.
#
# NOTE: This assumes that you have not transported this script file to some other location
#       unless you move the entire dse project directory along with it.
#
SCRIPTPATH=${0%/*}
if [[ "${SCRIPTPATH}" == "$0" ]]; then
  SCRIPTPATH=`pwd`
fi
SCRIPTPATH=`realpath ${SCRIPTPATH}`
#echo "script path = ${SCRIPTPATH}"

# first, find location of the root directory of all the DSE sub-projects
DSE_SRC_DIR=`realpath ${SCRIPTPATH}/../src`
DSE_TST_DIR=`realpath ${SCRIPTPATH}`

DANALYZER_DIR=${DSE_SRC_DIR}/danalyzer
DANHELPER_DIR=${DSE_SRC_DIR}/danhelper
DANPATCH_DIR=${DSE_SRC_DIR}/danpatch

# determine the correct name for the danhelper lib file (Linux uses .so, Mac uses .dylib)
DANHELPER_FILE=libdanhelper.so
if [[ "`uname`" == "Darwin" ]]; then
  DANHELPER_FILE=libdanhelper.dylib
fi

# now we need to know if the danhelper lib file is in the appropriate build subdir.
# if not, move it there (it gets built in the src subdir).
if [[ ! -f $DANHELPER_DIR/build/${DANHELPER_FILE} ]]; then
  cd ${DANHELPER_DIR}
  mkdir -p build
  cmake .
  make
  if [[ -f $DANHELPER_DIR/${DANHELPER_FILE} ]]; then
    mv $DANHELPER_DIR/${DANHELPER_FILE} ${DANHELPER_DIR}/build/
  elif [[ -f $DANHELPER_DIR/src/${DANHELPER_FILE} ]]; then
    mv $DANHELPER_DIR/src/${DANHELPER_FILE} ${DANHELPER_DIR}/build/
  else
    echo "ERROR: danhelper library was not created!"
    exit 1
  fi
  cd ${DSE_TST_DIR}
fi

# these active ingredients in these paths are assumed to be in their associated build directories
DANHELPER_DIR=${DANHELPER_DIR}/build
DANPATCH_DIR=${DANPATCH_DIR}/build

function classpath_init
{
  CLASSPATH=$1
}

function classpath_add
{
  CLASSPATH=${CLASSPATH}:$1
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

function verify_test
{
  if [[ ${VERIFY} -eq 1 && ${TESTMODE} -eq 0 ]]; then
    # (NOTE: a failure in this call will perform an exit, which will terminate the script)
    echo "==> Checking test results"
    ./check_result.sh ${pid}
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
  classpath_init ${instrfile}
  classpath_add $DANALYZER_DIR/dist/danalyzer.jar
  if [[ -d lib ]]; then
    classpath_add lib/*
  fi

  # if verification requested & the danfig or check_results.sh script files are missing, skip the verification
  if [[ ${VERIFY} -eq 1 ]]; then
    if [[ ! -f check_result.sh ]]; then
      echo "==> SKIPPING verify of ${test}: MISSING: check_result.sh"
      VERIFY=0
    fi
  fi

  # the run command to use
  local runcmd="java -Xverify:none -Dsun.boot.library.path=${LIBPATH} -Xbootclasspath${BOOTCPATH} -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${MAINCLASS} ${RUNARGS}"

  # now run the test
  if [[ ${TESTMODE} -eq 1 ]]; then
    echo "==> Running instrumented jar file"
    echo "${runcmd}"
    return
  fi
  
  # no verification - run test in foreground and wait for it to finish
  if [[ ${VERIFY} -eq 0 ]]; then
    # these tests require user input to complete, so warn the user
    if [[ "${test}" == "SimpleCWE129" || "${test}" == "SimpleCWE6069" ]]; then
      echo
      echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      echo "!!! This test will hang until user directs input to application's standard input"
      echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      echo
    elif [[ "${test}" == "SimpleNano" || "${test}" == "SimpleServer" ]]; then
      echo
      echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      echo "!!! This test will hang until user directs input to localhost:8080"
      echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
      echo
    fi
    
    echo "==> Running instrumented jar file"
    ${runcmd}
    return
  fi
    
  # else run the test in background mode so verification process can issue message to it
  # use a pipe to handle redirecting stdin to the application, since it runs as background process
  echo "==> Running instrumented jar file (in background)"
  if [ ! -p inpipe ]; then
    mkfifo inpipe
  fi
  tail -f inpipe | ${runcmd} &
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

# runs the test specified by: ${test}
#
function run_chain
{
  # this must start from the directory this script is in
  cd ${DSE_TST_DIR}

  echo "Runing tests: ${test}"

  # these specify where the build dir is in which all operations are performed
  # and the files that are required to have been built by make_test.sh.
  builddir="results/${test}"
  instrfile="${test}-dan-ed.jar"
  jsonfile="testcfg.json"

  # make sure we have a directory for the specified test
  if [[ ! -d ${builddir} ]]; then
    echo "FAILURE ${test}: results directory not found!"
    exit 1
  fi

  cd ${builddir}

  # if instrumented file is not found, exit
  if [ ! -f ${instrfile} ]; then
    echo "FAILURE ${test}: instrumented jar not found in results directory"
    exit 1
  fi

  # check for the required JSON config file
  if [[ ! -f ${jsonfile} ]]; then
    echo "FAILURE ${test}: ${jsonfile} file not found in results directory"
    exit 1
  fi

  # extract test info from JSON file
  RUNARGS=`cat ${jsonfile} | jq -r '.runargs'`
  MAINCLASS=`cat ${jsonfile} | jq -r '.mainclass'`

  set +o nounset
  if [[ ${RUNARGS} == null ]]; then
    RUNARGS=""
  fi
  if [[ ${MAINCLASS} == null ]]; then
    echo "FAILURE ${test}: Main Class value not defined in ${jsonfile}"
    exit 1
  fi
  set -o nounset

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
VERIFY=0
COUNT_TOTAL=0
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

# all tests will be kept in a sub-folder of the current location called "results"
if [[ ! -d "results" ]]; then
  echo "FAILURE: results directory not found"
  exit 1
fi

if [ ${ALLTESTS} -eq 0 ]; then
  # if a single test is requested, just make that one
  test=${COMMAND}
  run_chain
else
  # else, we are going to generate them all...
  # copy the source and library files from the dse tests project
  echo "Runing all tests..."
  cd ${DSE_TST_DIR}
  testlist=`ls results | sort`
  for test in ${testlist}; do
    run_chain
    echo "------------------------------------------"
  done
  echo "All ${COUNT_TOTAL} tests completed: no failures"
fi

