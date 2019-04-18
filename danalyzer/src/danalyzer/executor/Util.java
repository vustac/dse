package danalyzer.executor;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.SeqExpr;
import danalyzer.gui.DataConvert;
import danalyzer.gui.DebugUtil;
import static danalyzer.gui.DebugUtil.debugPrintError;
import static danalyzer.gui.DebugUtil.debugPrintInfo;
import static danalyzer.gui.DebugUtil.debugPrintObjects;
import static danalyzer.gui.DebugUtil.debugPrintObjectMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author zzk
 */
public class Util {

  /**
   * returns the max bit length for the selected data type.
   * 
   * @param type - the Value type (or types) - only integral types are observed
   * @return the bit length corresponding to the longest of the data types passed
   */
  public static int getBVLength(int type) {
    int length = 32;
    if ((type & Value.INT64) != 0) {
      length = 64;
    } 
//    else if ((type & Value.INT32) != 0) {
//      length = 32;
//    } else if ((type & Value.INT16) != 0) {
//      length = 16;
//    } else if ((type & Value.CHR) != 0) {
//      length = 16;
//    } else if ((type & Value.INT8) != 0) {
//      length = 8;
//    } else if ((type & Value.BLN) != 0) {
//      length = 32;
//    }
    return length;
  }
  
  // who is to blame: zzk
  public static Expr getExpression(Value val, Context z3Context) {
    Expr expr = getString(val, z3Context);
    if (expr == null) {
      expr = getBitVector(val, z3Context);
      if (expr == null) {
        expr = getReal(val, z3Context);
      }
    }
    
    return expr;
  }
  
  // who is to blame: zzk 
  public static SeqExpr getString(Value val, Context z3Context) {
    if (val == null || !val.isType(Value.STR)) {
      return null;
    }
    
    // if null, return the default value
    Object obj = val.getValue();
    if (obj == null) {
      // TODO: what the heck will be the default value?
      return z3Context.mkString("");
    } else if (val.isType(Value.SYM)) {
      return (SeqExpr) obj;
    } else {
      return z3Context.mkString((String) obj);
    }
  }
  
  // who is to blame: zzk
  public static BitVecExpr getBitVector(Value val, Context z3Context) {
    if (val == null || !val.isType(Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64 | Value.BLN | Value.CHR)) {
      return null;
    }
    
    // if null, return the default value
    Object obj = val.getValue();
    if (obj == null) {
      return z3Context.mkBV(0, 32);
    }
    
    // check for symbolic type
    int type = val.getType();
    if ((type & Value.SYM) != 0) {
      return (BitVecExpr) obj;
    }
    
    // this handles the primitive types
    int dfltlen = getBVLength(type);
    BitVecExpr bv;
    switch (type) {
      case Value.BLN:
        bv = z3Context.mkBV(((Boolean) obj).equals(true) ? 1 : 0, dfltlen);
        break;
      case Value.CHR:
        bv = z3Context.mkBV((Character) obj, dfltlen);
        break;
      case Value.INT8:
        bv = z3Context.mkBV((Byte) obj, dfltlen);
        break;
      case Value.INT16:
        bv = z3Context.mkBV((Short) obj, dfltlen);
        break;
      case Value.INT32:
        bv = z3Context.mkBV((Integer) obj, dfltlen);
        break;
      case Value.INT64:
      default:
        bv = z3Context.mkBV((Long) obj, dfltlen);
        break;
    }
    return bv;
  }

