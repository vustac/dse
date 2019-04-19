/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danalyzer.executor;

import danalyzer.gui.DataConvert;
import static danalyzer.gui.DebugUtil.debugPrintStart;
import static danalyzer.gui.DebugUtil.debugPrintInfo;
import danalyzer.gui.DebugUtil;
import danalyzer.gui.GuiPanel;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.common.collect.MapMaker;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author dmcd2356
 */
public class ExecWrapper {
  private static boolean                          bFirstThread = false;
  private static Map<Integer, Executor>           executorMap = new HashMap<>();

  // User-defined symbolic parameters and constraints:
  // symbolicList - list of all symbolic parameters and their associated constraints
  private static ArrayList<Executor.UserSymbolic> symbolicList = new ArrayList<>();

  // FIELDS:
  // staticFieldMap - static fields: K = field name, V = field Value
  private static Map<String, Value>               staticFieldMap = new HashMap<>();

  // OBJECTS:
  // objectCount  - key value for looking up in objectMap
  // objectMap    - Object references: K = refCnt, V = <field name, field Value>
  // concreteObjectLookup - concrete values for objects: K = Object value, V = refCnt
  private static int                              objectCount = 1;
  private static Map<Integer, Map<String, Value>> objectMap = new HashMap<>();
  //private static Map<Object, Integer>             concreteObjectLookup = new IdentityHashMap<>();
  private static ConcurrentMap<Object, Integer>     concreteObjectLookup = new MapMaker().weakKeys().makeMap();
  
  // ARRAYS:
  // arrayCount - key value for looking up in arrayMap
  // arrayMap   - array values: K = refCnt, V = combo entry (0 = array of values, 1 = # of entries)
  // multiCount - key value for looking up in multiMap
  // multiSizes - multi-dim array sizes: K = ref to full array, V = size of each dimension for the array
  // multiMap   - multi-dim array references: K = refCnt, V = multi-dimensional array info
  private static int                              arrayCount = 1;
  //private static Map<Integer, Value[]>            arrayMap = new HashMap<>();
  private static ConcurrentMap<Integer, Value[]>  arrayMap = new MapMaker().weakKeys().makeMap();
  private static int                              multiCount = 1;
  private static Map<Integer, ArrayList<Integer>> multiSizes = new HashMap<>();
  private static Map<Integer, MultiArrayInfo>     multiMap = new HashMap<>();
  
  private static class ThreadId {
    // Atomic integer containing the next thread ID to be assigned
    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    // Thread local variable containing each thread's ID
    private static final ThreadLocal<Integer>  THREAD_ID = new ThreadLocal<Integer>() {
      @Override
      protected Integer initialValue() {
        return NEXT_ID.getAndIncrement();
      }
    };

    // Returns the current thread's unique ID, assigning it if necessary
    public static int get() {
      return THREAD_ID.get();
    }
  }

  public static class MultiArrayInfo {
    public final int initRef;  // key value into multiMap to initial multi-dim allocation
    public final int arrayRef; // key value into arrayMap to full multi-dim array
    public final int type;     // type of elements in full array
    public final int dim;      // number of dimensions for this portion
    public final int offset;   // offset into full array to start of this portion
    
    public MultiArrayInfo(int initKey, int arrayKey, int elementType, int dimNum, int off) {
      initRef = initKey;
      arrayRef = arrayKey;
      type = elementType;
      dim = dimNum;
      offset = off;
    }
  }
  
  /**
   * gets the Executor for the specified thread.
   * Each thread gets its own Executor. If one is not found, creates a new one.
   * 
   * @return the Executor to run.
   */
  private static Executor getExecSelect() {
    int tid = ThreadId.get(); // long tid = Thread.currentThread().getId();
    Executor exec = executorMap.get(tid);
    if (exec == null) {
      exec = new Executor(tid, symbolicList);
      executorMap.put(tid, exec);
    }
    
    return exec;
  }
  
