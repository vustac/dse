#ifndef DANHELPER_H
#define DANHELPER_H

#include <jvmti.h>
#include <iostream>
#include <unordered_set>
#include <unordered_map>
#include <vector>
#include <mutex>
#include <chrono>

// log messages are output if these are set
//#define _DEBUG_PARSE_PARAMS         // logs the ParseParameters details when I-U EnterMethod is called
//#define _DEBUG_ENTER_EXIT           // logs the Enter/Leave method calls for valid action cases (I-I, I-U, U-I)
//#define _DEBUG_ENTER_EXIT_TIMING    // logs the Enter/Leave method timing for valid calls and # times method is called unnecessarily


// defines the thread-specific data in danhelper
typedef struct threadinfo {
  unsigned long mPid;               // the pthread id
  unsigned long mThreadCnt;         // a simple thread id based on the number of threads detected
  bool mNewThread;                  // flag set to indicate this is a new thread
  bool mEngaged;                    // used for synchronization of danalyzer calls
  int  mExecWrapperLevel;           // the nesting level of the ExecWrapper calls
  int  mClassLoaderLevel;           // the nesting level of the ClassLoader calls
  std::vector<bool> mInstrStack;    // the instrumentation status of each method that is either I-I, U-I or I-U

#ifdef _DEBUG_ENTER_EXIT_TIMING
  std::chrono::high_resolution_clock::time_point iStart;  // the start time when an instrumented method is called
  std::chrono::high_resolution_clock::time_point uStart;  // the start time when an uninstrumented method is called
  long uElapsed;                    // holds elapsed time for uninstrumented calls when U-U enters or leaves
#endif
} ThreadInfo;

class DanHelper {

  static std::mutex mxLock;

  static std::unordered_set<std::string> mClassList;
  static bool mStarted;
  static bool mSetupSymbolics;
  static int mClassCount;
  static unsigned long mPthreadId;
  static unsigned long mThreadCnt;
  static jclass mDanClass;

#ifdef _DEBUG_ENTER_EXIT_TIMING
  static std::chrono::high_resolution_clock::time_point mBeginTime;  // the start time when 1st instrumented method is called
  static long ignoreEnter;          // number of times EnterMethod is called unnecessarily
  static long ignoreLeave;          // number of times LeaveMethod is called unnecessarily
#endif

  // the thread-specific data is mapped to its pthreadid value
  static std::unordered_map<unsigned long,ThreadInfo*> mThreadData;

  // Global refs to Danalyzer
  static jmethodID mDanSetupStack;
  static jmethodID mDanRemoveParams;
  static jmethodID mDanObjectCloneEnter;
  static jmethodID mDanObjectCloneExit;
  static jmethodID mDanCreateFrame;
  static jmethodID mDanAddBooleanParameter;
  static jmethodID mDanAddCharParameter;
  static jmethodID mDanAddByteParameter;
  static jmethodID mDanAddShortParameter;
  static jmethodID mDanAddIntegerParameter;
  static jmethodID mDanAddLongParameter;
  static jmethodID mDanAddFloatParameter;
  static jmethodID mDanAddDoubleParameter;
  static jmethodID mDanAddObjectParameter;
  static jmethodID mDanAddArrayParameter;
  static jmethodID mDanBeginFrame;
  static jmethodID mDanPopFrame;
  static jmethodID mDanPopFrameAndPush;
  static jmethodID mDanPushIntegralType;
  static jmethodID mDanPushLongType;
  static jmethodID mDanPushFloatType;
  static jmethodID mDanPushDoubleType;
  static jmethodID mDanPushReferenceType;
  static jmethodID mDanPushArrayType;
  static jmethodID mDanPushVoidType;
  static jmethodID mDanInitSymbolicSelections;
  static jmethodID mDanException;
  static jmethodID mDanStringLength;
  static jmethodID mDanStringEquals;
  static jmethodID mDanStringSubstring;
  static jmethodID mDanGetRGB;
  static jmethodID mDanSetRGB;
  static jmethodID mDanReadLine;
  static jmethodID mDanParseInt;

public:
  static ThreadInfo*
  getThreadInfo(unsigned long);

  static ThreadInfo*
  makeNewThread(unsigned long);

  static void JNICALL
  InitializeHelper(jvmtiEnv *, JNIEnv *, jthread);

  static void JNICALL
  VMStart(jvmtiEnv *, JNIEnv *);

  static void JNICALL
  EnterMethod(jvmtiEnv *, JNIEnv *, jthread, jmethodID);

  static void JNICALL
  LeaveMethod(jvmtiEnv *, JNIEnv *, jthread, jmethodID, jboolean, jvalue);

  static void
  AddClass(const std::string&);

  static void
  InitializeMethods(JNIEnv *);

  static inline void
  ParseParameters(jvmtiEnv *, JNIEnv *, jthread, jmethodID);

  static int
  getValueType(const char* c);

  static void
  GetReturnValue(JNIEnv *, const char *, jvalue);
};

#endif