  public static BitVecExpr getBitVector(int exttype, Value val, Context z3Context) {
    if (val == null || !val.isType(Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64 | Value.BLN | Value.CHR)) {
      return null;
    }

    // if null, return the default value
    Object obj = val.getValue();
    if (obj == null) {
      return z3Context.mkBV(0, 32);
    }
    
    // check for symbolic type
    BitVecExpr bv;
    int type = val.getType();
    int dfltlen = getBVLength(type);
    if ((type & Value.SYM) != 0) {
      bv = (BitVecExpr) obj;
    } else {
      // this handles the primitive types
      switch (type) {
        case Value.BLN:
          bv = z3Context.mkBV(((Boolean) obj).equals(true) ? 1 : 0, dfltlen);
          break;
        case Value.CHR:
          bv = z3Context.mkBV((Character) obj, dfltlen);
          break;
        case Value.INT8:
          bv = z3Context.mkBV((Byte) obj, dfltlen);
          break;
        case Value.INT16:
          bv = z3Context.mkBV((Short) obj, dfltlen);
          break;
        case Value.INT32:
          bv = z3Context.mkBV((Integer) obj, dfltlen);
          break;
        case Value.INT64:
        default:
          bv = z3Context.mkBV((Long) obj, dfltlen);
          break;
      }
    }
    
    // get desired size of bit vector and make sure it is not smaller than original length
    int extlen = getBVLength(exttype) - dfltlen;
    if (extlen > 0) {
      bv = z3Context.mkSignExt(extlen, bv);
    }
    
    return bv;
}
  
  // who is to blame: zzk
  public static ArithExpr getReal(Value val, Context z3Context) {
    if (val == null || !val.isType(Value.FLT | Value.DBL)) {
      return null;
    }
    
    // if null, return the default value
    Object obj = val.getValue();
    if (obj == null) {
      return z3Context.mkReal("" + 0.0);
    } else if (val.isType(Value.SYM)) {
      return (ArithExpr) obj;
    } else if (val.isType(Value.FLT)) {
      return z3Context.mkReal("" + (Float) obj);
    } else {
      return z3Context.mkReal("" + (Double) obj);
    }
  }
  
  /**
   * finds and returns the object from the concrete map or creates a new entry to the object map
   * and adds it to the concrete if not found.
   * 
   * @param threadId - the current thread id
   * @param obj      - the concrete object to lookup/add
   * @return the reference Value corresponding to the object
   */
  public static Value makeNewRefValue(int threadId, Object obj) {
    Integer objCnt = null;
    
    // check if entry was in concrete list
    if (obj != null) {
      objCnt = ExecWrapper.getConcreteObject(obj);
    }
    // if not in list, create new object & add entry to list
    if (objCnt == null) {
      Map<String, Value> newMap = new HashMap<>();
      objCnt = ExecWrapper.putReferenceObject(newMap);
      debugPrintObjectMap(threadId, objCnt, newMap);
      boolean added = ExecWrapper.putConcreteObject(obj, objCnt);

      /* ----------debug head--------- *@*/
      if (added) {
        debugPrintObjects(threadId, "  OBJECT[" + objCnt + "] added to CONCRETE");
      }
      // ----------debug tail---------- */
    }
    return new Value(objCnt, Value.REF);
  }
  
  /**
   * creates an array of values of the specified size and sets them to default values.
   * If a firstEntry is supplied, it will copy that value to the 1st array location.
   * 
   * @param threadId   - the current thread id
   * @param type       - the data type for the array
   * @param size       - number of entries in the array
   * @param firstEntry - value of the 1st entry, or null if leave as default value
   * @return the array of Values created
   */
  public static Value[] makeValueArray(int threadId, int type, int size, Value firstEntry) {
    // create the array and initialize the contents
    Value[] arraynew = new Value[size];
    if (type == Value.REF) {
      for (int ix = 0; ix < size; ix++) {
        arraynew[ix] = makeNewRefValue(threadId, null);
      }
    } else {
      Arrays.fill(arraynew, new Value(null, type));
    }
    
    // if specified, assign the 1st array element value
    if (firstEntry != null) {
      arraynew[0] = firstEntry;
    }
    
    return arraynew;
  }

