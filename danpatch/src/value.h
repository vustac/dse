#include <jni.h>

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

class Value {
  private:
    jobject *value = NULL;
    int      type = 0;
  
  public:
    Value(int type);
};
