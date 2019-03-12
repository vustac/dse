#include <string.h>
#include "danhelper.h"
#include "string_lookup.hpp"
#include <cassert>
#include <pthread.h>
#include <chrono>
#include <iomanip>

// These are definitions pulled from Value.java file in the danalyzer project
static const int VALTYPE_BLN   = 0x0001;
static const int VALTYPE_CHR   = 0x0002;
static const int VALTYPE_INT8  = 0x0004;
static const int VALTYPE_INT16 = 0x0008;
static const int VALTYPE_INT32 = 0x0010;
static const int VALTYPE_INT64 = 0x0020;
static const int VALTYPE_FLT   = 0x0040;
static const int VALTYPE_DBL   = 0x0080;
static const int VALTYPE_STR   = 0x0100;
static const int VALTYPE_REF   = 0x0200;
static const int VALTYPE_ARY   = 0x2000;
static const int VALTYPE_MARY  = 0x4000;
static const int VALTYPE_SYM   = 0x8000;

static const unsigned long INVALID_THREAD_ID = -1L;


std::mutex DanHelper::mxLock;                   // used for locking access to params to make thread-safe

// these are accessible to all threads provided they use the lock
std::unordered_set<std::string> DanHelper::mClassList;  // a list of the classes in the project (written by Agent_OnLoad)
bool DanHelper::mStarted = false;               // set to true when started (written by VMStart)
bool DanHelper::mSetupSymbolics = false;        // (written once by EnterMethod)
int DanHelper::mClassCount = 1;                 // number of classes in the project (written by LoadClass)
unsigned long DanHelper::mPthreadId = INVALID_THREAD_ID; // set when the pthreadid changes (written by EnterMethod)
unsigned long DanHelper::mThreadCnt = 0;        // a thread counter
jclass DanHelper::mDanClass = NULL;             // (written once by EnterMethod)

#ifdef _DEBUG_ENTER_EXIT_TIMING
std::chrono::high_resolution_clock::time_point DanHelper::mBeginTime;  // the start time when 1st instrumented method is called
long DanHelper::ignoreEnter = 0;  // counts the # of EnterMethod calls that are ignored before a valid entry
long DanHelper::ignoreLeave = 0;  // counts the # of LeaveMethod calls that are ignored before a valid entry
#endif

// this is the thread-specific data (each thread only has access to its own data)
std::unordered_map<unsigned long,ThreadInfo*> DanHelper::mThreadData;

// callbacks
jmethodID DanHelper::mDanRemoveParams = NULL;
jmethodID DanHelper::mDanObjectCloneEnter = NULL;
jmethodID DanHelper::mDanObjectCloneExit = NULL;
jmethodID DanHelper::mDanAddBooleanParameter = NULL;
jmethodID DanHelper::mDanAddCharParameter = NULL;
jmethodID DanHelper::mDanAddByteParameter = NULL;
jmethodID DanHelper::mDanAddShortParameter = NULL;
jmethodID DanHelper::mDanAddIntegerParameter = NULL;
jmethodID DanHelper::mDanAddLongParameter = NULL;
jmethodID DanHelper::mDanAddFloatParameter = NULL;
jmethodID DanHelper::mDanAddDoubleParameter = NULL;
jmethodID DanHelper::mDanAddObjectParameter = NULL;
jmethodID DanHelper::mDanAddArrayParameter = NULL;   
jmethodID DanHelper::mDanBeginFrame = NULL;
jmethodID DanHelper::mDanCreateFrame = NULL;
jmethodID DanHelper::mDanPopFrame = NULL;
jmethodID DanHelper::mDanPopFrameAndPush = NULL;
jmethodID DanHelper::mDanPushIntegralType = NULL;
jmethodID DanHelper::mDanPushLongType = NULL;
jmethodID DanHelper::mDanPushFloatType = NULL;
jmethodID DanHelper::mDanPushDoubleType = NULL;
jmethodID DanHelper::mDanPushReferenceType = NULL;
jmethodID DanHelper::mDanPushArrayType = NULL;
jmethodID DanHelper::mDanPushVoidType = NULL;
jmethodID DanHelper::mDanInitSymbolicSelections = NULL;
jmethodID DanHelper::mDanException = NULL;
jmethodID DanHelper::mDanStringLength = NULL;
jmethodID DanHelper::mDanStringEquals = NULL;
jmethodID DanHelper::mDanStringSubstring = NULL;
jmethodID DanHelper::mDanGetRGB = NULL;
jmethodID DanHelper::mDanSetRGB = NULL;
jmethodID DanHelper::mDanReadLine = NULL;
jmethodID DanHelper::mDanParseInt = NULL;

#ifdef __cplusplus
extern "C" {
#endif

static void JNICALL
_setupStack(JNIEnv *env, jclass klass, jint numParams)
{
  printf("Invoked native _setupStack with %d parameters\n", numParams);
}

#ifdef __cplusplus
}
#endif

void JNICALL
DanHelper::InitializeHelper(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread)
{
}

/**
* returns the ThreadInfo class that was saved for a corresponding pthreadid.
* if there was no entry found for the pthreadid, create a new one.
*/
ThreadInfo*
DanHelper::getThreadInfo(unsigned long pid)
{
  mxLock.lock();
  ThreadInfo *tdata = NULL;

  // search for entry for specified pid in map
  if (!mThreadData.empty()) {
    auto iter = mThreadData.find(pid);
    if (iter != mThreadData.end()) {
      tdata = iter->second;
      mxLock.unlock();
      return tdata;
    }
  }

  // not found - create new entry
  tdata = new ThreadInfo();
  tdata->mPid = pid;
  tdata->mThreadCnt = DanHelper::mThreadCnt++;
  tdata->mNewThread = true;
  tdata->mEngaged = true;
  tdata->mExecWrapperLevel = 0;
  tdata->mClassLoaderLevel = 0;
#ifdef _DEBUG_ENTER_EXIT_TIMING
  auto start = std::chrono::high_resolution_clock::now();
  tdata->iStart = start;
  tdata->uStart = start;
  tdata->uElapsed = 0;
#endif

  std::pair<unsigned long,ThreadInfo*> entry (pid, tdata);
  mThreadData.insert(entry);
//  std::cout << "New Thread: 0x" << std::hex << pid << std::endl;

  mxLock.unlock();
  return tdata;
}