  // allows the user to reset the Executor so a second run can be performed.
  public static void reset() {
    debugPrintInfo(ThreadId.get(), "--- USER RESET STARTED ---");
    System.out.println("reset started");
    DebugUtil.debugReset();

    // clear out the Executors for each thread
    executorMap.clear();

    // clear our lists of objects, but not the user-supplied symbolics & constraints
    objectCount = 1;
    objectMap.clear();
    concreteObjectLookup.clear();
    staticFieldMap.clear();
    arrayCount = 1;
    arrayMap.clear();
    multiCount = 1;
    multiSizes.clear();
    multiMap.clear();
  }
  
  //================================================================
  // ACCESSOR METHODS FOR OBJECTS SHARED BETWEEN THREADS
  //================================================================

  public static synchronized int putComboValue(Value[] combo) {
    int key = arrayCount++;
    arrayMap.put(key, combo);
    return key;
  }
  
  public static synchronized void updateComboValue(int key, Value[] combo) {
    arrayMap.put(key, combo);
  }
  
  public static synchronized void putMultiArraySizes(int key, ArrayList<Integer> dimensions) {
    multiSizes.put(key, dimensions);
  }
  
  public static synchronized int putMultiArrayInfo(int arrayRef, int type, int size, int offset) {
    int key = multiCount++;
    MultiArrayInfo info = new MultiArrayInfo(key, arrayRef, type, size, offset);
    multiMap.put(key, info);
    return key;
  }
  
  public static synchronized int addMultiArrayInfo(int arrayRef, int type, int size, int offset,
                                                   int initKey) {
    int key = multiCount++;
    MultiArrayInfo info = new MultiArrayInfo(initKey, arrayRef, type, size, offset);
    multiMap.put(key, info);
    return key;
  }
  
  public static synchronized int putReferenceObject(Map<String, Value> mapval) {
    int objCnt = objectCount++;
    objectMap.put(objCnt, mapval);
    return objCnt;
  }

  // TODO: should be able to make this non-synchronized now that a ConcurrentMap is used
  public static synchronized boolean putConcreteObject(Object conObject, int objRef) {
    if (conObject == null || concreteObjectLookup.get(conObject) != null) {
      return false;
    }
    concreteObjectLookup.put(conObject, objRef);
    return true;
  }
  
  public static synchronized void putStaticFieldObject(String fieldName, Value obj) {
    staticFieldMap.put(fieldName, obj);
  }
  
  public static Value[] getComboValue(int key) {
    return (Value[]) arrayMap.get(key);
  }
  
  public static ArrayList<Integer> getMultiArraySizes(int key) {
    return multiSizes.get(key);
  }
  
  public static MultiArrayInfo getMultiArrayInfo(int key) {
    return multiMap.get(key);
  }
  
  public static Map<String, Value> getReferenceObject(int key) {
    return objectMap.get(key);
  }
  
  public static Integer getConcreteObject(Object conObject) {
    return concreteObjectLookup.get(conObject);
  }
  
  public static Value getStaticFieldObject(String fieldName) {
    return staticFieldMap.get(fieldName);
  }
  
  public static int getMultiSize() {
    return multiCount;
  }
  
  //================================================================
  // GUI SETTINGS METHODS
  //================================================================
  
  /**
   * allows the user to add symbolic parameters.
   * 
   * @param param    - index of the parameter (0 = 'this', passed args next, locally defined last)
   * @param methName - name of method the parameter is referenced to
   * @param start    - start line number within method for parameter usage
   * @param end      - end   line number within method for parameter usage
   * @param name     - name of parameter (to identify it)
   * @param type     - data type for parameter
   */
  public static void addSymbolicEntry(String methName, int param, int start, int end, String name, String type) {
    // convert data type to a Value type
    int valtype = Executor.UserSymbolic.convertDataType(type);

    // add entry to list for constraint reference
    System.out.println("Added Symbolic Entry " + symbolicList.size() + ": param: " + param +
        ", method: " + methName + ", start: " + start + ", end: " + end +
        ", name: " + name + ", type: " + type + " (" + DataConvert.showValueDataType(valtype)+ ")");
    
    // create a new symbolic entry
    Executor.UserSymbolic entry = new Executor.UserSymbolic(methName, param, start, end, name, valtype);
    String keyRef = entry.getKeyReference();

    // make sure entry doesn't already exist
    for (Executor.UserSymbolic listVal : symbolicList) {
      if (listVal.id.equals(name)) {
        System.err.println("Symbolic entry '" + name + "' (name) already exists");
        return;
      }
      if (listVal.getKeyReference().equals(keyRef)) {
        System.err.println("Symbolic entry '" + keyRef + "' (key ref) already exists");
        return;
      }
    }
    
    symbolicList.add(entry);
  }
  
