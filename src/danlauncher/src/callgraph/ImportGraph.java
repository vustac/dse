/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import main.LauncherMain;
import gui.GuiControls;
import util.Utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.handler.mxGraphHandler;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

/**
 *
 * @author dan
 */
public class ImportGraph {
    
  private static String  tabName;
  private static JPanel  graphPanel;
  private static JScrollPane scrollPanel;
  private static boolean     tabSelected;
  private static GuiControls gui;
  private static mxGraphComponent graphComponent;
  private static BaseGraph<ImportMethod> callGraph;
  private static ArrayList<ImportMethod> graphMethList;
  private static GuiControls methInfoPanel;
  private static boolean changePending;

  public ImportGraph(String name) {
    tabName = name;
    graphPanel = new JPanel();
    gui = new GuiControls();
    scrollPanel = gui.makeRawScrollPanel(name, graphPanel);
    
    callGraph  = null;
    graphComponent = null;
    graphMethList = new ArrayList<>();
    tabSelected = false;
    changePending = false;
  }
  
  public void activate() {
  }
  
  public class ImportMethod {
    public String  fullName;          // full name of method (package, class, method + signature)
    public ArrayList<String> parent;  // list of caller methods (full names)
    public boolean original;          // true if method was one of the original loaded
    public boolean newmeth;           // true if method was new during this run
    public boolean oldmeth;           // true if method was new during previous run
    
    public ImportMethod(String fullname) {
      this.fullName = fullname;
      this.parent = new ArrayList<>();
      this.original = false;
      this.newmeth = false;
      this.oldmeth = false;
    }
    
    public void addParent(String name) {
      this.parent.add(name);
    }
  }

  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  public void setZoomFactor(double scaleFactor) {                                             
    if (callGraph != null) {
      callGraph.scaleVerticies(scaleFactor);
//      graphPanel.update(graphPanel.getGraphics());   
    }
  }
  
  /**
   * this is used to clear the entire graph contents and refresh the display.
   */
  public void clearGraph() {
    Utils.printStatusInfo("ImportGraph: clearGraph");
    // remove all methods
    graphMethList.clear();

    // re-draw call graph
    drawCallGraph();
  
    graphComponent = null;
    if (graphPanel != null) {
      graphPanel.removeAll();
      Graphics graphics = graphPanel.getGraphics();
      if (graphics != null) {
        graphPanel.update(graphics);
      }
    }
  }

  /**
   * this is used to simply clear the display. The call graph information is left intact.
   */
  private void clearDisplay() {
    Utils.printStatusInfo("ImportGraph: clearDisplay");
    callGraph = null;
    graphComponent = null;

    if (graphPanel != null) {
      graphPanel.removeAll();
      Graphics graphics = graphPanel.getGraphics();
      if (graphics != null) {
        graphPanel.update(graphics);
      }
    }
  }

  /**
   * this is used to reset the call graph contents to its initial loaded value.
   * 
   * (returns to last value from either import from CallGraph (using addMethodEntry, or
   * from loadFromJSONFile.
   */
  public void resetGraph() {
    Utils.printStatusInfo("ImportGraph: resetGraph");
  
    // reset the "new" markings in the methods in the graph to "old"
    if (callGraph != null && !graphMethList.isEmpty()) {
      for (ImportMethod entry : graphMethList) {
        entry.oldmeth = entry.newmeth;
        entry.newmeth = false;
      }
    }

    // re-draw the graph
    changePending = true;
    clearDisplay();
    drawCallGraph();

    // update the contents of the graph component
    if (graphPanel != null) {
      graphPanel.removeAll();
      Graphics graphics = graphPanel.getGraphics();
      if (graphics != null) {
        graphPanel.update(graphics);
      }
    }
  }
  
  /**
   * this is called during execution when a new method has been called.
   * 
   * This will always set the 'newmeth' flag to indicate the method was called during execution
   * of the current path. The call graph produced will indicate PINK if this was a path not
   * previously encountered and BLUE if it was encountered before.
   * 
   * @param methcall - the name of the method executed
   * @param parent   - the name of the method that called this method
   */
  public void addPathEntry(String methcall, String parent) {
    ImportMethod node;
    
    if (graphMethList != null && !graphMethList.isEmpty()) {
      // check if new method found
      node = findMethodEntry(methcall);
      if (node == null) {
        // yes, add new entry
        node = new ImportMethod(methcall);
        node.addParent(parent);
        node.original = false;
        node.newmeth = true;
        node.oldmeth = false;
        graphMethList.add(node);
        Utils.printStatusInfo("ImportGraph added method: " + methcall);

        // update call graph
        callGraphMethodAdd(node);
        callGraphMethodSetColor(node);
        callGraphMethodConnect(node);
        changePending = true;
      } else if (! node.newmeth) {
        // no, set flag to indicate this was also part of the new path
        node.newmeth = true;
        callGraphMethodSetColor(node);
        changePending = true;
      }

      // update graph
      if (changePending && tabSelected) {
        updateCallGraph();
      }
    }
  }
  
