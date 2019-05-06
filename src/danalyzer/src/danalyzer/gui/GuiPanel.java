/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danalyzer.gui;

import danalyzer.gui.Danfig.TriggerSetup;

import java.awt.Color;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JTextPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author dmcd2356
 */
public class GuiPanel {

  final static private String NEWLINE = System.getProperty("line.separator");

  public enum Orient { NONE, LEFT, RIGHT, CENTER }

  private static PropertiesFile props;
  private final static GuiControls  mainFrame = new GuiControls();
  private final static GuiControls  debugFrame = new GuiControls();
  private final static GuiControls  loggerFrame = new GuiControls();
  private static JTextPane          debugTextPane;
  private static JList              symbList;
  private static JComboBox          classCBox;
  private static JComboBox          methodCBox;
  private static JFileChooser       fileSelector;
  private static DefaultListModel   symbolicGuiList;
  private static DefaultListModel   constraintGuiList;
  private static int                nextAssignedSymbol = 0;
  private static ArrayList<String>  classList;
  private static Map<String, ArrayList<String>> clsMethMap; // maps the list of methods to each class
  private static boolean            isRunning = true;
  
  public static String getSolverAddress() {
    return Danfig.solverIpAddress;
  }
  
  public static int getSolverPort() {
    return Danfig.solverPort;
  }
  
  /**
   * parses the danfig file for a list of method parameter selections to symbolize
   * 
   * @param danfig - file to parse
   * @return true on success, false if invalid file
   */
  public static boolean parseDanfigFile(File danfig) {
    System.out.println("danfig file found!");
    BufferedReader in;
    try {
      in = new BufferedReader(new FileReader(danfig));
    } catch (FileNotFoundException ex) {
      System.err.println("danfig file unreadable");
      return false;
    }

    // initialze to no triggering of debug output
    DebugUtil.setFlagsNoTrigger();
    try {
      int linecount = 0;
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        // make sure the file is a valid symbolic list
        if (linecount == 0) {
          if (line.equals(Danfig.SYMB_LIST_TITLE)) {
            System.out.println("Reading symbolic list: " + danfig.getAbsolutePath());
            // set default debug selections (no debug msgs & no gui display)
            Danfig.setDebugMode("STDOUT");
            DebugUtil.setDebugFlags(DebugUtil.ALL, false);
          }
          else {
            System.err.println("danfig file format invalid");
            return false;
          }
        }
        if (Danfig.fromDanfigFile(line, linecount) != 0) {
          System.err.println("ERROR: <GuiPanel.parseDanfigFile> parsing danfig file!");
          System.exit(0);
        }
        ++linecount;
      }
    } catch (IOException ex) {
      System.err.println("ERROR: <GuiPanel.parseDanfigFile> " + ex.getMessage());
      System.exit(0);
    }

    // create a debug panel if needed
    configureDebugOutput();
    
