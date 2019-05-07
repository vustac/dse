#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit

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
DSE_TST_DIR=`realpath ${SCRIPTPATH}/../test`

# now define the common ones we need for execution
DANALYZER_DIR=${DSE_SRC_DIR}/danalyzer
DANHELPER_DIR=${DSE_SRC_DIR}/danhelper
DANPATCH_DIR=${DSE_SRC_DIR}/danpatch
DANTESTGEN_DIR=${DSE_SRC_DIR}/dantestgen

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
  cd ${BASEDIR}
fi

# these active ingredients in these paths are assumed to be in their associated build directories
DANHELPER_DIR=${DANHELPER_DIR}/build
DANPATCH_DIR=${DANPATCH_DIR}/build

# now let's introduce some functions to make life easier...

# these are used to initialize and add entries to a CLASSPATH field used for builds
function classpath_init
{
  CLASSPATH=$1
}

function classpath_add
{
  CLASSPATH=${CLASSPATH}:$1
}

# this handles command execution and simply echos the command if $TESTMODE is set.
# returns: $STATUS = 1 if command was executed, 0 if we are not actually running
#
function execute_command
{
  STATUS=0
  echo "${TITLE}"
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "${COMMAND}"
    echo
    return
  fi

  ${COMMAND}
  STATUS=1
}

# same as 'execute_command' except command is executed as a background process and a pipe
# is used to feed user input to the STDIN of the process
# returns: $STATUS = pid of process if command was executed, 0 if we are not actually running
#
function execute_command_in_background
{
  STATUS=0
  if [[ ${RUN_STDIN} -eq 1 ]]; then
    echo "${TITLE} (in background with pipe for STDIN)"
  else
    echo "${TITLE} (in background)"
  fi
  
  # check for test mode
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "${COMMAND} &"
    echo
    return
  fi

  # determine if we need to use pipe for re-directing STDIN to app
  if [[ ${RUN_STDIN} -eq 1 ]]; then
    if [ ! -p inpipe ]; then
      mkfifo inpipe
    fi
    tail -f inpipe | ${COMMAND} &
    STATUS=$!
  else
    ${COMMAND} &
    STATUS=$!
  fi
}

# clears the ongodb database.
# can be run from any directory
# no inputs or outputs
#
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

