#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit
set -o pipefail
set -e

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
    echo "java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib:/usr/local/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${class}/${test} ${runargs}"
  else
    # if the danfig or check_results.sh script files are missing, skip the verification
    check_if_viable
    if [[ ${VERIFY} -eq 0 || ${VALID} -eq 0 ]]; then
      java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib:/usr/local/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${class}/${test} ${runargs}
      return
    fi
    
    # use a pipe to handle redirecting stdin to the application, since it runs as background process
    if [ ! -p inpipe ]; then
      mkfifo inpipe
    fi
    tail -f inpipe | java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib:/usr/local/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${class}/${test} ${runargs} &
    pid=$!

    # delay just a bit to make sure app is running before starting checker
    sleep 2

    # run the script to check correctness
    # (NOTE: a failure in this call will perform an exit, which will terminate the script)
    echo "Checking test results"
    ./check_result.sh ${pid}

    # delete the pipe we created
    if [ -p inpipe ]; then
      rm -f inpipe > /dev/null 2>&1
    fi

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
  if [[ ! -f danfig ]]; then
    MISSING="${MISSING} danfig"
    VALID=0
  fi

  # check for the correctness checking script
  if [[ ! -f check_result.sh ]]; then
    MISSING="${MISSING} check_result.sh"
    VALID=0
  fi

  if [[ ${VALID} -eq 0 ]]; then
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

if [[ ${ALLTESTS} -ne 0 ]]; then
  echo "FAILURE: Must specify a test to run"
  exit 0
fi

# find the source file for the specified test and get the corresponding Main Class for it
file=`find edu -name "${COMMAND}.java"`
if [[ ${file} == "" ]]; then
  echo "FAILURE: test not found for: ${COMMAND}"
  exit 0
fi
extract_test ${file}

# get the run command args from the JSON config file (if it exists)
runargs=""
jsonfile="${class}/testcfg.json"
if [[ -f ${jsonfile} ]]; then
  runargs=`cat ${jsonfile} | jq -r '.runargs'`
fi

# all tests are kept in a sub-folder of the current location called "results"
if [[ ! -d "results/${COMMAND}" ]]; then
  echo "FAILURE: results directory ${COMMAND} not found!"
  exit 0
fi
cd results/${COMMAND}

# if instrumented file is not found, create it
if [ ! -f ${test}-dan-ed.jar ]; then
  instrument_test
fi
  
# clear the database before we run
clear_database
# run instrumented jar and and verify results
run_test
