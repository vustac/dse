#include <jni.h>
#include <stdio.h>
#include "danalyzer_executor_NativeCode.h"
#include "value.h"

  JNIEXPORT void JNICALL Java_danalyzer_executor_NativeCode_newArrayNative
    (JNIEnv *env, jclass clz, jobjectArray ptr, jint size, jint type) {

    // create a single object of the specified type
    Value *val = new Value(type);

    // now fill array with it
    for (int ix = 0; ix < size; ix++) {
      ((Value*)ptr)[ix] = *val;
    }
    printf("newArrayNative: created array of length: %d\n", size);
  }
