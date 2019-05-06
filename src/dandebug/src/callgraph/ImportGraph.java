/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import gui.GuiControls;
import util.Utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
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
  private static BaseGraph<ImportMethod> callGraph = new BaseGraph<>();
  private static ArrayList<ImportMethod> graphMethList = new ArrayList<>();
  private static GuiControls methInfoPanel;

  public ImportGraph(String name) {
    tabName = name;
    graphPanel = new JPanel();
    gui = new GuiControls();
    scrollPanel = gui.makeRawScrollPanel(name, graphPanel);
    
    callGraph = null;
    graphComponent = null;
    graphMethList = new ArrayList<>();
    tabSelected = false;
  }
  
  public void activate() {
  }
  
  public class ImportMethod {
    public String  fullName;       // full name of method (package, class, method + signature)
    public ArrayList<String> parent; // list of caller methods (full names)
    
    public ImportMethod(String fullname) {
      this.fullName = fullname;
      this.parent = new ArrayList<>();
    }
  
  }

  public void addMethodEntry(String fullname, ArrayList<String> parents) {
      if (fullname.startsWith("L")) {
        fullname = fullname.substring(1);
      }
      ImportMethod entry = new ImportMethod(fullname);
      entry.parent.addAll(parents);
      graphMethList.add(entry);
  }
  
  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  private void printError(String message) {
    System.err.println("ERROR: " + message);
  }
  
  private void printCommandMessage(String message) {
    System.out.println(message);
  }
  
  private void printStatus(String message) {
    System.out.println(message);
  }
  
  public void clearGraph() {
    printCommandMessage("ImportGraph: clearGraph");
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

  public void setZoomFactor(double scaleFactor) {                                             
    if (callGraph != null) {
      callGraph.scaleVerticies(scaleFactor);
//      graphPanel.update(graphPanel.getGraphics());   
    }
  }
  
  public void clearPath() {
    // remove the color tracing of the traveled path from the graph
    if (callGraph != null && !graphMethList.isEmpty()) {
      for (int ix = 0; ix < graphMethList.size(); ix++) {
        ImportMethod node = graphMethList.get(ix);
        callGraph.colorVertex(node, "D2E9FF"); // set color to default
      }
    }
  }
  
  public boolean addPathEntry(String methcall) {
    // add color to specified method block
    if (callGraph != null && !graphMethList.isEmpty()) {
      // Janalyzer keeps the "L" entry in the method name, but the received method name does not
      ImportMethod node = findMethodEntry(methcall);
      if (node != null) {
        callGraph.colorVertex(node, "6666FF"); // set color to blue
        return true;
      }

      // ignore the <clinit>methods - they are not included in the methods provided by Janalyzer.
      // Also some <init> methods are excluded if they are only initializing parameters.
      if (!methcall.contains("<clinit>") && !methcall.contains("<init>")) {
        System.err.println("ImportGraph: Method not found: " + methcall);
      }
    }
    return false;
  }
  
  /**
   * reads the CallGraph.graphMethList entries and saves to file
   * 
   * @param file - name of file to save content to
   * @param allThreads - true if save all threads
   */  
  public void saveAsJSONFile(File file, boolean allThreads) {
    // open the file to write to
    BufferedWriter bw;
    try {
      bw = new BufferedWriter(new FileWriter(file));
    } catch (IOException ex) {
      printError(ex.getMessage());
      return;
    }

    // convert to json and save to file
    printStatus("ImportGraph.saveAsJSONFile: " + graphMethList.size() + " methods");
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting().serializeNulls();
    //builder.excludeFieldsWithoutExposeAnnotation().create();
    Gson gson = builder.create();
    gson.toJson(graphMethList, bw);

    try {
      bw.close();
    } catch (IOException ex) {
      printError(ex.getMessage());
    }
  }
  
  /**
   * reads method info from specified file and saves in graphMethList
   * 
   * @param file - name of file to load data from
   * @return number of methods found
   */  
  public int loadFromJSONFile(File file) {
    // open the file to read from
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(file));
    } catch (FileNotFoundException ex) {
      printError(ex.getMessage());
      return 0;
    }
    
    // load the method list info from json file
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
    Type methodListType = new TypeToken<List<ImportMethod>>() {}.getType();
    graphMethList = gson.fromJson(br, methodListType);
    printCommandMessage("ImportGraph.loadFromJSONFile: " + graphMethList.size() + " methods");

    // draw the new graph on seperate pane
    clearGraph();
    drawCallGraph(graphMethList);
      
    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
    }
      
    return graphMethList.size();
  }
  
  public void saveAsImageFile(File file) {
    printStatus("ImportGraph.saveAsImageFile: " + graphMethList.size() + " methods");
    BufferedImage bi = new BufferedImage(graphPanel.getSize().width,
        graphPanel.getSize().height, BufferedImage.TYPE_INT_ARGB); 
    Graphics graphics = bi.createGraphics();
    graphPanel.paint(graphics);
    graphics.dispose();
    try {
      ImageIO.write(bi,"png",file);
    } catch (IOException ex) {
      printError(ex.getMessage());
    }
  }

  public void updateCallGraph() {
    // exit if the graphics panel has not been established
    if (graphPanel == null || callGraph == null) {
      return;
    }
    
    // if no graph has been composed yet, set it up now
    mxGraph graph = callGraph.getGraph();
    if (graphComponent == null) {
      graphComponent = new mxGraphComponent(graph);
      graphComponent.setConnectable(false);
    }

    // update the contents of the graph component
    Graphics graphics = graphPanel.getGraphics();
    if (graphics != null) {
      graphPanel.update(graphics);
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
    
    // setup initial method info text
    String message = CallGraph.getSelectedMethodInfo(selected, -1);
    textPanel.setText(message);

    methInfoPanel.display();
  }
  
  private class Window_ExitListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      methInfoPanel.close();
    }
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
    for (int ix = 0; ix < graphMethList.size(); ix++) {
      ImportMethod entry = graphMethList.get(ix);
      if (entry.fullName.equals(method)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * draws the graph as defined by the list passed.
   * 
   * @return the number of threads found
   */  
  private void drawCallGraph(List<ImportMethod> methList) {
    callGraph = new BaseGraph<>();

    printCommandMessage("ImportGraph.drawCallGraph: Methods = " + methList.size());

    // add vertexes to graph
    for(ImportMethod mthNode : methList) {
      if (mthNode != null) {
        callGraph.addVertex(mthNode, getCGName(mthNode.fullName));
      }
    }
    
    // now connect the methods to their parents
    for (ImportMethod mthNode : methList) {
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
  
}