  /**
   * this is only used for copying the current Call Graph contents over to this Explore Graph.
   * 
   * Because of this, all method entries are marked as 'original'.
   * 
   * @param fullname - name of method being added
   * @param parents  - array of methods that call this method
   */
  public void addMethodEntry(String fullname, ArrayList<String> parents) {
    if (fullname.startsWith("L")) {
      fullname = fullname.substring(1);
    }
    ImportMethod entry = new ImportMethod(fullname);
    entry.parent.addAll(parents);
    entry.original = true;
    entry.newmeth = false;
    entry.oldmeth = false;
    graphMethList.add(entry);
  }
  
  /**
   * reads the CallGraph.graphMethList entries and saves as JSON format to file
   * 
   * @param file - name of file to save content to
   */  
  public void saveAsJSONFile(File file) {
    if (graphMethList == null || graphMethList.isEmpty()) {
      Utils.printStatusError("Import Graph is empty!");
      return;
    }
    
    // convert the list of MethodInfo into an list of JsonMethod entries
    List<JsonMethod> methlist = new ArrayList<>();
    for (ImportMethod entry : graphMethList) {
      methlist.add(new JsonMethod(entry.fullName, entry.parent));
    }

    Utils.printStatusInfo("saving ImportGraph: " + graphMethList.size() + " methods to file " + file.getName());
    Utils.saveAsJSONFile(methlist, file);
  }
  