void JNICALL
DanHelper::VMStart(jvmtiEnv *jvmti, JNIEnv *env)
{
  // jclass klass;
  // jfieldID field;
  // jint rc;
  // char* nativeName = (char *)"_setupStack";
  // char* nativeSig = (char *)"(I)V";

  // JNINativeMethod registry[1] = {
  //   {nativeName, nativeSig, (void*)&_setupStack}
  // };

  // klass = env->FindClass("Dan");
  // if (klass == NULL) {
  //   std::cout << "Error: could not find class Dan to register natives!" << std::endl;
  // }
  // rc = env->RegisterNatives(klass, registry, 1);
  // if (rc) {
  //   std::cout << "Error: could not register natives for class Dan!" << std::endl;
  // }

  /*
  field = env->GetStaticFieldID(klass, (char *)"engaged", "I");
  if (field != NULL) {
    env->SetStaticIntField(klass, field, 1);
  } else {
    std::cout << "Error: could not get engaged field for class Dan!" << std::endl;
  }
  */

  // get the ThreadInfo for the current pid, or create a new one if not found.
  unsigned long pid = (unsigned long) pthread_self();
  ThreadInfo *tdata = getThreadInfo(pid);
  tdata->mInstrStack.push_back(false); // we are called by uninstrumented code
  mStarted = true;
}

bool
IsVoidReturn(const char *sig)
{
  const char *c = sig;
  while (*c++ != ')')
    ;

  return *c == 'V';
}

static bool
IsStaticMethod(jvmtiEnv *jvmti, jmethodID mtdID)
{
  jvmtiError errNum;
  jint mtd_modifiers = 0;
  errNum = jvmti->GetMethodModifiers(mtdID, &mtd_modifiers);
  assert(JVMTI_ERROR_NONE == errNum);
  return mtd_modifiers & 0x8; // 0x8 == ACC_STATIC
}

static inline int
GetParameterCount(jvmtiEnv *jvmti, jmethodID mtdID)
{
  jvmtiError errNum;
  int paramCnt = IsStaticMethod(jvmti, mtdID) ? 0 : 1;

  char *name, *sig;
  errNum = jvmti->GetMethodName(mtdID, &name, &sig, NULL);
  assert(JVMTI_ERROR_NONE == errNum);    

  const char *curr = sig;
  while (true) {
    char c = *curr++;
    if (c == '(') {
      continue;
    } else if (c == ')') {
      break;
    }

    switch (c) {
      case 'Z':
      case 'C':
      case 'B':
      case 'S':
      case 'I':
      case 'J':
      case 'F':
      case 'D':
    ++paramCnt;
    break;
      case 'L':
    while (*curr++ != ';')
      ;
    ++paramCnt;
    break;
      case '[':
    while (*curr == '[') {
      curr++;
    }
    if (*curr == 'L') {
      while (*curr++ != ';')
        ;
    } else {
      c = *curr++;
    }
    ++paramCnt;
    break;
    }
  }

  jvmti->Deallocate((unsigned char*)name);
  jvmti->Deallocate((unsigned char*)sig);

  return paramCnt;
}

