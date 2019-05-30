/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import gui.GuiControls;
import callgraph.BaseGraph;
import callgraph.CallGraph;
import callgraph.MethodInfo;
import main.LauncherMain;
import util.Utils;

import com.mxgraph.model.mxCell;
import com.mxgraph.swing.handler.mxGraphHandler;
import com.mxgraph.swing.mxGraphComponent;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 *
 * @author dan
 */
public class BytecodeGraph {
  
  private static enum BlockColor { NONE, PINK, CYAN, BLUE, GOLD, VIOLET }
  
  private static String  tabName;
  private static JPanel graphPanel;
  private static JScrollPane scrollPanel;
  private static GuiControls gui;
  private static mxGraphComponent graphComponent = null;
  private static BaseGraph<FlowInfo> flowGraph = new BaseGraph<>();
  private static BytecodeViewer bcViewer;
  private static ArrayList<FlowInfo> flowBlocks = new ArrayList<>();
  private static HashMap<Integer, FlowInfo> branchMap = new HashMap<>();
  private static ArrayList<Integer> branchMarks = new ArrayList<>();

  
  public BytecodeGraph(String name, BytecodeViewer viewer) {
    tabName = name;
    graphPanel = new JPanel();
    gui = new GuiControls();
    scrollPanel = gui.makeRawScrollPanel(name, graphPanel);
    bcViewer = viewer;
  }
  
  public void activate() {
  }
  
  public JPanel getPanel() {
    return graphPanel;
  }
  
  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void drawGraphNormal() {
    branchMarks.clear();
      
    // redraw the graph without highlights
    drawGraph();
  }
  
  public void drawGraphHighlights(ArrayList<Integer> branch) {
    // copy over the highlight marks
    branchMarks.clear();

    if (branch != null) {
      for (Integer entry : branch) {
        branchMarks.add(entry);
      }
    }
    
    // redraw the graph with the highlights
    drawGraph();
  }
  
  public void clear() {
    clearGraph();
    updateGraph();
  }
  
  public boolean isValid() {
    return !flowBlocks.isEmpty();
  }
  
  public void saveAsImageFile(File file) {
    BufferedImage bi = new BufferedImage(graphPanel.getSize().width,
      graphPanel.getSize().height, BufferedImage.TYPE_INT_ARGB); 
    Graphics graphics = bi.createGraphics();
    graphPanel.paint(graphics);
    graphics.dispose();
    try {
      ImageIO.write(bi,"png",file);
    } catch (Exception ex) {
      Utils.printStatusError(ex.getMessage());
    }
  }