  /**
   * reads method info from specified file and saves in graphMethList
   * 
   * @param file - name of file to load data from
   * @return number of methods found
   */  
  public int loadFromJSONFile(File file) {
    // clear out previous list
    graphMethList.clear();

    // open the file to read from
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(file));
    } catch (FileNotFoundException ex) {
      Utils.printStatusError(ex.getMessage());
      return 0;
    }
    
    // load the method list info from json file
    GsonBuilder builder = new GsonBuilder();
    Gson gson = builder.create();
    Type methodListType = new TypeToken<List<ImportMethod>>() {}.getType();
    graphMethList = gson.fromJson(br, methodListType);
    Utils.printStatusInfo("loaded ImportGraph: " + graphMethList.size() + " methods from file " + file.getName());

    // init all types to ORIGINAL
    if (graphMethList != null) {
      for (ImportMethod entry : graphMethList) {
        entry.original = true;
        entry.newmeth = false;
        entry.oldmeth = false;
      }
    }

    // draw the new graph
    changePending = true;
    clearDisplay();
    drawCallGraph();
      
    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
      
    return graphMethList.size();
  }
  
  /**
   * imports CallGraph data.
   * 
   * CallGraph will make repeated calls to 'addMethodEntry' in this class for each method.
   * 
   * @param cg - the call graph to copy from
   */  
  public void importData(CallGraph cg) {
    // clear out previous list
    graphMethList.clear();

    // copy data from Call Graph
    cg.exportData(this);
    Utils.printStatusInfo("ImportGraph data imported : " + graphMethList.size() + " methods");

    // draw the new graph
    changePending = true;
    clearDisplay();
    drawCallGraph();

    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
  }
  
  /**
   * reads the CallGraph.graphMethList entries and saves as image to file
   * 
   * @param file - name of file to save content to
   */  
  public void saveAsImageFile(File file) {
    // make sure we have updated the graphics before we save
    updateCallGraph();

    Utils.printStatusInfo("save ImportGraph as ImageFile: " + graphMethList.size()
            + " methods, size " + graphPanel.getSize().width + " x " + graphPanel.getSize().height);

    BufferedImage bi = new BufferedImage(graphPanel.getSize().width,
                                         graphPanel.getSize().height,
                                         BufferedImage.TYPE_INT_ARGB); 
    Graphics graphics = bi.createGraphics();
    graphPanel.paint(graphics);
    graphics.dispose();
    try {
      ImageIO.write(bi,"png",file);
    } catch (IOException ex) {
      Utils.printStatusError(ex.getMessage());
    }
  }

  /**
   * performs an update of the explore graph to the display.
   */
  public void updateCallGraph() {
    // exit if the graphics panel has not been established
    if (graphPanel == null || callGraph == null || !changePending) {
      return;
    }
    
    // if no graph has been composed yet, set it up now
    mxGraph graph = callGraph.getGraph();
    if (graphComponent == null) {
      graphComponent = new mxGraphComponent(graph);
      graphComponent.setConnectable(false);
      graphComponent.getGraphControl().addMouseListener(new MouseListener());
      graphPanel.add(graphComponent);
    }

    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
      changePending = false;
    }
      
    // update the graph layout
    callGraph.layoutGraph();
  }
  
  /**
   * returns the name to display in the Call Graph for the method.
   * 
   * This should be: class.method
   * where class does not include the package path and method only consists of the method name
   * 
   * @param fullname - the full name of the method
   * @return the CG-formatted method name to display
   */
  private static String getCGName(String fullname) {
    String  className = "";
    String  methName = fullname.replace("/", ".");
    if (methName.contains("(")) {
      methName = methName.substring(0, methName.lastIndexOf("("));
    }
    int offset = methName.lastIndexOf(".");
    if (offset > 0) {
      className = methName.substring(0, offset);
      methName = methName.substring(offset + 1);
      offset = className.lastIndexOf(".");
      if (offset > 0) {
        className = className.substring(offset + 1);
      }
    }
    return className.isEmpty() ? methName : className + Utils.NEWLINE + methName;
  }

  /**
   * search for and return the entry in graphMethList having the specified method name.
   * 
   * @param method - the name of the method to search for
   * @return 
   */
  private static ImportMethod findMethodEntry(String method) {
    method = method.replaceAll("/", ".");
    if (method != null && !method.isEmpty()) {
      for (int ix = 0; ix < graphMethList.size(); ix++) {
        ImportMethod entry = graphMethList.get(ix);
        String tblmeth = entry.fullName.replaceAll("/", ".");
        if (tblmeth.equals(method)) {
          return entry;
        }
      }
    }
    return null;
  }

  /**
   * sets the color of the specified call graph method
   * 
   * @param mthNode - the ImportMethod entry to color
   */
  private void callGraphMethodSetColor(ImportMethod mthNode) {
    if (mthNode != null) {
      // set the color selection of the method
      boolean oldpath = mthNode.original | mthNode.oldmeth;
      String color = "D2E9FF"; // this is the default (GREY) color
      if (mthNode.newmeth) {
        if (oldpath) {
          color = "FF6666"; // a new entry from a prev run is set to ORANGE
        } else {
          color = "FFCCCC"; // a new entry that was never encountered before is set to PINK
        }
      } else if (mthNode.oldmeth) {
        color = "6666FF";   // a prev run entry is set to BLUE
      }
      callGraph.colorVertex(mthNode, color);
    }
  }
  
  /**
   * adds the specified method to the call graph
   * 
   * @param mthNode - the ImportMethod entry to add
   */
  private void callGraphMethodAdd(ImportMethod mthNode) {
    if (mthNode != null) {
      callGraph.addVertex(mthNode, getCGName(mthNode.fullName));
    }
  }

  /**
   * connects the specified method to its callers in the call graph.
   * 
   * NOTE: all parent methods must be defined prior to this call.
   * 
   * @param mthNode - the ImportMethod entry to connect
   */
  private void callGraphMethodConnect(ImportMethod mthNode) {
    if (mthNode != null) {
      // for each parent entry for a method...
      for (String parent : mthNode.parent) {
        if (parent != null && !parent.isEmpty()) {
          ImportMethod parNode = findMethodEntry(parent);
          // only add connection if parent was found and there isn't already a connection
          if (parNode != null && callGraph.getEdge(parNode, mthNode) == null) {
            // now add the connection from the method to the parent
            callGraph.addEdge(parNode, mthNode, null);
          }
        }
      }
    }
  }
  
  /**
   * draws the graph as defined by the list passed (performs color formatting as well).
   * 
   * @return the number of threads found
   */  
  private void drawCallGraph() {
    callGraph = new BaseGraph<>();

    Utils.printStatusInfo("ImportGraph: drawCallGraph Methods: " + graphMethList.size());

    // add vertexes to graph
    for(ImportMethod mthNode : graphMethList) {
      callGraphMethodAdd(mthNode);
      callGraphMethodSetColor(mthNode);
    }
    
    // now connect the methods to their parents
    for (ImportMethod mthNode : graphMethList) {
      callGraphMethodConnect(mthNode);
    }
  }

  /**
   * displays a panel containing Call Graph information for the selected method.
   * 
   * @param selected - the method selection
   */
  private void displayMethodInfoPanel(MethodInfo selected) {
    // if panel is currently displayed for another method, close that one before opening the new one
    if (methInfoPanel != null) {
      methInfoPanel.close();
    }
    
    // create the panel to display the selected method info on
    methInfoPanel = new GuiControls();
    JFrame frame = methInfoPanel.newFrame("Executed Method Information", 600, 450, GuiControls.FrameSize.FIXEDSIZE);
    frame.addWindowListener(new Window_ExitListener());

    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient RIGHT = GuiControls.Orient.RIGHT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    
    String panel = null;
    methInfoPanel.makePanel (panel, "PNL_INFO", LEFT, true , "", 600, 400);
    
    panel = "PNL_INFO";
    JTextPane textPanel =
        methInfoPanel.makeScrollTextPane(panel, "TXT_METHINFO");
    JButton bcodeButton =
        methInfoPanel.makeButton(panel, "BTN_BYTECODE", CENTER, false, "Show Byte Code");
    JButton bflowButton =
        methInfoPanel.makeButton(panel, "BTN_BYTEFLOW", CENTER, false, "Show Byte Flow");
    JButton bExit =
        methInfoPanel.makeButton(panel, "BTN_EXIT", RIGHT, true, "Exit");
    
    bcodeButton.addActionListener(new Action_RunBytecode());
    bflowButton.addActionListener(new Action_RunByteflow());
    bExit.addActionListener(new Action_RunExit());

    // setup initial method info text
    String message = CallGraph.getSelectedMethodInfo(selected, -1);
    textPanel.setText(message);
    textPanel.setFont(new Font("Courier New", Font.PLAIN, 12));

    methInfoPanel.display();
  }
  
  private class Window_ExitListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      methInfoPanel.close();
    }
  }

  private class Action_RunExit implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      methInfoPanel.close();
    }
  }
  
  private class Action_RunBytecode implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // show selected method in bytecode viewer
      ImportMethod selected = callGraph.getSelectedNode();

      Utils.ClassMethodName classmeth = new Utils.ClassMethodName(selected.fullName);
      String meth = classmeth.methName + classmeth.signature;
      String cls = classmeth.className;
      
      LauncherMain.runBytecodeViewer(cls, meth, false);
      
      // close this menu
      methInfoPanel.close();
    }
  }
  
  private class Action_RunByteflow implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      if (callGraph == null) {
        return;
      }
      
      // show selected method in bytecode viewer
      // Janalyzer keeps the "L" entry in the method name, but the Bytecode viewer name does not
      ImportMethod selected = callGraph.getSelectedNode();

      Utils.ClassMethodName classmeth = new Utils.ClassMethodName(selected.fullName);
      String meth = classmeth.methName + classmeth.signature;
      String cls = classmeth.className;

      LauncherMain.runBytecodeViewer(cls, meth, true);
      
      // close this menu
      methInfoPanel.close();
    }
  }
  
  /**
   * add listener to show details of selected element
   */
  private class MouseListener extends MouseAdapter {
    @Override
    public void mouseReleased(MouseEvent e) {
      if (callGraph == null) {
        return;
      }
      
      // get coordinates of mouse click & see if it is one of the method blocks
      mxGraphHandler handler = graphComponent.getGraphHandler();
      mxCell cell = (mxCell) handler.getGraphComponent().getCellAt(e.getX(), e.getY());
      if (cell != null && cell.isVertex()) {
        // yes, show selected method in bytecode viewer
        // Janalyzer keeps the "L" entry in the method name, but the Bytecode viewer name does not
        ImportMethod selected = callGraph.getSelectedNode();
        String methname = selected.fullName;
        if (methname.startsWith("L")) {
          methname = methname.substring(1);
        }
        MethodInfo methSelect = CallGraph.getMethodInfo(methname);
        if (methSelect != null) {
          displayMethodInfoPanel(methSelect);
        } else {
          Utils.ClassMethodName classmeth = new Utils.ClassMethodName(selected.fullName);
          String meth = classmeth.methName + classmeth.signature;
          String cls = classmeth.className;
          LauncherMain.runBytecodeViewer(cls, meth, true);
        }
      }
    }
  }
  
}
