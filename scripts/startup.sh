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

# find location of the root directory of all the DSE sub-projects
DSE_SRC_DIR=`realpath ${SCRIPTPATH}/../src`

function set_os_type
{
  OSTYPE=`uname`
  if [[ "${OSTYPE}" == "Linux" ]]; then
    LINUX=1
  elif [[ "${OSTYPE}" == "Darwin" ]]; then
    LINUX=0
  else
    echo "ERROR: unknown operating system: ${OSTYPE}"
    exit 1
  fi
}

function do_update
{
  count=$#
  for ((index=0; index < ${count}; index++)) do
    if command -v $1 > /dev/null 2>&1; then
      echo "... $1 already installed."
    else
      if [ ${LINUX} -eq 1 ]; then
        echo "installing: $1"
        sudo apt install -y $1
      else
        brew install $1
      fi
    fi
    shift
  done
}

# TODO: check if mongo and java need updating prior to installation
function do_install
{
  if [ ${LINUX} -eq 1 ]; then
    sudo apt update
    do_update git ant cmake g++ jq wget unzip
    sudo apt install -y mongodb
    sudo apt install -y openjdk-8-jdk
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
  else
    brew update
    do_update bash ant gcc jq unzip
    brew install mongodb
    brew cask install java
    # brew installs jdk 10 by default
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home
    echo "JAVA_HOME = $JAVA_HOME"
    sudo mkdir -p /data/db
    sudo chown -R `id -un` /data/db
  fi
}

function do_z3
{
  # exit if z3 project already exists
  if [ -d ${DSE_SRC_DIR}/z3 ]; then
    return
  fi
  
  # the URL and base name of the z3 prover tool (common to both OSes)
  z3url_path="https://github.com/Z3Prover/z3/releases/download/z3-4.8.4"
  z3basename="z3-4.8.4.d6df51951f4c-x64"

  # this defines the os version part of the z3 tool name
  if [ ${LINUX} -eq 1 ]; then
    os_vers="ubuntu-16.04"
  else
    os_vers="osx-10.14.1"
  fi

  # stop dansolver if it is running, since it uses this library
  local pid=$( ps -ef | grep dansolver | grep -v grep | cut -c 11-15 2>/dev/null )
  if [[ "${pid}" != "" ]]; then
    echo "killing dansolver process ${pid}"
    kill -9 ${pid}
    sleep 4
  fi

  echo "Building z3..."
  mkdir ${DSE_SRC_DIR}/z3
  cd ${DSE_SRC_DIR}/z3
  wget ${z3url_path}/${z3basename}-${os_vers}.zip
  unzip ${z3basename}-${os_vers}.zip
  cd ${z3basename}-${os_vers}/bin

  # copy the shared library files to user library
  if [ ${LINUX} -eq 1 ]; then
    sudo cp z3 libz3java.so libz3.so com.microsoft.z3.jar /usr/lib
  else
    sudo cp z3 libz3java.dylib libz3.dylib com.microsoft.z3.jar /usr/local/lib
    sudo cp z3 libz3java.dylib libz3.dylib com.microsoft.z3.jar /Library/Java/Extensions
  fi
}

function do_build
{
  # stop dansolver if it is running
  local pid=$( ps -ef | grep dansolver | grep -v grep | cut -c 11-15 2>/dev/null )
  if [[ "${pid}" != "" ]]; then
    echo "killing dansolver process ${pid}"
    kill -9 ${pid}
    sleep 4
  fi

  # these commands are common to both OSes
  echo "Building danhelper..."
  cd ${DSE_SRC_DIR}/danhelper
  git clean -d -f -x
  if [ -d build ]; then
    rm -rf build
  fi
  mkdir build
  cd build
  cmake ..
  make
  echo "Building danpatch..."
  cd ${DSE_SRC_DIR}/danpatch
  git clean -d -f -x
  if [ -d build ]; then
    rm -rf build
  fi
  mkdir build
  cd build
  cmake ..
  make
  echo "Building danalyzer..."
  cd ${DSE_SRC_DIR}/danalyzer
  ant
  echo "Building dansolver..."
  cd ${DSE_SRC_DIR}/dansolver
  ant
  echo "Building danlauncher..."
  cd ${DSE_SRC_DIR}/danlauncher
  ant
  echo "Building dantestgen..."
  cd ${DSE_SRC_DIR}/dantestgen
  ant

  # start mongo if it is not already running
  # NOTE: cut -c 1-80 eliminates the case where another program is using mongodb as an argument in
  #   its command. probably never necessary, as dansolver is the only known user and it is not
  #   running at this point, but just to be safe.
  local mongopid=$( ps -ef | cut -c 1-80 | grep mongod | grep -v grep | cut -c 11-15 2>/dev/null )
  if [[ "${mongopid}" == "" ]]; then
    echo "Starting mongo"
    mongod &
    sleep 2
    mongopid=$( ps -ef | grep mongod | grep -v grep | cut -c 11-15 2>/dev/null )
  fi
  
  echo "Starting dansolver"
  cd ${DSE_SRC_DIR}/dansolver
  ant run &
  sleep 2
  pid=$( ps -ef | grep dansolver | grep -v grep | cut -c 11-15 2>/dev/null )

  echo
  echo "mongod running (in background) as process ${mongopid}"
  echo "dansolver running (in background) as process ${pid}"
}

#========================================= START HERE ============================================
# read options
INSTALL=0
BUILD=0
FORCE_Z3=0
HELP=0
ENTRY=()
while [[ $# -gt 0 ]]; do
  key="$1"
  case ${key} in
    -i|--install)
      INSTALL=1
      shift
      ;;
    -b|--build)
      BUILD=1
      shift
      ;;
    -z|--force-z3)
      FORCE_Z3=1
      shift
      ;;
    -h|--help)
      HELP=1
      shift
      ;;
    *)
      ENTRY+=("$1")
      shift
      ;;
  esac
done

# this allows picking off selected entries from the rest of the command entered
#entry_first="${ENTRY[@]:0:1}"
#entry_rest="${ENTRY[@]:1}"

set +o nounset
if [ ${HELP} -eq 1 ]; then
  echo "Usage: ./startup.sh [options]"
  echo
  echo "Options:"
  echo " -i = perform an install of all necessary tools and libraries needed by the system."
  echo "      (default is OFF)"
  echo " -b = stop dansolver (if running), build all DSE components, startup mongodb (if not"
  echo "      running) and start dansolver to allow it to start receiving symbolic constraints"
  echo "      to be solved from the instrumented code."
  echo "      (default is ON)"
  echo " -z = force pulling in and building z3 even if already installed"
  echo "      (default is OFF)"
  echo " -h = print this help message"
  echo
  echo "With no options specified, this will behave as if the -b option was selected."
  echo
  exit 0
fi
set -o nounset

if [[ ${INSTALL} -eq 0 && ${BUILD} -eq 0 ]]; then
  BUILD=1
fi

set_os_type

# do the installation if requested
if [ ${INSTALL} -eq 1 ]; then
  do_install
fi

# if forcing re-build of z3, remove the current directory
if [[ ${FORCE_Z3} -eq 1 ]]; then
  rm -rf ${DSE_SRC_DIR}/z3
fi

# download and install z3 library if not found
do_z3

# do the build if requested
if [ ${BUILD} -eq 1 ]; then
  do_build
fi