  private void drawGraph() {
    Utils.printStatusInfo("drawGraph: Bytecode entries = " + BytecodeViewer.bytecode.size());
    
    // clear the current graph
    clearGraph();
    
    // start with an initial ENTRY block and add EXCEPTION entry points (if any)
    FlowInfo lastBlock = new FlowInfo();
    flowBlocks.add(lastBlock);
    for (HashMap.Entry pair : BytecodeViewer.exceptions.entrySet()) {
      flowBlocks.add(new FlowInfo((String) pair.getValue(), (Integer) pair.getKey()));
    }

    // now add the opcode blocks
    int line = 0;
    for(BytecodeViewer.BytecodeInfo bc : BytecodeViewer.bytecode) {
      FlowInfo entry = new FlowInfo(line, bc);
      flowBlocks.add(entry);
      ++line;
    }
    
    // find all branch points that we need to keep
    ArrayList<Integer> branchpoints = new ArrayList<>();
    for(FlowInfo flow : flowBlocks) {
      int branchloc;
      switch (flow.type) {
        case Branch:
        case SymBranch:
          branchloc = flow.nextloc.get(0);
          if (!branchpoints.contains(branchloc)) {
            branchpoints.add(branchloc);
          }
          break;
        case Switch:
          for (int ix = 0; ix < flow.nextloc.size(); ix++) {
            branchloc = flow.nextloc.get(ix);
            if (!branchpoints.contains(branchloc)) {
              branchpoints.add(branchloc);
            }
          }
          break;
        default:
          break;
      }
    }

    // make a copy of the list so we can do some node trimming
    // Here we eliminate all BLOCKs whose predecessor was also a BLOCK if it is not a branchpoint.
    ArrayList<FlowInfo> essentialList = new ArrayList<>();
    FlowInfo last = null;
    for(FlowInfo flow : flowBlocks) {
      if (last == null || last.type != FlowType.Block || flow.type != FlowType.Block ||
                          branchpoints.contains(flow.offset)) {
        essentialList.add(flow);
        branchMap.put(flow.offset, flow); // map the line number to the flow entry
        last = flow;
      } else {
        flow.ignore = true;
      }
    }

    // now create graph nodes for all blocks
    for(FlowInfo flow : essentialList) {
      if (!flow.opcode.equals("GOTO")) {
        flowGraph.addVertex(flow, flow.title);
        
        BlockColor color = flow.color;
        if (!branchMarks.isEmpty() && branchMarks.contains(flow.offset)) {
          color = BlockColor.PINK;
        }
        String colorStr = "";
        switch (color) {
          case PINK:    colorStr = Utils.COLOR_LT_PINK;   break;
          case CYAN:    colorStr = Utils.COLOR_LT_CYAN;   break;
          case BLUE:    colorStr = Utils.COLOR_LT_BLUE;   break;
          case GOLD:    colorStr = Utils.COLOR_LT_GOLD;   break;
          case VIOLET:  colorStr = Utils.COLOR_LT_VIOLET; break;
          default:
            break;
        }
        if (!colorStr.isEmpty()) {
          flowGraph.colorVertex(flow, colorStr);
        }
      }
    }
    
    // now connect the blocks
    int nextloc = 0;
    for (int index = 0; index < essentialList.size(); index++) {
      FlowInfo flow = essentialList.get(index);
      FlowInfo nextflow;
      
      if (flow.type != FlowType.Return) { // skip any RETURN type block
        // this section handles connecting a block to the next opcode following it.
        // This is the normal process for opcodes other than GOTO and SWITCH (and the last
        // opcode in the method, which should be either a RETURN or GOTO).
        // The conditional BRANCH instructions also need to connect to the next block for the
        // case in which the branch is not taken. We must also add in the branch case for these
        // instructions, as well as the case for the SWITCH and GOTOs, which are handled in
        // the next section.
        if (index < essentialList.size() - 1) {
          // first, let's find the next valid opcode block
          nextflow = essentialList.get(index + 1);
          while (nextflow != null && nextflow.opcode.equals("GOTO")) {
            nextloc = nextflow.nextloc.get(0);
            nextflow = branchMap.get(nextloc);
          }
          if (nextflow == null) {
            Utils.printStatusError("Failed Connection: " + flow.offset + "_" +
                flow.opcode + " to offset " + nextloc);
            continue;
          }
          
          // all other opcodes except for SWITCH and GOTO will always have a connection to
          // the next opcode (this will be for one case of the branch conditions).
          if (!flow.opcode.isEmpty() && !flow.opcode.equals("GOTO") && flow.type != FlowType.Switch) {
            flowGraph.addEdge(flow, nextflow, null);
          }
        }

        // go through all connections
        ArrayList<Integer> connected = new ArrayList<>();
        for (int ix = 0; ix < flow.nextloc.size(); ix++) {
          nextloc = flow.nextloc.get(ix);
          nextflow = branchMap.get(nextloc);
          while (nextflow != null && nextflow.opcode.equals("GOTO")) {
            nextflow = branchMap.get(nextflow.nextloc.get(0));
          }
          if (nextflow == null) {
            Utils.printStatusError("Failed Connection: " + flow.offset + "_" +
                flow.opcode + " to offset " + nextloc);
            continue;
          }
          // skip if this block has already been connected to the branch location
          // (this can happen on switch statement that has multiple conditions that branch to same location)
          if (!connected.contains(nextloc)) {
            flowGraph.addEdge(flow, nextflow, null);
            connected.add(nextloc);
          }
        }
      }
    }
    
    updateGraph();
  }
  
  /**
   * displays a panel containing Call Graph information for the selected method.
   * 
   * @param selected - the method selection
   */
  private void displayMethodSolutionsPanel(String method, int offset) {
    Utils.msgLogger(Utils.LogType.INFO, "display solutions for offset " + offset + " of " + method);
    
    // check if there are any solutions to this method
    ArrayList<MethodInfo.SolutionInfo> allsols = CallGraph.getMethodSolutions(method);
    if (allsols == null) {
      Utils.msgLogger(Utils.LogType.INFO, "no solutions found");
      return;
    }

    // now weed out only the PATH type that have the specified byte offset
    ArrayList<MethodInfo.SolutionInfo> solutions = new ArrayList<>();
    Integer byteoff = BytecodeViewer.byteOffsetToLineNumber(offset);
    if (byteoff != null) {
      for (MethodInfo.SolutionInfo entry : allsols) {
        if (entry.offset == byteoff && entry.type.equals("PATH")) {
          solutions.add(entry);
        }
      }
    }
    if (solutions.isEmpty()) {
      Utils.msgLogger(Utils.LogType.INFO, "no solutions found");
      return;
    }
    
    // setup solution info text
    String message = "";
    for (MethodInfo.SolutionInfo entry : solutions) {
      message += entry.sol + Utils.NEWLINE;
    }

    // put up an info dialog,with the solution
    JOptionPane.showMessageDialog(null, message,
        "Solution for branch at byte offset " + offset, JOptionPane.PLAIN_MESSAGE);
  }
  
  private class MouseListener extends MouseAdapter {

