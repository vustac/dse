package danalyzer.instrumenter;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

/**
 *
 * @author zzk
 */
public class LoopIdentifier {
  public static class Node {
    private boolean                 mark = false;
    private int                     number = -1;
    private List<AbstractInsnNode>  instructionList = new LinkedList<>();
    private Set<Node>               predecessorSet = new HashSet<>();
    private Set<Node>               successorSet = new HashSet<>();
    
    public boolean isMarked() {
      return this.mark;
    }
    
    public void setMark() {
      this.mark = true;
    }
    
    public void clearMark() {
      this.mark = false;
    }
    
    public int getNumber() {
      return this.number;
    }
    
    public void setNumber(int num) {
      this.number = num;
    }
    
    public void addInstruction(AbstractInsnNode inst) {
      if (inst == null)
        return;
      this.instructionList.add(inst);
    }
    
    public void addControlFlow(Node dstNode) {
      if (dstNode == null)
        return;
      this.successorSet.add(dstNode);
      dstNode.predecessorSet.add(this);
    }
    
    public Node splitNode(AbstractInsnNode inst) {
      int instPos = this.instructionList.indexOf(inst);
      if (instPos == -1)
        return null;
      else if (instPos == 0)
        return this;
      
      // new a node, which takes over the instructions from instPos to the end
      Node newNode = new Node();
      List newList = this.instructionList.subList(instPos, this.instructionList.size());
      newNode.instructionList.addAll(newList);
      newList.clear();
      
      // transfer control flows from "this" to "new"
      for (Node succNode : this.successorSet) {
        succNode.predecessorSet.remove(this);
        succNode.predecessorSet.add(newNode);
        newNode.successorSet.add(succNode);
      }
      this.successorSet.clear();
      
      return newNode;
    }
    
    public List<AbstractInsnNode> getInstructionList() {
      return this.instructionList;
    }
    
    public AbstractInsnNode getLastInstruction() {
      return this.instructionList.get(this.instructionList.size() - 1);
    }
    
    public Set<Node> getPredecessorSet() {
      return this.predecessorSet;
    }
    
    public Set<Node> getSuccessorSet() {
      return this.successorSet;
    }
  }
  
  private TreeMap<Integer, Node>  nodeMap = new TreeMap<>();
  private Map<LabelNode, Integer> labelMap = new HashMap<>();
  private Node                    entry = new Node();
  private Node                    exit = new Node();
  
  private LinkedList<Node>        reversePostOrderList = new LinkedList<>();
  private Map<Node, Node>         dominatorMap = new HashMap<>();
  private Set<Node>               loopHeaderSet = new HashSet<>();
  
  public LoopIdentifier(MethodNode mtd) {
    // use a set of positions to keep where the flow of control is affected
    Set<Integer> ctrlPosSet = new HashSet<>();
    int instPos = 0;
    boolean newNodeFlag = true;
    Node node = null;
    
    InsnList instList = mtd.instructions;
    Iterator<AbstractInsnNode> absInstNodeIter = instList.iterator();
    while (absInstNodeIter.hasNext()) {
      AbstractInsnNode absInst = absInstNodeIter.next();
      int opcode = absInst.getOpcode();
      //if (opcode <= 0)
        //continue;
      if (absInst.getType() == AbstractInsnNode.LABEL) {
        LabelNode label = (LabelNode) absInst;
        this.labelMap.put(label, instPos);
      }
      
      if (newNodeFlag) {
        node = new Node();
        this.nodeMap.put(instPos, node);
        newNodeFlag = false;
      }
      
      // we only care about intra-procedural control transfer, so we ignore method invocations
      switch (opcode) {
        case Opcodes.IFEQ:
        case Opcodes.IFNE:
        case Opcodes.IFLT:
        case Opcodes.IFGE:
        case Opcodes.IFGT:
        case Opcodes.IFLE:
        case Opcodes.IF_ICMPEQ:
        case Opcodes.IF_ICMPNE:
        case Opcodes.IF_ICMPLT:
        case Opcodes.IF_ICMPGE:
        case Opcodes.IF_ICMPGT:
        case Opcodes.IF_ICMPLE:
        case Opcodes.IF_ACMPEQ:
        case Opcodes.IF_ACMPNE:
        case Opcodes.IFNULL:
        case Opcodes.IFNONNULL:
        case Opcodes.LOOKUPSWITCH:
        case Opcodes.TABLESWITCH:
        case Opcodes.GOTO:
        case Opcodes.JSR:
        case Opcodes.RET:
        case Opcodes.ARETURN:
        case Opcodes.DRETURN:
        case Opcodes.FRETURN:
        case Opcodes.IRETURN:
        case Opcodes.LRETURN:
        case Opcodes.RETURN:
        case Opcodes.ATHROW:
          ctrlPosSet.add(instPos);
          newNodeFlag = true;
      }
      
      node.addInstruction(absInst);
      ++instPos;
    }
    
    // add the initial control flows (entry-->exit) and (entry-->1st)
    this.entry.addControlFlow(this.exit);
    if (!this.nodeMap.isEmpty()) {
      Node head = this.nodeMap.firstEntry().getValue();
      this.entry.addControlFlow(head);
    }
    
    // construct intra-procedural CFG
    for (int ctrlPos : ctrlPosSet) {
      Node ctrlNode = getNode(ctrlPos);
      AbstractInsnNode absInst = ctrlNode.getLastInstruction();
      int opcode = absInst.getOpcode();
      
      // if it is a return/throw instruction, we don't need to do things further
      switch (opcode) {
        case Opcodes.RET:
        case Opcodes.ARETURN:
        case Opcodes.DRETURN:
        case Opcodes.FRETURN:
        case Opcodes.IRETURN:
        case Opcodes.LRETURN:
        case Opcodes.RETURN:
        case Opcodes.ATHROW:
          ctrlNode.addControlFlow(this.exit);
          continue;
      }
      
      // we have either a branch or a switch instruction
      Set<LabelNode> labelSet = new HashSet<>();
      if (opcode == Opcodes.LOOKUPSWITCH || opcode == Opcodes.TABLESWITCH) {
        if (opcode == Opcodes.LOOKUPSWITCH) {
          LookupSwitchInsnNode switchInst = (LookupSwitchInsnNode) absInst;
          labelSet.add(switchInst.dflt);
          labelSet.addAll(switchInst.labels);
        } else {
          TableSwitchInsnNode switchInst = (TableSwitchInsnNode) absInst;
          labelSet.add(switchInst.dflt);
          labelSet.addAll(switchInst.labels);
        }
      } else {
        JumpInsnNode jumpInst = (JumpInsnNode) absInst;
        labelSet.add(jumpInst.label);
        // deal with fall-through
        if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR) {
          Node nextNode = getNode(ctrlPos + 1);
          ctrlNode.addControlFlow(nextNode);
        }
      }
      
      // now we have a bunch of labels
      for (LabelNode label : labelSet) {
        int labelPos = this.labelMap.get(label);
        Node prevNode = getNode(labelPos);
        Node tagtNode = prevNode.splitNode(label);
        if (prevNode != tagtNode) {
          prevNode.addControlFlow(tagtNode);
          this.nodeMap.put(labelPos, tagtNode);
        }
        ctrlNode.addControlFlow(tagtNode);
      }
    }
    
