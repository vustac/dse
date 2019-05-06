/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danalyzer.gui;

import danalyzer.executor.Value;
import danalyzer.gui.Danfig.TriggerSetup;
import danalyzer.gui.DebugMessage.DebugType;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;


/**
 * @author dmcd2356
 *
 */
public class DebugUtil {

  private static final String NEWLINE = System.getProperty("line.separator");

  // these are bit definitions for enabling portions of debugger used in debugFlags
  public static final int WARN    = 0x00000001; // warnings
  public static final int SOLVE   = 0x00000002; // symbolic execution solver output
  public static final int CALLS   = 0x00000004; // the stack events for calls & returns only
  public static final int STACK   = 0x00000008; // all stack activity
  public static final int LOCAL   = 0x00000010; // local parameter activity
  public static final int COMMAND = 0x00000020; // the commands executed
  public static final int OBJECTS = 0x00000040; // object (REF type) storage info
  public static final int ARRAYS  = 0x00000080; // array storage info
  public static final int AGENT   = 0x00000100; // agent command
  public static final int BRANCH  = 0x00000200; // branching info
  public static final int CONSTR  = 0x00000400; // constraints
  public static final int INFO    = 0x00000800; // additional info
  public static final int ALL     = 0x00000FFF; // all messages enabled

  // these are bit definitions for the trigger types used in TriggerSetup.tEnable
  public static final int TRG_ERROR    = 0x0001; // on error
  public static final int TRG_EXCEPT   = 0x0002; // on exception
  public static final int TRG_INSTRUCT = 0x0004; // on instruction count
  public static final int TRG_REF_W    = 0x0008; // on write to reference parameter
  public static final int TRG_CALL     = 0x0040; // on called method
  public static final int TRG_RETURN   = 0x0080; // on return from call

  // keeps track of the methods called (for determining the parent of a called method)
  private static final Map<Integer, Stack<Boolean>> threadStack = new HashMap<>();
  private static final Map<Integer, Stack<String>> methodStack = new HashMap<>();

  // keeps track of the number of instructions executed
  private static int    instrCounter = 0;
  
  // set the following to some selection to enable debug messages
  private static int    debugFlags = ALL;
  private static int    runningFlags = 0;
  private static int    triggerFlags = 0;
  private static int    currentCount = 0;
  private static int    refCount   = 0;
  private static int    currentRange = 0;
  private static boolean triggerStatus = false;
  private static TriggerSetup trigger;  // the trigger setup conditions


  public static int getInstructionCount() {
    return instrCounter;
  }
  
  public static void resetInstructionCount() {
    instrCounter = 0;
  }
  
  /**
   * this sets the debug flags to be used after a method call has been reached the specifeid times.
   */
  public static void setFlagsTriggerStart() {
    if (TriggerSetup.isTriggered()) {
      currentCount = 0;
      refCount   = 0;
      currentRange = 0;
      triggerFlags = debugFlags;
      runningFlags = 0; // don't set the debug flags yet (wait for trigger)
      triggerStatus = false;
      System.out.println("Debug start after trigger");
    } else {
      // make sure these entries are disabled
      runningFlags = debugFlags; // set debug flags immediately
      triggerStatus = true;
      System.out.println("Debug start immediately");
    }
  }
  
  /**
   * this will set the debug flags to be used immediately (no trigger) from the saved settings.
   */
  public static void setFlagsNoTrigger() {
    TriggerSetup.tEnable = 0;
    runningFlags = debugFlags;
    triggerStatus = true;
  }
  
  private static enum TriggerType { Error, Except, Instruction, Call, Return, RefWrite }
  
