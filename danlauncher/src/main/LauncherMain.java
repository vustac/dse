/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import gui.GuiControls;
import logging.FontInfo;
import logging.Logger;
import logging.DebugParams;
import panels.BytecodeViewer;
import panels.BytecodeGraph;
import panels.DatabaseTable;
import panels.DebugLogger;
import panels.HelpLogger;
import panels.ParamTable;
import panels.SymbolTable;
import callgraph.CallGraph;
import callgraph.ImportGraph;
import util.CommandLauncher;
import util.ThreadLauncher;
import util.Utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author dmcd2356
 */
public final class LauncherMain {

  private static final PrintStream STANDARD_OUT = System.out;
  private static final PrintStream STANDARD_ERR = System.err;
  
  // locations to store output files created in the project dir
  private static final String PROJ_CONFIG = ".danlauncher";
  private static final String OUTPUT_FOLDER = "danlauncher/";
  private static final String CLASSFILE_STORAGE = OUTPUT_FOLDER + "classes";
  private static final String JAVAPFILE_STORAGE = OUTPUT_FOLDER + "javap";

  // location of this program
  private static final String HOMEPATH = System.getProperty("user.dir");

  // default location for jre for running instrumented project
  private static final String JAVA_HOME = "/usr/lib/jvm/java-8-openjdk-amd64";
  
  // Properties that are specific to the danlauncher system
  private enum SystemProperties { 
    SYS_JAVA_HOME,    // location of jvm path used for running instrumented jar
    SYS_DSEPATH,      // location of the DSE repo
    SYS_PROJECT_PATH, // last project path location (to start location to look for jar file to load)
    SYS_LOG_LENGTH,   // max log file length to maintain in debugLogger
    SYS_DEBUG_PORT,   // the TCP port to receive debug info from the application (in debugParams file)
    SYS_SOLVER_PORT,  // the TCP port for application to send solver output to   (in debugParams file)
    SYS_CLR_OLD_FILES,  // clear out the old javap and class files when a jar file is loaded
  }
  
  // Properties that are project-specific (excluding the DebugParams settings)
  private enum ProjectProperties { 
    RUN_ARGUMENTS,    // the argument list for the application to run
    MAIN_CLASS,       // the Main Class for the application
    APP_SERVER_PORT,  // the server port to send messages to in the application
    INPUT_MODE,       // type of input for the application
  }
  
  // Method of input to running application
  private enum InputMode {
    NONE,             // no input method
    STDIN,            // command-line standard input
    HTTP_RAW,         // simple message (unformatted) to HTTP port
    HTTP_POST,        // using a POST to HTTP port
    HTTP_GET,         // using GET to HTTP port
  }
  
  // Commands that can be saved in record session
  private enum RecordID {
    RUN,              // start the application (optional arglist)
    STOP,             // terminate the application
    DELAY,            // delay in seconds (arg required)
    WAIT_FOR_TERM,    // wait until application has terminated
    WAIT_FOR_SERVER,  // wait until server has come up
    SET_HTTP,         // set the HTTP port to use (arglist required = port)
    SEND_HTTP_RAW,    // send message to HTTP port (arglist required)
    SEND_HTTP_POST,   // send POST-formatted message to HTTP port (arglist required)
    SEND_HTTP_GET,    // send GET-formatted  message to HTTP port
    SEND_STDIN,       // send message to standard input (arglist required)
    CHECK,            // verify solution found (name and value)
  }
  
  private enum RunMode { IDLE, RUNNING, TERMINATING, KILLING, EXITING }

  // tab panel selections
  private enum PanelTabs { COMMAND, SOLUTIONS, BYTECODE, BYTEFLOW, LOG, CALLGRAPH, COMPGRAPH }
  
  private static CallGraph       callGraph;
  private static ImportGraph     importGraph;
  private static BytecodeViewer  bytecodeViewer;
  private static BytecodeGraph   bytecodeGraph;
  private static DebugLogger     debugLogger;
  private static HelpLogger      helpLogger;
  private static ThreadLauncher  threadLauncher;
  private static DatabaseTable   dbtable;
  private static ParamTable      localVarTbl;
  private static SymbolTable     symbolTbl;
  private static NetworkServer   udpThread;
  private static NetworkListener networkListener;
  private static SolverInterface solverConnection;
  private static Visitor         makeConnection;
  private static Logger          commandLogger;
  private static DebugParams     debugParams;
  private static JFileChooser    fileSelector;
  private static JFileChooser    javaHomeSelector;
  private static JComboBox       mainClassCombo;
  private static JComboBox       classCombo;
  private static JComboBox       methodCombo;
  private static InputMode       inputMode;
  private static boolean         loadDanfigOnStartup;
  private static boolean         clearSolutionsOnStartup;
  private static boolean         clearSolutionsOnRun;
  private static boolean         clearClassFilesOnStartup;
  private static boolean         allowRunWithoutSolver;
  private static JMenuBar        launcherMenuBar;
  private static Timer           debugMsgTimer;
  private static Timer           graphTimer;
  private static Timer           killTimer;
  private static CallGraph.GraphHighlight  graphMode;
  private static String          projectPathName;
  private static String          projectName;
  private static long            startTime;
  private static int             debugPort;
  private static int             solverPort;
  private static int             tabIndex;
  private static String          clientPort;
  private static String          javaHome;
  private static String          dsePath;
  private static String          maxLogLength;
  private static boolean         mainClassInitializing;
  private static RunMode         runMode;
  private static String          currentTab;
  
  // recording info for creating test
  private static boolean         recordState;
  private static long            recordStartTime;
  private static final ArrayList<RecordCommand>   recording = new ArrayList<>();
  
  // the collection of classes and methods for the selected project
  private static ArrayList<String> fullMethList = new ArrayList<>();
  private static ArrayList<String> classList = new ArrayList<>();
  private static HashMap<String, ArrayList<String>>  clsMethMap = new HashMap<>(); // maps the list of methods to each class
  
  private static final HashMap<String, JCheckBoxMenuItem> menuCheckboxes = new HashMap<>();
  private static final HashMap<String, JMenuItem>         menuItems = new HashMap<>();
  private static final HashMap<String, Integer>           tabSelect = new HashMap<>();
  private static final HashMap<String, FontInfo>          bytecodeFontTbl = new HashMap<>();
  private static final HashMap<String, FontInfo>          debugFontTbl = new HashMap<>();
  private static final ArrayList<MethodHistoryInfo>       bytecodeHistory = new ArrayList<>();
  
  // the gui frames created
  private static final GuiControls mainFrame = new GuiControls();
  private static final GuiControls graphSetupFrame = new GuiControls();
  private static final GuiControls debugSetupFrame = new GuiControls();
  private static final GuiControls systemSetupFrame = new GuiControls();
  private static final GuiControls helpFrame = new GuiControls();

  // configuration file settings
  private static PropertiesFile  systemProps;   // this is for the generic properties for the user
  private static PropertiesFile  projectProps;  // this is for the project-specific properties

  // this defines the project properties that have corresponding user controls
  // (this does not include the Debug parameters that are defined in DebugParams)
  private static final PropertiesTable[] PROJ_PROP_TBL = {
    new PropertiesTable (ProjectProperties.RUN_ARGUMENTS,
                      mainFrame, "TXT_ARGLIST"  , GuiControls.InputControl.TextField),
    new PropertiesTable (ProjectProperties.APP_SERVER_PORT,
                      mainFrame, "TXT_PORT"     , GuiControls.InputControl.TextField),
    new PropertiesTable (ProjectProperties.MAIN_CLASS,
                      mainFrame, "COMBO_MAINCLS", GuiControls.InputControl.ComboBox ),
    new PropertiesTable (ProjectProperties.INPUT_MODE,
                      mainFrame, "COMBO_INMODE" , GuiControls.InputControl.ComboBox ),
  };

  private static class PropertiesTable {
    public ProjectProperties tag;         // the tag used to access the Properties entry
    public GuiControls       panel;       // the GuiControl panel that holds the control
    public String            controlName; // name of the widget corresponding to this property
    public GuiControls.InputControl controlType; // the type of user-input widget
    
    public PropertiesTable(ProjectProperties propname, GuiControls frame, String name, GuiControls.InputControl type) {
      tag = propname;
      panel = frame;
      controlName = name;
      controlType = type;
    }
  }
  
  private static class RecordCommand {
    public RecordID command;
    public String   argument;
    
    public RecordCommand(RecordID cmd, String arg) {
      command = cmd;
      argument = arg;
    }
  }
  
  private static class MethodHistoryInfo {
    public String className;
    public String methodName;
    
    public MethodHistoryInfo(String cls, String meth) {
      className = cls;
      methodName = meth;
    }
  }
  
  // allow ServerThread to indicate on panel when a connection has been made for TCP
  public interface Visitor {
    void showConnection(String connection);
    void resetConnection();
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // start the debug message panel
    LauncherMain gui = new LauncherMain();
  }

  private LauncherMain() {
    clientPort = "";
    makeConnection = new Visitor() {
      @Override
      public void showConnection(String connection) {
        clientPort = connection;
        printCommandMessage("connected to  " + clientPort);
      }

      @Override
      public void resetConnection() {
        printCommandMessage("connected to  " + clientPort + "  (CONNECTION CLOSED)");
      }
    };

    classList = new ArrayList<>();
    clsMethMap = new HashMap<>();
    graphMode = CallGraph.GraphHighlight.NONE;
    runMode = RunMode.IDLE;
    mainClassInitializing = false;
    recordState = false;
    tabIndex = 0;
    String projectPath;

    // check for the global danlauncher properties file and init default values if not found
    systemProps = new PropertiesFile(HOMEPATH + "/" + PROJ_CONFIG, "SYSTEM_PROPERTIES");
    javaHome     = systemProps.getPropertiesItem("SYS_JAVA_HOME", JAVA_HOME);
    dsePath      = systemProps.getPropertiesItem("SYS_DSEPATH", HOMEPATH);
    maxLogLength = systemProps.getPropertiesItem("SYS_LOG_LENGTH", "500000");
    projectPath  = systemProps.getPropertiesItem("SYS_PROJECT_PATH", HOMEPATH);
    debugPort    = systemProps.getIntegerProperties("SYS_DEBUG_PORT", 5000, 100, 65535);
    solverPort   = systemProps.getIntegerProperties("SYS_SOLVER_PORT", 4000, 100, 65535);
    clearClassFilesOnStartup = systemProps.getPropertiesItem("SYS_CLR_OLD_FILES","false").equals("true");
    
    inputMode = InputMode.NONE;
    loadDanfigOnStartup = true;
    clearSolutionsOnStartup = true;
    clearSolutionsOnRun = false;
    allowRunWithoutSolver = false;
    
    // we need a filechooser and initialize it to the project path
    fileSelector = new JFileChooser();
    fileSelector.setCurrentDirectory(new File(projectPath));

    // create the main panel and controls
    createMainPanel();

    if (debugLogger != null && !maxLogLength.isEmpty()) {
      try {
        int size = Integer.parseUnsignedInt(maxLogLength);
        debugLogger.setMaxBufferSize(size);
      } catch (NumberFormatException ex) {
        printCommandError("ERROR: invalid value for System Properties SYS_LOG_LENGTH: " + maxLogLength);
      }
    }

    // create an interface to send commands to the solver
    solverConnection= new SolverInterface(solverPort);
    if (!solverConnection.isValid()) {
      printStatusError("solver port failure on " + solverPort +
          ". Make sure dansolver is running and verify port.");
    } else {
      printStatusMessage("Connected to Solver on port " + solverPort);
    }
    
    // this creates a command launcher that can run on a separate thread
    threadLauncher = new ThreadLauncher((JTextArea) commandLogger.getTextPanel());

    // create a timers
    debugMsgTimer = new Timer(1, new DebugInputListener()); // reads debug msgs from instrumented code
    graphTimer = new Timer(1000, new GraphUpdateListener());  // updates the call graph
    killTimer = new Timer(2000, new KillTimerListener());  // issues kill to instrumented code
}

  //=================== public methods ======================
  
  public static boolean isInstrumentedMethod(String methName) {
    return fullMethList.contains(methName);
  }
  
  public static void printStatusClear() {
    mainFrame.getTextField("TXT_MESSAGES").setText("");
  }
  
  public static void printStatusMessage(String message) {
    JTextField textField = mainFrame.getTextField("TXT_MESSAGES");
    textField.setForeground(Color.black);
    textField.setText(message);
      
    // echo status to command output window
    printCommandError(message);
  }
  
  public static void printStatusError(String message) {
    JTextField textField = mainFrame.getTextField("TXT_MESSAGES");
    textField.setForeground(Color.red);
    textField.setText("ERROR: " + message);
      
    // echo status to command output window
    printCommandError("ERROR: " + message);
  }
  
  public static void printCommandMessage(String message) {
    commandLogger.printLine(message);
  }
  
  public static void printCommandError(String message) {
    commandLogger.printLine(message);
  }
  
  public static void highlightBranch(int start, boolean branch) {
    ArrayList<Integer> branchMarks = bytecodeViewer.highlightBranch(start, branch);
    bytecodeGraph.drawGraphHighlights(branchMarks);
  }
  
  public static void addLocalVariable(String name, String type, String slot, String start, String end) {
    localVarTbl.addEntry(name, type, slot, start, end);
  }

  public static String addSymbVariable(String meth, String name, String type, String slot,
                                     String start, String end, int opstrt, int oplast) {
    String newEntry = symbolTbl.addEntry(meth, name, type, slot, start, end, opstrt, oplast);
    if (newEntry != null) {
      updateDanfigFile();
    }
    return newEntry;
  }

  public static void addSymbConstraint(String id, String type, String value) {
    symbolTbl.addConstraint(id, type, value);
  }
  
  public static void setSolutionsReceived(int formulas, int solutions) {
    mainFrame.getTextField("TXT_FORMULAS").setText("" + formulas);
    mainFrame.getTextField("TXT_SOLUTIONS").setText("" + solutions);
  }
  
  public static void checkSelectedSolution(String solution) {
    // if recording enabled, add command to list
    addRecordCommand(RecordID.CHECK, solution);
  }
  
