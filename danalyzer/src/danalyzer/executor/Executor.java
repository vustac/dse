package danalyzer.executor;

import static danalyzer.gui.DebugUtil.debugPrintCommand;
import static danalyzer.gui.DebugUtil.debugPrintCommand_type;
import static danalyzer.gui.DebugUtil.debugPrintCallEntry;
import static danalyzer.gui.DebugUtil.debugPrintCallExit;
import static danalyzer.gui.DebugUtil.debugPrintStackRestore;
import static danalyzer.gui.DebugUtil.debugPrintInfo;
import static danalyzer.gui.DebugUtil.debugPrintWarning;
import static danalyzer.gui.DebugUtil.debugPrintError;
import static danalyzer.gui.DebugUtil.debugPrintArrayMap;
import static danalyzer.gui.DebugUtil.debugPrintObjectMap;
import static danalyzer.gui.DebugUtil.debugPrintObjectField;
import static danalyzer.gui.DebugUtil.debugPrintException;
import static danalyzer.gui.DebugUtil.debugPrintAgent;
import static danalyzer.gui.DebugUtil.debugPrintArrayList;
import static danalyzer.gui.DebugUtil.debugPrintObjects;
import static danalyzer.gui.DebugUtil.debugPrintArrays;
import static danalyzer.gui.DebugUtil.debugPrintSolve;
import static danalyzer.gui.DebugUtil.debugPrintBranch;
import static danalyzer.gui.DebugUtil.debugPrintDump;
import static danalyzer.gui.DebugUtil.debugPrintConstraint;
import danalyzer.gui.DebugUtil;
import danalyzer.gui.DataConvert;
import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.RealExpr;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 *
 * @author zzk
 */
public class Executor {
  // used in formatting debugPrintDump
  private static final String NEWLINE = System.getProperty("line.separator");

  private static enum ConstraintType {
    PATH,       // normal path constraint
    ARRAY,      // array out-of-bounds constraint
    LOOPBOUND,  // loop bounds constraint
  }
  
  private StackFrame           currentStackFrame;
  private Stack<StackFrame>    executionStack;
  private boolean              initialized;
  private int                  threadId;
  private boolean              invokeDynamic;

  // User-defined Symbolics & Constraints
  private Map<String, ArrayList<UserSymbolic>> symbolicMap = new HashMap<>();
  
  private Context               z3Context;
  private List<BoolExpr>        z3Constraints;
  private Map<String, Expr>     exprMap;
  private Optimize              z3Optimize;
  
  // used for handling special uninstrumented methods like String.length()
  private boolean needReturnValue = true;

  // for BufferedImage agent commands
  private HashMap<java.awt.image.BufferedImage, Value[][]> bufferedImageMap = new HashMap<>();
  
  // interface to the solver (dansolver)
  private SolverInterface       solverPort;
  private int                   constraintCount;

  private int numArrayConstraints = 0;
  

  Executor(int id, ArrayList<UserSymbolic> symbolicInfo) {

    /* ----------debug head--------- *@*/
    debugPrintInfo(threadId, "Starting Executor[" + id + "]");
    // ----------debug tail---------- */
    
    threadId = id;
    constraintCount = 0;
    currentStackFrame = null;
    executionStack = new Stack<>();
    invokeDynamic = false;
    initialized = false;
    solverPort = new SolverInterface();
    
    // copy the symbolic info over (must use deep-copy loop instead of shallow-copy putAll,
    // because each Executor will remove entries from their copy when the symbolic/constraint
    // has been applied so that it won't continue to initialize the usage of the parameter.
    // A shallow copy would affect the ExecWrapper copy as well as all other Executor copies and
    // would lose the information.
    ArrayList<UserSymbolic> symbolicEntries = new ArrayList<>(symbolicInfo);
    
    // now let's create a Map to organize the symbolics based on the method
    for (UserSymbolic entry : symbolicInfo) {
      String method = entry.methodName;
      
      // let's convert the method name passed to use a dot instead of slash between the class
      // and method name so it can be compared directly to the format of the method names used here.
      int offset = method.lastIndexOf("(");
      String signature = method.substring(offset);
      method = method.substring(0, offset);
      offset = method.lastIndexOf("/");
      method = method.substring(0, offset) + "." + method.substring(offset + 1) + signature;
      
      // now add the entry to the list maintained for the specified method
      ArrayList<UserSymbolic> list;
      if (symbolicMap.isEmpty() || !symbolicMap.containsKey(method)) {
        list = new ArrayList<>();
        symbolicMap.put(method, list);
      } else {
        list = symbolicMap.get(method);
      }
      list.add(entry);
    }

    exprMap = new HashMap<>();
    z3Context = new Context();
    z3Constraints = new ArrayList<>();
    z3Optimize = z3Context.mkOptimize();

    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "initialized constraints");
    // ----------debug tail---------- */
  }
  

  public static class UserSymbolic {

    public String  id;            // user-defined reference name
    public String  methodName;    // name of method the local parameter is defined in
    public int     param;         // slot number of the local parameter
    public int     ixStart;       // starting opcode offset that param is valid over
    public int     ixEnd;         // ending   opcode offset that param is valid over
    public int     type;          // Value data type of parameter
    public ArrayList<UserConstraint> constraint;  // list of user constraints for this parameter
    
    public UserSymbolic(String methName, int slot) {
      methodName = methName;
      param = slot;
      ixStart = 0;
      ixEnd = 0; // this will encompass entire method range
      type = Value.INT32;
      id = methName + "_" + slot + "_0_0";
      constraint = new ArrayList<>();
    }
    
    public UserSymbolic(String methName, int slot, int start, int end, String name, int valtype) {
      methodName = methName;
      param = slot;
      ixStart = start;
      ixEnd = end;
      type = valtype;
      id = name;
      constraint = new ArrayList<>();
    }
    
    public static int convertDataType(String dtype) {
      int valtype = Value.INT32; // set a default type
      int aryType = 0; // default to not an array
      
      // standardize the data type entry
      if (dtype.startsWith("L")) {
        dtype = dtype.substring(1);
        valtype = Value.REF;
      }
      if (dtype.endsWith(";")) {
        dtype = dtype.substring(0, dtype.length() - 1);
      }
      dtype = dtype.replaceAll("/", ".");
      
      // check for array or multi-dimensional array
      if (dtype.startsWith("[")) {
        if (dtype.startsWith("[[")) {
          aryType = Value.MARY;
        } else {
          aryType = Value.ARY;
        }
        dtype = dtype.substring(dtype.lastIndexOf("[") + 1);
      }
      
      // now convert it to what we require
      switch (dtype) {
        case "Z":
        case "java.lang.Boolean":
          valtype = Value.BLN;
          break;
        case "C":
        case "java.lang.Char":
          valtype = Value.CHR;
          break;
        case "B":
        case "java.lang.Byte":
          valtype = Value.INT8;
          break;
        case "S":
        case "java.lang.Short":
          valtype = Value.INT16;
          break;
        case "I":
        case "java.lang.Integer":
          valtype = Value.INT32;
          break;
        case "J":
        case "java.lang.Long":
          valtype = Value.INT64;
          break;
        case "F":
        case "java.lang.Float":
          valtype = Value.FLT;
          break;
        case "D":
        case "java.lang.Double":
          valtype = Value.DBL;
          break;
        case "java.lang.String":
        case "Value.STR":
          valtype = Value.STR;
          break;
        default:
          break;
      }

      return valtype + aryType;
    }
    
    public String getKeyReference() {
      return methodName + "_" + param + "_" + ixStart + "_" + ixEnd;
    }
  }
  
  public static class UserConstraint {
    public String  compareType;
    public String  value;
    
    public UserConstraint(String type, String val) {
      compareType = type;
      value = val;
    }
  }
  
  //================================================================
  // LOCAL HELPER METHODS
  //================================================================
  
  /**
   * this will report the specified error, dump the current stack contents and halt execution.
   * 
   * @param message - the error message to display
   */
  private void exitError(String message) {
    debugPrintError(threadId, message);
    dumpStackInfo(true);
    System.exit(1);
  }

  /**
   * indicate an error if the specified Value is null.
   * The program will terminate if the error condition is true.
   * 
   * @param val - the Value to test
   * @param opcode - the opcode being run
   * @param param  - the param value tested
   */
  private void assertValueNonNull(Value val, String opcode, String param) {
    if (val == null) {
      exitError("(" + opcode + ") - " + param + " is NULL");
    }
  }

  /**
   * indicate an error if the specified Value is not of one of the specified types.
   * The program will terminate if the error condition is true.
   * 
   * @param val - the Value to test
   * @param opcode - the opcode being run
   * @param param  - the param value tested
   * @param types - one or more Value type bits that the Value must have
   */
  private void assertInvalidType(Value val, String opcode, String param, int types) {
    if (val == null) {
      exitError("(" + opcode + ") - " + param + " is NULL");
    } else if ((types & val.getType()) == 0) {
      exitError("(" + opcode + ") - " + param + " has invalid data type: " +
          DataConvert.showValueDataType(val.getType()));
    }
  }
  
  /**
   * indicate an error if the specified Reference or Array Value is not an Integer type.
   * The program will terminate if the error condition is true.
   * 
   * @param val - the Value for the reference or array
   * @param opcode - the opcode being run
   * @param param  - the param value tested
   */
  private void assertUnsignedIntValue(Value val, String opcode, String param) {
    if (val == null) {
      exitError("(" + opcode + ") - " + param + " Value is NULL");
    } else if (val.getValue() == null) {
      exitError("(" + opcode + ") - " + param + " Value contents is NULL");
    } else {
      String clstype = DataConvert.getClassType(val.getValue()); // NOTE: THIS USES REFLECTION
      if (!clstype.equals("java.lang.Integer")) {
        String type = DataConvert.showValueDataType(val.getType());
        exitError("(" + opcode + ") - " + param + " Value is not Integer - (" + type + ") class = " + clstype);
      } else if ((Integer) val.getValue() < 0) {
        exitError("(" + opcode + ") - " + param + " Value is < 0");
      }
    }
  }

  /**
   * returns the corresponding Opcode character specifying the corresponding data type passed.
   * 
   * @param type - the data type
   * @return string consisting of the initial character indicating the data type of the opcode.
   */
  private static String getOpcodeDataType(int type) {
    switch (type) {
      case Value.CHR:   return "C";
      case Value.INT8:  return "B";
      case Value.INT16: return "S";
      case Value.INT32: return "I";
      case Value.INT64: return "L";
      case Value.FLT:   return "F";
      case Value.DBL:   return "D";
    }
    return "x";
  }

  private static String shortenMethodName(String mtdName) {
    String shortName = mtdName;
    int offset = shortName.indexOf("(");
    if (offset > 0) {
      shortName = shortName.substring(0, offset);
    }
    offset = shortName.lastIndexOf("/");
    if (offset > 0) {
      shortName = shortName.substring(offset + 1);
    }
    return shortName;
  }
  
  private ArrayList<UserSymbolic> getSymbolicList(String mtdName) {
    if (symbolicMap.isEmpty() || !symbolicMap.containsKey(mtdName)) {
      return null;
    }
    
    ArrayList<UserSymbolic> methList = symbolicMap.get(mtdName);
    if (methList.isEmpty()) {
      symbolicMap.remove(mtdName);
      return null;
    }

    /* ----------debug head--------- *@*/
    DebugUtil.debugPrintInfo(threadId, DebugUtil.CONSTR, methList.size() + " symbolic entries for: " + mtdName);
    // ----------debug tail---------- */

    return methList;
  }

  private UserSymbolic getSymbolicEntry(ArrayList<UserSymbolic> methList, int slot, int opLine) {
    // search for parameter definition in the symbolic list
    for (UserSymbolic entry : methList) {
      String methComp = entry.methodName;

      /* ----------debug head--------- *@*/
      DebugUtil.debugPrintInfo(threadId, DebugUtil.CONSTR, "- entry = " + methComp + ", slot " + entry.param);
      // ----------debug tail---------- */
      
//      if (methComp.equals(mtdName.replace('.', '/')) && entry.param == slot) {
      if (entry.param == slot) {
        // if end == 0, use entire menthod range. else allow the line before start, since local var table
        // doesn't include the line in which the param is first loaded or stored, but we do.
        if (entry.ixEnd == 0 || (opLine + 1 >= entry.ixStart && opLine <= entry.ixEnd)) {
          /* ----------debug head--------- *@*/
          DebugUtil.debugPrintInfo(threadId, DebugUtil.CONSTR, "Found symbolic for " +
              shortenMethodName(methComp) + " (slot " + entry.param + ", line " + opLine +
              ") : type = " + DataConvert.showValueDataType(entry.type));
          // ----------debug tail---------- */

          return entry;
        }
      }
    }

    return null;
  }
  
  private void removeSymbolicEntry(ArrayList<UserSymbolic> methList, UserSymbolic entry) {
    if (methList != null) {
      String mtdName = entry.methodName;

      // if entry found, remove it. if no more entries for method, delete method list
      if (methList.contains(entry)) {
        methList.remove(entry);
        if (methList.isEmpty()) {
          symbolicMap.remove(mtdName);
        }
      }
    }
  }
  
  /**
   * Initializes the specified local parameter if it is symbolic but hasn't been initialized yet.
   * 
   * if the specified method parameter is found in the list of user-defined symbolic parameters,
   * the initial symbolic value will be returned and will be removed from the list so it won't
   * be initialized again.
   * 
   * If not found, it returns a null (not symbolic or already initialized).
   * 
   * @param slot    - param index of method to check
   * @param type    - the type of symbolic parameter
   * @param opLine  - the opcode line number within the method
   * @param val     - for REF and ARY types only, the concrete value
   * @return initial symbolic value if specified param is symbolic, NULL otherwise
   */
  private Value getInitialSymbolicParam(String mtdName, int slot, int type, int opLine, Value val) {
    Value symValue = null;
    com.microsoft.z3.Expr expr;

    // get the list of symbolics for the specified method
    ArrayList<UserSymbolic> methList = getSymbolicList(mtdName);
    if (methList == null) {
      return null;
    }

    UserSymbolic entry = getSymbolicEntry(methList, slot, opLine);
    if (entry == null) {
      return null;
    }

    // TODO: for now, we can't handle Multi-dim arrays as symbolic
    if (type == Value.MARY) {
      exitError("getInitialSymbolicParam: can't handle Multi-dimensional array as symbolic!");
    }
    
    // if we weren't specific about the type of array, use the value from the symbolic table def
    if (type == Value.ARY && (entry.type & Value.ARY) != 0) {
      type = entry.type;
    }

    if (entry.type != type) {
      /* ----------debug head--------- *@*/
      DebugUtil.debugPrintWarning(threadId, "Symbolic type does not match opcode access: " +
          DataConvert.showValueDataType(entry.type) + " != " +
          DataConvert.showValueDataType(type));
      // ----------debug tail---------- */

      type = entry.type;
    }

    // check for array types, since this bit can be set with several other bits
    if ((type & Value.ARY) != 0) {
      if (!val.isType(Value.ARY)) {
        exitError("getInitialSymbolicParam: concrete value is not ARY type");
      }
      
      /* ----------debug head--------- *@*/
      DebugUtil.debugPrintWarning(threadId, "Symbolic array being loaded!");
      // ----------debug tail---------- */
        
      Value[] combo = getArrayCombo(val, type);
      Value[] array = (Value[]) combo[0].getValue();
      int eltype = array[0].getType();
      int size = (Integer) combo[1].getValue();
      for (int ix = 0; ix < size; ix++) {
        // this will name each entry in the array by the name of the array folowed by "_N", where
        // N is the array index value.
        array[ix] = makeSymbolicPrimitive(entry.id + "_" + ix, eltype, z3Context);
      }

      /* ----------debug head--------- *@*/
      DebugUtil.debugPrintWarning(threadId, "Array replaced with symbolic entries, size = " + size);
      // ----------debug tail---------- */
          
      // TODO: this will not return a SYM type to the caller because it is the elements
      // that are marked as symbolic, not the array itself. If we want to mark the array
      // as well, uncomment the next line.
//      symValue = new Value(val.getValue(), Value.SYM | val.getType());

      // remove entry from list, since we have initialized this param
      methList.remove(entry);
      if (methList.isEmpty()) {
        symbolicMap.remove(mtdName);
      }
      return symValue;
    }

    // else, determine type of symbolic value
    symValue = makeSymbolicPrimitive(entry.id, type, z3Context);
    if (symValue != null) {
      // apply the fixed constraints for integer types
      applyFixedIntegerConstraints(symValue, type);
      
      // apply user constraints if any
      for (UserConstraint constraint : entry.constraint) {
        applyUserConstraint(constraint, symValue, type);
      }
    }

    // remove entry from list, since we have initialized this param
    methList.remove(entry);
    if (methList.isEmpty()) {
      symbolicMap.remove(mtdName);
    }
    
    return symValue;
  }
  
  /**
   * Applies the user-defined constraints to the local parameter if it is symbolic but hasn't
   * been initialized yet.
   * 
   * This is used for storing to a local parameter, where we have a value to set the parameter to.
   * In this case, we still need to apply the user-defined constraints if they have not been
   * applied (they are removed from the table when they have been), but we apply them to the
   * value that was passed.
   * 
   * @param value - the symbolic value to set the local parameter to
   * @param entry - the entry in the symbolics table for the parameter (null if not found)
   * @return the passed value with the constraints applied
   */
  private Value applyLocalParamConstraints(Value value, UserSymbolic entry) {
    if (entry != null) {
      // apply user constraints if any
      for (UserConstraint constraint : entry.constraint) {
        // just use the type specified in the Value
        int type = value.getType() & ~(Value.SYM);
        applyUserConstraint(constraint, value, type);
      }
    }
    return value;
  }
  
  /**
   * this makes symbolic entries from a unique string value and saves in a table.
   * if the field passed in was previously used, it will return the previously made value.
   * it is up to the user to guarantee that the field values are unique for unique entries.
   * 
   * @param threadId - the id of the thread
   * @param constraintName - key for constraint map entry = method name + "_" + param value
   * @param val - the symbolic parameter value
   * @param type - the type of constraint (this way we can restrict INT32 to smaller range)
   */
  private void applyUserConstraint(UserConstraint constraint, Value val, int type) {
    String compType = constraint.compareType;
    Value constraintVal;
    int iminval, imaxval, iconst;
    long lminval, lmaxval, lconst;
    try {
      switch (type) {
        case Value.INT8:
          if (!val.isType(type | Value.INT32)) {
            return;
          }
          iminval = Byte.MIN_VALUE;
          imaxval = Byte.MAX_VALUE;
          iconst = Integer.parseInt(constraint.value);
          if (iconst < iminval || iconst > imaxval) {
            /* ----------debug head--------- *@*/
            DebugUtil.debugPrintWarning(threadId, "User-constraint limit exceeded for type INT8: " + constraint.value);
            // ----------debug tail---------- */
            return;
          }
          constraintVal = new Value(iconst, type);
          break;
        case Value.INT16:
          if (!val.isType(type | Value.INT32)) {
            return;
          }
          iminval = Short.MIN_VALUE;
          imaxval = Short.MAX_VALUE;
          iconst = Integer.parseInt(constraint.value);
          if (iconst < iminval || iconst > imaxval) {
            /* ----------debug head--------- *@*/
            DebugUtil.debugPrintWarning(threadId, "User-constraint limit exceeded for type INT16: " + constraint.value);
            // ----------debug tail---------- */
            return;
          }
          constraintVal = new Value(Integer.parseInt(constraint.value), type);
          break;
        case Value.INT32:
          if (!val.isType(type)) {
            return;
          }
          iminval = Integer.MIN_VALUE;
          imaxval = Integer.MAX_VALUE;
          iconst = Integer.parseInt(constraint.value);
          if (iconst < iminval || iconst > imaxval) {
            /* ----------debug head--------- *@*/
            DebugUtil.debugPrintWarning(threadId, "User-constraint limit exceeded for type INT32: " + constraint.value);
            // ----------debug tail---------- */
            return;
          }
          constraintVal = new Value(Integer.parseInt(constraint.value), type);
          break;
        case Value.INT64:
          if (!val.isType(type)) {
            return;
          }
          lminval = Long.MIN_VALUE;
          lmaxval = Long.MAX_VALUE;
          lconst = Long.parseLong(constraint.value);
          if (lconst < lminval || lconst > lmaxval) {
            /* ----------debug head--------- *@*/
            DebugUtil.debugPrintWarning(threadId, "User-constraint limit exceeded for type INT64: " + constraint.value);
            // ----------debug tail---------- */
            return;
          }
          constraintVal = new Value(Long.parseLong(constraint.value), type);
          break;
        case Value.FLT:
          if (!val.isType(type)) {
            return;
          }
          constraintVal = new Value(Float.parseFloat(constraint.value), type);
          break;
        case Value.DBL:
          if (!val.isType(type)) {
            return;
          }
          constraintVal = new Value(Double.parseDouble(constraint.value), type);
          break;
        case Value.STR:
          // the constraint value is used for limiting the length of the string
          constraintVal = new Value(Long.parseLong(constraint.value), type);
          break;
        default:
          return;
      }
    } catch (NumberFormatException ex) {
      /* ----------debug head--------- *@*/
      DebugUtil.debugPrintWarning(threadId, "User-constraint limit exceeded for type " +
              DataConvert.showValueDataType(type) + ": " + constraint.value);
      // ----------debug tail---------- */
      return;
    }

    // convert input value & constraint to z3 expressions
    BitVecExpr expr = Util.getBitVector(val, z3Context);
    BitVecExpr exprConstr = Util.getBitVector(constraintVal, z3Context);
    
    // determine boolean z3 expression for comparison
    BoolExpr exprBool;
    String conType = "";
    switch (compType) {
      case "EQ":
        conType = "Eq";
        exprBool = z3Context.mkEq(expr, exprConstr);
        break;
      case "NE":
        conType = "Not + Eq";
        exprBool = z3Context.mkNot(z3Context.mkEq(expr, exprConstr));
        break;
      case "GT":
        conType = "BVSGT";
        exprBool = z3Context.mkBVSGT(expr, exprConstr);
        break;
      case "GE":
        conType = "BVSGE";
        exprBool = z3Context.mkBVSGE(expr, exprConstr);
        break;
      case "LT":
        conType = "BVSLT";
        exprBool = z3Context.mkBVSLT(expr, exprConstr);
        break;
      case "LE":
        conType = "BVSLE";
        exprBool = z3Context.mkBVSLE(expr, exprConstr);
        break;
      default:
        exitError("applyUserConstraint: Invalid User Constraint { " + compType + ", " + constraintVal + " }");
        return;
    }
    
    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "added user constraint: " + conType + " " + constraintVal);
    // ----------debug tail---------- */

    z3Constraints.add(exprBool);
  }

  /**
   * this makes symbolic entries from a unique string value and saves in a table.
   * if the field passed in was previously used, it will return the previously made value.
   * it is up to the user to guarantee that the field values are unique for unique entries.
   * 
   * @param threadId - the id of the thread
   * @param constraintName - key for constraint map entry = method name + "_" + param value
   * @param val - the symbolic parameter value
   */
  private void applyFixedIntegerConstraints(Value val, int type) {
    //int type = val.getType() & ~(Value.SYM); // mask off symbolic flag from data type
    Value minVal, maxVal;
    switch (type) {
      case Value.BLN:
        minVal = new Value(0, type);
        maxVal = new Value(1, type);
        break;
      case Value.CHR:
        minVal = new Value(Character.MIN_VALUE, type);
        maxVal = new Value(Character.MAX_VALUE, type);
        break;
      case Value.INT8:
        minVal = new Value(Byte.MIN_VALUE, type);
        maxVal = new Value(Byte.MAX_VALUE, type);
        break;
      case Value.INT16:
        minVal = new Value(Short.MIN_VALUE, type);
        maxVal = new Value(Short.MAX_VALUE, type);
        break;
      case Value.INT32:
        minVal = new Value(Integer.MIN_VALUE, type);
        maxVal = new Value(Integer.MAX_VALUE, type);
        break;
      case Value.INT64:
        minVal = new Value(Long.MIN_VALUE, type);
        maxVal = new Value(Long.MAX_VALUE, type);
        break;
      default:
        return;
    }

    // apply constraints
    z3Constraints.add(z3Context.mkBVSGE(Util.getBitVector(val, z3Context),
                                        Util.getBitVector(minVal, z3Context)));
    z3Constraints.add(z3Context.mkBVSLE(Util.getBitVector(val, z3Context),
                                        Util.getBitVector(maxVal, z3Context)));
    
    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "added integer constraints for " + DataConvert.showValueDataType(type)
        + ": BVSGE " + minVal.getValue() + " & BVSLE " + maxVal.getValue());
    // ----------debug tail---------- */
  }

  /**
   * updates the length of the specified combo array.
   * 
   * @param combo     - the array combo
   * @param conLength - the actual (concrete value) array length
   * @param arrObj    - the reference index value if entry was a reference
   */
