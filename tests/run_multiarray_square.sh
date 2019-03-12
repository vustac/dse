#!/bin/bash

DANALYZER_DIR=`pwd`/../danalyzer
DANHELPER_DIR=`pwd`/../danhelper
MONGO_JARS=$DANALYZER_DIR/lib/mongodb-driver-core-3.8.2.jar:$DANALYZER_DIR/lib/mongodb-driver-sync-3.8.2.jar:$DANALYZER_DIR/lib/bson-3.8.2.jar

if [[ ! -f $DANHELPER_DIR/libdanhelper.so ]]; then
  DANHELPER_DIR="${DANHELPER_DIR}/build/src"
fi

# BASEPATH = relative path to directory containing all tests and libraries of this type (referenced from the "tests" directory)
# FILENAME = name and relative path of source file (without file type extension) that contains the main method (referenced from $BASEPATH)
BASEPATH="edu/vanderbilt/isis/arrays"
FILENAME="multidim_square/MultiArraySquare"

# derrived names
# MAINCLASS = the Main Class for the program
# RUNNAME   = name of jar file (and danalyzed jar file) to produce
MAINCLASS=`tr '/' '.' <<< "${BASEPATH}.${FILENAME}"`
RUNNAME="symbolic_${FOLDER}"


# build the instrumented file
javac ${BASEPATH}/${FILENAME}.java
jar cvf ${RUNNAME}.jar ${BASEPATH}/${FILENAME}.class

# instrument jar file
java -cp $DANALYZER_DIR/lib/asm-all-5.2.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/dist/danalyzer.jar danalyzer.instrumenter.Instrumenter ${RUNNAME}.jar

# run jar file
java -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$MONGO_JARS -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib -agentpath:$DANHELPER_DIR/libdanhelper.so -Xverify:none -cp $DANALYZER_DIR/dist/danalyzer.jar:./${RUNNAME}-dan-ed.jar ${MAINCLASS}