void
DanHelper::ParseParameters(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jmethodID mtdID)
{
  jvmtiError errNum;
  jint max_locals;
  errNum = jvmti->GetMaxLocals(mtdID, &max_locals);
  assert(JVMTI_ERROR_NONE == errNum);
  jni->CallStaticVoidMethod(mDanClass, mDanBeginFrame, max_locals);

#ifdef _DEBUG_PARSE_PARAMS
  std::cout << "Beginning parse parameters" << std::endl;
#endif

  char *name, *sig;
  errNum = jvmti->GetMethodName(mtdID, &name, &sig, NULL);
  assert(JVMTI_ERROR_NONE == errNum);

#ifdef _DEBUG_PARSE_PARAMS
  int param_count = GetParameterCount(jvmti, mtdID);
  std::cout << "Parsing name: " << name << ", sig: " << sig << " parameter count: " << param_count << std::endl;
#else
  GetParameterCount(jvmti, mtdID);
#endif

  int slot = 0;
  jobject object_value;

  if (!IsStaticMethod(jvmti, mtdID)) {
    errNum = jvmti->GetLocalObject(thread, 0, slot, &object_value);
    assert(JVMTI_ERROR_NONE == errNum);
    jni->CallStaticVoidMethod(mDanClass, mDanAddObjectParameter, object_value, slot);   
    slot++;
    jni->DeleteLocalRef(object_value);
  }

  const char *curr = sig;
  while (true) {
    char c = *curr++;
    if (c == '(') {
      continue;
    } else if (c == ')') {
      break;
    }

    switch (c) {
      case 'Z':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Boolean, ";
#endif
        jint bool_value;
        errNum = jvmti->GetLocalInt(thread, 0, slot, &bool_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddBooleanParameter, bool_value, slot);    
        slot++;
        break;
      case 'C':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Char, ";
#endif
        jint char_value;
        errNum = jvmti->GetLocalInt(thread, 0, slot, &char_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddCharParameter, char_value, slot);   
        slot++;
        break;
      case 'B':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Byte, ";
#endif
        jint byte_value;
        errNum = jvmti->GetLocalInt(thread, 0, slot, &byte_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddByteParameter, byte_value, slot);   
        slot++;
        break;
      case 'S':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Short, ";
#endif
        jint short_value;
        errNum = jvmti->GetLocalInt(thread, 0, slot, &short_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddShortParameter, short_value, slot); 
        slot++;
        break;
      case 'I':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Integer, ";
#endif
        jint int_value;
        errNum = jvmti->GetLocalInt(thread, 0, slot, &int_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddIntegerParameter, int_value, slot);
        slot++;
        break;
      case 'J':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Long, ";
#endif
        jlong long_value;
        errNum = jvmti->GetLocalLong(thread, 0, slot, &long_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddLongParameter, long_value, slot);
        slot += 2; // longs take 2 slots
        break;
      case 'F':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Float, ";
#endif
        jfloat float_value;
        errNum = jvmti->GetLocalFloat(thread, 0, slot, &float_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddFloatParameter, float_value, slot);
        slot++;
        break;
      case 'D':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Double, ";
#endif
        jdouble double_value;
        errNum = jvmti->GetLocalDouble(thread, 0, slot, &double_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddDoubleParameter, double_value, slot);
        slot += 2; // doubles take 2 slots
        break;
      case 'L':
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Object, ";
#endif
        while (*curr++ != ';')
          ;
        errNum = jvmti->GetLocalObject(thread, 0, slot, &object_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddObjectParameter, object_value, slot);
        slot++;
        jni->DeleteLocalRef(object_value);
        break;
      case '[':
        int depth = 1;
        while (*curr == '[') {
          curr++;
          depth++;
        }
#ifdef _DEBUG_PARSE_PARAMS
        std::cout << "Array [" << depth << "], ";
#endif
        int value_type = getValueType(curr);
        if (*curr == 'L') {
#ifdef _DEBUG_PARSE_PARAMS
          std::cout << "Object, ";
#endif
          while (*curr++ != ';')
            ;
        } else {
          c = *curr++; // it was a primitive
#ifdef _DEBUG_PARSE_PARAMS
          std::cout << c;
#endif
        }

        errNum = jvmti->GetLocalObject(thread, 0, slot, &object_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanAddArrayParameter, object_value, slot, depth, value_type);
        slot++;
        jni->DeleteLocalRef(object_value);
        break;
    }
  }

#ifdef _DEBUG_PARSE_PARAMS
  std::cout << std::endl;
#endif

  jvmti->Deallocate((unsigned char*)name);
  jvmti->Deallocate((unsigned char*)sig);

}

