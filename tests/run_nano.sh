#!/bin/bash

DANALYZER_DIR=`pwd`/../danalyzer
DANHELPER_DIR=`pwd`/../danhelper
ORIG_JAR=nano-example.jar
TARGET_JAR=nano-example-dan-ed.jar
NANO_PATH=./lib/nanohttpd-2.2.0.jar

if [[ ! -f $DANHELPER_DIR/libdanhelper.so ]]; then
  DANHELPER_DIR="${DANHELPER_DIR}/build/src"
fi

javac -cp .:./lib/nanohttpd-2.2.0.jar edu/vanderbilt/isis/nano/SimpleNano.java
jar cfv $ORIG_JAR edu/vanderbilt/isis/nano

java -cp $DANALYZER_DIR/lib/asm-all-5.2.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/dist/danalyzer.jar danalyzer.instrumenter.Instrumenter $ORIG_JAR

java -Xverify:none -Dsun.boot.library.path=$JAVA_HOME/bin:/usr/lib -Xbootclasspath/a:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/com.microsoft.z3.jar -agentpath:$DANHELPER_DIR/libdanhelper.so -cp $TARGET_JAR:$DANALYZER_DIR/dist/danalyzer.jar:$DANALYZER_DIR/lib/commons-io-2.5.jar:$DANALYZER_DIR/lib/asm-all-5.2.jar:$NANO_PATH edu.vanderbilt.isis.nano.SimpleNano