//  int newLength = conArray == null ? 0 : conArray.length;
  private boolean updateValueArrayLength(Value[] combo, int conLength, Object arrObj) {
    if (combo == null || combo[0] == null || combo[1] == null) {
      return false;
    }

    // determine if the array size is unknown (combo[0] value will = 0) and if so, fix it here.
    int asnLength = (Integer) combo[1].getValue();

    boolean updated = false;
    if (asnLength == 0 && asnLength != conLength) {
      // array length does not match the concrete size - must have been unknown on creation
      // during a getField, so let's remedy the situation now.

      /* ----------debug head--------- *@*/
      debugPrintInfo(threadId, DebugUtil.ARRAYS, "Updating array length from " + asnLength + " to " + conLength);
      // ----------debug tail---------- */
        
      int type = combo[0].getType();
      Value[] origArray = (Value[]) combo[0].getValue();
      Value[] newArray = Util.makeValueArray(threadId, type, conLength, origArray[0]);

      if ((type & Value.MARY) != 0) {
        debugPrintError(threadId, "updateValueArrayLength: Invalid type - MARY is set");
      }
      
      // update the combo entries
      combo[0] = new Value((Value[]) newArray, type | Value.ARY);
      combo[1] = new Value(conLength, Value.INT32);
      updated = true;

      // if reference exists for array, update it as well
      if (DataConvert.getClassType(arrObj).equals("java.lang.Integer")) { // NOTE: THIS USES REFLECTION
        int arrCnt = (Integer) arrObj;
        ExecWrapper.updateComboValue(arrCnt, combo);

        /* ----------debug head--------- *@*/
        debugPrintArrayMap(threadId, arrCnt, combo);
        // ----------debug tail---------- */
      }
    }
    return updated;
  }
      
  /**
   * creates a combo from an array of objects and adds it to arrayMap.
   * 
   * The 'array' object can be one of the following:
   * - a symbolic value (must have valid 'size' and 'type' specified)
   * - an array of Values, with each Value entry defining the array entry (some or all can be
   *   symbolic entries. the 'size' can be specified as NULL and the actual size will be determined
   *   from the array.length field). The 'type' must be valid. 
   * - an array of primitives or objects (returned from an uninstrumented call). The 'size' should
   *   be specified as NULL to indicate this is not a Value array. The 'type' can be specified as 0
   *   if it is not known and it will infer the value from the class of the array type.
   * - NULL if we need to define a Value array but don't yet know the size. The 'size' should be
   *   specified as 1 initially and will be increased when we determine the actual size. We must
   *   know the data 'type' though.
   * 
   * @param array - the array of objects
   * @param size  - Value specifying the size of the array (may be symbolic)
   * @param type  - the type to use (0 if infer the type from the object)
   * @return a value containing a reference to the saved array object
   */
  private Value putArrayCombo(Object array, Value size, int type, boolean isSymbolic) {
    Value[] combo = new Value[2];
    int refType = Value.ARY;

    if ((type & Value.MARY) != 0) {
      /* ----------debug head--------- *@*/
      debugPrintError(threadId, "putArrayCombo: Invalid type - MARY is set");
      // ----------debug tail---------- */
    }
      
    if (array == null) {
      // this handles the null array case
      if (size == null || type == 0) {
        exitError("putArrayCombo: invalid params for null array");
      }
      combo[0] = new Value((Object[]) array, type | Value.ARY);
      combo[1] = size;
    } else if (isSymbolic) {
      // this handles the symbolic case
      if (size == null || type == 0) {
        exitError("putArrayCombo: invalid params for null array");
      }
      combo[0] = new Value(array, type | Value.SYM);
      combo[1] = size;
      refType |= Value.SYM;
    } else {
      // else, we have a non-symbolic array object
      // verify the object is an array type of some sort
      String clstype = DataConvert.getClassType(array); // NOTE: THIS USES REFLECTION
      if (!clstype.startsWith("[")) {
        exitError("putArrayCombo: entry is not array type: " + clstype);
      }
      // if params not defined, derrive them from object passed
      if (size == null && clstype.equals("[Ldanalyzer.executor.Value")) {
        // the size should only be derrived from Value type arrays
        // (could also do this for any object types, but not for primitives, since we can't
        // do a cast to Object[] for those)
        size = new Value(((Object[]) array).length, Value.INT32);
      }
      if (type == 0) {
        type = DataConvert.getValueDataType(clstype);
      }

      combo[0] = new Value(array, type & ~(Value.ARY | Value.MARY));
      combo[1] = size;
    }
    
    int arrCnt = ExecWrapper.putComboValue(combo);

    /* ----------debug head--------- *@*/
    debugPrintArrayMap(threadId, arrCnt, combo);
    // ----------debug tail---------- */

    return new Value(arrCnt, refType);
  }

  /**
   * gets the array combo value for the Value containing an array.
   * 
   * The 'array' value can be one of the following types:
   * - a combo value that contains 2 entries:
   *   combo[0] contains the actual Value array (array of Values that comprise the array entity)
   *   combo[1] contains the size of the array
   * - an Integer value that is a reference key value for finding the combo value in arrayMap.
   * 
   * Determines if the 'array' entry is a combo or a reference to a combo. If it is a reference
   * type, it gets the combo value from the arrayMap.
   * 
   * @param array - the Value that is specified to be an array
   * @param type  - the data type fro the array (0 if not known)
   * @return the corresponding combo[] value ([0] is the array itself and [1] contains the length)
   */
  private Value[] getArrayCombo(Value array, int type) {
    if (!array.isType(Value.ARY)) {
      /* ----------debug head--------- *@*/
      debugPrintWarning(threadId, "getArrayCombo: array is not type Value.ARY: " +
          DataConvert.showValueDataType(type));
      // ----------debug tail---------- */
    } 
    
    Value[] combo = ExecWrapper.getComboValue((Integer) array.getValue());
    
//    Value[] combo = null;
//    String classType = DataConvert.getClassType(array.getValue());
//    if (classType.equals("[Ldanalyzer.executor.Value")) {
//      // this is a combo array
//      combo = (Value[]) array.getValue();
//    } else if (classType.equals("java.lang.Integer")) {
//      // else this is a reference to a combo array
//      combo = (Value[]) ExecWrapper.getComboValue((Integer) array.getValue());
//    } else {
//      // unknown type
//      exitError("getArrayCombo: invalid combo type");
//    }

    // if the entry is NULL, create an initial array which will be expanded when we know the size
    if (combo == null) {
      /* ----------debug head--------- *@*/
      debugPrintInfo(threadId, DebugUtil.ARRAYS, "getArrayCombo: combo is null - creating initial array entry");
      // ----------debug tail---------- */
      
      if (type == 0) {
        type = Value.REF; // this is just in case no type was entered.
      }
      int length = 1; // init the array size to 1
      Value[] core = Util.makeValueArray(threadId, type, length, null);
      combo = new Value[2];
      combo[0] = new Value(core, type);
      combo[1] = new Value(0, Value.INT32); // set size to 0 to indicate we don't know it yet.
    }
    return combo;
  }

  /**
   * converts an array returned from an un-instrumented method into a combo value placed in
   * the array map and returns a reference Value for it.
   * 
   * @param val  - the array (primitives or Objects)
   * @param type - the data type for the array
   * 
   * @return a reference Value for the array that was placed in the map
   */
  private Value makeReturnArray(Object val, int type) {
    // simple array: create a Value-array from the array of primitives or Objects
    Value[] combo = Util.cvtValueArray(threadId, val, type);
    
    // save the combo entry in the map and get its assigned ref index
    int arrCnt = ExecWrapper.putComboValue(combo);

    /* ----------debug head--------- *@*/
    debugPrintArrayMap(threadId, arrCnt, combo);
    // ----------debug tail---------- */
    
    // return the array reference value
    return new Value(arrCnt, Value.INT32 | Value.ARY);
  }
  
  /**
   * This will replace a combo that is composed of an array of primitives or Objects into the
   * 'normal' Value-wrapped array type.
   * Arrays that are returned from uninstrumented methods are the unwrapped types, and are denoted
   * by having a null value for the size entry in the combo.
   * 
   * @param combo - the current combo value
   * @param type  - the data type for the array
   */
  private void repackCombo(Value[] combo, int type) {
    // make sure it is not already a Value array
    if (combo[1] == null) {
      Value[] newarr = Util.cvtValueArray(threadId, combo[0].getValue(), type);
      combo[0] = newarr[0];
      combo[1] = newarr[1];
    }
  }

  /**
   * creates the base combo array data for a mulit-dimensional array of the specified type.
   * 
   * @param dim  - an array of the size of each dimension of the array
   * @param type - the Value type of the data in the array
   * @return the reference value to the combo created
   */
  private Value makeNewArrayCombo(ArrayList<Integer> dim, int type) {
    int total = 1; // total number of array entries needed
    for (int ix = 0; ix < dim.size(); ix++) {
      total *= dim.get(ix);
    }

    Value[] core = new Value[total];
    for (int ix = 0; ix < total; ix++) {
      // generate the data value to place in the array
      Value entry;
      if (type == Value.REF) {
        entry = Util.makeNewRefValue(threadId, null);
      } else {
        entry = new Value (null, type);
      }

      core[ix] = entry;
    }
    
    /* ----------debug head--------- *@*/
    DebugUtil.debugPrintArrays(threadId, "  Created array of type " +
        DataConvert.showValueDataType(type) + ", length " + total);
    // ----------debug tail---------- */

    // create the array object entry in arrayMap and return reference to it
    Value size = new Value (total, Value.INT32);
    Value arrayVal = putArrayCombo(core, size, type, false);
    return arrayVal;
  }
  
  /**
   * creates the a mulit-dimensional array of the specified type.
   * 
   * @param arrayVal - the combo reference to the array data used by the multi-array
   * @param dim  - an array of the size of each dimension of the array
   * @param type - the Value type of the data in the array
   * @return the reference value to the multi-array entry created
   */
  private Value makeNewMultiArray(Value arrayVal, ArrayList<Integer> dim, int type) {
    /* ----------debug head--------- *@*/
    DebugUtil.debugPrintArrays(threadId, "makeNewMultiArray: type " +
        DataConvert.showValueDataType(type) + ", sizes: " + Arrays.toString(dim.toArray()));
    // ----------debug tail---------- */

    // determine the total array size and the number of references the main MultiArrayInfo will need
    if (dim.size() < 2) {
      exitError("makeNewMultiArray: invalid number of dimensions: " + dim.size());
      return null;
    }
    
    int total = 1; // total number of array entries needed
    for (int ix = 0; ix < dim.size(); ix++) {
      total *= dim.get(ix);
    }
    
    // create the MultiArrayInfo for the full array
    int refCnt = (Integer)arrayVal.getValue();
    int multiRef = ExecWrapper.putMultiArrayInfo(refCnt, type, dim.size(), 0);

    /* ----------debug head--------- *@*/
    DebugUtil.debugPrintArrays(threadId, "  Created new MultiArrayInfo[" + multiRef +
        "] = { Mref " + multiRef + ", Aref " + refCnt + ", type " + DataConvert.showValueDataType(type) +
        ", lev " + dim.size() + ", offset 0 }");
    DebugUtil.debugPrintArrays(threadId, "  Creating multi-array dimensions: " + dim.toString());
    // ----------debug tail---------- */

    // create the entry for the dimensions of the full array
    ExecWrapper.putMultiArraySizes(refCnt, dim);

    // create reference entries for all of the sub-arrays that can reference it
    int mult = 1;
    int offset = 0;
    int stepsize = total;
    // create sub array entries in reverse order of the levels defined
    for (int lev = dim.size() - 1; lev > 0; lev--) {
      int curdim = dim.get(lev);
      stepsize = stepsize / curdim;
      for (int ix = 0; ix < mult * curdim; ix++) {
        int index = ExecWrapper.addMultiArrayInfo(refCnt, type, lev, offset, multiRef);
        
        /* ----------debug head--------- *@*/
        DebugUtil.debugPrintArrays(threadId, "  Added MultiArrayInfo[" + index +
            "] = { Mref " + multiRef + ", Aref " + refCnt + ", type " + DataConvert.showValueDataType(type) +
            ", lev " + lev + ", offset " + offset + " } : L" + lev + "-" + ix + "/" + (mult * curdim));
        // ----------debug tail---------- */
        
        offset += stepsize;
      }
      mult *= curdim;
    }
    
    // return a Value referencing it
    Value val = new Value(multiRef, Value.MARY | Value.INT32);
    return val;
  }
  
  /**
   * makes a copy of the specified multi-dimensional array.
   * (references the same base array values, since this makes a copy of the reference only,
   * not a deep copy).
   * 
   * @param key - the multi-array reference value
   * @return the reference value to the multi-array entry created
   */
  private Value cloneMultiArray(int key) {
    // get the info for the current level of the multi-dimensional array
    ExecWrapper.MultiArrayInfo parentinfo = ExecWrapper.getMultiArrayInfo(key);
    if (parentinfo == null) {
      exitError("cloneMultiArray: multi-array reference not found for: " + key
          + " (max is " + ExecWrapper.getMultiSize() + ")");
      return null;
    }
    ArrayList<Integer> dimensions = ExecWrapper.getMultiArraySizes(parentinfo.arrayRef);
    if (dimensions == null) {
      exitError("cloneMultiArray: array reference not found for: " + parentinfo.arrayRef);
      return null;
    }
    if (dimensions.isEmpty()) {
      return null;
    }

    // get the array being cloned
    Value[] refcombo = getArrayCombo(new Value (parentinfo.arrayRef, Value.INT32), parentinfo.type);

    // create a new array reference and set its value equal to the previous
    Object array = refcombo[0].getValue();
    int arrtype = refcombo[0].getType();
    Value size = refcombo[1];
    boolean isSymbolic = array instanceof com.microsoft.z3.ArrayExpr;
    Value arrayVal = putArrayCombo(array, size, arrtype, isSymbolic);

    // now create the multi-array entries
    return makeNewMultiArray(arrayVal, dimensions, parentinfo.type);
  }
  
  /**
   * gets an item from the current level of a multi-dimension array.
   * 
   * @param key - the multi-array reference value (can be a sub-array element from the main array)
   * @param index - the index into the current dimension of the array
   * @return the entry of the multi-array, which can be either a reference to a multi-array 1 dimension
   *         less than the previous one if the entry array has a dimension greater than 1.
   */
  private Value getMultiArrayElement(int key, int index) {
    Value element = null;

    // get the info for the current level of the multi-dimensional array
    ExecWrapper.MultiArrayInfo parentinfo = ExecWrapper.getMultiArrayInfo(key);
    if (parentinfo == null) {
      exitError("getMultiArrayElement: multi-array reference not found for: " + key
          + " (max is " + ExecWrapper.getMultiSize() + ")");
      return null;
    }
    ArrayList<Integer> dimensions = ExecWrapper.getMultiArraySizes(parentinfo.arrayRef);
    if (dimensions == null) {
      exitError("getMultiArrayElement: array reference not found for: " + parentinfo.arrayRef);
      return null;
    }
    if (!dimensions.isEmpty()) {
      int type = parentinfo.type;
      int dim = parentinfo.dim - 1;
      if (dim < 0 || dim > dimensions.size()) {
        exitError("getMultiArrayElement: invalid dimension value: " + dim);
        return null;
      } else if (dim > 0) {
        // return another sub-level of the multi-array. need to determine the reference value for it
        // that is based on the reference of the initial entry
        int refix = key + 1 + index;
        int level = dimensions.size() - dim;
        if (level >= 2) {
          int mult = 1;
          for (int ix = level - 2; ix >= 0; ix--) {
            mult *= dimensions.get(ix);
          }
          refix += mult;
        }
        
        ExecWrapper.MultiArrayInfo info = ExecWrapper.getMultiArrayInfo(refix);
        if (info == null) {
          exitError("getMultiArrayElement: sub-array reference not found for: " + refix);
          return null;
        }

        element = new Value(refix, Value.MARY | Value.INT32);

        /* ----------debug head--------- *@*/
        DebugUtil.debugPrintArrays(threadId, "  getMultiArrayElement: MULTI[" + refix + "] has " +
            info.dim + " dimensions and points to ARRAY[" + info.arrayRef + "][" + info.offset + "]");
        // ----------debug tail---------- */
      } else {
        // return the array element value
        Value arrayRef = new Value(parentinfo.arrayRef, Value.ARY | Value.INT32);
        Value[] combo = getArrayCombo(arrayRef, type);
        if (combo == null) {
          exitError("getMultiArrayElement: combo is null");
          return null;
        }

        // calculate the number of array entries in the sub-element we are returning.
        // use this to determine the offset into the full array.
        int size = 1;
        for (int ix = dimensions.size() - dim; ix < dimensions.size(); ix++) {
          size *= dimensions.get(ix);
        }
        int offset = parentinfo.offset + index * size;

        Value[] array = (Value[]) combo[0].getValue();
        element = array[offset];

        /* ----------debug head--------- *@*/
        DebugUtil.debugPrintArrays(threadId, "  getMultiArrayElement: MULTI[" + key + "][" + index +
            "] = ARRAY[" + arrayRef + "][" + offset + "] = " + element.getValue());
        // ----------debug tail---------- */
      }
    }
    
    return element;
  }

  /**
   * writes an item to the current level of a multi-dimension array.
   * 
   * @param key - the multi-array reference value (can be a sub-array element from the main array)
   * @param index - the index into the current dimension of the array
   * @param element - the entry to set the specified index value to
   */
  private void setMultiArrayElement(int key, int index, Value element) {
    // get the info for the multi-dimensional array
    ExecWrapper.MultiArrayInfo info = ExecWrapper.getMultiArrayInfo(key);
    if (info == null) {
      exitError("setMultiArrayElement: multi-array reference not found for: " + key
          + " (max is " + ExecWrapper.getMultiSize() + ")");
      return;
    }
    ArrayList<Integer> dimensions = ExecWrapper.getMultiArraySizes(info.arrayRef);
    if (dimensions == null) {
      exitError("setMultiArrayElement: array reference not found for: " + info.arrayRef);
      return;
    }
    if (!dimensions.isEmpty()) {
      int type = info.type;
      if (info.dim != 1) {
        exitError("setMultiArrayElement: element specified is an array of dimension " + info.dim);
        return;
      }

      // return the array element value
      Value arrayRef = new Value(info.arrayRef, Value.ARY | Value.INT32);
      Value[] combo = getArrayCombo(arrayRef, type);
      if (combo == null) {
        exitError("setMultiArrayElement: combo is null at ARRAY[" + info.arrayRef + "]");
        return;
      }

      // calculate the number of array entries in the sub-element we are returning.
      // use this to determine the offset into the full array.
      int offset = info.offset + index;
      Value[] array = (Value[]) combo[0].getValue();
      array[offset] = element;

      /* ----------debug head--------- *@*/
      DebugUtil.debugPrintArrays(threadId, "  setMultiArrayElement: MULTI[" + key + "][" + index +
          "] = ARRAY[" + info.arrayRef + "][" + offset + "] = " + element.getValue());
      // ----------debug tail---------- */
    }
  }
  
  private Value makeReturnMultiArray(Object val, int type, int depth) {
    // extract the size of each dimension from the concrete object
    ArrayList<Integer> dimArray = new ArrayList<>();
    Object[] array = (Object[]) val; // first time thru, convert object to array of level 1
    for (int ix = 0; ix < depth; ix++) {
      if (ix > 0) { // subsequent times thru, convert current object into array of level 1
        array = (Object[]) array[0];
      }
      Integer dimSize = array.length;
      dimArray.add(dimSize);

      /* ----------debug head--------- *@*/
      debugPrintInfo(threadId, DebugUtil.ARRAYS, "makeReturnMultiArray: level " + ix + ": " + dimSize);
      // ----------debug tail---------- */
    }

    Value arrayVal = makeNewArrayCombo(dimArray, type);
    Value multiArray = makeNewMultiArray(arrayVal, dimArray, type);
    return multiArray;
  }
  
  private void putSymbolicExpression(String symbolicParam, Expr ex) {
    exprMap.put(symbolicParam, ex);
  }

  private Expr makeLong(String name, int dataType) {
    Expr expr = z3Context.mkBVConst(name, Util.getBVLength(dataType));
    putSymbolicExpression(name, expr);
    return expr;
  }

  private Expr makeFloat(String name) {
    return z3Context.mkRealConst(name);
  }

  private Value makeSymbolicPrimitive(String paramName, int type, Context z3Context) {
    Value symValue = null;
    com.microsoft.z3.Expr expr;
    switch(type) {
      case Value.DBL:
      case Value.FLT:
        symValue = new Value(makeFloat(paramName), Value.SYM | type);
        break;
      case Value.CHR:
      case Value.BLN:
      case Value.INT8:
      case Value.INT16:
      case Value.INT32:
      case Value.INT64:
        symValue = new Value(makeLong(paramName, type), Value.SYM | type);
        break;
      case Value.STR:
        expr = z3Context.mkConst(paramName, z3Context.getStringSort());
        symValue = new Value(expr, Value.SYM | type);
        break;
      case Value.REF:
        expr = z3Context.mkString(paramName);
//        expr = Util.getExpression(val, z3Context);
        symValue = new Value(expr, Value.SYM | type);
        break;
    }
    
    return symValue;
  }
  
  private void addArrayIndexConstraints(BitVecExpr index, int length, int opcodeOffset) {
    BitVecExpr symLen = z3Context.mkBV(length, 32);
    BitVecExpr symZero = z3Context.mkBV(0, 32);
    
    BoolExpr expr1 = z3Context.mkBVSGE(index, symLen);
    BoolExpr expr2 = z3Context.mkBVSLT(index, symZero);
    BoolExpr expr3 = z3Context.mkOr(expr1, expr2);
    
    int num = numArrayConstraints++;
    BoolExpr assump[] = new BoolExpr[0]; // no assumptions here
    String formula = z3Context.benchmarkToSMTString("benchmark_array_constraint_" + num, "", "unknown", "", assump, expr3);
    
    /* ----------debug head--------- *@*/
    debugPrintSolve(threadId, "Table entry[" + threadId + "][" + constraintCount++ + "]:");
    debugPrintSolve(threadId, formula);
    debugPrintSolve(threadId, "-------------------------");
    // ----------debug tail---------- */

    // send info to solver
    boolean pathsel = false;
    solverPort.sendMessage(threadId, currentStackFrame.getMethodName(), opcodeOffset, pathsel,
        ConstraintType.ARRAY.toString(), formula);
  }

  private void solvePC(int threadId, int opcodeOffset, boolean pathsel, ConstraintType ctype) {
    // add up the constraints and invert the last one to find a new path
    // first, start with the last constraint negated (in case there is only 1 constraint)
    int lastix = z3Constraints.size() - 1;
    BoolExpr lastExpr = z3Constraints.get(lastix);
    BoolExpr expr = z3Context.mkNot(lastExpr);

    // if more than 1, 'and' all the prior constraints plus the last negated one
    if (lastix > 0) {
      BoolExpr initexpr = z3Constraints.get(0);
      for (int i = 1; i < lastix; i++) {
        initexpr = z3Context.mkAnd(initexpr, z3Constraints.get(i));
      }
      expr = z3Context.mkAnd(initexpr, expr);
    }

    // add optimization
    z3Optimize.Push();
    z3Optimize.Add(expr);
    String formula = z3Optimize.toString();
    z3Optimize.Pop();
//    // Original version: let's put this in a String format for saving to mongo
//    BoolExpr assump[] = new BoolExpr[0]; // no assumptions here
//    String formula = z3Context.benchmarkToSMTString("benchmark", "", "unknown", "", assump, expr);
    
    /* ----------debug head--------- *@*/
    debugPrintSolve(threadId, "Table entry[" + threadId + "][" + constraintCount++ + "]:");
    debugPrintSolve(threadId, formula);
    debugPrintSolve(threadId, "-------------------------");
    // ----------debug tail---------- */

    // send info to solver
    solverPort.sendMessage(threadId, currentStackFrame.getMethodName(), opcodeOffset, pathsel,
        ctype.toString(), formula);
  }
  
  private void dumpStackInfo(boolean error) {
    String contents = "";
    contents += "================= Stack trace ====================" + NEWLINE;
    if (executionStack.isEmpty()) {
      contents += "stack is empty" + NEWLINE;
    } else {
      for (int pos = executionStack.size() - 1; pos >= 0; pos--) {
        if (executionStack.get(pos) == null) {
          contents += "[" + pos + "] <null>" + NEWLINE;
        } else {
          contents += "[" + pos + "] " + executionStack.get(pos).getMethodName() + NEWLINE;
        }
      }
    }
    if (error) {
      contents += "============= Current frame contents =============" + NEWLINE;
      if (currentStackFrame == null) {
        contents += "currentStackFrame is NULL" + NEWLINE;
      } else {
        contents += currentStackFrame.dumpStack() + NEWLINE;
      }
    }
    contents += "==================================================" + NEWLINE;
    debugPrintDump(threadId, contents);
  }
  
