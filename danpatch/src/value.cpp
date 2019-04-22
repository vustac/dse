#include "value.h"

Value::Value(int type) {
  this->value = new jobject();
  this->type = type;

  // assign default value for the type
  switch (type) {
    case VALTYPE_CHR:
    case VALTYPE_BLN:
    case VALTYPE_INT8:
    case VALTYPE_INT16:
    case VALTYPE_INT32:
      {
//        int newval = 0;
//        *this->value = (jobject) & newval;
      }
      break;
    case VALTYPE_INT64:
      {
//        long long int newval = 0L;
//        *this->value = (jobject) & newval;
      }
      break;
    case VALTYPE_FLT:
      {
//        float newval = 0.0;
//        *this->value = (jobject) & newval;
      }
      break;
    case VALTYPE_DBL:
      {
//        double newval = 0.0;
//        *this->value = (jobject) & newval;
      }
      break;
    case VALTYPE_STR:
      {
//        char newval[1];
//        newval[0] = 0;
//        *this->value = (jobject) newval;
      }
      break;
    default:
      break;
  }
}