    // enable the selected messages
    DebugUtil.setFlagsTriggerStart();
    return true;
  }

  /**
   * greates the GUI panel for allowing the user to select the method parameters to symbolize
   * for the program.
   */
  public static void createSelectorPanel() {
    GuiPanel.classList = new ArrayList<>();
    GuiPanel.symbolicGuiList = new DefaultListModel();
    GuiPanel.constraintGuiList = new DefaultListModel();

    // check for a properties file & init debug flags
    props = new PropertiesFile();
    // load list of symbolic entries
    String value = props.getPropertiesItem("SymbolicList", "");
    if (value != null) {
      GuiPanel.symbolicGuiList.clear();
      String[] list = value.split(NEWLINE);
      for (int ix = 0; ix < list.length; ix++) {
        if (!list[ix].trim().isEmpty() && !GuiPanel.symbolicGuiList.contains(list[ix].trim())) {
          GuiPanel.symbolicGuiList.addElement(list[ix].trim());
          System.out.println("Set Symbolic entry from properties: " + list[ix].trim());
        }
      }
    }
    Danfig.setFromProperties(props);
    TriggerSetup.setFromProperties(props);
    DebugUtil.setDebugFlags(DebugUtil.ALL, false);
    DebugUtil.setDebugFlags(getPropHex("DebugFlags", 0), true);
    Danfig.setDebugMode(props.getPropertiesItem("DebugOutput", "STDOUT"));

    // display the entries for the selected parameter
    showSelectedConstraints();

    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT = GuiControls.Orient.RIGHT;
    
    // create the frame
    GuiPanel.mainFrame.newFrame("danalyzer", 800, 600, false);

    // setup the list of classes and methods for each class
    setupClassList();

    // create the entries in the main frame
    mainFrame.makePanel      (null, "PNL_SYMBOLIC"  , "Symbolic Parameter Selections", LEFT, true);
    mainFrame.setPanelSize   (      "PNL_SYMBOLIC", 780, 130);
    mainFrame.makePanel      (null, "PNL_SYM_CTRL"  , ""      , LEFT , true);
    mainFrame.makeScrollList (null, "SLIST_SYMBOLS" , ""      , GuiPanel.symbolicGuiList);

    mainFrame.makePanel      (null, "PNL_CONSTRAINT", "User-added constraints", LEFT, true);
    mainFrame.setPanelSize   (      "PNL_CONSTRAINT", 780, 50);
    mainFrame.makePanel      (null, "PNL_CON_CTRL"  , ""      , LEFT , true);
    mainFrame.makeScrollList (null, "CLIST_SYMBOLS" , ""      , GuiPanel.constraintGuiList);

    mainFrame.makePanel      (null, "PNL_BUTTONS"   , ""      , LEFT , true);
    mainFrame.makeButton     (null, "BTN_SAVE"      , "Save danfig"  , LEFT, false);
    mainFrame.makeButton     (null, "BTN_EXIT"      , "Run"   , CENTER, true);

    // now add controls to the sub-panels
    panel = "PNL_SYMBOLIC";
    mainFrame.makeCombobox (panel, "COMBO_CLASS" , "Class"    , LEFT, true);
    mainFrame.makeCombobox (panel, "COMBO_METHOD", "Method"   , LEFT, true);
    mainFrame.makeSpinner  (panel, "SPIN_PARAM"  , "Parameter", LEFT, true, 0, 99, 1, 0);
    
    panel = "PNL_SYM_CTRL";
    mainFrame.makeButton   (panel, "BTN_SYM_ADD", "Add"       , LEFT, false);
    mainFrame.makeButton   (panel, "BTN_SYM_CLR", "Clear"     , LEFT, true);

    panel = "PNL_CONSTRAINT";
    mainFrame.makeCombobox (panel, "COMBO_EQUAL", "Comparison", LEFT, false);
    mainFrame.makeTextField(panel, "TEXT_VALUE" , "Value"     , LEFT, true, "0", true);

    panel = "PNL_CON_CTRL";
    mainFrame.makeButton   (panel, "BTN_CON_ADD", "Add"       , LEFT, false);
    mainFrame.makeButton   (panel, "BTN_CON_CLR", "Clear"     , LEFT, false);
    mainFrame.makeLabel    (panel, "LBL_CON_NUM", "for Symbolic selection entry: 0", LEFT, true);
    
    panel = "PNL_BUTTONS";
    mainFrame.makeButton   (panel, "BTN_DEBUG"  , "Debug"     , LEFT, false);
    mainFrame.makeLabel    (panel, "LBL_DBGOUT" , "- Debug out to " + Danfig.getDebugMode(), LEFT, false);
    mainFrame.makeButton   (panel, "BTN_CHECK"  , "Check"     , RIGHT, false);
    mainFrame.makeLabel    (panel, "LBL_CHKOUT" , " "         , RIGHT, true);
    
    // mark the exit button
    mainFrame.getButton("BTN_EXIT").setBackground(Color.green);
    setDebugLabelContent();

    // we need a filechooser for the Load/Save buttons
    GuiPanel.fileSelector = new JFileChooser();
    
    // get access to the controls
    GuiPanel.symbList   = mainFrame.getList("SLIST_SYMBOLS");
    GuiPanel.classCBox  = mainFrame.getComboBox("COMBO_CLASS");
    GuiPanel.methodCBox = mainFrame.getComboBox("COMBO_METHOD");
    
    // add listener to symbolic list
    GuiPanel.symbList.addListSelectionListener(symbListListener);

    // disable controls based on a class list if there is none
    if (GuiPanel.classList.isEmpty()) {
      mainFrame.getComboBox("COMBO_CLASS").setEnabled(false);
      mainFrame.getLabel("COMBO_CLASS").setEnabled(false);
      mainFrame.getComboBox("COMBO_METHOD").setEnabled(false);
      mainFrame.getLabel("COMBO_METHOD").setEnabled(false);
      mainFrame.getSpinner("SPIN_PARAM").setEnabled(false);
      mainFrame.getLabel("SPIN_PARAM").setEnabled(false);
      mainFrame.getButton("BTN_SYM_ADD").setEnabled(false);
      mainFrame.getButton("BTN_SYM_CLR").setEnabled(false);
    }

    // disable controls based on list of symbolics if none are defined
    if (GuiPanel.symbolicGuiList.isEmpty()) {
      mainFrame.getButton("BTN_CON_ADD").setEnabled(false);
      mainFrame.getButton("BTN_CON_CLR").setEnabled(false);
      mainFrame.getLabel ("LBL_CON_NUM").setEnabled(false);
    } else {
      // init 1st entry as current symbolic selection and highlight it
      GuiPanel.symbList.setSelectedIndex(0);
    }
    
    // setup the constraint type selections
    JComboBox constraintCBox = mainFrame.getComboBox("COMBO_EQUAL");
    constraintCBox.removeAllItems();
    constraintCBox.addItem("EQ");
    constraintCBox.addItem("NE");
    constraintCBox.addItem("GT");
    constraintCBox.addItem("GE");
    constraintCBox.addItem("LT");
    constraintCBox.addItem("LE");
    
    // setup the control actions
    mainFrame.getComboBox("COMBO_CLASS").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JComboBox cbSelect = (JComboBox) evt.getSource();
        String clz = (String) cbSelect.getSelectedItem();
        setMethodSelections(clz, methodCBox);
        mainFrame.getFrame().pack(); // need to update frame in case width requirements change
      }
    });
