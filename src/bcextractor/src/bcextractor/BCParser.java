/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bcextractor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import util.Utils;

/**
 *
 * @author dmcd2356
 */
public class BCParser {
  // This is how the switch opcode branch data gets saved in the comment
  private static final String SWITCH_DELIMITER = "//-- { ";

  private static enum MsgType { ERROR, METHOD, TEXT, PARAM, COMMENT, LINENUM,
                                SYMBRA, BRANCH, SWITCH, GOTO,  INVOKE, RETURN, LOAD, STORE, OTHER }
  
  // states for parseJavap
  private static enum ParseMode { NONE, CLASS, METHOD, DESCRIPTION, OPCODE, OPSWITCH, LINENUM_TBL, LOCALVAR_TBL, EXCEPTION_TBL }
  
  // types of opcode instructions that we handle
  public static enum OpcodeType { SYMBRA, BRANCH, SWITCH, GOTO, INVOKE, RETURN, LOAD, STORE, OTHER }

  private static String          classLoaded = "";
  private static String          methodLoaded = "";
  private static ParseMode       parseMode;
  private static boolean         validMethod;
  private static BytecodeInfo    bytecode = new BytecodeInfo();

  
  private static class BytecodeInfo {
    private final ArrayList<OpcodeInfo>     opcodeList = new ArrayList<>(); // opcode info
    private final ArrayList<LocalParamInfo> paramList  = new ArrayList<>(); // local param info
    private final HashMap<Integer, String>  exceptions = new HashMap<>();   // exception info
    private final HashMap<Integer, Integer> boff2Line  = new HashMap<>();   // byte offset to line number reference
  }
  
  // contains the info for one entry (opcode) of the bytecode
  private static class OpcodeInfo {
    public int     line;        // line number in method (assumes 1 opcode per line)
    public int     offset;      // byte offset of entry within the method
    public String  opcode;      // opcode
    public String  param;       // param entry(s)
    public String  comment;     // comment
    public OpcodeType optype;   // the type of opcode
    public int     ixStart;     // char offset in text of start of line
    public int     ixEnd;       // char offset in text of end of line
    public int     mark;        // highlight bits
    public String  callMeth;    // for INVOKEs, the method being called
    public HashMap<String, Integer> switchinfo; // for SWITCHs
    
    public OpcodeInfo() {
      switchinfo = new HashMap<>();
    }
  }
  
  // defines a local parameter
  private static class LocalParamInfo {
    String  name;
    String  type;
    int     slot;
    int     start;
    int     end;
    
    LocalParamInfo(String name, String type, int slot, int startix, int endix) {
      this.name  = name;
      this.type  = type;
      this.slot  = slot;
      this.start = startix;
      this.end   = endix;
    }
  }
    
  /**
   * determine if line is valid opcode entry & return byte offset index if so.
   * check for line number followed by ": " followed by opcode, indicating this is bytecode.
   * 
   * @param entry - the line to be examined
   * @param lastoffset - the byte offset of the last line read (for verifying validity)
   * @return opcode offset (-1 if not opcode or found next method)
   */
  private int getOpcodeOffset(String entry, int lastoffset) {
    int ix = entry.indexOf(": ");
    if (ix <= 0) {
      return -1;
    }

    try {
      int offset = Integer.parseUnsignedInt(entry.substring(0, ix));
      if (offset > lastoffset) {
        return offset;
      }
    } catch (NumberFormatException ex) { }

    return -1;
  }
  