  /**
   * allows the user to add constraints to symbolic parameters.
   * 
   * @param id   - name associated with the symbolic parameter
   * @param type - constraint comparison type: EQ, NE, GT, GE, LT, LE
   * @param data - constraint comparison value
   * @return - true if successfully added
   */
  public static boolean addUserConstraint(String id, String type, String data) {
    // validate inputs
    if (symbolicList.isEmpty()) {
      System.err.println("Symbolic entry not defined yet!");
      return false;
    }

    if (data == null || type == null) {
      System.err.println("NULL value passed in constraint definition");
      return false;
    }

    switch (type) {
      case "EQ":
      case "NE":
      case "GT":
      case "GE":
      case "LT":
      case "LE":
        break;
      default:
        System.err.println("Invalid constraint equality: " + type);
        return false;
    }

    for (Executor.UserSymbolic entry : symbolicList) {
      if (entry.id.equals(id)) {
        // add the entry to the constraint list
        Executor.UserConstraint constraint = new Executor.UserConstraint(type, data);
        if (!entry.constraint.contains(constraint)) {
          entry.constraint.add(constraint);

          System.out.println("Added User Constraint " + entry.constraint.size() +
              ": " + type + " " + data + " to param '" + id + "'");
        }
        return true;
      }
    }

    System.err.println("Symbolic not defined for param '" + id + "'");
    return false;
  }  

  //================================================================
  // INSTRUMENTER METHODS
  //================================================================
  
  public static void unimplementedOpcode(String opcode) {
    Executor exec = getExecSelect();
    if (exec != null) exec.unimplementedOpcode(opcode);
  }

  public static void noActionOpcode(String opcode) {
    Executor exec = getExecSelect();
    if (exec != null) exec.noActionOpcode(opcode);
  }

  public static void initializeExecutor(String[] argv) {
    Executor exec = getExecSelect();
    if (exec != null) exec.initializeExecutor(argv);
  }

  public static void establishStackFrame(String fullname, int paramCnt) {
    Executor exec = getExecSelect();
    if (exec != null) exec.establishStackFrame(fullname, paramCnt);
  }

  public static void newStringObject() {
    Executor exec = getExecSelect();
    if (exec != null) exec.newStringObject();
  }
  
  public static void newObject(String clsName) {
    Executor exec = getExecSelect();
    if (exec != null) exec.newObject(clsName);
  }  

  public static void loadConstant(int val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadConstant(val);
  }

  public static void loadConstant(long val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadConstant(val);
  }

  public static void loadConstant(float val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadConstant(val);
  }

  public static void loadConstant(double val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadConstant(val);
  }
  
  public static void loadConstant(Object val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadConstant(val);
  }

  public static void loadConstant(String val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadConstant(val);
  }

  public static void pushByteAsInteger(int val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushAsInteger("BIPUSH", val);
  }
  