void 
DanHelper::InitializeMethods(JNIEnv *jni)
{
  jmethodID m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13, m14, m15;
  jmethodID m16, m17, m18, m19, m20, m21, m22, m23, m24, m25, m26, m27, m28, m29;
  jmethodID m30, m31, m32, m33;

  m1 = jni->GetStaticMethodID(mDanClass, "addBooleanParameter", "(ZI)V");
  if (m1 == 0) {
    std::cout << "Error : could not find addBooleanParameter method!" << std::endl;
  }
  m2 = jni->GetStaticMethodID(mDanClass, "addCharParameter", "(CI)V");
  if (m2 == 0) {
    std::cout << "Error : could not find addCharParameter method!" << std::endl;
  }
  m3 = jni->GetStaticMethodID(mDanClass, "addByteParameter", "(BI)V");
  if (m3 == 0) {
    std::cout << "Error : could not find addByteParameter method!" << std::endl;
  }
  m4 = jni->GetStaticMethodID(mDanClass, "addShortParameter", "(SI)V");
  if (m4 == 0) {
    std::cout << "Error : could not find addShortParameter method!" << std::endl;
  }
  m5 = jni->GetStaticMethodID(mDanClass, "addIntegerParameter", "(II)V");
  if (m5 == 0) {
    std::cout << "Error : could not find addIntegerParameter method!" << std::endl;
  }
  m6 = jni->GetStaticMethodID(mDanClass, "addLongParameter", "(JI)V");
  if (m6 == 0) {
    std::cout << "Error : could not find addLongParameter method!" << std::endl;
  }
  m7 = jni->GetStaticMethodID(mDanClass, "addFloatParameter", "(FI)V");
  if (m7 == 0) {
    std::cout << "Error : could not find addFloatParameter method!" << std::endl;
  }
  m8 = jni->GetStaticMethodID(mDanClass, "addDoubleParameter", "(DI)V");
  if (m8 == 0) {
    std::cout << "Error : could not find addDoubleParameter method!" << std::endl;
  }
  m9 = jni->GetStaticMethodID(mDanClass, "addObjectParameter", "(Ljava/lang/Object;I)V");
  if (m9 == 0) {
    std::cout << "Error : could not find addObjectParameter method!" << std::endl;
  }
  m10 = jni->GetStaticMethodID(mDanClass, "addArrayParameter", "(Ljava/lang/Object;III)V");
  if (m10 == 0) {
    std::cout << "Error : could not find addArrayParameter method!" << std::endl;
  }
  m11 = jni->GetStaticMethodID(mDanClass, "beginFrame", "(I)V");
  if (m11 == 0) {
    std::cout << "Error : could not find beginFrame method!" << std::endl;
  }
  m12 = jni->GetStaticMethodID(mDanClass, "removeParams", "(I)V");
  if (m12 == 0) {
    std::cout << "Error : could not find removeParams method!" << std::endl;
  }
  m13 = jni->GetStaticMethodID(mDanClass, "createFrame", "(II)V");
  if (m13 == 0) {
    std::cout << "Error : could not find createFrame method!" << std::endl;
  }
  m14 = jni->GetStaticMethodID(mDanClass, "popFrame", "(Z)V");
  if (m14 == 0) {
    std::cout << "Error : could not find popFrame method!" << std::endl;
  }
  m15 = jni->GetStaticMethodID(mDanClass, "popFrameAndPush", "(ZZ)V");
  if (m15 == 0) {
    std::cout << "Error : could not find popFrameAndPush method!" << std::endl;
  }
  m16 = jni->GetStaticMethodID(mDanClass, "pushIntegralType", "(II)V");
  if (m16 == 0) {
    std::cout << "Error : could not find pushIntegralType method!" << std::endl;
  }
  m17 = jni->GetStaticMethodID(mDanClass, "pushLongType", "(JI)V");
  if (m17 == 0) {
    std::cout << "Error : could not find pushLongType method!" << std::endl;
  }
  m18 = jni->GetStaticMethodID(mDanClass, "pushFloatType", "(FI)V");
  if (m18 == 0) {
    std::cout << "Error : could not find pushFloatType method!" << std::endl;
  }
  m19 = jni->GetStaticMethodID(mDanClass, "pushDoubleType", "(DI)V");
  if (m19 == 0) {
    std::cout << "Error : could not find pushDoubleType method!" << std::endl;
  }
  m20 = jni->GetStaticMethodID(mDanClass, "pushReferenceType", "(Ljava/lang/Object;I)V");
  if (m20 == 0) {
    std::cout << "Error : could not find pushReferenceType method!" << std::endl;
  }
  m21 = jni->GetStaticMethodID(mDanClass, "pushArrayType", "(Ljava/lang/Object;II)V");
  if (m21 == 0) {
    std::cout << "Error : could not find pushArrayType method!" << std::endl;
  }
  m22 = jni->GetStaticMethodID(mDanClass, "pushVoidType", "()V");
  if (m22 == 0) {
    std::cout << "Error : could not find pushVoidType method!" << std::endl;
  }
  m23 = jni->GetStaticMethodID(mDanClass, "initSymbolicSelections", "()V");
  if (m23 == 0) {
    std::cout << "Error : could not find initSymbolicSelections method!" << std::endl;
  }
  m24 = jni->GetStaticMethodID(mDanClass, "objectCloneEnter", "()V");
  if (m24 == 0) {
    std::cout << "Error : could not find objectCloneEnter method!" << std::endl;
  }
  m25 = jni->GetStaticMethodID(mDanClass, "objectCloneExit", "()V");
  if (m25 == 0) {
    std::cout << "Error : could not find objectCloneExit method!" << std::endl;
  }
  m26 = jni->GetStaticMethodID(mDanClass, "exception", "()V");
  if (m26 == 0) {
    std::cout << "Error : could not find exception method!" << std::endl;
  }
  m27 = jni->GetStaticMethodID(mDanClass, "stringLength", "()V");
  if (m27 == 0) {
    std::cout << "Error : could not find stringLength method!" << std::endl;
  }
  m28 = jni->GetStaticMethodID(mDanClass, "stringEquals", "(Ljava/lang/String;Ljava/lang/Object;)V");
  if (m28 == 0) {
    std::cout << "Error : could not find stringEquals method!" << std::endl;
  }
  m29 = jni->GetStaticMethodID(mDanClass, "stringSubstring", "(Ljava/lang/String;I)V");
  if (m29 == 0) {
    std::cout << "Error : could not find stringSubstring method!" << std::endl;
  }
  m30 = jni->GetStaticMethodID(mDanClass, "bufferedImageGetRGB", "(Ljava/lang/Object;II)V");
  if (m30 == 0) {
    std::cout << "Error : could not find getRGB method!" << std::endl;
  }
  m31 = jni->GetStaticMethodID(mDanClass, "bufferedImageSetRGB", "(Ljava/lang/Object;III)V");
  if (m31 == 0) {
    std::cout << "Error : could not find setRGB method!" << std::endl;
  }
  m32 = jni->GetStaticMethodID(mDanClass, "bufferedReaderReadLine", "()V");
  if (m32 == 0) {
    std::cout << "Error : could not find bufferedReaderReadLine method!" << std::endl;
  }
  m33 = jni->GetStaticMethodID(mDanClass, "integerParseInt", "()V");
  if (m33 == 0) {
    std::cout << "Error : could not find integerParseInt method!" << std::endl;
  }
  
  mDanAddBooleanParameter = m1;
  mDanAddCharParameter = m2;
  mDanAddByteParameter = m3;
  mDanAddShortParameter = m4;
  mDanAddIntegerParameter = m5;
  mDanAddLongParameter = m6;
  mDanAddFloatParameter = m7;
  mDanAddDoubleParameter = m8;
  mDanAddObjectParameter = m9;
  mDanAddArrayParameter = m10;
  mDanBeginFrame = m11;
  mDanRemoveParams = m12;
  mDanCreateFrame = m13;
  mDanPopFrame = m14;
  mDanPopFrameAndPush = m15;
  mDanPushIntegralType = m16;
  mDanPushLongType = m17;
  mDanPushFloatType = m18;
  mDanPushDoubleType = m19;
  mDanPushReferenceType = m20;
  mDanPushArrayType = m21;
  mDanPushVoidType = m22;
  mDanInitSymbolicSelections = m23;
  mDanObjectCloneEnter = m24;
  mDanObjectCloneExit = m25;
  mDanException = m26;
  mDanStringLength = m27;
  mDanStringEquals = m28;
  mDanStringSubstring = m29;
  mDanGetRGB = m30;
  mDanSetRGB = m31;
  mDanReadLine = m32;
  mDanParseInt = m33;
}