//  /**
//   * this makes symbolic entries from a unique string value and saves in a table.
//   * if the field passed in was previously used, it will return the previously made value.
//   * it is up to the user to guarantee that the field values are unique for unique entries.
//   * 
//   * @param fieldName - the identifier for the symbolic parameter
//   * @return the symbolic parameter
//   */
//  public static Value makeSymbolicValue(String fieldName) {
//    Value symVal = symbolicConfigMap.get(fieldName);
//    if (symVal == null) {
//      BitVecExpr expr = getZ3Context().mkBVConst(fieldName, 64);
//      getZ3Context().mkBVSLT(expr, getZ3Context().mkBV("" + 2147483648l, 64));
//      getZ3Context().mkBVSGT(expr, getZ3Context().mkBV("" + (-2147483649l), 64));
//      putSymbolicExpression(fieldName, expr);
//
//      symVal = new Value(expr, Value.SYM | Value.INT32);
//
//      symbolicConfigMap.put(fieldName, symVal);
//    }
//    return symVal;
//  }
//  
//  public void retrieveMethodSymbolic(Object conObj, int x, int y) {
//    /* ----------debug head--------- *@*/
//    debugPrintCommand(threadId, "retrieveMethodSymbolic", x, y, conObj);
//    // ----------debug tail---------- */
//
//    // create a unique symbolic value for each entry
//    // for now we keep the key simple - it is the x and y coordinates
//    Value symVal = makeSymbolicValue(x + "_" + y);
//
//    // push the result onto our stack.
//    // need to push this item above the next 3, which will be popped off by the
//    // synchronizeStackFrame call. It's response will not be pushed onto our stack, so we will
//    // effectively be replacing its return value with this one.
//    Value arg_Y = currentStackFrame.popValue();
//    Value arg_X = currentStackFrame.popValue();
//    Value arg_Obj = currentStackFrame.popValue();
//    currentStackFrame.pushValue(symVal);
//    currentStackFrame.pushValue(arg_Obj);
//    currentStackFrame.pushValue(arg_X);
//    currentStackFrame.pushValue(arg_Y);
//  }
  
  //================================================================
  // INSTRUMENTER METHODS
  //================================================================
  
  public void unimplementedOpcode(String opcode) {
    /* ----------debug head--------- *@*/
    debugPrintWarning(threadId, opcode + " - unimplementedOpcode");
    // ----------debug tail---------- */
  }

  public void noActionOpcode(String opcode) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - noActionOpcode");
    // ----------debug tail---------- */
  }
  
  // who is to blame: zzk, dmcd2356
  public void initializeExecutor(String[] argv) {
    // it is only allowed to initialize once (make sure the selected thread is running)
    if (initialized) {
      return;
    }
    if (currentStackFrame == null) {
      System.err.println("initializeExecutor: currentStackFrame is NULL");
      System.exit(1);
    }

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, "initializeExecutor", currentStackFrame.getMethodName(), argv.length);
    // ----------debug tail---------- */

    initialized = true;
    
    // make a concrete array of strings to save main arguments
    Value arrayVal = putArrayCombo(argv, null, 0, false);
    currentStackFrame.storeLocalVariable(0, arrayVal);
    
    z3Context = new Context();
    z3Optimize = z3Context.mkOptimize();
  }
  
  public void establishStackFrame(String fullName, int paramCnt) {
//  public boolean establishStackFrame(String fullName, int paramCnt, int maxLocals) {
//    // get the symbolic list and thread selection or run the GUI to get them
//    getSymbolicSelections(fullName);
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, "establishStackFrame", fullName, paramCnt);
    debugPrintCallEntry(threadId, fullName);
    // ----------debug tail---------- */

    // No longer need to handle special case, since <clinit> method is now tracked by agent.
    // Otherwise, we needed to create a new stack frame here and push the prev one.

    // set the method name of the current stack frame
    // (the static initializer doesn't create a new frame though)
    if (currentStackFrame != null) {
      currentStackFrame.setMethodName(fullName);
    }
  }

  public void newStringObject() {
    String opcode = "NEW";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - newStringObject", "java/lang/String");
    // ----------debug tail---------- */
    
    // add new entry to objectMap
    Map<String, Value> newMap = new HashMap<>();
    int objCnt = ExecWrapper.putReferenceObject(newMap);

    /* ----------debug head--------- *@*/
    debugPrintObjectMap(threadId, objCnt, newMap);
    // ----------debug tail---------- */
    
    // now, the object is uninitialized
    Value ref = new Value(objCnt, Value.STR);
    currentStackFrame.pushValue(ref);
  }  
  
  // who is to blame: zzk
  public void newObject(String clsName) {
    String opcode = "NEW";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - newObject", clsName);
    // ----------debug tail---------- */
    
    // add new entry to objectMap
    Map<String, Value> newMap = new HashMap<>();
    int objCnt = ExecWrapper.putReferenceObject(newMap);

    /* ----------debug head--------- *@*/
    debugPrintObjectMap(threadId, objCnt, newMap);
    // ----------debug tail---------- */
    
    // now, the object is uninitialized
    Value ref = new Value(objCnt, Value.REF);
    currentStackFrame.pushValue(ref);
  }  

  public void loadConstant(int val) {
    String opcode = "ICONST";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadConstant <int>", val);
    // ----------debug tail---------- */
    
    currentStackFrame.pushValue(new Value(val, Value.INT32));
  }

  public void loadConstant(long val) {
    String opcode = "LCONST";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadConstant <long>", val);
    // ----------debug tail---------- */
    
    currentStackFrame.pushValue(new Value(val, Value.INT64));
  }

  public void loadConstant(float val) {
    String opcode = "FCONST";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadConstant <float>", val);
    // ----------debug tail---------- */
    
    currentStackFrame.pushValue(new Value(val, Value.FLT));
  }

  public void loadConstant(double val) {
    String opcode = "DCONST";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadConstant <double>", val);
    // ----------debug tail---------- */
    
    currentStackFrame.pushValue(new Value(val, Value.DBL));
  }
  
  public void loadConstant(Object val) {
    String opcode = "ACONST";
    
    Value newval = null;
    String typstr = DataConvert.getClassType(val); // NOTE: THIS USES REFLECTION
    if (typstr.equals("java.lang.String")) {
      // string object
      newval = new Value(val, Value.STR);
    } else {
      // reference object:
      newval = Util.makeNewRefValue(threadId, val);
    }

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadConstant <" + typstr + ">",
        val != null ? val.toString() : "<null>");
    // ----------debug tail---------- */
    
    currentStackFrame.pushValue(newval);
  }

  public void loadConstant(String val) {
    String opcode = "LDC";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadConstant <java.lang.String>", val);
    // ----------debug tail---------- */
    
    // TODO: should we use a Value.REF | Value.STR and create a field map?
    currentStackFrame.pushValue(new Value(val, Value.STR));
  }

  public void pushAsInteger(String opcode, int val) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - pushAsInteger", val);
    // ----------debug tail---------- */
    
    currentStackFrame.pushValue(new Value(val, Value.INT32));
  }
  
  // who is to blame: danielbala
  // who blames him: zzk
  public void loadLocalReference(Object conObj, int index, int opline) {
    String opcode = "ALOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadLocalReference", index);
    // ----------debug tail---------- */
    
    String typestr = DataConvert.getClassType(conObj); // NOTE: THIS USES REFLECTION
    int type = DataConvert.getValueDataType(typestr);

    /* ----------debug head--------- *@*/
    debugPrintInfo(threadId, DebugUtil.OBJECTS, "  object = " + typestr + ": type = " +
        DataConvert.showValueDataType(type));
    // ----------debug tail---------- */
    
    // use the concrete value if the entry on the stack is null
    Value val = currentStackFrame.getLocalVariable(index); 
    if (val == null) { // TODO: should this ever happen?
      debugPrintWarning(threadId, "loadLocalReference: null entry on stack");
      val = new Value(conObj, type);
      currentStackFrame.storeLocalVariable(index, val);
    }
    
    // check if the local parameter was defined as symbolic by the user (REF, STR, ARY, MARY)
    String mtdName = currentStackFrame.getMethodName();
    if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
      Value symbolicVal = getInitialSymbolicParam(mtdName, index, val.getType(), opline, val);
      if (symbolicVal != null) {
        if (val.isType(Value.ARY | Value.MARY)) {
          // symbolic array
          currentStackFrame.pushValue(val);
        } else {
          // symbolic reference or string
          currentStackFrame.storeLocalVariable(index, symbolicVal);
          currentStackFrame.pushValue(symbolicVal);
        }
        return;
      }
    }

    // reference is NOT symbolic...
    if (val.getValue() == null && val.isType(Value.REF)) {
      // if it is a null reference value, use the concrete value
      Integer objCnt = ExecWrapper.getConcreteObject(conObj);
      val = new Value(objCnt, Value.REF);
      currentStackFrame.storeLocalVariable(index, val);
    }

    currentStackFrame.pushValue(val);
  }
  
  public void loadDouble(int index, int opline) {
    String opcode = "DLOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadDouble", index);
    // ----------debug tail---------- */
    
    // check if no symbolics for this method
    int type = Value.DBL;
    String mtdName = currentStackFrame.getMethodName();
    if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
      // check if the local parameter is defined as symbolic by the user. if so, get its initial value
      Value symbolicVal = getInitialSymbolicParam(mtdName, index, type, opline, null);
      if (symbolicVal != null) {
        currentStackFrame.storeLocalVariable(index, symbolicVal);
        currentStackFrame.pushValue(symbolicVal);

        // TODO: bit-vector symbolic value?
        //Expression expr = new 
        //Value symVal = new Value(expr, Value.SYM | Value.DBL);
        //currentStackFrame.storeLocalVariable(symVal);
        //currentStackFrame.pushValue(symVal);
        return;
      }
    }

    // not symbolic
    currentStackFrame.loadLocalVariable(index, type);
  }

  public void loadFloat(int index, int opline) {
    String opcode = "FLOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadFloat", index);
    // ----------debug tail---------- */
    
    // check if no symbolics for this method
    int type = Value.FLT;
    String mtdName = currentStackFrame.getMethodName();
    if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
      // check if the local parameter is defined as symbolic by the user. if so, get its initial value
      Value symbolicVal = getInitialSymbolicParam(mtdName, index, type, opline, null);
      if (symbolicVal != null) {
        currentStackFrame.storeLocalVariable(index, symbolicVal);
        currentStackFrame.pushValue(symbolicVal);
        return;
      }
    }
    
    // not symbolic
    currentStackFrame.loadLocalVariable(index, type);
  }

  public void loadInteger(int index, int opline) {
    String opcode = "ILOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadInteger", index);
    // ----------debug tail---------- */
    
    int type = Value.INT32;
    Value val = currentStackFrame.getLocalVariable(index);
    if (val != null) {
      type = val.getType();
    }
    
    // check if no symbolics for this method
    String mtdName = currentStackFrame.getMethodName();
    if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
      // check if the local parameter is defined as symbolic by the user. if so, get its initial value
      Value symbolicVal = getInitialSymbolicParam(mtdName, index, type, opline, null);
      if (symbolicVal != null) {
        currentStackFrame.storeLocalVariable(index, symbolicVal);
        currentStackFrame.pushValue(symbolicVal);
        return;
      }
    }

    // not symbolic
    currentStackFrame.loadLocalVariable(index, type);
  }

  public void loadLong(int index, int opline) {
    String opcode = "LLOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadLong", index);
    // ----------debug tail---------- */
    
    // check if no symbolics for this method
    int type = Value.INT64;
    String mtdName = currentStackFrame.getMethodName();
    if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
      // check if the local parameter is defined as symbolic by the user. if so, get its initial value
      Value symbolicVal = getInitialSymbolicParam(mtdName, index, type, opline, null);
      if (symbolicVal != null) {
        currentStackFrame.storeLocalVariable(index, symbolicVal);
        currentStackFrame.pushValue(symbolicVal);
        return;
      }
    }

    // not symbolic
    currentStackFrame.loadLocalVariable(index, type);
  }

  public void storeLocalReference(Object conObject, int index, int opline) {
    String opcode = "ASTORE";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - storeLocalReference", index);
    // ----------debug tail---------- */
    
    // we'd better have an entry on the stack - it is what we are wanting to store!
    if (currentStackFrame.isEmpty()) {
      exitError("storeLocalReference: stack is empty!");
      return;
    }
    
    Value value = currentStackFrame.popValue();
    assertValueNonNull(value, opcode, "value");

    // if the ref type is really an array, don't replace with symbolic
    if (value.isType(Value.MARY | Value.ARY)) {
      currentStackFrame.storeLocalVariable(index, value);
      return;
    }
      
    // check if local param is symbolic and whether it is in the table of symbolics.
    // (if current value is NULL, we can't tell the type, so need to get it from table)
    Value local = currentStackFrame.getLocalVariable(index);
    boolean isLocalSymb = (local == null) ? false : local.isType(Value.SYM);

    UserSymbolic entry = null;
    String mtdName = currentStackFrame.getMethodName();
    ArrayList<UserSymbolic> methList = getSymbolicList(mtdName);
    if (methList != null) {
      entry = getSymbolicEntry(methList, index, opline);
    }

    // check for symbolic case
    if (value.isType(Value.SYM)) {
      // if input is symbolic, apply user constraints (if any) to it and save as local param
      value = applyLocalParamConstraints(value, entry);
      removeSymbolicEntry(methList, entry); 
    } else if (entry != null || isLocalSymb) {
      // else if local param is symbolic, convert concrete input value to a symbolic
      // and apply user constraints if any
      if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
        // check if the local parameter is defined as symbolic by the user. if so, get its initial value
        Value symValue = getInitialSymbolicParam(mtdName, index, value.getType(), opline, null);
        if (symValue != null) {
          value = symValue;
        }
      }
    } else {
      // not symbolic, if it is reference type, add entry to concrete list
      if (value.isType(Value.REF) && value.getValue() != null) {
        // verify the index entry is an Integer type
        assertUnsignedIntValue(value, opcode, "objectMap index");
        int objCnt = (Integer)value.getValue();
        boolean added = ExecWrapper.putConcreteObject(conObject, objCnt);

        /* ----------debug head--------- *@*/
        if (added) {
          debugPrintObjects(threadId, "  OBJECT[" + objCnt + "] added to CONCRETE");
        }
        // ----------debug tail---------- */
      }
    }

    // update the local param
    currentStackFrame.storeLocalVariable(index, value);
  }
  
  public void storeDouble(int index, int opline) {
    String opcode = "DSTORE";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - storeDouble", index);
    // ----------debug tail---------- */
    
    int type = Value.DBL;
    Value value = currentStackFrame.popValue();
    assertValueNonNull(value, opcode, "value");
    
    // check if local param is symbolic and whether it is in the table of symbolics.
    // (if current value is NULL, we can't tell the type, so need to get it from table)
    Value local = currentStackFrame.getLocalVariable(index);
    boolean isLocalSymb = (local == null) ? false : local.isType(Value.SYM);

    UserSymbolic entry = null;
    String mtdName = currentStackFrame.getMethodName();
    ArrayList<UserSymbolic> methList = getSymbolicList(mtdName);
    if (methList != null) {
      entry = getSymbolicEntry(methList, index, opline);
    }

    // check for symbolic case
    if (value.isType(Value.SYM)) {
      // if input is symbolic, apply user constraints (if any) to it and save as local param
      value = applyLocalParamConstraints(value, entry);
      removeSymbolicEntry(methList, entry); 
    } else if (entry != null || isLocalSymb) {
      // else if local param is symbolic, convert concrete input value to a symbolic
      // and apply user constraints if any
      if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
        // check if the local parameter is defined as symbolic by the user. if so, get its initial value
        Value symValue = getInitialSymbolicParam(mtdName, index, type, opline, null);
        if (symValue != null) {
          value = symValue;
        }
      }
    }

    // otherwise value and local param are concrete, just update local
    currentStackFrame.storeLocalVariable(index, value);
  }

  public void storeFloat(int index, int opline) {
    String opcode = "FSTORE";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - storeFloat", index);
    // ----------debug tail---------- */
    
    int type = Value.FLT;
    Value value = currentStackFrame.popValue();
    assertValueNonNull(value, opcode, "value");

    // check if local param is symbolic and whether it is in the table of symbolics.
    // (if current value is NULL, we can't tell the type, so need to get it from table)
    Value local = currentStackFrame.getLocalVariable(index);
    boolean isLocalSymb = (local == null) ? false : local.isType(Value.SYM);

    UserSymbolic entry = null;
    String mtdName = currentStackFrame.getMethodName();
    ArrayList<UserSymbolic> methList = getSymbolicList(mtdName);
    if (methList != null) {
      entry = getSymbolicEntry(methList, index, opline);
    }

    // check for symbolic case
    if (value.isType(Value.SYM)) {
      // if input is symbolic, apply user constraints (if any) to it and save as local param
      value = applyLocalParamConstraints(value, entry);
      removeSymbolicEntry(methList, entry); 
    } else if (entry != null || isLocalSymb) {
      // else if local param is symbolic, convert concrete input value to a symbolic
      // and apply user constraints if any
      if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
        // check if the local parameter is defined as symbolic by the user. if so, get its initial value
        Value symValue = getInitialSymbolicParam(mtdName, index, type, opline, null);
        if (symValue != null) {
          value = symValue;
        }
      }
    }

    // otherwise value and local param are concrete, just update local
    currentStackFrame.storeLocalVariable(index, value);
  }

  public void storeInteger(int index, int opline) {
    String opcode = "ISTORE";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - storeInteger", index);
    // ----------debug tail---------- */
    
    Value value = currentStackFrame.popValue();
    assertValueNonNull(value, opcode, "value");
    int type = value.getType();

    // check if local param is symbolic and whether it is in the table of symbolics.
    // (if current value is NULL, we can't tell the type, so need to get it from table)
    Value local = currentStackFrame.getLocalVariable(index);
    boolean isLocalSymb = (local == null) ? false : local.isType(Value.SYM);

    UserSymbolic entry = null;
    String mtdName = currentStackFrame.getMethodName();
    ArrayList<UserSymbolic> methList = getSymbolicList(mtdName);
    if (methList != null) {
      entry = getSymbolicEntry(methList, index, opline);
    }

    // check for symbolic case
    if (value.isType(Value.SYM)) {
      // if input is symbolic, apply user constraints (if any) to it and save as local param
      value = applyLocalParamConstraints(value, entry);
      removeSymbolicEntry(methList, entry); 
    } else if (entry != null || isLocalSymb) {
      // else if local param is symbolic, convert concrete input value to a symbolic
      // and apply user constraints if any
      if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
        // check if the local parameter is defined as symbolic by the user. if so, get its initial value
        Value symValue = getInitialSymbolicParam(mtdName, index, type, opline, null);
        if (symValue != null) {
          value = symValue;
        }
      }
    }

    // otherwise value and local param are concrete, just update local
    currentStackFrame.storeLocalVariable(index, value);
  }

  public void storeLong(int index, int opline) {
    String opcode = "LSTORE";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - storeLong", index);
    // ----------debug tail---------- */
    
    int type = Value.INT64;
    Value value = currentStackFrame.popValue();
    assertValueNonNull(value, opcode, "value");

    // check if local param is symbolic and whether it is in the table of symbolics.
    // (if current value is NULL, we can't tell the type, so need to get it from table)
    Value local = currentStackFrame.getLocalVariable(index);
    boolean isLocalSymb = (local == null) ? false : local.isType(Value.SYM);

    UserSymbolic entry = null;
    String mtdName = currentStackFrame.getMethodName();
    ArrayList<UserSymbolic> methList = getSymbolicList(mtdName);
    if (methList != null) {
      entry = getSymbolicEntry(methList, index, opline);
    }

    // check for symbolic case
    if (value.isType(Value.SYM)) {
      // if input is symbolic, apply user constraints (if any) to it and save as local param
      value = applyLocalParamConstraints(value, entry);
      removeSymbolicEntry(methList, entry); 
    } else if (entry != null || isLocalSymb) {
      // else if local param is symbolic, convert concrete input value to a symbolic
      // and apply user constraints if any
      if (!symbolicMap.isEmpty() && symbolicMap.containsKey(mtdName)) {
        // check if the local parameter is defined as symbolic by the user. if so, get its initial value
        Value symValue = getInitialSymbolicParam(mtdName, index, type, opline, null);
        if (symValue != null) {
          value = symValue;
        }
      }
    }

    // otherwise value and local param are concrete, just update local
    currentStackFrame.storeLocalVariable(index, value);
  }

  public void loadReferenceFromArray(Object conObj, int index, int opline) {
    String opcode = "AALOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - loadReferenceFromArray", index);
    // ----------debug tail---------- */
    
    currentStackFrame.popValue(); // discard the possibly symbolic index value
    Value array = currentStackFrame.popValue();
    assertValueNonNull(array, opcode, "array");
    
    // check for multi-array
    if (array.isType(Value.MARY)) {
      Integer key = (Integer) array.getValue();
      if (key == null) {
        exitError("loadReferenceFromArray: Multi-array key value was not defined");
      }
      Value element = getMultiArrayElement(key, index);
      // if REF type and entry is null, create a new value for it
      if (element.isType(Value.REF) && element.getValue() == null) {
        element = Util.makeNewRefValue(threadId, conObj);
        setMultiArrayElement(key, index, element);
      }
      currentStackFrame.pushValue(element);
      return;
    }
    
    // should be a combo
    int type = DataConvert.getValueDataType(DataConvert.getClassType(conObj)); // NOTE: THIS USES REFLECTION
    type &= ~(Value.ARY | Value.MARY);
    Value[] combo = getArrayCombo(array, type);
    Value element;
    if (combo[1] == null) {
      // this is a combo consisting of a primitive array ([0] is array, [1] is null)
      // the data type can be inferred from the array type, less the ARY marking
      Object[] elements = (Object[]) combo[0].getValue();
      if (elements == null) {
        exitError("loadReferenceFromArray: primitive array is null");
        return;
      }
      if (type == Value.REF) {
        element = Util.makeNewRefValue(threadId, elements[index]);
      } else {
        element = new Value(elements[index], type);
      }
      currentStackFrame.pushValue(element);
    } else {
      // this is a value-packed array
      Value[] elements = (Value[]) combo[0].getValue();
      if (elements == null) {
        if (conObj == null) {
          exitError("loadReferenceFromArray: Value array is null");
          return;
        } else {
          // this was a case where we didn't have the array info when it was referenced
          // (such as in getField when the field type was an array). We must create the array
          // here from the concrete Value.
          // NOTE:
          // this assumes that array value itself is an Integer value to reference the entry in
          // arrayMap, and the arrayMap entry has a combo[0] value of null and combo[1] value of 0.
          // this is how getField behaves for an array field.
          Object[] conArray = (Object[]) conObj;
          int size = conArray.length;
          elements = new Value[size];

          int objCnt = 0;
          for (int ix = 0; ix < size; ix++) {
            Map<String, Value> newMap = new HashMap<>();
            objCnt = ExecWrapper.putReferenceObject(newMap);
            
            /* ----------debug head--------- *@*/
            debugPrintObjectMap(threadId, objCnt, newMap);
            // ----------debug tail---------- */
            
            elements[ix] = new Value (objCnt, Value.REF);
          }

          /* ----------debug head--------- *@*/
          String arrtype = DataConvert.getObjectValue(conObj);
          if (arrtype.contains("[")) {
            arrtype = arrtype.substring(1);
          }
          debugPrintArrayList(threadId, (Integer) array.getValue(), objCnt - size, size, arrtype);
          // ----------debug tail---------- */
          
          combo[0] = new Value(elements, Value.REF);
          combo[1] = new Value(size, Value.INT32);
        }
      }

      // update the array length from the length of the concrete value if length is incorrect
      int length = ((Object[]) conObj).length;
      boolean updated = updateValueArrayLength(combo, length, array.getValue());
      if (updated) {
        // if changed, make sure to update the elements array that was using the original combo value
        elements = (Value[]) combo[0].getValue();
      }
      
      element = elements[index];
      if (element == null) {
        type = combo[0].getType();
        if (type == Value.REF) {
          element = Util.makeNewRefValue(threadId, element);
          elements[index] = element;
        } else {
          element = new Value(null, type); // convert null entry to an appropriate Value
        }
      }
      currentStackFrame.pushValue(element);
    }
  }
  
  private Value readSymbolicArray(int type, int conLength, Value idx, Value[] combo, int opline) {
    Value val = null;
    boolean arrSymb = combo[0].isType(Value.SYM);
    boolean idxSymb = idx.isType(Value.SYM);
        
    if (arrSymb) {
      ArrayExpr core = (ArrayExpr) combo[0].getValue();
      BitVecExpr idxExpr = Util.getBitVector(idx, z3Context);
      Expr element = z3Context.mkSelect(core, idxExpr);
      z3Optimize.MkMaximize(element);
      val = new Value(element, type | Value.SYM);
    } else if (idxSymb) {
      Util.convertCombo(combo, type, z3Context);
      ArrayExpr core = (com.microsoft.z3.ArrayExpr) combo[0].getValue();            
      Expr element = z3Context.mkSelect(core, (BitVecExpr) idx.getValue());
      z3Optimize.MkMaximize(element);
      val = new Value(element, type | Value.SYM);
      
      // add constraints on index values
      BitVecExpr zero = z3Context.mkBV(0, 32);
      BitVecExpr bound = z3Context.mkBV(conLength, 32);
      z3Constraints.add(z3Context.mkBVSGE((BitVecExpr) idx.getValue(), zero));
      z3Constraints.add(z3Context.mkBVSLT((BitVecExpr) idx.getValue(), bound));

      /* ----------debug head--------- *@*/
      debugPrintConstraint(threadId, "added array bounds constraints: BVSGE to 0 & BVSLT to" + bound);
      // ----------debug tail---------- */
    }
    
    // only add index constraints if index is symbolic
    if (idxSymb) {
      addArrayIndexConstraints((BitVecExpr)idx.getValue(), conLength, opline);
    }
    
    return val;
  }
  
  public void readCharArray(char[] conArray, int opline) {
    String opcode = "CALOAD";
    int type = Value.CHR;
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - readCharArray", opline);
    // ----------debug tail---------- */
    
    Value idx = currentStackFrame.popValue();
    Value arr = currentStackFrame.popValue();
    
    // check for multi-array
    if (arr.isType(Value.MARY)) {
      Value element = getMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue());
      currentStackFrame.pushValue(element);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);
    
    int conLength = conArray == null ? 0 : conArray.length;

    // determine if this is a symbolic array
    Value val = readSymbolicArray(type, conLength, idx, combo, opline);
    if (val == null) {
      // concrete array...
      // verify the index is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");

      // update the array length from the length of the concrete value if length is incorrect
      updateValueArrayLength(combo, conLength, arr.getValue());
      
      // get the selected array entry and return it
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      val = core[index];
      
      // check if we need to translate the z3 context (if sym value was generated in another thread)
      if (val != null && val.isType(Value.SYM)) {
        val = new Value(((Expr)val.getValue()).translate(z3Context), val.getType());
      } else {
        // else, use the concrete
        val = new Value(conArray[index], type);
        core[index] = val;
      }
    }

    currentStackFrame.pushValue(val);
  }
  
  public void readByteArray(byte[] conArray, int opline) {
    String opcode = "BALOAD";
    int type = Value.INT8;
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - readByteArray", opline);
    // ----------debug tail---------- */
    
    Value idx = currentStackFrame.popValue();
    Value arr = currentStackFrame.popValue();
    
    // check for multi-array
    if (arr.isType(Value.MARY)) {
      Value element = getMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue());
      currentStackFrame.pushValue(element);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);
    
    int conLength = conArray == null ? 0 : conArray.length;

    // determine if this is a symbolic array
    Value val = readSymbolicArray(type, conLength, idx, combo, opline);
    if (val == null) {
      // concrete array...
      // verify the index is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");

      // update the array length from the length of the concrete value if length is incorrect
      updateValueArrayLength(combo, conLength, arr.getValue());
      
      // get the selected array entry and return it
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      val = core[index];
      
      // check if we need to translate the z3 context (if sym value was generated in another thread)
      if (val != null && val.isType(Value.SYM)) {
        val = new Value(((Expr)val.getValue()).translate(z3Context), val.getType());
      } else {
        // else, use the concrete
        val = new Value(conArray[index], type);
        core[index] = val;
      }
    }

    currentStackFrame.pushValue(val);
  }
  
  public void readShortArray(short[] conArray, int opline) {
    String opcode = "SALOAD";
    int type = Value.INT16;
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - readShortArray", opline);
    // ----------debug tail---------- */
    
    Value idx = currentStackFrame.popValue();
    Value arr = currentStackFrame.popValue();
    
    // check for multi-array
    if (arr.isType(Value.MARY)) {
      Value element = getMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue());
      currentStackFrame.pushValue(element);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);
    
    int conLength = conArray == null ? 0 : conArray.length;

    // determine if this is a symbolic array
    Value val = readSymbolicArray(type, conLength, idx, combo, opline);
    if (val == null) {
      // concrete array...
      // verify the index is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");

      // update the array length from the length of the concrete value if length is incorrect
      updateValueArrayLength(combo, conLength, arr.getValue());
      
      // get the selected array entry and return it
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      val = core[index];
      
      // check if we need to translate the z3 context (if sym value was generated in another thread)
      if (val != null && val.isType(Value.SYM)) {
        val = new Value(((Expr)val.getValue()).translate(z3Context), val.getType());
      } else {
        // else, use the concrete
        val = new Value(conArray[index], type);
        core[index] = val;
      }
    }

    currentStackFrame.pushValue(val);
  }
  
  public void readIntegerArray(int[] conArray, int opline) {
    String opcode = "IALOAD";
    int type = Value.INT32;
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - readIntegerArray", opline);
    // ----------debug tail---------- */
    
    Value idx = currentStackFrame.popValue();
    Value arr = currentStackFrame.popValue();
    
    // check for multi-array
    if (arr.isType(Value.MARY)) {
      Value element = getMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue());
      currentStackFrame.pushValue(element);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);
    
    int conLength = conArray == null ? 0 : conArray.length;

    // determine if this is a symbolic array
    Value val = readSymbolicArray(type, conLength, idx, combo, opline);
    if (val == null) {
      // concrete array...
      // verify the index is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");

      // update the array length from the length of the concrete value if length is incorrect
      updateValueArrayLength(combo, conLength, arr.getValue());
      
      // get the selected array entry and return it
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      val = core[index];
      
      // check if we need to translate the z3 context (if sym value was generated in another thread)
      if (val != null && val.isType(Value.SYM)) {
        val = new Value(((Expr)val.getValue()).translate(z3Context), val.getType());
      } else {
        // else, use the concrete
        val = new Value(conArray[index], type);
        core[index] = val;
      }
    }

    currentStackFrame.pushValue(val);
  }
  
  public void readLongArray(long[] conArray, int opline) {
    String opcode = "LALOAD";
    int type = Value.INT64;
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - readLongArray", opline);
    // ----------debug tail---------- */
    
    Value idx = currentStackFrame.popValue();
    Value arr = currentStackFrame.popValue();
    
    // check for multi-array
    if (arr.isType(Value.MARY)) {
      Value element = getMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue());
      currentStackFrame.pushValue(element);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);
    
    int conLength = conArray == null ? 0 : conArray.length;

    // determine if this is a symbolic array
    Value val = readSymbolicArray(type, conLength, idx, combo, opline);
    if (val == null) {
      // concrete array...
      // verify the index is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");

      // update the array length from the length of the concrete value if length is incorrect
      updateValueArrayLength(combo, conLength, arr.getValue());
      
      // get the selected array entry and return it
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      val = core[index];

      // check if we need to translate the z3 context (if sym value was generated in another thread)
      if (val != null && val.isType(Value.SYM)) {
        val = new Value(((Expr)val.getValue()).translate(z3Context), val.getType());
      } else {
        // else, use the concrete
        val = new Value(conArray[index], type);
        core[index] = val;
      }
    }

    currentStackFrame.pushValue(val);
  }
  
  public void readFloatArray(float[] conArray, int opline) {
    String opcode = "FALOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - readFloatArray");
    // ----------debug tail---------- */
    
    Value idx = currentStackFrame.popValue();
    Value arr = currentStackFrame.popValue();

    // check for multi-array
    if (arr.isType(Value.MARY)) {
      Value element = getMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue());
      currentStackFrame.pushValue(element);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    int type = Value.FLT;
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);
    
    boolean arrsymb = combo[0].isType(Value.SYM);
    if (arrsymb) {
      ArrayExpr core = (ArrayExpr) combo[0].getValue();
      BitVecExpr idxExpr = Util.getBitVector(idx, z3Context);
      Expr element = z3Context.mkSelect(core, idxExpr);
      currentStackFrame.pushValue(new Value(element, type | Value.SYM));
    } else if (idx.isType(Value.SYM)) {
      //if (!(combo[0].getValue() instanceof com.microsoft.z3.ArrayExpr)) {
      if (!arrsymb) {
        Util.convertCombo(combo, type, z3Context);
      }
      ArrayExpr core = (com.microsoft.z3.ArrayExpr) combo[0].getValue();            
      Expr element = z3Context.mkSelect(core, (BitVecExpr) idx.getValue());
      currentStackFrame.pushValue(new Value(element, type | Value.SYM));
    } else {
      // verify the entry is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");
      
      // update the array length from the length of the concrete value if length is incorrect
      int conLength = conArray == null ? 0 : conArray.length;
      updateValueArrayLength(combo, conLength, arr.getValue());
      
      // get the selected array entry and return it
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      Value val = core[index];
      // check if we need to translate the z3 context (if sym value was generated in another thread)
      if (val != null && val.isType(Value.SYM)) {
        val = new Value(((Expr)val.getValue()).translate(z3Context), val.getType());
      } else {
        // else, use the concrete
        val = new Value(conArray[index], type);
        core[index] = val;
      }

      currentStackFrame.pushValue(val);
    }
  }

  public void readDoubleArray(double[] conArray, int opline) {
    String opcode = "DALOAD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - readDoubleArray");
    // ----------debug tail---------- */
    
    Value idx = currentStackFrame.popValue();
    Value arr = currentStackFrame.popValue();

    // check for multi-array
    if (arr.isType(Value.MARY)) {
      Value element = getMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue());
      currentStackFrame.pushValue(element);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    int type = Value.DBL;
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);

    boolean arrsymb = combo[0].isType(Value.SYM);
    if (arrsymb) {
      ArrayExpr core = (ArrayExpr) combo[0].getValue();
      BitVecExpr idxExpr = Util.getBitVector(idx, z3Context);
      Expr element = z3Context.mkSelect(core, idxExpr);
      currentStackFrame.pushValue(new Value(element, type | Value.SYM));
    } else if (idx.isType(Value.SYM)) {
      //if (!(combo[0].getValue() instanceof com.microsoft.z3.ArrayExpr)) {
      if (!arrsymb) {
        Util.convertCombo(combo, type, z3Context);
      }
      ArrayExpr core = (com.microsoft.z3.ArrayExpr) combo[0].getValue();            
      Expr element = z3Context.mkSelect(core, (BitVecExpr) idx.getValue());
      currentStackFrame.pushValue(new Value(element, type | Value.SYM));
    } else {
      // verify the entry is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");
      
      // update the array length from the length of the concrete value if length is incorrect
      int conLength = conArray == null ? 0 : conArray.length;
      updateValueArrayLength(combo, conLength, arr.getValue());
      
      // get the selected array entry and return it
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      Value val = core[index];
      // check if we need to translate the z3 context (if sym value was generated in another thread)
      if (val != null && val.isType(Value.SYM)) {
        val = new Value(((Expr)val.getValue()).translate(z3Context), val.getType());
      } else {
        // else, use the concrete
        val = new Value(conArray[index], type);
        core[index] = val;
      }

      currentStackFrame.pushValue(val);
    }
  }
  
  // who is to blame: danielbala, dmcd2356
  // who blames him: zzk
  public void writePrimitiveArray(int type, int opline) {
    String opcode = getOpcodeDataType(type) + "ASTORE";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand_type(threadId, opcode + " - writePrimitiveArray", type);
    // ----------debug tail---------- */

    Value val = currentStackFrame.popValue(); // value to write to array
    Value idx = currentStackFrame.popValue(); // index of array to write to
    Value arr = currentStackFrame.popValue(); // the array itself

    // check for multi-array
    if (arr.isType(Value.MARY)) {
      setMultiArrayElement((Integer) arr.getValue(), (Integer) idx.getValue(), val);
      return;
    }
    
    // get the array combo entry (and verify)
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    Value[] combo = getArrayCombo(arr, type);
    repackCombo(combo, type);
    
    if (arr.isType(Value.SYM)) {
      Expr valExpr = Util.getExpression(val, z3Context);
      BitVecExpr idxExpr = Util.getBitVector(idx, z3Context);
      ArrayExpr core = (ArrayExpr) combo[0].getValue();
      combo[0] = new Value(z3Context.mkStore(core, idxExpr, valExpr),
          Value.ARY | Value.SYM);
    } else if (idx.isType(Value.SYM)) {
      Expr valExpr = Util.getExpression(val, z3Context);
      BitVecExpr idxExpr = Util.getBitVector(idx, z3Context);
      Util.convertCombo(combo, type, z3Context);
      ArrayExpr core = (ArrayExpr) combo[0].getValue();
      combo[0] = new Value(z3Context.mkStore(core, idxExpr, valExpr),
          Value.ARY | Value.SYM);
    } else {
      // verify the entry is an Integer type
      assertUnsignedIntValue(idx, opcode, "array index");
      int index = (Integer) idx.getValue();
      Value[] core = (Value[]) combo[0].getValue();
      core[index] = val;
    }
  }

  public void arrayStore(Object[] conObj, int conIndex, int opline) {
    String opcode = "AASTORE";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - arrayStore", conIndex);
    // ----------debug tail---------- */

    Value value = currentStackFrame.popValue();
    Value index = currentStackFrame.popValue(); // use concrete instead
    Value array = currentStackFrame.popValue();

    String classType = DataConvert.getClassType(conObj); // NOTE: THIS USES REFLECTION
    int arrlev = 0;
    for (int ix = 0; classType.charAt(ix) == '['; ix++) {
      ++arrlev;
    }
    if (arrlev < 1) {
      exitError("arrayStore: called on non-array type: " + classType);
    }

    // this just checks to see if we have the ARY flag reported correctly
    /* ----------debug head--------- *@*/
    if (!array.isType(Value.ARY | Value.MARY)) {
        debugPrintWarning(threadId, "arrayStore: ARY flag missing in type: " +
            DataConvert.showValueDataType(array.getType()));
    }
    // ----------debug tail---------- */
    
    // check for multi-array
    if (array.isType(Value.MARY)) {
      Integer key = (Integer) array.getValue();
      if (key == null) {
        exitError("arrayStore: Multi-array key value was not defined");
      }
      setMultiArrayElement(key, conIndex, value);
      return;
    }
    
    // TODO: do we need to handle symbolics here as we do for writePrimitivaArray ?
    
    // (in case it was returned from an uninstrumented method, we need to repack info Value[])
    Value[] combo = getArrayCombo(array, DataConvert.getValueDataType(classType));
    repackCombo(combo, combo[0].getType());

    Value[] elements = (Value[]) combo[0].getValue();
    elements[conIndex] = value;
  }

  public void executePop() {
    String opcode = "POP";
    
   /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executePop");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();

    /* ----------debug head--------- *@*/
    if (value1 != null && value1.isType(Value.INT64 | Value.DBL)) {
      debugPrintError(threadId, "stack entry contains long or double for POP");
    }
    // ----------debug tail---------- */
  }

  public void executePop2() {
    String opcode = "POP2";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executePop2");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();
    if (!value1.isType(Value.INT64 | Value.DBL)) {
      Value value2 = currentStackFrame.popValue();

      /* ----------debug head--------- *@*/
      if (value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
        debugPrintError(threadId, "stack entry 2 contains long or double for POP2");
      }
      // ----------debug tail---------- */
    }
  }

  public void executeDup() {
    String opcode = "DUP";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeDup");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.peekValue(0);
    currentStackFrame.pushValue(value1);

    /* ----------debug head--------- *@*/
    if (value1 != null && value1.isType(Value.INT64 | Value.DBL)) {
      debugPrintError(threadId, "stack entry contains long or double for DUP");
    }
    // ----------debug tail---------- */
  }

  public void executeDup_x1() {
    String opcode = "DUP_X1";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeDup_x1");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();
    Value value2 = currentStackFrame.popValue();

    currentStackFrame.pushValue(value1);
    currentStackFrame.pushValue(value2);
    currentStackFrame.pushValue(value1);

    /* ----------debug head--------- *@*/
    if (value1 != null && value1.isType(Value.INT64 | Value.DBL) ||
        value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
      debugPrintError(threadId, "stack entry contains long or double for DUP_X1");
    }
    // ----------debug tail---------- */
  }

  public void executeDup_x2() {
    String opcode = "DUP_X2";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeDup_x2");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();
    Value value2 = currentStackFrame.popValue();

    /* ----------debug head--------- *@*/
    if (value1 != null && value1.isType(Value.INT64 | Value.DBL)) {
      debugPrintError(threadId, "stack entry 1 contains long or double for DUP_X2");
    }
    // ----------debug tail---------- */

    if (value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
      currentStackFrame.pushValue(value1);
      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
    } else {
      Value value3 = currentStackFrame.popValue();
      currentStackFrame.pushValue(value1);
      currentStackFrame.pushValue(value3);
      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
    }
  }

  public void executeDup2() {
    String opcode = "DUP2";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeDup2");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();
    if (value1 != null && value1.isType(Value.INT64 | Value.DBL)) {
      currentStackFrame.pushValue(value1);
      currentStackFrame.pushValue(value1);
    } else {
      Value value2 = currentStackFrame.popValue();

      /* ----------debug head--------- *@*/
      if (value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
        debugPrintError(threadId, "stack entry 2 contains long or double for DUP2");
      }
      // ----------debug tail---------- */

      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
    }
  }

  public void executeDup2_x1() {
    String opcode = "DUP2_X1";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeDup2_x1");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();
    Value value2 = currentStackFrame.popValue();

    /* ----------debug head--------- *@*/
    if (value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
      debugPrintError(threadId, "stack entry 2 contains long or double for DUP2_X1");
    }
    // ----------debug tail---------- */

    if (value1.isType(Value.INT64 | Value.DBL)) {
      currentStackFrame.pushValue(value1);
      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
    } else {
      Value value3 = currentStackFrame.popValue();

      /* ----------debug head--------- *@*/
      if (value3 != null && value3.isType(Value.INT64 | Value.DBL)) {
        debugPrintError(threadId, "stack entry 3 contains long or double for DUP2_X1");
      }
      // ----------debug tail---------- */

      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
      currentStackFrame.pushValue(value3);
      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
    }
  }

  public void executeDup2_x2() {
    String opcode = "DUP2_X2";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeDup2_x2");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();
    Value value2 = currentStackFrame.popValue();
    if (value1 != null && value1.isType(Value.INT64 | Value.DBL) ||
        value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
      // 2 double entries
      currentStackFrame.pushValue(value1); // double entry
      currentStackFrame.pushValue(value2); // double entry
      currentStackFrame.pushValue(value1); // double entry
    } else if (value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
      // invalid selection

      /* ----------debug head--------- *@*/
      debugPrintError(threadId, "stack entry 2 contains long or double for DUP2_X2");
      // ----------debug tail---------- */

      currentStackFrame.pushValue(value1);
      currentStackFrame.pushValue(value2);
      currentStackFrame.pushValue(value1);
    } else {
      Value value3 = currentStackFrame.popValue();
      if (value1 != null && value1.isType(Value.INT64 | Value.DBL)) {
        // 1st entry is double, next 2 entries not
        currentStackFrame.pushValue(value1); // double entry
        currentStackFrame.pushValue(value3);
        currentStackFrame.pushValue(value2);
        currentStackFrame.pushValue(value1); // double entry

        /* ----------debug head--------- *@*/
        if (value3 != null && value3.isType(Value.INT64 | Value.DBL)) {
          debugPrintError(threadId, "stack entry 3 contains long or double for DUP2_X2");
        }
        // ----------debug tail---------- */
      } else if (value3 != null && value3.isType(Value.INT64 | Value.DBL)) {
        // 1st 2 entries not double, 3 entry is double
        currentStackFrame.pushValue(value2);
        currentStackFrame.pushValue(value1);
        currentStackFrame.pushValue(value3); // double entry
        currentStackFrame.pushValue(value2);
        currentStackFrame.pushValue(value1);
      } else {
        // 4 entries on stack, none of them double
        Value value4 = currentStackFrame.popValue();
        currentStackFrame.pushValue(value2);
        currentStackFrame.pushValue(value1);
        currentStackFrame.pushValue(value4);
        currentStackFrame.pushValue(value3);
        currentStackFrame.pushValue(value2);
        currentStackFrame.pushValue(value1);

        /* ----------debug head--------- *@*/
        if (value4 != null && value4.isType(Value.INT64 | Value.DBL)) {
          debugPrintError(threadId, "stack entry 4 contains long or double for DUP2_X2");
        }
        // ----------debug tail---------- */
      }
    }
  }

  public void executeSwap() {
    String opcode = "SWAP";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeSwap");
    // ----------debug tail---------- */

    Value value1 = currentStackFrame.popValue();
    Value value2 = currentStackFrame.popValue();

    currentStackFrame.pushValue(value1);
    currentStackFrame.pushValue(value2);

    /* ----------debug head--------- *@*/
    if (value1 != null && value1.isType(Value.INT64 | Value.DBL) ||
        value2 != null && value2.isType(Value.INT64 | Value.DBL)) {
      debugPrintError(threadId, "stack entry contains long or double for SWAP");
    }
    // ----------debug tail---------- */
  }

  public void addDouble() {
    String opcode = "DADD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - addDouble");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.addRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void subDouble() {
    String opcode = "DSUB";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - subDouble");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.subRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void mulDouble() {
    String opcode = "DMUL";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - mulDouble");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.mulRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void divDouble() {
    String opcode = "DDIV";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - divDouble");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.divRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void remDouble() {
    String opcode = "DREM";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - remDouble");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.modRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void negDouble() {
    String opcode = "DNEG";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - negDouble");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();

    Value val2 = ValueOp.negRealValue(val1, z3Context);
    currentStackFrame.pushValue(val2);
  }

  public void addFloat() {
    String opcode = "FADD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - addFloat");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.addRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void subFloat() {
    String opcode = "FSUB";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - subFloat");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.subRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void mulFloat() {
    String opcode = "FMUL";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - mulFloat");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.mulRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void divFloat() {
    String opcode = "FDIV";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - divFloat");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.divRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void remFloat() {
    String opcode = "FREM";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - remFloat");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.modRealValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void negFloat() {
    String opcode = "FNEG";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - negFloat");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();

    Value val2 = ValueOp.negRealValue(val1, z3Context);
    currentStackFrame.pushValue(val2);
  }

  public void incInteger(int var, int incr) {
    String opcode = "IINC";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - incInteger", var, incr);
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.getLocalVariable(var);
    if (val1 == null) {
      val1 = new Value(null, Value.INT32);
      currentStackFrame.storeLocalVariable(var, val1);
    }

    Value val2 = new Value(new Integer(incr), Value.INT32);

    Value val3 = ValueOp.addIntegralValue(val1, val2, z3Context);
    currentStackFrame.storeLocalVariable(var, val3);
  }

  public void addInteger() {
    String opcode = "IADD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - addInteger");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.addIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void subInteger() {
    String opcode = "ISUB";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - subInteger");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.subIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void mulInteger() {
    String opcode = "IMUL";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - mulInteger");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.mulIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void divInteger() {
    String opcode = "IDIV";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - divInteger");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.divIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void remInteger() {
    String opcode = "IREM";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - remInteger");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.modIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void negInteger() {
    String opcode = "INEG";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - negInteger");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();

    Value val2 = ValueOp.negIntegralValue(val1, z3Context);
    currentStackFrame.pushValue(val2);
  }

  public void addLong() {
    String opcode = "LADD";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - addLong");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.addIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void subLong() {
    String opcode = "LSUB";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - subLong");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.subIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void mulLong() {
    String opcode = "LMUL";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - mulLong");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.mulIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void divLong() {
    String opcode = "LDIV";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - divLong");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.divIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void remLong() {
    String opcode = "LREM";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - remLong");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.modIntegralValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void negLong() {
    String opcode = "LNEG";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - negLong");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();

    Value val2 = ValueOp.negIntegralValue(val1, z3Context);
    currentStackFrame.pushValue(val2);
  }

  public void executeIntegerAnd() {
    String opcode = "IAND";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIntegerAnd");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    assertInvalidType(val1, opcode, "val1", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32);
    assertInvalidType(val2, opcode, "val2", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32);

    Value val3 = ValueOp.andValue(Value.INT32, val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeIntegerOr() {
    String opcode = "IOR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIntegerOr");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    assertInvalidType(val1, opcode, "val1", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32);
    assertInvalidType(val2, opcode, "val2", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32);

    Value val3 = ValueOp.orValue(Value.INT32, val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeIntegerXor() {
    String opcode = "IXOR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIntegerXor");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    assertInvalidType(val1, opcode, "val1", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32);
    assertInvalidType(val2, opcode, "val2", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32);

    Value val3 = ValueOp.xorValue(Value.INT32, val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeIntegerShiftLeft() {
    String opcode = "ISHL";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIntegerShiftLeft");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.shiftLeftValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeIntegerArithShiftRight() {
    String opcode = "ISHR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIntegerArithShiftRight");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.shiftRightArithValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeIntegerShiftRight() {
    String opcode = "IUSHR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIntegerShiftRight");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.shiftRightLogicValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeLongAnd() {
    String opcode = "LAND";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeLongAnd");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    assertInvalidType(val1, opcode, "val1", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64);
    assertInvalidType(val2, opcode, "val2", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64);

    Value val3 = ValueOp.andValue(Value.INT64, val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeLongOr() {
    String opcode = "LOR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeLongOr");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    assertInvalidType(val1, opcode, "val1", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64);
    assertInvalidType(val2, opcode, "val2", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64);

    Value val3 = ValueOp.orValue(Value.INT64, val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeLongXor() {
    String opcode = "LXOR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeLongXor");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    assertInvalidType(val1, opcode, "val1", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64);
    assertInvalidType(val2, opcode, "val2", Value.CHR | Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64);

    Value val3 = ValueOp.xorValue(Value.INT64, val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeLongShiftLeft() {
    String opcode = "LSHL";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeLongShiftLeft");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.shiftLeftValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeLongArithShiftRight() {
    String opcode = "LSHR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeLongArithShiftRight");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.shiftRightArithValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void executeLongShiftRight() {
    String opcode = "LUSHR";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeLongShiftRight");
    // ----------debug tail---------- */

    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();

    Value val3 = ValueOp.shiftRightLogicValue(val1, val2, z3Context);
    currentStackFrame.pushValue(val3);
  }

  public void convertToDouble(char optype) {
    String opcode = optype + "2D";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - convertToDouble");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();
    assertValueNonNull(val1, opcode, "val1");

    if (val1.isType(Value.SYM)) {
      if (val1.isType(Value.FLT)) {
        currentStackFrame.pushValue(val1);
      } else if (val1.isType(Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64)) {
        // Is there an alternative way to do this?
        IntExpr intExpr = z3Context.mkBV2Int((BitVecExpr) val1.getValue(), true);
        RealExpr realExpr = z3Context.mkInt2Real(intExpr);
        currentStackFrame.pushValue(new Value(realExpr, Value.SYM | Value.DBL));
      }
      return;
    }

    double newvalue = 0;
    switch (val1.getValue().getClass().getName()) { // NOTE: THIS USES REFLECTION
      case "java.lang.Integer":
        newvalue = ((Integer) val1.getValue()).doubleValue();
        break;
      case "java.lang.Long":
        newvalue = ((Long) val1.getValue()).doubleValue();
        break;
      case "java.lang.Float":
        newvalue = ((Float) val1.getValue()).doubleValue();
        break;
      default:
        /* ----------debug head--------- *@*/
        debugPrintError(threadId, "Invalid type conversion!");
        // ----------debug tail---------- */

        break;
    }

    Value val2 = new Value(newvalue, Value.DBL);
    currentStackFrame.pushValue(val2);
  }

  public void convertToFloat(char optype) {
    String opcode = optype + "2F";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - convertToFloat");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();
    assertValueNonNull(val1, opcode, "val1");

    if (val1.isType(Value.SYM)) {
      if (val1.isType(Value.FLT)) {
        currentStackFrame.pushValue(val1);
      } else if (val1.isType(Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64)) {
        // Is there an alternative way to do this?
        IntExpr intExpr = z3Context.mkBV2Int((BitVecExpr) val1.getValue(), true);
        RealExpr realExpr = z3Context.mkInt2Real(intExpr);
        currentStackFrame.pushValue(new Value(realExpr, Value.SYM | Value.DBL));
      }
      return;
    }

    float newvalue = 0;
    switch (val1.getValue().getClass().getName()) { // NOTE: THIS USES REFLECTION
      case "java.lang.Integer":
        newvalue = ((Integer) val1.getValue()).floatValue();
        break;
      case "java.lang.Long":
        newvalue = ((Long) val1.getValue()).floatValue();
        break;
      case "java.lang.Double":
        newvalue = ((Double) val1.getValue()).floatValue();
        break;
      default:
        /* ----------debug head--------- *@*/
        debugPrintError(threadId, "Invalid type conversion!");
        // ----------debug tail---------- */

        break;
    }

    Value val2 = new Value(newvalue, Value.FLT);
    currentStackFrame.pushValue(val2);
  }

  public void convertToLong(char optype) {
    String opcode = optype + "2L";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - convertToLong");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();
    assertValueNonNull(val1, opcode, "val1");

    if (val1.isType(Value.SYM)) {
      if (val1.isType(Value.INT8 | Value.INT16 | Value.INT32 | Value.INT64)) {
        currentStackFrame.pushValue(val1);
      } else if (val1.isType(Value.FLT | Value.DBL)) {
        // Is there an alternative way to do this?        
        IntExpr intExpr = z3Context.mkReal2Int((RealExpr) val1.getValue());
        BitVecExpr bvExpr = z3Context.mkInt2BV(64, intExpr);
        currentStackFrame.pushValue(new Value(bvExpr, Value.SYM | Value.INT64));
      }
      return;
    }

    long newvalue = 0;
    switch (val1.getValue().getClass().getName()) { // NOTE: THIS USES REFLECTION
      case "java.lang.Integer":
        newvalue = ((Integer) val1.getValue()).longValue();
        break;
      case "java.lang.Float":
        newvalue = ((Float) val1.getValue()).longValue();
        break;
      case "java.lang.Double":
        newvalue = ((Double) val1.getValue()).longValue();
        break;
      default:
        /* ----------debug head--------- *@*/
        debugPrintError(threadId, "Invalid type conversion!");
        // ----------debug tail---------- */

        break;
    }

    Value val2 = new Value(newvalue, Value.INT64);
    currentStackFrame.pushValue(val2);
  }

  public void convertToInteger(char optype) {
    String opcode = optype + "2I";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - convertToInteger");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();
    assertValueNonNull(val1, opcode, "val1");

    if (val1.isType(Value.SYM)) {
      BitVecExpr bvExpr;
      switch(val1.getType() & ~Value.SYM) {
        case Value.INT8:
          bvExpr = (BitVecExpr) val1.getValue();
          bvExpr = z3Context.mkSignExt(24, bvExpr);
          currentStackFrame.pushValue(new Value(bvExpr, Value.INT32));
          break;
        case Value.INT16:
          bvExpr = (BitVecExpr) val1.getValue();
          bvExpr = z3Context.mkSignExt(16, bvExpr);
          currentStackFrame.pushValue(new Value(bvExpr, Value.INT32));
          break;
        case Value.INT32:
          currentStackFrame.pushValue(val1);
          break;
        case Value.INT64:
          bvExpr = (BitVecExpr) val1.getValue();
          bvExpr = z3Context.mkExtract(31, 0, bvExpr);
          currentStackFrame.pushValue(new Value(bvExpr, Value.INT32));
          break;
        case Value.FLT:
        case Value.DBL:
          // Is there an alternative way to do this?        
          IntExpr intExpr = z3Context.mkReal2Int((RealExpr) val1.getValue());
          bvExpr = z3Context.mkInt2BV(32, intExpr);
          currentStackFrame.pushValue(new Value(bvExpr, Value.SYM | Value.INT32));
          break;
      }
      return;
    }

    int newvalue = 0;
    switch (val1.getValue().getClass().getName()) { // NOTE: THIS USES REFLECTION
      case "java.lang.Long":
        newvalue = ((Long) val1.getValue()).intValue();
        break;
      case "java.lang.Float":
        newvalue = ((Float) val1.getValue()).intValue();
        break;
      case "java.lang.Double":
        newvalue = ((Double) val1.getValue()).intValue();
        break;
      default:
        /* ----------debug head--------- *@*/
        debugPrintError(threadId, "Invalid type conversion!");
        // ----------debug tail---------- */

        break;
    }

    Value val2 = new Value(newvalue, Value.INT32);
    currentStackFrame.pushValue(val2);
  }

  public void convertToShort() {
    String opcode = "I2S";

    Value val1 = currentStackFrame.popValue();
    assertValueNonNull(val1, opcode, "val1");

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - convertToShort");
    // ----------debug tail---------- */

    if (val1.isType(Value.SYM)) {
      BitVecExpr bvExpr = (BitVecExpr) val1.getValue();
      bvExpr = z3Context.mkExtract(15, 0, bvExpr);
      bvExpr = z3Context.mkSignExt(16, bvExpr);
      currentStackFrame.pushValue(new Value(bvExpr, Value.INT16 | Value.SYM));
      return;
    }

    Value val2;
    int mask = 0x7FFF;
    int signbit = mask + 1;
    if (val1.getValue().getClass().getName().equals("java.lang.Integer")) { // NOTE: THIS USES REFLECTION
      int intval = (Integer) val1.getValue();
      intval = (intval & signbit) == 0 ? intval & mask : -(intval & mask);
      val2 = new Value(intval, Value.INT16);
    } else {
      /* ----------debug head--------- *@*/
      debugPrintError(threadId, "Invalid type conversion!");
      // ----------debug tail---------- */

      val2 = new Value(0, Value.INT16);
    }

    currentStackFrame.pushValue(val2);
  }

  public void convertToChar() {
    String opcode = "I2C";

    Value val1 = currentStackFrame.popValue();
    assertValueNonNull(val1, opcode, "val1");

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - convertToChar");
    // ----------debug tail---------- */

    if (val1.isType(Value.SYM)) {
      BitVecExpr bvExpr = (BitVecExpr) val1.getValue();
      bvExpr = z3Context.mkExtract(15, 0, bvExpr);
      bvExpr = z3Context.mkSignExt(16, bvExpr);
      currentStackFrame.pushValue(new Value(bvExpr, Value.CHR | Value.SYM));
      return;
    }

    Value val2;
    int mask = 0xFFFF;
    if (val1.getValue().getClass().getName().equals("java.lang.Integer")) { // NOTE: THIS USES REFLECTION
      val2 = new Value((Integer) val1.getValue() & mask, Value.CHR);
    } else {
      /* ----------debug head--------- *@*/
      debugPrintError(threadId, "Invalid type conversion!");
      // ----------debug tail---------- */

      val2 = new Value(0, Value.CHR);
    }

    currentStackFrame.pushValue(val2);
  }

  public void convertToByte() {
    String opcode = "I2B";

    Value val1 = currentStackFrame.popValue();
    assertValueNonNull(val1, opcode, "val1");

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - convertToByte");
    // ----------debug tail---------- */

    if (val1.isType(Value.SYM)) {
      BitVecExpr bvExpr = (BitVecExpr) val1.getValue();
      bvExpr = z3Context.mkExtract(7, 0, bvExpr);
      bvExpr = z3Context.mkSignExt(24, bvExpr);
      currentStackFrame.pushValue(new Value(bvExpr, Value.INT32 | Value.SYM));
      return;
    }

    Value val2;
    int mask = 0x7F;
    int signbit = mask + 1;
    if (val1.getValue().getClass().getName().equals("java.lang.Integer")) { // NOTE: THIS USES REFLECTION
      int intval = (Integer) val1.getValue();
      intval = (intval & signbit) == 0 ? intval & mask : -(intval & mask);
      val2 = new Value(intval, Value.INT8);
    } else {
      /* ----------debug head--------- *@*/
      debugPrintError(threadId, "Invalid type conversion!");
      // ----------debug tail---------- */

      val2 = new Value(0, Value.INT8);
    }

    currentStackFrame.pushValue(val2);
  }

  public void executeLongCompare() {
    String opcode = "LCMP";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeLongCompare");
    // ----------debug tail---------- */

    Value val1 = currentStackFrame.popValue();
    Value val2 = currentStackFrame.popValue();

    boolean isSymbolic1 = val1.isType(Value.SYM);
    boolean isSymbolic2 = val2.isType(Value.SYM);

    if (!isSymbolic1 && !isSymbolic2) {
      long l1 = (Long) val1.getValue();
      long l2 = (Long) val2.getValue();
      int res = (l1 == l2) ? 0 : (l2 > l1) ? 1 : -1;
      currentStackFrame.pushValue(new Value(res, Value.INT32));
      return;
    }

    com.microsoft.z3.BitVecExpr expr1 = isSymbolic1
            ? (com.microsoft.z3.BitVecExpr) val1.getValue()
            : z3Context.mkBV((Long) val1.getValue(), 64);

    com.microsoft.z3.BitVecExpr expr2 = isSymbolic2
            ? (com.microsoft.z3.BitVecExpr) val2.getValue()
            : z3Context.mkBV((Long) val2.getValue(), 64);

    com.microsoft.z3.BitVecExpr zero = z3Context.mkBV(0, 64);
    com.microsoft.z3.BitVecExpr posOne = z3Context.mkBV(1, 64);
    com.microsoft.z3.BitVecExpr negOne = z3Context.mkBV(-1, 64);

    com.microsoft.z3.Expr res =
        z3Context.mkITE( z3Context.mkEq(expr1, expr2), zero,
        z3Context.mkITE( z3Context.mkBVSGT(expr2, expr1), posOne, negOne));

    currentStackFrame.pushValue(new Value(res, Value.SYM | Value.INT32));
  }
    
  public void compareIntegerEquality(String opcode, int con1, int con2, int line, String method) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - compareIntegerEquality", con1, con2);
    // ----------debug tail---------- */
    
    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();
    
    boolean sym2 = val2.isType(Value.SYM);
    boolean sym1 = val1.isType(Value.SYM);

    // get compare status
    boolean pathsel = con1 == con2;

    /* ----------debug head--------- *@*/
    if (sym1 || sym2) {
      debugPrintBranch(threadId, opcode + " " + pathsel + " " + line + " " + method);
    } else {
      debugPrintInfo(threadId, DebugUtil.BRANCH, opcode + " " + pathsel + " " + line + " " + method);
    }
    // ----------debug tail---------- */

    if (!sym2 && !sym1) {
      return; // ignore non-symbolic compares
    }
    
    Expr expr2 = Util.getBitVector(Value.INT32, val2, z3Context);
    Expr expr1 = Util.getBitVector(Value.INT32, val1, z3Context);
    
    BoolExpr expr3 = z3Context.mkEq(expr2, expr1);
    expr3 = pathsel ? expr3 : z3Context.mkNot(expr3);
    z3Constraints.add(expr3);
    
    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "added " + opcode + " constraint: " + (pathsel ? "Eq" : "Eq + Not")
            + " for " + con1 + " ? " + con2);
    // ----------debug tail---------- */

    solvePC(threadId, line, pathsel, ConstraintType.PATH);
  }
  
  public void compareIntegerGreaterThan(String opcode, int con1, int con2, int line, String method) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - compareIntegerGreaterThan", con1, con2);
    // ----------debug tail---------- */
    
    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();
    
    boolean sym2 = val2.isType(Value.SYM);
    boolean sym1 = val1.isType(Value.SYM);
    
    // get compare status
    boolean pathsel = con1 > con2;

    /* ----------debug head--------- *@*/
    if (sym1 || sym2) {
      debugPrintBranch(threadId, opcode + " " + pathsel + " " + line + " " + method);
    } else {
      debugPrintInfo(threadId, DebugUtil.BRANCH, opcode + " " + pathsel + " " + line + " " + method);
    }
    // ----------debug tail---------- */

    if (!sym2 && !sym1) {
      return; // ignore non-symbolic compares
    }
    
    BitVecExpr expr2 = Util.getBitVector(Value.INT32, val2, z3Context);
    BitVecExpr expr1 = Util.getBitVector(Value.INT32, val1, z3Context);
    BoolExpr expr3 = null;
    expr3 = pathsel ? z3Context.mkBVSGT(expr1, expr2) : z3Context.mkBVSLE(expr1, expr2);
    z3Constraints.add(expr3);

    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "added " + opcode + " constraint: " + (pathsel ? "BVSGT" : "BVSLE")
            + " for " + con1 + " ? " + con2);
    // ----------debug tail---------- */

    solvePC(threadId, line, pathsel, ConstraintType.PATH);
  }
  
  public void compareIntegerLessThan(String opcode, int con1, int con2, int line, String method) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - compareIntegerLessThan", con1, con2);
    // ----------debug tail---------- */
    
    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();
    
    boolean sym2 = val2.isType(Value.SYM);
    boolean sym1 = val1.isType(Value.SYM);
    
    // get compare status
    boolean pathsel = con1 < con2;

    /* ----------debug head--------- *@*/
    if (sym1 || sym2) {
      debugPrintBranch(threadId, opcode + " " + pathsel + " " + line + " " + method);
    } else {
      debugPrintInfo(threadId, DebugUtil.BRANCH, opcode + " " + pathsel + " " + line + " " + method);
    }
    // ----------debug tail---------- */

    if (!sym2 && !sym1) {
      return; // ignore non-symbolic compares
    }
    
    BitVecExpr expr2 = Util.getBitVector(Value.INT32, val2, z3Context);
    BitVecExpr expr1 = Util.getBitVector(Value.INT32, val1, z3Context);
    BoolExpr expr3 = pathsel ? z3Context.mkBVSLT(expr1, expr2) : z3Context.mkBVSGE(expr1, expr2);
    z3Constraints.add(expr3);

    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "added " + opcode + " constraint: " + (pathsel ? "BVSLT" : "BVSGE")
            + " for " + con1 + " ? " + con2);
    // ----------debug tail---------- */

    solvePC(threadId, line, pathsel, ConstraintType.PATH);
  }
  
  public void maxCompareIntegerLessThan(String opcode, int con1, int con2, int line, String method) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - maxCompareIntegerLessThan", con1, con2);
    // ----------debug tail---------- */
    
    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();
    
    boolean sym2 = val2.isType(Value.SYM);
    boolean sym1 = val1.isType(Value.SYM);
    
    // get compare status
    boolean pathsel = con1 < con2;

    /* ----------debug head--------- *@*/
    if (sym1 || sym2) {
      debugPrintBranch(threadId, opcode + " " + pathsel + " " + line + " " + method);
    } else {
      debugPrintInfo(threadId, DebugUtil.BRANCH, opcode + " " + pathsel + " " + line + " " + method);
    }
    // ----------debug tail---------- */

    if (!sym2 && !sym1) {
      return; // ignore non-symbolic compares
    }
    
    BitVecExpr expr2 = Util.getBitVector(Value.INT32, val2, z3Context);
    BitVecExpr expr1 = Util.getBitVector(Value.INT32, val1, z3Context);
    BoolExpr expr3 = pathsel ? z3Context.mkBVSLT(expr1, expr2) : z3Context.mkBVSGE(expr1, expr2);
    z3Constraints.add(expr3);

    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "added " + opcode + " constraint: " + (pathsel ? "BVSLT" : "BVSGE")
            + " for " + con1 + " ? " + con2);
    // ----------debug tail---------- */

    // try to maximize the loop bound if it's symbolic
    if (sym2) {
      z3Optimize.Push();
      z3Optimize.MkMaximize(expr2);
    }
    
    solvePC(threadId, line, pathsel, ConstraintType.LOOPBOUND);
    
    if (sym2) {
      z3Optimize.Pop();
    }
  }
  
  public void compareFloatGreater(String opcode) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - compareFloatGreater");
    // ----------debug tail---------- */
    
    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();
    
    boolean sym2 = val2.isType(Value.SYM);
    boolean sym1 = val1.isType(Value.SYM);
    
    if (!sym2 && !sym1) {
      float con2 = (Float) val2.getValue();
      float con1 = (Float) val1.getValue();
      int res = (con1 == con2) ? 0 : (con1 < con2 ? -1 : 1);
      currentStackFrame.pushValue((new Value(res, Value.INT32)));
      return;
    }
    
    ArithExpr expr2 = Util.getReal(val2, z3Context);
    ArithExpr expr1 = Util.getReal(val1, z3Context);
    BitVecExpr zero = z3Context.mkBV(0, 32);
    BitVecExpr negOne = z3Context.mkBV(-1, 32);
    BitVecExpr posOne = z3Context.mkBV(1, 32);
    
    Expr expr3 = z3Context.mkITE(
        z3Context.mkEq(expr1, expr2), zero,
          z3Context.mkITE(
            z3Context.mkLt(expr1, expr2), negOne, posOne));
    currentStackFrame.pushValue(new Value(expr3, Value.SYM | Value.INT32));
  }
  
  public void compareDoubleGreater(String opcode) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - compareDoubleGreater");
    // ----------debug tail---------- */
    
    Value val2 = currentStackFrame.popValue();
    Value val1 = currentStackFrame.popValue();
    
    boolean sym2 = val2.isType(Value.SYM);
    boolean sym1 = val1.isType(Value.SYM);
    
    if (!sym2 && !sym1) {
      double con2 = (Double) val2.getValue();
      double con1 = (Double) val1.getValue();
      int res = (con1 == con2) ? 0 : (con1 < con2 ? -1 : 1);
      currentStackFrame.pushValue((new Value(res, Value.INT32)));
      return;
    }
    
    ArithExpr expr2 = Util.getReal(val2, z3Context);
    ArithExpr expr1 = Util.getReal(val1, z3Context);
    BitVecExpr zero = z3Context.mkBV(0, 32);
    BitVecExpr negOne = z3Context.mkBV(-1, 32);
    BitVecExpr posOne = z3Context.mkBV(1, 32);
    
    Expr expr3 = z3Context.mkITE(
        z3Context.mkEq(expr1, expr2), zero,
          z3Context.mkITE(
            z3Context.mkLt(expr1, expr2), negOne, posOne));
    currentStackFrame.pushValue(new Value(expr3, Value.SYM | Value.INT32));
  }
  
  public void executeIfReferenceEqual(Object con1, Object con2, int line, String method) {
    String opcode = "IF_ACMPEQ";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIfReferenceEqual", con1, con2);
    // ----------debug tail---------- */

    Value ref2 = currentStackFrame.popValue();
    Value ref1 = currentStackFrame.popValue();

    if (ref2.getValue() == null || ref1.getValue() == null) {
      return;
    }

    boolean isSymbolic2 = ref2.isType(Value.SYM);
    boolean isSymbolic1 = ref1.isType(Value.SYM);
    boolean isEqual = con1 == con2;

    /* ----------debug head--------- *@*/
    if (isSymbolic1 || isSymbolic2) {
      debugPrintBranch(threadId, opcode + " " + isEqual + " " + line + " " + method);
    } else {
      debugPrintInfo(threadId, DebugUtil.BRANCH, opcode + " " + isEqual + " " + line + " " + method);
    }
    // ----------debug tail---------- */

    if (!isSymbolic1 && !isSymbolic2) {
      return; // ignore non-symbolic compares
    }
    
    Object rawVal2 = ref2.getValue();
    Object rawVal1 = ref1.getValue();

    com.microsoft.z3.BoolExpr expr = z3Context.mkEq((com.microsoft.z3.Expr) rawVal2,
            (com.microsoft.z3.Expr) rawVal1);
    expr = isEqual ? expr : z3Context.mkNot(expr);
    z3Constraints.add(expr);

    /* ----------debug head--------- *@*/
    debugPrintConstraint(threadId, "added IF_ACMPEQ constraint: " + (isEqual ? "Eq" : "Eq + Not"));
    // ----------debug tail---------- */

    solvePC(threadId, line, isEqual, ConstraintType.PATH);

    // TODO: construct symbolic counterpart of the concrete object
  }
  
  public void executeIfReferenceNotEqual(Object con1, Object con2, int line, String method) {
    String opcode = "IF_ACMPNE";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIfReferenceNotEqual", con1, con2);
    // ----------debug tail---------- */

    Value ref2 = currentStackFrame.popValue();
    Value ref1 = currentStackFrame.popValue();

    if (ref2.getValue() == null || ref1.getValue() == null) {
      return;
    }

    boolean isSymbolic2 = ref2.isType(Value.SYM);
    boolean isSymbolic1 = ref1.isType(Value.SYM);
    boolean isNotEqual = con1 != con2;

    /* ----------debug head--------- *@*/
    if (isSymbolic1 || isSymbolic2) {
      debugPrintBranch(threadId, opcode + " " + isNotEqual + " " + line + " " + method);
    } else {
      debugPrintInfo(threadId, DebugUtil.BRANCH, opcode + " " + isNotEqual + " " + line + " " + method);
    }
    // ----------debug tail---------- */

    if (!isSymbolic1 && !isSymbolic2) {
      return; // ignore non-symbolic compares
    }
    
//    if (isSymbolic1 && isSymbolic2) {
      Object rawVal2 = ref2.getValue();
      Object rawVal1 = ref1.getValue();

      com.microsoft.z3.BoolExpr expr = z3Context.mkEq((com.microsoft.z3.Expr) rawVal2,
              (com.microsoft.z3.Expr) rawVal1);
      expr = isNotEqual ? expr : z3Context.mkNot(expr);
      z3Constraints.add(expr);

      /* ----------debug head--------- *@*/
      debugPrintConstraint(threadId, "added IF_ACMPNE constraint: " + (isNotEqual ? "Eq" : "Eq + Not"));
      // ----------debug tail---------- */

      solvePC(threadId, line, isNotEqual, ConstraintType.PATH);
//    }

    // TODO: construct symbolic counterpart of the concrete object
  }

  public void executeIfReferenceNull(Object con1) {
    String opcode = "IFNULL";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIfReferenceNull", con1);
    // ----------debug tail---------- */

    Value val = currentStackFrame.popValue();
    
    /* ----------debug head--------- *@*/
    if (val != null && val.isType(Value.SYM)) {
      debugPrintWarning(threadId, "Warning: not handling symbolic null reference comparison.");
    }
    // ----------debug tail---------- */
  }

  public void executeIfReferenceNonNull(Object con1) {
    String opcode = "IFNONNULL";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - executeIfReferenceNonNull", con1);
    // ----------debug tail---------- */

    Value val = currentStackFrame.popValue();

    /* ----------debug head--------- *@*/
    if (val != null && val.isType(Value.SYM)) {
      debugPrintWarning(threadId, "Warning: not handling symbolic null reference comparison.");
    }    
    // ----------debug tail---------- */
  }
  
  public void returnReference() {
    String opcode = "ARETURN";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - returnReference");
    debugPrintCallExit(threadId);
    // ----------debug tail---------- */
  }

  public void returnDouble() {
    String opcode = "DRETURN";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - returnDouble");
    debugPrintCallExit(threadId);
    // ----------debug tail---------- */
  }

  public void returnFloat() {
    String opcode = "FRETURN";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - returnFloat");
    debugPrintCallExit(threadId);
    // ----------debug tail---------- */
  }

  public void returnInteger() {
    String opcode = "IRETURN";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - returnInteger");
    debugPrintCallExit(threadId);
    // ----------debug tail---------- */
  }

  public void returnLong() {
    String opcode = "LRETURN";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - returnLong");
    debugPrintCallExit(threadId);
    // ----------debug tail---------- */
  }

  public void returnVoid() {
    String opcode = "RETURN";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - returnVoid");
    debugPrintCallExit(threadId);
    // ----------debug tail---------- */
  }
  
  public void returnStaticVoid() {
    String opcode = "RETURN";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - returnStaticVoid");
    debugPrintCallExit(threadId);
    // ----------debug tail---------- */
  }

  public void getField(Object conObj, String fieldName, String fieldType) {
    String opcode = "GETFIELD";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - getField", fieldName, fieldType);
    debugPrintInfo(threadId, DebugUtil.COMMAND, "  concrete data type: " + DataConvert.getClassType(conObj));
    // ----------debug tail---------- */
    
    Value ref = currentStackFrame.popValue();
    assertValueNonNull(ref, opcode, "ref");
    // DON'T COMMENT OUT! - this checks for fatal error & dumps stack contents if so.
    if (ref.getValue() == null) {
      exitError("getField: field entry '" + fieldName + "' - stack value is null, must be a refCnt");
      return;
    }
    
    // verify the entry is an Integer type
    assertUnsignedIntValue(ref, opcode, "objectMap index");
    Integer objCnt = (Integer) ref.getValue();
    Map<String, Value> obj = ExecWrapper.getReferenceObject(objCnt);
    if (obj == null) {
      exitError("getField: field entry '" + fieldName + "' - object value is null");
      return;
    }

    Value field = obj.get(fieldName);

    // Multiple contexts due to multithreading
    if (field != null && field.isType(Value.SYM)) {
      // if entry is a reference (to an array or object), just return the reference value.
      // Otherwise it is a symbolic expression that we need to translate and return.
      if (!DataConvert.getClassType(field.getValue()).equals("java.lang.Integer")) { // NOTE: THIS USES REFLECTION
        Expr expr = (Expr)field.getValue();
        field = new Value(expr.translate(z3Context), field.getType());

        /* ----------debug head--------- *@*/
        debugPrintInfo(threadId, DebugUtil.COMMAND, "Converted to new context!");
        // ----------debug tail---------- */
      }
    } 

    if (field == null) {
      /* ----------debug head--------- *@*/
      debugPrintInfo(threadId, DebugUtil.COMMAND, "- Field not found: " + fieldName);
      // ----------debug tail---------- */

      // determine if the field is an array
      if (fieldType.startsWith("[")) {
        // create a new array of primitives.
        // initially make it a size of 1 (we will fix the size later, when we know it) but
        // set the size value to 0, so we will know to update it.
        int elementType = DataConvert.getValueDataType(fieldType) & ~(Value.ARY | Value.MARY | Value.SYM);
        Value[] arraynew = Util.makeValueArray(threadId, elementType, 1, null);
        
        // place it in a combo and add to array map (set size to 0 since it is unknown)
        field = putArrayCombo(arraynew, new Value(0, Value.INT32), elementType, false);
      } else {
        // determine if the field is a primitive or string
        switch(fieldType) {
          case "Z":
            field = new Value((Boolean) conObj, Value.BLN);
            break;
          case "C":
            field = new Value((Character) conObj, Value.CHR);
            break;
          case "B":
            field = new Value((Byte) conObj, Value.INT8);
            break;
          case "S":
            field = new Value((Short) conObj, Value.INT16);
            break;
          case "I":
            field = new Value((Integer) conObj, Value.INT32);
            break;
          case "J":
            field = new Value((Long) conObj, Value.INT64);
            break;
          case "F":
            field = new Value((Float) conObj, Value.FLT);
            break;
          case "D":
            field = new Value((Double) conObj, Value.DBL);
            break;
          case "Ljava/lang/String;":
            field = new Value((String) conObj, Value.STR);
            break;
          default:
            // field is an object - create a new reference entry for the field
            Integer newCnt = ExecWrapper.putReferenceObject(new HashMap<>());
            field = new Value(newCnt, Value.REF);

            /* ----------debug head--------- *@*/
            debugPrintObjectField(threadId, objCnt, fieldName, field);
            // ----------debug tail---------- */
            break;
        }
      }

      // add the entry to list of fields for the object
      obj.put(fieldName, field);
    }

    /* ----------debug head--------- *@*/
    debugPrintObjectMap(threadId, objCnt, obj);
    // ----------debug tail---------- */

    currentStackFrame.pushValue(field);
  }

  // who is to blame: zzk
  public void putField(String fieldName) {
    String opcode = "PUTFIELD";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - putField", fieldName);
    // ----------debug tail---------- */
    
    Value val = currentStackFrame.popValue();
    Value ref = currentStackFrame.popValue();    
    
    // verify the entry is an Integer type
    assertUnsignedIntValue(ref, opcode, "objectMap index");
    int objCnt = (Integer) ref.getValue();
    Map<String, Value> obj = ExecWrapper.getReferenceObject(objCnt);
//    if (obj == null) {
//      exitError("putField: field entry '" + fieldName + "' - object value is null");
//      return;
//    }

    obj.put(fieldName, val);

    /* ----------debug head--------- *@*/
    debugPrintObjectField(threadId, objCnt, fieldName, val);
    // ----------debug tail---------- */
  }
  
  // who is to blame: zzk
  public void getStaticField(String fieldName, String fieldType) {
    String opcode = "GETSTATIC";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - getStaticField", fieldName, fieldType);
    // ----------debug tail---------- */
    
    Value staticField = ExecWrapper.getStaticFieldObject(fieldName);
    if (staticField == null) {
      /* ----------debug head--------- *@*/
      debugPrintInfo(threadId, DebugUtil.COMMAND, fieldName + " field not found - creating null field");
      // ----------debug tail---------- */

      // determine if the field is an array
      if (fieldType.startsWith("[")) {
        // create a new array of primitives.
        // initially make it a size of 1 (we will fix the size later, when we know it) but
        // set the size value to 0, so we will know to update it.
        int elementType = DataConvert.getValueDataType(fieldType) & ~(Value.ARY | Value.MARY | Value.SYM);
        Value[] arraynew = Util.makeValueArray(threadId, elementType, 1, null);
        
        // place it in a combo and add to array map (set size to 0 since it is unknown)
        staticField = putArrayCombo(arraynew, new Value(0, Value.INT32), elementType, false);
      } else {
        // determine if the field is a primitive or string
        switch(fieldType) {
          case "Z":
            staticField = new Value(null, Value.BLN);
            break;
          case "C":
            staticField = new Value(null, Value.CHR);
            break;
          case "B":
            staticField = new Value(null, Value.INT8);
            break;
          case "S":
            staticField = new Value(null, Value.INT16);
            break;
          case "I":
            staticField = new Value(null, Value.INT32);
            break;
          case "J":
            staticField = new Value(null, Value.INT64);
            break;
          case "F":
            staticField = new Value(null, Value.FLT);
            break;
          case "D":
            staticField = new Value(null, Value.DBL);
            break;
          case "Ljava/lang/String;":
            staticField = new Value(null, Value.STR);
            break;
          default:
            // field is an object - create a new reference entry for the field
            Integer objCnt = ExecWrapper.putReferenceObject(new HashMap<>());
            staticField = new Value(objCnt, Value.REF);

            /* ----------debug head--------- *@*/
            debugPrintObjectField(threadId, objCnt, fieldName + " (static)", null);
            // ----------debug tail---------- */
            break;
        }
      }

      // add the entry to list of fields for the object
      ExecWrapper.putStaticFieldObject(fieldName, staticField);
    }

    currentStackFrame.pushValue(staticField);
  }
  
  // who is to blame: zzk
  public void putStaticField(String fieldName) {
    String opcode = "PUTSTATIC";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - putStaticField", fieldName);
    // ----------debug tail---------- */
    
    Value val = currentStackFrame.popValue();
    ExecWrapper.putStaticFieldObject(fieldName, val);
  }

  // who is to blame: zzk
  public void newArray(int type) {
    String opcode = "ANEWARRAY";

    /* ----------debug head--------- *@*/
    debugPrintCommand_type(threadId, opcode + " - newArray", type, currentStackFrame.peekValue(0));
    // ----------debug tail---------- */
    
    if (type == 0) {
      exitError("newArray: (" + opcode + ") specified type is 0");
      return;
    }
    // all arrays (symbolic/concrete) are packaged in a combo structure
    
    // if the size is symbolic, we have to introduce a symbolic array; otherwise, concrete
    Value size = currentStackFrame.popValue();
    if (size.isType(Value.SYM)) {
      // if type is STR, make a symbolic string array
      // if type is FLT/DBL, make a symbolic real array
      // otherwise, type is REF/BLN/INT8/INT16/INT32/INT64/CHR, make a symbolic integer array
      ArrayExpr core = null;
      if ((type & Value.STR) != 0) {
        core = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), 
            z3Context.mkStringSort());
      } else if ((type & Value.FLT) != 0 || (type & Value.DBL) != 0) {
        core = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), 
            z3Context.mkRealSort());
      } else if ((type & Value.INT64) != 0) {
        core = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), 
            z3Context.mkBitVecSort(64));
      } else {
        core = z3Context.mkArrayConst("array", z3Context.mkBitVecSort(32), 
            z3Context.mkBitVecSort(32));
      }

      // create the array object entry in arrayMap and return reference to it
      Value arrayVal = putArrayCombo(core, size, type, true);
      currentStackFrame.pushValue(arrayVal);
    } else {
      // verify the entry is an Integer type
      assertUnsignedIntValue(size, opcode, "array size");
      
      // make a Value array with the concrete size and fill in each entry with default values
      Value[] core = NativeCode.newArrayNative((Integer) size.getValue(), type);

      // create the array object entry in arrayMap and return reference to it
      Value arrayVal = putArrayCombo(core, size, type, false);
      currentStackFrame.pushValue(arrayVal);
    }
  }

  public void multiNewArray(int type, int dimensions) {
    String opcode = "MULTIANEWARRAY";
    
    /* ----------debug head--------- *@*/
    debugPrintCommand_type(threadId, opcode + " - multiNewArray", type, dimensions + "");
    // ----------debug tail---------- */
    
    // type will never have ARY or MARY bits set
    if (type == 0) {
      exitError("multiNewArray: (" + opcode + ") specified type is 0");
      return;
    }

    // TODO: ignoring symbolic case for now

    // get the total array size (the size of each dimension is pulled from stack)
    ArrayList<Integer> dimArray = new ArrayList<>();
    for (int ix = 0; ix < dimensions; ix++) {
      Value valSize = currentStackFrame.popValue();
      assertUnsignedIntValue(valSize, opcode, "multi-dim array size");
      Integer dimSize = (Integer) valSize.getValue();
      dimArray.add(dimSize);
    }
    
    // create the multi-dimension array structure and return reference to it
    Value arrayVal = makeNewArrayCombo(dimArray, type);
    Value multiArray = makeNewMultiArray(arrayVal, dimArray, type);
    currentStackFrame.pushValue(multiArray);
  }
  
  public void arrayLength(int length) {
    String opcode = "ARRAYLENGTH";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - arrayLength", length);
    // ----------debug tail---------- */
    
    // in case the array has not been allocated, default length to 0
    Value array = currentStackFrame.popValue();
    Value conLength = null;

    if (array.isType(Value.SYM)) {
      // if symbolic, use size from symbolic stack
      if (array.isType(Value.MARY)) {
        // get the info for the multi-dimensional array
        int key = (Integer) array.getValue();
        ExecWrapper.MultiArrayInfo info = ExecWrapper.getMultiArrayInfo(key);
        if (info == null) {
          exitError("arrayLength: multi-array reference not found for: " + key);
          return;
        }
        ArrayList<Integer> dimensions = ExecWrapper.getMultiArraySizes(info.arrayRef);
        if (dimensions == null) {
          exitError("arrayLength: array reference not found for: " + info.arrayRef);
          return;
        }
        if (!dimensions.isEmpty()) {
          int offset = dimensions.size() - info.dim;
          int dimlength = offset < 0 ? 0 : dimensions.get(offset);
          conLength = new Value(dimlength, Value.INT32);
        }
      } else {
        Value[] combo = getArrayCombo(array, 0);
        conLength = combo[1];
      }
    } else {
      // if not symbolic, use the concrete length value
      conLength = new Value(length, Value.INT32);

      // update the array length from the length of the concrete value if length is incorrect
      if (!array.isType(Value.MARY)) {
        Value[] combo = getArrayCombo(array, 0);
        updateValueArrayLength(combo, length, array.getValue());
      }
    }

    currentStackFrame.pushValue(conLength);
  }

  public void checkInstanceOf() {
    String opcode = "INSTANCEOF";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - checkInstanceOf");
    // ----------debug tail---------- */
    
    // we do nothing here, let concrete execution decides whether instanceof is true or false
    currentStackFrame.popValue();
  }

  public void invoke(String opcode, String fullmethod) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - " + fullmethod);
    // ----------debug tail---------- */
  }
  
  public void invoke(Object conObj, int parmCount, String opcode, String fullmethod) {
    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - " + fullmethod);
    // ----------debug tail---------- */

    // pop off arguments
    for (int ix = 0; ix < parmCount; ix++) {
      currentStackFrame.popValue();
    }
        
    // push return value
    currentStackFrame.pushValue(new Value(conObj, Value.DBL));
  }

  public void invokeDynamicEnter() {
    String opcode = "INVOKEDYNAMIC";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - enter");
    // ----------debug tail---------- */

    // the following allows us to mark the agent log output when we enter/exit the INVOKEDYNAMIC
    //System.out.println("Executor.invokeDynamic - enter");

    // set flag to prevent agent Enter/Exit method calls from modifying stack
    invokeDynamic = true;
  }
  
  public void invokeDynamicExit(Object conObj) {
    String opcode = "INVOKEDYNAMIC";

    /* ----------debug head--------- *@*/
    debugPrintCommand(threadId, opcode + " - exit - return: " + DataConvert.getClassType(conObj));
    // ----------debug tail---------- */

    // the following allows us to mark the agent log output when we enter/exit the INVOKEDYNAMIC
    //System.out.println("Executor.invokeDynamic - exit");

    Value stackVal;
    stackVal = Util.makeNewRefValue(threadId, conObj);
    currentStackFrame.pushValue(stackVal);

    // restore flag for handling stack on method Enter/Exit calls
    invokeDynamic = false;
  }
  
  public void catchException(Throwable except, int line, String fullName) {
    /* ----------debug head--------- *@*/
    debugPrintException(threadId, except.toString(), line, fullName);
    // ----------debug tail---------- */

    // check if entry was in concrete list. if not, create new entry
    Value val = Util.makeNewRefValue(threadId, except);
    
    while (!currentStackFrame.getMethodName().equals(fullName) && !executionStack.empty()) {
      currentStackFrame = executionStack.pop();
      if (currentStackFrame != null) {
        currentStackFrame.setStackLevel(executionStack.size());
      }
      /* ----------debug head--------- *@*/
      debugPrintStackRestore(threadId, currentStackFrame.getMethodName(), executionStack.size());
      // ----------debug tail---------- */
    }

    // clear out the stack frame in which the exception is handled & push the exception object
    while (!currentStackFrame.isEmpty()) {
      currentStackFrame.popValue();
    }
    currentStackFrame.pushValue(val);
  }

  //================================================================
  // DANHELPER AGENT CALLBACK METHODS
  //================================================================
  
  // caller and callee both instrumented
  public void popFrameAndPush(boolean isVoid, boolean except) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-I Exit  - popFrameAndPush: isVoid = " + isVoid + ", except = " + except);
    // ----------debug tail---------- */

    if (executionStack.empty()) {
      exitError("popFrameAndPush: executionStack empty!");
    }
    if (isVoid || except) {
      currentStackFrame = executionStack.pop();
      if (currentStackFrame != null) {
        currentStackFrame.setStackLevel(executionStack.size());
      }
    } else {
      Value val = currentStackFrame.popValue();
      currentStackFrame = executionStack.pop();
      if (currentStackFrame != null) {
        currentStackFrame.setStackLevel(executionStack.size());
      }
      currentStackFrame.pushValue(val);
    }

    /* ----------debug head--------- *@*/
    debugPrintStackRestore(threadId, currentStackFrame.getMethodName(), executionStack.size());
    // ----------debug tail---------- */
  }
  
  public void createFrame(int numParams, int maxLocals) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-I Enter - createFrame: numParams = " + numParams + ", maxLocals = " + maxLocals);
    // ----------debug tail---------- */

    Deque<Value> valDeck = new LinkedList<>();
    for (int i = 0; i < numParams; ++i) {
      Value val = currentStackFrame.popValue();
      valDeck.addFirst(val);
    }

    executionStack.push(currentStackFrame);
    currentStackFrame = new StackFrame("dummy", maxLocals, threadId, executionStack.size());
    int index = 0;
    for (Value val : valDeck) {
      currentStackFrame.storeLocalVariable(index, val);
      // accomodate indexing offsets following a double or long
      if (val != null & val.isType(Value.INT64 | Value.DBL)) {
        index += 2;
      } else {
        ++index;
      }
    }
  }

  // caller uninstrumented, callee instrumented
  public void popFrame(boolean except) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Exit  - popFrame: except = " + except);
    // ----------debug tail---------- */

    if (!executionStack.isEmpty()) {
      currentStackFrame = executionStack.pop();
      if (currentStackFrame != null) {
        currentStackFrame.setStackLevel(executionStack.size());
      }

      /* ----------debug head--------- *@*/
      debugPrintStackRestore(threadId, currentStackFrame == null ? "null" : currentStackFrame.getMethodName(),
          executionStack.size());
      // ----------debug tail---------- */
    }
  }

  public void addBooleanParameter(boolean b, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addBooleanParameter: slot = " + slot + ", val = " + b);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(b, Value.BLN));
  }

  public void addCharParameter(char c, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addCharParameter: slot = " + slot + ", val = " + c);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(c, Value.CHR));
  }

  public void addByteParameter(byte b, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addByteParameter: slot = " + slot + ", val = " + b);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(b, Value.INT8));
  }

  public void addShortParameter(short s, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addShortParameter: slot = " + slot + ", val = " + s);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(s, Value.INT16));
  }

  public void addIntegerParameter(int i, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addIntegerParameter: slot = " + slot + ", val = " + i);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(i, Value.INT32));
  }

  public void addLongParameter(long l, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addLongParameter: slot = " + slot + ", val = " + l);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(l, Value.INT64));
  }

  public void addFloatParameter(float f, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addFloatParameter: slot = " + slot + ", val = " + f);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(f, Value.FLT));
  }

  public void addDoubleParameter(double d, int slot) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addDoubleParameter: slot = " + slot + ", val = " + d);
    // ----------debug tail---------- */

    currentStackFrame.storeLocalVariable(slot, new Value(d, Value.DBL));
  }

  public void addObjectParameter(Object obj, int slot) {
    /* ----------debug head--------- *@*/
    // package the object in a Value so we can display the contents
    int type = DataConvert.getClassType(obj).equals("java.lang.String") ? Value.STR : Value.REF;
    String showVal = DataConvert.valueToString(new Value(obj, type));
    debugPrintAgent(threadId, "U-I Enter - addObjectParameter: slot = " + slot + ", val = " + showVal);
    // ----------debug tail---------- */

    Value stackVal = Util.makeNewRefValue(threadId, obj);

    currentStackFrame.storeLocalVariable(slot, stackVal);
  }

  public void addArrayParameter(Object arr, int slot, int depth, int type) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - addArrayParameter: slot = " + slot +
            ", depth = " + depth + ", type = " + DataConvert.showValueDataType(type)); // + ", val = " + arr);
    // ----------debug tail---------- */

    if (arr == null) {
      exitError("addArrayParameter: array value was null");
      return;
    }
    
    Value newarr;
    
    // check the number of dimensions of the array
    if (depth == 1) {
      // simple array: create a Value-array from the array of primitives or Objects
      newarr = makeReturnArray(arr, type);
    } else {
      // multi-dimensional: extract the size of each dimension from the concrete object
      ArrayList<Integer> dimArray = new ArrayList<>();
      Object[] marray = (Object[]) arr; // first time thru, convert object to array of level 1
      for (int ix = 0; ix < depth; ix++) {
        if (ix > 0) { // subsequent times thru, convert current object into array of level 1
          marray = (Object[]) marray[0];
        }
        Integer dimSize = marray.length;
        dimArray.add(dimSize);

        /* ----------debug head--------- *@*/
        debugPrintInfo(threadId, DebugUtil.ARRAYS, "addArrayParameter: level " + ix + ": " + dimSize);
        // ----------debug tail---------- */
      }

      // now create the multi-dim array
      Value arrayVal = makeNewArrayCombo(dimArray, type);
      newarr = makeNewMultiArray(arrayVal, dimArray, type);
    }

    currentStackFrame.storeLocalVariable(slot, newarr);
  }

  public void beginFrame(int maxLocals) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Enter - beginFrame: maxLocals = " + maxLocals);
    // ----------debug tail---------- */
    
    executionStack.push(currentStackFrame);
    if (currentStackFrame != null) {
      currentStackFrame.setStackLevel(executionStack.size());
    }
    currentStackFrame = new StackFrame("dummy", maxLocals, threadId, executionStack.size());
  }

  public void pushIntegralType(int val, int type) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - pushIntegralType: val = " + val + ", type = " + DataConvert.showValueDataType(type));
    // ----------debug tail---------- */

    if (needReturnValue) {
      currentStackFrame.pushValue(new Value(val, type));
    }
    
    needReturnValue = true;
  }

  public void pushLongType(long val, int type) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - pushLonglType: val = " + val + ", type = " + DataConvert.showValueDataType(type));
    // ----------debug tail---------- */

    currentStackFrame.pushValue(new Value(val, Value.INT64));
  }

  public void pushFloatType(float val, int type) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - pushFloatType: val = " + val + ", type = " + DataConvert.showValueDataType(type));
    // ----------debug tail---------- */

    currentStackFrame.pushValue(new Value(val, Value.FLT));
  }

  public void pushDoubleType(double val, int type) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - pushDoubleType: val = " + val + ", type = " + DataConvert.showValueDataType(type));
    // ----------debug tail---------- */

    currentStackFrame.pushValue(new Value(val, Value.DBL));
  }

