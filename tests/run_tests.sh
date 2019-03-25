#!/bin/bash

DSE_DIR=../
DANALYZER_DIR=`realpath ${DSE_DIR}danalyzer`
DANHELPER_DIR=`realpath ${DSE_DIR}danhelper`

if [[ ! -f $DANHELPER_DIR/libdanhelper.so ]]; then
  DANHELPER_DIR="${DANHELPER_DIR}/build/src"
fi

# this takes the full pathname of the source file and converts it into a 'test' name (the name
# of the source file without the path or the ".java" extension) and a 'class' name (the path).
function extract_test
{
  cmd=$1
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

#class="edu/vanderbilt/isis/arrays/maximize_test"
#test="SymbolicMaximizeTest"

# read options
FAILURE=0
TESTMODE=0
COMMAND=()
while [[ $# -gt 0 ]]; do
  key="$1"
  case ${key} in
    -t|--test)
      TESTMODE=1
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

# these options help catch errors. (NOTE: nounset must be set after testing for ${COMMAND})
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit
set -o pipefail
set -e

if [[ ${ALLTESTS} -ne 0 ]]; then
  echo "FAILURE: Must specify a test to run"
  exit 0
fi

# all tests will be kept in a sub-folder of the current location called "results"
if [[ ! -d "results/${COMMAND}" ]]; then
  echo "FAILURE: results directory ${COMMAND} not found!"
  exit 0
fi

file=`find edu -name "${COMMAND}.java"`
if [[ ${file} == "" ]]; then
  echo "FAILURE: test not found for: ${COMMAND}"
  exit 0
fi
extract_test ${file}
echo "Running test '${test}'"

cd results/${COMMAND}

#echo "class: ${class}"
#echo "test: ${test}"

CLASSPATH=${test}-dan-ed.jar:$DANALYZER_DIR/dist/danalyzer.jar
if [[ -d lib ]]; then
  CLASSPATH=${CLASSPATH}:lib/*
fi

if [[ ${TESTMODE} -ne 0 ]]; then
  echo "strip debug info from jar file:"
  echo "pack200 -r -G ${test}-strip.jar ${test}.jar"
  echo
  echo "instrument jar file:"
  echo "java -cp $DANALYZER_DIR/lib/asm-tree-7.2.jar:$DANALYZER_DIR/lib/asm-7.2.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/dist/danalyzer.jar danalyzer.instrumenter.Instrumenter ${test}-strip.jar"
  echo
  echo "rename instrumented file:"
  echo "mv ${test}-strip-dan-ed.jar ${test}-dan-ed.jar"
  echo
  echo "run instrumented jar file:"
  echo "java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/libdanhelper.so -cp ${CLASSPATH} ${class}/${test}"
else
  # strip debug info from jar file
  pack200 -r -G ${test}-strip.jar ${test}.jar

  # instrument jar file
  java -cp $DANALYZER_DIR/lib/asm-tree-7.2.jar:$DANALYZER_DIR/lib/asm-7.2.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/dist/danalyzer.jar danalyzer.instrumenter.Instrumenter ${test}-strip.jar
  if [[ ! -f ${test}-strip-dan-ed.jar ]]; then
    echo "FAILURE: instrumenting file: ${test}-strip.jar"
    exit 1
  fi
  mv ${test}-strip-dan-ed.jar ${test}-dan-ed.jar

  # run instrumented jar file
  java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/libdanhelper.so -cp ${CLASSPATH} ${class}/${test}

  # run the script to check correctness
  ./check_result.sh
fi