  private static OpcodeType getOpcodeType(String opcode) {
    // determine the opcode type
    OpcodeType type = OpcodeType.OTHER; // the default type
    switch (opcode.toUpperCase()) {
      case "IF_ICMPEQ":
      case "IF_ICMPNE":
      case "IF_ICMPGT":
      case "IF_ICMPLE":
      case "IF_ICMPLT":
      case "IF_ICMPGE":
      case "IFEQ":
      case "IFNE":
      case "IFGT":
      case "IFLE":
      case "IFLT":
      case "IFGE":
        // NOTE: these branch instructions check for symbolic values
        type = OpcodeType.SYMBRA; // Symbollic branches
        break;
      
      case "IF_ACMPEQ":
      case "IF_ACMPNE":
      case "IFNULL":
      case "IFNONNULL":
        // NOTE: these do NOT check for symbolic types
        type = OpcodeType.BRANCH; // Other branches
        break;
      
      case "TABLESWITCH":
      case "LOOKUPSWITCH":
        // NOTE: these do NOT check for symbolic types
        type = OpcodeType.SWITCH; // switch instructions that may take several different paths
        break;
      
      case "GOTO":
        type = OpcodeType.GOTO; // a simple non-conditional redirect to another byte offset
        break;
      
      case "INVOKESTATIC":
      case "INVOKEVIRTUAL":
      case "INVOKESPECIAL":
      case "INVOKEINTERFACE":
      case "INVOKEDYNAMIC":
        type = OpcodeType.INVOKE; // method calls
        break;
      
      case "ALOAD":
      case "ALOAD_0":
      case "ALOAD_1":
      case "ALOAD_2":
      case "ALOAD_3":
      case "ILOAD":
      case "ILOAD_0":
      case "ILOAD_1":
      case "ILOAD_2":
      case "ILOAD_3":
      case "LLOAD":
      case "LLOAD_0":
      case "LLOAD_1":
      case "LLOAD_2":
      case "LLOAD_3":
      case "FLOAD":
      case "FLOAD_0":
      case "FLOAD_1":
      case "FLOAD_2":
      case "FLOAD_3":
      case "DLOAD":
      case "DLOAD_0":
      case "DLOAD_1":
      case "DLOAD_2":
      case "DLOAD_3":
//      case "AALOAD:"
//      case "CALOAD:"
//      case "SALOAD:"
//      case "IALOAD:"
//      case "LALOAD:"
//      case "FALOAD:"
//      case "DALOAD:"
        type = OpcodeType.LOAD; // load local parameter
        break;
      
      case "ASTORE":
      case "ASTORE_0":
      case "ASTORE_1":
      case "ASTORE_2":
      case "ASTORE_3":
      case "ISTORE":
      case "ISTORE_0":
      case "ISTORE_1":
      case "ISTORE_2":
      case "ISTORE_3":
      case "LSTORE":
      case "LSTORE_0":
      case "LSTORE_1":
      case "LSTORE_2":
      case "LSTORE_3":
      case "FSTORE":
      case "FSTORE_0":
      case "FSTORE_1":
      case "FSTORE_2":
      case "FSTORE_3":
      case "DSTORE":
      case "DSTORE_0":
      case "DSTORE_1":
      case "DSTORE_2":
      case "DSTORE_3":
//      case "AASTORE:"
//      case "CASTORE:"
//      case "SASTORE:"
//      case "IASTORE:"
//      case "LASTORE:"
//      case "FASTORE:"
//      case "DASTORE:"
        type = OpcodeType.STORE; // store local parameter
        break;
      
      case "IRETURN":
      case "LRETURN":
      case "FRETURN":
      case "DRETURN":
      case "ARETURN":
      case "RETURN":
        type = OpcodeType.RETURN; // return to previous stack frame
        break;
      default:
        break;
    }
    return type;
  }
  
  private String extractClass(String methName) {
    String className = "";
    // the class will be seperated from the method by a "."
    int offset = methName.indexOf(".");
    if (offset > 0) {
      className = methName.substring(0, offset);
    }
    return className;
  }
  
