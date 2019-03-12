#!/bin/bash

DANALYZER_DIR=`pwd`/../danalyzer
DANHELPER_DIR=`pwd`/../danhelper
MONGO_JARS=$DANALYZER_DIR/lib/mongodb-driver-core-3.8.2.jar:$DANALYZER_DIR/lib/mongodb-driver-sync-3.8.2.jar:$DANALYZER_DIR/lib/bson-3.8.2.jar

if [[ ! -f $DANHELPER_DIR/libdanhelper.so ]]; then
  DANHELPER_DIR="${DANHELPER_DIR}/build/src"
fi

javac edu/vanderbilt/isis/create_object/CreateObjectTest.java

jar cvf create_object_test.jar edu/vanderbilt/isis/create_object/

java -cp $DANALYZER_DIR/lib/asm-all-5.2.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/dist/danalyzer.jar danalyzer.instrumenter.Instrumenter create_object_test.jar

java -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$MONGO_JARS -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib -agentpath:$DANHELPER_DIR/libdanhelper.so -Xverify:none -cp $DANALYZER_DIR/dist/danalyzer.jar:./create_object_test-dan-ed.jar edu/vanderbilt/isis/create_object/CreateObjectTest