public void pushReferenceType(Object val, int type) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - pushReferenceType: type = " + 
        DataConvert.showValueDataType(type));
    // ----------debug tail---------- */

    // exit if we are running an INVOKEDYNAMIC opcode
    if (invokeDynamic) {
      return;
    }
    
    if (needReturnValue) {
      Value stackVal;
      if (type != Value.REF) {
        // this would be the STR type
        /* ----------debug head--------- *@*/
        debugPrintInfo(threadId, DebugUtil.AGENT, "  object: " + (String) val);
        // ----------debug tail---------- */

        stackVal = new Value(val, type);
      } else if (val == null) {
        /* ----------debug head--------- *@*/
        debugPrintInfo(threadId, DebugUtil.AGENT, "  object: null");
        // ----------debug tail---------- */

        stackVal = Util.makeNewRefValue(threadId, val);
      } else {
        // for REF type, we need to verify whether this is truly a REF or maybe an array or boxed
        // primitive - it was only marked as REF because the signature of the method was an
        // object type (i.e. was of the "Lxxxxx;" foemat). However, this could have been
        // Ljava.lang.Long; which is a boxed primitive or an array object. We must use reflection
        // here to determine this from the actual object itself (if it's not null, of course).

        // reflectively get the object's class
        String classType = DataConvert.getClassType(val); // NOTE: THIS USES REFLECTION
        
        /* ----------debug head--------- *@*/
        debugPrintInfo(threadId, DebugUtil.AGENT, "  class: " + classType);
        // ----------debug tail---------- */

        // if it is an array, we will handle it as such instead of as a reference
        int depth = 0;
        type = DataConvert.getValueDataType(classType) & ~(Value.ARY | Value.MARY);
        while (classType.startsWith("[")) {
          classType = classType.substring(1);
          ++depth;
        }

        if (depth > 1) {
          // multi-dimensional array
          stackVal = makeReturnMultiArray(val, type, depth);
        } else if (depth > 0) {
          // array value
          stackVal = makeReturnArray(val, type);
        } else {
          // reference value
          stackVal = Util.makeNewRefValue(threadId, val);
        }
      }

      currentStackFrame.pushValue(stackVal);
    }
    
    needReturnValue = true;
  }

  public void pushArrayType(Object val, int type, int depth) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - pushArrayType: type = "
        + DataConvert.showValueDataType(type) + ", depth = " + depth);
    // ----------debug tail---------- */

    if (val == null) {
      exitError("pushArrayType: array object is null");
    }
    
    Value ary;
    if (depth > 1) {
      ary = makeReturnMultiArray(val, type, depth);
    } else {
      ary = makeReturnArray(val, type);
    }

    currentStackFrame.pushValue(ary);
  }

  public void pushVoidType() {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - pushVoidType");
    // ----------debug tail---------- */
  }
  
  public void objectCloneExit() {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - objectCloneExit");
    // ----------debug tail---------- */
  }
  
  public void exception() {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "U-I Exit  - exception");
    // ----------debug tail---------- */
  }

  public void removeParams(int numParams) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Enter - removeParams: numParams = " + numParams);
    // ----------debug tail---------- */

    // exit if we are running an INVOKEDYNAMIC opcode
    if (invokeDynamic) {
      return;
    }
    
    for (int i = 0; i < numParams; i++) {
      currentStackFrame.popValue();
    }
  }

  public void objectCloneEnter() {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Enter - objectCloneEnter");
    // ----------debug tail---------- */

    Value val = currentStackFrame.popValue();

    int type = val.getType();
    if (type == Value.REF) { // reference type
      // get the object being cloned
      assertUnsignedIntValue(val, "objectCloneEnter", "objectMap index");
      Integer refCnt = (Integer) val.getValue();
      Map<String, Value> obj = ExecWrapper.getReferenceObject(refCnt);

      // create a new object reference and set its value equal to the previous
      int objCnt = ExecWrapper.putReferenceObject(obj);

      /* ----------debug head--------- *@*/
      debugPrintObjectMap(threadId, objCnt, obj);
      // ----------debug tail---------- */

      // return the cloned reference object on the stack
      currentStackFrame.pushValue(new Value(objCnt, Value.REF));
    } else if ((type & Value.ARY) != 0) { // array type
      // get the array being cloned
      Value[] refcombo = getArrayCombo(val, type);

      // create a new array reference and set its value equal to the previous
      Object array = refcombo[0].getValue();
      int arrtype = refcombo[0].getType();
      Value size = refcombo[1];
      boolean isSymbolic = array instanceof com.microsoft.z3.ArrayExpr;
      Value arrayVal = putArrayCombo(array, size, arrtype, isSymbolic);
      
      // return the cloned array object on the stack
      currentStackFrame.pushValue(arrayVal);
    } else if ((type & Value.MARY) != 0) {
      // create a new multi-array and copy the info to it
      Value multiArray = cloneMultiArray((Integer) val.getValue());
      
      // return the cloned multi-array object on the stack
      currentStackFrame.pushValue(multiArray);
    } else {
      exitError("objectCloneEnter: Invalid object type being cloned: " +
          DataConvert.showValueDataType(type));
    }

    // (NOTE: this function performs both the I-U Entry and the U-I Exit functionality because
    // we won't have access to the info to push onto the stack unless we do it here.
    // We compensate by not doing anything for the Exit function.)
  }
  
  public void stringLength() {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Enter - stringLength");
    // ----------debug tail---------- */
    
    Value val = currentStackFrame.popValue();
    if (val.isType(Value.SYM) && val.isType(Value.STR)) {
//      System.out.println("Pushing symbolic length onto stack!");
      com.microsoft.z3.SeqExpr expr = (com.microsoft.z3.SeqExpr) val.getValue();
      BitVecExpr bvExpr = z3Context.mkInt2BV(32, z3Context.mkLength(expr));
      currentStackFrame.pushValue(new Value(bvExpr, Value.INT32 | Value.SYM));
      needReturnValue = false;
    }
  }
  
  public void stringEquals(String conStr1, Object conObj) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Enter - stringEqualsMethod");
    // ----------debug tail---------- */

    Value str2 = currentStackFrame.popValue();
    Value str1 = currentStackFrame.popValue();
    
    if (!(conObj instanceof String))
      return;
    
    String conStr2 = (String) conObj;