  public static void updateDanfigFile() {
    if (projectPathName == null) {
      return;
    }
    
    // init the background stuff
    String content = initDanfigInfo();
    
    // now add in the latest and greatest symbolic defs
    boolean validConstr = false;
    if (symbolTbl.getSymbolicEntry(0) != null) {
      content += "#" + Utils.NEWLINE;
      content += "# SYMBOLICS" + Utils.NEWLINE;
      content += "# Symbolic: <symbol_id> <method> <slot>   --OR--" + Utils.NEWLINE;
      content += "# Symbolic: <symbol_id> <method> <slot> <start range> <end range> <type>" + Utils.NEWLINE;
      for (int ix = 0; true; ix++) {
        SymbolTable.TableListInfo entry = symbolTbl.getSymbolicEntry(ix);
        if (entry == null) {
          break;
        }
        if (!entry.constraints.isEmpty()) {
          validConstr = true;
        }
        content += "Symbolic: " +  entry.name + " " + entry.method + " " + entry.slot + " " +
            entry.opStart + " " + entry.opEnd + " " + entry.type + Utils.NEWLINE;
      }
    }

    // and now the user-defined constraints
    if (validConstr) {
      content += "#" + Utils.NEWLINE;
      content += "# CONSTRAINTS (NOTE: SYMBOLICS section must be defined prior to this section)" + Utils.NEWLINE;
      content += "# Constraint: <symbol_id>  <EQ|NE|GT|GE|LT|LE> <value>" + Utils.NEWLINE;
      for (int ix = 0; true; ix++) {
        SymbolTable.TableListInfo entry = symbolTbl.getSymbolicEntry(ix);
        if (entry == null) {
          break;
        }
        for (int conix = 0; conix < entry.constraints.size(); conix++) {
          SymbolTable.ConstraintInfo constraint = entry.constraints.get(conix);
          content += "Constraint: " +  entry.name + " " +
              constraint.comptype + " " + constraint.compvalue + Utils.NEWLINE;
        }
      }
    }

    // now save to the file
    Utils.saveTextFile(projectPathName + "danfig", content);
    printStatusMessage("Updated danfig file: " + symbolTbl.getSize() + " symbolic entries");
  }

  public static void setBytecodeSelections(String classSelect, String methodSelect) {
    String curClass = (String) classCombo.getSelectedItem();
    String curMeth = (String) methodCombo.getSelectedItem();

    if (!curClass.equals(classSelect) || !curMeth.equals(methodSelect)) {
      // push current entry onto history stack
      bytecodeHistory.add(new MethodHistoryInfo(curClass, curMeth));
      mainFrame.getButton("BTN_BACK").setVisible(true);
            
      // make new selections
      classCombo.setSelectedItem(classSelect);
      methodCombo.setSelectedItem(methodSelect);
    }
  }

  public static int runBytecodeViewer(String classSelect, String methodSelect) {
    printCommandMessage("Running Bytecode Viewer for class: " + classSelect + " method: " + methodSelect);
    
    // check if bytecode for this method already displayed
    if (bytecodeViewer.isMethodDisplayed(classSelect, methodSelect)) {
      // just clear any highlighting
      bytecodeViewer.highlightClear();
      printStatusMessage("Bytecode already loaded");
    } else {
      String content;
      String fname = projectPathName + JAVAPFILE_STORAGE + "/" + classSelect + ".txt";
      File file = new File(fname);
      if (file.isFile()) {
        // use the javap file already generated
        printCommandMessage("Using existing javap file: " + fname);
        content = Utils.readTextFile(fname);
      } else {
        // else, we need to generate bytecode source from jar using javap...
        // can't allow running javap while instrumented code is running - the stdout of the
        // run command will merge with the javap output and corrupt the bytecode source file.
        if (runMode == RunMode.RUNNING) {
          JOptionPane.showConfirmDialog(null,
                  "Cannot run javap to generate bytecode data" + Utils.NEWLINE
                      + "while running instrumented code.",
                  "Instrumented code running",
                  JOptionPane.DEFAULT_OPTION);
          return -1;
        }
      
        // need to run javap to generate the bytecode source
        content = generateJavapFile(classSelect);
        if (content == null) {
          return -1;
        }
        Utils.saveTextFile(fname, content);
      }

      // clear out the local variable list
      localVarTbl.clear(classSelect + "." + methodSelect);

      // if successful, load it in the Bytecode viewer
      runBytecodeParser(classSelect, methodSelect, content);
    }
      
    // swich tab to show bytecode
    String selected = getTabSelect();
    setTabSelect(PanelTabs.BYTECODE.toString());
      
    // this is an attempt to get the bytecode graph to update its display properly by
    // switching to different panel and back again
    String byteflowTab = PanelTabs.BYTEFLOW.toString();
    if (selected.equals(byteflowTab)) {
      setTabSelect(byteflowTab);
    }

    // make sure the sizing is correct on split screens
    mainFrame.setSplitDivider("SPLIT_MAIN", 0.6);
    return 0;
  }

  //=================== private panel methods ======================
  
  /**
   * The Main Panel
   */
  private void createMainPanel() {
    // if a panel already exists, close the old one
    if (mainFrame.isValidFrame()) {
      mainFrame.close();
    }

    GuiControls gui = mainFrame;

    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient NONE = GuiControls.Orient.NONE;
    
    // create the frame
    JFrame frame = gui.newFrame("danlauncher", 1200, 800, GuiControls.FrameSize.NOLIMIT); //.FULLSCREEN);
    frame.addWindowListener(new Window_MainListener());

    String panel = null; // this creates the entries in the main frame
    gui.makePanel      (panel, "PNL_MESSAGES" , LEFT, true , "Status");
    gui.makePanel      (panel, "PNL_CONTAINER", LEFT, true , "", GuiControls.Expand.HORIZONTAL);
    gui.makePanel      (panel, "PNL_BYTECODE" , LEFT, true , "Bytecode");
    gui.makeTabbedPanel(panel, "PNL_TABBED");

    panel = "PNL_MESSAGES";
    gui.makeTextField  (panel, "TXT_MESSAGES" , LEFT, true , "", 138, false);
    gui.makeLabel      (panel, "LBL_0"        , LEFT, false, "Elapsed");
    gui.makeTextField  (panel, "TXT_ELAPSED"  , LEFT, false, "00:00", 8, false);
    gui.makeLabel      (panel, "LBL_1"        , LEFT, false, "Classes");
    gui.makeTextField  (panel, "TXT_CLASSES"  , LEFT, false, "0", 6, false);
    gui.makeLabel      (panel, "LBL_2"        , LEFT, false, "Methods");
    gui.makeTextField  (panel, "TXT_METHODS"  , LEFT, false, "0", 6, false);
    gui.makeLabel      (panel, "LBL_3"        , LEFT, false, "Formulas");
    gui.makeTextField  (panel, "TXT_FORMULAS" , LEFT, false, "0", 6, false);
    gui.makeLabel      (panel, "LBL_4"        , LEFT, false, "Solutions");
    gui.makeTextField  (panel, "TXT_SOLUTIONS", LEFT, true , "0", 6, false);

    // the split panel is encapsulated in a JPanel to prevent limit the height
    panel = "PNL_CONTAINER";
    gui.makeSplitPanel (panel, "SPLIT_IFC"    , NONE, true, GuiControls.Expand.HORIZONTAL, true, 0.5);

    panel = "SPLIT_IFC";
    gui.makePanel      (panel, "PNL_CONTROLS" , LEFT, true , "Controls");
    gui.makePanel      (panel, "PNL_SYMBOLICS", LEFT, true , "Symbolic Parameters");
    
    panel = "PNL_SYMBOLICS";
    gui.makeScrollTable(panel, "TBL_SYMBOLICS");

    panel = "PNL_CONTROLS";
    gui.makeLabel     (panel, "LBL_MAINCLS"  , LEFT, false, "Main Class");
    gui.makeCombobox  (panel, "COMBO_MAINCLS", LEFT, true);

    gui.makeButton    (panel, "BTN_RUNTEST"  , LEFT, false, "RUN");
    gui.makeTextField (panel, "TXT_ARGLIST"  , LEFT, true , "", 45, true);
    gui.makeButton    (panel, "BTN_STOPTEST" , LEFT, true , "STOP");

    gui.makeLabel     (panel, "LBL_INMODE"   , LEFT, false, "Input mode");
    gui.makeCombobox  (panel, "COMBO_INMODE" , LEFT, false);
    gui.makeLabel     (panel, "LBL_PORT"     , LEFT, false, "Port");
    gui.makeTextField (panel, "TXT_PORT"     , LEFT, true , "8080", 6, true);
    gui.makeButton    (panel, "BTN_SEND"     , LEFT, false, "Send");
    gui.makeTextField (panel, "TXT_INPUT"    , LEFT, true , "", 45, true);

    panel = "PNL_BYTECODE";
    gui.makeLabel     (panel, "LBL_CLASS"    , LEFT, false, "Class");
    gui.makeCombobox  (panel, "COMBO_CLASS"  , LEFT, true);
    gui.makeLabel     (panel, "LBL_METHOD"   , LEFT, false, "Method");
    gui.makeCombobox  (panel, "COMBO_METHOD" , LEFT, true);
    gui.makeButton    (panel, "BTN_BYTECODE" , LEFT, false, "Get Bytecode");
    gui.makeButton    (panel, "BTN_BACK"     , LEFT, true , "Back");

    // set these buttons to the same width
    gui.makeGroup("GRP_CTL_BUTTONS");
    gui.addGroupComponent("GRP_CTL_BUTTONS", "BTN_RUNTEST");
    gui.addGroupComponent("GRP_CTL_BUTTONS", "BTN_STOPTEST");
    gui.addGroupComponent("GRP_CTL_BUTTONS", "BTN_SEND");
    gui.setGroupSameMinSize("GRP_CTL_BUTTONS", GuiControls.DimType.WIDTH);
    
    // set color of STOP button
    gui.getButton("BTN_STOPTEST").setBackground(Color.pink);

    // disable the back button initially
    gui.getButton("BTN_BACK").setVisible(false);

    // initially disable the bytecode control selections
    boolean bcPanelEnable = false;
    gui.getPanelInfo("PNL_BYTECODE").panel.setVisible(bcPanelEnable);
        
    // add a menu to the frame
    createMainMenu(bcPanelEnable);

    // init current tab selection to 1st entry
    currentTab = PanelTabs.COMMAND.toString();

    // create the panel classes
    commandLogger = new Logger(PanelTabs.COMMAND.toString(), Logger.PanelType.TEXTAREA, true, null);
    debugLogger = new DebugLogger(PanelTabs.LOG.toString());
    bytecodeViewer = new BytecodeViewer(PanelTabs.BYTECODE.toString());
    bytecodeGraph = new BytecodeGraph(PanelTabs.BYTEFLOW.toString(), bytecodeViewer);
    callGraph = new CallGraph(PanelTabs.CALLGRAPH.toString());
    importGraph = new ImportGraph(PanelTabs.COMPGRAPH.toString());
    dbtable = new DatabaseTable(PanelTabs.SOLUTIONS.toString());
    
    // create the help panels
    helpLogger = new HelpLogger("HELP", helpFrame);
    
    // wrap the bytecode logger in another pane to prevent line wrapping on a JTextPane
    JPanel noWrapBytecodePanel = new JPanel(new BorderLayout());
    noWrapBytecodePanel.add(bytecodeViewer.getScrollPanel());

    // create a scrollable table and encapsulate it in a panel to add a title
    JTable localParams = gui.makeRawTable("TBL_PARAMLIST");

    // pack contents of the frame
    gui.pack();

    // create a split panel for the BYTECODE panel and the table of local parameters
    String splitName = "SPLIT_MAIN";
    JSplitPane splitMain = gui.makeRawSplitPanel(splitName, true, 0.5);
    gui.addSplitComponent(splitName, 0, "BYTECODE"     , noWrapBytecodePanel, true);
    gui.addSplitComponent(splitName, 1, "TBL_PARAMLIST", localParams, true);
    
    // add the tabbed message panels and a listener to detect when a tab has been selected
    System.err.println("**starting tabbed panels");
    GuiControls.PanelInfo panelInfo = gui.getPanelInfo("PNL_TABBED");
    if (panelInfo != null && panelInfo.panel instanceof JTabbedPane) {
      JTabbedPane tabPanel = (JTabbedPane) panelInfo.panel;
      addPanelToTab(tabPanel, PanelTabs.COMMAND  , commandLogger.getScrollPanel());
      addPanelToTab(tabPanel, PanelTabs.SOLUTIONS, dbtable.getScrollPanel());
      addPanelToTab(tabPanel, PanelTabs.BYTECODE , splitMain);
      addPanelToTab(tabPanel, PanelTabs.BYTEFLOW , bytecodeGraph.getScrollPanel());
      addPanelToTab(tabPanel, PanelTabs.LOG      , debugLogger.getScrollPanel());
      addPanelToTab(tabPanel, PanelTabs.CALLGRAPH, callGraph.getScrollPanel());
      addPanelToTab(tabPanel, PanelTabs.COMPGRAPH, importGraph.getScrollPanel());
      tabPanel.addChangeListener(new Change_TabPanelSelect());
    }
    System.err.println("**completed tabbed panels");

    // activate the panels that have listeners and timers
    debugLogger.activate();
    bytecodeViewer.activate();
    bytecodeGraph.activate();
    callGraph.activate();
    importGraph.activate();
    dbtable.activate();
    helpLogger.activate();

    // init the local variable and symbolic list tables
    localVarTbl = new ParamTable(localParams);
    symbolTbl   = new SymbolTable(gui.getTable("TBL_SYMBOLICS"));

    // setup selections for INPUT MODE
    JComboBox inModeCombo = gui.getCombobox("COMBO_INMODE");
    inModeCombo.addItem("NONE");
    inModeCombo.addItem("STDIN");
    inModeCombo.addItem("HTTP_RAW");
    inModeCombo.addItem("HTTP_POST");
    inModeCombo.addItem("HTTP_GET");
    inModeCombo.setSelectedIndex(0);
    enableInputModeSelections();
    
    // setup the handlers for the controls
    inModeCombo.addActionListener(new Action_EnablePost());
    gui.getCombobox("COMBO_CLASS").addActionListener(new Action_BytecodeClassSelect());
    gui.getCombobox("COMBO_METHOD").addActionListener(new Action_BytecodeMethodSelect());
    gui.getCombobox("COMBO_MAINCLS").addActionListener(new Action_MainClassSelect());
    gui.getButton("BTN_BYTECODE").addActionListener(new Action_RunBytecode());
    gui.getButton("BTN_BACK").addActionListener(new Action_RunPrevBytecode());
    gui.getButton("BTN_RUNTEST").addActionListener(new Action_RunTest());
    gui.getButton("BTN_SEND").addActionListener(new Action_SendHttpMessage());
    gui.getButton("BTN_STOPTEST").addActionListener(new Action_StopExecution());
    gui.getTextField("TXT_ARGLIST").addActionListener(new Action_UpdateArglist());
    gui.getTextField("TXT_ARGLIST").addFocusListener(new Focus_UpdateArglist());
    gui.getTextField("TXT_PORT").addActionListener(new Action_UpdatePort());
    gui.getTextField("TXT_PORT").addFocusListener(new Focus_UpdatePort());

    // initially disable the class/method select and generating bytecode
    mainClassCombo = gui.getCombobox ("COMBO_MAINCLS");
    classCombo  = gui.getCombobox ("COMBO_CLASS");
    methodCombo = gui.getCombobox ("COMBO_METHOD");
    enableControlSelections(false);

    // initially disable STOP button
    gui.getButton("BTN_STOPTEST").setEnabled(false);

    // create the the setup frames, but initially hide them
    createSystemConfigPanel();
    createGraphSetupPanel();
    createDebugSelectPanel();
  
    // update divider locations in split frame now that it has been placed (and the dimensions are set)
    gui.setSplitDivider("SPLIT_MAIN", 0.5);

    // display the frame
    gui.display();
  }

  private class Window_MainListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      printCommandMessage("Closing danlauncher...");
      
      // close up services
      enableUpdateTimers(false);
      if (networkListener != null) {
        networkListener.exit();
      }
      dbtable.exit();