  private static boolean checkTrigger(int threadId, TriggerType type) {
    // trigger only if we have reached the specified number of trigger occurrances.
    int limit, count;
    switch (type) {
      case Call:
      case Return:
        ++currentCount;
        count = currentCount;
        limit = TriggerSetup.tCount;
        break;
      case RefWrite:
        ++refCount;
        count = refCount;
        limit = TriggerSetup.tRefCount;
        break;
      default:
        // other cases aren't handled here
        return false;
    }
    if (count == limit || (count > limit && TriggerSetup.tAuto)) {
      runningFlags = triggerFlags;
      currentRange = 0;
      triggerStatus = true;
      System.out.println("-- Debug Triggered --");
      debugPrint(threadId, DebugType.Info, "-------------------- Trigger Event: " + type.toString() + " --------------------");
      return true;
    }

    if (TriggerSetup.tAuto) {
      System.out.println("-- Trigger event " + type + " ("+ count + " of " + limit + ") --");
    }
    return false;
  }

  /**
   * this is executed when a method call is caught to determine if the trigger should occur.
   * 
   * @param threadId  - the current thread running
   * @param fullName  - name of method being called
   */
  private static boolean checkIfMethodMatch(int threadId, String fullName) {
    int offset = fullName.indexOf(".");
    String className = fullName.substring(0, offset);
    String methName = fullName.substring(offset+1);
    return className.equals(TriggerSetup.tClass) &&
          (methName.equals(TriggerSetup.tMethod) || TriggerSetup.tAnyMeth);
  }
  
  /**
   * this is executed on each debug message to determine if trigger should occur.
   */
  private static void checkIfTriggerOnInstruction(int threadId) {
    if ((TriggerSetup.tEnable & TRG_INSTRUCT) != 0) {
      if (TriggerSetup.tInsNum == instrCounter) {
        runningFlags = triggerFlags;
        currentRange = 0;
        triggerStatus = true;
        System.out.println("-- Instruction " + instrCounter + " Trigger --");
        debugPrint(threadId, DebugType.Info, "-------------------- Triggered On Instruction " + instrCounter + " --------------------");
      }
    }
  }

  /**
   * this is executed when an error msg is caught to determine if trigger should occur.
   */
  private static void checkIfTriggerOnError(int threadId) {
    if ((TriggerSetup.tEnable & TRG_ERROR) != 0) {
      runningFlags = triggerFlags;
      currentRange = 0;
      triggerStatus = true;
      System.out.println("-- Error Trigger --");
      debugPrint(threadId, DebugType.Info, "-------------------- Triggered On Error --------------------");
      debugPrint(threadId, DebugType.Info, "(from: " + methodStack.get(threadId).peek() + ")");
    }
  }

  /**
   * this is executed when an exception msg is caught to determine if trigger should occur.
   */
  private static void checkIfTriggerOnException(int threadId) {
    if ((TriggerSetup.tEnable & TRG_EXCEPT) != 0) {
      runningFlags = triggerFlags;
      currentRange = 0;
      triggerStatus = true;
      System.out.println("-- Exception Trigger --");
      debugPrint(threadId, DebugType.Info, "-------------------- Triggered On Exception --------------------");
      debugPrint(threadId, DebugType.Info, "(from: " + methodStack.get(threadId).peek() + ")");
    }
  }
  
  public static int getDebugFlags() {
    return debugFlags & ALL;
  }

  public static String getDebugFlagNames() {
    if ((debugFlags & DebugUtil.ALL) == DebugUtil.ALL) {
      return "ALL";
    }
    
    String names = "";
    // iterate over the selections
    if ((debugFlags & DebugUtil.WARN) != 0) {
      names += " WARN";
    }
    if ((debugFlags & DebugUtil.SOLVE) != 0) {
      names += " SOLVE";
    }
    if ((debugFlags & DebugUtil.CALLS) != 0) {
      names += " CALLS";
    }
    if ((debugFlags & DebugUtil.STACK) != 0) {
      names += " STACK";
    }
    if ((debugFlags & DebugUtil.LOCAL) != 0) {
      names += " LOCAL";
    }
    if ((debugFlags & DebugUtil.COMMAND) != 0) {
      names += " COMMAND";
    }
    if ((debugFlags & DebugUtil.INFO) != 0) {
      names += " INFO";
    }
    if ((debugFlags & DebugUtil.AGENT) != 0) {
      names += " AGENT";
    }
    if ((debugFlags & DebugUtil.OBJECTS) != 0) {
      names += " OBJECTS";
    }
    if ((debugFlags & DebugUtil.ARRAYS) != 0) {
      names += " ARRAYS";
    }
    if ((debugFlags & DebugUtil.BRANCH) != 0) {
      names += " BRANCH";
    }
    if ((debugFlags & DebugUtil.CONSTR) != 0) {
      names += " CONSTR";
    }
    
    return names;
  }
  
