package danalyzer.executor;

/**
 *
 * @author zzk
 */
public class Value {
  // NOTE: the type specified must be either one of the entries excluding ARY, MARY, and SYM.
  // ARY or MARY can be combined with any of these types to indicate an array of the specified type.
  // SYM will be set on any of the above if the value also happens to be symbolic.
  // (so an entry can have SYM + either ARY or MARY + the base data type flag set).
  public static final int BLN   = 0x0001;   // bool or Boolean
  public static final int CHR   = 0x0002;   // char or Char
  public static final int INT8  = 0x0004;   // byte or Byte
  public static final int INT16 = 0x0008;   // short or Short
  public static final int INT32 = 0x0010;   // int or Integer
  public static final int INT64 = 0x0020;   // long or Long
  public static final int FLT   = 0x0040;   // float or Float
  public static final int DBL   = 0x0080;   // double or Double
  public static final int STR   = 0x0100;   // String
  public static final int REF   = 0x0200;   // Object reference
  public static final int ARY   = 0x2000;   // single-dimensional array reference
  public static final int MARY  = 0x4000;   // multi-dimensional array reference
  public static final int SYM   = 0x8000;   // symbolic entry
  
  private Object  value = null;
  private int     type = 0;
  
  public Value(Object val, int type) {
    this.value = val;
    this.type = type;

    // TODO: verify type is not 0
    if (this.value != null) {
      return;
    }
    
    switch (type) {
      case BLN:
      case INT8:
      case INT16:
      case INT32:
      case CHR:
        this.value = (Integer) 0;
        break;
      case INT64:
        this.value = (Long) 0L;
        break;
      case FLT:
        this.value = (Float) 0.0F;
        break;
      case DBL:
        this.value = (Double) 0.0;
        break;
      case STR:
        this.value = "";
        break;
      default:
        break;
    }
  }
  
  public Object getValue() {
    return this.value;
  }
    
  public boolean isType(int type) {
    int tmp = this.type & type;
    return tmp != 0;
  }
  
  public int getType() {
    return this.type;
  }
    
  @Override
  public String toString() {
    if (this.value == null) {
      return "<null>";
    }
    else if (!isType(SYM)) {
      return (this.value).toString();
    }
    return "<symbolic>";
  }
}