  /**
   * outputs next entry to array of OpcodeInfo for a method
   * 
   * @param lineCount - line offset in method (opcode index in method, not the byte offset)
   * @param boffset - byte offset of the instruction
   * @param entry  - the line read from javap
   */
  private void formatBytecode(int lineCount, int boffset, String entry) {
    if (entry == null || entry.isEmpty()) {
      return;
    }
    
    String opcode = entry;
    String param = "";
    String comment = "";
    // check for parameter
    int offset = opcode.indexOf(" ");
    if (offset > 0 ) {
      param = opcode.substring(offset).trim();
      opcode = opcode.substring(0, offset);

      // check for comment
      offset = param.indexOf("//");
      if (offset > 0) {
        comment = param.substring(offset);
        param = param.substring(0, offset).trim();
      }
    }
      
    // check special case of tableswitch and lookupswitch - these are multiline cases
    String switchList = "";
    if (comment.startsWith(SWITCH_DELIMITER)) {
      switchList = comment.substring(SWITCH_DELIMITER.length()).trim(); // remove the delimiter
      offset = switchList.lastIndexOf("}");   // remove trailing end brace
      if (offset > 0) {
        switchList = switchList.substring(0, offset).trim();
      }
    }

    // get the opcode type
    OpcodeType optype = getOpcodeType(opcode);
      
    // if opcode is INVOKE, get the method being called (from the comment section)
    boolean bIsInstr = false;
    String callMethod = "";
    if (optype == OpcodeType.INVOKE) {
      offset = comment.indexOf("Method ");
      if (offset > 0) {
        callMethod = comment.substring(offset + "Method ".length()).trim();
        callMethod = callMethod.replaceAll("\"", ""); // remove any quotes placed around the <init>
        offset = callMethod.indexOf(":");
        if (offset > 0) {
          callMethod = callMethod.substring(0, offset) + callMethod.substring(offset + 1);
        }
          
        // sometimes the current class name will be omitted. if there is none, we must supply it
        String clz = extractClass(callMethod);
        if (clz.isEmpty()) {
          clz = classLoaded;
          callMethod = classLoaded + "." + callMethod;
        }
        
        // check if class of called method is instrumented
        bIsInstr = BCExtractor.isInstrumented(clz);
      }

      if (!bIsInstr) {
        optype = OpcodeType.OTHER; // only count the calls to instrumented code
      }
    }
    
    // add opcode information entry to array
    OpcodeInfo bc = new OpcodeInfo();
    bc.line     = lineCount;
    bc.offset   = boffset;
    bc.opcode   = opcode;
    bc.param    = param;
    bc.comment  = comment;
    bc.optype   = optype;
    bc.callMeth = callMethod;
    bc.mark     = 0;

    bc.ixStart  = 0; // TODO: this will have to be determined when bytecode text is displayed
    // add the line to the text display (need to do here so we have panel length info for ixEnd)
    // printBytecodeOpcode(bc);
    bc.ixEnd    = 0; // TODO: this will have to be determined when bytecode text is displayed

    // for switch statements, let's gather up the conditions and the corresponding branch locations
    bc.switchinfo = new HashMap<>();
    String[] entries = switchList.split(",");
    for (String switchval : entries) {
      offset = switchval.indexOf(":");
      if (offset > 0) {
        String key = switchval.substring(0, offset).trim();
        String val = switchval.substring(offset + 1).trim(); // the branch location
        try {
          Integer branchpt = Integer.parseUnsignedInt(val);
          bc.switchinfo.put(key, branchpt);
        } catch (NumberFormatException ex) {
          Utils.printStatusError("invalid branch location on line " + lineCount + ": " + val);
        }
      }
    }
      
    // now make a reference of the byte offset to the line number and from the line number
    // to the bytecode array index, then add the entry to the array
    bytecode.boff2Line.put(boffset, lineCount);
    bytecode.opcodeList.add(bc);
  }
  
