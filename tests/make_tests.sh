#!/bin/bash

# this allows you to build a single test or build them all. It will create the test build
# directories wherever this script file is run from. A seperate directory is created for each
# test object so they can each have independent danfig files for them.

DSE_DIR=../

# build the jar file with debug info enabled (so danlauncher can access local parameters)
function build
{
  libs=""
  echo "Building ${test}..."

  # make the directory for the selected test (if already exists, just remove the jar file)
  if [[ ${TESTMODE} -eq 0 ]]; then
    if [[ -d ${test} ]]; then
      rm -f results/${test}/${test}.jar
    else
      mkdir -p results/${test}
    fi
  fi
  
  # handle special cases (such as if a library file or other files are necessary)
  case ${test} in
    SimpleNano)
      ;&   # fall through...
    SimpleServer)
      if [ ! -d ${test}/lib ]; then
        mkdir ${test}/lib
      fi
      cp lib/nanohttpd-2.2.0.jar ${test}/lib
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
      LIBFILE="lib/LibReturnObject"
      if [ ! -d ${test}/lib ]; then
        mkdir ${test}/lib
      fi
      javac ${path}/${LIBFILE}.java
      jar cvf ${test}/${LIBFILE}.jar ${path}/${LIBFILE}.class ${path}/${LIBFILE}.java
      libs="-cp .:lib/LibArrayObject.jar"
      ;;
    *)
      ;;
  esac

  # now build the file(s) and jar them up (include source and class files in jar)
  if [[ ${TESTMODE} -ne 0 ]]; then
    echo "javac -g ${libs} ${class}/${test}.java"
    echo "jar cvf results/${test}/${test}.jar ${class}/${test}.class ${class}/${test}.java"
  else
    javac -g ${libs} ${class}/${test}.java
    jar cvf results/${test}/${test}.jar ${class}/${test}.class ${class}/${test}.java
  fi
}

# this takes the full pathname of the source file and converts it into a 'test' name (the name
# of the source file without the path or the ".java" extension) and a 'class' name (the path).
function extract_test
{
  cmd=$1
  if [[ ${cmd} == "" ]]; then
    echo "File '${cmd}' not found!"
    exit 1
  fi
  # extract the java filename only & get its length
  test=`echo ${cmd} | sed 's#.*/##g'`
  namelen=${#test}
  if [[ ${namelen} -eq 0 ]]; then
    echo "extraction of filename failed!"
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

# all tests will be kept in a sub-folder of the current location called "results"
if [[ ! -d "results" ]]; then
  mkdir "results"
fi

# this is assumed to be running from the tests directory
if [[ ${COMMAND} != "" ]]; then
  # if a single test is requested, just make that one
#  file=`find . -name "${COMMAND}.java"`
  file=`find edu -name "${COMMAND}.java"`
  if [[ ${file} == "" ]]; then
    echo "test not found for: ${COMMAND}"
    exit 0
  fi
  extract_test ${file}
  echo "Building single test '${test}' from path '${class}'"
  build

  # copy the danfig file to the build directory
  if [[ -f ${class}/danfig ]]; then
    cp ${class}/danfig results/${test}
  else
    echo "MISSING DANFIG FILE!"
  fi

  # copy the correctness checking script to the build directory
  if [[ -f ${class}/check_result.sh ]]; then
    cp ${class}/check_result.sh results/${test}
  else
    echo "MISSING CHECK_RESULT.SH FILE!"
  fi
else
  # else, we are going to genberate them all...
  # copy the source and library files from the dse tests project
  echo "Building all tests..."

  # search dse test folders recursively for source files to build
  testlist=`find . -name "*.java"`
  for file in ${testlist}; do
    extract_test ${file}
    build

    # copy the danfig file to the build directory
    if [[ -f ${class}/danfig ]]; then
      cp ${class}/danfig results/${test}
    else
      echo "MISSING DANFIG FILE!"
    fi
  done
fi
