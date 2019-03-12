package danalyzer.executor;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;

/**
 *
 * @author zzk
 */
public class ValueOp {
  // Character, Byte and Short types are all lumped in with Integer in some cases.
  // This is to prevent an exception for casting one of these boxed types directly to Integer,
  // which is not allowed. Also can't directly convert from boxed Integer to Long, which this fixes.
  private static Object getIntegralValue(Value val, int intType) {
    Object obj = val.getValue();
    if (intType == Value.INT32) {
      switch (obj.getClass().getName()) {
        case "java.lang.Character":
          return (Integer) ((int) ((Character) obj));
        case "java.lang.Byte":
          return (Integer) ((Byte) obj).intValue();
        case "java.lang.Short":
          return (Integer) ((Short) obj).intValue();
        case "java.lang.Long":
          return (Integer) ((Long) obj).intValue();
      }
      return (Integer) obj;
    }

    switch (obj.getClass().getName()) {
      case "java.lang.Character":
        return new Long ((int) ((Character) obj));
      case "java.lang.Byte":
        return (Long) ((Byte) obj).longValue();
      case "java.lang.Short":
        return (Long) ((Short) obj).longValue();
      case "java.lang.Integer":
        return (Long) ((Integer) obj).longValue();
    }
    return (Long) obj;
  }
  