  /**
   * reads the javap output and extracts and displays the opcodes for the selected method.
   * outputs the opcode info to the ArrayList 'bytecode' and saves the method name in 'methodLoaded'.
   * 
   * @param classSelect  - the class name of the method
   * @param methodSelect - the method name to search for
   * @param content      - the javap output
   * @param fname        - the name to store the JSON file in
   */
  public void parseJavap(String classSelect, String methodSelect, String content, String fname) {
    // make sure we use dotted format for class name as that is what javap uses
    classLoaded = classSelect;
    classSelect = classSelect.replaceAll("/", ".");
    Utils.printStatusInfo("parseJavap: " + classSelect + "." + methodSelect);
    
    // javap uses the dot format for the class name, so convert it
    classSelect = classSelect.replace("/", ".");
    int lastoffset = -1;
    int curLine = 0;
    
    // start with a clean slate
    bytecode = new BytecodeInfo();
    parseMode = ParseMode.NONE;
    methodLoaded = "";
    validMethod = false;
    int offset = methodSelect.indexOf("(");
    String signature = methodSelect.substring(offset);
    methodSelect = methodSelect.substring(0, offset);
    String clsCurrent = classSelect; // the default if no class specified
    int linesRead = 0;
    String switchInProcess = "";
    int switchOffset = 0;
    boolean validLine = false;

    // read input line at a time
    String[] lines = content.split(Utils.NEWLINE);
    for (String entry : lines) {
      ++curLine;
      entry = entry.replace("\t", " ").trim();

      // look for start of file - skip anything before this as it may be a remnant of a terminated run.
      if (!validLine) {
        if (entry.startsWith("Compiled from ") || entry.startsWith("No unimplemented opcodes !")) {
          Utils.printStatusInfo("Line: " + curLine + " - found start");
          validLine = true;
        } else if (entry.startsWith("class ") && entry.endsWith("{")) {
          Utils.printStatusInfo("Line: " + curLine + " - found start");
          validLine = true;
        }
        continue;
      }
      
      // count the leading spaces in the line read.
      // method names always have 2 leading spaces.
      // 'descriptor', 'Code', 'LineNumberTable', 'LocalVariableTable' always have 4
      int leadingSpaces = 0;
      for (int ix = 0; ix < entry.length() && entry.charAt(ix) == ' '; ix++) {
        ++leadingSpaces;
      }

      // if we haven't found the method we are looking for yet, ignore any line that doesn't
      // start with 2 spaces.
      if (validLine && !validMethod && leadingSpaces > 2) {
        Utils.printStatusInfo("Line: " + curLine + " - skip");
        continue;
      }
      
      // modify this entry - it indicates the static initializer method
      if (entry.equals("static {};") || entry.equals("static ();")) {
        entry = "<clinit>()";
      }
      
      // skip past these keywords
      if (entry.startsWith("public ") || entry.startsWith("private ") || entry.startsWith("protected ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }
      if (entry.startsWith("static ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }
      if (entry.startsWith("final ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }
      if (entry.startsWith("synchronized ")) {
        entry = entry.substring(entry.indexOf(" ") + 1).trim();
      }

      String keyword = entry.trim();
      offset = keyword.indexOf(" ");
      if (offset > 0) {
        keyword = keyword.substring(0, offset);
      }
      switch (keyword) {
        case "class":
          parseMode = ParseMode.CLASS;
          break; // handle the data on this line below
        case "LineNumberTable:":
          parseMode = ParseMode.LINENUM_TBL;
          Utils.printStatusInfo("Line: " + curLine + " - LineNumberTable");
          continue;
        case "LocalVariableTable:":
          parseMode = ParseMode.LOCALVAR_TBL;
          Utils.printStatusInfo("Line: " + curLine + " - LocalVariableTable");
          continue;
        case "Exception": //  table:
          parseMode = ParseMode.EXCEPTION_TBL;
          Utils.printStatusInfo("Line: " + curLine + " - Exception table");
          continue;
        case "descriptor:":
          parseMode = ParseMode.DESCRIPTION;
          Utils.printStatusInfo("Line: " + curLine + " - descriptor");
          break; // handle the data on this line below
        case "Code:":
          parseMode = ParseMode.OPCODE;
          Utils.printStatusInfo("Line: " + curLine + " - Code");
          linesRead = 0;
          continue;
        default:
          break;
      }

      switch(parseMode) {
        case CLASS:
          // get the first word that follows "class"
          clsCurrent = entry.substring("class".length() + 1);
          offset = clsCurrent.indexOf(" ");
          if (offset > 0) {
            clsCurrent = clsCurrent.substring(0, offset);
          }
          clsCurrent = clsCurrent.trim();
          Utils.printStatusInfo("Line: " + curLine + " - Class = " + clsCurrent);
          parseMode = ParseMode.NONE;
          continue;
        case LINENUM_TBL:
          if (keyword.equals("line")) {
            continue;
          }
          // else, we are no longer processing line numbers, revert to unknown state
          parseMode = ParseMode.NONE;
          break;
        case LOCALVAR_TBL:
          // this line we can ignore
          if (entry.equals("Start  Length  Slot  Name   Signature")) {
            // Start        (int) = bytecode offset to start of scope for parameter
            // Length       (int) = number of bytecode bytes in which it has scope
            // slot         (int) = parameter index
            // Name      (String) = parameter name
            // Signature (String) = data type
            continue;
          }
          // if starts with numeric, we have a valid table entry
          // add them to the local variable table display
          try {
            String[] words = entry.split("\\s+");
            if (words.length >= 5) {
              int start = Integer.parseUnsignedInt(words[0]);
              int len   = Integer.parseUnsignedInt(words[1]);
              int slot  = Integer.parseUnsignedInt(words[2]);
              if (validMethod) {
                bytecode.paramList.add(new LocalParamInfo(words[3], // name
                                                          words[4], // type
                                                          slot,
                                                          start,
                                                          start + len - 1));
              }
              continue;
            }
          } catch (NumberFormatException ex) { }
          parseMode = ParseMode.NONE;
          break;
        case EXCEPTION_TBL:
          // this line we can ignore
          if (entry.equals("from    to  target type")) {
            continue;
          }
          // if starts with numeric, we have a valid table entry
          try {
            int val = Integer.parseUnsignedInt(keyword);
            // from    (int) = bytecode offset to start of scope for exception
            // to      (int) = bytecode offset to end of scope for exception
            // target  (int) = bytecode offset to start of exception handler
            // type (String) = the exception that causes the jump
            String[] words = entry.split("\\s+");
            if (words.length >= 4) {
              Integer target = Integer.parseUnsignedInt(words[2]);
              if (validMethod) {
                String  type = words[3];
                if (bytecode.exceptions.containsKey(target)) {
                  String prev = bytecode.exceptions.get(target);
                  if (!prev.equals(type)) {
                    bytecode.exceptions.replace(target, "(multi)");
                  }
                } else {
                  if (type.equals("Class") && words.length >= 5) {
                    type = words[4];
                    offset = type.lastIndexOf("/");
                    if (offset > 0) {
                      type = type.substring(offset + 1);
                    }
                  }
                  bytecode.exceptions.put(target, type);
                }
              }
            }
            continue;
          } catch (NumberFormatException ex) { }
          parseMode = ParseMode.NONE;
          break;
        case DESCRIPTION:
          if (!methodLoaded.isEmpty()) {
            // compare descriptor value with our method signature
            entry = entry.substring(entry.indexOf(" ") + 1).trim();
            if (entry.equals(signature)) {
              validMethod = true;
              Utils.printStatusInfo("Line: " + curLine + " - signature match: " + entry);
            } else {
              Utils.printStatusInfo("Line: " + curLine + " - no match: " + entry);
            }
          }
          parseMode = ParseMode.NONE;
          continue;
        case OPCODE:
          // check if we are at the correct method
          if (!validMethod) {
            parseMode = ParseMode.NONE;
            break; // check for method
          }

          // else, verify entry beings with "<numeric>: " header
          offset = getOpcodeOffset(entry, lastoffset);
          if (offset >= 0) {
            // check for a SWITCH opcode (special handling for multi-line opcodes)
            if ((entry.contains("tableswitch") || entry.contains("lookupswitch"))) {
              // save the opcode info up to, but excluding the comment field
              switchInProcess = entry.substring(0, entry.indexOf("//")) + SWITCH_DELIMITER;
              switchOffset = offset;
              parseMode = ParseMode.OPSWITCH;
              Utils.printStatusInfo("Line: " + curLine + " - SWITCH statement");
              continue;
            }

            // process the bytecode instruction (save info in array for reference & display text in panel)
            lastoffset = offset;
            entry = entry.substring(entry.indexOf(": ") + 1).trim();
            formatBytecode(linesRead, offset, entry);
            ++linesRead;
            continue;
          }
          break; // else, we probably completed the opcodes, so check for next method definition
        case OPSWITCH:
          // to eliminate having multi-line opcodes "tableswitch" and lookupswitch" we're going to
          // check if we are decoding the specified method and have found the start of one of these.
          // if so, we keep reading input and removing the NEWLINEs until we reach the end.
          // that way, we have all this info on a single line
          if (switchInProcess.isEmpty()) {
            break;
          }
          if (entry.startsWith("}")) {
            // this terminates the SWITCH mode
            if (switchInProcess.endsWith(", ")) {
              switchInProcess = switchInProcess.substring(0, switchInProcess.length() - 2);
            }
            entry = switchInProcess + " }";
            // now we can process the opcode (since offset was already validated, this will always pass)
            lastoffset = switchOffset;
            entry = entry.substring(entry.indexOf(": ") + 1).trim();
            formatBytecode(linesRead, switchOffset, entry);
            ++linesRead;
            parseMode = ParseMode.OPCODE; // switch back to normal opcode handling
          } else {
            // this is the next switch line that holds the selection followed by the branch
            // location to jump to. add the entry in a comma-separated list.
            switchInProcess += entry + ", ";
          }
          continue;
        default:
          break;
      }
      
      // otherwise, let's check if it is a method definition
      // check for start of selected method (method must contain the parameter list)
      // first, eliminate the comment section, which may contain () and method names
      offset = entry.indexOf("//");
      if (offset > 0) {
        entry = entry.substring(0, offset);
      }
      offset = entry.indexOf("(");
      if (offset <= 0 || !entry.contains(")")) {
        continue;
      }

      // now remove the argument list, so the string will end with the method name and
      // seperate the remaining words. This should consist of one of the forllowing formats:
      // <className>                      - <init> method for specified class
      // <retType> <methName>             - class is inferred from last Class: definition
      // <className> <methName>           - static class is not same as last Class: definition
      // <retType> <className> <methName> - class is not same as last Class: definition
      entry = entry.substring(0, offset);

      String clsName = "";
      String methName = "";
      int type = 0;
      if (entry.equals("<clinit>")) {
        // first check if we determined this is a static init metthod
        clsName = clsCurrent;
        methName = entry;
      } else {
        // check for Generic parameters and remove any spaces in them so we treat them as a single word
        // (e.g.   <K, V> com.example.Pair<K, V> createPair   will become:
        //         <K,V> com.example.Pair<K,V> createPair
        int istart = entry.indexOf("<");
        if (istart >= 0 && entry.substring(istart+1).contains(">")) {
          int iend = entry.lastIndexOf(">");
          String ckentry = "";
          while (istart >= 0 && iend >= 0 && iend > istart) {
            ckentry += entry.substring(0, istart);
            String packed = entry.substring(istart, iend + 1);
            entry = entry.substring(iend + 1);
            packed = packed.replaceAll(" ", "");
            ckentry += packed;

            istart = entry.indexOf("<");
            iend = entry.lastIndexOf(">");
          }
          ckentry += entry;
          entry = ckentry;
        }
      
        String[] words = entry.split(" ");
        type = words.length;
        switch (type) {
          case 1: // <init> method for specified class
            clsName = words[0].trim();
            methName = "<init>";
            break;
          case 2: // a method from the given class
            clsName = words[0].trim(); // assume the 1st word is the class
            if (!classSelect.equals(clsName)) {
              clsName = clsCurrent;   // if it isn't, try the last Class definitionn
            }
            methName = words[1].trim();
            break;
          case 3: // a method for an inner class
            clsName = words[1].trim();
            methName = words[2].trim();
            break;
          default:
            Utils.printStatusError("invalid list for method: " + entry);
            break;
        }
      }
      
      // remove Generic entry (e.g. <K,V>) from end of class name if present
      int iStart = clsName.indexOf("<");
      if (iStart > 0) {
        clsName = clsName.substring(0, iStart);
      }
      
      Utils.printStatusInfo("Line: " + curLine + " Type: " + type + " - Method: " + clsName + "." + methName);

      // entry has paranthesis, must be a method name
      parseMode = ParseMode.METHOD;
      if (validMethod) {
        // exit parsing when we hit the method following the correct one
        break;
      }
          
      // method entry found, let's see if it's the one we want
      if (methodSelect.equals(methName) && classSelect.equals(clsName)) {
        methodLoaded = classSelect + "." + methodSelect + signature;
        Utils.printStatusInfo("Line: " + curLine + " - Method match");
      }
    }

    Utils.printStatusInfo("Line: " + curLine + " - opcode lines read: " + linesRead);
    Utils.saveAsJSONFile(bytecode, new File(fname));
  }

}