      // if instrumented code is running, abort it before closing
      if (runMode == RunMode.RUNNING) {
        ThreadLauncher.ThreadInfo threadInfo = threadLauncher.stopAll();
        if (threadInfo != null && threadInfo.pid >= 0 && runMode == RunMode.RUNNING) {
          printCommandMessage("Killing job " + threadInfo.jobid + ": pid " + threadInfo.pid);
          String[] command = { "kill", "-9", threadInfo.pid.toString() }; // SIGKILL
          CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
          commandLauncher.start(command, null);
          runMode = RunMode.EXITING;

          // check on progress and take further action if necessary
          killTimer.start();
        }
      } else {
        // else we can close the frame and exit
        mainFrame.close();
        System.exit(0);
      }
    }
  }

  private class Change_TabPanelSelect implements ChangeListener{
    @Override
    public void stateChanged(ChangeEvent e) {
      // get the tab selection
      GuiControls.PanelInfo panelInfo = mainFrame.getPanelInfo("PNL_TABBED");
      if (panelInfo == null || panelInfo.panel == null || tabSelect.isEmpty()) {
        return;
      }
      if (panelInfo.panel instanceof JTabbedPane) {
        JTabbedPane tabPanel = (JTabbedPane) panelInfo.panel;
        int curTab = tabPanel.getSelectedIndex();
        currentTab = tabPanel.getTitleAt(curTab);
      
        // now inform panels of the selection
        debugLogger.setTabSelection(currentTab);
        callGraph.setTabSelection(currentTab);
        importGraph.setTabSelection(currentTab);
        dbtable.setTabSelection(currentTab);

        // special actions
        if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
          callGraph.updateCallGraph(graphMode, false);
        } else if (currentTab.equals(PanelTabs.COMPGRAPH.toString())) {
          importGraph.updateCallGraph();
        }
      }
    }
  }
  
  private class Action_BytecodeClassSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JComboBox cbSelect = (JComboBox) evt.getSource();
      String classSelect = (String) cbSelect.getSelectedItem();
      setMethodSelections(classSelect);