//    mainFrame.getComboBox("COMBO_METHOD").addActionListener(new ActionListener() {
//      @Override
//      public void actionPerformed(java.awt.event.ActionEvent evt) {
//        // this sets the limit of the parameters based on the maxLocals defined for the method
//        JComboBox cbSelect = (JComboBox) evt.getSource();
//        int methix = cbSelect.getSelectedIndex();
//        methodComboBoxActionPerformed(methix);
//      }
//    });
    mainFrame.getButton("BTN_SYM_ADD").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // add the method & param entry to the list
        String entry = "P" + nextAssignedSymbol +
            " " + (String) classCBox.getSelectedItem() + "." + (String) methodCBox.getSelectedItem() +
            " " + mainFrame.getSpinner("SPIN_PARAM").getValue().toString();
        ++nextAssignedSymbol;
        if (!GuiPanel.symbolicGuiList.contains(entry)) {
          System.out.println("Added symbolic entry " + symbolicGuiList.size() + ": " + entry);
          GuiPanel.symbolicGuiList.addElement(entry);
          GuiPanel.symbList.setSelectedIndex(symbolicGuiList.size()-1); // select the new (last) entry
        }

        // only display the constraints for the selected parameter
        showSelectedConstraints();

        // enable constraints
        mainFrame.getButton("BTN_CON_ADD").setEnabled(true);
        mainFrame.getButton("BTN_CON_CLR").setEnabled(true);
        mainFrame.getLabel ("LBL_CON_NUM").setEnabled(true);
      }
    });
    mainFrame.getButton("BTN_SYM_CLR").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // init the gui list
        GuiPanel.symbolicGuiList.clear();
        GuiPanel.symbList.removeAll();
        GuiPanel.constraintGuiList.clear();
        Danfig.constraintRemoveAll();

        // disable constraints until a symbolic is added
        mainFrame.getButton("BTN_CON_ADD").setEnabled(false);
        mainFrame.getButton("BTN_CON_CLR").setEnabled(false);
        mainFrame.getLabel ("LBL_CON_NUM").setEnabled(false);
        mainFrame.getLabel ("LBL_CON_NUM").setText("for Symbolic selection entry: 0");
        System.out.println("Cleared all symbolic and constraint entries");
      }
    });
    mainFrame.getButton("BTN_SAVE").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveButtonActionPerformed(evt);
      }
    });
    mainFrame.getButton("BTN_CON_ADD").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        if (GuiPanel.symbolicGuiList.isEmpty()) {
          System.err.println("ERROR: <GuiPanel.createSelectorPanel> no symbolic parameters defined");
          return;
        }
        String comptype = (String) mainFrame.getComboBox ("COMBO_EQUAL").getSelectedItem();
        if (comptype == null) {
          System.err.println("ERROR: <GuiPanel.createSelectorPanel> invalid constraint type selection");
          return;
        }
        String constrVal = mainFrame.getTextField("TEXT_VALUE").getText();

        // get the selected symbolic entry (exit if user hasn't made a selection yet)
        String selection = (String) symbList.getSelectedValue();
        if (selection == null || !selection.contains(" ")) {
          System.err.println("No symbolic parameter selected!");
          return;
        }
        selection = selection.substring(0, selection.indexOf(" ")).trim(); // parameter name is 1st entry in selection

        // add entry to current display
        String value = comptype + " " + constrVal;
        Danfig.constraintAdd(selection, value);
        if (!GuiPanel.constraintGuiList.contains(value)) {
          GuiPanel.constraintGuiList.addElement(value);
        }
      }
    });
    mainFrame.getButton("BTN_CON_CLR").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // get the selected symbolic entry (exit if user hasn't made a selection yet)
        String selection = (String) symbList.getSelectedValue();
        if (selection == null || !selection.contains(" ")) {
          System.err.println("No symbolic parameter selected!");
          return;
        }
        selection = selection.substring(0, selection.indexOf(" ")).trim(); // parameter name is 1st entry in selection

        // remove constraints for the current parameter selection
        int count = Danfig.constraintRemoveParameter(selection);

        // init the gui list for current selection
        GuiPanel.constraintGuiList.clear();
        System.out.println("Cleared " + count + " constraint entries for parameter " + selection);
        System.out.println("Constraints defined: " + Danfig.constraintList.size());
      }
    });
    mainFrame.getButton("BTN_EXIT").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        exitGui();
        GuiPanel.mainFrame.close();
      }
    });
    mainFrame.getButton("BTN_DEBUG").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // start up the debug select panel
        createDebugSelectPanel();
      }
    });
    mainFrame.getButton("BTN_CHECK").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // send a test message
        JLabel results = mainFrame.getLabel("LBL_CHKOUT");

        int ret = DebugMessage.outputCheck(Danfig.getDebugMode(), Danfig.debugIpAddress, Danfig.debugPort);

        String message;
        if (ret == 0) {
          message = "Success";
        } else {
          message = "Failed";
        }
        results.setText(message);
      }
    });
    GuiPanel.mainFrame.getFrame().addWindowFocusListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowGainedFocus(java.awt.event.WindowEvent evt) {
        // this handles when the main panel gets focus, such as when we close the debug panel.
        // nothing to do for now.
      }
    });
    GuiPanel.mainFrame.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        exitGui();
      }
    });
    
    // setup the class and method selections
    if (GuiPanel.classList.size() > 0) {
      classCBox.removeAllItems();
      for (int ix = 0; ix < GuiPanel.classList.size(); ix++) {
        classCBox.addItem(GuiPanel.classList.get(ix));
      }
      if (classCBox.getItemCount() > 0) {
        classCBox.setSelectedIndex(0);
      }
    }
    
    // display the frame
    GuiPanel.mainFrame.display();

    // create the selector panel and wait until user exits
    while (GuiPanel.isRunning) {
      try {
        TimeUnit.SECONDS.sleep(1);
      } catch (InterruptedException ex) {
        System.err.println("ERROR: <GuiPanel.windowClosing> " + ex.getMessage());
      }
    }
      
    // now start up the debug panel, if one was selected
    configureDebugOutput();
  }

  private static void exitGui() {
    // update the properties file with any new selections
    // create list of symbolic entries
    String list = "";
    for (int ix = 0; ix < GuiPanel.symbolicGuiList.getSize(); ix++) {
      if (ix > 0) {
        list += NEWLINE;
      }
      list += (String) GuiPanel.symbolicGuiList.get(ix);
    }
    props.setPropertiesItem("SymbolicList", list);

    // debug ip address and port
    Danfig.saveToProperties(props);
        
    // add the symbolic entries
    for (int ix = 0; ix < GuiPanel.symbolicGuiList.getSize(); ix++) {
      String entry = (String) GuiPanel.symbolicGuiList.get(ix);
      Danfig.fromDanfigFile("Symbolic: " + entry, ix);
    }

    // add the constraint entries
    for (int ix = 0; ix < Danfig.constraintList.size(); ix++) {
      String entry = (String) Danfig.constraintList.get(ix);
      Danfig.fromDanfigFile("Constraint: " + entry, ix);
    }

    // setup the triggering conditions
    DebugUtil.setFlagsTriggerStart();
    GuiPanel.isRunning = false;
  }

  private static ListSelectionListener symbListListener = new ListSelectionListener() {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      // only display the constraints for the selected parameter
      showSelectedConstraints();
    }
    
  };

  private static void showSelectedConstraints() {
    if (GuiPanel.symbList == null) {
      return;
    }
    
    // get the selected symbolic entry (exit if user hasn't made a selection yet)
    String selection = (String) symbList.getSelectedValue();
    if (selection == null || !selection.contains(" ")) {
      System.err.println("No symbolic parameter selected!");
      return;
    }
    selection = selection.substring(0, selection.indexOf(" ")).trim(); // parameter name is 1st entry in selection

    mainFrame.getLabel ("LBL_CON_NUM").setText("for Symbolic selection entry: " + selection);

    // only display the constraints for the selected parameter
    GuiPanel.constraintGuiList.clear();
    for(int ix = 0; ix < Danfig.constraintList.size(); ix++) {
      String constraint = Danfig.constraintList.get(ix);
      String[] fields = constraint.split(" ");
      if (fields.length != 3) {
        System.err.println("ERROR: <showSelectedConstraints> - invalid constraint: " + constraint);
        return;
      }
      if (selection.equals(fields[0])) {
        constraint = fields[1] + " " + fields[2];
        GuiPanel.constraintGuiList.addElement(constraint);
      }
    }
  }
  
  private static void createDebugSelectPanel() {
    if (GuiPanel.debugFrame.isValidFrame()) {
      return;
    }

    // make sure we have valid settings upon entry
    if (TriggerSetup.tCount < 1 || TriggerSetup.tCount > 10000) {
      TriggerSetup.tCount = 1;
    }
    if (TriggerSetup.tRange < 0) {
      TriggerSetup.tRange = 0;
    }
    int flags = DebugUtil.getDebugFlags();

    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT   = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT  = GuiControls.Orient.RIGHT;
    
    // create the frame
    GuiPanel.debugFrame.newFrame("Debug Selection", 240, 300, false);

    // create the entries in the main frame
    debugFrame.makePanel (null, "PNL_DBGFLAGS", "Debug Flags" , LEFT, false);
    debugFrame.makePanel (null, "PNL_DEBUGOUT", "Debug Output", RIGHT, false);
    debugFrame.makePanel (null, "PNL_TRIGGER" , "Triggering"  , LEFT, true);
    debugFrame.makePanel (null, "PNL_TRIGMETH", "Triggering Method Selection" , CENTER, true);
    debugFrame.makeButton(null, "BTN_EXIT"    , "Exit"        , CENTER, true);
    
    // now add controls to the sub-panels
    panel = "PNL_DBGFLAGS";
    debugFrame.makeCheckbox(panel, "DBG_WARNING" , "Warnings"      , LEFT, false , flags & DebugUtil.WARN);
    debugFrame.makeCheckbox(panel, "DBG_CALL"    , "Call/Return"   , LEFT, true  , flags & DebugUtil.CALLS);
    debugFrame.makeCheckbox(panel, "DBG_INFO"    , "Info"          , LEFT, false , flags & DebugUtil.INFO);
    debugFrame.makeCheckbox(panel, "DBG_STACK"   , "Stack"         , LEFT, true  , flags & DebugUtil.STACK);
    debugFrame.makeCheckbox(panel, "DBG_COMMAND" , "Commands"      , LEFT, false , flags & DebugUtil.COMMAND);
    debugFrame.makeCheckbox(panel, "DBG_LOCALS"  , "Locals"        , LEFT, true  , flags & DebugUtil.LOCAL);
    debugFrame.makeCheckbox(panel, "DBG_AGENT"   , "Agent"         , LEFT, false , flags & DebugUtil.AGENT);
    debugFrame.makeCheckbox(panel, "DBG_OBJECTS" , "Objects"       , LEFT, true  , flags & DebugUtil.OBJECTS);
    debugFrame.makeCheckbox(panel, "DBG_BRANCH"  , "Branch"        , LEFT, false , flags & DebugUtil.BRANCH);
    debugFrame.makeCheckbox(panel, "DBG_ARRAYS"  , "Arrays"        , LEFT, true  , flags & DebugUtil.ARRAYS);
    debugFrame.makeCheckbox(panel, "DBG_CONSTR"  , "Constraints"   , LEFT, false , flags & DebugUtil.CONSTR);
    debugFrame.makeCheckbox(panel, "DBG_SOLVER"  , "Solver"        , LEFT, true  , flags & DebugUtil.SOLVE);

    panel = "PNL_DEBUGOUT";
    debugFrame.makeRadiobutton(panel, "RB_STDOUT", "Debug to stdout"  , LEFT, true,
        (Danfig.getDebugMode().equals("STDOUT")) ? 1 : 0);
    debugFrame.makeRadiobutton(panel, "RB_GUI"   , "Debug to gui"     , LEFT, true,
        (Danfig.getDebugMode().equals("GUI")) ? 1 : 0);
    debugFrame.makeRadiobutton(panel, "RB_UDP"   , "Debug to UDP port", LEFT, true,
        (Danfig.getDebugMode().equals("UDPPORT")) ? 1 : 0);
    debugFrame.makeRadiobutton(panel, "RB_TCP"   , "Debug to TCP port", LEFT, true,
        (Danfig.getDebugMode().equals("TCPPORT")) ? 1 : 0);
    debugFrame.makeTextField(panel, "TEXT_IPADDR" , "Addr"            , LEFT, true,
        Danfig.debugIpAddress, true);
    debugFrame.makeTextField(panel, "TEXT_PORT"   , "Port"            , LEFT, true,
        Danfig.debugPort + "", true);
    
    panel = "PNL_TRIGGER";
    debugFrame.makeCheckbox(panel, "TRIG_ON_ERROR" , "on Error"      , LEFT , true ,
        TriggerSetup.isTriggerEnabled(DebugUtil.TRG_ERROR));
    debugFrame.makeCheckbox(panel, "TRIG_ON_EXCEPT", "on Exception"  , LEFT , true ,
        TriggerSetup.isTriggerEnabled(DebugUtil.TRG_EXCEPT));
    debugFrame.makeCheckbox(panel, "TRIG_ON_LINE"  , "on Instruction", LEFT , false,
        TriggerSetup.isTriggerEnabled(DebugUtil.TRG_INSTRUCT));
    debugFrame.makeSpinner (panel, "SPIN_LINE"     , "Count"         , RIGHT, true , 1, 1000000, 100,
        TriggerSetup.tInsNum);
    debugFrame.makeCheckbox(panel, "TRIG_ON_CALL"  , "on Call"       , LEFT , false,
        TriggerSetup.isTriggerEnabled(DebugUtil.TRG_CALL));
    debugFrame.makeSpinner (panel, "SPIN_COUNT"    , "Count"         , RIGHT, true , 1, 10000, 1,
        TriggerSetup.tCount);
    debugFrame.makeCheckbox(panel, "TRIG_ON_RETURN", "on Return"     , LEFT , true ,
        TriggerSetup.isTriggerEnabled(DebugUtil.TRG_RETURN));
    debugFrame.makeCheckbox(panel, "AUTO_RESET"    , "Auto Reset"    , LEFT , false,
        TriggerSetup.tAuto ? 1 : 0);
    debugFrame.makeSpinner (panel, "SPIN_RANGE"    , "Limit"         , RIGHT, true , 0, 100000, 100,
        TriggerSetup.tRange);

    panel = "PNL_TRIGMETH";
    debugFrame.makeCombobox(panel, "COMBO_CLASS"   , "Class"     , LEFT, true);
    debugFrame.makeCombobox(panel, "COMBO_METHOD"  , "Method"    , LEFT, true);
    debugFrame.makeCheckbox(panel, "ANY_METHOD"    , "Any Method", LEFT, true ,
        TriggerSetup.tAnyMeth ? 1 : 0);
    
    // mark the exit button
    debugFrame.getButton("BTN_EXIT").setBackground(Color.green);

    // get access to the controls
    JComboBox trigClassCBox = debugFrame.getComboBox("COMBO_CLASS");
    JComboBox trigMethCBox  = debugFrame.getComboBox("COMBO_METHOD");

    // disable selections if all triggers disabled
    enableTriggerControls();
    
    // setup the class and method selections (setupClassList was previously called)
    trigClassCBox.removeAllItems();
    for (int ix = 0; ix < GuiPanel.classList.size(); ix++) {
      trigClassCBox.addItem(GuiPanel.classList.get(ix));
    }
    // if last selection is valid, set class and method to these values
    if (trigClassCBox.getItemCount() > 0) {
      if (TriggerSetup.tClass != null && !TriggerSetup.tClass.isEmpty() &&
          GuiPanel.classList.contains(TriggerSetup.tClass)) {
        trigClassCBox.setSelectedItem(TriggerSetup.tClass);

        setMethodSelections((String) trigClassCBox.getSelectedItem(), trigMethCBox);
        DefaultComboBoxModel model = (DefaultComboBoxModel) trigMethCBox.getModel();
        if (TriggerSetup.tMethod != null && !TriggerSetup.tMethod.isEmpty() &&
            model.getIndexOf(TriggerSetup.tMethod) >= 0) {
//            GuiPanel.methodList.contains(GuiPanel.trigMethod)) {
          trigMethCBox.setSelectedItem(TriggerSetup.tMethod);
        }
      } else {
        trigClassCBox.setSelectedIndex(0);
        TriggerSetup.tClass = "";
        TriggerSetup.tMethod = "";
      }
    }

    // setup the control actions
    GuiPanel.debugFrame.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent evt) {
        // set the runtime debug flags based on the selections
        TriggerSetup.tClass   = (String) debugFrame.getComboBox("COMBO_CLASS").getSelectedItem();
        TriggerSetup.tMethod  = (String) debugFrame.getComboBox("COMBO_METHOD").getSelectedItem();
        TriggerSetup.tCount   = (Integer) debugFrame.getSpinner("SPIN_COUNT").getValue();
        TriggerSetup.tRange   = (Integer) debugFrame.getSpinner("SPIN_RANGE").getValue();
        TriggerSetup.tInsNum  = (Integer) debugFrame.getSpinner("SPIN_LINE").getValue();
        TriggerSetup.tAuto    = debugFrame.getCheckBox("AUTO_RESET").isSelected();
        TriggerSetup.tAnyMeth = debugFrame.getCheckBox("ANY_METHOD").isSelected();
        TriggerSetup.tEnable = 0;
        TriggerSetup.tEnable |= debugFrame.getCheckBox("TRIG_ON_ERROR").isSelected()  ? DebugUtil.TRG_ERROR : 0;
        TriggerSetup.tEnable |= debugFrame.getCheckBox("TRIG_ON_EXCEPT").isSelected() ? DebugUtil.TRG_EXCEPT : 0;
        TriggerSetup.tEnable |= debugFrame.getCheckBox("TRIG_ON_LINE").isSelected()   ? DebugUtil.TRG_INSTRUCT: 0;
        TriggerSetup.tEnable |= debugFrame.getCheckBox("TRIG_ON_CALL").isSelected()   ? DebugUtil.TRG_CALL : 0;
        TriggerSetup.tEnable |= debugFrame.getCheckBox("TRIG_ON_RETURN").isSelected() ? DebugUtil.TRG_RETURN : 0;

        // setup the triggering conditions
        DebugUtil.setFlagsTriggerStart();

        // save degug flag selection in properties file
        if (props != null) {
          String hexval = Integer.toHexString(DebugUtil.getDebugFlags());
          props.setPropertiesItem("DebugFlags", hexval);
          TriggerSetup.saveToProperties(props);
        }

        // update ipaddr and port selections
        Danfig.debugIpAddress = debugFrame.getTextField("TEXT_IPADDR").getText();
        String port = debugFrame.getTextField("TEXT_PORT").getText();
        try {
          Danfig.debugPort = Integer.parseUnsignedInt(port);
        } catch (NumberFormatException ex) {
          System.err.println("Invalid port selection: " + port + " - reset to default (5000)");
          Danfig.debugPort = 5000;
        }
    
        // update main frame indicator
        setDebugLabelContent();

        // update the main frame
        GuiPanel.mainFrame.update();
        GuiPanel.debugFrame.close();
      }
    });
    debugFrame.getButton("BTN_EXIT").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // update main frame indicator
        setDebugLabelContent();
        mainFrame.update();

        // kill frame (this will cause the windowClosed action to occur)
        GuiPanel.debugFrame.getFrame().dispose();
      }
    });
    debugFrame.getCheckBox("ANY_METHOD").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        boolean bAnyMeth = cbSelect.isSelected();
        debugFrame.getComboBox("COMBO_METHOD").setEnabled(!bAnyMeth);
        debugFrame.getLabel   ("COMBO_METHOD").setEnabled(!bAnyMeth);
      }
    });
    debugFrame.getCheckBox("TRIG_ON_LINE").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        enableTriggerControls();
      }
    });
    debugFrame.getCheckBox("TRIG_ON_CALL").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        enableTriggerControls();
      }
    });
    debugFrame.getCheckBox("TRIG_ON_ERROR").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        enableTriggerControls();
      }
    });
    debugFrame.getCheckBox("TRIG_ON_EXCEPT").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        enableTriggerControls();
      }
    });
    debugFrame.getComboBox("COMBO_CLASS").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JComboBox cbSelect = (JComboBox) evt.getSource();
        JComboBox trigMethCBox = debugFrame.getComboBox("COMBO_METHOD");
        setMethodSelections((String) cbSelect.getSelectedItem(), trigMethCBox);
        debugFrame.getFrame().pack(); // need to update frame in case width requirements change
      }
    });
    debugFrame.getCheckBox("DBG_WARNING").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.WARN, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_BRANCH").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.BRANCH, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_CONSTR").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.CONSTR, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_SOLVER").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.SOLVE, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_CALL").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.CALLS, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_OBJECTS").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.OBJECTS, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_ARRAYS").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.ARRAYS, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_STACK").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.STACK, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_LOCALS").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.LOCAL, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_COMMAND").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.COMMAND, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_INFO").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.INFO, cbSelect.isSelected());
      }
    });
    debugFrame.getCheckBox("DBG_AGENT").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cbSelect = (JCheckBox) evt.getSource();
        DebugUtil.setDebugFlags(DebugUtil.AGENT, cbSelect.isSelected());
      }
    });
    debugFrame.getRadioButton("RB_GUI").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JRadioButton rbSelect = (JRadioButton) evt.getSource();
        if (rbSelect.isSelected()) {
          setSelection_GUI();
          props.setPropertiesItem("DebugOutput", "GUI");
        }
      }
    });
    debugFrame.getRadioButton("RB_UDP").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JRadioButton rbSelect = (JRadioButton) evt.getSource();
        if (rbSelect.isSelected()) {
          setSelection_UDP();
          props.setPropertiesItem("DebugOutput", "UDPPORT");
        }
      }
    });
    debugFrame.getRadioButton("RB_TCP").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JRadioButton rbSelect = (JRadioButton) evt.getSource();
        if (rbSelect.isSelected()) {
          setSelection_TCP();
          props.setPropertiesItem("DebugOutput", "TCPPORT");
        }
      }
    });
    debugFrame.getRadioButton("RB_STDOUT").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JRadioButton rbSelect = (JRadioButton) evt.getSource();
        if (rbSelect.isSelected()) {
          setSelection_STDOUT();
          props.setPropertiesItem("DebugOutput", "STDOUT");
        }
      }
    });

    // display the frame
    GuiPanel.debugFrame.display();
  }

