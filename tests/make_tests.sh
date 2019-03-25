#!/bin/bash

# this allows you to build a single test or build them all. It will create the test build
# directories wherever this script file is run from. A seperate directory is created for each
# test object so they can each have independent danfig files for them.

DSE_DIR=../

# build the jar file with debug info enabled (so danlauncher can access local parameters)
function build
{
  libs=""

  # make the directory for the selected test (if already exists, delete it first)
  if [[ ${TESTMODE} -eq 0 ]]; then
    if [[ -d ${test} ]]; then
      rm -Rf results/${test}
    fi
    mkdir -p results/${test}
  fi
  
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
    echo "jar cvf results/${test}/${test}.jar ${class}/${test}.class ${class}/${test}.java"
  else
    javac -g ${libs} ${class}/${test}.java
    jar cvf results/${test}/${test}.jar ${class}/${test}.class ${class}/${test}.java
    if [[ ! -f results/${test}/${test}.jar ]]; then
      echo "FAILURE: instrumented file not produced!"
      FAILURE=1
    fi
  fi
}

function copy_files
{
  # skip if we are in test mode
  if [[ ${TESTMODE} -ne 0 ]]; then
    exit 0
  fi
  
  # copy the danfig file to the build directory
  if [[ -f ${class}/danfig ]]; then
    cp ${class}/danfig results/${test}
  else
    echo "FAILURE: MISSING DANFIG FILE!"
    FAILURE=1
  fi

  # copy the correctness checking script to the build directory
  if [[ -f ${class}/check_result.sh ]]; then
    cp ${class}/check_result.sh results/${test}
    chmod +x results/${test}/check_result.sh
  else
    echo "FAILURE: MISSING CHECK_RESULT.SH FILE!"
    FAILURE=1
  fi
}

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
  build
  copy_files
else
  # else, we are going to genberate them all...
  # copy the source and library files from the dse tests project
  echo "Building all tests..."

  # search dse test folders recursively for source files to build
  testlist=`find . -name "*.java"`
  for file in ${testlist}; do
    extract_test ${file}
    if [[ ${test} == "LibReturnObject" ]]; then
      # skip this, it is just a lib file used by other tests
      continue
    fi
    build
    copy_files

    # exit loop on any failures
    if [[ ${FAILURE} -ne 0 ]]; then
      exit 1
    fi
    echo "------------------------------------------"
  done
fi