  public static void pushShortAsInteger(int val) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushAsInteger("SIPUSH", val);
  }
  
  public static void loadLocalReference(Object conObj, int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadLocalReference(conObj, index, opline);
  }
  
  public static void loadDouble(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadDouble(index, opline);
  }

  public static void loadFloat(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadFloat(index, opline);
  }

  public static void loadInteger(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadInteger(index, opline);
  }

  public static void loadLong(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadLong(index, opline);
  }

  public static void storeLocalReference(Object conObject, int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.storeLocalReference(conObject, index, opline);
  }
  
  public static void storeDouble(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.storeDouble(index, opline);
  }

  public static void storeFloat(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.storeFloat(index, opline);
  }

  public static void storeInteger(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.storeInteger(index, opline);
  }

  public static void storeLong(int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.storeLong(index, opline);
  }

  public static void loadReferenceFromArray(Object conObj, int index, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.loadReferenceFromArray(conObj, index, opline);
  }
  
  public static void readCharArray(char[] conArray, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.readCharArray(conArray, opline);
  }
  
  public static void readByteArray(byte[] conArray, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.readByteArray(conArray, opline);
  }
  
  public static void readShortArray(short[] conArray, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.readShortArray(conArray, opline);
  }
  
  public static void readIntegerArray(int[] conArray, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.readIntegerArray(conArray, opline);
  }
  
  public static void readLongArray(long[] conArray, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.readLongArray(conArray, opline);
  }
  
  public static void readFloatArray(float[] conArray, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.readFloatArray(conArray, opline);
  }
  
  public static void readDoubleArray(double[] conArray, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.readDoubleArray(conArray, opline);
  }
  
  public static void writePrimitiveArray(int type, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.writePrimitiveArray(type, opline);
  }

  public static void arrayStore(Object[] conObj, int conIndex, int opline) {
    Executor exec = getExecSelect();
    if (exec != null) exec.arrayStore(conObj, conIndex, opline);
  }

  public static void executePop() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executePop();
  }

  public static void executePop2() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executePop2();
  }

  public static void executeDup() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeDup();
  }

  public static void executeDup_x1() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeDup_x1();
  }

  public static void executeDup_x2() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeDup_x2();
  }

  public static void executeDup2() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeDup2();
  }

  public static void executeDup2_x1() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeDup2_x1();
  }

  public static void executeDup2_x2() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeDup2_x2();
  }

  public static void executeSwap() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeSwap();
  }

  public static void addDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.addDouble();
  }

  public static void subDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.subDouble();
  }

  public static void mulDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.mulDouble();
  }

  public static void divDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.divDouble();
  }

  public static void remDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.remDouble();
  }

  public static void negDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.negDouble();
  }

  public static void addFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.addFloat();
  }

  public static void subFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.subFloat();
  }

  public static void mulFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.mulFloat();
  }

  public static void divFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.divFloat();
  }

  public static void remFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.remFloat();
  }

  public static void negFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.negFloat();
  }

  public static void incInteger(int var, int incr) {
    Executor exec = getExecSelect();
    if (exec != null) exec.incInteger(var, incr);
  }

  public static void addInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.addInteger();
  }

  public static void subInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.subInteger();
  }

  public static void mulInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.mulInteger();
  }

  public static void divInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.divInteger();
  }

  public static void remInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.remInteger();
  }

  public static void negInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.negInteger();
  }

  public static void addLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.addLong();
  }

  public static void subLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.subLong();
  }

  public static void mulLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.mulLong();
  }

  public static void divLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.divLong();
  }

  public static void remLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.remLong();
  }

  public static void negLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.negLong();
  }

  public static void executeIntegerAnd() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIntegerAnd();
  }

  public static void executeIntegerOr() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIntegerOr();
  }

  public static void executeIntegerXor() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIntegerXor();
  }

  public static void executeIntegerShiftLeft() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIntegerShiftLeft();
  }

  public static void executeIntegerArithShiftRight() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIntegerArithShiftRight();
  }

  public static void executeIntegerShiftRight() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIntegerShiftRight();
  }

  public static void executeLongAnd() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeLongAnd();
  }

  public static void executeLongOr() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeLongOr();
  }

  public static void executeLongXor() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeLongXor();
  }

  public static void executeLongShiftLeft() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeLongShiftLeft();
  }

  public static void executeLongArithShiftRight() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeLongArithShiftRight();
  }

  public static void executeLongShiftRight() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeLongShiftRight();
  }

  public static void i_convertToDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToDouble('I');
  }

  public static void l_convertToDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToDouble('L');
  }

  public static void f_convertToDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToDouble('F');
  }

  public static void i_convertToFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToFloat('I');
  }

  public static void l_convertToFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToFloat('L');
  }

  public static void d_convertToFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToFloat('D');
  }

  public static void i_convertToLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToLong('I');
  }

  public static void f_convertToLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToLong('F');
  }

  public static void d_convertToLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToLong('D');
  }

  public static void l_convertToInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToInteger('L');
  }

  public static void f_convertToInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToInteger('F');
  }

  public static void d_convertToInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToInteger('D');
  }

  public static void convertToShort() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToShort();
  }

  public static void convertToChar() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToChar();
  }

  public static void convertToByte() {
    Executor exec = getExecSelect();
    if (exec != null) exec.convertToByte();
  }

  public static void executeLongCompare() {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeLongCompare();
  }
    
  public static void compareIntegerEQ(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerEquality("IFEQ", con1, con2, opline, method);
  }
  
  public static void compareIntegerNE(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerEquality("IFNE", con1, con2, opline, method);
  }
  
  public static void compareIntegerGT(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerGreaterThan("IFGT", con1, con2, opline, method);
  }
  
  public static void compareIntegerLE(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerGreaterThan("IFLE", con1, con2, opline, method);
  }
  
  public static void compareIntegerLT(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerLessThan("IFLT", con1, con2, opline, method);
  }
  
  public static void compareIntegerGE(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerLessThan("IFGE", con1, con2, opline, method);
  }
  
  public static void compareIntegerICMPEQ(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerEquality("IF_ICMPEQ", con1, con2, opline, method);
  }
  
  public static void compareIntegerICMPNE(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerEquality("IF_ICMPNE", con1, con2, opline, method);
  }
  
  public static void compareIntegerICMPGT(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerGreaterThan("IF_ICMPGT", con1, con2, opline, method);
  }
  
  public static void compareIntegerICMPLE(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerGreaterThan("IF_ICMPLE", con1, con2, opline, method);
  }
  
  public static void compareIntegerICMPLT(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerLessThan("IF_ICMPLT", con1, con2, opline, method);
  }
  
  public static void compareIntegerICMPGE(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareIntegerLessThan("IF_ICMPGE", con1, con2, opline, method);
  }
  
  public static void maxCompareIntegerICMPGE(int con1, int con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.maxCompareIntegerLessThan("IF_ICMPGE", con1, con2, opline, method);
  }
  
  public static void compareFloatGreater() {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareFloatGreater("FCMPG");
  }
  
  public static void compareFloatLess() {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareFloatGreater("FCMPL");
  }
  
  public static void compareDoubleGreater() {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareDoubleGreater("DCMPG");
  }
  
  public static void compareDoubleLess() {
    Executor exec = getExecSelect();
    if (exec != null) exec.compareDoubleGreater("DCMPL");
  }
  
  public static void executeIfReferenceEqual(Object con1, Object con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIfReferenceEqual(con1, con2, opline, method);
  }
  
  public static void executeIfReferenceNotEqual(Object con1, Object con2, int opline, String method) {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIfReferenceNotEqual(con1, con2, opline, method);
  }

  public static void executeIfReferenceNull(Object con1) {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIfReferenceNull(con1);
  }

  public static void executeIfReferenceNonNull(Object con1) {
    Executor exec = getExecSelect();
    if (exec != null) exec.executeIfReferenceNonNull(con1);
  }
  
  public static void returnReference() {
    Executor exec = getExecSelect();
    if (exec != null) exec.returnReference();
  }

  public static void returnDouble() {
    Executor exec = getExecSelect();
    if (exec != null) exec.returnDouble();
  }

  public static void returnFloat() {
    Executor exec = getExecSelect();
    if (exec != null) exec.returnFloat();
  }

  public static void returnInteger() {
    Executor exec = getExecSelect();
    if (exec != null) exec.returnInteger();
  }

  public static void returnLong() {
    Executor exec = getExecSelect();
    if (exec != null) exec.returnLong();
  }

  public static void returnVoid() {
    Executor exec = getExecSelect();
    if (exec != null) exec.returnVoid();
  }
  
  public static void returnStaticVoid() {
    Executor exec = getExecSelect();
    if (exec != null) exec.returnStaticVoid();
  }

  public static void getField(Object conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(conObj, fieldName, fieldType);
  }

  public static void getField(boolean conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Boolean.valueOf(conObj), fieldName, fieldType);
  }

  public static void getField(char conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Character.valueOf(conObj), fieldName, fieldType);
  }

  public static void getField(byte conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Byte.valueOf(conObj), fieldName, fieldType);
  }

  public static void getField(short conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Short.valueOf(conObj), fieldName, fieldType);
  }

  public static void getField(int conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Integer.valueOf(conObj), fieldName, fieldType);
  }

  public static void getField(long conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Long.valueOf(conObj), fieldName, fieldType);
  }

  public static void getField(float conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Float.valueOf(conObj), fieldName, fieldType);
  }

  public static void getField(double conObj, String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getField(Double.valueOf(conObj), fieldName, fieldType);
  }

  public static void putField(String fieldName) {
    Executor exec = getExecSelect();
    if (exec != null) exec.putField(fieldName);
  }
  
  public static void getStaticField(String fieldName, String fieldType) {
    Executor exec = getExecSelect();
    if (exec != null) exec.getStaticField(fieldName, fieldType);
  }
  
  public static void putStaticField(String fieldName) {
    Executor exec = getExecSelect();
    if (exec != null) exec.putStaticField(fieldName);
  }

  public static void newArray(int type) {
    Executor exec = getExecSelect();
    if (exec != null) exec.newArray(type);
  }

  public static void multiNewArray(int type, int dimensions) {
    Executor exec = getExecSelect();
    if (exec != null) exec.multiNewArray(type, dimensions);
  }
  
  public static void arrayLength(int length) {
    Executor exec = getExecSelect();
    if (exec != null) exec.arrayLength(length);
  }

  public static void checkInstanceOf() {
    Executor exec = getExecSelect();
    if (exec != null) exec.checkInstanceOf();
  }

  public static void invoke(String opcode, String fullmethod) {
    Executor exec = getExecSelect();
    if (exec != null) exec.invoke(opcode, fullmethod);
  }

  public static void invoke(Object conObj, int parmCount, String opcode, String fullmethod) {
    Executor exec = getExecSelect();
    if (exec != null) exec.invoke(conObj, parmCount, opcode, fullmethod);
  }

  public static void invoke(double conObj, int parmCount, String opcode, String fullmethod) {
    Executor exec = getExecSelect();
    if (exec != null) exec.invoke(Double.valueOf(conObj), parmCount, opcode, fullmethod);
  }

  public static void invoke(float conObj, int parmCount, String opcode, String fullmethod) {
    Executor exec = getExecSelect();
    if (exec != null) exec.invoke(Float.valueOf(conObj), parmCount, opcode, fullmethod);
  }

  public static void invoke(long conObj, int parmCount, String opcode, String fullmethod) {
    Executor exec = getExecSelect();
    if (exec != null) exec.invoke(Long.valueOf(conObj), parmCount, opcode, fullmethod);
  }

  public static void invoke(int conObj, int parmCount, String opcode, String fullmethod) {
    Executor exec = getExecSelect();
    if (exec != null) exec.invoke(Integer.valueOf(conObj), parmCount, opcode, fullmethod);
  }

  public static void invokeDynamicEnter() {
    Executor exec = getExecSelect();
    if (exec != null) exec.invokeDynamicEnter();
  }

  public static void invokeDynamicExit(Object conObj) {
    Executor exec = getExecSelect();
    if (exec != null) exec.invokeDynamicExit(conObj);
  }

  public static void catchException(Throwable except, int opline, String fullName) {
    Executor exec = getExecSelect();
    if (exec != null) exec.catchException(except, opline, fullName);
  }

  //================================================================
  // DANHELPER AGENT CALLBACK METHODS
  //================================================================
  
  public static void addBooleanParameter(boolean b, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addBooleanParameter(b, slot);
  }

  public static void addCharParameter(char c, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addCharParameter(c, slot);
  }

  public static void addByteParameter(byte b, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addByteParameter(b, slot);
  }

  public static void addShortParameter(short s, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addShortParameter(s, slot);
  }

  public static void addIntegerParameter(int i, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addIntegerParameter(i, slot);
  }

  public static void addLongParameter(long l, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addLongParameter(l, slot);
  }

  public static void addFloatParameter(float f, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addFloatParameter(f, slot);
  }

  public static void addDoubleParameter(double d, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addDoubleParameter(d, slot);
  }

  public static void addObjectParameter(Object obj, int slot) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addObjectParameter(obj, slot);
  }

  public static void addArrayParameter(Object arr, int slot, int depth, int type) {
    Executor exec = getExecSelect();
    if (exec != null) exec.addArrayParameter(arr, slot, depth, type);
  }

  public static void beginFrame(int maxLocals) {
    Executor exec = getExecSelect();
    if (exec != null) exec.beginFrame(maxLocals);
  }

  public static void removeParams(int numParams) {
    Executor exec = getExecSelect();
    if (exec != null) exec.removeParams(numParams);
  }
  
  public static void bufferedImageGetRGB(Object image, int x, int y) {
    Executor exec = getExecSelect();
    if (exec != null) exec.bufferedImageGetRGB(image, x, y);
  }
  
  public static void bufferedImageSetRGB(Object image, int x, int y, int rgb) {
    Executor exec = getExecSelect();
    if (exec != null) exec.bufferedImageSetRGB(image, x, y, rgb);
  }
  
  public static void bufferedReaderReadLine() {
    Executor exec = getExecSelect();
    if (exec != null) exec.bufferedReaderReadLine();
  }
  
  public static void integerParseInt() {
    Executor exec = getExecSelect();
    if (exec != null) exec.integerParseInt();
  }
  
  public static void stringLength() {
    Executor exec = getExecSelect();
    if (exec != null) exec.stringLength();
  }
  
  public static void stringEquals(String conStr1, Object conObj) {
    Executor exec = getExecSelect();
    if (exec != null) exec.stringEquals(conStr1, conObj);
  }
  
  public static void stringSubstring(String conStr, int conOffset) {
    Executor exec = getExecSelect();
    if (exec != null) exec.stringSubstring(conStr, conOffset);
  }

  public static void objectCloneEnter() {
    Executor exec = getExecSelect();
    if (exec != null) exec.objectCloneEnter();
  }

  public static void createFrame(int numParams, int maxLocals) {
    Executor exec = getExecSelect();
    if (exec != null) exec.createFrame(numParams, maxLocals);
  }

  public static void popFrame(boolean except) {
    Executor exec = getExecSelect();
    if (exec != null) exec.popFrame(except);
  }

  public static void popFrameAndPush(boolean isVoid, boolean except) {
    Executor exec = getExecSelect();
    if (exec != null) exec.popFrameAndPush(isVoid, except);
  }

  public static void pushIntegralType(int val, int type) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushIntegralType(val, type);
  }

  public static void pushLongType(long val, int type) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushLongType(val, type);
  }

  public static void pushFloatType(float val, int type) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushFloatType(val, type);
  }

  public static void pushDoubleType(double val, int type) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushDoubleType(val, type);
  }

  public static void pushReferenceType(Object val, int type) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushReferenceType(val, type);
  }

  public static void pushArrayType(Object val, int type, int depth) {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushArrayType(val, type, depth);
  }

  public static void pushVoidType() {
    Executor exec = getExecSelect();
    if (exec != null) exec.pushVoidType();
  }
  
  public static void objectCloneExit() {
    Executor exec = getExecSelect();
    if (exec != null) exec.objectCloneExit();
  }
  
  public static void exception() {
    Executor exec = getExecSelect();
    if (exec != null) exec.exception();
  }

  public static void initSymbolicSelections() {
    // we should only be running this method once for the project when the first instrumented
    // method is called for the Main thread.
    if (bFirstThread) {
      System.err.println("ERROR: initSymbolicSelections was already called.");
      return;
    }

    bFirstThread = true;

    // search for danfig file in the current working dir
    File workdir = new File (System.getProperty("user.dir"));
    System.out.println("danalyzer working dir = " + workdir);
    File[] danfig = workdir.listFiles((File file) -> file.getName().equals("danfig"));
    boolean autoRun = false;
    if (danfig.length != 0) {
      autoRun = GuiPanel.parseDanfigFile(danfig[0]);
    }
    if (!autoRun) {
      // if not (or danfig file error), run GUI to get the user selections for configuration
      GuiPanel.createSelectorPanel();
    }

    /* ----------debug head--------- *@*/
    int tid = ThreadId.get();
    debugPrintStart(tid, "initSymbolicSelections: starting main()");
    // ----------debug tail---------- */
//    Executor exec = getExecSelect();
  }

}
