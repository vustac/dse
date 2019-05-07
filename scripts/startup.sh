#!/bin/bash

# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
set -o errexit

ostype=`uname`
if [[ "${ostype}" == "Linux" ]]; then
  os_vers="ubuntu-16.04"
  sudo apt-get update
  sudo apt-get install -y git ant cmake openjdk-8-jdk g++ jq wget unzip
  export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
elif [[ "${ostype}" == "Darwin" ]]; then
  os_vers="osx-10.14.1"
  brew update
  brew install bash ant gcc jq unzip mongodb
  brew cask install java
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home
  echo "JAVA_HOME = $JAVA_HOME"
  sudo mkdir -p /data/db
  sudo chown -R `id -un` /data/db
  mongod &
else
  echo "ERROR: unknown operating system: ${ostype}"
  exit 1
fi

# the URL and base name of the z3 prover tool (common to both OSes)
z3url_path="https://github.com/Z3Prover/z3/releases/download/z3-4.8.4"
z3basename="z3-4.8.4.d6df51951f4c-x64"

# these commands are common to both OSes
echo "Building danhelper..."
cd src/danhelper
mkdir build
cd build
cmake ..
make
echo "Building danpatch..."
cd ../../danpatch
mkdir build
cmake .
make
cp src/libdanpatch.* build
echo "Building danalyzer..."
cd ../danalyzer
ant
echo "Building dansolver..."
cd ../dansolver
ant
echo "Building danlauncher..."
cd ../danlauncher
ant
echo "Building dantestgen..."
cd ../dantestgen
ant

echo "Building z3..."
cd ..
mkdir z3
cd z3
wget ${z3url_path}/${z3basename}-${os_vers}.zip
unzip ${z3basename}-${os_vers}.zip
cd ${z3basename}-${os_vers}/bin

if [[ "${ostype}" == "Linux" ]]; then
  sudo cp z3 libz3java.so libz3.so com.microsoft.z3.jar /usr/lib
else
  sudo cp z3 libz3java.dylib libz3.dylib com.microsoft.z3.jar /usr/local/lib
  sudo cp z3 libz3java.dylib libz3.dylib com.microsoft.z3.jar /Library/Java/Extensions
fi
cd ../../..

echo "Running dansolver..."
cd dansolver
ant run &
ps -ef | grep dansolver
cd ../..

#cd test
#if [[ "${ostype}" == "Linux" ]]; then
#  ./make_tests.sh --run
#else
#  /usr/local/bin/bash ./make_tests.sh --run
#fi