void JNICALL
DanHelper::EnterMethod(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread,
               jmethodID mtdID)
{
  if (!mStarted)
    return;

#ifdef _DEBUG_ENTER_EXIT_TIMING
  // get the time at start of method
  bool firstTime = false;
  ++ignoreEnter;
  auto start = std::chrono::high_resolution_clock::now();
  std::chrono::duration<double, std::ratio<1,1000000>> elapsed_us;
  std::chrono::duration<double, std::ratio<1,1000>> elapsed_ms;
  if (mPthreadId == INVALID_THREAD_ID) {
    mBeginTime = start; // elapsed time will be 0 until we pass 1st instrumented call, which will be our new ref
  }
#endif

  // get the ThreadInfo for the current pid, or create a new one if not found.
  unsigned long pid = (unsigned long) pthread_self();
  ThreadInfo *tdata = getThreadInfo(pid);

  if (!tdata->mEngaged)
    return;

  // initialize classes and methods we call in Danalyzer
  if (mDanClass == NULL) {
    jclass local_dan_ptr = jni->FindClass("danalyzer/executor/ExecWrapper");
    if (local_dan_ptr == NULL) {
      std::cout << "Error: could not find class Dan in EnterMethod!" << std::endl;
      return;
    }
    mxLock.lock();
    mDanClass = (jclass)jni->NewGlobalRef(local_dan_ptr);
    if (mDanClass == NULL) {
      std::cout << "Error: could not get global refererence to Dan class!" << std::endl;
    }
    InitializeMethods(jni);
    mxLock.unlock();
  }

  // check for classes to ignore (Danalyzer and ClassLoader)
  jclass class_ptr;
  char *class_sig;
  jvmtiError errNum;
  errNum = jvmti->GetMethodDeclaringClass(mtdID, &class_ptr);
  assert(JVMTI_ERROR_NONE == errNum);
  errNum = jvmti->GetClassSignature(class_ptr, &class_sig, NULL);
  assert(JVMTI_ERROR_NONE == errNum);
  
  // keep stack of ExecWrapper calls and exit if we are in the process of one
  /*
  if (strstr(class_sig, "Ldanalyzer/executor/ExecWrapper") != 0 ||
      strstr(class_sig, "Ldanalyzer/executor/Executor")    != 0) {
    tdata->mExecWrapperLevel++;
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }
  */

  // try to make the string comparison faster
  if (!strncmp("Ldanalyzer/executor/ExecWrapper", class_sig, 31) ||
      !strncmp("Ldanalyzer/executor/Executor", class_sig, 28)) {
    tdata->mExecWrapperLevel++;
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }
  
  if (tdata->mExecWrapperLevel > 0) {
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }

  // determine if we are in the ClassLoader, and if so we exit. (keep track of the call depth)
  if (!strcmp(class_sig, "Ljava/lang/ClassLoader;")) {
    tdata->mClassLoaderLevel++;
  }
  
  if (tdata->mClassLoaderLevel > 0) {
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }

  // check if entered method or its caller is instrumented
  mxLock.lock();
  bool instr = mClassList.find(class_sig) != mClassList.end();
  mxLock.unlock();
  bool caller_instr = tdata->mInstrStack.empty() ? false : tdata->mInstrStack.back();

  // save instrumentation status of current method
  tdata->mInstrStack.push_back(instr);

  // exit if neither method nor its caller is instrumented
  std::string type;
  if (!caller_instr && !instr) {
#ifdef _DEBUG_ENTER_EXIT_TIMING
    // update elapsed time for uninstrumented methods & restart timer for uninstrumented
    if (mPthreadId != INVALID_THREAD_ID) {
      auto finish = std::chrono::high_resolution_clock::now();
      elapsed_us = finish - tdata->uStart;
      tdata->uElapsed += (long) elapsed_us.count();
      tdata->uStart = start;
    }
#endif
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  } else if (caller_instr && instr) {
    type = "I-I";
  } else if (caller_instr && !instr) {
    type = "I-U";
  } else {
    type = "U-I";
  }

  // check for 1st instrumented method entered - must be main(). Save it's thread id.
  mxLock.lock();
  if (mPthreadId == INVALID_THREAD_ID && instr) {
    mPthreadId = pid;
    std::cout << "Initial pthreadid = 0x" << pid << std::endl;
#ifdef _DEBUG_ENTER_EXIT_TIMING
    // don't start counting uninstrumented timing until now
    tdata->uStart = start;
    tdata->uElapsed = 0;
    firstTime = true;
#endif
  }
  mxLock.unlock();

  // get method name and signature
  char *name, *sig;
  errNum = jvmti->GetMethodName(mtdID, &name, &sig, NULL);
  assert(JVMTI_ERROR_NONE == errNum);    

#ifdef _DEBUG_ENTER_EXIT
  std::cout << type << " (enter):  0x" << std::hex << pid << ", " << class_sig << name << sig << std::endl;
#endif

  tdata->mEngaged = false;
  if (!instr && caller_instr) {
    // handle calling UNINSTRUMENTED method from INSTRUMENTED method
    int len_1, len_2, len_3;
    
    len_1 = strlen(name);
    len_2 = strlen(class_sig);
    len_3 = strlen(sig);
    int lookup_len = len_1 + len_2 + len_3;
    
    char full_name[lookup_len + 1];
    memcpy(full_name, name, len_1);
    memcpy(full_name + len_1, class_sig, len_2);
    memcpy(full_name + len_1 + len_2, sig, len_3);
    full_name[lookup_len] = '\0';
    
    int param_count = GetParameterCount(jvmti, mtdID);

    if (!Perfect_Hash::in_word_set(full_name, lookup_len)) {
      jni->CallStaticVoidMethod(mDanClass, mDanRemoveParams, param_count);
    } else {
      // normal cases is to remove the params pushed on shadow stack, since they won't get popped (we don't execute the code)
      // special case: if the method is the Object.clone operation, let's make a clone to return
      if (!strcmp(class_sig,"Ljava/lang/Object;") && !strcmp(name,"clone") && !strcmp(sig,"()Ljava/lang/Object;"))
        jni->CallStaticVoidMethod(mDanClass, mDanObjectCloneEnter);
      else if (!strcmp(class_sig, "Ljava/lang/String;") && !strcmp(name, "length") && !strcmp(sig, "()I")) {
#ifdef _DEBUG_ENTER_EXIT
        std::cout << "Detected string length!" << std::endl;
#endif
        jni->CallStaticVoidMethod(mDanClass, mDanStringLength);
      } else if (!strcmp(class_sig, "Ljava/lang/String;") && !strcmp(name, "equals") && !strcmp(sig, "(Ljava/lang/Object;)Z")) {
        jobject string_value, object_value;
        errNum = jvmti->GetLocalObject(thread, 0, 0, &string_value);
        assert(JVMTI_ERROR_NONE == errNum);
        errNum = jvmti->GetLocalObject(thread, 0, 1, &object_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanStringEquals, string_value, object_value);
      } else if (!strcmp(class_sig, "Ljava/lang/String;") && !strcmp(name, "substring") && !strcmp(sig, "(I)Ljava/lang/String;")) {
        jobject string_value;
        jint int_value;
        errNum = jvmti->GetLocalObject(thread, 0, 0, &string_value);
        assert(JVMTI_ERROR_NONE == errNum);
        errNum = jvmti->GetLocalInt(thread, 0, 1, &int_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanStringSubstring, string_value, int_value);
      } else if (!strcmp(class_sig, "Ljava/awt/image/BufferedImage;") && !strcmp(name, "getRGB") && !strcmp(sig, "(II)I")) {
        jobject image_value;
        jint x_value, y_value;
        errNum = jvmti->GetLocalObject(thread, 0, 0, &image_value);
        assert(JVMTI_ERROR_NONE == errNum);
        errNum = jvmti->GetLocalInt(thread, 0, 1, &x_value);
        assert(JVMTI_ERROR_NONE == errNum);
        errNum = jvmti->GetLocalInt(thread, 0, 2, &y_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanGetRGB, image_value, x_value, y_value);
      } else if (!strcmp(class_sig, "Ljava/awt/image/BufferedImage;") && !strcmp(name, "setRGB") && !strcmp(sig, "(III)V")) {
        jobject image_value;
        jint x_value, y_value, rgb_value;
        errNum = jvmti->GetLocalObject(thread, 0, 0, &image_value);
        assert(JVMTI_ERROR_NONE == errNum);
        errNum = jvmti->GetLocalInt(thread, 0, 1, &x_value);
        assert(JVMTI_ERROR_NONE == errNum);
        errNum = jvmti->GetLocalInt(thread, 0, 2, &y_value);
        assert(JVMTI_ERROR_NONE == errNum);
        errNum = jvmti->GetLocalInt(thread, 0, 3, &rgb_value);
        assert(JVMTI_ERROR_NONE == errNum);
        jni->CallStaticVoidMethod(mDanClass, mDanSetRGB, image_value, x_value, y_value, rgb_value);
      } else if(!strcmp(class_sig, "Ljava/io/BufferedReader;") && !strcmp(name, "readLine") && !strcmp(sig, "()Ljava/lang/String;")) {
	jni->CallStaticVoidMethod(mDanClass, mDanReadLine);
      } else if(!strcmp(class_sig, "Ljava/lang/Integer;") && !strcmp(name, "parseInt") && !strcmp(sig, "(Ljava/lang/String;)I")) {
	jni->CallStaticVoidMethod(mDanClass, mDanParseInt);
      } 
    }
  } else if (instr && caller_instr) {
    // handle calling INSTRUMENTED method from INSTRUMENTED method
    // create a new stack frame for it prior to running.
    int param_count = GetParameterCount(jvmti, mtdID);
    jint max_locals;
    errNum = jvmti->GetMaxLocals(mtdID, &max_locals);
    assert(JVMTI_ERROR_NONE == errNum);
    jni->CallStaticVoidMethod(mDanClass, mDanCreateFrame, param_count, max_locals);
  } else {
    // handle calling INSTRUMENTED method from UNINSTRUMENTED method (as in a callback)
    // first time an instrumented method called from uninstrumented is the main method for the application
    // we need to setup the symbolic parameters selected by the user (or run the GUI for user to manually setup)
    if (!mSetupSymbolics) {
      jni->CallStaticVoidMethod(mDanClass, mDanInitSymbolicSelections);
      mSetupSymbolics = true;
    }
    ParseParameters(jvmti, jni, thread, mtdID);
  }
  tdata->mEngaged = true;

#ifdef _DEBUG_ENTER_EXIT_TIMING
  // update elapsed time spent in instrumented vs uninstrumented calls
  auto finish = std::chrono::high_resolution_clock::now();
  long elapsedMethod;
  if (tdata->mNewThread) {
    elapsedMethod = 0;
  } else if (caller_instr) {
    // get time elapsed since last Instrumented method call (in microseconds)
    elapsed_us = finish - tdata->iStart;
    elapsedMethod = (long) elapsed_us.count();
  } else {
    // get time elapsed since last Uninstrumented method call (in microseconds) and add U-U call elapsed time
    elapsed_us = finish - tdata->uStart;
    elapsedMethod = (long) elapsed_us.count();
    elapsedMethod += tdata->uElapsed;
  }
  tdata->uElapsed = 0;
  instr ? tdata->iStart = start : tdata->uStart = start;

  // if we just started main, update the start time, since the one at the begining of this method was
  // delayed until the user completed his selection of symbolic parameters.
  if (firstTime) {
    start = std::chrono::high_resolution_clock::now();
    mBeginTime = start;
    tdata->iStart = start;
  }

  // calculate elapsed time from 1st instrumented call in milliseconds
  elapsed_ms = finish - mBeginTime;
  long elapsedTotal = (long) elapsed_ms.count();

  // get time elapsed from begining of this call (in microseconds) - this is the duration of the EnterMethod call.
  elapsed_us = finish - start;
  long elapsedEnter = (long) elapsed_us.count();

  // display timing info for call entry
  std::cout << "<" << std::setfill ('0') << std::setw (8) << elapsedTotal << ">  " << type << "  ENTER: " 
            << std::setfill (' ')
            << std::setw (4) << tdata->mThreadCnt
            << std::setw (10) << elapsedMethod
            << std::setw (10) << elapsedEnter
            << std::setw (10) << ignoreEnter
            << "  " << class_sig << name << sig << std::endl;
  ignoreEnter = 0;
#endif

  // make sure we indicate this thread has been used
  tdata->mNewThread = false;

  jvmti->Deallocate((unsigned char*)class_sig);  
  jvmti->Deallocate((unsigned char*)name);
  jvmti->Deallocate((unsigned char*)sig);
}