  /**
   * This will convert an array of primitives or Objects into a Value-wrapped combo array type.
   * 
   * @param threadId - the thread id running
   * @param val - the current array of primitives or Objects
   * @param type  - the data type for the array
   */
  // who is to blame: dmcd2356
  public static Value[] cvtValueArray(int threadId, Object val, int type) {
    Value[] combo = new Value[2];    
    Value[] core;
    int size;

    switch (type) {
      case Value.CHR:
        {
          char[] raw = (char[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.BLN:
        {
          boolean[] raw = (boolean[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.INT8:
        {
          byte[] raw = (byte[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.INT16:
        {
          short[] raw = (short[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.INT32:
        {
          int[] raw = (int[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.INT64:
        {
          long[] raw = (long[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.FLT:
        {
          float[] raw = (float[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.DBL:
        {
          double[] raw = (double[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.STR:
        {
          String[] raw = (String[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = new Value(raw[i], type);
          }
          break;
        }
      case Value.REF:
        {
          Object[] raw = (Object[])val;
          size = raw.length;
          core = new Value[size];
          for (int i = 0; i < size; i++) {
            core[i] = makeNewRefValue(threadId, null);
          }
          break;
        }
      default:
        /* ----------debug head--------- *@*/
        debugPrintError(threadId, "cvtValueArray: Invalid data type " + DataConvert.showValueDataType(type));
        // ----------debug tail---------- */
        return null;
    }

    /* ----------debug head--------- *@*/
    debugPrintInfo(threadId, DebugUtil.ARRAYS, "cvtValueArray: type " + DataConvert.showValueDataType(type));
    // ----------debug tail---------- */
    
    // zzk makes some changes to the original one (1) we don't put ARY in combo[0] (2) len is 32-bit
    combo[0] = new Value(core, type);
    combo[1] = new Value(size, Value.INT32);
    return combo;
  }
  
//  public static int convertClassType(String typstr) {
//    int type = 0;
//    if (typstr.startsWith("[")) {
//      type |= Value.ARY;
//      typstr = typstr.substring(typstr.lastIndexOf("[") + 1);
//    }
//
//    switch (typstr) {
//      case "Z":
//        type |= Value.BLN;
//        break;
//      case "C":
//        type |= Value.CHR;
//        break;
//      case "B":
//        type |= Value.INT8;
//        break;
//      case "S":
//        type |= Value.INT16;
//        break;
//      case "I":
//        type |= Value.INT32;
//        break;
//      case "J":
//        type |= Value.INT64;
//        break;
//      case "F":
//        type |= Value.FLT;
//        break;
//      case "D":
//        type |= Value.DBL;
//        break;
//      case "java.lang.String":
//      case "Ljava.lang.String;":
//        type |= Value.STR;
//        break;
//      default:
//        type |= Value.REF;
//        break;
//    }
//
//    return type;
//  }
  
  // who is to blame: zzk
  public static void convertCombo(Value[] combo, int type, Context z3Context) {
    Value[] core = (Value[]) combo[0].getValue();
    
    ArrayExpr newCore = null;
    if ((type & Value.STR) != 0) {
      newCore = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), z3Context.mkStringSort());
    } else if ((type & Value.FLT) != 0 || (type & Value.DBL) != 0) {
      newCore = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), z3Context.mkRealSort());
    } else if ((type & Value.INT64) != 0) {
      newCore = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), z3Context.mkBitVecSort(64));
    } else {
      newCore = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), z3Context.mkBitVecSort(32));
    }
    
    for (int i = 0; i < core.length; i++) {
      BitVecExpr index = z3Context.mkBV(i, 32);
      
      Expr element = null;
      if ((type & Value.STR) != 0) {
        element = getString(core[i], z3Context);
      } else if ((type & Value.FLT) != 0 || (type & Value.DBL) != 0) {
        element = getReal(core[i], z3Context);
      } else {
        element = getBitVector(core[i], z3Context);
      }
      newCore = z3Context.mkStore(newCore, index, element);
    }
    combo[0] = new Value(newCore, type | Value.SYM);
  }
}
