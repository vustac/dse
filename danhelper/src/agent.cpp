#include "danhelper.h"
#include <cassert>
#include <fstream>

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved)
{
  jvmtiEnv *jvmti = NULL;
  jvm->GetEnv((void **)&jvmti, JVMTI_VERSION);

  // capabilities
  jvmtiCapabilities capa = {0};
  capa.can_generate_method_entry_events = 1;
  capa.can_generate_method_exit_events = 1;
  capa.can_access_local_variables = 1;
  capa.can_generate_all_class_hook_events = 1;
  jvmtiError errNum = jvmti->AddCapabilities(&capa);
  assert(JVMTI_ERROR_NONE == errNum);

  errNum = jvmti->SetEventNotificationMode(
      JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
  assert(JVMTI_ERROR_NONE == errNum);
  errNum = jvmti->SetEventNotificationMode(
      JVMTI_ENABLE, JVMTI_EVENT_VM_START, NULL);
  assert(JVMTI_ERROR_NONE == errNum);
  errNum = jvmti->SetEventNotificationMode(
      JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, NULL);
  assert(JVMTI_ERROR_NONE == errNum);        
  errNum = jvmti->SetEventNotificationMode(
      JVMTI_ENABLE, JVMTI_EVENT_METHOD_EXIT, NULL);   
  assert(JVMTI_ERROR_NONE == errNum);

  // register callbacks
  jvmtiEventCallbacks callbacks = {0};
  callbacks.VMInit = &DanHelper::InitializeHelper;
  callbacks.VMStart = &DanHelper::VMStart;
  callbacks.MethodEntry = &DanHelper::EnterMethod;
  callbacks.MethodExit = &DanHelper::LeaveMethod;
  errNum = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
  assert(JVMTI_ERROR_NONE == errNum);

  // read class names
  std::ifstream tagFile("classlist.txt");
  if (tagFile.is_open()) {
    std::string className;
    while (tagFile >> className) {
      DanHelper::AddClass(className);
    }
  }

  return JNI_OK;
}