  public static boolean setDebugFlag(String name) {
    if (name == null || name.isEmpty()) {
      return true;
    }
    switch (name.trim()) {
      case "WARN":
        debugFlags |= DebugUtil.WARN;
        break;
      case "SOLVE":
        debugFlags |= DebugUtil.SOLVE;
        break;
      case "CALLS":
        debugFlags |= DebugUtil.CALLS;
        break;
      case "STACK":
        debugFlags |= DebugUtil.STACK;
        break;
      case "LOCAL":
        debugFlags |= DebugUtil.LOCAL;
        break;
      case "COMMAND":
        debugFlags |= DebugUtil.COMMAND;
        break;
      case "INFO":
        debugFlags |= DebugUtil.INFO;
        break;
      case "AGENT":
        debugFlags |= DebugUtil.AGENT;
        break;
      case "OBJECTS":
        debugFlags |= DebugUtil.OBJECTS;
        break;
      case "ARRAYS":
        debugFlags |= DebugUtil.ARRAYS;
        break;
      case "BRANCH":
        debugFlags |= DebugUtil.BRANCH;
        break;
      case "CONSTR":
        debugFlags |= DebugUtil.CONSTR;
        break;
      case "ALL":
        debugFlags |= DebugUtil.ALL;
        break;
      default:
        System.err.println("Invalid DebugFlag selection: " + name);
        return false;
    }
    return true;
  }
  
  public static int setDebugFlags(String list) {
    int retcode = 0;
    setDebugFlags(DebugUtil.ALL, false);
    String[] array = list.split(" ");
    for (String next : array) {
      if (! setDebugFlag(next)) {
        retcode = 1;
      }
      System.out.println("DebugFlag enabled: " + next);
    }
    return retcode;
  }
  
  /**
   * sets or clears the specified bits of the debugFlags value.
   * 
   * @param value - the bits to set or clear
   * @param set   - true to set, false to clear
   */
  public static void setDebugFlags(int value, boolean set) {
    if (set) {
      debugFlags = (debugFlags | value) & ALL;
    } else {
      debugFlags = (debugFlags & ~value) & ALL;
    }
  }
  
  private static synchronized void debugPrint(int tid, DebugType type, String message) {
    // only output if we haven't exceeded the max range (or no range is set)
    if (type == DebugType.Error || type == DebugType.Dump || type == DebugType.Start || triggerStatus) {
      DebugMessage.print(tid, type, message);

      // if set for a max limit to print, keep track of the lines output.
      // if debug messages output has exceeded this limit, disable trigger.
      if (TriggerSetup.tAuto && TriggerSetup.tRange > 0) {
        if (++currentRange >= TriggerSetup.tRange) {
          triggerStatus = false;
          System.out.println("Trigger reset");
        }
      }
    }
  }

  public static void debugReset() {
    threadStack.clear();
    methodStack.clear();
    instrCounter = 0;
  } 
  
  private static void instructionUpdate(int threadId) {
    ++instrCounter;
    if (!triggerStatus) {
      checkIfTriggerOnInstruction(threadId);
    }
  }

