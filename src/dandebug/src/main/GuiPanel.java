/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import gui.GuiControls;
import callgraph.CallGraph;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import panels.DebugLogger;

/**
 *
 * @author dmcd2356
 */
public class GuiPanel {

  // location of properties file to use
  private final static String PROPERTIES_PATH = ".dandebug/";
  private final static String PROPERTIES_FILE = "site.properties";
  
  private enum ElapsedMode { OFF, RUN, RESET }

  private enum PanelTabs { DEBUG, CALLGRAPH, FILE }
  
  private final static GuiControls  mainFrame = new GuiControls();
  private static PropertiesFile  props;
  private static JTabbedPane     tabPanel;
  private static JTextPane       liveTextPane;
  private static JTextPane       fileTextPane;
  private static JPanel          graphPanel;
  private static JFileChooser    fileSelector;
  private static CallGraph       callGraph;
  private static DebugLogger     debugLogger;
  private static NetworkServer   udpThread;
  private static NetworkListener listener;
  private static DebugInputListener inputListener;
  private static Timer           pktTimer;
  private static Timer           graphTimer;
  private static Timer           statsTimer;
  private static int             tabIndex;
  private static int             linesRead;
  private static int             threadCount;
  private static int             errorCount;
  private static ArrayList<Integer> threadList = new ArrayList<>();
  private static long            elapsedStart;
  private static ElapsedMode     elapsedMode;
  private static CallGraph.GraphHighlight  graphMode;
  private static boolean         graphShowAllThreads;
  private static HashMap<PanelTabs, Integer> tabSelect = new HashMap<>();
  private static String          serverPort = "";
  private static String          clientPort = "";
  private static Visitor         makeConnection;
  private static String          currentTab;
  
  private static final Dimension SCREEN_DIM = Toolkit.getDefaultToolkit().getScreenSize();

  // allow ServerThread to indicate on panel when a connection has been made for TCP
  public interface Visitor {
    void showConnection(String connection);
    void resetConnection();
  }