/**
 * creates a debug panel to display the DebugMessage messages in
 * 
 * @return the panel to use
 */  
  private static void createDebugPanel() {
    if (GuiPanel.mainFrame != null) {
      GuiPanel.mainFrame.close();
    }
    
    // create the frame
    // create the frame
    GuiPanel.loggerFrame.newFrame("danalyzer", 1000, 600, false);

    // we need a filechooser for the Save buttons
    GuiPanel.fileSelector = new JFileChooser();

    // add the components
    loggerFrame.makeButton(null, "BTN_SAVETEXT", "Save", GuiControls.Orient.CENTER, true);
    GuiPanel.debugTextPane = loggerFrame.makeScrollText(null, "SRCOLL_TEXT", "");

    // setup the control actions
    loggerFrame.getButton("BTN_SAVETEXT").addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveDebugButtonActionPerformed(evt);
      }
    });

    // display the frame
    GuiPanel.loggerFrame.display();
  }

  private static void setDebugLabelContent() {
    JLabel msgOut = mainFrame.getLabel("LBL_DBGOUT");
    if (msgOut != null) {
      String hexval = Integer.toHexString(DebugUtil.getDebugFlags());
      String content = "- Debug out to " + Danfig.getDebugMode() + ", Flags = 0x" + hexval +
              (TriggerSetup.isTriggered() ? ", Triggered" : ", Immediate");
      msgOut.setText(content);
    }
  }
  
  private static void setSelection_STDOUT() {
    if (debugFrame.isValidFrame()) {
      debugFrame.getRadioButton("RB_STDOUT").setSelected(true);
      debugFrame.getRadioButton("RB_UDP").setSelected(false);
      debugFrame.getRadioButton("RB_TCP").setSelected(false);
      debugFrame.getRadioButton("RB_GUI").setSelected(false);
    }

    // save the debug output selection
    Danfig.setDebugMode("STDOUT");
  }
  
  private static void setSelection_UDP() {
    if (debugFrame.isValidFrame()) {
      debugFrame.getRadioButton("RB_STDOUT").setSelected(false);
      debugFrame.getRadioButton("RB_UDP").setSelected(true);
      debugFrame.getRadioButton("RB_TCP").setSelected(false);
      debugFrame.getRadioButton("RB_GUI").setSelected(false);
    }

    // save the debug output selection
    Danfig.setDebugMode("UDPPORT");
  }
  
  private static void setSelection_TCP() {
    if (debugFrame.isValidFrame()) {
      debugFrame.getRadioButton("RB_STDOUT").setSelected(false);
      debugFrame.getRadioButton("RB_UDP").setSelected(false);
      debugFrame.getRadioButton("RB_TCP").setSelected(true);
      debugFrame.getRadioButton("RB_GUI").setSelected(false);
    }

    // save the debug output selection
    Danfig.setDebugMode("TCPPORT");
  }
  
  private static void setSelection_GUI() {
    if (debugFrame.isValidFrame()) {
      debugFrame.getRadioButton("RB_STDOUT").setSelected(false);
      debugFrame.getRadioButton("RB_UDP").setSelected(false);
      debugFrame.getRadioButton("RB_TCP").setSelected(false);
      debugFrame.getRadioButton("RB_GUI").setSelected(true);
    }

    // save the debug output selection
    Danfig.setDebugMode("GUI");
  }
  
  private static void configureDebugOutput() {
    DebugMessage debug;
    switch (Danfig.getDebugMode()) {
      case "GUI":
        // if text panel does not already exist, create a text panel & output messages to it
        if (GuiPanel.debugTextPane == null) {
          GuiPanel.createDebugPanel();
          debug = new DebugMessage(GuiPanel.debugTextPane);
        }
        break;
      case "UDPPORT":
        // output messages to UDP port
        GuiPanel.debugTextPane = null;
        debug = new DebugMessage(Danfig.debugIpAddress, Danfig.debugPort, false);
        break;
      case "TCPPORT":
        // output messages to TCP port
        GuiPanel.debugTextPane = null;
        debug = new DebugMessage(Danfig.debugIpAddress, Danfig.debugPort, true);
        break;
      default:
      case "STDOUT":
        // output debug msgs to stdout
        GuiPanel.debugTextPane = null;
        debug = new DebugMessage(null);
        break;
    }
  }
  
  private static int getPropHex(String field, int dflt) {
    int result = dflt;
    if (props != null) {
      String value = props.getPropertiesItem(field, "" + dflt);
      if (value != null) {
        try {
          result = (int) Long.parseLong(value, 16);
        } catch (NumberFormatException ex) {
          System.err.println("ERROR: <GuiPanel.getPropHex> Invalid property value "
                  + value + " for field: " + field);
        }
      }
    }
    return result;
  }

  private static void enableTriggerControls() {
    boolean bLine   = debugFrame.getCheckBox("TRIG_ON_LINE").isSelected();
    boolean bCall   = debugFrame.getCheckBox("TRIG_ON_CALL").isSelected();
    boolean bError  = debugFrame.getCheckBox("TRIG_ON_ERROR").isSelected();
    boolean bExcept = debugFrame.getCheckBox("TRIG_ON_EXCEPT").isSelected();
    boolean bAnyMeth = debugFrame.getCheckBox("ANY_METHOD").isSelected();

    // the range is used by all trigger types
    debugFrame.getSpinner("SPIN_RANGE").setEnabled(bLine || bCall || bError || bExcept);
    debugFrame.getLabel  ("SPIN_RANGE").setEnabled(bLine || bCall || bError || bExcept);

    // can't reset line count trigger, but the others can be
    debugFrame.getCheckBox("AUTO_RESET").setEnabled(bCall || bError || bExcept);

    // line number is only used by trigger on line
    debugFrame.getSpinner("SPIN_LINE").setEnabled(bLine);
    debugFrame.getLabel  ("SPIN_LINE").setEnabled(bLine);

    // these selections are only used by the trigger on call
    debugFrame.getSpinner("SPIN_COUNT").setEnabled(bCall);
    debugFrame.getLabel  ("SPIN_COUNT").setEnabled(bCall);
    debugFrame.getComboBox("COMBO_CLASS").setEnabled(bCall);
    debugFrame.getLabel   ("COMBO_CLASS").setEnabled(bCall);
    debugFrame.getComboBox("COMBO_METHOD").setEnabled(bCall && !bAnyMeth); // disable these if "any method" selected
    debugFrame.getLabel   ("COMBO_METHOD").setEnabled(bCall && !bAnyMeth);
    debugFrame.getCheckBox("ANY_METHOD").setEnabled(bCall);
    debugFrame.getCheckBox("TRIG_ON_RETURN").setEnabled(bCall);
  }
  
  /**
   * finds the classes in a jar file & sets the Class ComboBox to these values.
   */
  private static void setupClassList () {
    // init the class list
    GuiPanel.clsMethMap = new HashMap<>();
    GuiPanel.classList = new ArrayList<>();
    ArrayList<String> fullMethList = new ArrayList<>();

    // read the list of methods from the "methodlist.txt" file created by Instrumentor
    try {
      File file = new File("methodlist.txt");
      FileReader fileReader = new FileReader(file);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        fullMethList.add(line);
      }
      fileReader.close();
    } catch (IOException ex) {
      System.err.println("ERROR: <GuiPanel.setupClassList> " + ex);
      return;
    }

    // exit if no methods were found
    if (fullMethList.isEmpty()) {
      System.err.println("ERROR: <GuiPanel.setupClassList> No methods found in methodlist.txt file!");
      return;
    }
        
    // now map the entries from the method list to the corresponding class name
    Collections.sort(fullMethList);
    String curClass = "";
    ArrayList<String> methList = new ArrayList<>();
    for (String fullMethodName : fullMethList) {
      int methOffset = fullMethodName.indexOf(".");
      if (methOffset > 0) {
        String className = fullMethodName.substring(0, methOffset);
        String methName = fullMethodName.substring(methOffset + 1);
        // if new class was found and a method list was valid, save the class and classMap
        if (!curClass.equals(className) && !methList.isEmpty()) {
          GuiPanel.classList.add(curClass);
          clsMethMap.put(curClass, methList);
          methList = new ArrayList<>();
        }

        // save the class name for the list and add the method name to it
        curClass = className;
        methList.add(methName);
      }
    }

    // save the remaining class
    if (!methList.isEmpty()) {
      GuiPanel.classList.add(curClass);
      clsMethMap.put(curClass, methList);
    }

    System.out.println(GuiPanel.classList.size() + " classes and " +
        fullMethList.size() + " methods found");
  }
  
  private static void setMethodSelections(String clsname, JComboBox methCBox) {
    // init the method selection list
    methCBox.removeAllItems();

    // make sure we have a valid class selection
    if (clsname == null || clsname.isEmpty()) {
      return;
    }
    
    if (clsname.endsWith(".class")) {
      clsname = clsname.substring(0, clsname.length()-".class".length());
    }

    // get the list of methods for the selected class from the hash map
    ArrayList<String> methodSelection = GuiPanel.clsMethMap.get(clsname);
    if (methodSelection != null) {
      // now get the methods for the class and place in the method selection combobox
      for (String method : methodSelection) {
        methCBox.addItem(method);
      }

      // set the 1st entry as the default selection
      if (methCBox.getItemCount() > 0) {
        methCBox.setSelectedIndex(0);
      }
    }
  }
  