# runs the instrumented jar file
# run from the BUILD directory
#
# $TESTNAME = base name of application to run (not including 'dan-ed' or '.jar')
# $RUN_IN_BKGND = 1 if run application in background
#
function run_test
{
  # setup the library path for running the test
  LIBPATH=$JAVA_HOME/bin:/usr/lib:/usr/local/lib:${DANPATCH_DIR}
    
  # setup the boot classpath for running the test
  classpath_init /a
  classpath_add $DANALYZER_DIR/dist/danalyzer.jar
  classpath_add $DANALYZER_DIR/lib/com.microsoft.z3.jar
  classpath_add $DANALYZER_DIR/lib/guava-27.1-jre.jar
  BOOTCPATH=${CLASSPATH}

  # setup the classpath for running the test
  classpath_init ${TESTNAME}-dan-ed.jar
  classpath_add $DANALYZER_DIR/dist/danalyzer.jar
  if [[ -d lib ]]; then
    classpath_add lib/*
  fi

  # get the run command
  TITLE="==> Running instrumented jar file"
  COMMAND="java -Xverify:none -Dsun.boot.library.path=${LIBPATH} -Xbootclasspath${BOOTCPATH} -agentpath:$DANHELPER_DIR/$DANHELPER_FILE -cp ${CLASSPATH} ${MAINCLASS} ${RUNARGS}"

  # if the danfig and check_results.sh script are present, run the verification
  RUN_IN_BKGND=0
  if [[ -f danfig && -f check_result.sh ]]; then
    RUN_IN_BKGND=1
  fi

  # now run the test
  # no verification - run test in foreground and wait for it to finish
  if [[ ${RUN_IN_BKGND} -eq 0 ]]; then
    execute_command
    return
  fi
  
  # else run the test in background mode so verification process can issue message to it
  execute_command_in_background
  pid=${STATUS}
  
  # delay just a bit to make sure app is running before starting checker
  sleep 2

  # run the script to check correctness
  # (NOTE: a failure in this call will perform an exit, which will terminate the script)
  if [[ ${TESTMODE} -eq 0 ]]; then
    echo "==> Checking test results"
    ./check_result.sh ${pid}
  fi

  # delete the pipe we created
  if [[ ${RUN_STDIN} -eq 1 ]]; then
    if [ -p inpipe ]; then
      rm -f inpipe > /dev/null 2>&1
    fi

    # kill the tail process
    pkill tail > /dev/null 2>&1
  fi
}

# create the instrumented jar file
# run from the BUILD directory
#
# $INPUTJAR = name of jar file to instrument
# $TESTNAME = base name of instrumented jar file to produce
#
function instrument_test
{
  # exit if uninstrumented file not found
  if [ ! -f ${INPUTJAR} ]; then
    echo "FAILURE: Test jar file '${INPUTJAR}' not found!"
    exit 1
  fi
  
  # strip debug info from jar file
  TITLE="==> Stripping debug info from jar file"
  COMMAND="pack200 -r -G ${TESTNAME}-strip.jar ${INPUTJAR}"
  execute_command

  if [[ ${STATUS} -eq 1 && ! -f ${TESTNAME}-strip.jar ]]; then
    echo "FAILURE: stripped file not produced!"
    exit 1
  fi

  # setup the classpath for instrumenting the test
  classpath_init $DANALYZER_DIR/lib/asm-tree-7.2.jar
  classpath_add $DANALYZER_DIR/lib/asm-7.2.jar
  classpath_add $DANALYZER_DIR/lib/com.microsoft.z3.jar
  classpath_add $DANALYZER_DIR/lib/commons-io-2.5.jar
  classpath_add $DANALYZER_DIR/dist/danalyzer.jar

  # set this to enable loop bounds testing (must be blank to skip)
  maximizeLoopBounds="1"

  # instrument jar file
  TITLE="==> Building instrumented jar file '${TESTNAME}'"
  COMMAND="java -cp ${CLASSPATH} danalyzer.instrumenter.Instrumenter ${TESTNAME}-strip.jar ${maximizeLoopBounds}"
  execute_command

  if [[ ${STATUS} -eq 0 ]]; then
    return
  fi

  if [[ -f ${TESTNAME}-strip-dan-ed.jar ]]; then
    mv ${TESTNAME}-strip-dan-ed.jar ${TESTNAME}-dan-ed.jar
  else
    echo "FAILURE: instrumented file not produced: ${TESTNAME}-dan-ed.jar"
    exit 1
  fi
}

# create the test script from the test config file
function create_test_script
{
  # indicate there are no commands to run
  RUN_STDIN=0
  
  # exit if in test mode
  if [[ ${TESTMODE} -eq 1 ]]; then
    return
  fi
  
  local count=`cat ${jsonfile} | jq -r '.commandlist' | jq length`
  if [ ${count} -eq 0 ]; then
    echo "==> Not creating test script (no commands defined)"
    return
  else
    # check to see if we need to issue STDIN commands to process (requires pipe to be used to issue it)
    for ((index=0; index < ${count}; index++)) do
      local name=`cat ${jsonfile} | jq -r '.commandlist['${index}'].command'`
      if [[ ${name} == "SEND_STDIN" ]]; then
        RUN_STDIN=1
        break
      fi
    done
  fi
  
  echo "==> Creating test script"
  java -cp ${DANTESTGEN_DIR}/dist/dantestgen.jar:${DANTESTGEN_DIR}/lib/gson-2.8.1.jar main.GenerateTestScript testcfg.json test_script.sh > /dev/null 2>&1
  cat ${DSE_TST_DIR}/base_check.sh test_script.sh > check_result.sh
  chmod +x check_result.sh
}

function create_danfig
{
  # exit if in test mode
  if [[ ${TESTMODE} -eq 1 ]]; then
    return
  fi
  
  danfigfile="danfig"
  echo "==> Creating danfig file"
  echo "#! DANALYZER SYMBOLIC EXPRESSION LIST" > ${danfigfile}
  echo "#" >> ${danfigfile}
  echo "# DEBUG SETUP" >> ${danfigfile}
  echo "DebugFlags: " >> ${danfigfile}
  echo "DebugMode: TCPPORT" >> ${danfigfile}
  echo "DebugPort: 5000" >> ${danfigfile}
  echo "IPAddress: localhost" >> ${danfigfile}
  echo "#" >> ${danfigfile}
  echo "# SOLVER INTERFACE" >> ${danfigfile}
  echo "SolverPort: 4000" >> ${danfigfile}
  echo "SolverAddress: localhost" >> ${danfigfile}
  echo "SolverMethod: NONE" >> ${danfigfile}
  echo "#" >> ${danfigfile}
  echo "# SYMBOLIC_PARAMETERS" >> ${danfigfile}

  local count=`cat ${jsonfile} | jq -r '.symbolicList' | jq length`
  if [ ${count} -eq 0 ]; then
    echo "# <none defined>" >> ${danfigfile}
  else
    for ((index=0; index < ${count}; index++)) do
      local name=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].name'`
      local meth=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].method'`
      local type=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].type'`
      local slot=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].slot'`
      local strt=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].start'`
      local end=`cat ${jsonfile} | jq -r '.symbolicList['${index}'].end'`
      echo "Symbolic: ${name} ${meth} ${slot} ${strt} ${end} ${type}" >> ${danfigfile}
    done
  fi
}

#========================================= START HERE ============================================
# read options
TESTMODE=0
RUNTEST=0
RUNARGS=""
command=()
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
      command+=("$1")
      shift
      ;;
  esac
done

# split the name of the selected jar file into a path and the base name
#test=basename ${command}
#builddir=dirname ${command}
INPUTJAR=${command##*/}
builddir=${command%/*}
if [[ "${builddir}" == "${command}" ]]; then
  builddir=""
fi
  
# make sure our build dir is valid
if [[ ${TESTMODE} -eq 0 && ! -f ${command} ]]; then
  echo "FAILURE: can't find file: ${command}"
  exit 1
fi
  
# these commands must be executed from the build directory
if [[ "${builddir}" != "" ]]; then
  cd ${builddir}
fi

# extract info from JSON config file
jsonfile="testcfg.json"

if [[ ! -f "${jsonfile}" ]]; then
  echo "FAILURE: JSON file not found: ${jsonfile}"
  exit 1
fi

# get the main class and running args from the JSON config file
TESTNAME=`cat ${jsonfile} | jq -r '.testname'`
RUNARGS=`cat ${jsonfile} | jq -r '.runargs'`
MAINCLASS=`cat ${jsonfile} | jq -r '.mainclass'`

set +o nounset
if [[ ${MAINCLASS} == null ]]; then
  echo "FAILURE: MainClass not defined in JSON file"
  exit 1
fi
if [[ ${TESTNAME} == null ]]; then
  TESTNAME=${INPUTJAR}
fi
set -o nounset

# create the check_results.sh script file (if commands found in json file)
create_test_script

# create a danfig file to run the test with
create_danfig
  
# create instrumented jar (from debug-stripped version of jar)
instrument_test

# exit if we are not running test
if [[ ${RUNTEST} -eq 0 ]]; then
  exit 0
fi
  
# clear the database before we run
clear_database

# run instrumented jar and and verify results
run_test
