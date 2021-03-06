#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
#set -o errexit

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

function get_pid
{
  PID=""
  local entry=$( ps -ef | cut -c 1-80 | grep $1 | grep -v grep )
  if [[ "${entry}" != "" ]]; then
    IFS=' ' read -r -a array <<< "${entry}"
    PID=${array[1]}
  fi
}

# finds the pid of the process that is set as a listener of the specified local port.
# (checks for both ip4 and ip6 addresses on local port)
#
# $1 = the local port to check
#
# returns:
# PID = the pid found (empty if none)
#
function find_local_server_pid
{
  # look for ip4 listener on local port
  local iplocal="127.0.0.1"
  local entry=$( sudo netstat -plntu | grep "${iplocal}:$1" | grep LISTEN 2>/dev/null )
  if [[ ${entry} == "" ]]; then
    local iplocal="::"
    entry=$( sudo netstat -plntu | grep "${iplocal}:$1" | grep LISTEN 2>/dev/null )
  fi
#  echo "${entry}"

  # the pid/program is the 7th (last) field of the netstat response
  # (must remove '/program' from entry)
  PID=""
  if [[ ${entry} != "" ]]; then
    IFS=' ' read -r -a array <<< "${entry}"
    PID=${array[6]%/*}
  fi
}

# finds the listening port for the specified server process name and pid.
#
# $1 = the pid of the process
# $2 = the process name
#
# returns:
# PORT = the local host port (empty if none)
#
function find_server_port
{
  local entry=$( sudo netstat -plntu | grep "LISTEN" | grep "$1/$2" 2>/dev/null )
#  echo "${entry}"

  # the port is the 4th field of the netstat response
  PORT=""
  if [[ ${entry} != "" ]]; then
    IFS=' ' read -r -a array <<< "${entry}"
    PORT=${array[3]}
  fi
}

function halt_dansolver
{
  # check if dansolver is in process list
  get_pid dansolver
  if [[ "${PID}" == "" ]]; then
    # if not, check to see if port 4000 is being used as server socket
    # (the above may not catch dansolver if started by java)
    find_local_server_pid 4000
  fi

  if [[ "${PID}" != "" ]]; then
    echo "------------------------------------------------------------"
    echo "killing dansolver process ${PID}"
    kill -9 ${PID}
    sleep 4
  fi
}

function set_java_home
{
  if [ ${LINUX} -eq 1 ]; then
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
  else
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home
  fi
}

function verify_prog
{
  local cmd=$1
  local arg=$2
  local padding=`echo "      " | cut -c ${#cmd}-`
  if command -v ${cmd} > /dev/null 2>&1; then
    if [[ $# -eq 2 ]]; then
      echo "- ${cmd}${padding}: `${cmd} ${arg} | head -1`"
    else
      local fields=$3
      echo "- ${cmd}${padding}: `${cmd} ${arg} | head -1 | cut -d ' ' -f ${fields}`"
    fi
  else
    echo "- ${cmd}${padding}: ** not installed **"
  fi
}

function verify_lib
{
  if [[ ${LINUX} -eq 1 ]]; then
    libext="so"
  else
    libext="dylib"
  fi

  local prog=$1
  local padding=`echo "            " | cut -c ${#prog}-`
  # get date executable was created
  local file="${DSE_SRC_DIR}/${prog}/build/lib${prog}.${libext}"
  if [[ -f ${file} ]]; then
    entry=$( ls -l ${file} )
    IFS=' ' read -r -a array <<< "${entry}"
    echo "- ${prog}${padding}: built on ${array[5]} ${array[6]} ${array[7]}"
  else
    echo "- ${prog}${padding}: ** project not built **"
  fi
}

function verify_jar
{
  local prog=$1
  local padding=`echo "            " | cut -c ${#prog}-`
  # get date executable was created
  local file="${DSE_SRC_DIR}/${prog}/dist/${prog}.jar"
  if [[ -f ${file} ]]; then
    entry=$( ls -l ${file} )
    IFS=' ' read -r -a array <<< "${entry}"
    echo "- ${prog}${padding}: built on ${array[5]} ${array[6]} ${array[7]}"
  else
    echo "- ${prog}${padding}: ** project not built **"
  fi
}

function do_verify
{
  # since we will need to use sudo at some point which will require user password,
  # let's get it over with at the start of the command rather than have user enter
  # in the middle of executing.
  sudo echo "Software versions installed:"

  verify_prog git   --version "3"
  verify_prog ant   -version  "4"
  verify_prog bash  --version "4"
  verify_prog wget  --version "3"
  verify_prog cmake --version "3"
  verify_prog g++   --version "2,3"
  verify_prog jq    --version "1"
  verify_prog unzip -v        "2"
  verify_prog curl  --version "2"
  verify_prog z3    --version "3"

  verify_prog mongod --version "3"

  if which javac > /dev/null 2>&1; then
#    set_java_home
#    echo "- JDK: ${JAVA_HOME}"
    echo "- java   : `java -version  2>&1 | head -1 | cut -d " " -f 3`"
  else
    echo "- java   : ** not installed **"
  fi
  
  echo
  echo "Danalyzer Libraries built:"
  verify_lib "danhelper"
  verify_lib "danpatch"
  verify_jar "danalyzer"

  echo
  echo "Danalyzer Tools built:"
  verify_jar "bcextractor"
  verify_jar "dansolver"
  verify_jar "danlauncher"
  verify_jar "dantestgen"
  verify_jar "dandebug"
  
  echo
  get_pid mongod
  if [[ "${PID}" != "" ]]; then
    find_server_port ${PID} mongod
    if [[ "${PORT}" != "" ]]; then
      echo "- MongoDB currently running as process: ${PID}, listening on ${PORT}"
    else
      echo "- MongoDB currently running as process: ${PID}, but not listening"
    fi
  else
    echo "- MongoDB not currently running"
  fi

  get_pid dansolver
  if [[ "${PID}" == "" ]]; then
    find_local_server_pid 4000
  fi
  if [[ "${PID}" == "" ]]; then
    echo "- dansolver not currently running"
  else
    echo "- dansolver currently running as process ${PID}"
  fi
  echo
}

# this will check if we are either forcing an install of a list of programs or if they are missing
# and will install them for the appropriate syatem.
#
# input: list of programs to install
# FORCE = 1 if we want to install whether they are currently installed or not
#
function do_update_standard
{
  count=$#
  for ((index=0; index < ${count}; index++)) do
    install=0
    if command -v $1 > /dev/null 2>&1; then
      install=1
    fi
    echo "------------------------------------------------------------"
    if [[ ${install} -eq 1 && ${FORCE} -eq 0 ]]; then
      echo "... $1 already installed."
    else
      echo "... installing: $1"
      if [ ${LINUX} -eq 1 ]; then
        sudo apt install -y $1
      else
        brew install $1
      fi
    fi
    shift
  done
}

# perform installation of all needed programs
function do_install
{
  # install standard programs
  if [ ${LINUX} -eq 1 ]; then
    sudo apt update
    do_update_standard git ant cmake g++ jq wget unzip curl
  else
    brew update
    do_update_standard bash ant gcc jq unzip curl
  fi

  # install java
  echo "------------------------------------------------------------"
  install=0
  if which javac > /dev/null 2>&1; then
    install=1
  fi
  if [[ ${install} -eq 1 && ${FORCE} -eq 0 ]]; then
    set_java_home
    echo "... java JDK already installed: ${JAVA_HOME}"
  else
    echo "... installing: java JDK"
    if [ ${LINUX} -eq 1 ]; then
      # jdk 8 is the latest version for ubuntu 16.04 in standard repo
      sudo apt install -y openjdk-8-jdk
    else
      # brew installs jdk 10 by default
      brew cask install java
    fi
    set_java_home
    echo "JAVA_HOME = $JAVA_HOME"
  fi
  
  # install mongodb
  echo "------------------------------------------------------------"
  install=0
  if which mongodb > /dev/null 2>&1; then
    install=1
  fi
  if [[ ${install} -eq 1 && ${FORCE} -eq 0 ]]; then
    echo "... mongodb already installed."
  else
    echo "... installing: MongoDB"
    if [ ${LINUX} -eq 1 ]; then
      sudo apt install -y mongodb
    else
      brew install mongodb
    fi
  fi

  # make sure to setup our database directory on mongodb
  sudo mkdir -p /data/db
  sudo chown -R `id -un` /data/db
}

function do_z3
{
  # define the z3 library files and locations and the os version part of the z3 tool name
  if [ ${LINUX} -eq 1 ]; then
    os_vers="ubuntu-16.04"
    libext="so"
    declare -a z3libfiles=("libz3java.${libext}" "libz3.${libext}" "com.microsoft.z3.jar")
    declare -a z3libpaths=("/usr/lib")
  else
    os_vers="osx-10.14.1"
    libext="dylib"
    declare -a z3libfiles=("libz3java.${libext}" "libz3.${libext}" "com.microsoft.z3.jar")
    declare -a z3libpaths=("/usr/local/lib" "/Library/Java/Extensions")
  fi

  # if we are not forcing a re-install, check if z3 library files are missing
  if [[ ${FORCE} -eq 0 ]]; then
    for path in "${z3libpaths[@]}"; do
      for file in "${z3libfiles[@]}"; do
        if [[ ! -f ${path}/${file} ]]; then
          FORCE=1
          echo "missing z3 lib file: ${path}/${file}"
        fi
      done
    done
    if [[ ! -f "/usr/bin/z3" ]]; then
      FORCE=1
      echo "missing z3 file: /usr/bin/z3"
    fi
    if [[ ${FORCE} -eq 0 ]]; then
      return
    fi
  fi
  
  # we are re-installing, so remove any existing z3 directory
  if [ -d ${DSE_SRC_DIR}/z3 ]; then
    rm -rf ${DSE_SRC_DIR}/z3
  fi
  
  # the URL and base name of the z3 prover tool (common to both OSes)
  z3url_path="https://github.com/Z3Prover/z3/releases/download/z3-4.8.4"
  z3basename="z3-4.8.4.d6df51951f4c-x64"

  # stop dansolver if it is running, since it uses this library
  halt_dansolver

  echo "------------------------------------------------------------"
  echo "... installing z3"
  mkdir ${DSE_SRC_DIR}/z3
  cd ${DSE_SRC_DIR}/z3
  wget ${z3url_path}/${z3basename}-${os_vers}.zip
  unzip ${z3basename}-${os_vers}.zip
  cd ${z3basename}-${os_vers}/bin

  # copy the shared library files to user library
  for path in "${z3libpaths[@]}"; do
    for file in "${z3libfiles[@]}"; do
      sudo cp ${file} ${path}
    done
  done
  sudo cp z3 /usr/bin
}

function do_build
{
  # since we will need to use sudo at some point which will require user password,
  # let's get it over with at the start of the command rather than have user enter
  # in the middle of executing.
  sudo echo "Begining build process."

  # stop dansolver if it is running
  halt_dansolver
  
  # set the java home reference
  set_java_home

  # these commands are common to both OSes
  echo "------------------------------------------------------------"
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
  cp src/libdanhelper.* .
  echo "------------------------------------------------------------"
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
  cp src/libdanpatch.* .
  echo "------------------------------------------------------------"
  echo "Building danalyzer..."
  cd ${DSE_SRC_DIR}/danalyzer
  if [ -d dist ]; then
    rm -rf dist
  fi
  ant
  echo "------------------------------------------------------------"
  echo "Building dansolver..."
  cd ${DSE_SRC_DIR}/dansolver
  if [ -d dist ]; then
    rm -rf dist
  fi
  ant
  echo "------------------------------------------------------------"
  echo "Building danlauncher..."
  cd ${DSE_SRC_DIR}/danlauncher
  if [ -d dist ]; then
    rm -rf dist
  fi
  ant
  echo "------------------------------------------------------------"
  echo "Building bcextractor..."
  cd ${DSE_SRC_DIR}/bcextractor
  if [ -d dist ]; then
    rm -rf dist
  fi
  ant
  echo "------------------------------------------------------------"
  echo "Building dandebug..."
  cd ${DSE_SRC_DIR}/dandebug
  if [ -d dist ]; then
    rm -rf dist
  fi
  ant
  echo "------------------------------------------------------------"
  echo "Building dantestgen..."
  cd ${DSE_SRC_DIR}/dantestgen
  if [ -d dist ]; then
    rm -rf dist
  fi
  ant
  echo

  # start mongo if it is not already running
  # NOTE: cut -c 1-80 eliminates the case where another program is using mongodb as an argument in
  #   its command. probably never necessary, as dansolver is the only known user and it is not
  #   running at this point, but just to be safe.
  get_pid mongod
  if [[ "${PID}" == "" ]]; then
    echo "------------------------------------------------------------"
    echo "Starting MongoDB"
    mongod &
    sleep 2
    get_pid mongod
  fi
  echo "MongoDB running (in background) as process ${PID}"
  
  echo "------------------------------------------------------------"
  echo "Starting dansolver"
  cd ${DSE_SRC_DIR}/dansolver
  ant run &
  sleep 2
  get_pid dansolver
  if [[ "${PID}" == "" ]]; then
    find_local_server_pid 4000
  fi
  if [[ "${PID}" == "" ]]; then
    echo "ERROR: dansolver not running !"
  else
    echo "dansolver running (in background) as process ${PID}"
  fi
  
  echo "------------------------------------------------------------"
  echo "Starting bcextractor"
  cd ${DSE_SRC_DIR}/bcextractor
  ant run &
  sleep 2
  get_pid bcextractor
  if [[ "${PID}" == "" ]]; then
    find_local_server_pid 6000
  fi
  if [[ "${PID}" == "" ]]; then
    echo "ERROR: bcextractor not running !"
  else
    echo "bcextractor running (in background) as process ${PID}"
  fi
}

#========================================= START HERE ============================================
# read options
VERIFY_ONLY=0
INSTALL=0
BUILD=0
FORCE=0
HELP=0
ENTRY=()
while [[ $# -gt 0 ]]; do
  key="$1"
  case ${key} in
    -v|--version)
      VERIFY_ONLY=1
      shift
      ;;
    -i|--install)
      INSTALL=1
      shift
      ;;
    -f|--force)
      FORCE=1
      INSTALL=1
      shift
      ;;
    -b|--build)
      BUILD=1
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

# if neither install or build option is specified, default to build
if [[ ${INSTALL} -eq 0 && ${BUILD} -eq 0 ]]; then
  BUILD=1
fi

set +o nounset
if [ ${HELP} -eq 1 ]; then
  echo "Usage: ./startup.sh [options]"
  echo
  echo "Options:"
  echo " -h = print this help message (no install or build performed)."
  echo " -v = display the installed versions only (no install or build performed)."
  echo " -i = perform an install of all missing tools and libraries needed by the system."
  echo " -f = perform an install of all tools and libraries needed by the system even if already installed."
  echo " -b = stop dansolver (if running), build all DSE components, startup mongodb (if not"
  echo "      running) and start dansolver to allow it to start receiving symbolic constraints"
  echo "      to be solved from the instrumented code."
  echo
  echo "With no options specified, this will behave as if the -b option was selected."
  echo
  exit 0
fi
set -o nounset

set_os_type

if [[ ${VERIFY_ONLY} -eq 1 ]]; then
  do_verify
  exit 0
fi

# do the installation if requested
if [ ${INSTALL} -eq 1 ]; then
  do_install
  do_z3
fi

# do the build if requested
if [ ${BUILD} -eq 1 ]; then
  do_build
fi