void JNICALL
DanHelper::LeaveMethod(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread,
               jmethodID mtdID, jboolean exceptFlag, jvalue retVal)
{
#ifdef _DEBUG_ENTER_EXIT_TIMING
  // get the time at start of method
  ++ignoreLeave;
  auto start = std::chrono::high_resolution_clock::now();
  std::chrono::duration<double, std::ratio<1,1000000>> elapsed_us;
  std::chrono::duration<double, std::ratio<1,1000>> elapsed_ms;
  if (mPthreadId == INVALID_THREAD_ID) {
    mBeginTime = start; // elapsed time will be 0 until we pass 1st instrumented call, which will be our new ref
  }
#endif

  // get the ThreadInfo for the current pid, or create a new one if not found.
  unsigned long pid = (unsigned long) pthread_self();
  ThreadInfo *tdata = getThreadInfo(pid);

  if (!tdata->mEngaged)
    return;

  // check for classes to ignore (Danalyzer and ClassLoader)
  jclass class_ptr;
  char *class_sig;
  jvmtiError errNum;
  errNum = jvmti->GetMethodDeclaringClass(mtdID, &class_ptr);
  assert(JVMTI_ERROR_NONE == errNum);
  errNum = jvmti->GetClassSignature(class_ptr, &class_sig, NULL);
  assert(JVMTI_ERROR_NONE == errNum);

  // keep stack of ExecWrapper calls and exit if we are in the process of one
  /*
  if (strstr(class_sig, "Ldanalyzer/executor/ExecWrapper") != 0 ||
      strstr(class_sig, "Ldanalyzer/executor/Executor")    != 0) {
    tdata->mExecWrapperLevel--;
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }
  */

  // try to make the string comparison faster
  if (!strncmp("Ldanalyzer/executor/ExecWrapper", class_sig, 31) ||
      !strncmp("Ldanalyzer/executor/Executor", class_sig, 28)) {
    tdata->mExecWrapperLevel--;
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }
  
  if (tdata->mExecWrapperLevel > 0) {
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }

  // determine if we are in the ClassLoader, and if so we exit. (keep track of the call depth)
  if (tdata->mClassLoaderLevel > 0) {
    if (!strcmp(class_sig, "Ljava/lang/ClassLoader;")) {
      tdata->mClassLoaderLevel--;
    }
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  }

  // check if entered method or its caller is instrumented
  bool instr = tdata->mInstrStack.empty() ? false : tdata->mInstrStack.back();
  bool caller_instr = false;
  if (!tdata->mInstrStack.empty()) {
    tdata->mInstrStack.pop_back();
    caller_instr = tdata->mInstrStack.back();
  }

  // exit if neither method nor its caller is instrumented
  std::string type;
  if (!instr && !caller_instr) {
#ifdef _DEBUG_ENTER_EXIT_TIMING
    // update elapsed time for uninstrumented methods & restart timer for uninstrumented
    if (mPthreadId != INVALID_THREAD_ID) {
      auto finish = std::chrono::high_resolution_clock::now();
      elapsed_us = finish - tdata->uStart;
      tdata->uElapsed += (long) elapsed_us.count();
      tdata->uStart = start;
    }
#endif
    jvmti->Deallocate((unsigned char*)class_sig);
    return;
  } else if (instr && caller_instr) {
    type = "I-I";
  } else if (!instr && caller_instr) {
    type = "U-I";
  } else {
    type = "I-U";
  }

  // get method name and signature
  char *name, *sig;
  errNum = jvmti->GetMethodName(mtdID, &name, &sig, NULL);
  assert(JVMTI_ERROR_NONE == errNum);    

#ifdef _DEBUG_ENTER_EXIT
  std::cout << type << " (return): 0x" << std::hex << pid << ", " << class_sig << name << sig << std::endl;
#endif

  tdata->mEngaged = false;
  if (!instr && caller_instr) {
    // handle returning from UNINSTRUMENTED method to INSTRUMENTED caller
    if (exceptFlag) {
      // indicate an exception
      jni->CallStaticVoidMethod(mDanClass, mDanException);
    } else if (!strcmp(class_sig,"Ljava/lang/Object;") && !strcmp(name,"clone") && !strcmp(sig,"()Ljava/lang/Object;")) {
      // handle return from Object.clone differently - we don't really need to do anything here.
      jni->CallStaticVoidMethod(mDanClass, mDanObjectCloneExit);
    } else {
      // "normal" case: parse the signature to get the return type
      GetReturnValue(jni, sig, retVal);
    }
  } else if (instr && caller_instr) {
    // handle returning from INSTRUMENTED method to INSTRUMENTED caller
    // remove the instrumented frame from the shadow stack and push the top entry to the next stack to set return value.
    bool isVoid = IsVoidReturn(sig);
    jni->CallStaticVoidMethod(mDanClass, mDanPopFrameAndPush, isVoid, exceptFlag);
  } else {
    // handle returning from INSTRUMENTED method to UNINSTRUMENTED caller (as in a callback)
    // remove the instrumented frame from the shadow stack (don't need return value since caller is uninstrumented).
    jni->CallStaticVoidMethod(mDanClass, mDanPopFrame, exceptFlag);
  }
  tdata->mEngaged = true;

#ifdef _DEBUG_ENTER_EXIT_TIMING
  // update elapsed time spent in instrumented vs uninstrumented calls
  auto finish = std::chrono::high_resolution_clock::now();
  long elapsedMethod;
  if (instr) {
    // get time elapsed since last Instrumented method call (in microseconds)
    elapsed_us = finish - tdata->iStart;
    elapsedMethod = (long) elapsed_us.count();
  } else {
    // get time elapsed since last Uninstrumented method call (in microseconds) and add U-U call elapsed time
    elapsed_us = finish - tdata->uStart;
    elapsedMethod = (long) elapsed_us.count();
    elapsedMethod += tdata->uElapsed;
  }
  tdata->uElapsed = 0;
  caller_instr ? tdata->iStart = start : tdata->uStart = start;

  // calculate elapsed time from 1st instrumented call in milliseconds
  elapsed_ms = finish - mBeginTime;
  long elapsedTotal = (long) elapsed_ms.count();

  // get time elapsed from begining of this call (in microseconds) - this is the duration of the LeaveMethod call.
  elapsed_us = finish - start;
  long elapsedLeave = (long) elapsed_us.count();

  // display timing info for call entry
  std::cout << "<" << std::setfill ('0') << std::setw (8) << elapsedTotal << ">  " << type << "  LEAVE: " 
            << std::setfill (' ')
            << std::setw (4) << tdata->mThreadCnt
            << std::setw (10) << elapsedMethod
            << std::setw (10) << elapsedLeave
            << std::setw (10) << ignoreLeave
            << "  " << class_sig << name << sig << std::endl;
  ignoreLeave = 0;
#endif

  // make sure we indicate this thread has been used
  tdata->mNewThread = false;

  jvmti->Deallocate((unsigned char*)class_sig);
  jvmti->Deallocate((unsigned char*)name);
  jvmti->Deallocate((unsigned char*)sig);
}