//    System.out.println("Concrete string is: " + conStr2);

    if (!str1.isType(Value.SYM) && !str2.isType(Value.SYM)) {
//      System.out.println("No symbolics in string equals");
      return;
    }

    com.microsoft.z3.Expr expr1 = str1.isType(Value.SYM) ? (com.microsoft.z3.Expr) str1.getValue()
            : z3Context.mkString(conStr1);

    com.microsoft.z3.Expr expr2 = str2.isType(Value.SYM) ? (com.microsoft.z3.Expr) str2.getValue()
            : z3Context.mkString(conStr2);

    com.microsoft.z3.Expr expr3 = z3Context.mkITE(z3Context.mkEq(expr1, expr2),
            z3Context.mkBV(1, 32), z3Context.mkBV(0, 32));
                       
    currentStackFrame.pushValue(new Value(expr3, Value.SYM | Value.BLN));
    needReturnValue = false;
  }
  
  public void stringSubstring(String conStr, int conOffset) {
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Enter - stringSubstringMethod");
    // ----------debug tail---------- */

    Value off = currentStackFrame.popValue();
    Value str = currentStackFrame.popValue();

    if (!off.isType(Value.SYM) && !str.isType(Value.SYM)) {
//      System.out.println("No symbolics in string substring");
      return;
    } else if (off.isType(Value.SYM) && str.isType(Value.SYM)) {
      com.microsoft.z3.BitVecExpr symOff = (com.microsoft.z3.BitVecExpr) off.getValue();
      com.microsoft.z3.SeqExpr symStr = (com.microsoft.z3.SeqExpr) str.getValue();
      com.microsoft.z3.SeqExpr symRes
              //= z3Context.mkExtract(symStr, z3Context.mkInt(0), z3Context.mkBV2Int(symOff, true));
              = z3Context.mkExtract(symStr, 
                                    z3Context.mkBV2Int(symOff, true), 
                                    (IntExpr) z3Context.mkSub(
                                            z3Context.mkLength(symStr), 
                                            z3Context.mkBV2Int(symOff, true)));
      currentStackFrame.pushValue(new Value(symRes, Value.SYM | Value.STR));
    } else if (off.isType(Value.SYM)) {
      com.microsoft.z3.BitVecExpr symOff = (com.microsoft.z3.BitVecExpr) off.getValue();
      com.microsoft.z3.SeqExpr symStr = z3Context.mkString(conStr);
      com.microsoft.z3.SeqExpr symRes
              //= z3Context.mkExtract(symStr, z3Context.mkInt(0), z3Context.mkBV2Int(symOff, true));
              = z3Context.mkExtract(symStr, 
                                    z3Context.mkBV2Int(symOff, true), 
                                    (IntExpr) z3Context.mkSub(
                                            z3Context.mkLength(symStr), 
                                            z3Context.mkBV2Int(symOff, true)));

      currentStackFrame.pushValue(new Value(symRes, Value.SYM | Value.STR));
    } else {
      com.microsoft.z3.SeqExpr symStr = (com.microsoft.z3.SeqExpr) str.getValue();
      com.microsoft.z3.IntExpr symLen = z3Context.mkLength(symStr);
      // TODO: Z3 bug causes some solutions to be printed as ""
      // The line below will test that it can find a valid solution
      //symLen = (IntExpr) z3Context.mkSub(symLen, z3Context.mkInt(1));
      com.microsoft.z3.IntExpr symOff = z3Context.mkInt(conOffset);
      com.microsoft.z3.IntExpr symSize = (com.microsoft.z3.IntExpr) z3Context.mkSub(symLen, symOff);

      com.microsoft.z3.SeqExpr symRes
              = z3Context.mkExtract(symStr, symOff, symSize);

      currentStackFrame.pushValue(new Value(symRes, Value.SYM | Value.STR));
    }
    
    System.out.println("Pushed symbolic substring onto stack!");
    needReturnValue = false;
  }
  
  public void bufferedImageGetRGB(Object objImage, int x, int y) {
//    System.out.println("Inside symbolic buffered image");
    
    Value symY = currentStackFrame.popValue();
    Value symX = currentStackFrame.popValue();
    Value symImage = currentStackFrame.popValue();
    boolean bSym = symImage.isType(Value.SYM);
    
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Enter - bufferedImageGetRGB: (" + x + "," + y + ") = " +
        (bSym ? "(Symbolic)" : "(Concrete)"));
    // ----------debug tail---------- */
    
    if (!bSym) {
      return;
    }
    
    System.out.println("bufferedImageGetRGB: symbolic: (" + x + "," + y + ")");
    java.awt.image.BufferedImage image = (java.awt.image.BufferedImage)objImage;
    Value[][] pixels = null;
    
    if (bufferedImageMap.containsKey(objImage)) {
      pixels = bufferedImageMap.get(objImage);
    } else {
      int width = image.getWidth();
      int height = image.getHeight();
      pixels = new Value[width][height];
      for (int i = 0; i < width; i++) {
        for (int j = 0; j < height; j++) {
          pixels[i][j] = new Value(makeLong("x_" + i + "_y_" + j, Value.INT32), 
                                   Value.INT32 | Value.SYM);
        }
      }
      bufferedImageMap.put(image, pixels);
    }
    
    currentStackFrame.pushValue(pixels[x][y]);
    needReturnValue = false;
  }
  
  public void bufferedImageSetRGB(Object objImage, int x, int y, int rgb) {
//    System.out.println("Inside symbolic buffered image set rgb");
    
    Value symRGB = currentStackFrame.popValue();
    Value symY = currentStackFrame.popValue();
    Value symX = currentStackFrame.popValue();
    Value symImage = currentStackFrame.popValue();
    boolean bSym = symImage.isType(Value.SYM);
    
    /* ----------debug head--------- *@*/
    debugPrintAgent(threadId, "I-U Enter - bufferedImageSetRGB: (" + x + "," + y + ") = " + rgb +
        (bSym ? "(Symbolic)" : "(Concrete)"));
    // ----------debug tail---------- */
    
    if (!bSym) {
      return;
    }
    
    System.out.println("bufferedImageSetRGB: symbolic: (" + x + "," + y + ") = " + rgb);
    java.awt.image.BufferedImage image = (java.awt.image.BufferedImage)objImage;
    Value[][] pixels = null;
    
    if (bufferedImageMap.containsKey(objImage)) {
      pixels = bufferedImageMap.get(objImage);
    } else {
      int width = image.getWidth();
      int height = image.getHeight();
      pixels = new Value[width][height];
      for (int i = 0; i < width; i++) {
        for (int j = 0; j < height; j++) {
          pixels[i][j] = new Value(makeLong("x_" + i + "_y_" + j, Value.INT32), 
                                   Value.INT32 | Value.SYM);
        }
      }
      bufferedImageMap.put(image, pixels);
    }
    
    pixels[x][y] = symRGB;
  }
  
  public void bufferedReaderReadLine() {
    Expr expr = z3Context.mkConst("bufferedReaderString", z3Context.mkStringSort());
    currentStackFrame.pushValue(new Value(expr, Value.STR | Value.SYM));
    needReturnValue = false;
  }
  
  public void integerParseInt() {
    Value val = currentStackFrame.popValue();
    if (val.isType(Value.SYM | Value.STR)) {
      Value ret = new Value(makeLong("parsedInt", Value.INT32), Value.INT32 | Value.SYM);
      currentStackFrame.pushValue(ret);
      needReturnValue = false;
    }
  }
}