    @Override
    public void mouseReleased(MouseEvent e) {
      int x = e.getX();
      int y = e.getY();
      mxGraphHandler handler = graphComponent.getGraphHandler();
      mxCell cell = (mxCell) handler.getGraphComponent().getCellAt(x, y);
      if (cell != null && cell.isVertex()) {
        FlowInfo selected = flowGraph.getSelectedNode();
        switch (selected.type) {
          case Call:
            // switch to the bytecode for the selected method
            Utils.ClassMethodName classmeth = new Utils.ClassMethodName(selected.bcode.callMeth);
            String meth = classmeth.methName + classmeth.signature;
            String cls = classmeth.className;

            // skip if the method is the one that's currently loaded
            if (!bcViewer.isMethodDisplayed(cls, meth)) {
              LauncherMain.runBytecodeViewer(cls, meth, false);
              LauncherMain.setBytecodeSelections(cls, meth);
            }
            break;
          case SymBranch:
            // display symbolic solution info for the branch
            displayMethodSolutionsPanel(bcViewer.getMethodName(), selected.offset);
            break;
          default:
            break;
        }
      }
    }
  }

  private void clearGraph() {
    // clear the current graph
    graphPanel.removeAll();
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
    graphComponent = null;
    flowGraph = new BaseGraph<>();
    flowBlocks.clear();
  }

  private void updateGraph() {
    graphComponent = new mxGraphComponent(flowGraph.getGraph());
    graphComponent.setConnectable(false);
    graphComponent.getGraphControl().addMouseListener(new MouseListener());
    graphPanel.add(graphComponent);

    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
      
    // update the graph layout
    flowGraph.layoutGraph();
  }

  private static enum FlowType { Entry, Exception, Call, SymBranch, Switch, Branch, Return, Block }
  
  private class FlowInfo {
    public boolean  ignore;     // true if we can ignore this block
    public FlowType type;       // type of block
    public int      line;       // bytecode line number corresponding to entry
    public int      offset;     // byte offset of opcode (-1 if not a bytecode entry)
    public String   opcode;     // opcode
    public BlockColor color;    // color to display
    public String   title;      // title for the block
    public BytecodeViewer.BytecodeInfo bcode;  // the Bytecode info
    ArrayList<Integer> nextloc = new ArrayList<>(); // list of offsets to branch to
    
    public FlowInfo(int count, BytecodeViewer.BytecodeInfo bc) {
      bcode  = bc;
      opcode = bc.opcode.toUpperCase();
      offset = bc.offset;
      line = count;
      ignore = false;

      int branchix;
      switch (bc.optype) {
        case INVOKE:
          // extract just the class and method names from the full method name
          String methname = bc.callMeth.substring(0, bc.callMeth.indexOf("("));
          int stroff = methname.lastIndexOf("/");
          if (stroff > 0) {
            methname = methname.substring(stroff + 1);
          }
          stroff = methname.indexOf(".");
          String clsname = methname.substring(0, stroff);
          methname = methname.substring(stroff + 1);

          type = FlowType.Call;
          color = BlockColor.GOLD;
          title = clsname + Utils.NEWLINE + methname;
          break;

        case SYMBRA:
          try {
            branchix = Integer.parseUnsignedInt(bc.param);

            type = FlowType.SymBranch;
            color = BlockColor.VIOLET;
            title = bc.offset + Utils.NEWLINE + bc.opcode.toUpperCase();
            nextloc.add(branchix);
          } catch (NumberFormatException ex) {
            Utils.printStatusError("Invalid Symbolic Branch location: " + bc.param);
          }
          break;

        case BRANCH:
        case GOTO:
          try {
            branchix = Integer.parseUnsignedInt(bc.param);

            type = FlowType.Branch;
            color = BlockColor.NONE;
            title = bc.offset + Utils.NEWLINE + bc.opcode.toUpperCase();
            nextloc.add(branchix);
          } catch (NumberFormatException ex) {
            Utils.printStatusError("Invalid Branch location: " + bc.param);
          }
          break;

        case SWITCH:
          type = FlowType.Switch;
          color = BlockColor.BLUE;
          title = bc.offset + Utils.NEWLINE + bc.opcode.toUpperCase();
          for (HashMap.Entry pair : bc.switchinfo.entrySet()) {
            nextloc.add((Integer) pair.getValue());
          }
          break;
          
        case RETURN:
          type = FlowType.Return;
          color = BlockColor.NONE;
          title = bc.offset + Utils.NEWLINE + bc.opcode.toUpperCase();
          // there is no branch from here
          break;

        case LOAD:
        case STORE:
        case OTHER:
          type = FlowType.Block;
          color = BlockColor.NONE;
          title = bc.offset + Utils.NEWLINE + "BLOCK";
          break;

        default:
          // indicate error
          Utils.printStatusError("Unhandled OpcodeType: " + bc.optype.toString());
          break;
      }
    }

    // this one for defining entry point
    public FlowInfo() {
      type = FlowType.Entry;
      opcode = "";
      offset = -1; // this indicates it is not an opcode
      title = "ENTRY";
      color = BlockColor.CYAN;
      nextloc.add(0);
    }

    // this one for defining exceptions
    public FlowInfo(String extype, int branchoff) {
      type = FlowType.Exception;
      opcode = "";
      offset = -1; // this indicates it is not an opcode
      title = "EXCEPTION" + Utils.NEWLINE + extype;
      color = BlockColor.CYAN;
      nextloc.add(branchoff);
    }
    
  }
  
}