int
DanHelper::getValueType(const char* c)
{
  int int_type = 0;

  switch (*c) {
    case 'Z':
      int_type = VALTYPE_BLN;
      break;
    case 'C':
      int_type = VALTYPE_CHR;
      break;
    case 'B':
      int_type = VALTYPE_INT8;
      break;
    case 'S':
      int_type = VALTYPE_INT16;
      break;
    case 'I':
      int_type = VALTYPE_INT32;
      break;
    case 'J':
      int_type = VALTYPE_INT64;
      break;
    case 'F':
      int_type = VALTYPE_FLT;
      break;
    case 'D':
      int_type = VALTYPE_DBL;
      break;
    case 'L':
      if (!strncmp(c, "Ljava/lang/String;", 18)) {
        int_type = VALTYPE_STR;
      } else {
        int_type = VALTYPE_REF;
      }
      break;
    case '[':
      int_type = VALTYPE_ARY;
      break;
  }
  return int_type;
}

void
DanHelper::GetReturnValue(JNIEnv *jni, const char * sig, jvalue retVal)
{
  // skip to end of param list to get to the return value
  const char *c = sig;
  while (*c++ != ')')
    ;

  // call the appropriate callback for the data type being returned
  switch (*c) {
    case 'V':
      jni->CallStaticVoidMethod(mDanClass, mDanPushVoidType);
      break;
    case 'Z':
      jni->CallStaticVoidMethod(mDanClass, mDanPushIntegralType, (jint)retVal.z, VALTYPE_BLN);
      break;
    case 'C':
      jni->CallStaticVoidMethod(mDanClass, mDanPushIntegralType, (jint)retVal.c, VALTYPE_CHR);
      break;
    case 'B':
      jni->CallStaticVoidMethod(mDanClass, mDanPushIntegralType, (jint)retVal.b, VALTYPE_INT8);
      break;
    case 'S':
      jni->CallStaticVoidMethod(mDanClass, mDanPushIntegralType, (jint)retVal.s, VALTYPE_INT16);
      break;
    case 'I':
      jni->CallStaticVoidMethod(mDanClass, mDanPushIntegralType, (jint)retVal.i, VALTYPE_INT32);
      break;
    case 'J':
      jni->CallStaticVoidMethod(mDanClass, mDanPushLongType, retVal.j, VALTYPE_INT64);
      break;
    case 'F':
      jni->CallStaticVoidMethod(mDanClass, mDanPushFloatType, retVal.f, VALTYPE_FLT);
      break;
    case 'D':
      jni->CallStaticVoidMethod(mDanClass, mDanPushDoubleType, retVal.d, VALTYPE_DBL);
      break;
    case 'L':
      if (!strncmp(c, "Ljava/lang/String;", 18)) {
        jni->CallStaticVoidMethod(mDanClass, mDanPushReferenceType, retVal.l, VALTYPE_STR);
      } else {
        jni->CallStaticVoidMethod(mDanClass, mDanPushReferenceType, retVal.l, VALTYPE_REF);
      }
      break;
    case '[':
      // skip past all the array bracket levels (don't care how many) to get to the data type
      int depth = 0;
      while (*c == '[') {
        ++depth;
        ++c;
      }
      jni->CallStaticVoidMethod(mDanClass, mDanPushArrayType, retVal.l, getValueType(c), depth);
      break;
  }
}

void 
DanHelper::AddClass(const std::string& className)
{
  mClassList.insert(className);
}