    // we go to find loop headers
    generateReversePostOrder(this.entry);
    for (int i = 0; i < this.reversePostOrderList.size(); ++i)
      this.reversePostOrderList.get(i).setNumber(i);
    generateDominatorTree();
    identifyLoops();
  }
  
  private Node getNode(int instPos) {
    Integer key = this.nodeMap.floorKey(instPos);
    if (key == null)
      return null;
    return this.nodeMap.get(key);
  }
  
  // use DFS to generate reverse post-order for CFG
  private void generateReversePostOrder(Node node) {
    node.setMark();
    
    Set<Node> succNodeSet = node.getSuccessorSet();
    for (Node succNode : succNodeSet) {
      if (!succNode.isMarked())
        generateReversePostOrder(succNode);
    }
    
    // RPO needs to have a node at front of all its followers
    this.reversePostOrderList.addFirst(node);
  }
  
  // refer to "A Simple, Fast Dominance Algorithm"
  private void generateDominatorTree() {
    this.dominatorMap.put(this.entry, this.entry);
    
    boolean change = true;
    while (change) {
      change = false;
      // for all nodes except the entry in RPO
      for (Node node : this.reversePostOrderList) {
        if (node == this.entry)
          continue;
        Node oldDomNode = this.dominatorMap.get(node);
        Node newDomNode = null;
        boolean init = true;
        
        Set<Node> predNodeSet = node.getPredecessorSet();
        for (Node predNode : predNodeSet) {
          // if it has not been assigned a idominator, it has not been processed yet
          if (!this.dominatorMap.containsKey(predNode))
            continue;
          
          // pick up any processed predecessor as the initial idominator
          if (init) {
            newDomNode = predNode;
            init = false;
          }
          
          // move the fingers up along the dominator tree till they converge
          Node tempDomNode = predNode;
          while (newDomNode != tempDomNode) {
            if (newDomNode.getNumber() > tempDomNode.getNumber())
              newDomNode = this.dominatorMap.get(newDomNode);
            else
              tempDomNode = this.dominatorMap.get(tempDomNode);
          }
        }
        
        if (newDomNode != oldDomNode) {
          this.dominatorMap.put(node, newDomNode);
          change = true;
        }
      }
    }
  }
  
  // identify loops in this method
  private void identifyLoops() {
    for (Node node : this.reversePostOrderList) {
      Set<Node> succNodeSet = node.getSuccessorSet();
      for (Node succNode : succNodeSet) {
        // check if this is a back edge in the graph
        if (succNode.getNumber() > node.getNumber())
          continue;
        
        // it is a back edge (i.e. succNodeNum <= nodeNum), so find which dominator this edge goes back to
        // since it may go back to itself, we start from itself
        Node domNode = node;
        while (domNode != this.entry || domNode == succNode) {
          // if the node goes back to its dominator, its dominator is the loop header
          if (domNode == succNode) {
            this.loopHeaderSet.add(domNode);
            break;
          }
          domNode = this.dominatorMap.get(domNode);
        }
      }
    }
  }
  
  public Set<Node> getLoopHeaderSet() {
    return this.loopHeaderSet;
  }
}