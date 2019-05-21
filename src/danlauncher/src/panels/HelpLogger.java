/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import gui.GuiControls;
import logging.FontInfo;
import logging.Logger;
import util.Utils;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;

/**
 *
 * @author dan
 */
public class HelpLogger {

  // types of messages
  private enum MsgType { TITLE, HEADER1, HEADER2, HEADER3, NORMAL, EMPHASIS, HYPERTEXT }

  private static JTextPane       panel;
  private static JScrollPane     scrollPanel;
  private static JFrame          helpFrame;
  private static GuiControls     gui;
  private static Logger          logger;
  private static String          tabName;
  private static boolean         tabSelected;
  private static String          curPage;
  private static List<MetadataMap> metaMap = new ArrayList<>();
  private static Stack<String>    backPages = new Stack<>();
  private static HashMap<String, FontInfo> fontmap = new HashMap<>();
  
  private class MetadataMap {
    public MsgType type;    // type of meta field
    public String  value;   // meta field entry (name of hypertext of emphasis entry)
    public int     offset;  // char offset to metadata field
    
    public MetadataMap(MsgType type, String field, int offset) {
      this.type = type;
      this.value = field;
      this.offset = offset;
    }
  }
  
  public HelpLogger(String name, GuiControls guicontrols) {
    tabSelected = false;
    tabName = name;
    curPage = "";
    gui = guicontrols;
    helpFrame = gui.newFrame(name, 700, 800, GuiControls.FrameSize.NOLIMIT);
    helpFrame.pack();
    helpFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    helpFrame.setLocationRelativeTo(null);
    helpFrame.addWindowListener(new Window_MainListener());

    String fonttype = "Courier";
    FontInfo.setTypeColor (fontmap, MsgType.TITLE.toString(),     FontInfo.TextColor.DkVio, FontInfo.FontType.BoldItalic, 20, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER1.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.Bold, 18, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER2.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.Bold, 16, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HEADER3.toString(),   FontInfo.TextColor.Black, FontInfo.FontType.BoldItalic, 15, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.NORMAL.toString(),    FontInfo.TextColor.Black, FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.EMPHASIS.toString(),  FontInfo.TextColor.DkGrey, FontInfo.FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.HYPERTEXT.toString(), FontInfo.TextColor.Blue,  FontInfo.FontType.Bold, 14, fonttype);

    // create the text panel and assign it to the logger
    logger = new Logger(name, Logger.PanelType.TEXTPANE, true, fontmap);
    panel = (JTextPane) logger.getTextPanel();
    scrollPanel = logger.getScrollPanel();
    if (scrollPanel == null) {
      Utils.printStatusError("HelpLogger failed to create a Scroll Pane");
      System.exit(1);
    }

    // add the scroll panel to the frame and a listener to the scroll bar
    gui.addPanelToPanel(null, scrollPanel);
    gui.setGridBagLayout(null, scrollPanel, GuiControls.Orient.NONE, true, GuiControls.Expand.BOTH);
    scrollPanel.getVerticalScrollBar().addAdjustmentListener(new HelpScrollListener());
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  public void activate() {
    // add mouse & key listeners for the bytecode viewer
    panel.addMouseListener(new HelpMouseListener());
    panel.addKeyListener(new HelpKeyListener());
  }
  
  public void clear() {
    logger.clear();
  }

  public void printHelpPanel(String select) {
    // make panel writable, erase current data, then write the selected header
    panel.setEditable(true);
    clear();
    metaMap.clear();
    curPage = select;
    
    // write the context-specific help messages
    switch(select) {
      case "INTRO":
        printInitialTitle("Introduction");
        printParagraph("This program allows the user to instrument and run a Java application using " +
                       "the ^^danalyzer^^ DSE (Dynamic Symbolic Execution). It provides an interface for " +
                       "allowing the user to specify the local parameters to make symbolic so that the " +
                       "DSE can track the operations performed on them in the shadow stack it maintains. " +
                       "As the program runs, the DSE builds up a list of constraints for the symbolic parameters, " +
                       "which are sent to the dansolver. This program determines whether the constraints are " +
                       "solvable and generates solutions for those that are. It places all the information for the constraints " +
                       "along with the solutions in a database (mongoDB). These results are read from the database " +
                       "by danlauncher and displayed for the user.");
        printParagraph("Note that the user must setup the configuration prior to running an application. " +
                       "This is done by selecting ^^Config^^ in the Menu and then ^^System Configuration^^. " +
                       "The most important selections that must be configured are the ^^JAVA_HOME^^ and the " +
                       "^^DSE_PATH^^ selections to specify where the JDK and the DSE repo are located.");
        printLinefeed();
        printParagraph("Other help page links:");
        printParagraph("<<GENERAL>>   - General Operation");
        printParagraph("<<CONFIG>>    - Configuration Panels (System, Debug, and CallGraph)");
        printParagraph("<<STATUS>>    - Status Panel");
        printParagraph("<<CONTROLS>>  - Control and Bytecode Panels");
        printParagraph("<<SYMBOLICS>> - Symbolic Parameters Panel");
        printLinefeed();
        printParagraph("Info pages for the tabs at the bottom:");
        printParagraph("<<SOLUTIONS>> - Displays the database information from the solver");
        printParagraph("<<BYTECODE>>  - Displays the bytecode and local parameters for the selected method");
        printParagraph("<<BYTEFLOW>>  - Displays the bytecode as a graphical flowchart");
        printParagraph("<<LOG>>       - Displays the captured log information");
        printParagraph("<<CALLGRAPH>> - Displays the call graph for the running application (when enabled)");
        printParagraph("<<XPLOREGRAPH>> - Displays the loaded call graph (used for exploring new paths)");
        break;
        
      case "GENERAL":
        printTitle("General Operation");
        printParagraph("The work flow should be something like the following:");
        printParagraph("1. Run ^^janalyzer^^ on the application to generate the Call Graph of the methods " +
                       "and isolate potential vulnerabilities in the code based on nested loops and allocations. " +
                       "The Call Graph can then be exported by saving to a JSON-formatted text file.");
        printParagraph("2. Run ^^dansolver^^ that runs as a server for handling TCP messages from danlauncher " +
                       "containing the constraints to be solved, solves them, and then places them in a " +
                       "database that danlauncher can access. Note that ^^mongoDB^^ is the database used and " +
                       "must be started (by typing the command 'mongod') prior to starting either dansolver or " +
                       "danlauncher, since both programs must attach to it in order to find solutions.");
        printParagraph("3. Run ^^danlauncher^^ (this program) and select the application to run. You do this by selecting the " +
                       "entry (Load jar file) from the Project menu selection. This will generate the " +
                       "instrumented jar file that can then be run with symbolic parameters enabled. This " +
                       "will then enable the entries in the ^^Controls^^ panel that allow you to run the " +
                       "application and solve any constraints.");
        printParagraph("4. Specify the Main Class of the program in the ^^Controls^^ panel that is the entry " +
                       "point for the application. This is also when you can specify additional configuration " +
                       "parameters that are project-specific. These can be found in the menu ^^Config^^ under " +
                       "the headings ^^Project Configuration^^ and ^^Debug Configuration^^. The Debug settings " +
                       "specify what data is output to the <<LOG>> as well as whether a Call Graph is generated.");
        printParagraph("5. (Optional) Load the JSON Call Graph file created by janalyzer, which can then be viewed " +
                       "in the XPLOREGRAPH tab. Search for the methods of interest that were indicated by " +
                       "janalyzer and click on the specific method block that you are interested in. " +
                       "This will bring up the bytecode for the specified method, along with the local " +
                       "parameters for that method. It usually also helps to have run the decompiler from " +
                       "janalyzer to try to find the corresponding section in the source code.");
        printParagraph("6. Select which local parameter(s) would be useful to make symbolic so the DSE " +
                       "will generate constraints for those parameters that can be solved by dansolver.");
        printParagraph("7. The instrumented code is now ready to be run. Specify the arguments required by " +
                       "the application in the text area to the right of the STOP button in the <<CONTROLS>> panel, " +
                       "then press the ^^RUN^^ button to begin execution. The ^^STOP^^ button can be used to " +
                       "terminate execution when needed. Command progress is displayed in the COMMAND tab.");
        printParagraph("8. When the application has completed, or has run for an appropriate amount of " +
                       "time, you can click on the <<SOLUTIONS>> tab to view the progress it has made on " +
                       "solving the constraints for the symbolic paramaters. The user can then stop the " +
                       "current execution and select a solution that will take the code down a different " +
                       "branch path in the hopes of finding a costlier solution. By clicking on the method " +
                       "column of the solutions list, the bytecode for the selected method will be displayed " +
                       "with 2 pink highlighted columns indicating the branch corresponding to the constraint. " +
                       "This can be used to help determine which branch path to explore. The <<BYTEFLOW>> tab " +
                       "can also be viewed that presents the bytecode in a flowwchart disgram, which is " +
                       "sometimes easier to follow.");
        printLinefeed();
        printParagraph("While the application is running, the user may opt to have messages from the " +
                       "application be displayed in the <<LOG>> tab at the bottom. The user enables these " +
                       "messages in the <<CONFIG>> panel. Note that the more messages are displayed, " +
                       "the slower the application will run, so take care to only enable those messages that" +
                       "are essential to be monitored. When logging is enabled, if the CALLS selection " +
                       "is made it will also keep track of the call structure of the application, which will " +
                       "be displayed graphically in the <<CALLGRAPH>> tab. This allows the user to see " +
                       "visually how the methods are called. It will also color the blocks in the <<XPLOREGRAPH>> " +
                       "tab to indicate the progress made in the overall application code.");
        printParagraph("To specify a symbolic parameter to define for the application, the user needs to " +
                       "bring up the method he is interested in in the <<BYTECODE>> viewer tab. He may do " +
                       "this by either making the method selection in the Bytecode panel and pressing the " +
                       "^^Get Bytecode^^ button, or by clicking on the method of interest in either the " +
                       "CALLGRAPH or XPLOREGRAPH tabs and indicating you want to display the bytecode for " +
                       "that method. When in the BYTECODE viewer tab, the bytecode for the selected method " +
                       "is displayed on the left panel and the local parameters found are displayed in the " +
                       "right panel. Symbolic parameters are selected from one or more of the local " +
                       "parameters defined for a method. A parameter may be added to the symbolic list " +
                       "simply by clicking on the entry in the local parameters list, which will present " +
                       "the information about the parameter and ask whether you wish to add this to the " +
                       "list of symbolics. Clicking yes will add it. You may alternatively click on the " +
                       "corresponding LOAD or STORE opcode entry for the desired parameter in the bytecode " +
                       "panel on the left to get the same panel asking whether you wish to add the selected " +
                       "entry to the symbolic list.");
        printParagraph("Entries in the symbolic list may be edited by clicking on the entry in the " +
                       "Symbolic Parameters panel at the top and selecting whether you wish to edit or " +
                       "delete the symbolic parameter. It also allows you to add your own user-defined " +
                       "constraints for the specified parameter by specifying one or more entries of " +
                       "EQ, NE, GT, GE, LT or LE to a specific numeric value.");
        break;
        
      case "CONFIG":
        printTitle("Configuration Panel");
        printParagraph("There are 3 sets of configurations that can be performed by the user under " +
                       "the ^^Config^^ Menu panel header:");
        printSubSubHeader("System Configuration:");
        printParagraph("This sets up the parameters that are stored in the .danlauncher/site.properties " +
                       "in the directory in which the danlauncher repo exists. This panel allows you to set " +
                       "most of the properties defined in this file. If there are changes made to the " +
                       "System Configuration parameter names or new entries added or old ones deleted, you " +
                       "may see some errors on startup in the COMMAND tab. If so, simply delete the current " +
                       "site.properties file in this directory and restart danlauncher. The site.properties " +
                       "tags for these are the same as the names below, but preceeded with a 'SYS_'. These parameters are:");
        printParagraph("^^JAVA_HOME^^ - which specifies a path to the JRE you wish to use when building " +
                       "and running the instrumented code. This is usually found in the /usr/lib/jvm path. " +
                       "This folder should contain the bin folder containing the java executable and the " +
                       "jre folder containing rt.jar");
        printParagraph("^^DSEPATH^^ - which specifies a path to the DSE repo where danalyzer, danhelper, " +
                       "and dansolver are located. This is also where the danlauncher program is located. " +
                       "You should also make sure the repo is up-to-date and all projects build without errors.");
        printParagraph("^^CLR_OLD_FILES^^ - allows you to specify whether the class and javap " +
                       "output files should be deleted every time a jar file is loaded. This will assure " +
                       "that the files are fresh, in the event that modifications are made to the jar file. " +
                       "However, if the jar is not being rebuilt, leaving older files intact will speedup " +
                       "generating the Bytecode Viewer information.");
        printParagraph("^^LOG_LENGTH^^ - specifies the max number of bytes to keep buffered in the LOG " +
                       "panel for showing the Debug information. Reducing this value reduces the amount of " +
                       "memory consumed by danlauncher, but also limits how far back in the panel you can " +
                       "scroll. Note that the logfile that is saved is not truncated, though.");
        printParagraph("^^SOLVER_PORT^^ - specifies the TCP port of the localhost that dansolver is " +
                       "listening for command input. The instrumented code issues data to dansolver on this " +
                       "port for formulas to solve and danlauncher uses this port to send commands to clear " +
                       "the database.");
        printParagraph("^^DEBUG_PORT^^ - specifies the TCP port of the localhost that the instrumented code is " +
                       "sending the debug information on. It runs as a server and the danlauncher attaches " +
                       "to this port to receive the debug messages that are sent.");

        printParagraph("The following entry is defined in the site.properties file, but not settable in this panel:");
        printParagraph("^^SYS_PROJECT_PATH^^ - this parameter is set automatically each time a jar file is loaded.");

        printParagraph("The following are not saved in the site.properties file:");
        printParagraph("^^Load symbolics from current danfig file^^ set this if you want to read the " +
                       "current danfig file and extract the symbolics defined in it. This is if you have " +
                       "previously created a danfig file for the project and want to run it. (default: ENABLED)");
        printParagraph("^^Clear SOLUTIONS on project Load^^ set this if you want to clear the database " +
                       "solutions every time you load a new jar file. (default: ENABLED)");
        printParagraph("^^Clear SOLUTIONS on project Run^^ set this if you want to clear the database " +
                       "solutions every time you run the application. (default: DISABLED)");
        printParagraph("^^Allow running without solver^^ set this if you want to continue to run the " +
                       "application if the solver is not running. Note that no solutions will be generated. (default: DISABLED)");
        
        printSubSubHeader("Debug Setup:");
        printParagraph("^^NOTE:^^ This panel is only available when a project jar has been selected, because " +
                       "the information for it is saved in a site.properties file in that project directory. " +
                       "These are also saved in the ^^danfig^^ file in order to allow the instrumented code to access them.");
        printParagraph("This specifies what message types are enabled to be sent by the instrumented code. " +
                       "Enabling more messages can reduce the execution speed of the instrumented code, " +
                       "depending on how frequently those messages occur. The STACK and LOCALS tend to " +
                       "be output more frequently and should only be enabled when necessary. The CALLS " +
                       "selection will allow danlauncher to capture the call structure and will be able " +
                       "to generate the CALLGRAPH information");
        
        printParagraph("The ^^Debug Flags^^ section allows the user to select the messages to be output. ");
        printParagraph("  ^^Warnings^^ - warnings in the opcode execution (possible danalyzer bugs)");
        printParagraph("  ^^Info^^ - additional info during opcode command execution");
        printParagraph("  ^^Commands^^ - the opcode commands executed");
        printParagraph("  ^^Agent^^ - callbacks from the agent");
        printParagraph("  ^^Branch^^ - branching conditions for symbolics");
        printParagraph("  ^^Constraints^^ - when a constraint is added to a symbolic");
        printParagraph("  ^^Call/Return^^ - method call and return info (required if CallGraph desired)");
        printParagraph("  ^^Stack^^ - contents of the stack pushes and pops for each method");
        printParagraph("  ^^Locals^^ - local parameters reads and writes (plus object and array accesses)");
        printParagraph("  ^^Objects^^ - new Object creation and modifications");
        printParagraph("  ^^Arrays^^ - new Array creation and modifications");
        printParagraph("  ^^Solver^^ - the output of the constraints solver");
        
        printParagraph("The ^^Trigger^^ section allows the user to wait until the specified trigger condition " +
                       "occurs before capturing the selected information. This is helpful if the section " +
                       "of code the user is interested in viewing occurs after the program has been running awhile. " +
                       "If a lot of messages are always enabled, the application will run very slow. By waiting " +
                       "until the desired event, much data can be captured without slowing the system down until " +
                       "it is necessary. The events that can be used to trigger are the occurrance of an error " +
                       "condition (if the DSE has a problem), the detection of an Exception, or when a specific " +
                       "method has been called or returned from. For the method call trigger, you can specify " +
                       "how many occurrances of the call before you want to trigger, and can specify a specific " +
                       "method of a class, or any method of a class. For a Reference write, you must specify the " +
                       "index of the reference value (an internal table is kept for each REF object), and may " +
                       "also specify the number of writes to it before causing a trigger event.");
        printParagraph("  ^^on Error^^ - will trigger when an opcode execution error is detected");
        printParagraph("  ^^on Exception^^ - will trigger when an Exception is detected");
        printParagraph("  ^^on Call^^ - will trigger when the selected method has been called");
        printParagraph("  ^^on Return^^ - will trigger when the selected method has returned to caller");
        printParagraph("  ^^on Ref (write)^^ - will trigger when the REF entry has been written to");
        printParagraph("Additional parameters that apply to the triggering section are:");
        printParagraph("^^Auto Reset^^ allows you to specify that you want the trigger to re-arm and " +
                       "trigger on each additional occurrance. " +
                       "The ^^Limit^^ field allows you to specify how many lines of data to capture after the trigger" +
                       "has occurred. A value of 0 will capture continuously (hence no re-triggering), whereas " +
                       "a value of 500 will capture up to 500 lines of data after a trigger event, then wait for " +
                       "the next trigger event before capturing another 500 lines. Note that the new trigger " +
                       "event will still occur even if the 500 lines have not occurred, this just represents the " +
                       "maximum number of lines to capture following the trigger.");
        printParagraph("The ^^Call Trigger Method Selection^^ section allows the user to select the Class and " +
                       "Method for the trigger ^^on Call^^ and ^^on Return^^ to operate on. If you want to " +
                       "trigger on any method in a specified class, simply tick the ^^Any Method^^ box.");

        printSubSubHeader("Callgraph Setup:");
        printParagraph("The following allow how the CALLGRAPH panel is viewed:");
        printParagraph("^^Highlight Mode^^ allows colorization of the method blocks in the panel based on the type chosen:");
        printParagraph("^^Thread^^ - highlights the methods for a specific thread in blue (pink for methods called by more " +
                       "than one thread. This selection is disabled for single-threaded applications.");
        printParagraph("^^Elapsed Time^^ - colors the methods based on how much time was spent in the method. " +
                       "The larger the number, the darker the color. Note that this is total time spend within the " +
                       "method, including the time spent in any subroutines it called (both instrumented and un-instrumented).");
        printParagraph("^^Instructions^^ - colors the methods based on the number of instructions used in the method. " +
                       "The larger the number, the darker the color. Note that the instruction count does not include " +
                       "any un-instrumented method instructions, but does include the instructions for instrumented " +
                       "methods that are called from within it.");
        printParagraph("^^Iterations^^ - colors the methods based on how many times the method was called. " +
                       "The larger the number, the darker the color.");
        printParagraph("^^Status^^ - colors methods in which an ERROR message or an Exception was indicated " +
                       "as pink and those that have never exited as aqua.");
        printParagraph("^^Off^^ - disables the highlighting.");
        printParagraph("^^Thread Select^^ - allows you to select which thread to observe when Thread Highlighting " +
                       "is selected. The number of threads running is dispayed above this selection.");
        printParagraph("^^Highlight Range^^ - allows the user to tighten or loosen the range of " +
                       "methods viewed when Iterations, Instructions or Elapsed Time is selected. This allows " +
                       "increasing the criteria for marking the items having significant values.");
        printParagraph("The following allow how the XPLOREGRAPH panel is viewed:");
        printParagraph("^^Zoom Factor^^ controls how much the graph should be magnified or reduced. It allows " +
                       "a magnification of up to 2x (200%) and a reduction to 20% in steps of 5%.");
        break;
        
      case "STATUS":
        printTitle("Status Panel");
        printParagraph("The top line of the Status panel shows the status of the last operation performed. " +
                       "A complete list of the commands and their outputs can be observed in the ^^COMMAND^^ " +
                       "tab at the bottom of the display. Error and warning conditions will be displayed " +
                       "in RED to alert the user.");
        printParagraph("Below the status message are some statistical displays that are updated during " +
                       "the running of the application:");
        printParagraph("^^Elapsed^^ time is displayed to indicate how much time " +
                       "(in minutes and seconds) have elapsed since the RUN button was pressed.");
        printParagraph("^^Classes^^ indicates number of different classes that methods were executed from.");
        printParagraph("^^Methods^^ indicates number of different methods that have been executed.");
        printParagraph("^^Formulas^^ indicates the number of formulas that have been posted to the database by dansolver. " +
                       "This will be the number of entries displayed in the <<SOLUTIONS>> tab.");
        printParagraph("^^Solutions^^ indicates the number of formulas that have solutions in the database.");
        printParagraph("NOTE: Classes and Methods will only be updated if the ^^Call/Return^^ flag selection " +
                       "has been enabled in the Debug Setup.");
        break;
        
      case "CONTROLS":
        printTitle("Control and Bytecode Panels");
        printParagraph("These are 2 seperate panels located above the set of tabbed panels, each of which" +
                       "may be hidden from view by one of the ^^Project^^ menu selections. To view these " +
                       "panels, make sure that both the ^^Show Upper Panel^^ and the ^^Show Bytecode Panel^^ " +
                       "selections under Project menu are both checked (These can be disabled from view in " +
                       "order to give more viewing area to the tab panel information). ");

        printSubSubHeader("Controls Panel:");
        printParagraph("This panel allows the user to specify the Main Class of the project jar file, and " +
                       "then allows him to run and stop the instrumented code and issue messages to it. Note " +
                       "that these controls are not active until a jar file has been loaded. Also, many of these " +
                       "parameters are saved in the .danlauncher/site.properties in the directory of the selected project" +
                       "in order to remember the settings each time you load that project. These will be mentioned in " +
                       "the definitions below as being a Project Properties entry." +
                       "The controls consist of the following:");
        printParagraph("^^Main Class^^ specifies the Main Class of the application that contains the main() " +
                       "method call that starts the application. All classes in the application that contain " +
                       "a main() method will be listed in a drop-down box that can be selected. " +
                       "This parameter is a Project Properties entry having the tag: ^^MAIN_CLASS^^.");
        printParagraph("^^RUN^^ button will begin the execution of the instrumented application, passing the " +
                       "argument list specified by the text box to the right of it. " +
                       "The argument list is a Project Properties entry having the tag: ^^RUN_ARGUMENTS^^.");
        printParagraph("^^STOP^^ button will terminate execution of the application.");
        printParagraph("^^Input Mode^^ specifies the type of interface the application uses for receiving " +
                       "user input. The selections can be:");
        printParagraph("    NONE - no ability to issue user input to application");
        printParagraph("    STDIN - stand-alone application receives input from console (message is re-directed to it's stdin)");
        printParagraph("    HTTP_RAW - application receives input from unformatted HTTP request");
        printParagraph("    HTTP_POST - application receives input from HTTP POST request");
        printParagraph("    HTTP_GET - application receives input from HTTP GET request");
        printParagraph("This parameter is a Project Properties entry having the tag: ^^INPUT_MODE^^.");
        printParagraph("^^Port^^ is the HTTP port to use for sending requests to the application if an HTTP " +
                       "type Inpot Mode is selected. " +
                       "This parameter is a Project Properties entry having the tag: ^^APP_SERVER_PORT^^.");
        printParagraph("^^Send^^ button will send the message in the text box to the right of it to the application " +
                       "in the manner as specified by the Input Mode selection.");

        printSubSubHeader("Bytecode Panel:");
        printParagraph("This panel provides the user the ability to select any of the instrumented methods " +
                       "to have its bytecode and local parameters viewed. The <<BYTECODE>> tab will show the " +
                       "opcodes for the specific method and the <<BYTEFLOW>> will graphically show a flowchart " +
                       "of the corresponding method that makes it easier to see how the code takes different " +
                       "branches. When a method is selected and the ^^Get Bytecode^^ button is pressed, it " +
                       "will extract the specified class from the project jar file (which will be placed in " +
                       "a ^^danlauncher/classes^^ subfolder of the path where the project jar file resides) and will then " +
                       "run ^^javap^^ on the class to generate the bytecode file (which will be placed in the " +
                       "^^danlauncher/javap^^ subfolder). The bytecode information for the specified file in that class " +
                       "will be displayed on the panel on the left and the local parameters will be on the right. " +
                       "Each line of the bytecode will show the ^^byte offset^^ numeric value, followed by the " +
                       "opcode and its corresponding arguments, if any. If the selected class is already found " +
                       "in the classes folder, this step is skipped and javap is simply run on the class. If the " +
                       "selected javap file is already found in the javap folder, this step is also skipped and " +
                       "the text file is simply scanned to extract the bytecode and local parameter information. " +
                       "This is important because neither the class extraction from the jar file nor the javap " +
                       "executable can be run while the application is running (both pipe stdout to capture the " +
                       "output generated by the command). Thus, during execution you may still request bytecode information " +
                       "for any method that is in a class that was previously requested. These directories are " +
                       "cleared out based on the System Parameter settings that specify whether to purge these when " +
                       "either a jar is loaded or the application is run. If no changes are being made to the " +
                       "application code, it is safe to disable both of these. If the code is being modified " +
                       "between runs, you should at least delete them every tie you load a jar file.");
        printParagraph("The Local Parameters that are displayed on the right panel will indicate the " +
                       "^^Name^^ of the parameter followed by its ^^data type^^, the ^^slot^^ it occupies " +
                       "(an index used in loading and storing the value), and the ^^start^^ and ^^end^^ byte " +
                       "offsets over which the parameter is valid. These last entries are necessary because " +
                       "the JVM is allowed to reuse a slot for multiple parameters, as long as a parameter " +
                       "falls out of scope prior to its slot being reused. These values are used to uniqely " +
                       "identify a parameter within a method, which can be used to replace it with a " +
                       "symbolic representation. By clicking on a local parameter of interest, the user can " +
                       "add the entry to the list of symbolic parameters kept by the DSE." +
                       "Alternatively, if there wasn't any local parameter information found by javap, the " +
                       "user may also select the local parameter from the BYTECODE tab by selecting the" +
                       "corresponding LOAD or STORE opcode with the mouse and the user will be asked whether " +
                       "he wishes to add it or not.");
        printParagraph("NOTE: If no data appears in the Local Parameter section, it " +
                       "can be due to not enabling all debug information when the source files were compiled " +
                       "prior to generating the application jar file. This can be remedied by recompiling " +
                       "the source files with javac but adding the option '-g', then re-zipping them to a jar.");
        break;
        
      case "SYMBOLICS":
        printTitle("Symbolics Panel");
        printParagraph("This panel displays the currently defined symbolic parameters for the project. " +
                       "When a jar file is loaded, if there is a 'danfig' file contained in the same " +
                       "directory and the user has checked the box for ^^Load symbolics from danfig^^ in " +
                       "the Project menu, it will load all symbolic parameters defined there, along with " +
                       "all user-defined constraints for those parameters. If additional symbolic parameters " +
                       "are to be added, the user will need to select the <<BYTECODE>> tab and can browse" +
                       "the parameters defined for each specified instrumented method in the application. ");
        printParagraph("If a symbolic parameter is to be removed or needs to be modified, the user may " +
                       "select the parameter with the mouse and a panel will be displayed that allows " +
                       "him to edit the name, type and start/stop range of the parameter within the method. " +
                       "This will also bring up the specified method in the BYTECODE tab, if the application is not running. " +
                       "Note that for symbolic replacement during a run, we need the start and stop parameters " +
                       "to be in terms of opcode line offsets instead of bytecode offset that is given by" +
                       "the Local Parameter listings delivered by javap. When they are assigned by selecting " +
                       "the entry from the Local Parameters table, this conversion is performed automatically. " +
                       "If you have selected the entry from the BYTECODE viewer panel by clicking on the " +
                       "LOAD or STORE opcode command, this information cannot be deduced, so a value of 0 is " +
                       "used for both, meaning it assumes the parameter is valid for the entire method (i.e. " +
                       "the slot is not re-used). If this is not the case, you will need to find the range " +
                       "and specify it by line number, which can be found by clicking on any line - the line " +
                       "number and the corresponding bytecode offset will be displayed in the ^^Status^^ box.");
        printParagraph("Removing a symbolic is done by clicking on the symbolic parameter to remove, which " +
                       "will bring up the same panel previously described, and then clicking on the ^^Remove^^ " +
                       "button. If you want to clear out all the defined symbolics, simply click on the " +
                       "^^Remove all^^ button.");
        printParagraph("You may also add a fixed user-defined constraint for a specified symbolic parameter " +
                       "by clicking of the specified parameter to again bring up the panel previously " +
                       "described, and then clicking on the ^^Add^^ button in the ^^Setup Constraint^^ " +
                       "section at the bottom of the panel. The constraints currently only apply to numeric " +
                       "symbolic parameters and you simply specify an equality with a fixed numeric value " +
                       "you would like to add to the symbolic. The valid equalities are EQ, NE, GT, GE, LT, LE. " +
                       "The ^^Show^^ button will show all current user-defined constraints for the parameter " +
                       "and ^^Remove^^ will remove all user-defined constraints for the parameter.");
        break;
        
      case "SOLUTIONS":
        printTitle("SOLUTIONS Tab");
        printParagraph("This tab displays the symbolic solutions contained in the mongo database. " +
                       "When the instrumented jar file is run with symbolic parameters defined, each " +
                       "branch condition submits the symbolic constraints to the database for solving. " +
                       "Pressing the ^^Solve^^ button will run the ^^dansolver^^ on this database to " +
                       "attempt to find solutions for the alternate path at each branch point. It will " +
                       "then mark the entries as ^^Solvable^^ or not, and will place the ^^Solution^^ in the " +
                       "database if it was solvable. The solution will reference the parameter using " +
                       "the name that was defined for the symbolic parameters.");
        printParagraph("The data placed in the database during the run is as follows:");
        printParagraph("  ^^Time^^   - the date and time when the branchpoint was hit");
        printParagraph("  ^^Method^^ - the method in which the branch occurred");
        printParagraph("  ^^Offset^^ - the opcode line index of the branch instruction ");
        printParagraph("  ^^Path^^   - ^^true^^ if the branch was taken, ^^false^^ if not");
        printParagraph("  ^^Cost^^   - the cost in terms of opcode instructions when the branch was hit");
        printParagraph("The additional data placed bt the Solver:");
        printParagraph("  ^^Solvable^^ - ^^true^^ if the constraint was solvable, ^^false^^ if not");
        printParagraph("  ^^Solution^^ - the solution to yield the opposite branch path");
        printParagraph("Note that the database does not get automatically cleared out when a new run " +
                       "of instrumented code is executed, so there can be old results left in the " +
                       "database. If you wish to clear out the old data, you may do so using the ^^Clear^^ " +
                       "menu selection for either ^^Clear SOLUTIONS^^ or ^^Clear SOLUTIONS (for unsolvables only)^^. " +
                       "Use the latter one if you want to just clear out the entries that are marked as " +
                       "unsolvable.");
        printParagraph("In this panel, you can mouse click on the ^^Method^^ column of an entry to bring " +
                       "up the specified method in the BYTECODE tab, if the application is not running. " +
                       "It will also highlight in pink the branch instruction that was being solved and " +
                       "the instruction after the branch that was chosen. Selecting any other column of " +
                       "a row will bring up a panel containing the full constraint value for that " +
                       "solution.");
        printParagraph("Under the ^^Save^^ menu tab, there is a ^^Save Solutions Table^^ selection. This " +
                       "will export the current solutions tables data to an Excel document.");
        break;
        
      case "BYTECODE":
        printTitle("BYTECODE Tab");
        printParagraph("This shows the bytecode information extracted by 'javap' from the specified " +
                       "class and the contents for the specified method displayed. It also provides a " +
                       "seperate panel on the right that displays the local parameters that were found " +
                       "in for the method. The parameters have the following properties:");
        printParagraph("^^Name^^ - The name of the local parameter (usually taken from the source code)");
        printParagraph("^^Type^^ - the data type of the parameter");
        printParagraph("^^Slot^^ - The slot (index) for the parameter. For static parameters the index begins " +
                       "with 0, for non-static 0 is used for 'this' and parameters start at indexc 1. The " +
                       "compiler does the assignment of these and it numbers them in the order they occur " +
                       "with each getting 1 slot except for longs and doubles which get 2 slots. Note that " +
                       "because the compiler can re-use the slots when it determines a parameter has gone " +
                       "out of scope, multiple parameters in a method can have the same slot value. These " +
                       "are differentiated based on the Start and End byte offset assigned to them. Also note " +
                       "that because ASM does not provide us the actual byte offset value for each instruction, " +
                       "we do know the instruction offset (instruction count within the method). Because of this " +
                       "when we create a symbolic parameter from a local parameter, we convert the byte offset " +
                       "into an instruction offset so we can detect the parameter correctly in the instrumented code.");
        printParagraph("^^Start^^ - The starting bytecode offset from the start of the method that the parameter " +
                       "is in scope. Note that this occurs on the instruction AFTER the LOAD or STORE command.");
        printParagraph("^^End^^ - The ending bytecode offset from the start of the method that the parameter " +
                       "is in scope.");
        printLinefeed();
        printParagraph("These two panels also provide the method by which symbolic " +
                       "parameters can be added. You may click on any of the local parameters in the " +
                       "right pane and a panel will show the selected parameter and ask if you " +
                       "wish to add the entry to the Symbolic Parameters list. You can also do this " +
                       "from the bytecode data in the left pane by clicking on any of the LOAD or STORE " +
                       "opcodes that correspond to interacting with a corresponding local parameter.");
        break;
        
      case "BYTEFLOW":
        printTitle("BYTEFLOW Tab");
        printParagraph("This shows the same information that is displayed in the <<BYTECODE>> tab, but " +
                       "in a graphical form.  This allows the user to 'see' more clearly how the code " +
                       "flows. It color codes special blocks to match the highlighting used in BYTECODE as follows:");
        printParagraph("^^ENTRY^^ - (Cyan) The entry point into the method.");
        printParagraph("^^EXCEPTION^^ - (Cyan) An exception entry handled by the method.");
        printParagraph("^^SWITCH^^ - (Blue) a TABLESWITCH or LOOKUPSWITCH opcode.");
        printParagraph("^^INVOKE^^ - (Gold) an INVOKE opcode that calls another method. Gold highlighting " +
                       "only if the method called is instrumented, else the color is Grey.");
        printParagraph("^^RETURN^^ - (Grey) a RETURN opcode that exits the method.");
        printParagraph("^^BRANCH^^ - (Grey) a branch opcode that does not involve a symbolic parameter.");
        printParagraph("^^SYMBOLIC BRANCH^^ - (Violet) a branch opcode that does involve a symbolic parameter.");
        printParagraph("^^BLOCK^^ - (Grey) one or more opcodes that do not fall into any of the above categories.");
        printLinefeed();
        printParagraph("By clicking on an instrumented method call, it will load that method into " +
                       "BYTECODE and BYTEFLOW to peer into its contents. A ^^BACK^^ button will then " +
                       "appear to allow the user to revert back to previous method selections. ");
        break;
        
      case "LOG":
        printTitle("LOG Tab");
        printParagraph("This displays the log messages received from the instrumented code. The " +
                       "selection of the message types to be enabled are settable by the ^^Config^^ " +
                       "menu item ^^Debug Setup^^. Note that changing the configuration value while " +
                       "instrumented code is running will not affect the log messages received. It " +
                       "will only take effect on the next run. The contents of this panel are also " +
                       "saved in the 'debug.log' file that is placed in the same directory as the " + 
                       "project jar file. The contents of this tab are cleared out when the ^^Run^^ " +
                       "button is pressed.");
        break;
        
      case "CALLGRAPH":
        printTitle("CALLGRAPH Tab");
        printParagraph("This displays the call graph for the running instrumented code if the ^^Call/Return^^ " +
                       "selection is enabled in ^^Debug Setup^^ under the ^^Config^^ menu. This " +
                       "will show the call hierarchy of the instrumented methods only. " +
                       "Clicking on a method block will show statistics that were captured for " +
                       "the specified method on a thread-by-thread basis, and will allow the user to " +
                       "open the Bytecode source for the selected method if code is not currently running. ");
        printParagraph("The ^^Callgraph Setup^^ in the ^^Config^^ menu allows the user to color-code " +
                       "the method blocks the highlight different aspects, such as which methods consumed " +
                       "the most time or instructions or had the most calls to it. It can also seperate " +
                       "calls that ran in a specific thread.");
        break;
        
      case "XPLOREGRAPH":
        printTitle("XPLOREGRAPH Tab");
        printParagraph("This displays the call graph that was either loaded from a JSON file or copied from " +
                       "the Call Graph for exploring new paths. The Janalyzer program can be used to " +
                       "generate a graph tree for the entire instrumented code base for the purpose of observing " +
                       "the percentage of methods traversed (code coverage). It can also be used to determine " +
                       "new branch paths to explore by determining the symbolic parameter values that can " +
                       "lead to these branches using the solver. The initial graph will be displayed with each " +
                       "method in neutral color. As the code runs (with ^^Call/Return^^ debug flag set, new methods " +
                       "will be added to the Call Graph in a different color to see the new paths followed. " +
                       "The external JSON file can be loaded into danlauncher using the " +
                       "^^Load Explore Graph^^ selection in the ^^Project^^ menu and can also be copied over " +
                       "from the current Call Graph from the ^^Save Call Graph for Exploring^^ selection in the " +
                       "^Save^^ menu.");
        printParagraph("This allows the user to see the methods that are run against the background of " +
                       "those that are not run. The user may then see methods he would like to explore that are " +
                       "currently not being run and can look for symbolic parameters that can be added " +
                       "to explore those paths." +
                       "Clicking on a method block will show statistics that were captured for " +
                       "the specified method if the method was executed, and will allow the user to " +
                       "open the Bytecode source for the selected method if code is not currently running.");
        break;
    }
    
    // now keep user from editing the text and display the panel
    panel.setCaretPosition(0);
    panel.setEditable(false);
    helpFrame.setVisible(true);
  }
  
  private void printInitialTitle(String message) {
    logger.printField(MsgType.HEADER1.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printTitle(String message) {
    logger.printField(MsgType.HEADER1.toString(), message + Utils.NEWLINE);
    printLinefeed();
    printHyperText("BACK");
    printLinefeed();
    printLinefeed();
  }
  
  private void printMainHeader(String message) {
    logger.printField(MsgType.HEADER1.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printSubHeader(String message) {
    logger.printField(MsgType.HEADER2.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printSubSubHeader(String message) {
    logger.printField(MsgType.HEADER3.toString(), message + Utils.NEWLINE);
    printLinefeed();
  }
  
  private void printNormalText(String message) {
    logger.printField(MsgType.NORMAL.toString(), message);
  }
  
  private void printHighlightedText(String message) {
    MsgType type = MsgType.EMPHASIS;
    int offset = logger.getTextLength();
    metaMap.add(new MetadataMap(type, message, offset));
    logger.printField(type.toString(), message);
  }
  
  private void printHyperText(String message) {
    MsgType type = MsgType.HYPERTEXT;
    int offset = logger.getTextLength();
    metaMap.add(new MetadataMap(type, message, offset));
    logger.printField(type.toString(), message);
  }
  
  private void printLinefeed() {
    logger.printField(MsgType.NORMAL.toString(), Utils.NEWLINE);
  }
  
  private void printParagraph(String message) {
    // setup the metas to find
    List<MetaData> metalist = new ArrayList<>();
    metalist.add(new MetaData(MsgType.EMPHASIS, "^^", "^^"));
    metalist.add(new MetaData(MsgType.HYPERTEXT, "<<", ">>"));

    while (!message.isEmpty()) {
      // search for any next meta, exit if none
      boolean valid = false;
      MetaData selected = null;
      for (MetaData entry : metalist) {
        entry.checkForMeta(message);
        valid = entry.valid ? true : valid;
        // select the entry that we encounter first in the string
        if (entry.valid) {
          if (selected == null || (entry.start < selected.start)) {
            selected = entry;
          }
        }
      }
      if (!valid || selected == null || !selected.valid) {
        break;
      }
      
      // if there is non-meta text first, output it now
      if (selected.start > 0) {
        printNormalText(message.substring(0, selected.start));
        message = message.substring(selected.start + selected.entry.length());
      } else {
        message = message.substring(selected.entry.length());
      }

      // convert and output the meta text
      String metastr = message.substring(0, selected.end);
      switch(selected.type) {
        case EMPHASIS:
          printHighlightedText(metastr);
          break;
        case HYPERTEXT:
          printHyperText(metastr);
          break;
      }
      message = message.substring(selected.end + selected.exit.length());
    }
      
    // output any remaining non-meta text
    if (!message.isEmpty()) {
      printNormalText(message + Utils.NEWLINE + Utils.NEWLINE);
    }
  }
  
  private class MetaData {
    public MsgType type;
    public String  entry;
    public String  exit;
    public boolean valid;
    public int     start;
    public int     end;
    
    public MetaData(MsgType msgtype, String begin, String term) {
      type  = msgtype;
      entry = begin;
      exit  = term;
      valid = true;
      start = 0;
      end   = 0;
    }
    
    public void checkForMeta(String message) {
      if (!valid) {
        return;
      }

      end = -1;
      start = message.indexOf(entry);
      if (start < 0) {
        valid = false;
        return;
      }
      end = message.substring(start + entry.length()).indexOf(exit);
      if (end <= 0) {
        valid = false;
        return;
      }

      valid = true;
    }
  }
  
  private class Window_MainListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      helpFrame.setVisible(false);
    }
  }
  
  private class HelpScrollListener implements AdjustmentListener {

    @Override
    public void adjustmentValueChanged(AdjustmentEvent evt) {
      // ignore if this tab is not active
      if (!tabSelected) {
        return;
      }
      
      if (evt.getValueIsAdjusting()) {
        return;
      }
      
      JScrollBar scrollBar = scrollPanel.getVerticalScrollBar();
    }
    
  }

  private class HelpMouseListener extends MouseAdapter {

    @Override
    public void mouseClicked (MouseEvent evt) {
      if (metaMap.isEmpty()) {
        return;
      }
      
      String contents = panel.getText();

      // set caret to the mouse location and get the caret position (char offset within text)
      panel.setCaretPosition(panel.viewToModel(evt.getPoint()));
      int curpos = panel.getCaretPosition();
      //Utils.printStatusInfo("HelpLogger: cursor = " + curpos
      //    + ", char = '" + contents.charAt(curpos) + "'");
      
      // now determine if the selected word is a hypertext word
      if (contents.charAt(curpos) > ' ') {
        // find begining of selected word
        int start = curpos;
        while (start > 0 && contents.charAt(start - 1) > ' ') {
          --start;
        }
        // search the current page map for locations of the meta fields in the text
        for (int ix = 0; ix < metaMap.size(); ix++) {
          MetadataMap entry = metaMap.get(ix);
          if (entry.offset == start) {
            if (entry.type == MsgType.HYPERTEXT) {
              if (entry.value.equals("BACK") && !backPages.empty()) {
                // the user selected the BACK button
                String lastPage = backPages.pop();
                Utils.printStatusInfo("HYPERTEXT page: BACK -> " + lastPage);
                printHelpPanel(lastPage);
              } else if (!entry.value.equals(curPage)) {
                // else we are visiting a new page
                Utils.printStatusInfo("HYPERTEXT page: " + entry.value);
                backPages.push(curPage);
                printHelpPanel(entry.value);
              }
            } else if (entry.type == MsgType.EMPHASIS) {
              Utils.printStatusInfo("EMPHASIS page: " + entry.value);
            }
          }
        }
      }
    }
  }
  
  private class HelpKeyListener implements KeyListener {

    @Override
    public void keyPressed(KeyEvent ke) {
      // when the key is initially pressed
      //Utils.printStatusInfo("HelpKeyListener: keyPressed: " + ke.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent ke) {
      // follows keyPressed and preceeds keyReleased when entered key is character type
      //Utils.printStatusInfo("HelpKeyListener: keyTyped: " + ke.getKeyCode() + " = '" + ke.getKeyChar() + "'");
    }

    @Override
    public void keyReleased(KeyEvent ke) {
      if (tabSelected) {
        // when the key has been released
        //Utils.printStatusInfo("HelpKeyListener: keyReleased: " + ke.getKeyCode());
        //int curpos = panel.getCaretPosition();
        switch (ke.getKeyCode()) {
          case KeyEvent.VK_ESCAPE:
            helpFrame.setVisible(false);
            break;
          case KeyEvent.VK_UP:
            break;
          case KeyEvent.VK_DOWN:
            break;
          case KeyEvent.VK_PAGE_UP:
            break;
          case KeyEvent.VK_PAGE_DOWN:
            break;
          default:
            break;
        }
      }
    }
  }

}
