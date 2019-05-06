package danalyzer.executor;

import danalyzer.gui.DataConvert;
import danalyzer.gui.DebugUtil.LocalSetType;
import danalyzer.gui.DebugUtil.StackSetType;
import static danalyzer.gui.DebugUtil.debugPrintError;
import static danalyzer.gui.DebugUtil.debugPrintLocal;
import static danalyzer.gui.DebugUtil.debugPrintStack;
import static danalyzer.gui.DebugUtil.debugPrintStackNew;
import static danalyzer.gui.DebugUtil.debugPrintWarning;
import java.util.Stack;

/**
 *
 * @author zzk
 */
public class StackFrame {
  private int                 threadId;
  private int                 stackLevel;
  private String              fullName = null;
  private Value[]             localVariableMap;
  private Stack<Value>        operandStack = new Stack<>();
  
  // used in formatting dumpStack
  private static final String NEWLINE = System.getProperty("line.separator");

  /**
   *
   * @param fullName - full name of the method in which the stack is used
   * @param maxLocals - the max number of locals for the method
   * @param tid - thread id
   * @param executionStackSize - size (in bytes) to make stack
   */
  public StackFrame(String fullName, int maxLocals, int tid, int executionStackSize) {
    /* ----------debug head--------- *@*/
    debugPrintStackNew(tid, fullName, executionStackSize);
    // ----------debug tail---------- */
    
    // TODO: this is not really kosher, but because <clinit> is not treated as an actual call
    // by jvmti, we don't get a new stack frame for it. If new params are used by it it can
    // overflow our array. Let's just add 10 as a buffer to try to avoid this.
    maxLocals += 10;

    this.stackLevel = executionStackSize;
    this.threadId = tid;
    this.fullName = fullName;
    this.localVariableMap = new Value[maxLocals];
  }
  
  public void setStackLevel(int level) {
    this.stackLevel = level;
  }
  
  public boolean isEmpty() {
    return this.operandStack.isEmpty();
  }
  
  public void setMethodName(String name) {
    if (this.fullName.equals("dummy")) {
      this.fullName = name;
    } else {
      debugPrintWarning(threadId,"  stack name already set to: " + this.fullName);
    }
  }
  
  public String getMethodName() {
    return this.fullName;
  }
  
  public void pushValue(Value val) {
    /* ----------debug head--------- *@*/
    debugPrintStack(threadId,StackSetType.psh, this.stackLevel, this.operandStack.size(), val);
    if (val == null) {
      debugPrintError(threadId,"  pushing NULL value to stack");
    }
    // ----------debug tail---------- */
    
    this.operandStack.push(val);
  }
  
  public Value popValue() {
    if (this.operandStack.empty()) {
      debugPrintError(threadId,"  pop - stack empty  !!! ERROR !!!");
      return new Value(null, Value.REF);
    }

    Value val = this.operandStack.pop();
    
    /* ----------debug head--------- *@*/
    debugPrintStack(threadId,StackSetType.pop, this.stackLevel, this.operandStack.size(), val);
    // ----------debug tail---------- */
    
    return val;
  }
  
  // who is to blame: zzk
  // away (down) from top: 0 = top, 1 = top-1
  public Value peekValue(int away) {
    int top = this.operandStack.size() - 1;
    int pos = top - away;
    if (pos < 0 || away < 0) {
      /* ----------debug head--------- *@*/
      debugPrintError(threadId,"stack peek index invalid: away = " + away + ", pos = " + pos);
      // ----------debug tail---------- */
      return null;
    }
    else {
      Value val = this.operandStack.elementAt(pos);
      /* ----------debug head--------- *@*/
      debugPrintStack(threadId,StackSetType.peek, this.stackLevel, pos, val);
      // ----------debug tail---------- */
      return val;
    }
  }
  
  public String dumpStack() {
    String contents = "";
    contents += "name: " + this.fullName + NEWLINE;
    if (this.operandStack.size() <= 0) {
      contents += "  (empty)" + NEWLINE;
    } else {
      for (int pos = this.operandStack.size() - 1; pos >= 0; pos--) {
        Value val = this.operandStack.elementAt(pos);
        contents += "   [" + this.stackLevel + "][" + pos + "]: " + DataConvert.valueToString(val) + NEWLINE;
      }
    }
    return contents;
  }
  
  public void loadLocalVariable(int index, int type) {
    if (index >= localVariableMap.length) {
      debugPrintError(threadId,"loadLocalVariable: index = " + index + ", maxLocals = " + localVariableMap.length);
      return;
    }
    
    Value val = getLocalVariable(index);
    if (val == null) {
      storeLocalVariable(index, new Value(null, type));
    }
    pushValue(val);
  }
  
  public void storeLocalVariable(int index, Value val) {
    if (index >= localVariableMap.length) {
      debugPrintError(threadId,"storeLocalVariable: index = " + index + ", maxLocals = " + localVariableMap.length);
      return;
    }
    
    /* ----------debug head--------- *@*/
    if (this.localVariableMap[index] == null) {
      debugPrintLocal(threadId,LocalSetType.add, this.stackLevel, index, val);
    } else {
      debugPrintLocal(threadId,LocalSetType.set, this.stackLevel, index, val);
    }
    //DebugUtil.performType`(val);
    // ----------debug tail---------- */
    
    //this.localVariableMap.put(index, val);
    this.localVariableMap[index] = val;
  }
  
  public boolean isLocalVariableNull(int index) {
    if (index >= localVariableMap.length) {
      debugPrintError(threadId,"isLocalVariableNull: index = " + index + ", maxLocals = " + localVariableMap.length);
      return false;
    }

    return (this.localVariableMap[index] == null);
  }
  
  public Value getLocalVariable(int index) {
    if (index >= localVariableMap.length) {
      debugPrintError(threadId,"getLocalVariable: index = " + index + ", maxLocals = " + localVariableMap.length);
      return null;
    }

    Value val = this.localVariableMap[index];
    
    /* ----------debug head--------- *@*/
    debugPrintLocal(threadId,LocalSetType.get, this.stackLevel, index, val);
    // ----------debug tail---------- */
    
    return val;
  }
}
