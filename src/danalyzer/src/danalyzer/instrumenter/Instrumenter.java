package danalyzer.instrumenter;

//import com.sun.xml.internal.ws.org.objectweb.asm.Type;
import danalyzer.executor.Value;
import danalyzer.gui.DataConvert;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
//import org.objectweb.asm.tree.JumpInsnNode;
//import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 *
 * @author zzk
 */
public class Instrumenter {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  private static final ArrayList<String> unimplementedCommands = new ArrayList<>();
  
  private static boolean maximizeLoopBounds = false;
  
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Missing jar file to instrument");
      System.exit(0);
    }
    
    String path = args[0];
    if (!path.endsWith(".jar")) {
      System.err.println("File to instrument was not a jar file: " + path);
      System.exit(1);
    }

    if (args.length > 1) {
      maximizeLoopBounds = true;
    }
    
    Map<String, ClassNode> clsNodeMap = Reader.readClasses(path);
    instrumentClasses(clsNodeMap);
    Writer.writeClasses(path, clsNodeMap.values());
  }

//  private static boolean isSymbolicMethod(String methName, InsnList instruction) {
//    Iterator<String> it = SYMBOLIC_METHODS.iterator();
//    while (it.hasNext()) {
//      if (methName.endsWith(it.next())) {
//        // This one only handles methods that have 2 integer arguments - the Object parameter
//        // will (probably) always be the 1st parameter. We will have to look at the param list
//        // of the method to determine how to handle different cases and will require another
//        // Executor method call for the different signature. For now, we only care about
//        // this one type of method.
//        
//        // this is a mess, but the object is to duplicate the top 3 entries on the stack.
//        // the method has the following entries on the stack: Obj, X, Y
//        instruction.add(new InsnNode(Opcodes.DUP));      // now: O, X, Y, Y
//        instruction.add(new InsnNode(Opcodes.DUP2_X2));  // now: Y, Y, O, X, Y, Y
//        instruction.add(new InsnNode(Opcodes.POP));      // now: Y, Y, O, X, Y
//        instruction.add(new InsnNode(Opcodes.POP));      // now: Y, Y, O, X
//        instruction.add(new InsnNode(Opcodes.DUP2_X2));  // now: O, X, Y, Y, O, X
//        instruction.add(new InsnNode(Opcodes.DUP2_X1));  // now: O, X, Y, O, X, Y, O, X
//        instruction.add(new InsnNode(Opcodes.POP));      // now: O, X, Y, O, X, Y, O
//        instruction.add(new InsnNode(Opcodes.POP));      // now: O, X, Y, O, X, Y
//        instruction.add(execute("retrieveMethodSymbolic", "(Ljava/lang/Object;II)V"));
//        return true;
//      }
//    }
//    return false;
//  }
  
  private static void unimplementedInstruction(InsnList instrument, int value, String opcode) {
    if (!unimplementedCommands.contains(opcode)) {
      unimplementedCommands.add(opcode);
      System.out.println("Missing opcode: (" + Integer.toHexString(value) + ") " + opcode);
    }
    instrument.add(new LdcInsnNode(opcode));
    instrument.add(execute("unimplementedOpcode", "(Ljava/lang/String;)V"));
  }

  private static void noActionInstruction(InsnList instrument, String opcode)   {
    instrument.add(new LdcInsnNode(opcode));
    instrument.add(execute("noActionOpcode", "(Ljava/lang/String;)V"));
  }

  private static String getReturnType(String desc) {
    int offset = desc.indexOf(")"); // if not found, offset will be -1, so +1 will start at begining
    String type = desc.substring(offset + 1);
    switch(type) {
      case "Z":
      case "C":
      case "B":
      case "S":
      case "I":
      case "J":
      case "F":
      case "D":
        break;
      case "V":
        type = "";
        break;
      default:
        type = "Ljava/lang/Object;";
        break;
    }

    return type;
  }
  
  private static void invokeMethod(InsnList instrument, MethodInsnNode methInstNode, String opcode) {
    String methName = methInstNode.owner + "." + methInstNode.name + methInstNode.desc;

    // check for methods that the JVM optimizes out so danhelper agent doesn't catch them
    String strType = "";
    if (opcode.equals("INVOKESTATIC")) {
      switch (methName) {
        case "java/lang/Math.pow(DD)D":
        case "java/lang/Math.log(DD)D":
        case "java/lang/Math.sqrt(D)D":
          int paramCount = getParameterCount(methInstNode.desc);
          strType = getReturnType(methInstNode.desc);
          // pass the concrete value of the field plus the number of arguments in the method
          if (strType.equals("J") || strType.equals("D")) {
            instrument.add(new InsnNode(Opcodes.DUP2));  
            instrument.add(new LdcInsnNode(paramCount));
          } else {
            instrument.add(new InsnNode(Opcodes.DUP));
            instrument.add(new LdcInsnNode(paramCount));
          }
          strType += "I";
          break;
      }
    }

    instrument.add(new LdcInsnNode(opcode));
    instrument.add(new LdcInsnNode(methName));
    instrument.add(execute("invoke", "(" + strType + "Ljava/lang/String;Ljava/lang/String;)V"));
  }
  
  private static boolean headerInstruction(Set<LoopIdentifier.Node> nodes, AbstractInsnNode insn) {
    if (!maximizeLoopBounds)
      return false;
    
    for (LoopIdentifier.Node headerNode : nodes) {
      List<AbstractInsnNode> instructions = headerNode.getInstructionList();
      for (AbstractInsnNode absInsn : instructions) {
        if (absInsn.equals(insn))
          return true;
      }
    }
    
    return false;
  }

  public static void instrumentClasses(Map<String, ClassNode> clsNodeMap) {
    int classCount = 0;
    int methCount = 0;
    int maxcount = 0;
    
    // create a file for holding the list of classes that are instrumented.
    // this will be placed in the path the instrumented is currently running from.
    BufferedWriter classWriter = null;
    BufferedWriter methodWriter = null;
    String classFileName = "classlist.txt";
    String methodFileName = "methodlist.txt";
    File file = new File(classFileName);
    if (file.isFile()) {
      file.delete();
    }
    try {
      classWriter = new BufferedWriter(new FileWriter(classFileName));
    } catch (IOException ex) { /* ignore error */ }
    file = new File(methodFileName);
    if (file.isFile()) {
      file.delete();
    }
    try {
      methodWriter = new BufferedWriter(new FileWriter(methodFileName));
    } catch (IOException ex) { /* ignore error */ }

    for (Map.Entry<String, ClassNode> clsNodeMapEnt : clsNodeMap.entrySet()) {
      ClassNode clsNode = clsNodeMapEnt.getValue();
      String clsNodeName = "L" + clsNode.name + ";";

      // output class name to file
      ++classCount;
      if (classWriter != null) {
        try {
          classWriter.write(clsNodeName + NEWLINE);
        } catch (IOException ex) { /* ignore error */ }
      } else {
        // if file was not created, output to console
        System.out.println(clsNodeName);
      }
      
      for (MethodNode mtdNode : (List<MethodNode>) clsNode.methods) {
        ++methCount;
        int mtdNodeAcc = mtdNode.access;
        String mtdNodeName = mtdNode.name;
        String mtdNodeDesc = mtdNode.desc;
        String fullName = clsNode.name + "." + mtdNodeName + mtdNodeDesc;
        if (methodWriter != null) {
          try {
            methodWriter.write(fullName + NEWLINE);
          } catch (IOException ex) { /* ignore error */ }
        }
      
        // find out which instructions correspond to the catch beginnings
        Set<AbstractInsnNode> exceptHandlerSet = new HashSet<>();
        for (TryCatchBlockNode exceptBlkNode : (List<TryCatchBlockNode>) mtdNode.tryCatchBlocks) {
          exceptHandlerSet.add(exceptBlkNode.handler);
        }

        // these are for keeping track of the current line number and the line number for
        // the start of the method
        int thisLine = -1;
        int methLine = -1;
        int byteOffset = 0; // byte offset from start of method
        
        LoopIdentifier loopIdentifier = new LoopIdentifier(mtdNode);
        Set<LoopIdentifier.Node> loopHeaders = loopIdentifier.getLoopHeaderSet();
        
        InsnList instList = mtdNode.instructions;
        Iterator<AbstractInsnNode> absInstNodeIter = instList.iterator();
        InsnList instrException = new InsnList(); // this is for holding instrs for Exception
        while (absInstNodeIter.hasNext()) {
          AbstractInsnNode absInstNode = absInstNodeIter.next();
          InsnList fore = new InsnList();
          InsnList hind = new InsnList();
          
          // deal with exception handling (the stack has an exception on top) - save for later
          if (exceptHandlerSet.contains(absInstNode)) {
            instrException.add(new InsnNode(Opcodes.DUP));
            instrException.add(new LdcInsnNode(byteOffset));
            instrException.add(new LdcInsnNode(fullName));
            instrException.add(execute("catchException", "(Ljava/lang/Throwable;ILjava/lang/String;)V"));
          }
          
          int opcode = absInstNode.getOpcode();
          if (opcode <= 0) {
            // if we hit a LineNumberNode, get the line number info
            if (absInstNode.getType() == AbstractInsnNode.LINE) {
              thisLine = ((LineNumberNode) absInstNode).line;
              if (methLine < 0) {
                methLine = thisLine;
                //System.out.println("method: " + fullName + ", line: " + methLine);
              }
            }
            continue;
          }

          // now copy exception info, if any, over to start of instruction list
          for (int ix = 0; ix < instrException.size(); ix++) {
            fore.add(instrException.get(ix));
          }
          instrException.clear();

          switch (opcode) {
            // --- load constant section ---
            case Opcodes.ACONST_NULL:
              fore.add(new InsnNode(opcode));
              fore.add(execute("loadConstant", "(Ljava/lang/Object;)V"));
              break;
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
              fore.add(new InsnNode(opcode));
              fore.add(execute("loadConstant", "(D)V"));  
              break;
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
              fore.add(new InsnNode(opcode));
              fore.add(execute("loadConstant", "(F)V"));  
              break;
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
              fore.add(new InsnNode(opcode));
              fore.add(execute("loadConstant", "(I)V"));  
              break;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
              fore.add(new InsnNode(opcode));
              fore.add(execute("loadConstant", "(J)V"));  
              break;
            case Opcodes.BIPUSH:
              fore.add(new IntInsnNode(opcode, ((IntInsnNode)absInstNode).operand));
              fore.add(execute("pushByteAsInteger", "(I)V"));  
              break;
            case Opcodes.SIPUSH:
              fore.add(new IntInsnNode(opcode, ((IntInsnNode)absInstNode).operand));
              fore.add(execute("pushShortAsInteger", "(I)V"));  
              break;
            case Opcodes.LDC: // includes LDC_W and LDC2_W
              Object cst = ((LdcInsnNode) absInstNode).cst;
              String type = DataConvert.getClassType(cst);
              String prim = DataConvert.getPrimitiveDataType(type);
              if (type == null || prim == null || type.startsWith("[") || type.endsWith(";")) {
                // handle arrays and references as Objects
                type = "Ljava/lang/Object;";
              } else {
                type = prim;
              }
              if (type.equals("C") || type.equals("B") || type.equals("S") || type.equals("Z")) {
                type = "I";
              }

              fore.add(new LdcInsnNode(cst));
              fore.add(execute("loadConstant", "(" + type + ")V"));  
              break;
            
            // --- load/store section ---
            case Opcodes.ALOAD: // includes ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3
              hind.add(new InsnNode(Opcodes.DUP));
              int index = ((VarInsnNode) absInstNode).var;
              hind.add(new IntInsnNode(Opcodes.BIPUSH, index));
              hind.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              hind.add(execute("loadLocalReference", "(Ljava/lang/Object;II)V"));
              break;
            case Opcodes.DLOAD: // includes DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("loadDouble", "(II)V"));  
              break;
            case Opcodes.FLOAD: // includes FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("loadFloat", "(II)V"));  
              break;
            case Opcodes.ILOAD: // includes ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("loadInteger", "(II)V"));  
              break;
            case Opcodes.LLOAD: // includes LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("loadLong", "(II)V"));  
              break;
            case Opcodes.ASTORE: // includes ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode)absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("storeLocalReference", "(Ljava/lang/Object;II)V"));  
              break;
            case Opcodes.DSTORE: // includes DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("storeDouble", "(II)V"));  
              break;
            case Opcodes.FSTORE: // includes FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("storeFloat", "(II)V"));  
              break;
            case Opcodes.ISTORE: // includes ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("storeInteger", "(II)V"));  
              break;
            case Opcodes.LSTORE: // includes LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((VarInsnNode) absInstNode).var));
              fore.add(new LdcInsnNode(byteOffset)); // add the byte offset for the opcode
              fore.add(execute("storeLong", "(II)V"));  
              break;
            
            // --- load/store from/to array section ---
            case Opcodes.AALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("loadReferenceFromArray", "(Ljava/lang/Object;II)V"));  
              break;
            case Opcodes.CALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new InsnNode(Opcodes.POP));      // remove index entry, so we just have arrayref
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("readCharArray", "([CI)V"));
              break;            
            case Opcodes.BALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new InsnNode(Opcodes.POP));      // remove index entry, so we just have arrayref
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("readByteArray", "([BI)V"));
              break;
            case Opcodes.SALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new InsnNode(Opcodes.POP));      // remove index entry, so we just have arrayref
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("readShortArray", "([SI)V"));
              break;            
            case Opcodes.IALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new InsnNode(Opcodes.POP));      // remove index entry, so we just have arrayref
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("readIntegerArray", "([II)V"));
              break;            
            case Opcodes.LALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new InsnNode(Opcodes.POP));      // remove index entry, so we just have arrayref
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("readLongArray", "([JI)V"));
              break;            
            case Opcodes.DALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new InsnNode(Opcodes.POP));      // remove index entry, so we just have arrayref
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("readDoubleArray", "([DI)V"));
              break;
            case Opcodes.FALOAD:
              // prior to the command, stack contains: arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate both params
              fore.add(new InsnNode(Opcodes.POP));      // remove index entry, so we just have arrayref
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("readFloatArray", "([FI)V"));
              break;
            case Opcodes.AASTORE:
              // prior to the command, stack contains: arrayref, index, value