//      mainFrame.getFrame().pack(); // need to update frame in case width requirements change
    }
  }

  private class Action_BytecodeMethodSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
    }
  }
  
  private class Action_MainClassSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      //if (evt.getActionCommand().equals("comboBoxChanged")) {
      if (!mainClassInitializing) {
        updateProjectProperty("COMBO_MAINCLS");
      }
    }
  }

  private class Action_EnablePost implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // enable the input mode controls based on the selection
      enableInputModeSelections();

      // update the project properties file
      updateProjectProperty("COMBO_INMODE");
    }
  }

  private class Action_SendHttpMessage implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String port = mainFrame.getTextField("TXT_PORT").getText();
      String input = mainFrame.getTextField("TXT_INPUT").getText();
      switch (inputMode) {
        case STDIN:
          threadLauncher.sendStdin(input);
          // if recording enabled, add command to list
          addRecordDelay();
          addRecordCommand(RecordID.SEND_STDIN, input);
          break;
        case HTTP_RAW:
          sendHttpMessage("HTTP", port, input);
          // if recording enabled, add command to list
          addRecordHttpDelay(port);
          addRecordCommand(RecordID.SEND_HTTP_RAW, input);
          break;
        case HTTP_GET:
          sendHttpMessage("GET", port, input);
          // if recording enabled, add command to list
          addRecordHttpDelay(port);
          addRecordCommand(RecordID.SEND_HTTP_GET, "");
          break;
        case HTTP_POST:
          sendHttpMessage("POST", port, input);
          // if recording enabled, add command to list
          addRecordHttpDelay(port);
          addRecordCommand(RecordID.SEND_HTTP_POST, input);
          break;
      }
    }
  }
      
  private class Action_RunBytecode implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String classSelect  = (String) classCombo.getSelectedItem();
      String methodSelect = (String) methodCombo.getSelectedItem();
      runBytecodeViewer(classSelect, methodSelect);
    }
  }

  private class Action_RunPrevBytecode implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      if (!bytecodeHistory.isEmpty()) {
        MethodHistoryInfo entry = bytecodeHistory.remove(bytecodeHistory.size() - 1);
        runBytecodeViewer(entry.className, entry.methodName);
        
        // update the selections
        classCombo.setSelectedItem(entry.className);
        methodCombo.setSelectedItem(entry.methodName);

        // disable back button if no more
        if (bytecodeHistory.isEmpty()) {
          mainFrame.getButton("BTN_BACK").setVisible(false);
        }
      }
    }
  }

  private class Action_RunTest implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String arglist = mainFrame.getTextField("TXT_ARGLIST").getText();
      runTest(arglist);

      // if prev recording currently running, terminate it first, so user can start a new one
      if (recordState && !recording.isEmpty()) {
        printCommandMessage("--- RECORD session terminated ---");
        recording.clear();
      }
      
      addRecordCommand(RecordID.RUN, arglist);
    }
  }

  private class Action_StopExecution implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // stop the running process
      ThreadLauncher.ThreadInfo threadInfo = threadLauncher.stopAll();
      if (threadInfo != null && threadInfo.pid >= 0 && runMode == RunMode.RUNNING) {
        printCommandMessage("Terminating job " + threadInfo.jobid + ": pid " + threadInfo.pid);
        String[] command = { "kill", "-15", threadInfo.pid.toString() }; // SIGTERM
        CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
        commandLauncher.start(command, null);
        runMode = RunMode.TERMINATING;

        // check on progress and take further action if necessary
        killTimer.start();

        // if recording enabled, add command to list
        addRecordCommand(RecordID.STOP, "");
      }
    }
  }
  
  private class Action_UpdateArglist implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      updateProjectProperty("TXT_ARGLIST");
    }
  }

  private class Focus_UpdateArglist implements FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
    }
    @Override
    public void focusLost(FocusEvent e) {
      updateProjectProperty("TXT_ARGLIST");
    }
  }
    
  private class Action_UpdatePort implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      updateProjectProperty("TXT_PORT");
    }
  }

  private class Focus_UpdatePort implements FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
    }
    @Override
    public void focusLost(FocusEvent e) {
      updateProjectProperty("TXT_PORT");
    }
  }

  /**
   * The Menu Controls
   * @param bEnable 
   */
  private void createMainMenu(boolean bEnable) {
    launcherMenuBar = new JMenuBar();
    mainFrame.getFrame().setJMenuBar(launcherMenuBar);
    JMenu menuProject = launcherMenuBar.add(new JMenu("Project"));
    JMenu menuConfig  = launcherMenuBar.add(new JMenu("Config"));
    JMenu menuClear   = launcherMenuBar.add(new JMenu("Clear"));
    JMenu menuSave    = launcherMenuBar.add(new JMenu("Save"));
    JMenu menuRecord  = launcherMenuBar.add(new JMenu("Record"));
    JMenu menuHelp    = launcherMenuBar.add(new JMenu("Help"));

    JMenu menu = menuProject; // selections for the Project Menu
    addMenuItem     (menu, "MENU_SEL_JAR"    , "Load Jar file", new Action_SelectJarFile());
    addMenuItem     (menu, "MENU_SEL_JSON"   , "Load Janalyzer Graph", new Action_SelectImportGraphFile());
    menu.addSeparator();
    addMenuCheckbox (menu, "MENU_SHOW_UPPER" , "Show Upper Panel", true, 
                      new ItemListener_ShowUpperPanel());
    addMenuCheckbox (menu, "MENU_SHOW_BCODE" , "Show Bytecode Panel", bEnable, 
                      new ItemListener_ShowBytecodePanel());

    menu = menuConfig; // selections for the Config Menu
    addMenuItem     (menu, "MENU_SETUP_SYS"  , "System Configuration", new Action_SystemSetup());
    addMenuItem     (menu, "MENU_SETUP_DBUG" , "Debug Setup", new Action_DebugSetup());
    addMenuItem     (menu, "MENU_SETUP_GRAF" , "Callgraph Setup", new Action_CallgraphSetup());

    menu = menuClear; // selections for the Clear Menu
    addMenuItem     (menu, "MENU_CLR_DBASE"  , "Clear SOLUTIONS", new Action_ClearDatabase());
    addMenuItem     (menu, "MENU_CLR_UNSOLVE", "Clear SOLUTIONS (old runs)", new Action_ClearDatabaseOldRuns());
    addMenuItem     (menu, "MENU_CLR_LOG"    , "Clear LOG / GRAPH", new Action_ClearLog());

    menu = menuSave; // selections for the Save Menu
    addMenuItem     (menu, "MENU_SAVE_DANFIG", "Update danfig file", new Action_UpdateDanfigFile());
    menu.addSeparator();
    addMenuItem     (menu, "MENU_SAVE_DBASE" , "Save Solutions Table", new Action_SaveDatabaseTable());
    addMenuItem     (menu, "MENU_SAVE_BCODE" , "Save Byteflow Graph", new Action_SaveByteFlowGraph());
    addMenuItem     (menu, "MENU_SAVE_CGRAPH", "Save Call Graph", new Action_SaveCallGraphPNG());
    addMenuItem     (menu, "MENU_SAVE_JSON"  , "Save Call Graph as JSON", new Action_SaveCallGraphJSON());
    addMenuItem     (menu, "MENU_SAVE_JGRAPH", "Save Compare Graph", new Action_SaveCompGraphPNG());

    menu = menuRecord; // selections for the Record Menu
    addMenuItem     (menu, "MENU_RECORD_START", "Start Recording", new Action_RecordStart());
    addMenuItem     (menu, "MENU_RECORD_STOP" , "Stop Recording", new Action_RecordStop());
    
//    menuHelp.addActionListener(new Action_ShowHelp());
    menu = menuHelp; // selections for the Help Menu
    addMenuItem     (menu, "MENU_HELP", "Help", new Action_ShowHelp());
    
    // setup access to menu controls
    getMenuItem("MENU_SETUP_DBUG").setEnabled(false);
    getMenuItem("MENU_SAVE_CGRAPH").setEnabled(false);
    getMenuItem("MENU_SAVE_JSON").setEnabled(false);
    getMenuItem("MENU_SAVE_JGRAPH").setEnabled(false);
    getMenuItem("MENU_SAVE_BCODE").setEnabled(false);
  }
  
  private class ItemListener_ShowUpperPanel implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent ie) {
      JCheckBoxMenuItem item = (JCheckBoxMenuItem) ie.getItem();
      boolean show = item.isSelected();
      GuiControls.PanelInfo panelInfo = mainFrame.getPanelInfo("PNL_CONTAINER");
      if (panelInfo != null) {
        ((JPanel)panelInfo.panel).setVisible(show);
//        mainFrame.getFrame().pack(); // need to update frame in case width requirements change
      }
    }
  }
  
  private class ItemListener_ShowBytecodePanel implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent ie) {
      JCheckBoxMenuItem item = (JCheckBoxMenuItem) ie.getItem();
      boolean show = item.isSelected();
      GuiControls.PanelInfo panelInfo = mainFrame.getPanelInfo("PNL_BYTECODE");
      if (panelInfo != null) {
        ((JPanel)panelInfo.panel).setVisible(show);
      }
    }
  }
  
  private class Action_SelectJarFile implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // load the selected file
      int rc = loadJarFile();
      if (rc == 0) {
        mainFrame.getFrame().setTitle(projectPathName + projectName);
      }

      // switch to the command tab
      setTabSelect(PanelTabs.COMMAND.toString());
      
      // enable the class and method selections
      enableControlSelections(true);

      // enable the debug setup selection
      getMenuItem("MENU_SETUP_DBUG").setEnabled(true);
    }
  }

  private class Action_SelectImportGraphFile implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      File file = loadJSONFile();
      if (file != null) {
        int count = importGraph.loadFromJSONFile(file);
        if (count > 0) {
          // switch tab to COMPGRAPH
          setTabSelect(PanelTabs.COMPGRAPH.toString());
          importGraph.updateCallGraph();
          
          // allow save
          getMenuItem("MENU_SAVE_JGRAPH").setEnabled(true);
        }
      }
    }
  }
  
  private class Action_UpdateDanfigFile implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      updateDanfigFile();
    }
  }
  
  private class Action_ClearDatabase implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // if solver didn't connect initially, try again here
      if (!solverConnection.isValid()) {
        printStatusError("Attempting to Reconnecting to port " + solverPort);
        solverConnection = new SolverInterface(solverPort);
        if (!solverConnection.isValid()) {
          printStatusError("Still not connected to Solver. Make sure dansolver is running and verify port.");
          return;
        } else {
          printStatusMessage("Connected to Solver on port " + solverPort);
        }
      }

      solverConnection.sendClearAll();
    }
  }
  
  private class Action_ClearDatabaseOldRuns implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // if solver didn't connect initially, try again here
      if (!solverConnection.isValid()) {
        printStatusError("Attempting to Reconnecting to port " + solverPort);
        solverConnection = new SolverInterface(solverPort);
        if (!solverConnection.isValid()) {
          printStatusError("Still not connected to Solver. Make sure dansolver is running and verify port.");
          return;
        } else {
          printStatusMessage("Connected to Solver on port " + solverPort);
        }
      }

      int select = dbtable.getCurrentConnection();
      solverConnection.sendClearExcept(select);
    }
  }
  
  private class Action_ClearLog implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      clearDebugLogger();
    }
  }

  private class Action_CallgraphSetup implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      graphSetupFrame.display();
    }
  }

  private class Action_DebugSetup implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      debugSetupFrame.display();
    }
  }
  
  private class Action_SystemSetup implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      systemSetupFrame.display();
    }
  }

  private class Action_SaveCallGraphPNG implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveCallGraph("callgraph", "png");
    }
  }
      
  private class Action_SaveCallGraphJSON implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveCallGraph("callgraph", "json");
    }
  }

  private class Action_SaveCompGraphPNG implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveCallGraph("janagraph", "png");
    }
  }
      
  private class Action_SaveByteFlowGraph implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveByteFlowGraph();
    }
  }
      
  private class Action_SaveDatabaseTable implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      saveDatabaseTable();
    }
  }

  private class Action_RecordStart implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      printCommandMessage("--- RECORD session started ---");
      
      // clear last recording
      recording.clear();
      recordState = true;

      // clear the debug output and solver
      clearDebugLogger();
      if (solverConnection.isValid()) {
        solverConnection.sendClearAll();
      }
    
      // if application already running, auto-insert the RUN command
      if (runMode != RunMode.IDLE) {
        String arglist = mainFrame.getTextField("TXT_ARGLIST").getText();
        addRecordCommand(RecordID.RUN, arglist);
      }
    }
  }
    
  private class Action_RecordStop implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      if (recordState) {
        printCommandMessage("--- RECORD session terminated ---");
        recordState = false;
      }
      
      if (recording.isEmpty()) {
        printStatusError("Nothing was recorded");
        return;
      }
      
      // create a panel containing the script to run for testing
      String baseName = projectName.substring(0, projectName.indexOf(".jar"));
      String content = "";
      String httpport = "";
      content += "TESTNAME=\"" + baseName + "\"" + Utils.NEWLINE;
      content += Utils.NEWLINE;
      content += "PID=$1" + Utils.NEWLINE;
      content += "echo \"pid = ${PID}\"" + Utils.NEWLINE;
      content += Utils.NEWLINE;
      for (RecordCommand entry : recording) {
        switch(entry.command) {
          case RUN:
            // TODO: this is used for passing an arg to the run script. need to figger this out...
            break;
          case STOP:
            content += "# terminate process (this one doesn't auto-terminate)" + Utils.NEWLINE;
            content += "kill -15 ${PID} > /dev/null 2>&1" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case DELAY:
            content += "# wait a short period" + Utils.NEWLINE;
            content += "sleep " + entry.argument + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case WAIT_FOR_TERM:
            content += "# wait for application to complete" + Utils.NEWLINE;
            content += "wait_for_app_completion 5 ${PID}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case WAIT_FOR_SERVER:
            content += "# wait for server to start then post message to it" + Utils.NEWLINE;
            content += "wait_for_server 10 ${PID} ${SERVERPORT}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SET_HTTP:
            httpport = entry.argument;
            content += "# http port and message to send" + Utils.NEWLINE;
            content += "SERVERPORT=\"" + httpport + "\"" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SEND_HTTP_RAW:
          case SEND_HTTP_POST:
            content += "# send the HTTP POST" + Utils.NEWLINE;
            content += "echo \"Sending message to application\"" + Utils.NEWLINE;
            content += "curl -d \"" + entry.argument + "\" http://localhost:${SERVERPORT}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SEND_HTTP_GET:
            content += "# send the HTTP GET" + Utils.NEWLINE;
            content += "echo \"Sending message to application\"" + Utils.NEWLINE;
            content += "curl http://localhost:${SERVERPORT}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SEND_STDIN:
            content += "# send the message to STDIN" + Utils.NEWLINE;
            content += "echo \"Sending message to application\"" + Utils.NEWLINE;
            content += "echo \"" + entry.argument + "\" > /proc/${PID}/fd/0" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case CHECK:
            String solution = entry.argument;
            String[] sol = solution.split(" "); // NOTE: this doesn't handle multiple entries yet
            content += "# get solver response and check against expected solution" + Utils.NEWLINE;
            content += "echo \"Debug info: ${TESTNAME} database entry\"" + Utils.NEWLINE;
            content += "check_single_solution \"" + sol[0] + "\" \"" + sol[2] + "\"" + Utils.NEWLINE;
            content += "retcode=$?" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            content += "show_results ${TESTNAME} ${retcode}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
        }
      }

      JFrame testPanel = GuiControls.makeFrameWithText(null, "check_results.sh", content, 400, 300);
    }
  }
    
  private class Action_ShowHelp implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      helpLogger.printHelpPanel("INTRO");
    }
  }

  /**
   * The Graph Setup Pane
   */
  private void createGraphSetupPanel() {
    GuiControls gui = graphSetupFrame;
    if (gui.isValidFrame()) {
      return;
    }

    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    
    // create the frame
    JFrame frame = gui.newFrame("Graph Setup", 400, 390, GuiControls.FrameSize.FIXEDSIZE);
    frame.addWindowListener(new Window_GraphSetupListener());
  
    String panel = null;
    gui.makePanel  (panel, "PNL_CALLGRF"  , LEFT, true , "CALLGRAPH Controls", 390, 250);
    gui.makePanel  (panel, "PNL_JSONGRF"  , LEFT, true , "COMPGRAPH Controls", 390, 100);

    panel = "PNL_CALLGRF";
    gui.makePanel  (panel, "PNL_HIGHLIGHT", LEFT, false, "Highlight Mode");
    gui.makePanel  (panel, "PNL_ADJUST"   , LEFT, true , "");
    
    panel = "PNL_ADJUST";
    gui.makeLabel  (panel, "LBL_THREADS"  , CENTER, true, "Threads: 0");
    gui.makeLineGap(panel, 20);
    gui.makePanel  (panel, "PNL_THREAD"   , LEFT, true , "Thread Select");
    gui.makeLineGap(panel, 58);
    gui.makePanel  (panel, "PNL_RANGE"    , LEFT, true , "Highlight Range");
    
    panel = "PNL_HIGHLIGHT";
    gui.makeRadiobutton(panel, "RB_THREAD"  , LEFT, true, "Thread"      , 0);
    gui.makeRadiobutton(panel, "RB_ELAPSED" , LEFT, true, "Elapsed Time", 0);
    gui.makeRadiobutton(panel, "RB_INSTRUCT", LEFT, true, "Instructions", 0);
    gui.makeRadiobutton(panel, "RB_ITER"    , LEFT, true, "Iterations"  , 0);
    gui.makeRadiobutton(panel, "RB_STATUS"  , LEFT, true, "Status"      , 0);
    gui.makeRadiobutton(panel, "RB_NONE"    , LEFT, true, "Off"         , 1);

    panel = "PNL_THREAD";
    gui.makeLabel      (panel, "TXT_TH_SEL" , LEFT, false, "0");
    gui.makeButton     (panel, "BTN_TH_UP"  , LEFT, false, "UP");
    gui.makeButton     (panel, "BTN_TH_DN"  , LEFT, true , "DN");
    
    panel = "PNL_RANGE";
    gui.makeLabel      (panel, "TXT_RANGE"  , LEFT, false, "20");
    gui.makeButton     (panel, "BTN_RG_UP"  , LEFT, false, "UP");
    gui.makeButton     (panel, "BTN_RG_DN"  , LEFT, true , "DN");

    panel = "PNL_JSONGRF";
    gui.makeLabel      (panel, "LBL_ZOOM"   , LEFT, false, "Zoom Factor");
    gui.makeSpinner    (panel, "SPIN_ZOOM"  , LEFT, true , 20, 200, 5, 100);

    // init thread selection in highlighting to OFF
    gui.getRadiobutton("RB_THREAD").setEnabled(false);
    setThreadControls(false);
    
    // setup the control actions
    gui.getRadiobutton("RB_THREAD"  ).addActionListener(new Action_GraphModeThread());
    gui.getRadiobutton("RB_ELAPSED" ).addActionListener(new Action_GraphModeElapsed());
    gui.getRadiobutton("RB_INSTRUCT").addActionListener(new Action_GraphModeInstruction());
    gui.getRadiobutton("RB_ITER"    ).addActionListener(new Action_GraphModeIteration());
    gui.getRadiobutton("RB_STATUS"  ).addActionListener(new Action_GraphModeStatus());
    gui.getRadiobutton("RB_NONE"    ).addActionListener(new Action_GraphModeNone());
    gui.getButton("BTN_TH_UP").addActionListener(new Action_ThreadUp());
    gui.getButton("BTN_TH_DN").addActionListener(new Action_ThreadDown());
    gui.getButton("BTN_RG_UP").addActionListener(new Action_RangeUp());
    gui.getButton("BTN_RG_DN").addActionListener(new Action_RangeDown());
    gui.getSpinner("SPIN_ZOOM").addChangeListener(new Action_ZoomRangeSelected());
}

  private class Window_GraphSetupListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      graphSetupFrame.hide();
    }
  }

  private class Action_GraphModeThread implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(CallGraph.GraphHighlight.THREAD);
        
      // enable the thread controls when this is selected
      setThreadControls(true);
    }
  }

  private class Action_GraphModeElapsed implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(CallGraph.GraphHighlight.TIME);
    }
  }

  private class Action_GraphModeInstruction implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(CallGraph.GraphHighlight.INSTRUCTION);
    }
  }

  private class Action_GraphModeIteration implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(CallGraph.GraphHighlight.ITERATION);
    }
  }

  private class Action_GraphModeStatus implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(CallGraph.GraphHighlight.STATUS);
    }
  }

  private class Action_GraphModeNone implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setHighlightMode(CallGraph.GraphHighlight.NONE);
    }
  }

  private class Action_ThreadUp implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = graphSetupFrame.getLabel("TXT_TH_SEL");
      int threadCount = debugLogger.getThreadCount();
      int value = Integer.parseInt(label.getText().trim());
      if (value < threadCount - 1) {
        value++;
        label.setText("" + value);
          
        callGraph.setThreadSelection(value);

        // if CallGraph is selected, update the graph
        if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
          callGraph.updateCallGraph(CallGraph.GraphHighlight.THREAD, true);
        }
      }
    }
  }

  private class Action_ThreadDown implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = graphSetupFrame.getLabel("TXT_TH_SEL");
      int value = Integer.parseInt(label.getText().trim());
      if (value > 0) {
        value--;
        label.setText("" + value);
          
        callGraph.setThreadSelection(value);

        // if CallGraph is selected, update the graph
        if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
          callGraph.updateCallGraph(CallGraph.GraphHighlight.THREAD, true);
        }
      }
    }
  }

  private class Action_RangeUp implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = graphSetupFrame.getLabel("TXT_RANGE");
      int step = Integer.parseInt(label.getText().trim());
      if (step < 20) {
        step++;
        label.setText("" + step);
          
        callGraph.setRangeStepSize(step);

        // if CallGraph is selected, update the graph
        if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
          callGraph.updateCallGraph(graphMode, true);
        }
      }
    }
  }

  private class Action_RangeDown implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JLabel label = graphSetupFrame.getLabel("TXT_RANGE");
      int step = Integer.parseInt(label.getText().trim());
      if (step > 1) {
        step--;
        label.setText("" + step);
          
        callGraph.setRangeStepSize(step);

        // if CallGraph is selected, update the graph
        if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
          callGraph.updateCallGraph(graphMode, true);
        }
      }
    }
  }

  private class Action_ZoomRangeSelected implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent ce) {
      JSpinner mySpinner = (JSpinner)(ce.getSource());
      Integer value = (Integer) mySpinner.getModel().getValue();
      if (value != null) {
        Double scaleFactor = value * 0.01;
        importGraph.setZoomFactor(scaleFactor);
        if (currentTab.equals(PanelTabs.COMPGRAPH.toString())) {
          importGraph.updateCallGraph();
        }
      }
    }
  }

  /**
   * The Debug Configuration panel
   */
  private void createDebugSelectPanel() {
    GuiControls gui = debugSetupFrame;
    if (gui.isValidFrame()) {
      return;
    }

    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT = GuiControls.Orient.RIGHT;
    
    // create the frame
    JFrame frame = gui.newFrame("Debug Setup", 700, 400, GuiControls.FrameSize.NOLIMIT);
  
    // setup debug parameters from project properties file & specify panel used
    debugParams = new DebugParams(gui);
    
    // create the entries in the main frame
    String panel = null;
    gui.makePanel (panel, "PNL_DBGFLAGS", LEFT  , false, "Debug Flags");
    gui.makePanel (panel, "PNL_TRIGGER" , LEFT  , true , "Trigger");
    gui.makePanel (panel, "PNL_TRIGMETH", LEFT  , true , "Call Trigger Method Selection", GuiControls.Expand.HORIZONTAL);
    
    // now add controls to the sub-panels
    panel = "PNL_DBGFLAGS";
    gui.makeCheckbox(panel, "DBG_WARNING" , LEFT, false, "Warnings"   , 0);
    gui.makeCheckbox(panel, "DBG_CALL"    , LEFT, true , "Call/Return", 0);
    gui.makeCheckbox(panel, "DBG_INFO"    , LEFT, false, "Info"       , 0);
    gui.makeCheckbox(panel, "DBG_STACK"   , LEFT, true , "Stack"      , 0);
    gui.makeCheckbox(panel, "DBG_COMMAND" , LEFT, false, "Commands"   , 0);
    gui.makeCheckbox(panel, "DBG_LOCALS"  , LEFT, true , "Locals"     , 0);
    gui.makeCheckbox(panel, "DBG_AGENT"   , LEFT, false, "Agent"      , 0);
    gui.makeCheckbox(panel, "DBG_OBJECTS" , LEFT, true , "Objects"    , 0);
    gui.makeCheckbox(panel, "DBG_BRANCH"  , LEFT, false, "Branch"     , 0);
    gui.makeCheckbox(panel, "DBG_ARRAYS"  , LEFT, true , "Arrays"     , 0);
    gui.makeCheckbox(panel, "DBG_CONSTR"  , LEFT, false, "Constraints", 0);
    gui.makeCheckbox(panel, "DBG_SOLVER"  , LEFT, true , "Solver"     , 0);

    panel = "PNL_TRIGGER";
    gui.makeCheckbox(panel, "TRIG_ON_ERROR" , LEFT , true , "on Error", 0);
    gui.makeCheckbox(panel, "TRIG_ON_EXCEPT", LEFT , true , "on Exception", 0);
    gui.makeCheckbox(panel, "TRIG_ON_CALL"  , LEFT , false, "on Call", 0);
    gui.makeSpinner (panel, "SPIN_COUNT"    , RIGHT, false, 1, 10000, 1, 1);
    gui.makeLabel   (panel, "LBL_COUNT"     , LEFT , true , "Count");
    gui.makeCheckbox(panel, "TRIG_ON_RETURN", LEFT , true , "on Return", 0);
    gui.makeCheckbox(panel, "TRIG_ON_REF_W" , LEFT , false, "on Ref (write)", 0);
    gui.makeSpinner (panel, "SPIN_REFCOUNT" , RIGHT, false, 1, 10000, 1, 1);
    gui.makeLabel   (panel, "LBL_REFCOUNT"  , LEFT , false, "Count");
    gui.makeSpinner (panel, "SPIN_REF"      , RIGHT, false, 0, 9999, 1, 0);
    gui.makeLabel   (panel, "LBL_REF"       , LEFT , true , "Index");
    gui.makeCheckbox(panel, "AUTO_RESET"    , LEFT , false, "Auto Reset", 0);
    gui.makeSpinner (panel, "SPIN_RANGE"    , RIGHT, false, 0, 100000, 100, 0);
    gui.makeLabel   (panel, "LBL_LIMIT"     , LEFT , true , "Limit");

    panel = "PNL_TRIGMETH";
    gui.makeLabel   (panel, "LBL_CLASS"     , LEFT, false, "Class");
    gui.makeCombobox(panel, "COMBO_CLASS"   , LEFT, true);
    gui.makeLabel   (panel, "LBL_METHOD"    , LEFT, false, "Method");
    gui.makeCombobox(panel, "COMBO_METHOD"  , LEFT, true);
    gui.makeCheckbox(panel, "ANY_METHOD"    , LEFT, true , "Any Method", 0);

    // resize slot spinner
    JSpinner spinner = gui.getSpinner("SPIN_REF");
    Dimension dim = spinner.getPreferredSize();
    spinner.setPreferredSize(new Dimension(50, dim.height));
    spinner.setMinimumSize(new Dimension(50, dim.height));
    
    // add listeners
    gui.getCombobox("COMBO_CLASS").addActionListener(new Action_DebugClassSelect());
    gui.getCheckbox("TRIG_ON_CALL").addActionListener(new Action_DebugMethodEnable());
    gui.getCheckbox("TRIG_ON_RETURN").addActionListener(new Action_DebugMethodEnable());
    gui.getCheckbox("TRIG_ON_REF_W").addActionListener(new Action_DebugMethodEnable());
    gui.getCheckbox("TRIG_ON_ERROR").addActionListener(new Action_DebugMethodEnable());
    gui.getCheckbox("TRIG_ON_EXCEPT").addActionListener(new Action_DebugMethodEnable());
    gui.getCheckbox("ANY_METHOD").addActionListener(new Action_DebugMethodEnable());
    gui.getCheckbox("AUTO_RESET").addActionListener(new Action_DebugMethodEnable());
    frame.addWindowListener(new Window_DebugSetupListener());
  }
  
  private class Window_DebugSetupListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      // save current settings and check if any changes
      if (debugParams.updateFromPanel()) {
        updateDanfigFile();
      } else {
        printCommandMessage("No changes to debug settings");
      }

      // now put this frame back into hiding
      debugSetupFrame.hide();
    }
  }

  private class Action_DebugClassSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JComboBox cbSelect = (JComboBox) evt.getSource();
      String classSelect = (String) cbSelect.getSelectedItem();
      ArrayList<String> methodList = clsMethMap.get(classSelect);
      if (methodList != null) {
        debugSetupFrame.setComboboxSelections("COMBO_METHOD", methodList);
      }
    }
  }

  private class Action_DebugMethodEnable implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      setDebugControlEnable();
    }
  }

  private static void setDebugControlEnable() {
    boolean bTrigCall   = debugSetupFrame.getCheckbox("TRIG_ON_CALL").isSelected();
    boolean bTrigRet    = debugSetupFrame.getCheckbox("TRIG_ON_RETURN").isSelected();
    boolean bTrigError  = debugSetupFrame.getCheckbox("TRIG_ON_ERROR").isSelected();
    boolean bTrigExcpt  = debugSetupFrame.getCheckbox("TRIG_ON_EXCEPT").isSelected();
    boolean bTrigLocalW = debugSetupFrame.getCheckbox("TRIG_ON_REF_W").isSelected();
    boolean bAnyMeth    = debugSetupFrame.getCheckbox("ANY_METHOD").isSelected();
    boolean bAutoReset  = debugSetupFrame.getCheckbox("AUTO_RESET").isSelected();

    // these are only enabled if any of the triggering methods is enabled
    boolean bEnable = bTrigCall | bTrigRet | bTrigLocalW | bTrigError | bTrigExcpt;
    debugSetupFrame.getCheckbox("AUTO_RESET").setEnabled(bEnable);
    // only allow changing range if Auto is also enabled
    bEnable = bAutoReset ? bEnable : false;
    debugSetupFrame.getSpinner ("SPIN_RANGE").setEnabled(bEnable);
    debugSetupFrame.getLabel   ("LBL_LIMIT").setEnabled(bEnable);
    if (!bEnable) {
      debugSetupFrame.getSpinner ("SPIN_RANGE").setValue(0); // also, make sure indicate range is 0
    }
    
    // these are enabled if either Reference trigger is enabled
    bEnable = bTrigLocalW;
    debugSetupFrame.getSpinner ("SPIN_REFCOUNT").setEnabled(bEnable);
    debugSetupFrame.getLabel   ("LBL_REFCOUNT").setEnabled(bEnable);
    debugSetupFrame.getSpinner ("SPIN_REF").setEnabled(bEnable);
    debugSetupFrame.getLabel   ("LBL_REF").setEnabled(bEnable);

    // these are enabled if Call or Return trigger is enabled
    bEnable = bTrigCall | bTrigRet;
    debugSetupFrame.getSpinner ("SPIN_COUNT").setEnabled(bEnable);
    debugSetupFrame.getLabel   ("LBL_COUNT").setEnabled(bEnable);
    debugSetupFrame.getCombobox("COMBO_CLASS").setEnabled(bEnable);
    debugSetupFrame.getLabel   ("LBL_CLASS").setEnabled(bEnable);
    debugSetupFrame.getCheckbox("ANY_METHOD").setEnabled(bEnable);
    // these are enabled if Call or Return trigger is enabled AND AnyMethod is disabled
    bEnable = bAnyMeth ? false : bEnable;
    debugSetupFrame.getCombobox("COMBO_METHOD").setEnabled(bEnable);
    debugSetupFrame.getLabel   ("LBL_METHOD").setEnabled(bEnable);
  }

  /**
   * The System Configuration Panel
   */  
  private void createSystemConfigPanel() {
    GuiControls gui = systemSetupFrame;
    if (gui.isValidFrame()) {
      return;
    }

    javaHomeSelector = new JFileChooser();
    javaHomeSelector.setCurrentDirectory(new File("/usr/lib/jvm"));
    
    // create the frame
    JFrame frame = gui.newFrame("System Configuration", 500, 400, GuiControls.FrameSize.FIXEDSIZE);
    frame.addWindowListener(new Window_SystemSetupListener());

    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    
    // create the entries in the main frame
    String panel = null;
    gui.makePanel (panel, "PNL_MAIN" , LEFT  , true, "");

    // now add controls to the sub-panels
    panel = "PNL_MAIN";
    gui.makeButton   (panel, "BTN_JAVAHOME"  , LEFT, false, "JAVA_HOME");
    gui.makeLabel    (panel, "LBL_JAVAHOME"  , LEFT, true , javaHome);
    gui.makeButton   (panel, "BTN_DSEPATH"   , LEFT, false, "DSEPATH");
    gui.makeLabel    (panel, "LBL_DSEPATH"   , LEFT, true , dsePath);
    gui.makeCheckbox (panel, "CBOX_CLR_FILES", LEFT, true , "CLR_OLD_FILES - Clear class & javap files on jar load", clearClassFilesOnStartup ? 1 : 0);
    gui.makeLabel    (panel, "LBL_1"         , LEFT, false, "LOG_LENGTH");
    gui.makeTextField(panel, "TXT_MAXLEN"    , LEFT, false, maxLogLength, 8, true);
    gui.makeLabel    (panel, "LBL_2"         , LEFT, true , "(maximum displayed log size)");
    gui.makeLabel    (panel, "LBL_3"         , LEFT, false, "SOLVER_PORT");
    gui.makeTextField(panel, "TXT_SOLVEPORT" , LEFT, false, solverPort + "", 8, true);
    gui.makeLabel    (panel, "LBL_4"         , LEFT, true , "(dansolver listener port)");
    gui.makeLabel    (panel, "LBL_5"         , LEFT, false, "DEBUG_PORT");
    gui.makeTextField(panel, "TXT_MYPORT"    , LEFT, false, debugPort + "", 8, true);
    gui.makeLabel    (panel, "LBL_6"         , LEFT, true , "(port for danalyzer to send debug to)");

    gui.makeLineGap  (panel, 10);
    gui.makeCheckbox(panel, "CBOX_LOAD_DANFIG", LEFT, true, "Load symbolics from current danfig file",
            loadDanfigOnStartup ? 1 : 0);
    gui.makeCheckbox(panel, "CBOX_CLR_PRJLOAD", LEFT, true, "Clear SOLUTIONS on project Load", 
            clearSolutionsOnStartup ? 1 : 0);
    gui.makeCheckbox(panel, "CBOX_CLR_PRJRUN" , LEFT, true, "Clear SOLUTIONS on project Run", 
            clearSolutionsOnRun ? 1 : 0);
    gui.makeCheckbox(panel, "CBOX_NO_SOLVER"  , LEFT, true, "Allow running without solver", 
            allowRunWithoutSolver ? 1 : 0);

    // setup actions for controls
    gui.getButton("BTN_JAVAHOME").addActionListener(new Action_SetJavaHome());
    gui.getButton("BTN_DSEPATH").addActionListener(new Action_SetDsePath());
    gui.getCheckbox("CBOX_CLR_FILES").addActionListener(new Action_ClearClassFilesOnLoad());
  }

  private class Window_SystemSetupListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      // handle the text box entries here, since user may not have pressed enter
      loadDanfigOnStartup     = systemSetupFrame.getCheckbox("CBOX_LOAD_DANFIG").isSelected();
      clearSolutionsOnStartup = systemSetupFrame.getCheckbox("CBOX_CLR_PRJLOAD").isSelected();
      clearSolutionsOnRun     = systemSetupFrame.getCheckbox("CBOX_CLR_PRJRUN").isSelected();
      allowRunWithoutSolver   = systemSetupFrame.getCheckbox("CBOX_NO_SOLVER").isSelected();

      // Solver Port
      String value = systemSetupFrame.getTextField("TXT_SOLVEPORT").getText();
      try {
        int intval = Integer.parseUnsignedInt(value);
        if (intval >= 100 && intval <= 65535) {
          // if port changed, setup new connection to solver
          if (solverConnection.getPort() != intval) {
            solverConnection.exit();
            solverConnection = new SolverInterface(intval);
            if (solverConnection.isValid()) {
              solverPort = intval;
              systemProps.setPropertiesItem(SystemProperties.SYS_SOLVER_PORT.toString(), value);
              updateDanfigFile();
            } else {
              solverConnection = new SolverInterface(solverPort);
              systemSetupFrame.getTextField("TXT_SOLVEPORT").setText(solverPort + "");
              printStatusError("solver port failure on " + intval + ". Reconnecting to port " + solverPort);
            }
          }
        }
      } catch (NumberFormatException ex) {
        systemSetupFrame.getTextField("TXT_SOLVEPORT").setText(solverPort + "");
        printStatusError("Invalid selection: must be value between 100 and 65535");
      }
      
      // Debug Port
      value = systemSetupFrame.getTextField("TXT_MYPORT").getText();
      try {
        int intval = Integer.parseUnsignedInt(value);
        if (intval >= 100 && intval <= 65535) {
          // don't change the port selection yet, since we haven't sent this to the
          // instrumented file yet. Just update the properties file info so we can use it
          // the next time we run.
          systemProps.setPropertiesItem(SystemProperties.SYS_DEBUG_PORT.toString(), value);
        }
      } catch (NumberFormatException ex) {
        systemSetupFrame.getTextField("TXT_MYPORT").setText(debugPort + "");
        printStatusError("Invalid selection: must be value between 100 and 65535");
      }
      
      // Max String Length
      value = systemSetupFrame.getTextField("TXT_MAXLEN").getText();
      try {
        int intval = Integer.parseUnsignedInt(value);
        if (intval >= 10000 && intval < 100000000) {
          maxLogLength = value;
          systemProps.setPropertiesItem(SystemProperties.SYS_LOG_LENGTH.toString(), maxLogLength);
        }
      } catch (NumberFormatException ex) {
        systemSetupFrame.getTextField("TXT_MAXLEN").setText(maxLogLength);
        printStatusError("Invalid selection: must be value between 10000 and 100000000 (10k to 100M)");
      }

      // now put this frame back into hiding
      systemSetupFrame.hide();
    }
  }
  
  private class Action_SetJavaHome implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      javaHomeSelector.setCurrentDirectory(new File(javaHome));
      javaHomeSelector.setDialogTitle("JAVA_HOME");
      javaHomeSelector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      //javaHomeSelector.setMultiSelectionEnabled(false);
      javaHomeSelector.setApproveButtonText("Select");
      int retVal = javaHomeSelector.showOpenDialog(mainFrame.getFrame());
      if (retVal == JFileChooser.APPROVE_OPTION) {
        // read the file
        File file = javaHomeSelector.getSelectedFile();
        javaHome = file.getAbsolutePath();
      
        // save location of project selection
        systemProps.setPropertiesItem(SystemProperties.SYS_JAVA_HOME.toString(), javaHome);
        systemSetupFrame.getLabel("LBL_JAVAHOME").setText(javaHome);
      }
    }
  }

  private class Action_SetDsePath implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      javaHomeSelector.setCurrentDirectory(new File(dsePath));
      javaHomeSelector.setDialogTitle("DSEPATH");
      javaHomeSelector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      //javaHomeSelector.setMultiSelectionEnabled(false);
      javaHomeSelector.setApproveButtonText("Select");
      int retVal = javaHomeSelector.showOpenDialog(mainFrame.getFrame());
      if (retVal == JFileChooser.APPROVE_OPTION) {
        // read the file
        File file = javaHomeSelector.getSelectedFile();
        dsePath = file.getAbsolutePath();
      
        // save location of project selection
        systemProps.setPropertiesItem(SystemProperties.SYS_DSEPATH.toString(), dsePath);
        systemSetupFrame.getLabel("LBL_DSEPATH").setText(dsePath);
      }
    }
  }

  private class Action_ClearClassFilesOnLoad implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      clearClassFilesOnStartup = systemSetupFrame.getCheckbox("CBOX_CLR_FILES").isSelected();
      systemProps.setPropertiesItem(SystemProperties.SYS_CLR_OLD_FILES.toString(),
              clearClassFilesOnStartup ? "true" : "false");
    }
  }
  
  private static void enableInputModeSelections() {
      String selection = (String) mainFrame.getCombobox("COMBO_INMODE").getSelectedItem();
      boolean bPort = false;
      boolean bEnable = false;
      switch (selection) {
        case "NONE":
          inputMode = InputMode.NONE;
          break;
        case "STDIN":
          inputMode = InputMode.STDIN;
          bEnable = true;
          break;
        case "HTTP_RAW":
          inputMode = InputMode.HTTP_RAW;
          bEnable = true;
          bPort = true;
          break;
        case "HTTP_POST":
          inputMode = InputMode.HTTP_POST;
          bEnable = true;
          bPort = true;
          break;
        case "HTTP_GET":
          inputMode = InputMode.HTTP_GET;
          bEnable = true;
          bPort = true;
          break;
      }

      // disable "send" controls if NONE selected, else enable it
      mainFrame.getButton("BTN_SEND").setEnabled(bEnable);
      mainFrame.getTextField("TXT_INPUT").setEnabled(bEnable);
      mainFrame.getTextField("TXT_PORT").setEnabled(bPort);
      mainFrame.getLabel("LBL_PORT").setEnabled(bPort);

      Color color;
      color = bEnable ? Color.white : mainFrame.getFrame().getBackground();
      mainFrame.getTextField("TXT_INPUT").setBackground(color);
      
      color = bPort ? Color.white : mainFrame.getFrame().getBackground();
      mainFrame.getTextField("TXT_PORT").setBackground(color);
  }
  
  private static void addMenuItem(JMenu menucat, String id, String title, ActionListener action) {
    if (menuItems.containsKey(id)) {
      System.err.println("ERROR: Menu Item '" + id + "' already defined!");
      return;
    }
    JMenuItem item = new JMenuItem(title);
    item.addActionListener(action);
    menucat.add(item);
    menuItems.put(id, item);
  }

  private static JMenuItem getMenuItem(String name) {
    return menuItems.get(name);
  }
  
  private static void addMenuCheckbox(JMenu menucat, String id, String title, boolean dflt, ItemListener action) {
    if (menuCheckboxes.containsKey(id)) {
      System.err.println("ERROR: Menu Checkbox '" + id + "' already defined!");
      return;
    }
    JCheckBoxMenuItem item = new JCheckBoxMenuItem(title);
    item.setSelected(dflt);
    if (action != null) {
      item.addItemListener(action);
    }
    menucat.add(item);
    menuCheckboxes.put(id, item);
  }
  
  private static JCheckBoxMenuItem getMenuCheckbox(String name) {
    return menuCheckboxes.get(name);
  }
  
  private void addPanelToTab(JTabbedPane tabpane, PanelTabs tabname, Component panel) {
    // make sure we don't already have the entry
    if (tabSelect.containsKey(tabname.toString())) {
      System.err.println("ERROR: '" + tabname + "' panel already defined in tabs");
      System.exit(1);
    }
    
    // now add the scroll pane to the tabbed pane
    tabpane.addTab(tabname.toString(), panel);
    
    // save access to text panel by name
    tabSelect.put(tabname.toString(), tabIndex++);
  }

  private static String getTabSelect() {
    GuiControls.PanelInfo panelInfo = mainFrame.getPanelInfo("PNL_TABBED");
    if (panelInfo == null || tabSelect.isEmpty()) {
      return null;
    }

    if (panelInfo.panel instanceof JTabbedPane) {
      JTabbedPane tabPanel = (JTabbedPane) panelInfo.panel;
      int curTab = tabPanel.getSelectedIndex();
      for (HashMap.Entry pair : tabSelect.entrySet()) {
        if ((Integer) pair.getValue() == curTab) {
          return (String) pair.getKey();
        }
      }
    }
    return null;
  }
  
  private static void setTabSelect(String tabname) {
    Integer index = tabSelect.get(tabname);
    if (index == null) {
      System.err.println("ERROR: '" + tabname + "' panel not found in tabs");
      System.exit(1);
    }

    GuiControls.PanelInfo panelInfo = mainFrame.getPanelInfo("PNL_TABBED");
    if (panelInfo != null && panelInfo.panel instanceof JTabbedPane) {
      JTabbedPane tabPanel = (JTabbedPane) panelInfo.panel;
      if (tabPanel.getTabCount() > index) {
        tabPanel.setSelectedIndex(index);
      }
    }
  }
  
  private static void setThreadControls(boolean enabled) {
    graphSetupFrame.getLabel ("TXT_TH_SEL").setEnabled(enabled);
    graphSetupFrame.getButton("BTN_TH_UP").setEnabled(enabled);
    graphSetupFrame.getButton("BTN_TH_DN").setEnabled(enabled);
  }
  
  private static void setThreadEnabled(boolean enabled) {
    JRadioButton button = graphSetupFrame.getRadiobutton("RB_THREAD");
    button.setEnabled(enabled);
    setThreadControls(button.isSelected());
  }
    
  private void enableControlSelections(boolean enable) {
    // control section widgets
    mainFrame.getCombobox ("COMBO_MAINCLS").setEnabled(enable);
    mainFrame.getLabel("LBL_MAINCLS").setEnabled(enable);

    mainFrame.getCombobox("COMBO_INMODE").setEnabled(enable);
    mainFrame.getLabel("LBL_INMODE").setEnabled(enable);

    mainFrame.getButton("BTN_RUNTEST").setEnabled(enable);
    mainFrame.getTextField("TXT_ARGLIST").setEnabled(enable);

    // bytecode section widgets
    mainFrame.getButton("BTN_BYTECODE").setEnabled(enable);
    mainFrame.getCombobox ("COMBO_CLASS").setEnabled(enable);
    mainFrame.getCombobox ("COMBO_METHOD").setEnabled(enable);
    mainFrame.getLabel("LBL_CLASS").setEnabled(enable);
    mainFrame.getLabel("LBL_METHOD").setEnabled(enable);
    
    // only enable these control widgets if the Input Mode is enabled
    enable = enable & (inputMode != InputMode.NONE);
    
    mainFrame.getButton("BTN_SEND").setEnabled(enable);
    mainFrame.getTextField("TXT_PORT").setEnabled(enable);
    mainFrame.getTextField("TXT_INPUT").setEnabled(enable);
  }  

  private static void setHighlightMode(CallGraph.GraphHighlight mode) {
    JRadioButton threadSelBtn = graphSetupFrame.getRadiobutton("RB_THREAD");
    JRadioButton timeSelBtn   = graphSetupFrame.getRadiobutton("RB_ELAPSED");
    JRadioButton instrSelBtn  = graphSetupFrame.getRadiobutton("RB_INSTRUCT");
    JRadioButton iterSelBtn   = graphSetupFrame.getRadiobutton("RB_ITER");
    JRadioButton statSelBtn   = graphSetupFrame.getRadiobutton("RB_STATUS");
    JRadioButton noneSelBtn   = graphSetupFrame.getRadiobutton("RB_NONE");

    // turn on the selected mode and turn off all others
    switch(mode) {
      case THREAD:
        threadSelBtn.setSelected(true);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);

        // send the thread selection to the CallGraph
        JLabel label = graphSetupFrame.getLabel("TXT_TH_SEL");
        int select = Integer.parseInt(label.getText().trim());
        callGraph.setThreadSelection(select);
        break;
      case TIME:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(true);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);
        break;
      case INSTRUCTION:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(true);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);
        break;
      case ITERATION:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(true);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(false);
        break;
      case STATUS:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(true);
        noneSelBtn.setSelected(false);
        break;
      case NONE:
        threadSelBtn.setSelected(false);
        timeSelBtn.setSelected(false);
        instrSelBtn.setSelected(false);
        iterSelBtn.setSelected(false);
        statSelBtn.setSelected(false);
        noneSelBtn.setSelected(true);
      default:
        break;
    }

    // set the mode flag & update graph
    graphMode = mode;
    if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
      callGraph.updateCallGraph(graphMode, false);
    }
  }

  private static void clearDebugLogger() {
    // clear out the debug logger
    debugLogger.clear();

    // reset debug input from network in case some messages are pending and clear buffer
    if (udpThread != null) {
      udpThread.resetInput();
      udpThread.clear();
    }

    // clear the graphics panel
    importGraph.clearPath();
    callGraph.clearGraphAndMethodList();
    if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
      callGraph.updateCallGraph(CallGraph.GraphHighlight.NONE, false);
    } else if (currentTab.equals(PanelTabs.COMPGRAPH.toString())) {
      importGraph.updateCallGraph();
    }

    // force the highlight selection back to NONE
    setHighlightMode(CallGraph.GraphHighlight.NONE);

    // reset thread selection to 0
    graphSetupFrame.getLabel("TXT_TH_SEL").setText("0");
    
    // reset the class and method counts
    mainFrame.getTextField("TXT_CLASSES").setText("0");
    mainFrame.getTextField("TXT_METHODS").setText("0");

    // init thread selection in highlighting to OFF
    setThreadEnabled(false);
  }
  
  private static void saveCallGraph(String graph, String type) {
    if (!type.equals("json")) {
      type = "png";
    }
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-");
    Date date = new Date();
    String defaultName = dateFormat.format(date) + graph;
    FileNameExtensionFilter filter = new FileNameExtensionFilter(type.toUpperCase() + " Files", type);
    String fileExtension = "." + type;
    fileSelector.setFileFilter(filter);
    fileSelector.setApproveButtonText("Save");
    fileSelector.setMultiSelectionEnabled(false);
    fileSelector.setSelectedFile(new File(defaultName + fileExtension));
    int retVal = fileSelector.showOpenDialog(graphSetupFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      File file = fileSelector.getSelectedFile();
      String basename = file.getAbsolutePath();
      
      // get the base name without extension so we can create matching json and png files
      int offset = basename.lastIndexOf('.');
      if (offset > 0) {
        basename = basename.substring(0, offset);
      }

      // remove any pre-existing file and convert method list to appropriate file
      if (graph.equals("callgraph")) {
        if (type.equals("json")) {
          File graphFile = new File(basename + fileExtension);
          graphFile.delete();
          callGraph.saveAsCompFile(graphFile);
        } else {
          File pngFile = new File(basename + fileExtension);
          pngFile.delete();
          callGraph.saveAsImageFile(pngFile);
        }
      } else {
        File pngFile = new File(basename + fileExtension);
        pngFile.delete();
        importGraph.saveAsImageFile(pngFile);
      }
    }
  }
  
  private static void saveByteFlowGraph() {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-");
    Date date = new Date();
    String classSelect  = (String) classCombo.getSelectedItem();
    classSelect = classSelect.replaceAll("/", "-");
    classSelect = classSelect.replaceAll("\\.", "-");
    classSelect = classSelect.replaceAll("\\$", "-");
    String methodSelect = (String) methodCombo.getSelectedItem();
    methodSelect = methodSelect.substring(0, methodSelect.indexOf("("));
    String defaultName = dateFormat.format(date) + classSelect + "-" + methodSelect;
    FileNameExtensionFilter filter = new FileNameExtensionFilter("PNG Files", "png");
    String fileExtension = ".png";
    fileSelector.setFileFilter(filter);
    fileSelector.setApproveButtonText("Save");
    fileSelector.setMultiSelectionEnabled(false);
    fileSelector.setSelectedFile(new File(defaultName + fileExtension));
    int retVal = fileSelector.showOpenDialog(graphSetupFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      File file = fileSelector.getSelectedFile();
      String basename = file.getAbsolutePath();
      
      // get the base name without extension so we can create matching json and png files
      int offset = basename.lastIndexOf('.');
      if (offset > 0) {
        basename = basename.substring(0, offset);
      }

      // remove any pre-existing file and convert method list to appropriate file
      File pngFile = new File(basename + fileExtension);
      pngFile.delete();
      bytecodeGraph.saveAsImageFile(pngFile);
    }
  }
  
  private static void saveDatabaseTable() {
    String filetype = "xls";
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-");
    String defaultName = dateFormat.format(new Date()) + "solutions." + filetype;
    FileNameExtensionFilter filter = new FileNameExtensionFilter(filetype.toUpperCase() + " Files", filetype);
    fileSelector.setFileFilter(filter);
    fileSelector.setApproveButtonText("Save");
    fileSelector.setMultiSelectionEnabled(false);
    fileSelector.setSelectedFile(new File(defaultName));
    int retVal = fileSelector.showOpenDialog(graphSetupFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      File file = fileSelector.getSelectedFile();
      file.delete();
      dbtable.saveDatabaseToExcel(file.getAbsolutePath());
    }
  }
  
  private static void startDebugPort() {
    // only run this once
    if (udpThread == null) {
      // disable timers while we are setting this up
      enableUpdateTimers(false);

      try {
        udpThread = new NetworkServer(debugPort);
      } catch (IOException ex) {
        System.err.println("ERROR: unable to start NetworkServer. " + ex);
      }

      printCommandMessage("danlauncher receiving port: " + debugPort);
      udpThread.start();
      udpThread.setLoggingCallback(makeConnection);
      networkListener = udpThread; // this allows us to signal the network listener

      // re-enable timers
      enableUpdateTimers(true);
    }
  }
  
  private static void enableUpdateTimers(boolean enable) {
    if (enable) {
      if (debugMsgTimer != null) {
        debugMsgTimer.start();
      }
      if (graphTimer != null) {
        graphTimer.start();
      }
    } else {
      if (debugMsgTimer != null) {
        debugMsgTimer.stop();
      }
      if (graphTimer != null) {
        graphTimer.stop();
      }
    }
  }
  
  /**
   * finds the classes in a jar file & sets the Class ComboBox to these values.
   */
  private static void setupClassList (String pathname) {
    // init the class list
    clsMethMap = new HashMap<>();
    classList = new ArrayList<>();
    fullMethList = new ArrayList<>();

    // read the list of methods from the "methodlist.txt" file created by Instrumentor
    try {
      File file = new File(pathname + "methodlist.txt");
      FileReader fileReader = new FileReader(file);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        fullMethList.add(line);
      }
      fileReader.close();
    } catch (IOException ex) {
      printCommandError("ERROR: setupClassList: " + ex);
      return;
    }

    // exit if no methods were found
    if (fullMethList.isEmpty()) {
      printCommandError("ERROR: setupClassList: No methods found in methodlist.txt file!");
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
          classList.add(curClass);
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
      classList.add(curClass);
      clsMethMap.put(curClass, methList);
    }

    // setup the class and method selections
    setClassSelections();
    printCommandMessage(classList.size() + " classes and " + fullMethList.size() + " methods found");
  }

  private static void setClassSelections() {
    // temp disable update of properties from main class selection, since all we are doing here
    // is filling the entries in. The user has not made a selection yet.
    mainClassInitializing = true;
    
    classCombo.removeAllItems();
    mainClassCombo.removeAllItems();
    for (int ix = 0; ix < classList.size(); ix++) {
      String cls = classList.get(ix);
      classCombo.addItem(cls);

      // now get the methods for the class and check if it has a "main"
      ArrayList<String> methodSelection = clsMethMap.get(cls);
      if (methodSelection != null && methodSelection.contains("main([Ljava/lang/String;)V")) {
        mainClassCombo.addItem(cls);
      }
    }

    // init class selection to 1st item
    classCombo.setSelectedIndex(0);
    
    // set main class selection from properties setting (wasn't done earlier because there may
    // not have been any selection yet).
    String val = projectProps.getPropertiesItem(ProjectProperties.MAIN_CLASS.toString(), "");
    if (!val.isEmpty()) {
      mainClassCombo.setSelectedItem(val);
    }
    
    // now we can re-enable changes in the main class causing the properties entry to be updated
    mainClassInitializing = false;
    updateProjectProperty("COMBO_MAINCLS");
    
    // now update the method selections
    setMethodSelections((String) classCombo.getSelectedItem());

    // in case selections require expansion, adjust frame packing
    // TODO: skip this, since it causes the frame size to get extremely large for some reason
//    GuiControls.getFrame().pack();
  }

  private static void setMethodSelections(String clsname) {
    // init the method selection list
    methodCombo.removeAllItems();

    // make sure we have a valid class selection
    if (clsname == null || clsname.isEmpty()) {
      return;
    }
    
    if (clsname.endsWith(".class")) {
      clsname = clsname.substring(0, clsname.length()-".class".length());
    }

    // get the list of methods for the selected class from the hash map
    ArrayList<String> methodSelection = clsMethMap.get(clsname);
    if (methodSelection != null) {
      // now get the methods for the class and place in the method selection combobox
      for (String method : methodSelection) {
        methodCombo.addItem(method);
      }

      // set the 1st entry as the default selection
      if (methodCombo.getItemCount() > 0) {
        methodCombo.setSelectedIndex(0);
      }
    }
  }
  
  private static void initProjectProperties(String pathname) {
    // create the properties file for the project
    projectProps = new PropertiesFile(pathname + ".danlauncher", "PROJECT_PROPERTIES", commandLogger);
    debugParams.setPropertiesFile(projectProps);

    // loop thru the properties table and set up each entry
    for (PropertiesTable propEntry : PROJ_PROP_TBL) {
      // get properties values if defined (use the current gui control value as the default if not)
      String setting = propEntry.panel.getInputControl(propEntry.controlName, propEntry.controlType);
      String val = projectProps.getPropertiesItem(propEntry.tag.toString(), setting);
      if (!setting.equals(val)) {
        // if property value was found & differs from gui setting, update the gui
        printCommandMessage("Project updated " + propEntry.controlName + " to value: " + val);
        propEntry.panel.setInputControl(propEntry.controlName, propEntry.controlType, val);
      }
    }
  }
  
  private static void updateProjectProperty(String ctlName) {
    for (PropertiesTable propEntry : PROJ_PROP_TBL) {
      if (ctlName.equals(propEntry.controlName)) {
        String val = propEntry.panel.getInputControl(propEntry.controlName, propEntry.controlType);
        if(projectProps != null) {
          projectProps.setPropertiesItem(propEntry.tag.toString(), val);
        }
        return;
      }
    }
    System.err.println("ERROR: User control '" + ctlName + "' not found");
  }

  private static void addRecordDelay() {
    // determine the delay time (ignore if it was < 1 sec or start wasn't initialized)
    if (recordStartTime != 0) {
      long elapsed = 1 + (System.currentTimeMillis() - recordStartTime) / 1000;
      if (elapsed > 1) {
        addRecordCommand(RecordID.DELAY, "" + elapsed);
      }
    }
  }

  private static void addRecordHttpDelay(String port) {
    if (recording.isEmpty()) {
      return;
    }
    
    // if we have already established the server port and waited for it, just do a delay
    for (RecordCommand entry : recording) {
      if (entry.command == RecordID.SET_HTTP) {
        addRecordDelay();
        return;
      }
    }

    // if 1st time on HTTP, indicate the port selection and wait till server is up
    addRecordCommand(RecordID.SET_HTTP, port);
    addRecordCommand(RecordID.WAIT_FOR_SERVER, "");
  }
  
  private static void addRecordCommand(RecordID command, String arg) {
    if (recordState) {
      // if command is not RUN command, then only add if RUN was previously saved
      if (recording.isEmpty() && command != RecordID.RUN) {
        printCommandMessage("Igoring command " + command.toString() + " until RUN command received");
        return;
      }
      
      // add command
      printCommandMessage("--- RECORDED: " + command.toString() + " " + arg);
      recording.add(new RecordCommand(command, arg));

      // get start of delay to next command
      recordStartTime = System.currentTimeMillis();
    }
  }
  
  private static boolean fileCheck(String fname) {
    if (new File(fname).isFile()) {
      return true;
    }

    printStatusError("Missing file: " + fname);
    return false;
  }
  
  private static File loadJSONFile() {
    FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON Files", "json");
    fileSelector.setFileFilter(filter);
    //fileSelector.setSelectedFile(new File("TestMain.jar"));
    fileSelector.setMultiSelectionEnabled(false);
    fileSelector.setApproveButtonText("Load");
    int retVal = fileSelector.showOpenDialog(mainFrame.getFrame());
    if (retVal != JFileChooser.APPROVE_OPTION) {
      return null;
    }

    // read the file
    return fileSelector.getSelectedFile();
  }
  
  private static int loadJarFile() {
    printStatusClear();

    // if application is running, tell user to stop before loading a new one
    if (runMode == RunMode.RUNNING) {
      printStatusError("Application is currently running - STOP it beefore loading new jar file");
      return -1;
    }
      
    FileNameExtensionFilter filter = new FileNameExtensionFilter("Jar Files", "jar");
    fileSelector.setFileFilter(filter);
    //fileSelector.setSelectedFile(new File("TestMain.jar"));
    fileSelector.setMultiSelectionEnabled(false);
    fileSelector.setApproveButtonText("Load");
    int retVal = fileSelector.showOpenDialog(mainFrame.getFrame());
    if (retVal != JFileChooser.APPROVE_OPTION) {
      return 1;
    }

    // read the file
    File file = fileSelector.getSelectedFile();
    projectName = file.getName();
    projectPathName = file.getParentFile().getAbsolutePath() + "/";
    File outPath = new File(projectPathName + OUTPUT_FOLDER);
    if (!outPath.isDirectory()) {
      outPath.mkdirs();
    }
      
    // save location of project selection
    systemProps.setPropertiesItem(SystemProperties.SYS_PROJECT_PATH.toString(), projectPathName);
        
    // init project config file to current settings
    initProjectProperties(projectPathName);
        
    // verify all the required files exist
    if (!fileCheck(projectPathName + projectName) ||
        !fileCheck(dsePath + "/danalyzer/dist/danalyzer.jar") ||
        !fileCheck(dsePath + "/danalyzer/lib/commons-io-2.5.jar") ||
        !fileCheck(dsePath + "/danalyzer/lib/asm-7.2.jar") ||
        !fileCheck(dsePath + "/danalyzer/lib/asm-tree-7.2.jar")) {
      return -1;
    }
    
    // disable the janalyzer graph Save button, since there is no graph now.
    getMenuItem("MENU_SAVE_JGRAPH").setEnabled(false);

    // clear out the symbolic parameter list and the history list for bytecode viewer
    // as well as the bytecode data, byteflow graph, solutions and debug/call graph info.
    symbolTbl.clear();
    localVarTbl.clear("");
    bytecodeHistory.clear();
    bytecodeViewer.clear();
    bytecodeGraph.clear();
    clearDebugLogger();
    if (clearSolutionsOnStartup && solverConnection.isValid()) {
      solverConnection.sendClearAll();
    }

    String content = initDanfigInfo();
    
    // read the symbolic parameter definitions from debugParams file (if present)
    if (loadDanfigOnStartup) {
      readSymbolicList(content);
    }
  
    String localpath = "*";
    if (new File(projectPathName + "lib").isDirectory()) {
      localpath += ":lib/*";
    }
    if (new File(projectPathName + "libs").isDirectory()) {
      localpath += ":libs/*";
    }

    String mainclass = "danalyzer.instrumenter.Instrumenter";
    String classpath = dsePath + "/danalyzer/dist/danalyzer.jar";
    classpath += ":" + dsePath + "/danalyzer/lib/commons-io-2.5.jar";
    classpath += ":" + dsePath + "/danalyzer/lib/asm-7.2.jar";
    classpath += ":" + dsePath + "/danalyzer/lib/asm-tree-7.2.jar";
    classpath += ":" + localpath;

    // remove any existing class and javap files in the location of the jar file
    if (clearClassFilesOnStartup) {
      try {
        FileUtils.deleteDirectory(new File(projectPathName + CLASSFILE_STORAGE));
        FileUtils.deleteDirectory(new File(projectPathName + JAVAPFILE_STORAGE));
      } catch (IOException ex) {
        printCommandError("ERROR: " + ex.getMessage());
      }
    }
      
    // determine if jar file needs instrumenting
    if (projectName.endsWith("-dan-ed.jar")) {
      // file is already instrumented - use it as-is
      printCommandMessage("Input file already instrumented - using as is.");
      projectName = projectName.substring(0, projectName.indexOf("-dan-ed.jar")) + ".jar";
    } else {
      int retcode = 0;
      String baseName = projectName.substring(0, projectName.indexOf(".jar"));

      // strip out any debug info (it screws up the agent)
      // this will transform the temp file and name it the same as the original instrumented file
      printCommandMessage("Stripping debug info from uninstrumented jar file");
      String outputName = baseName + "-strip.jar";
      String[] command2 = { "pack200", "-r", "-G", projectPathName + outputName, projectPathName + projectName };
      // this creates a command launcher that runs on the current thread
      CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
      retcode = commandLauncher.start(command2, projectPathName);
      if (retcode == 0) {
        printStatusMessage("Debug stripping was successful");
        printCommandMessage(commandLauncher.getResponse());
      } else {
        printStatusError("stripping file: " + projectName);
        return -1;
      }
        
      // instrument the jar file
      printCommandMessage("Instrumenting stripped file: " + outputName);
      String[] command = { "java", "-cp", classpath, mainclass, outputName, "1" };
      retcode = commandLauncher.start(command, projectPathName);
      if (retcode == 0) {
        printStatusMessage("Instrumentation successful");
        printCommandMessage(commandLauncher.getResponse());
        outputName = baseName + "-strip-dan-ed.jar";
      } else {
        printStatusError("instrumenting file: " + outputName);
        return -1;
      }
        
      // rename the instrumented file to the correct name
      String tempjar = outputName;
      outputName = baseName + "-dan-ed.jar";
      printCommandMessage("Renaming " + tempjar + " to " + outputName);
      File tempfile = new File(projectPathName + tempjar);
      if (!tempfile.isFile()) {
        printStatusError("instrumented file not found: " + tempjar);
        return -1;
      }
      File newfile = new File(projectPathName + outputName);
      tempfile.renameTo(newfile);
        
      // remove temp file
      tempjar = baseName + "-strip.jar";
      tempfile = new File(projectPathName + tempjar);
      if (tempfile.isFile()) {
        printCommandMessage("Removing " + tempjar);
        tempfile.delete();
      }
    }
      
    // update the class and method selections
    setupClassList(projectPathName);
    
    // now we can set the class list for the combo selections and the set the debug param selections
    debugSetupFrame.setComboboxSelections("COMBO_CLASS", classList);
    debugParams.setupPanel();
    setDebugControlEnable();
    
    // enable the input mode controls based on the selection
    enableInputModeSelections();

    // setup access to the network listener thread
    startDebugPort();
    return 0;
  }

  private void runTest(String arglist) {
    printStatusClear();
    
    // if application is running, tell user to stop before running a new one
    if (runMode == RunMode.RUNNING) {
      printStatusError("Application is currently running - STOP it beefore running again");
      return;
    }
    if (!Utils.isServerRunning(solverPort)) {
      if (allowRunWithoutSolver) {
        printStatusError("dansolver is not running on port " + solverPort + ". Solutions will not be generated.");
      } else {
        printStatusError("dansolver is not running on port " + solverPort + ". Start it prior to running the application.");
        return;
      }
    }
    
    // clear out the debugger so we don't add onto existing call graph
    clearDebugLogger();
    if (clearSolutionsOnRun && solverConnection.isValid()) {
      solverConnection.sendClearAll();
    }

    String instrJarFile = projectName.substring(0, projectName.lastIndexOf(".")) + "-dan-ed.jar";
    
    // verify all the required files exist
    if (!fileCheck(projectPathName + instrJarFile) ||
        !fileCheck(dsePath + "/danalyzer/dist/danalyzer.jar") ||
        !fileCheck(dsePath + "/danalyzer/lib/com.microsoft.z3.jar") ||
        !fileCheck(dsePath + "/danalyzer/lib/commons-io-2.5.jar") ||
        !fileCheck(dsePath + "/danalyzer/lib/asm-7.2.jar") ||
        !fileCheck(dsePath + "/danalyzer/lib/asm-tree-7.2.jar") ||
        !fileCheck(dsePath + "/danhelper/" + System.mapLibraryName("danhelper"))) {
      return;
    }
    
    // get the user-supplied main class and input value
    String mainclass = (String) mainClassCombo.getSelectedItem();
    if (mainclass == null) {
      printStatusError("no main class found!");
      return;
    }
    mainclass = mainclass.replaceAll("/", ".");

    // init the debug logging to the current project path
    if (udpThread != null) {
      udpThread.setStorageFile(projectPathName + OUTPUT_FOLDER + "debug.log");
      udpThread.resetInput();
    }
    
    // build up the command to run
    String localpath = "*";
    if (new File(projectPathName + "lib").isDirectory()) {
      localpath += ":lib/*";
    }
    if (new File(projectPathName + "libs").isDirectory()) {
      localpath += ":libs/*";
    }
    String options = "-Xverify:none";
    String bootlpath = "-Dsun.boot.library.path=" + javaHome + "/bin:/usr/lib:/usr/local/lib";
    String bootcpath ="-Xbootclasspath/a"
              + ":" + dsePath + "/danalyzer/dist/danalyzer.jar"
              + ":" + dsePath + "/danalyzer/lib/com.microsoft.z3.jar";
    String agentpath ="-agentpath:" + dsePath + "/danhelper/" + System.mapLibraryName("danhelper");
    String classpath = instrJarFile
              + ":" + dsePath + "/danalyzer/lib/commons-io-2.5.jar"
              + ":" + dsePath + "/danalyzer/lib/asm-7.2.jar"
              + ":" + dsePath + "/danalyzer/lib/asm-tree-7.2.jar"
              + ":" + localpath;

    printStatusMessage("Run command started...");
    
    // run the instrumented jar file
    String[] argarray = arglist.split("\\s+"); // need to seperate arg list into seperate entries
    String[] command = { "java", options, bootlpath, bootcpath, agentpath, "-cp", classpath, mainclass };
    ArrayList<String> cmdlist = new ArrayList(Arrays.asList(command));
    cmdlist.addAll(Arrays.asList(argarray));
    String[] fullcmd = new String[cmdlist.size()];
    fullcmd = cmdlist.toArray(fullcmd);

    runMode = RunMode.RUNNING;
    startTime = System.currentTimeMillis();
    
    threadLauncher.init(new ThreadTermination());
    threadLauncher.launch(fullcmd, projectPathName, "run_" + projectName, null);
    
    // disable the Run and Get Bytecode buttons until the code has terminated
    mainFrame.getButton("BTN_RUNTEST").setEnabled(false);
    mainFrame.getButton("BTN_BYTECODE").setEnabled(false);
    
    // allow user to terminate the test
    mainFrame.getButton("BTN_STOPTEST").setEnabled(true);
  }

  private static void runBytecodeParser(String classSelect, String methodSelect, String content) {
    bytecodeViewer.parseJavap(classSelect, methodSelect, content);
    if (bytecodeViewer.isValidBytecode()) {
      printStatusMessage("Successfully generated bytecode for method");
      bytecodeGraph.drawGraphNormal();
      getMenuItem("MENU_SAVE_BCODE").setEnabled(bytecodeGraph.isValid());
    } else {
      printStatusMessage("Generated class bytecode, but method not found");
    }
  }
  
  private static void sendHttpMessage(String mode, String targetURL, String urlParameters) {
    targetURL = "http://localhost:" + targetURL;

    String USER_AGENT = "Mozilla/5.0";
    HttpURLConnection connection = null;
    DataOutputStream wr;
    int responseCode;
    
    try {
      // Create connection
      URL url = new URL(targetURL);
      connection = (HttpURLConnection) url.openConnection();
      switch (mode) {
        case "HTTP":
          connection.setUseCaches(false);
          connection.setDoOutput(true);

          // Send request
          printStatusMessage("Sending HTTP request to: " + targetURL);
          wr = new DataOutputStream (connection.getOutputStream());
          wr.writeBytes(urlParameters);
          wr.flush();
          wr.close();

          responseCode = connection.getResponseCode();
          printCommandMessage("HTTP parameters: " + urlParameters);
          printCommandMessage("Response code: " + responseCode);
          break;
          
        case "POST":
          connection.setRequestMethod("POST");
          connection.setRequestProperty("User-Agent", USER_AGENT);
          connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
          connection.setRequestProperty("Content-Length", Integer.toString(urlParameters.getBytes().length));
          connection.setRequestProperty("Content-Language", "en-US");  
          connection.setUseCaches(false);
          connection.setDoOutput(true);

          // Send request
          printStatusMessage("Sending POST request to: " + targetURL);
          wr = new DataOutputStream (connection.getOutputStream());
          wr.writeBytes(urlParameters);
          wr.flush();
          wr.close();

          responseCode = connection.getResponseCode();
          printCommandMessage("POST parameters: " + urlParameters);
          printCommandMessage("Response code: " + responseCode);
          break;
          
        case "GET":
          connection.setRequestMethod("GET");
          connection.setRequestProperty("User-Agent", USER_AGENT);
          responseCode = connection.getResponseCode();

          printStatusMessage("Sending GET request to: " + targetURL);
          printCommandMessage("Response code: " + responseCode);
          break;
      }

      // Get Response  
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      // display response.toString()
      printStatusMessage(mode + " successful");
      printCommandMessage(mode + " RESPONSE: " + response.toString());
    } catch (IOException ex) {
      // display error
      printStatusError(mode + " failure: " +ex.getMessage());
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
  
  private static String generateJavapFile(String classSelect) {
    printStatusClear();

    // add the suffix
    classSelect = classSelect  + ".class";
    
    // first we have to extract the class file from the jar file
    if (projectPathName == null || projectName == null) {
      printStatusError("No project jar file hass been loaded.");
      return null;
    }
    File jarfile = new File(projectPathName + projectName);
    if (!jarfile.isFile()) {
      printStatusError("The project jar file was not found: " + projectName);
      return null;
    }
    try {
      // extract the selected class file
      int rc = extractClassFile(jarfile, classSelect);
      if (rc != 0) {
        return null;
      }
    } catch (IOException ex) {
      printStatusError(ex.getMessage());
      return null;
    }

    printCommandMessage("Generating javap file for: " + classSelect);
    
    // decompile the selected class file
    String[] command = { "javap", "-p", "-c", "-s", "-l", CLASSFILE_STORAGE + "/" + classSelect };
    // this creates a command launcher that runs on the current thread
    CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
    int retcode = commandLauncher.start(command, projectPathName);
    if (retcode != 0) {
      printStatusError("running javap on file: " + classSelect);
      return null;
    }

    // success - save the output as a file
    String content = commandLauncher.getResponse();
    return content;
  }

  private static int extractClassFile(File jarfile, String className) throws IOException {
    // get the path relative to the application directory
    int offset;
    String relpathname = "";
    offset = className.lastIndexOf('/');
    if (offset > 0)  {
      relpathname = className.substring(0, offset + 1);
      className = className.substring(offset + 1);
    }
    
    printCommandMessage("Extracting '" + className + "' from " + relpathname);
    
    // get the location of the jar file (where we will extract the class files to)
    String jarpathname = jarfile.getAbsolutePath();
    offset = jarpathname.lastIndexOf('/');
    if (offset > 0) {
      jarpathname = jarpathname.substring(0, offset + 1);
    }
    
    JarFile jar = new JarFile(jarfile.getAbsoluteFile());
    Enumeration enumEntries = jar.entries();

    // look for specified class file in the jar
    while (enumEntries.hasMoreElements()) {
      JarEntry file = (JarEntry) enumEntries.nextElement();
      String fullname = file.getName();

      if (fullname.equals(relpathname + className)) {
        String fullpath = jarpathname + CLASSFILE_STORAGE + "/" + relpathname;
        File fout = new File(fullpath + className);
        // skip if file already exists
        if (fout.isFile()) {
          printStatusMessage("File '" + className + "' already created");
        } else {
          // make sure to create the entire dir path needed
          File relpath = new File(fullpath);
          if (!relpath.isDirectory()) {
            relpath.mkdirs();
          }

          // extract the file to its new location
          InputStream istream = jar.getInputStream(file);
          FileOutputStream fos = new FileOutputStream(fout);
          while (istream.available() > 0) {
            // write contents of 'istream' to 'fos'
            fos.write(istream.read());
          }
        }
        return 0;
      }
    }
    
    printStatusError("The selected class '" + className + "' is not contained in the current project jar file");
    return -1;
  }
  
  private static String initDanfigInfo() {
    // initialize replacement config info
    String content = "#! DANALYZER SYMBOLIC EXPRESSION LIST" + Utils.NEWLINE;
    content += "# DANLAUNCHER_VERSION" + Utils.NEWLINE;
    content += "#" + Utils.NEWLINE;
    content += "# DEBUG SETUP" + Utils.NEWLINE;
    content += "DebugFlags: " + debugParams.getValue("DebugFlags") + Utils.NEWLINE;
    content += "DebugMode: TCPPORT" + Utils.NEWLINE;
    content += "DebugPort: " + debugPort + Utils.NEWLINE;
    content += "IPAddress: localhost" + Utils.NEWLINE;
    content += "#" + Utils.NEWLINE;
    content += "# DEBUG TRIGGER SETUP" + Utils.NEWLINE;
    content += "TriggerOnError: "  + debugParams.getValue("TriggerOnError") + Utils.NEWLINE;
    content += "TriggerOnException: " + debugParams.getValue("TriggerOnException") + Utils.NEWLINE;
    content += "TriggerOnInstr: "  + debugParams.getValue("TriggerOnInstr") + Utils.NEWLINE;
    content += "TriggerOnCall: "   + debugParams.getValue("TriggerOnCall") + Utils.NEWLINE;
    content += "TriggerOnReturn: " + debugParams.getValue("TriggerOnReturn") + Utils.NEWLINE;
    content += "TriggerOnRefWrite: " + debugParams.getValue("TriggerOnRefWrite") + Utils.NEWLINE;
    content += "TriggerAuto: "     + debugParams.getValue("TriggerAuto") + Utils.NEWLINE;
    content += "TriggerAnyMeth: "  + debugParams.getValue("TriggerAnyMeth") + Utils.NEWLINE;
    content += "TriggerClass: "    + debugParams.getValue("TriggerClass") + Utils.NEWLINE;
    content += "TriggerMethod: "   + debugParams.getValue("TriggerMethod") + Utils.NEWLINE;
    content += "TriggerCount: "    + debugParams.getValue("TriggerCount") + Utils.NEWLINE;
    content += "TriggerRefCount: " + debugParams.getValue("TriggerRefCount") + Utils.NEWLINE;
    content += "TriggerRange: "    + debugParams.getValue("TriggerRange") + Utils.NEWLINE;
    content += "TriggerInstruction: " + debugParams.getValue("TriggerInstruction") + Utils.NEWLINE;
    content += "TriggerRefIndex: " + debugParams.getValue("TriggerRefIndex") + Utils.NEWLINE;
    content += "#" + Utils.NEWLINE;
    content += "# SOLVER INTERFACE" + Utils.NEWLINE;
    content += "SolverPort: " + solverPort + Utils.NEWLINE;
    content += "SolverAddress: localhost" + Utils.NEWLINE;
    content += "SolverMethod: " + "NONE" + Utils.NEWLINE; // debugParams.getValue("SolverMethod") + Utils.NEWLINE;
    return content;
  }
  
  private static void readSymbolicList(String content) {

    boolean updateFile = false;
    boolean makeBackup = true;
    
    File file = new File(projectPathName + "danfig");
    if (!file.isFile()) {
      printCommandMessage("No danfig file found at path: " + projectPathName);
      updateFile = true;
    } else {
      try {
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        printCommandMessage("Reading danfig selections");
        while ((line = bufferedReader.readLine()) != null) {
          line = line.trim();
          if (line.startsWith("# DANLAUNCHER_VERSION")) {
            // determine if this file was created by danlauncher (else, it was a danalyzer original)
            printCommandMessage("danfig file was generated by danlauncher");
            makeBackup = false;
          } else if (line.startsWith("Symbolic:")) {
            // extract symbolic definition
            String word[] = line.split("\\s+");
            String method, index, start, end, name, type;
            switch(word.length - 1) {
              case 2: // method, slot
                name   = "";
                method = word[1].trim().replace(".","/");
                index  = word[2].trim();
                start  = "0";
                end    = "0"; // this will default to entire method range
                type   = "";
                break;
              case 3: // method, slot, name
                name   = word[1].trim();
                method = word[2].trim().replace(".","/");
                index  = word[3].trim();
                start  = "0";
                end    = "0"; // this will default to entire method range
                type   = "";
                break;
              case 6: // index, method, start range, end range, name, data type
                name   = word[1].trim();
                method = word[2].trim().replace(".","/");
                index  = word[3].trim();
                start  = word[4].trim();
                end    = word[5].trim();
                type   = word[6].trim();
                break;
              default:
                printCommandError("ERROR: Invalid Symbolic word count (" + word.length + "): " + line);
                return;
            }
            try {
              int lineStart = Integer.parseUnsignedInt(start);
              int lineEnd   = Integer.parseUnsignedInt(end);
              name = symbolTbl.addEntryByLine(method, name, type, index, lineStart, lineEnd);
            } catch (NumberFormatException ex) {
              printCommandError("ERROR: Invalid numeric values for lineStart and lineEnd: " + line);
              return;
            }
            if (name == null) {
              printCommandMessage("This symbolic value already exists");
            } else {
              printCommandMessage("Symbol added - id: '" + name + "', method: " + method + ", slot: " + index +
                  ", type: " + type + ", range { " + start + ", " + end + " }");

              // save line content
              content += line + Utils.NEWLINE;
              updateFile = true;
            }
          } else if (line.startsWith("Constraint:")) {
            // extract symbolic constraint
            String word[] = line.split("\\s+");
            if (word.length != 4) {
              printCommandError("ERROR: Invalid Constraint word count (" + word.length + "): " + line);
              return;
            }
            // get the entries in the line
            String id = word[1].trim();
            String compare = word[2].trim();
            String constrVal = word[3].trim();
            printCommandMessage("Constraint added - id: '" + id + "' : " + compare + " " + constrVal);

            // add entry to list 
            addSymbConstraint(id, compare, constrVal);
            
            // save line content
            content += line + Utils.NEWLINE;
            updateFile = true;
          }
        }
        fileReader.close();
      } catch (IOException ex) {
        printCommandError("ERROR: " + ex.getMessage());
      }
    }

    // if a change is needed, rename the old file and write the modified file back
    if (updateFile || makeBackup) {
      // backup current file if it was not one we made just for danlauncher
      if (makeBackup) {
        printCommandMessage("Making backup of original danfig file");
        file.renameTo(new File(projectPathName + "danfig.save"));
      }
      
      // create the new file
      printCommandMessage("Updating danfig file: " + symbolTbl.getSize() + " symbolics added");
      Utils.saveTextFile(projectPathName + "danfig", content);
    }
  }

  /**
   * This performs the actions to take upon completion of the thread command.
   */
  private class ThreadTermination implements ThreadLauncher.ThreadAction {

    @Override
    public void allcompleted(ThreadLauncher.ThreadInfo threadInfo) {
      // restore the stdout and stderr
      System.out.flush();
      System.err.flush();
      System.setOut(STANDARD_OUT);
      System.setErr(STANDARD_ERR);
    }

    @Override
    public void jobprestart(ThreadLauncher.ThreadInfo threadInfo) {
//      printCommandMessage("jobprestart - job " + threadInfo.jobid + ": " + threadInfo.jobname);
    }

    @Override
    public void jobstarted(ThreadLauncher.ThreadInfo threadInfo) {
      printCommandMessage("jobstart - " + threadInfo.jobname + ": pid " + threadInfo.pid);
    }
        
    @Override
    public void jobfinished(ThreadLauncher.ThreadInfo threadInfo) {
      printCommandMessage("jobfinished - " + threadInfo.jobname + ": status = " + threadInfo.exitcode);
      if (threadInfo.exitcode == 0) {
        printStatusMessage(threadInfo.jobname + " command (pid " + threadInfo.pid + ") completed successfully");
      } else if (!threadInfo.signal.isEmpty()) {
        printStatusMessage(threadInfo.jobname + " command terminated with " + threadInfo.signal);
      } else {
        printStatusError("Failure executing command: " + threadInfo.jobname);
      }
      
      // disable stop key abd re-enable the Run and Get Bytecode buttons
      runMode = RunMode.IDLE;
      mainFrame.getButton("BTN_STOPTEST").setEnabled(false);
      mainFrame.getButton("BTN_RUNTEST").setEnabled(true);
      mainFrame.getButton("BTN_BYTECODE").setEnabled(true);

      // if recording enabled, add command to list
      if (!recording.isEmpty()) {
        // this records that the application has terminated - skip if user issued STOP
        // (we use this to wait for the application to terminate on its own)
        for (RecordCommand entry : recording) {
          if (entry.command == RecordID.STOP) {
            return;
          }
        }
        addRecordCommand(RecordID.WAIT_FOR_TERM, "");
      }
    }
  }
        
  private class KillTimerListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      ThreadLauncher.ThreadInfo threadInfo = threadLauncher.stopAll();
      if (threadInfo == null || threadInfo.pid < 0) {
        runMode = RunMode.IDLE;
        killTimer.stop();
        return;
      }
      
      switch (runMode) {
        case IDLE:
        case RUNNING:
          // should not have gotten here, but let's kill the kill timer just to be safe
          killTimer.stop();
          break;

        case TERMINATING:
          printCommandMessage("Killing job " + threadInfo.jobid + ": pid " + threadInfo.pid);
          String[] command2 = { "kill", "-9", threadInfo.pid.toString() }; // SIGKILL
          CommandLauncher commandLauncher = new CommandLauncher(commandLogger);
          commandLauncher.start(command2, null);
          runMode = RunMode.KILLING;
          break;

        case KILLING:
          // didn't work - let's give up
          runMode = RunMode.IDLE;
          killTimer.stop();
          break;
          
        case EXITING:
          // complete the exit process by closing the frame and exiting
          mainFrame.close();
          System.exit(0);
          break;
      }
    }
  }
        
  private class GraphUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // if Call Graph tab selected, update graph
      if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
        callGraph.updateCallGraph(graphMode, false);
      }
    }
  }

  private class DebugInputListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // update elapsed time display
      if (runMode == RunMode.RUNNING) {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        String secs = "" + (elapsed % 60);
        String mins = "" + (elapsed / 60);
        secs = (secs.length() > 1) ? secs : "0" + secs;
        mins = (mins.length() > 1) ? mins : "0" + mins;
        mainFrame.getTextField("TXT_ELAPSED").setText(mins + ":" + secs);
      }
      
      // read & process next message
      if (udpThread != null) {
        String message = udpThread.getNextMessage();
        if (message != null) {
          String methCall = debugLogger.processMessage(message, callGraph);
          if (methCall != null) {
            boolean newEntry = importGraph.addPathEntry(methCall);
            if (newEntry && currentTab.equals(PanelTabs.COMPGRAPH.toString())) {
              importGraph.updateCallGraph();
            }
          }
          // enable/disable Call Graph save buttons based on whether there is anything to save
          int methodCount = callGraph.getMethodCount();
          getMenuItem("MENU_SAVE_CGRAPH").setEnabled(methodCount > 0);
          getMenuItem("MENU_SAVE_JSON").setEnabled(methodCount > 0);

          mainFrame.getTextField("TXT_CLASSES").setText("" + callGraph.getClassCount());
          mainFrame.getTextField("TXT_METHODS").setText("" + methodCount);
          
          // update the thread count display
          int threads = debugLogger.getThreadCount();
          if (threads > 1) {
            setThreadEnabled(true);
          }
          graphSetupFrame.getLabel("LBL_THREADS").setText("Threads: " + threads);
        }
      }
    }
  }

}