  public static void debugPrintError(int threadId, String message) {
    // check if this causes trigger
    checkIfTriggerOnError(threadId);
    
    // let's always enable error messages
    debugPrint(threadId, DebugType.Error, message);
  }
  
  public static void debugPrintDump(int threadId, String message) {
    // let's always enable the start message
    debugPrint(threadId, DebugType.Dump, message);
  }
  
  public static void debugPrintStart(int threadId, String message) {
    // let's always enable the start message
    debugPrint(threadId, DebugType.Start, message);
  }
  
  public static void debugPrintException(int threadId, String message, int line, String method) {
    // check if this causes trigger
    if (!triggerStatus) {
      checkIfTriggerOnException(threadId);
    }
    
    // let's always enable this warning message
    debugPrint(threadId, DebugType.Warn, "Exception: (" + line + ", " + method + ") " + message);
  }
  
  public static void debugPrintCommand(int threadId, String message) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, message);
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, int arg) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + ((Integer)arg).toString() + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, long arg) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + ((Long)arg).toString() + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, float arg) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + ((Float)arg).toString() + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, double arg) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + ((Double)arg).toString() + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, String arg) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + DataConvert.cleanStringValue(arg) + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, Object arg) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + DataConvert.getObjectValue(arg) + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, int arg1, int arg2) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + arg1 + ", " + arg2 + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, int arg1, String arg2) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + arg1 + ", " + DataConvert.cleanStringValue(arg2) + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, Object arg1, Object arg2) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + DataConvert.getObjectValue(arg1) + ", " +
          DataConvert.getObjectValue(arg2) + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, int arg1, int arg2, String arg3) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + arg1 + ", " + arg2 + ", " +
          DataConvert.cleanStringValue(arg3) + ")");
    }
  }
  
  public static void debugPrintCommand(int threadId, String cmd, int arg1, int arg2, Object arg3) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + arg1 + ", " + arg2 + ", " +
          DataConvert.getObjectValue(arg3) + ")");
    }
  }
  
  public static void debugPrintCommand_type(int threadId, String cmd, int type) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + DataConvert.showValueDataType(type) + ")");
    }
  }
  
  public static void debugPrintCommand_type(int threadId, String cmd, int type, String val) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      debugPrint(threadId, DebugType.Entry, cmd + " (" + DataConvert.showValueDataType(type) +
          ", " + val + ")");
    }
  }
  
  public static void debugPrintCommand_type(int threadId, String cmd, int type, Value val) {
    // every debugPrintCommand must incr the opcode count
    instructionUpdate(threadId);
    if ((runningFlags & COMMAND) != 0) {
      String size = val.isType(Value.SYM) ? "(symbolic)" : ((Integer)val.getValue()).toString();
      debugPrint(threadId, DebugType.Entry, cmd + " (" + DataConvert.showValueDataType(type) +
          ", size = " + size + ")");
      if (type == 0) {
        debugPrintWarning(threadId, "type not defined!");
      }
    }
  }
  
  public static void debugPrintCallEntry(int threadId, String fullName) {
    // if any debug is enabled, check if the method matches
    if (TriggerSetup.tEnable != 0) {
      // save the method name for the call
      if (!methodStack.containsKey(threadId)) {
        methodStack.put(threadId, new Stack<>());
      }
      methodStack.get(threadId).push(fullName);
    
      // determine if method encountered matches the user-specified entry
      boolean bMatch = checkIfMethodMatch(threadId, fullName);

      if (!threadStack.containsKey(threadId)) {
        threadStack.put(threadId, new Stack<>());
      }
      Stack<Boolean> stack = threadStack.get(threadId);
      stack.push(bMatch);

      // check if trigger occurred
      if (bMatch && (TriggerSetup.tEnable & TRG_CALL) != 0) {
        checkTrigger(threadId, TriggerType.Call);
      }
    }
    
    if ((runningFlags & CALLS) != 0) {
      debugPrint(threadId, DebugType.Call, instrCounter + " " + fullName);
    }
  }
  
  public static void debugPrintCallExit(int threadId) {
    // if any debug is enabled, remove the last called method from stack
    if (TriggerSetup.tEnable != 0) {
      String method = "";
      if (methodStack.containsKey(threadId)) {
        Stack<String> methstack = methodStack.get(threadId);
        if (!methstack.empty()) {
          method = methstack.pop();
        }
      }
    
      // must be in the same thread as the call to re-trigger on return
      if (threadStack.containsKey(threadId)) {
        Stack<Boolean> stack = threadStack.get(threadId);
        boolean bMatch = stack.peek(); // a match indicates we are returning from the matched method
    
        // check if trigger occurred
        if (bMatch && (TriggerSetup.tEnable & TRG_RETURN) != 0) {
          boolean trip = checkTrigger(threadId, TriggerType.Return);
          if (trip) {
            debugPrint(threadId, DebugType.Info, "(returned from: " + method + ")");
          }
        }

        // remove last method call from stack
        if (!stack.empty()) {
          stack.pop();
        }
      }
    }

    if ((runningFlags & CALLS) != 0) {
      debugPrint(threadId, DebugType.Return, "" + instrCounter);
    }
  }
  
  public static void debugPrintInfo(int threadId, String message) {
    if ((runningFlags & INFO) != 0) {
      debugPrint(threadId, DebugType.Info, message);
    }
  }
  
  public static void debugPrintInfo(int threadId, int subtype, String message) {
    if ((runningFlags & INFO) != 0 && (runningFlags & subtype) != 0) {
      debugPrint(threadId, DebugType.Info, message);
    }
  }
  
  public static void debugPrintAgent(int threadId, String message) {
    if ((runningFlags & AGENT) != 0) {
      debugPrint(threadId, DebugType.Agent, message);
    }
  }
  
  public static void debugPrintObjects(int threadId, String message) {
    if ((runningFlags & OBJECTS) != 0) {
      debugPrint(threadId, DebugType.Object, message);
    }
  }
  
  public static void debugPrintArrays(int threadId, String message) {
    if ((runningFlags & ARRAYS) != 0) {
      debugPrint(threadId, DebugType.Array, message);
    }
  }
  
  public static void debugPrintBranch(int threadId, String message) {
    if ((runningFlags & BRANCH) != 0) {
      debugPrint(threadId, DebugType.Branch, message);
    }
  }
  
  public static void debugPrintConstraint(int threadId, String message) {
    if ((runningFlags & CONSTR) != 0) {
      debugPrint(threadId, DebugType.Constr, message);
    }
  }
  
  public static void debugPrintSolve(int threadId, String message) {
    if ((runningFlags & SOLVE) != 0) {
      debugPrint(threadId, DebugType.Solve, message);
    }
  }
  
  public static void debugPrintWarning(int threadId, String message) {
    if ((triggerFlags & WARN) != 0) { // this will allow Warning msgs to trigger on error
      checkIfTriggerOnError(threadId);
      debugPrint(threadId, DebugType.Warn, message);
    }
  }
  
  public static void debugPrintStackNew(int threadId, String methName, int stackSize) {
    if ((runningFlags & STACK) != 0) {
      debugPrint(threadId, DebugType.StackI, "  new stack [" + stackSize + "]: <" + methName + ">");
    }
  }
  
  public static void debugPrintStackRestore(int threadId, String methName, int stackSize) {
    if ((runningFlags & STACK) != 0) {
      debugPrint(threadId, DebugType.StackI, "  restore stack [" + stackSize + "]: <" + methName + ">");
    }
  }
  
  /**
   * types of stack parameter access
   */
  public enum StackSetType { psh, pop, peek }
  
  public static void debugPrintStack(int threadId, StackSetType type, int stackSize, int index, Value val) {
    if ((type == StackSetType.pop || type == StackSetType.peek) && index < 0) {
      debugPrintError(threadId, "  " + type.toString() + " - stack empty  !!! ERROR !!!");
      return;
    }

    if ((runningFlags & STACK) != 0) {
      String prefix = "  " + type.toString() + " [" + stackSize + "][" + index + "]: ";

      if (val == null) {
        debugPrint(threadId, DebugType.Stack, prefix + "null");
      } else if (val.isType(Value.SYM)) {
        debugPrint(threadId, DebugType.StackS, prefix + DataConvert.valueToString(val));
      } else {
        debugPrint(threadId, DebugType.Stack, prefix + DataConvert.valueToString(val));
      }
    }
  }
  
  /**
   * types of local parameter access
   */
  public enum LocalSetType { get, set, add }
  
  public static void debugPrintLocal(int threadId, LocalSetType type, int stackSize, int index, Value val) {
    if ((runningFlags & LOCAL) != 0) {
      String prefix = "  " + type.toString() + " local [" + stackSize + "][" + index + "]: ";

      if (val == null) {
        debugPrint(threadId, DebugType.Local, prefix + "not found");
      } else if (val.isType(Value.SYM)) {
        debugPrint(threadId, DebugType.LocalS, prefix + DataConvert.valueToString(val));
      } else {
        debugPrint(threadId, DebugType.Local, prefix + DataConvert.valueToString(val));
      }
    }
  }
  
  public static void debugPrintObjectMap(int threadId, int index, Map<String, Value> obj) {
    // check if trigger occurred
    if ((TriggerSetup.tEnable & (TRG_REF_W)) != 0) {
      if (index == TriggerSetup.tRefIndex) {
        boolean trip = checkTrigger(threadId, TriggerType.RefWrite);
        if (trip) {
          debugPrint(threadId, DebugType.Info, "(from: " + methodStack.get(threadId).peek() + ")");
        }
      }
    }
    
    if ((runningFlags & OBJECTS) != 0) {
      DebugType dtype = DebugType.Object;
      String prefix = "  OBJECT[" + index + "] contents: ";
      debugPrint(threadId, dtype, prefix + DataConvert.valueToString(new Value(obj, Value.REF)));
    }
  }
  
  public static void debugPrintObjectField(int threadId, int index, String field, Value val) {
    // check if trigger occurred
    if ((TriggerSetup.tEnable & (TRG_REF_W)) != 0) {
      if (index == TriggerSetup.tRefIndex) {
        checkTrigger(threadId, TriggerType.RefWrite);
      }
    }
    
    if ((runningFlags & OBJECTS) != 0) {
      DebugType dtype = DebugType.Object;
      String prefix = "  OBJECT[" + index + "]: ";

      if (val == null) {
        debugPrint(threadId, dtype, prefix + "put field = '" + field + "' value = null");
      } else {
        debugPrint(threadId, dtype, prefix + "put field = '" + field + "' value = " + DataConvert.valueToString(val));
      }
    }
  }
  
  public static void debugPrintArrayMap(int threadId, int index, Value[] combo) {
    if ((runningFlags & ARRAYS) != 0) {
      DebugType dtype = DebugType.Array;
      String prefix = "  ARRAY[" + index + "]: ";

      if (combo == null) {
        debugPrint(threadId, dtype, prefix + "null combo");
      } else {
        debugPrint(threadId, dtype, prefix + DataConvert.valueToString(new Value(combo, Value.ARY)));
      }
    }
  }

  public static void debugPrintArrayList(int threadId, int index, int start, int size, String type) {
    if ((runningFlags & ARRAYS) != 0) {
      DebugType dtype = DebugType.Array;
      debugPrint(threadId, dtype, "  ARRAY[" + index + "]: { REF " + start + " - REF " +
          (start + size - 1) + " } " + " (" + size + " entries of type: " + type + ")");
    }
  }

}