//              fore.add(new InsnNode(Opcodes.DUP2));     // duplicate index and value entries
//              fore.add(new InsnNode(Opcodes.POP));      // remove value entry so we just have index
              fore.add(new InsnNode(Opcodes.DUP_X2));   // -> value, arrayref, index, value
              fore.add(new InsnNode(Opcodes.POP));      // -> value, arrayref, index
              fore.add(new InsnNode(Opcodes.DUP2_X1));  // -> arrayref, index, value, arrayref, index
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("arrayStore", "([Ljava/lang/Object;II)V"));  // so this simply tacks on: arrayref, index
              break;
            case Opcodes.CASTORE:
              fore.add(new LdcInsnNode(Value.CHR));     // specify data type
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("writePrimitiveArray", "(II)V"));
              break;
            case Opcodes.BASTORE:
              fore.add(new LdcInsnNode(Value.INT8));    // specify data type
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("writePrimitiveArray", "(II)V"));
              break;
            case Opcodes.SASTORE:
              fore.add(new LdcInsnNode(Value.INT16));   // specify data type
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("writePrimitiveArray", "(II)V"));
              break;
            case Opcodes.IASTORE:
              fore.add(new LdcInsnNode(Value.INT32));   // specify data type
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("writePrimitiveArray", "(II)V"));
              break;
            case Opcodes.LASTORE:
              fore.add(new LdcInsnNode(Value.INT64));   // specify data type
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("writePrimitiveArray", "(II)V"));
              break;
            case Opcodes.DASTORE:
              fore.add(new LdcInsnNode(Value.DBL));     // specify data type
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("writePrimitiveArray", "(II)V"));
              break;
            case Opcodes.FASTORE:
              fore.add(new LdcInsnNode(Value.FLT));     // specify data type
              fore.add(new LdcInsnNode(byteOffset));    // add the byte offset for the opcode
              fore.add(execute("writePrimitiveArray", "(II)V"));
              break;
              
            // --- stack section ---
            case Opcodes.POP:
              fore.add(execute("executePop", "()V"));
              break;
            case Opcodes.POP2:
              fore.add(execute("executePop2", "()V"));
              break;
            case Opcodes.DUP:
              fore.add(execute("executeDup", "()V"));  
              break;
            case Opcodes.DUP_X1:
              fore.add(execute("executeDup_x1", "()V"));  
              break;
            case Opcodes.DUP_X2:
              fore.add(execute("executeDup_x2", "()V"));  
              break;
            case Opcodes.DUP2:
              fore.add(execute("executeDup2", "()V"));  
              break;
            case Opcodes.DUP2_X1:
              fore.add(execute("executeDup2_x1", "()V"));  
              break;
            case Opcodes.DUP2_X2:
              fore.add(execute("executeDup2_x2", "()V"));  
              break;
            case Opcodes.SWAP:
              fore.add(execute("executeSwap", "()V"));
              break;
            
            // --- arithmetic & logic section ---
            case Opcodes.DADD:
              fore.add(execute("addDouble", "()V"));  
              break;
            case Opcodes.DSUB:
              fore.add(execute("subDouble", "()V"));  
              break;
            case Opcodes.DMUL:
              fore.add(execute("mulDouble", "()V"));  
              break;
            case Opcodes.DDIV:
              fore.add(execute("divDouble", "()V"));  
              break;
            case Opcodes.DREM:
              fore.add(execute("remDouble", "()V"));  
              break;
            case Opcodes.DNEG:
              fore.add(execute("negDouble", "()V"));  
              break;
            case Opcodes.FADD:
              fore.add(execute("addFloat", "()V"));  
              break;
            case Opcodes.FSUB:
              fore.add(execute("subFloat", "()V"));  
              break;
            case Opcodes.FMUL:
              fore.add(execute("mulFloat", "()V"));  
              break;
            case Opcodes.FDIV:
              fore.add(execute("divFloat", "()V"));  
              break;
            case Opcodes.FREM:
              fore.add(execute("remFloat", "()V"));  
              break;
            case Opcodes.FNEG:
              fore.add(execute("negFloat", "()V"));  
              break;
            case Opcodes.IINC:
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((IincInsnNode)absInstNode).var));
              fore.add(new IntInsnNode(Opcodes.BIPUSH, ((IincInsnNode)absInstNode).incr));
              fore.add(execute("incInteger", "(II)V"));  
              break;
            case Opcodes.IADD:
              fore.add(execute("addInteger", "()V"));  
              break;
            case Opcodes.ISUB:
              fore.add(execute("subInteger", "()V"));  
              break;
            case Opcodes.IMUL:
              fore.add(execute("mulInteger", "()V"));  
              break;
            case Opcodes.IDIV:
              fore.add(execute("divInteger", "()V"));  
              break;
            case Opcodes.IREM:
              fore.add(execute("remInteger", "()V"));  
              break;
            case Opcodes.INEG:
              fore.add(execute("negInteger", "()V"));  
              break;
            case Opcodes.LADD:
              fore.add(execute("addLong", "()V"));  
              break;
            case Opcodes.LSUB:
              fore.add(execute("subLong", "()V"));  
              break;
            case Opcodes.LMUL:
              fore.add(execute("mulLong", "()V"));  
              break;
            case Opcodes.LDIV:
              fore.add(execute("divLong", "()V"));  
              break;
            case Opcodes.LREM:
              fore.add(execute("remLong", "()V"));  
              break;
            case Opcodes.LNEG:
              fore.add(execute("negLong", "()V"));  
              break;
            case Opcodes.IAND:
              fore.add(execute("executeIntegerAnd", "()V"));  
              break;
            case Opcodes.IOR:
              fore.add(execute("executeIntegerOr", "()V"));  
              break;
            case Opcodes.IXOR:
              fore.add(execute("executeIntegerXor", "()V"));  
              break;
            case Opcodes.ISHL:
              fore.add(execute("executeIntegerShiftLeft", "()V"));  
              break;
            case Opcodes.ISHR:
              fore.add(execute("executeIntegerArithShiftRight", "()V"));  
              break;
            case Opcodes.IUSHR:
              fore.add(execute("executeIntegerShiftRight", "()V"));  
              break;
            case Opcodes.LAND:
              fore.add(execute("executeLongAnd", "()V"));  
              break;
            case Opcodes.LOR:
              fore.add(execute("executeLongOr", "()V"));  
              break;
            case Opcodes.LXOR:
              fore.add(execute("executeLongXor", "()V"));  
              break;
            case Opcodes.LSHL:
              fore.add(execute("executeLongShiftLeft", "()V"));  
              break;
            case Opcodes.LSHR:
              fore.add(execute("executeLongArithShiftRight", "()V"));  
              break;
            case Opcodes.LUSHR:
              fore.add(execute("executeLongShiftRight", "()V"));  
              break;
            case Opcodes.DCMPG:
              fore.add(execute("compareDoubleGreater", "()V"));  
              break;
            case Opcodes.DCMPL:
              fore.add(execute("compareDoubleLess", "()V"));  
              break;
            case Opcodes.FCMPG:
              fore.add(execute("compareFloatGreater", "()V"));  
              break;
            case Opcodes.FCMPL:
              fore.add(execute("compareFloatLess", "()V"));  
              break;
            case Opcodes.LCMP:
              fore.add(execute("executeLongCompare", "()V"));  
              break;
            
            // --- type conversion section ---
            case Opcodes.I2D:
              fore.add(execute("i_convertToDouble", "()V"));  
              break;
            case Opcodes.L2D:
              fore.add(execute("l_convertToDouble", "()V"));  
              break;
            case Opcodes.F2D:
              fore.add(execute("f_convertToDouble", "()V"));  
              break;
            case Opcodes.I2F:
              fore.add(execute("i_convertToFloat", "()V"));  
              break;
            case Opcodes.L2F:
              fore.add(execute("l_convertToFloat", "()V"));  
              break;
            case Opcodes.D2F:
              fore.add(execute("d_convertToFloat", "()V"));  
              break;
            case Opcodes.I2L:
              fore.add(execute("i_convertToLong", "()V"));  
              break;
            case Opcodes.F2L:
              fore.add(execute("f_convertToLong", "()V"));  
              break;
            case Opcodes.D2L:
              fore.add(execute("d_convertToLong", "()V"));  
              break;
            case Opcodes.L2I:
              fore.add(execute("l_convertToInteger", "()V"));  
              break;
            case Opcodes.F2I:
              fore.add(execute("f_convertToInteger", "()V"));  
              break;
            case Opcodes.D2I:
              fore.add(execute("d_convertToInteger", "()V"));  
              break;
            case Opcodes.I2S:
              fore.add(execute("convertToShort", "()V"));  
              break;
            case Opcodes.I2C:
              fore.add(execute("convertToChar", "()V"));  
              break;
            case Opcodes.I2B:
              fore.add(execute("convertToByte", "()V"));  
              break;
            
            // --- control transfer section ---
            case Opcodes.IFEQ:
              fore.add(new InsnNode(Opcodes.ICONST_0));
              fore.add(execute("loadConstant", "(I)V"));
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(new InsnNode(Opcodes.ICONST_0));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerEQ", "(IIILjava/lang/String;)V"));  
              break;
            case Opcodes.IFNE:
              fore.add(new InsnNode(Opcodes.ICONST_0));
              fore.add(execute("loadConstant", "(I)V"));
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(new InsnNode(Opcodes.ICONST_0));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerNE", "(IIILjava/lang/String;)V"));  
              break;
            case Opcodes.IFLT:
              fore.add(new InsnNode(Opcodes.ICONST_0));
              fore.add(execute("loadConstant", "(I)V"));
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(new InsnNode(Opcodes.ICONST_0));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerLT", "(IIILjava/lang/String;)V"));
              break;
            case Opcodes.IFGE:
              fore.add(new InsnNode(Opcodes.ICONST_0));
              fore.add(execute("loadConstant", "(I)V"));
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(new InsnNode(Opcodes.ICONST_0));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerGE", "(IIILjava/lang/String;)V"));
              break;
            case Opcodes.IFGT:
              fore.add(new InsnNode(Opcodes.ICONST_0));
              fore.add(execute("loadConstant", "(I)V"));
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(new InsnNode(Opcodes.ICONST_0));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerGT", "(IIILjava/lang/String;)V"));  
              break;            
            case Opcodes.IFLE:
              fore.add(new InsnNode(Opcodes.ICONST_0));
              fore.add(execute("loadConstant", "(I)V"));
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(new InsnNode(Opcodes.ICONST_0));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerLE", "(IIILjava/lang/String;)V"));  
              break;            
            case Opcodes.IF_ICMPEQ:
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerICMPEQ", "(IIILjava/lang/String;)V"));  
              break;            
            case Opcodes.IF_ICMPNE:
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerICMPNE", "(IIILjava/lang/String;)V"));  
              break;
            case Opcodes.IF_ICMPLT:
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerICMPLT", "(IIILjava/lang/String;)V"));  
              break;
            case Opcodes.IF_ICMPGE:
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              if (headerInstruction(loopHeaders, absInstNode)) {
                fore.add(execute("maxCompareIntegerICMPGE", "(IIILjava/lang/String;)V"));
                ++maxcount;
              }
              else {
                fore.add(execute("compareIntegerICMPGE", "(IIILjava/lang/String;)V"));  
              }
              break;
            case Opcodes.IF_ICMPGT:
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerICMPGT", "(IIILjava/lang/String;)V"));  
              break;
            case Opcodes.IF_ICMPLE:
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("compareIntegerICMPLE", "(IIILjava/lang/String;)V"));  
              break;
            case Opcodes.IF_ACMPEQ:              
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("executeIfReferenceEqual",
                  "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;)V"));   
              break;
            case Opcodes.IF_ACMPNE:
              fore.add(new InsnNode(Opcodes.DUP2));
              // this adds in the method and byte offset associated with the if statement
              fore.add(new LdcInsnNode(byteOffset));
              fore.add(new LdcInsnNode(fullName));
              fore.add(execute("executeIfReferenceNotEqual",
                  "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;)V"));   
              break;
            case Opcodes.IFNULL:
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(execute("executeIfReferenceNull", "(Ljava/lang/Object;)V"));  
              break;
            case Opcodes.IFNONNULL:
              fore.add(new InsnNode(Opcodes.DUP));
              fore.add(execute("executeIfReferenceNonNull", "(Ljava/lang/Object;)V"));  
              break;
            case Opcodes.LOOKUPSWITCH:
              noActionInstruction(fore, "LOOKUPSWITCH");
              break;
            case Opcodes.TABLESWITCH:
              noActionInstruction(fore, "TABLESWITCH");
              break;
            case Opcodes.GOTO: // includes GOTO_W
              noActionInstruction(fore, "GOTO");
              break;
            case Opcodes.JSR: // includes JSR_W
              unimplementedInstruction(fore, opcode, "JSR");
              break;
            case Opcodes.RET:
              unimplementedInstruction(fore, opcode, "RET");
              break;
            case Opcodes.ARETURN:
              fore.add(execute("returnReference", "()V"));
              break;
            case Opcodes.DRETURN:
              fore.add(execute("returnDouble", "()V"));
              break;
            case Opcodes.FRETURN:
              fore.add(execute("returnFloat", "()V"));
              break;
            case Opcodes.IRETURN:
              fore.add(execute("returnInteger", "()V"));
              break;
            case Opcodes.LRETURN:
              fore.add(execute("returnLong", "()V"));
              break;
            case Opcodes.RETURN:
              if (mtdNodeName.equals("<clinit>")) {
                fore.add(execute("returnStaticVoid", "()V"));
              } else {
                fore.add(execute("returnVoid", "()V"));
              }
              break;
            
            // --- field access section ---
            case Opcodes.GETFIELD:
              FieldInsnNode getFieldInstNode = (FieldInsnNode) absInstNode;
              String strType = getReturnType(getFieldInstNode.desc);
              if (strType.equals("J") || strType.equals("D")) {
                hind.add(new InsnNode(Opcodes.DUP2));  // pass the concrete value of the field
              } else {
                hind.add(new InsnNode(Opcodes.DUP));  // pass the concrete value of the field
              }
              hind.add(new LdcInsnNode(getFieldInstNode.name));
              hind.add(new LdcInsnNode(getFieldInstNode.desc));
              hind.add(execute("getField", "(" + strType + "Ljava/lang/String;Ljava/lang/String;)V"));
              break;
            case Opcodes.PUTFIELD:
              FieldInsnNode putFieldInstNode = (FieldInsnNode) absInstNode;
              hind.add(new LdcInsnNode(putFieldInstNode.name));
              hind.add(execute("putField", "(Ljava/lang/String;)V"));
              break;
            case Opcodes.GETSTATIC:
              FieldInsnNode getStaticFieldInstNode = (FieldInsnNode) absInstNode;
              String getFieldName = getStaticFieldInstNode.owner + getStaticFieldInstNode.name;
              hind.add(new LdcInsnNode(getFieldName));
              hind.add(new LdcInsnNode(getStaticFieldInstNode.desc));
              hind.add(execute("getStaticField", "(Ljava/lang/String;Ljava/lang/String;)V"));
              break;
            case Opcodes.PUTSTATIC:
              FieldInsnNode putStaticFieldInstNode = (FieldInsnNode) absInstNode;
              String putFieldName = putStaticFieldInstNode.owner + putStaticFieldInstNode.name;
              hind.add(new LdcInsnNode(putFieldName));
              hind.add(execute("putStaticField", "(Ljava/lang/String;)V"));
              break;
            
            // --- invoke method section ---
            case Opcodes.INVOKEDYNAMIC:
              // this will forcast the upcoming INVOKEDYNAMIC call to modify behavior
              fore.add(execute("invokeDynamicEnter", "()V"));
              // this will retrieve the result returned from the INVOKEDYNAMIC call
              hind.add(new InsnNode(Opcodes.DUP));
              hind.add(execute("invokeDynamicExit", "(Ljava/lang/Object;)V"));
              break;
            case Opcodes.INVOKESPECIAL:
              invokeMethod(hind, (MethodInsnNode) absInstNode, "INVOKESPECIAL");
              break;                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                
            case Opcodes.INVOKEINTERFACE:            
              invokeMethod(hind, (MethodInsnNode) absInstNode, "INVOKEINTERFACE");
              break;                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                
            case Opcodes.INVOKESTATIC:
              invokeMethod(hind, (MethodInsnNode) absInstNode, "INVOKESTATIC");
              break;                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                
            case Opcodes.INVOKEVIRTUAL:
              invokeMethod(hind, (MethodInsnNode) absInstNode, "INVOKEVIRTUAL");
              break;                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                
            
            // --- object & array section ---
            case Opcodes.NEW:
              String objType = ((TypeInsnNode) absInstNode).desc.trim();
              if (objType.equals("java/lang/String")) {
                fore.add(execute("newStringObject", "()V"));
              } else {
                fore.add(new LdcInsnNode(objType));
                fore.add(execute("newObject", "(Ljava/lang/String;)V"));
              }
              break;
            case Opcodes.INSTANCEOF: 
              fore.add(execute("checkInstanceOf", "()V"));
              // treat instanceof as an uninstrumented function
              hind.add(new InsnNode(Opcodes.DUP));
              hind.add(execute("loadConstant", "(I)V"));
              break;
            case Opcodes.CHECKCAST:
              // checkcast does not need to be implemented: if good, nothing changes; or exception
              noActionInstruction(fore, "CHECKCAST");
              break;
            case Opcodes.NEWARRAY:
              int p = ((IntInsnNode) absInstNode).operand;
              int pType = p == Opcodes.T_BYTE ? Value.INT8 : p == Opcodes.T_SHORT ? Value.INT16 : 
                  p == Opcodes.T_INT ? Value.INT32 : p == Opcodes.T_LONG ? Value.INT64 : 
                  p == Opcodes.T_BOOLEAN ? Value.BLN : p == Opcodes.T_CHAR ? Value.CHR : 
                  p == Opcodes.T_FLOAT ? Value.FLT : p == Opcodes.T_DOUBLE ? Value.DBL : 0;
