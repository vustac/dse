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
    
    public ImportMethod(String fullname) {
      this.fullName = fullname;
      this.parent = new ArrayList<>();
      this.original = false;
      this.newmeth = false;
    }
    
    public void addParent(String name) {
      this.parent.add(name);
    }
  }

  public void addMethodEntry(String fullname, ArrayList<String> parents) {
    if (fullname.startsWith("L")) {
      fullname = fullname.substring(1);
    }
    ImportMethod entry = new ImportMethod(fullname);
    entry.parent.addAll(parents);
    entry.original = true;
    entry.newmeth = false;
    graphMethList.add(entry);
  }
  
  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
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

  public void resetGraph() {
    Utils.printStatusInfo("ImportGraph: resetGraph");
  
    // reset the "new" markings in the methods in the graph
    if (callGraph != null && !graphMethList.isEmpty()) {
      for (ImportMethod entry : graphMethList) {
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
  
  public void setZoomFactor(double scaleFactor) {                                             
    if (callGraph != null) {
      callGraph.scaleVerticies(scaleFactor);
//      graphPanel.update(graphPanel.getGraphics());   
    }
  }
  
  public void addPathEntry(String methcall, String parent) {
    ImportMethod node;
    boolean change = false;
    
    if (graphMethList != null && !graphMethList.isEmpty()) {
      // check if new method found
      node = findMethodEntry(methcall);
      if (node == null) {
        // yes, add entry
        node = new ImportMethod(methcall);
        node.addParent(parent);
        node.original = false;
        node.newmeth = true;
        graphMethList.add(node);
        Utils.printStatusInfo("ImportGraph added method: " + methcall);

        // update call graph
        addCallGraphMethod(node, parent);
        change = true;
        changePending = true;
      }

      // update graph
      if (change && tabSelected) {
        updateCallGraph();
      }
    }
  }
  
  /**
   * reads the CallGraph.graphMethList entries and saves to file
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
   * imports CallGraph data
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
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    
    String panel = null;
    methInfoPanel.makePanel (panel, "PNL_INFO", LEFT, true , "", 600, 400);
    
    panel = "PNL_INFO";
    JTextPane textPanel = methInfoPanel.makeScrollTextPane(panel, "TXT_METHINFO");
    JButton bcodeButton = methInfoPanel.makeButton(panel, "BTN_BYTECODE", CENTER, true, "Show bytecode");
    
    bcodeButton.addActionListener(new Action_RunBytecode());
    
    // setup initial method info text
    String message = CallGraph.getSelectedMethodInfo(selected, -1);
    textPanel.setText(message);

    methInfoPanel.display();
  }
  
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

  private static ImportMethod findMethodEntry(String method) {
    if (method != null && !method.isEmpty()) {
      for (int ix = 0; ix < graphMethList.size(); ix++) {
        ImportMethod entry = graphMethList.get(ix);
        if (entry.fullName.equals(method)) {
          return entry;
        }
      }
    }
    return null;
  }

  /**
   * draws the graph as defined by the list passed.
   * 
   * @return the number of threads found
   */  
  private void drawCallGraph() {
    callGraph = new BaseGraph<>();

    Utils.printStatusInfo("ImportGraph: drawCallGraph Methods: " + graphMethList.size());

    // add vertexes to graph
    String color;
    for(ImportMethod mthNode : graphMethList) {
      if (mthNode != null) {
        callGraph.addVertex(mthNode, getCGName(mthNode.fullName));
        
        // set the color selection of the method
        if (mthNode.newmeth) {
          color = "FFCCCC"; // a NEW entry is set to pink
        } else if (mthNode.original) {
          color = "D2E9FF"; // an ORIGINAL method is set to default
        } else {
          color = "6666FF"; // otherwise, a new entry from a prev run is set to blue
        }
        callGraph.colorVertex(mthNode, color);
      }
    }
    
    // now connect the methods to their parents
    for (ImportMethod mthNode : graphMethList) {
      if (mthNode == null) {
        break;
      }
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
   * draws the graph as defined by the list passed.
   * 
   * @return the number of threads found
   */  
  private void addCallGraphMethod(ImportMethod mthNode, String parent) {
    String color;
    if (mthNode == null) {
      return;
    }
    ImportMethod parNode = findMethodEntry(parent);
    if (parNode == null) {
      Utils.printStatusError("ImportGraph: addCallGraphMethod: parent not found: " + parent);
      return;
    }

    // add vertex to graph and connect to its parent
    callGraph.addVertex(mthNode, getCGName(mthNode.fullName));
    callGraph.addEdge(parNode, mthNode, null);
    callGraph.colorVertex(mthNode, "FFCCCC"); // a NEW method entry is set to pink
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
          LauncherMain.runBytecodeViewer(cls, meth);
        }
      }
    }
  }
  
  private class Window_ExitListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      methInfoPanel.close();
    }
  }

  private class Action_RunBytecode implements ActionListener{
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

      LauncherMain.runBytecodeViewer(cls, meth);
      
      // close this menu
      methInfoPanel.close();
    }
  }
  
}