//  private static void methodComboBoxActionPerformed(int methIx) {
//    // get the number of locals for the method (I think this includes the passed parameters)
//    if (classNodeSelection != null) {
//      List<MethodNode> list = (List<MethodNode>)classNodeSelection.methods;
//      if (methIx >= 0) {
//        MethodNode method = list.get(methIx);
//        if (method != null) {
//          int maxval = (method.maxLocals <= 0) ? 0 : method.maxLocals - 1;
//          JSpinner spinner = mainFrame.getSpinner("SPIN_PARAM");
//          SpinnerModel model = spinner.getModel();
//          int current = (int)model.getValue();
//          if (current > maxval) {
//            current = maxval;
//          }
//          spinner.setModel(new SpinnerNumberModel(current, 0, maxval, 1));
//        }
//      }
//    }
//  }

  private static void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {
    FileNameExtensionFilter filter = new FileNameExtensionFilter("danfig Files","danfig");
    GuiPanel.fileSelector.setCurrentDirectory(new File(System.getProperty("user.dir")));
    GuiPanel.fileSelector.setFileFilter(filter);
    GuiPanel.fileSelector.setApproveButtonText("Save");
    GuiPanel.fileSelector.setMultiSelectionEnabled(false);
    String defaultFile = "danfig";
    GuiPanel.fileSelector.setSelectedFile(new File(defaultFile));
    int retVal = GuiPanel.fileSelector.showOpenDialog(GuiPanel.mainFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      String content = Danfig.toDanfigFile(symbolicGuiList);

      // output to the file
      File file = GuiPanel.fileSelector.getSelectedFile();
      file.delete();
      try {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write( content);
        writer.close();
      } catch (IOException ex) {
        System.err.println("ERROR: <GuiPanel.saveButtonActionPerformed> " + ex.getMessage());
      }
    }
  }
  
  private static void saveDebugButtonActionPerformed(java.awt.event.ActionEvent evt) {
    String defaultFile = "debug.log";
    GuiPanel.fileSelector.setApproveButtonText("Save");
    GuiPanel.fileSelector.setMultiSelectionEnabled(false);
    GuiPanel.fileSelector.setSelectedFile(new File(defaultFile));
    int retVal = GuiPanel.fileSelector.showOpenDialog(GuiPanel.mainFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      // copy debug text to specified file
      String content = GuiPanel.debugTextPane.getText();

      // output to the file
      File file = GuiPanel.fileSelector.getSelectedFile();
      file.delete();
      try {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write( content);
        writer.close();
      } catch (IOException ex) {
        System.err.println("ERROR: <GuiPanel.saveDebugButtonActionPerformed> " + ex.getMessage());
      }
    }
  }

}