  // --- bitwise operations ---
  public static Value andValue(int type, Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    } 
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (type == Value.INT64) {
        rest = (Long) obj1 & (Long) obj2;
      } else {
        rest = (Integer) obj1 & (Integer) obj2;
      }
      return new Value(rest, type);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVAND(expr1, expr2), Value.SYM | type);
  }
  
  public static Value orValue(int type, Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }

    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (type == Value.INT64) {
        rest = (Long) obj1 | (Long) obj2;
      } else {
        rest = (Integer) obj1 | (Integer) obj2;
      }
      return new Value(rest, type);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVOR(expr1, expr2), Value.SYM | type);      
  }      
  
  public static Value xorValue(int type, Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (type == Value.INT64) {
        rest = (Long) obj1 ^ (Long) obj2;
      } else {
        rest = (Integer) obj1 ^ (Integer) obj2;
      }
      return new Value(rest, type);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVXOR(expr1, expr2), Value.SYM | type);     
  }      
  
  public static Value shiftLeftValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT32)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 << (Integer) obj2;
      } else {
        rest = (Long) obj1 << (Integer) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVSHL(expr1, expr2), Value.SYM | intType);     
  }      
  
  public static Value shiftRightLogicValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT32)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 >>> (Integer) obj2;
      } else {
        rest = (Long) obj1 >>> (Integer) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVLSHR(expr1, expr2), Value.SYM | intType);    
  }      
  
  public static Value shiftRightArithValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT32)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 >> (Integer) obj2;
      } else {
        rest = (Long) obj1 >> (Integer) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVASHR(expr1, expr2), Value.SYM | intType);      
  }  
  
  // --- arithmetic operations ---
  public static Value negIntegralValue(Value val, Context z3Context) {
    if (val == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val.isType(Value.INT64)) {
      intType = Value.INT64;
    } else if (!val.isType(Value.INT32)) {
      return null;
    }
    
    if (!val.isType(Value.SYM)) {
      Object obj = val.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = 0 - (Integer) obj;
      } else {
        rest = 0 - (Long) obj;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = z3Context.mkBV(0, 64);
    BitVecExpr expr2 = Util.getBitVector(val, z3Context);
    return new Value(z3Context.mkBVSub(expr1, expr2), Value.SYM | intType);
  }
  
  public static Value addIntegralValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT64)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = getIntegralValue(val1, intType);
      Object obj2 = getIntegralValue(val2, intType);
      Object rest;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 + (Integer) obj2;
      } else {
        rest = (Long) obj1 + (Long) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);    
    return new Value(z3Context.mkBVAdd(expr1, expr2), Value.SYM | intType);
  }      
  
  public static Value subIntegralValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT64)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = getIntegralValue(val1, intType);
      Object obj2 = getIntegralValue(val2, intType);
      Object rest;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 - (Integer) obj2;
      } else {
        rest = (Long) obj1 - (Long) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVSub(expr1, expr2), Value.SYM | intType);     
  }      
  
  public static Value mulIntegralValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT64)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = getIntegralValue(val1, intType);
      Object obj2 = getIntegralValue(val2, intType);
      Object rest;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 * (Integer) obj2;
      } else {
        rest = (Long) obj1 * (Long) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVMul(expr1, expr2), Value.SYM | intType);     
  }      
  
  public static Value divIntegralValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT64)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = getIntegralValue(val1, intType);
      Object obj2 = getIntegralValue(val2, intType);
      Object rest;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 / (Integer) obj2;
      } else {
        rest = (Long) obj1 / (Long) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVSDiv(expr1, expr2), Value.SYM | intType);    
  }      
  
  public static Value modIntegralValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int intType = Value.INT32;
    if (val1.isType(Value.INT64) && val2.isType(Value.INT64)) {
      intType = Value.INT64;
    } else if (!val1.isType(Value.INT32) && !val2.isType(Value.INT32)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = getIntegralValue(val1, intType);
      Object obj2 = getIntegralValue(val2, intType);
      Object rest;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (intType == Value.INT32) {
        rest = (Integer) obj1 % (Integer) obj2;
      } else {
        rest = (Long) obj1 % (Long) obj2;
      }
      return new Value(rest, intType);
    }
    
    BitVecExpr expr1 = Util.getBitVector(val1, z3Context);
    BitVecExpr expr2 = Util.getBitVector(val2, z3Context);
    return new Value(z3Context.mkBVSRem(expr1, expr2), Value.SYM | intType);    
  }
  
  public static Value negRealValue(Value val, Context z3Context) {
    if (val == null) {
      return null;
    }
    
    int realType = Value.FLT;
    if (val.isType(Value.DBL)) {
      realType = Value.DBL;
    } else if (!val.isType(Value.FLT)) {
      return null;
    }
    
    if (!val.isType(Value.SYM)) {
      Object obj = val.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (realType == Value.FLT) {
        rest = 0.0 - (Float) obj;
      } else {
        rest = 0.0 - (Double) obj;
      }
      return new Value(rest, realType);
    }
    
    ArithExpr expr1 = z3Context.mkReal("" + 0.0);
    ArithExpr expr2 = Util.getReal(val, z3Context);
    return new Value(z3Context.mkSub(expr1, expr2), Value.SYM | realType);
  }
  
  public static Value addRealValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int realType = Value.FLT;
    if (val1.isType(Value.DBL) && val2.isType(Value.DBL)) {
      realType = Value.DBL;
    } else if (!val1.isType(Value.FLT) && !val2.isType(Value.FLT)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (realType == Value.FLT) {
        rest = (Float) obj1 + (Float) obj2;
      } else {
        rest = (Double) obj1 + (Double) obj2;
      }
      return new Value(rest, realType);
    }
    
    ArithExpr expr1 = Util.getReal(val1, z3Context);
    ArithExpr expr2 = Util.getReal(val2, z3Context);
    return new Value(z3Context.mkAdd(expr1, expr2), Value.SYM | realType);
  }      
  
  public static Value subRealValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int realType = Value.FLT;
    if (val1.isType(Value.DBL) && val2.isType(Value.DBL)) {
      realType = Value.DBL;
    } else if (!val1.isType(Value.FLT) && !val2.isType(Value.FLT)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (realType == Value.FLT) {
        rest = (Float) obj1 - (Float) obj2;
      } else {
        rest = (Double) obj1 - (Double) obj2;
      }
      return new Value(rest, realType);
    }
    
    ArithExpr expr1 = Util.getReal(val1, z3Context);
    ArithExpr expr2 = Util.getReal(val2, z3Context);
    return new Value(z3Context.mkSub(expr1, expr2), Value.SYM | realType);
  }      
  
  public static Value mulRealValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int realType = Value.FLT;
    if (val1.isType(Value.DBL) && val2.isType(Value.DBL)) {
      realType = Value.DBL;
    } else if (!val1.isType(Value.FLT) && !val2.isType(Value.FLT)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (realType == Value.FLT) {
        rest = (Float) obj1 * (Float) obj2;
      } else {
        rest = (Double) obj1 * (Double) obj2;
      }
      return new Value(rest, realType);
    }
    
    ArithExpr expr1 = Util.getReal(val1, z3Context);
    ArithExpr expr2 = Util.getReal(val2, z3Context);
    return new Value(z3Context.mkMul(expr1, expr2), Value.SYM | realType);
  }      
  
  public static Value divRealValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
    
    int realType = Value.FLT;
    if (val1.isType(Value.DBL) && val2.isType(Value.DBL)) {
      realType = Value.DBL;
    } else if (!val1.isType(Value.FLT) && !val2.isType(Value.FLT)) {
      return null;
    }
    
    if (!val1.isType(Value.SYM) && !val2.isType(Value.SYM)) {
      Object obj1 = val1.getValue();
      Object obj2 = val2.getValue();
      Object rest = null;
      // do not modify this conditional or rest will always be type Long instead of Integer!
      if (realType == Value.FLT) {
        rest = (Float) obj1 / (Float) obj2;
      } else {
        rest = (Double) obj1 / (Double) obj2;
      }
      return new Value(rest, realType);
    }
    
    ArithExpr expr1 = Util.getReal(val1, z3Context);
    ArithExpr expr2 = Util.getReal(val2, z3Context);
    return new Value(z3Context.mkDiv(expr1, expr2), Value.SYM | realType);
  }
  
  public static Value modRealValue(Value val1, Value val2, Context z3Context) {
    if (val1 == null || val2 == null) {
      return null;
    }
        
    // since mkRem can't take real as the second, let us try to use math to get this round
    Value quot = divRealValue(val1, val2, z3Context);
    Value prod = mulRealValue(val2, quot, z3Context);
    return subRealValue(val1, prod, z3Context);
  }
}