//              if (pType == 0) {
//                System.err.println("Invalid operand type to NEWARRAY!");
//                System.exit(0);
//              }
              // for a specific NEWARRAY, pType is a fixed constant and is put in the constant pool
              fore.add(new LdcInsnNode(pType));
              fore.add(execute("newArray", "(I)V"));
              break;
            case Opcodes.ANEWARRAY:
              String r = ((TypeInsnNode) absInstNode).desc;
              int rType = r.equals("Ljava/lang/String;") ? Value.STR : Value.REF;
              // for a specific NEWARRAY, pType is a fixed constant and is put in the constant pool
              fore.add(new LdcInsnNode(rType));
              fore.add(execute("newArray", "(I)V"));
              break;
            case Opcodes.MULTIANEWARRAY:
              String arrClass = ((MultiANewArrayInsnNode) absInstNode).desc.trim();
              // get data type of array elements (ignore array bits)
              int arrType = DataConvert.getValueDataType(arrClass) & ~(Value.MARY | Value.ARY);
              int dims = ((MultiANewArrayInsnNode) absInstNode).dims;
              fore.add(new LdcInsnNode(arrType));
              fore.add(new LdcInsnNode(dims));
              fore.add(execute("multiNewArray", "(II)V"));
              break;
            case Opcodes.ARRAYLENGTH:
              hind.add(new InsnNode(Opcodes.DUP));
              hind.add(execute("arrayLength", "(I)V"));
              break;
            
            // --- miscellaneous section ---
            case Opcodes.ATHROW:
              // athrow does not need to be implemented: exception is handled when it reaches there
              noActionInstruction(fore, "ATHROW");
              break;
            case Opcodes.MONITORENTER:
              noActionInstruction(fore, "MONITORENTER");
              break;
            case Opcodes.MONITOREXIT:
              noActionInstruction(fore, "MONITOREXIT");
              break;
            case Opcodes.NOP:
              noActionInstruction(fore, "NOP");
              break;
            default:
              System.out.println("Unknown opcode: 0x" + Integer.toHexString(opcode));
              continue;
          }
          
          // tally up the byte count
          byteOffset += getByteCount(opcode, absInstNode);
          
          instList.insertBefore(absInstNode, fore);
          instList.insert(absInstNode, hind);
          
          // adjust iterator for the loop if we added anything AFTER the current instruction
          int numToPop = hind.size();
          while (numToPop-- > 0 && absInstNodeIter.hasNext()) {
            absInstNodeIter.next();
          }
        }
        
        // instrument each method to set up currentStackFrame.
        byteOffset = 0;
        InsnList instrument = new InsnList();
        int justParamCnt = getParameterCount(mtdNodeDesc);
        int paramCnt = justParamCnt + ((mtdNodeAcc & Opcodes.ACC_STATIC) != 0 ? 0 : 1);
        instrument.add(new LdcInsnNode(fullName));
        instrument.add(paramCnt <= 5 ? 
            new InsnNode(Opcodes.ICONST_0 + paramCnt) : new LdcInsnNode(paramCnt));
        instrument.add(execute("establishStackFrame", "(Ljava/lang/String;I)V"));

        // instrument main() method to get user's inputs
        if ((mtdNodeAcc & Opcodes.ACC_PUBLIC) != 0 && (mtdNodeAcc & Opcodes.ACC_STATIC) != 0 && 
            mtdNodeName.equals("main") && mtdNodeDesc.equals("([Ljava/lang/String;)V") && 
            instList.size() >= 1) {
          instrument.add(new VarInsnNode(Opcodes.ALOAD, 0));
          instrument.add(execute("initializeExecutor", "([Ljava/lang/String;)V"));
        }
        AbstractInsnNode firstInstNode = instList.getFirst();
        if (firstInstNode != null) {
          instList.insertBefore(firstInstNode, instrument);
        }
      }
    }
    
    System.out.println("Total classes: " + classCount);
    System.out.println("Total methods: " + methCount);
    System.out.println("Found max instruction " + maxcount + " times");
    try {
      if (classWriter != null) {
        classWriter.close();
      }
      if (methodWriter != null) {
        methodWriter.close();
      }
    } catch (IOException ex) { /* ignore */ }
    if (unimplementedCommands.isEmpty()) {
      System.out.println("No unimplemented opcodes !");
    }
  }

  private static MethodInsnNode execute(String mthName, String mthDesc) {
    MethodInsnNode mtdInstNode = new MethodInsnNode(Opcodes.INVOKESTATIC, 
        "danalyzer/executor/ExecWrapper", mthName, mthDesc, false);
    return mtdInstNode;
  }
  
  private static int getParameterCount(String mthDesc) {
    int paramCnt = 0;
    boolean clsType = false;
    for (int i = 0; i < mthDesc.length(); ++i) {
      char c = mthDesc.charAt(i);
      if (c == '(') {
        continue;
      } else if (c == ')') {
        break;
      }
      
      if (c == '[') {  
        continue;
      } else if (c == 'L' && !clsType) {
        clsType = true;
      } else if (c == ';' && clsType) {
        clsType = false;
      }
      if (!clsType) {
        ++paramCnt;
      }
    }
    return paramCnt;
  }
  
  /**
   * This calculates the byte offset for the specified opcode. Unfortunately, ASM doesn't give
   * enough info to make this calculation in many cases, causing errors (especially if the is a
   * 'switch' statement in the code.
   * 
   * So for now, this will just indicate the instruction count and not the actual byte offset.
   * 
   * @param opcode
   * @param absInstNode
   * @return 
   */
  private static int getByteCount(int opcode, AbstractInsnNode absInstNode) {
    int byteOffset = 1; // by default, indicate size of 1 (no parameters)

//    switch (opcode) {
//      // these have a single byte parameter
//      case Opcodes.BIPUSH:
//        // NOTE: ASM does not indicate whether an argument was implied in the instruction (e.g. ILOAD_0)
//        // If so, the offset for these is actually 1 istead of 2.
//      case Opcodes.ILOAD:  // NOTE: must exclude: ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3
//      case Opcodes.LLOAD:  // NOTE: must exclude: LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3
//      case Opcodes.FLOAD:  // NOTE: must exclude: FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3
//      case Opcodes.DLOAD:  // NOTE: must exclude: DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3
//      case Opcodes.ALOAD:  // NOTE: must exclude: ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3
//      case Opcodes.ISTORE: // NOTE: must exclude: ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3
//      case Opcodes.LSTORE: // NOTE: must exclude: LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3
//      case Opcodes.FSTORE: // NOTE: must exclude: FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3
//      case Opcodes.DSTORE: // NOTE: must exclude: DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3
//      case Opcodes.ASTORE: // NOTE: must exclude: ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3
//      case Opcodes.RET:
//      case Opcodes.NEWARRAY:
//        byteOffset += 2;
//        break;
//
//      // these have a single 16-bit parameter
//      case Opcodes.SIPUSH:
//      case Opcodes.IINC:
//      case Opcodes.IFEQ:
//      case Opcodes.IFNE:
//      case Opcodes.IFLT:
//      case Opcodes.IFGE:
//      case Opcodes.IFGT:
//      case Opcodes.IFLE:
//      case Opcodes.IF_ICMPEQ:
//      case Opcodes.IF_ICMPNE:
//      case Opcodes.IF_ICMPLT:
//      case Opcodes.IF_ICMPGE:
//      case Opcodes.IF_ICMPGT:
//      case Opcodes.IF_ICMPLE:
//      case Opcodes.IF_ACMPEQ:              
//      case Opcodes.IF_ACMPNE:
//        // NOTE: ASM does not indicate whether these are WIDE commands or not (GOTO_W, JSR_W).
//        // If so, the offset should be 5 instead of 3.
//      case Opcodes.GOTO:    // if GOTO_W, offset = 5
//      case Opcodes.JSR:     // if JSR_W, offset = 5
//      case Opcodes.GETSTATIC:
//      case Opcodes.PUTSTATIC:
//      case Opcodes.GETFIELD:
//      case Opcodes.PUTFIELD:
//      case Opcodes.INVOKEVIRTUAL:
//      case Opcodes.INVOKESPECIAL:
//      case Opcodes.INVOKESTATIC:
//      case Opcodes.NEW:
//      case Opcodes.ANEWARRAY:
//      case Opcodes.CHECKCAST:
//      case Opcodes.INSTANCEOF: 
//      case Opcodes.IFNULL:
//      case Opcodes.IFNONNULL:
//        byteOffset += 3;
//        break;
//
//      // these have a 16-bit parameter and an 8-bit parameter
//      case Opcodes.MULTIANEWARRAY:
//        byteOffset += 4;
//        break;
//
//      // these are special cases
//      case Opcodes.INVOKEDYNAMIC:
//      case Opcodes.INVOKEINTERFACE:            
//        byteOffset += 5;
//        break;
//        
//      case Opcodes.LDC: // includes LDC_W and LDC2_W
//        Object cst = ((LdcInsnNode) absInstNode).cst;
//        String type = DataConvert.getClassType(cst);
//        String prim = DataConvert.getPrimitiveDataType(type);
//        if (type == null || prim == null || type.startsWith("[") || type.endsWith(";")) {
//          type = "Ljava/lang/Object;";
//        } else {
//          type = prim;
//        }
//        if (type.equals("D") || type.equals("J")) {
//          byteOffset += 3; // LDC2_W
//        } else {
//          byteOffset += 2; // TODO: for LDC_W, the size is 3
//        }
//        break;
//            
//      case Opcodes.TABLESWITCH:
//        // TODO: special case - do not know how to determine the size of this
//        break;
//        
//      case Opcodes.LOOKUPSWITCH:
//        // TODO: special case - do not know how to determine the size of this
//        break;
//        
//      default:
//        break;
//    }
    
    return byteOffset;
  }
  
}