  GuiPanel() {
    makeConnection = new Visitor() {
      @Override
      public void showConnection(String connection) {
        clientPort = connection;
        JLabel label = mainFrame.getLabel("LBL_MESSAGES");
        if (label != null) {
          label.setText("connected to  " + GuiPanel.clientPort);
        }
      }

      @Override
      public void resetConnection() {
        JLabel label = mainFrame.getLabel("LBL_MESSAGES");
        if (label != null) {
          label.setText("connected to  " + GuiPanel.clientPort + "  (CONNECTION CLOSED)");
        }
      }
    };
  } 

/**
 * creates a debug panel to display the Logger messages in.
   * @param port  - the port to use for reading messages
   * @param tcp     - true if use TCP, false to use UDP
 */  
  public void createDebugPanel(int port, boolean tcp) {
    // if a panel already exists, close the old one
    if (GuiPanel.mainFrame.isValidFrame()) {
      GuiPanel.mainFrame.close();
    }

    GuiPanel.errorCount = 0;
    GuiPanel.threadCount = 0;
    GuiPanel.elapsedStart = 0;
    GuiPanel.elapsedMode = ElapsedMode.OFF;
    
    GuiPanel.graphMode = CallGraph.GraphHighlight.NONE;
    GuiPanel.graphShowAllThreads = true;
    String ipaddr = "<unknown>";
    // get this server's ip address
    try(final DatagramSocket socket = new DatagramSocket()){
      socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
      ipaddr = socket.getLocalAddress().getHostAddress();
    } catch (SocketException | UnknownHostException ex) {  }
    GuiPanel.serverPort = "Server port (" + (tcp ? "TCP" : "UDP") + ")  -  " + ipaddr + ":" + port;
    
    // these just make the gui entries cleaner
    String panel;
    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
    GuiControls.Orient CENTER = GuiControls.Orient.CENTER;
    GuiControls.Orient RIGHT = GuiControls.Orient.RIGHT;
    
    // create the frame
    mainFrame.newFrame("dandebug", 1200, 800, GuiControls.FrameSize.FULLSCREEN);

    // create the entries in the main frame
    panel = null;
    mainFrame.makePanel (panel, "PNL_CONTAINER1", LEFT, false, "");
    mainFrame.makePanel (panel, "PNL_HIGHLIGHT" , LEFT, false, "Graph Highlighting");
    mainFrame.makePanel (panel, "PNL_CONTAINER2", LEFT, true , "");
    mainFrame.makePanel (panel, "PNL_MESSAGES"  , LEFT, true , "Messages");
    mainFrame.makeTabbedPanel(panel, "PNL_TABBED");

    panel = "PNL_CONTAINER1";
    mainFrame.makePanel (panel, "PNL_STATS"    , LEFT, false, "Statistics");
    mainFrame.makePanel (panel, "PNL_CONTROL"  , LEFT, false, "Controls");

    panel = "PNL_CONTAINER2";
    mainFrame.makePanel (panel, "PNL_THREAD"   , LEFT, true , "Thread Select");
    mainFrame.makeLabel (panel, "LBL_3"        , LEFT, true , " "); // dummy seperator
    mainFrame.makePanel (panel, "PNL_RANGE"    , LEFT, true , "Highlight Range");
    mainFrame.makeLabel (panel, "LBL_4"        , LEFT, true , " "); // dummy seperator
    
    panel = "PNL_STATS";
    mainFrame.makeLabel      (panel, "LBL_ELAPSED"   , LEFT,  false , "Elapsed");
    mainFrame.makeTextField  (panel, "FIELD_ELAPSED" , LEFT,  false, "00:00", 6, false);
    mainFrame.makeLabel      (panel, "LBL_1"         , RIGHT, true, " ");
    mainFrame.makeLabel      (panel, "LBL_2"         , LEFT,  true, " "); // dummy seperator
    mainFrame.makeLabel      (panel, "LBL_ERROR"     , LEFT,  false , "Errors");
    mainFrame.makeTextField  (panel, "TXT_ERROR"     , LEFT,  false, "------", 6, false);
    mainFrame.makeLabel      (panel, "LBL_PROCESSED" , LEFT,  false , "Processed");
    mainFrame.makeTextField  (panel, "TXT_PROCESSED" , LEFT,  true , "------", 6, false);
    mainFrame.makeLabel      (panel, "LBL_PKTSREAD"  , LEFT,  false , "Lines Read");
    mainFrame.makeTextField  (panel, "TXT_PKTSREAD"  , LEFT,  false, "------", 6, false);
    mainFrame.makeLabel      (panel, "LBL_METHODS"   , LEFT,  false , "Methods");
    mainFrame.makeTextField  (panel, "TXT_METHODS"   , LEFT,  true , "------", 6, false);
    mainFrame.makeLabel      (panel, "LBL_PKTSLOST"  , LEFT,  false , "Lines Lost");
    mainFrame.makeTextField  (panel, "TXT_PKTSLOST"  , LEFT,  false, "------", 6, false);
    mainFrame.makeLabel      (panel, "LBL_THREADS"   , LEFT,  false , "Threads");
    mainFrame.makeTextField  (panel, "TXT_THREADS"   , LEFT,  true , "------", 6, false);

    panel = "PNL_CONTROL";
    mainFrame.makeButton     (panel, "BTN_LOADFILE"  , LEFT, false, "Load File");
    mainFrame.makeButton     (panel, "BTN_LOGFILE"   , LEFT, true , "Set Logfile");
    mainFrame.makeButton     (panel, "BTN_LOADGRAPH" , LEFT, false, "Load Graph");
    mainFrame.makeButton     (panel, "BTN_CLEAR"     , LEFT, true , "Clear Log");
    mainFrame.makeButton     (panel, "BTN_SAVEGRAPH" , LEFT, false, "Save Graph");
    mainFrame.makeButton     (panel, "BTN_ERASEFILE" , LEFT, true , "Erase Log");
    mainFrame.makeGap        (panel, 20);
    mainFrame.makeButton     (panel, "BTN_PAUSE"     , LEFT, true , "Pause");
    mainFrame.makeCheckbox   (panel, "BTN_GRAPHTYPE" , LEFT, true , "Save only methods for graph", 0);
    mainFrame.makeCheckbox   (panel, "BTN_AUTORESET" , LEFT, true , "Auto-Reset on Start", 1);

    panel = "PNL_HIGHLIGHT";
    mainFrame.makeRadiobutton(panel, "RB_THREAD"     , LEFT, true, "Thread", 0);
    mainFrame.makeRadiobutton(panel, "RB_ELAPSED"    , LEFT, true, "Elapsed Time", 0);
    mainFrame.makeRadiobutton(panel, "RB_INSTRUCT"   , LEFT, true, "Instructions", 0);
    mainFrame.makeRadiobutton(panel, "RB_ITER"       , LEFT, true, "Iterations Used", 0);
    mainFrame.makeRadiobutton(panel, "RB_STATUS"     , LEFT, true, "Status", 0);
    mainFrame.makeRadiobutton(panel, "RB_NONE"       , LEFT, true, "Off", 1);

    panel = "PNL_THREAD";
    mainFrame.makeCheckbox   (panel, "CB_SHOW_ALL"   , LEFT, true, "Show all threads", 1);
    mainFrame.makeLabel      (panel, "TXT_TH_SEL"    , LEFT, false, "0");
    mainFrame.makeButton     (panel, "BTN_TH_UP"     , LEFT, false, "UP");
    mainFrame.makeButton     (panel, "BTN_TH_DN"     , LEFT, true, "DN");
    
    panel = "PNL_RANGE";
    mainFrame.makeLabel      (panel, "TXT_RANGE"     , LEFT, false, "20");
    mainFrame.makeButton     (panel, "BTN_RG_UP"     , LEFT, false, "UP");
    mainFrame.makeButton     (panel, "BTN_RG_DN"     , LEFT, true, "DN");

    panel = "PNL_MESSAGES";
    mainFrame.makeLabel  (panel, "LBL_CONNECTION"    , LEFT, true, GuiPanel.serverPort);
    mainFrame.makeLabel  (panel, "LBL_MESSAGES"      , LEFT, true, "                    ");

    // init thread selection in highlighting to OFF
    setThreadEnabled(false);
    
    // disable the Save Graph button (until we actually have a graph)
    GuiPanel.mainFrame.getButton("BTN_SAVEGRAPH").setEnabled(false);
    
    // TODO: for now, disable the All threads checkbox so it is always selected
    JCheckBox cb = GuiPanel.mainFrame.getCheckbox("CB_SHOW_ALL");
    cb.setEnabled(false);
    
    // we need a filechooser for the Save buttons
    GuiPanel.fileSelector = new JFileChooser();

    // init current tab selection to 1st entry
    currentTab = PanelTabs.DEBUG.toString();

    // create the debug logger and the call graph
    debugLogger = new DebugLogger(PanelTabs.DEBUG.toString());
    callGraph = new CallGraph(PanelTabs.CALLGRAPH.toString());

    // add the tabbed message panels and a listener to detect when a tab has been selected
    tabIndex = 0;
    GuiControls.PanelInfo panelInfo = mainFrame.getPanelInfo("PNL_TABBED");
    if (panelInfo != null && panelInfo.panel instanceof JTabbedPane) {
      tabPanel = (JTabbedPane) panelInfo.panel;
      addPanelToTab(tabPanel, PanelTabs.DEBUG, debugLogger.getScrollPanel());
      addPanelToTab(tabPanel, PanelTabs.CALLGRAPH, callGraph.getScrollPanel());
    }
    
    // activate the panels
    debugLogger.activate();
    callGraph.activate();

    // setup the control actions
    GuiPanel.mainFrame.getFrame().addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        formWindowClosing(evt);
      }
    });
    GuiPanel.tabPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        int curTab = tabPanel.getSelectedIndex();
        currentTab = tabPanel.getTitleAt(curTab);

        // now inform panels of the selection
        debugLogger.setTabSelection(currentTab);
        callGraph.setTabSelection(currentTab);

        // if we switched to the graph display tab, update the graph
        if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
          if (callGraph.updateCallGraph(graphMode, false)) {
            GuiPanel.mainFrame.repack();
          }
          
          // if we captured any call graph info, we can now enable the Save Graph button
          if (callGraph.getMethodCount() > 0) {
            GuiPanel.mainFrame.getButton("BTN_SAVEGRAPH").setEnabled(true);
          }
        }
      }
    });
    (GuiPanel.mainFrame.getCheckbox("CB_SHOW_ALL")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
          JCheckBox cb = GuiPanel.mainFrame.getCheckbox("CB_SHOW_ALL");
          int threadid;
          if (cb.isSelected()) {
            GuiPanel.graphShowAllThreads = true;
            threadid = -1;
          } else {
            GuiPanel.graphShowAllThreads = false;
            JLabel label = mainFrame.getLabel("TXT_TH_SEL");
            threadid = Integer.parseInt(label.getText().trim());
          }

          redrawCallGraph(threadid);
        }
      }
    });
    (GuiPanel.mainFrame.getRadiobutton("RB_THREAD")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(CallGraph.GraphHighlight.THREAD);
        
        // enable the thread controls when this is selected
        setThreadEnabled(true);
      }
    });
    (GuiPanel.mainFrame.getRadiobutton("RB_ELAPSED")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(CallGraph.GraphHighlight.TIME);
      }
    });
    (GuiPanel.mainFrame.getRadiobutton("RB_INSTRUCT")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(CallGraph.GraphHighlight.INSTRUCTION);
      }
    });
    (GuiPanel.mainFrame.getRadiobutton("RB_ITER")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(CallGraph.GraphHighlight.ITERATION);
      }
    });
    (GuiPanel.mainFrame.getRadiobutton("RB_STATUS")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(CallGraph.GraphHighlight.STATUS);
      }
    });
    (GuiPanel.mainFrame.getRadiobutton("RB_NONE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setHighlightMode(CallGraph.GraphHighlight.NONE);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_SAVEGRAPH")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        boolean onlyMethods = mainFrame.getCheckbox("BTN_GRAPHTYPE").isSelected();
        saveGraphButtonActionPerformed(evt, onlyMethods);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_LOADGRAPH")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // reset the error count
        GuiPanel.errorCount = 0;

        // force the highlight selection back to NONE
        resetHighlightMode();

        // load the file & get the statistics
        GuiPanel.threadCount = loadGraphButtonActionPerformed(evt);

        // enable the thread highlighting controls if we have more than 1 thread
        if (GuiPanel.threadCount > 1) {
          setThreadEnabled(true);
        }
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_LOADFILE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        // reset the error count
        GuiPanel.errorCount = 0;

        // reset the number of threads found
        GuiPanel.threadCount = 0;
        GuiPanel.threadList.clear();

        // force the highlight selection back to NONE
        resetHighlightMode();

        // load the file & get the statistics
        loadFileButtonActionPerformed(evt);

        // enable the thread highlighting controls if we have more than 1 thread
        if (GuiPanel.threadCount > 1) {
          setThreadEnabled(true);
        }
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_ERASEFILE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        eraseStorageButtonActionPerformed(evt);
        // disable save graph button, since there is no longer any graph data
        GuiPanel.mainFrame.getButton("BTN_SAVEGRAPH").setEnabled(false);

        // reset the error count
        GuiPanel.errorCount = 0;

        // reset the number of threads found
        GuiPanel.threadCount = 0;
        GuiPanel.threadList.clear();

        // force the highlight selection back to NONE
        setHighlightMode(CallGraph.GraphHighlight.NONE);

        // init thread selection in highlighting to OFF
        setThreadEnabled(false);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_LOGFILE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setStorageButtonActionPerformed(evt);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_CLEAR")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        udpThread.resetInput();
        resetCapturedInput();
        // disable save graph button, since there is no longer any graph data
        GuiPanel.mainFrame.getButton("BTN_SAVEGRAPH").setEnabled(false);

        // reset the error count
        GuiPanel.errorCount = 0;

        // reset the number of threads found
        GuiPanel.threadCount = 0;
        GuiPanel.threadList.clear();
        
        // force the highlight selection back to NONE
        setHighlightMode(CallGraph.GraphHighlight.NONE);

        // init thread selection in highlighting to OFF
        setThreadEnabled(false);
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_TH_UP")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = mainFrame.getLabel("TXT_TH_SEL");
        int value = Integer.parseInt(label.getText().trim());
        if (value < GuiPanel.threadCount - 1) {
          value++;
          label.setText("" + value);
          
          callGraph.setThreadSelection(value);

          // if CallGraph is selected, update the graph
          if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
            JCheckBox showall = GuiPanel.mainFrame.getCheckbox("CB_SHOW_ALL");
            JRadioButton threadMode = GuiPanel.mainFrame.getRadiobutton("RB_THREAD");
            if (showall.isSelected()) {
              callGraph.updateCallGraph(CallGraph.GraphHighlight.THREAD, true);
            } else if (threadMode.isSelected()) {
              redrawCallGraph(value);
            }
          }
        }
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_TH_DN")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = mainFrame.getLabel("TXT_TH_SEL");
        int value = Integer.parseInt(label.getText().trim());
        if (value > 0) {
          value--;
          label.setText("" + value);
          
          callGraph.setThreadSelection(value);

          // if CallGraph is selected, update the graph
          if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
            JCheckBox showall = GuiPanel.mainFrame.getCheckbox("CB_SHOW_ALL");
            JRadioButton threadMode = GuiPanel.mainFrame.getRadiobutton("RB_THREAD");
            if (showall.isSelected()) {
              callGraph.updateCallGraph(CallGraph.GraphHighlight.THREAD, true);
            } else if (threadMode.isSelected()) {
              redrawCallGraph(value);
            }
          }
        }
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_RG_UP")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = mainFrame.getLabel("TXT_RANGE");
        int step = Integer.parseInt(label.getText().trim());
        if (step < 20) {
          step++;
          label.setText("" + step);
          
          callGraph.setRangeStepSize(step);

          // if CallGraph is selected, update the graph
          if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
            callGraph.updateCallGraph(GuiPanel.graphMode, true);
          }
        }
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_RG_DN")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JLabel label = mainFrame.getLabel("TXT_RANGE");
        int step = Integer.parseInt(label.getText().trim());
        if (step > 1) {
          step--;
          label.setText("" + step);
          
          callGraph.setRangeStepSize(step);

          // if CallGraph is selected, update the graph
          if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
            callGraph.updateCallGraph(GuiPanel.graphMode, true);
          }
        }
      }
    });
    (GuiPanel.mainFrame.getButton("BTN_PAUSE")).addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        JButton pauseButton = GuiPanel.mainFrame.getButton("BTN_PAUSE");
        if (pauseButton.getText().equals("Pause")) {
          enableUpdateTimers(false);
          pauseButton.setText("Resume");
        } else {
          enableUpdateTimers(true);
          pauseButton.setText("Pause");
        }
      }
    });

    // display the frame
    GuiPanel.mainFrame.display();

    // check for a properties file
    props = new PropertiesFile(PROPERTIES_PATH, PROPERTIES_FILE);
    String logfileName = props.getPropertiesItem("LogFile", "");
    if (logfileName.isEmpty()) {
      logfileName = System.getProperty("user.dir") + "/debug.log";
    }
    GuiPanel.fileSelector.setCurrentDirectory(new File(logfileName));

    // start the TCP or UDP listener thread
    try {
      GuiPanel.udpThread = new NetworkServer(port, tcp, makeConnection);
      GuiPanel.listener = GuiPanel.udpThread;
      GuiPanel.udpThread.setStorageFile(logfileName);
      GuiPanel.udpThread.start();
      GuiPanel.mainFrame.getLabel("LBL_MESSAGES").setText("Logfile: " + udpThread.getOutputFile());
    } catch (IOException ex) {
      System.out.println(ex.getMessage());
      System.exit(1);
    }

    // create a timer for reading and displaying the messages received (from either network or file)
    inputListener = new DebugInputListener();
    pktTimer = new Timer(1, inputListener);
    pktTimer.start();

    // create a slow timer for updating the call graph
    graphTimer = new Timer(1000, new GraphUpdateListener());
    graphTimer.start();

    // create a timer for updating the statistics
    statsTimer = new Timer(100, new StatsUpdateListener());
    statsTimer.start();

    // start timer when 1st line is received from port
    GuiPanel.elapsedMode = ElapsedMode.RESET;
  }

  private void addPanelToTab(JTabbedPane tabpane, PanelTabs tabname, Component panel) {
    // make sure we don't already have the entry
    if (tabSelect.containsKey(tabname)) {
      System.err.println("ERROR: '" + tabname + "' panel already defined in tabs");
      System.exit(1);
    }
    
    // now add the scroll pane to the tabbed pane
    tabpane.addTab(tabname.toString(), panel);
    
    // save access to text panel by name
    tabSelect.put(tabname, tabIndex++);
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
  
  private static void resetHighlightMode() {
    // reset mode to disable all highlighting
    GuiPanel.graphMode = CallGraph.GraphHighlight.NONE;
    setHighlightMode(GuiPanel.graphMode);

    // reset mode to show all threads
    GuiPanel.graphShowAllThreads = true;
    JCheckBox cb = GuiPanel.mainFrame.getCheckbox("CB_SHOW_ALL");
    cb.setSelected(GuiPanel.graphShowAllThreads);
  }  
  
  private static void setThreadEnabled(boolean enabled) {
    JRadioButton button = mainFrame.getRadiobutton("RB_THREAD");
    button.setEnabled(enabled);
    if (enabled) {
      enabled = button.isSelected();
    }

    JLabel label = mainFrame.getLabel ("TXT_TH_SEL");
    label.setText("0");

    label.setEnabled(enabled);
    mainFrame.getButton("BTN_TH_UP").setEnabled(enabled);
    mainFrame.getButton("BTN_TH_DN").setEnabled(enabled);
  }
  
  private static void startElapsedTime() {
    GuiPanel.elapsedStart = System.currentTimeMillis();
    GuiPanel.elapsedMode = ElapsedMode.RUN;
    GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void resetElapsedTime() {
    GuiPanel.elapsedStart = 0;
    GuiPanel.elapsedMode = ElapsedMode.RESET;
    GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText("00:00");
  }
  
  private static void updateElapsedTime() {
    if (GuiPanel.elapsedMode == ElapsedMode.RUN) {
      long elapsed = System.currentTimeMillis() - GuiPanel.elapsedStart;
      if (elapsed > 0) {
        Integer msec = (int)(elapsed % 1000);
        elapsed = elapsed / 1000;
        Integer secs = (int)(elapsed % 60);
        Integer mins = (int)(elapsed / 60);
        String timestamp = ((mins < 10) ? "0" : "") + mins.toString() + ":" +
                           ((secs < 10) ? "0" : "") + secs.toString(); // + "." +
                           //((msec < 10) ? "00" : (msec < 100) ? "0" : "") + msec.toString();
        GuiPanel.mainFrame.getTextField("FIELD_ELAPSED").setText(timestamp);
      }
    }
  }
  
  private static void enableUpdateTimers(boolean enable) {
    if (enable) {
      if (pktTimer != null) {
        pktTimer.start();
      }
      if (graphTimer != null) {
        graphTimer.start();
      }
    } else {
      if (pktTimer != null) {
        pktTimer.stop();
      }
      if (graphTimer != null) {
        graphTimer.stop();
      }
    }
  }
  
  private static void setHighlightMode(CallGraph.GraphHighlight mode) {
    JRadioButton threadSelBtn = GuiPanel.mainFrame.getRadiobutton("RB_THREAD");
    JRadioButton timeSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_ELAPSED");
    JRadioButton instrSelBtn  = GuiPanel.mainFrame.getRadiobutton("RB_INSTRUCT");
    JRadioButton iterSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_ITER");
    JRadioButton statSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_STATUS");
    JRadioButton noneSelBtn   = GuiPanel.mainFrame.getRadiobutton("RB_NONE");

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
        JLabel label = mainFrame.getLabel("TXT_TH_SEL");
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
  
  private class DebugInputListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // read & process next message
      if (udpThread != null) {
        String message = udpThread.getNextMessage();
        if (message != null) {
          String methCall = debugLogger.processMessage(message, callGraph);

//          mainFrame.getTextField("TXT_CLASSES").setText("" + callGraph.getClassCount());
          mainFrame.getTextField("TXT_METHODS").setText("" + callGraph.getMethodCount());
          
          // update the thread count display
          int threads = debugLogger.getThreadCount();
          if (threads > 1) {
            setThreadEnabled(true);
          }

          if (GuiPanel.elapsedMode == ElapsedMode.RESET && threads > 0) {
            startElapsedTime();
          }
        }
      }
    }
  }

  private class StatsUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // update statistics
      int methods = callGraph.getMethodCount();
      
      (GuiPanel.mainFrame.getTextField("TXT_ERROR")).setText("" + debugLogger.getErrorCount());
      (GuiPanel.mainFrame.getTextField("TXT_PKTSREAD")).setText("" + udpThread.getPktsRead());
      (GuiPanel.mainFrame.getTextField("TXT_PKTSLOST")).setText("" + udpThread.getPktsLost());
      (GuiPanel.mainFrame.getTextField("TXT_PROCESSED")).setText("" + debugLogger.getLinesRead());
      (GuiPanel.mainFrame.getTextField("TXT_METHODS")).setText("" + methods);
      (GuiPanel.mainFrame.getTextField("TXT_THREADS")).setText("" + debugLogger.getThreadCount());

      JButton savegraph = GuiPanel.mainFrame.getButton("BTN_SAVEGRAPH");
      if (methods > 0 && !savegraph.isEnabled()) {
        savegraph.setEnabled(true);
      }
          
      // update elapsed time if enabled
      updateElapsedTime();
    }
  }

  private class GraphUpdateListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // if Call Graph tab selected, update graph
      if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
        if (callGraph.updateCallGraph(graphMode, false)) {
          GuiPanel.mainFrame.repack();
        }
      }
    }
  }

  private static void eraseStorageButtonActionPerformed(java.awt.event.ActionEvent evt) {
    resetInputAndFile();
  }

  private static void setStorageButtonActionPerformed(java.awt.event.ActionEvent evt) {
    FileNameExtensionFilter filter = new FileNameExtensionFilter("Log Files", "log");
    GuiPanel.fileSelector.setFileFilter(filter);
    GuiPanel.fileSelector.setSelectedFile(new File("debug.log"));
    GuiPanel.fileSelector.setMultiSelectionEnabled(false);
    GuiPanel.fileSelector.setApproveButtonText("Set");
    int retVal = GuiPanel.fileSelector.showOpenDialog(GuiPanel.mainFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      // stop the timers from updating the display
      enableUpdateTimers(false);

      // shut down the port input and specify the new name
      File file = GuiPanel.fileSelector.getSelectedFile();
      String fname = file.getAbsolutePath();
      udpThread.setStorageFile(fname);
      
      // display the new log file location & save it
      GuiPanel.mainFrame.getLabel("LBL_MESSAGES").setText("Logfile: " + fname);
      props.setPropertiesItem("LogFile", fname);

      // now restart the update timers
      enableUpdateTimers(true);
    }
  }
  
  private static void loadFileButtonActionPerformed(java.awt.event.ActionEvent evt) {
    FileNameExtensionFilter filter = new FileNameExtensionFilter("Log Files", "txt", "log");
    GuiPanel.fileSelector.setFileFilter(filter);
    GuiPanel.fileSelector.setSelectedFile(new File("debug.log"));
    GuiPanel.fileSelector.setMultiSelectionEnabled(false);
    GuiPanel.fileSelector.setApproveButtonText("Load");
    int retVal = GuiPanel.fileSelector.showOpenDialog(GuiPanel.mainFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      // stop the timers from updating the display
      enableUpdateTimers(false);

      // clear the current display
      resetCapturedInput();
      
      // read the file
//      File file = GuiPanel.fileSelector.getSelectedFile();
//      mainFrame.getLabel("LBL_MESSAGES").setText("read from file: " + file.getName());
//      try {
//        String message;
//        BufferedReader in = new BufferedReader(new FileReader(file));
//        while ((message = in.readLine()) != null) {
//          processMessage(fileLogger, message);
//        }
//      } catch (IOException ex) {
//        mainFrame.getLabel("LBL_MESSAGES").setText("ERROR: reading from file: " + file.getName());
//        System.out.println(ex.getMessage());
//      }
//      
//      // update the graphics (if enabled)
//      if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
//        if (callGraph.updateCallGraph(graphMode, false)) {
//          GuiPanel.mainFrame.repack();
//          (GuiPanel.mainFrame.getTextField("TXT_METHODS")).setText("" + callGraph.getMethodCount());
//          (GuiPanel.mainFrame.getTextField("TXT_THREADS")).setText("" + GuiPanel.threadCount);
//        }
//      }
      
      // now restart the update timers
      enableUpdateTimers(true);
    }
  }
  
  private static int loadGraphButtonActionPerformed(java.awt.event.ActionEvent evt) {
    int threads = 0;
    String defaultName = "callgraph";
    FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON Files", "json");
    GuiPanel.fileSelector.setFileFilter(filter);
    GuiPanel.fileSelector.setSelectedFile(new File(defaultName + ".json"));
    GuiPanel.fileSelector.setMultiSelectionEnabled(false);
    GuiPanel.fileSelector.setApproveButtonText("Load");
    int retVal = GuiPanel.fileSelector.showOpenDialog(GuiPanel.mainFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      File file = GuiPanel.fileSelector.getSelectedFile();

      // stop the timers from updating the display
      enableUpdateTimers(false);

      // clear the current display
      resetCapturedInput();
      
      // create call graph from file
      threads = callGraph.loadFromJSONFile(file);
      mainFrame.getLabel("LBL_MESSAGES").setText("read from graph: " + file.getName());

      // now restart the update timers
      enableUpdateTimers(true);
    }
    return threads;
  }

  private static void saveGraphButtonActionPerformed(java.awt.event.ActionEvent evt, boolean onlyMethods) {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-");
    Date date = new Date();
    String threadsel = "";
    if (GuiPanel.threadCount > 1) {
      if (GuiPanel.graphShowAllThreads == true) {
        threadsel = "all-threads-";
      } else {
        JLabel label = mainFrame.getLabel("TXT_TH_SEL");
        int value = Integer.parseInt(label.getText().trim());
        threadsel = "thread" + value + "-";
      }
    }
    String defaultName = dateFormat.format(date) + threadsel + "callgraph";
    FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON Files", "json");
    GuiPanel.fileSelector.setFileFilter(filter);
    GuiPanel.fileSelector.setApproveButtonText("Save");
    GuiPanel.fileSelector.setMultiSelectionEnabled(false);
    GuiPanel.fileSelector.setSelectedFile(new File(defaultName + ".json"));
    int retVal = GuiPanel.fileSelector.showOpenDialog(GuiPanel.mainFrame.getFrame());
    if (retVal == JFileChooser.APPROVE_OPTION) {
      File file = GuiPanel.fileSelector.getSelectedFile();
      String basename = file.getAbsolutePath();
      
      // get the base name without extension so we can create matching json and png files
      int offset = basename.lastIndexOf('.');
      if (offset > 0) {
        basename = basename.substring(0, offset);
      }

      // remove any pre-existing file and convert method list to json file
      File graphFile = new File(basename + ".json");
      graphFile.delete();
      if (onlyMethods) {
        callGraph.saveAsCompFile(graphFile);
      } else {
        callGraph.saveAsJSONFile(graphFile, graphShowAllThreads);
      }

      // remove any pre-existing file and save image as png file
      File pngFile = new File(basename + ".png");
      pngFile.delete();
      callGraph.saveAsImageFile(pngFile);
    }
  }
  
  private static void redrawCallGraph(int threadid) {
    // stop the timers from updating the display
    enableUpdateTimers(false);

    // clear the current display & redraw
    callGraph.updateCallGraph(graphMode, true);
        //.generateCallGraph(threadid);
        
    // now restart the update timers
    enableUpdateTimers(true);

    // enable the thread highlighting controls if we have more than 1 thread
//    if (threadid < 0 && GuiPanel.threadCount > 1) {
//      setThreadEnabled(true);
//    } else {
//      setThreadEnabled(false);
//    }
  }
  
  private static void formWindowClosing(java.awt.event.WindowEvent evt) {
    if (graphTimer != null) {
      graphTimer.stop();
    }
    if (pktTimer != null) {
      pktTimer.stop();
    }
    if (statsTimer != null) {
      statsTimer.stop();
    }
    if (listener != null) {
      listener.exit();
    }
    if (udpThread != null) {
      udpThread.exit();
    }
    mainFrame.close();
    System.exit(0);
  }

  private static void resetCapturedInput() {
    // clear the packet buffer and statistics
    udpThread.clear();

    // clear the graphics panel
    callGraph.clearGraphAndMethodList();
    if (currentTab.equals(PanelTabs.CALLGRAPH.toString())) {
      callGraph.updateCallGraph(CallGraph.GraphHighlight.NONE, false);
    }
    
    // clear the stats
    GuiPanel.linesRead = 0;
    (GuiPanel.mainFrame.getTextField("TXT_ERROR")).setText("------");
    (GuiPanel.mainFrame.getTextField("TXT_PKTSREAD")).setText("------");
    (GuiPanel.mainFrame.getTextField("TXT_PKTSLOST")).setText("------");
    (GuiPanel.mainFrame.getTextField("TXT_PROCESSED")).setText("------");
    (GuiPanel.mainFrame.getTextField("TXT_METHODS")).setText("------");
    (GuiPanel.mainFrame.getTextField("TXT_THREADS")).setText("------");

    // reset the Pause/Resume button to indicate we are no longer paused
    JButton pauseButton = GuiPanel.mainFrame.getButton("BTN_PAUSE");
    pauseButton.setText("Pause");
          
    // reset the elapsed time
    GuiPanel.resetElapsedTime();
  }

  private static void resetInputAndFile() {
    // stop the timers from updating the display
    enableUpdateTimers(false);

    // erase the current file selection
    udpThread.eraseBufferFile();
    
    // clear the display and buffers
    resetCapturedInput();
      
    // now restart the update timers
    enableUpdateTimers(true);
  }

}
