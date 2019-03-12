/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danalyzer.gui;

import danalyzer.executor.ExecWrapper;
import static danalyzer.gui.DebugUtil.TRG_CALL;
import static danalyzer.gui.DebugUtil.TRG_ERROR;
import static danalyzer.gui.DebugUtil.TRG_EXCEPT;
import static danalyzer.gui.DebugUtil.TRG_INSTRUCT;
import static danalyzer.gui.DebugUtil.TRG_REF_W;
import static danalyzer.gui.DebugUtil.TRG_RETURN;

import java.util.ArrayList;
import javax.swing.DefaultListModel;

/**
 *
 * @author dan
 */
public class Danfig {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  public final static String SYMB_LIST_TITLE = "#! DANALYZER SYMBOLIC EXPRESSION LIST";
  
  private static enum DebugMode { STDOUT, GUI, UDPPORT, TCPPORT }

  private static enum SolverMethods { NONE, MAXIMIZE, MINIMIZE }

  // Debug parameters
  private static DebugMode      debugMode;
  public  static String         debugIpAddress;
  public  static int            debugPort;
  // Solver parameters
  public  static String         solverIpAddress;
  public  static int            solverPort;
  private static SolverMethods  solverMethod;
  // Symbolic parameters
  public static ArrayList<String>  constraintList = new ArrayList<>();

  public static void setDebugMode(String value) {
    switch (value) {
      case "GUI":
        debugMode = DebugMode.GUI;
        break;
      case "UDPPORT":
        debugMode = DebugMode.UDPPORT;
        break;
      case "TCPPORT":
        debugMode = DebugMode.TCPPORT;
        break;
      case "STDOUT":
        debugMode = DebugMode.STDOUT;
        break;
      default:
        System.err.println("Invalid DebugMode selection: " + value);
        break;
    }
  }

  public static String getDebugMode() {
    return debugMode.toString();
  }
  
  public static void setSolverMethod(String value) {
    switch (value) {
      case "NONE":
        solverMethod = SolverMethods.NONE;
        break;
      case "MAXIMIZE":
        solverMethod = SolverMethods.MAXIMIZE;
        break;
      case "MINIMIZE":
        solverMethod = SolverMethods.MINIMIZE;
        break;
      default:
        System.err.println("Invalid SolverMethod selection: " + value);
        break;
    }
  }

  public static String getSolverMethod() {
    return solverMethod.toString();
  }

  public static void constraintAdd(String selection, String value) {
    String entry = selection + " " + value;
    if (!constraintList.contains(entry)) {
      constraintList.add(entry);
      System.out.println("Added constraint <" + value + "> to symbolic entry " + selection);
    }
  }
  
  public static void constraintRemoveAll() {
    constraintList.clear();
  }
  
  public static int constraintRemoveParameter(String selection) {
    int count = 0;
    for(int ix = constraintList.size() - 1; ix >= 0; ix--) {
      String constraint = constraintList.get(ix);
      String[] fields = constraint.split(" ");
      if (selection.equals(fields[0])) {
        constraintList.remove(ix);
        ++count;
      }
    }
    return count;
  }
  
  public static void setFromProperties(PropertiesFile props) {
    debugIpAddress = getPropString(props, "IPAddress", "localhost");
    debugPort      = getPropInteger(props, "DebugPort", 5000, 100, 65535);
      
    solverIpAddress = getPropString(props, "SolverAddress", "localhost");
    solverPort      = getPropInteger(props, "SolverPort", 4000, 100, 65535);
    setSolverMethod(getPropString(props, "SolverMethod", "NONE"));

    // load list of constraint entries
    String value = props.getPropertiesItem("ConstraintList", "");
    if (value != null) {
      constraintList.clear();
      String[] list = value.split(NEWLINE);
      for (int ix = 0; ix < list.length; ix++) {
        String entry = list[ix].trim();
        String[] array = entry.split(" ");
        if (!entry.isEmpty() && array.length == 2) {
          constraintAdd(array[0], array[1]);
        }
      }
    }
  }

  public static void saveToProperties(PropertiesFile props) {
    props.setPropertiesItem("IPAddress", debugIpAddress);
    props.setPropertiesItem("DebugPort", debugPort + "");
      
    props.setPropertiesItem("SolverAddress", solverIpAddress);
    props.setPropertiesItem("SolverPort", solverPort + "");
    props.setPropertiesItem("SolverMethod", solverMethod.toString());

    // create list of constraint entries
    String list = "";
    for (int ix = 0; ix < constraintList.size(); ix++) {
      if (ix > 0) {
        list += NEWLINE;
      }
      list += constraintList.get(ix);
    }
    props.setPropertiesItem("ConstraintList", list);
  }
  
  public static int fromDanfigFile(String entry, int line) {
    if (entry.startsWith("#") || entry.isEmpty()) {
      return 0;
    }
    
    // check for debug selection setup
    int offset = entry.indexOf(':');
    if (offset <= 0) {
      return 0;
    }
    int param, startline, endline;
    String type = entry.substring(0, offset);
    String value = entry.substring(offset + 1).trim();
    if (value.isEmpty()) {
        return 0;
    }
    switch(type) {
      case "DebugMode":
        // determines where the debug info should be sent to
        setDebugMode(value);
        System.out.println(type + " set to: " + value);
        return 0;
      case "DebugPort":
        int port;
        try {
          port = Integer.parseUnsignedInt(value);
        } catch (NumberFormatException ex) {
          port = -1;
        }
        if (port< 100 || port > 65535) {
          System.err.println("Invalid port selection: " + value + " - reset to default (5000)");
          port = 5000;
        }
        debugPort = port;
        System.out.println(type + " set to: " + port);
        return 0;
      case "IPAddress":
        debugIpAddress = value;
        System.out.println(type + " set to: " + value);
        return 0;
      case "DebugFlags":
        return DebugUtil.setDebugFlags(value);
      case "SolverPort":
        try {
          port = Integer.parseUnsignedInt(value);
        } catch (NumberFormatException ex) {
          port = -1;
        }
        if (port< 100 || port > 65535) {
          System.err.println("Invalid port selection: " + value + " - reset to default (4000)");
          port = 4000;
        }
        solverPort = port;
        System.out.println(type + " set to: " + port);
        return 0;
      case "SolverAddress":
        solverIpAddress = value;
        System.out.println(type + " set to: " + value);
        return 0;
      case "SolverMethod":
        setSolverMethod(value);
        System.out.println(type + " set to: " + value);
        return 0;
      case "Thread":
        // ignore - this is obsoleted
        return 0;
      case "Symbolic":
        String word[] = value.split("\\s+");
        String method, index, start, end, name, ptype;
        switch(word.length) {
          case 2: // index, method
            name   = "";
            method = word[0].trim().replace(".","/");
            index  = word[1].trim();
            start  = "0";
            end    = "0"; // this will default to entire method range
            ptype  = "";
            break;
          case 3: // method, slot, name
            name   = word[0].trim();
            method = word[1].trim().replace(".","/");
            index  = word[2].trim();
            start  = "0";
            end    = "0"; // this will default to entire method range
            ptype  = "";
            break;
          case 6: // index, method, start range, end range, name, type
            name   = word[0].trim();
            method = word[1].trim().replace(".","/");
            index  = word[2].trim();
            start  = word[3].trim();
            end    = word[4].trim();
            ptype  = word[5].trim();
            break;
          default:
            System.err.println("ERROR: <GuiPanel.parseDanfigEntry> " +
              "Invalid symbolic word count (" + word.length + ") " + line + ": " + value);
            return 1;
        }
        try {
          param = Integer.parseUnsignedInt(index);
          startline = Integer.parseUnsignedInt(start);
          endline = Integer.parseUnsignedInt(end);
        } catch (NumberFormatException ex) {
          System.err.println("ERROR: <GuiPanel.parseDanfigEntry> " +
              "Invalid Symbolic syntax (parameter number) for index " + line);
          return 1;
        }

        // add the symbolic entry to Executor
        ExecWrapper.addSymbolicEntry(method, param, startline, endline, name, ptype);
        return 0;
      case "Constraint":
        // NOTE: constraints must be defined after their corresponding Symbolic has been defined,
        // since it relies on finding its definition in the list.
        String[] array = value.split("\\s+");
        if (array.length != 3) {
          System.err.println("ERROR: <GuiPanel.parseDanfigEntry> Invalid Constraint syntax index " +
              line + ": " + value);
          return 1;
        }
        // get the entries in the line
        String id = array[0].trim();
        String compare = array[1].trim();
        String constrVal = array[2].trim();
        
        // add the constraint entry to Executor
        ExecWrapper.addUserConstraint(id, compare, constrVal);
        return 0;
        
      default:
        if (TriggerSetup.setFromList(type, value)) {
          return 0;
        }
        break;
    }

    return 1;
  }

  public static String toDanfigFile(DefaultListModel symbolicList) {
      String content = SYMB_LIST_TITLE + NEWLINE;
      content += "#" + NEWLINE;
      content += "# DEBUG SETUP" + NEWLINE;
      content += "DebugFlags: " + DebugUtil.getDebugFlagNames() + NEWLINE;
      content += "DebugMode: " + getDebugMode() + NEWLINE;
      content += "DebugPort: " + debugPort + NEWLINE;
      content += "IPAddress: " + debugIpAddress + NEWLINE;

      content += "#" + NEWLINE;
      content += "# DEBUG TRIGGER SETUP" + NEWLINE;
      content += TriggerSetup.makeList();

      content += "#" + NEWLINE;
      content += "# SOLVER INTERFACE" + NEWLINE;
      content += "SolverPort: " + solverPort + NEWLINE;
      content += "SolverAddress: " + solverIpAddress + NEWLINE;
      content += "SolverMethod: " + solverMethod.toString() + NEWLINE;
      
      // create the symbolic entry list
      content += "#" + NEWLINE;
      content += "# SYMBOLICS" + NEWLINE;
      content += "# Symbolic: <symbol_id> <method> <slot>   --OR--" + NEWLINE;
      content += "# Symbolic: <symbol_id> <method> <slot> <start range> <end range> <type>" + NEWLINE;
      for (int ix = 0; ix < symbolicList.getSize(); ix++) {
        String entry = (String) symbolicList.get(ix);
        entry = entry.trim();
        if (!entry.isEmpty()) {
          content += "Symbolic: " + entry + NEWLINE;
        }
      }

      // create the cinstraint entry list
      content += "#" + NEWLINE;
      content += "# CONSTRAINTS (NOTE: SYMBOLICS section must be defined prior to this section)" + NEWLINE;
      content += "# Constraint: <symbol_id>  <EQ|NE|GT|GE|LT|LE> <value>" + NEWLINE;
      for (int ix = 0; ix < constraintList.size(); ix++) {
        String entry = (String) constraintList.get(ix);
        entry = entry.trim();
        if (!entry.isEmpty()) {
          content += "Constraint: " + entry + NEWLINE;
        }
      }
      return content;
  }
  
  private static boolean getPropBoolean(PropertiesFile props, String tag, boolean dflt) {
    boolean result = dflt;
    if (props != null) {
      String value = props.getPropertiesItem(tag, dflt ? "1" : "0");
      if (value != null) {
        result = value.equals("1");
      }
    }
    return result;
  }
  
  private static int getPropInteger(PropertiesFile props, String tag, int dflt, int min, int max) {
    int retval;
    if (props != null) {
      String value = props.getPropertiesItem(tag, "" + dflt);
      try {
        retval = Integer.parseInt(value);
        if (retval >= min && retval <= max) {
          return retval; // valid
        }
      } catch (NumberFormatException ex) { }

      System.err.println("ERROR: <Danfig.getPropInteger> Invalid property value " +
          value + " for field: " + tag);
      props.setPropertiesItem(tag, "" + dflt);
    }
    return dflt;
  }
  
  private static String getPropString(PropertiesFile props, String tag, String dflt) {
    String result = dflt;
    if (props != null) {
      result = props.getPropertiesItem(tag, dflt);
    }
    return result;
  }

  public static class TriggerSetup {
    public static Integer  tEnable;   // bits are set to enable triggering
    public static Integer  tCount;    // number of condition occurrances before call/return trigger (e.g. 1 = 1st occurrance)
    public static Integer  tRefCount; // number of condition occurrances before reference trigger (e.g. 1 = 1st occurrance)
    public static Integer  tRange;    // number of debug lines to capture before disabling trigger (0 = never)
    public static Integer  tInsNum;   // instruction count number to trigger on
    public static Integer  tRefIndex; // reference parameter index number
    public static Boolean  tAuto;     // true if trigger will occur every time conditions are met (tRange must not be 0)
    public static Boolean  tAnyMeth;  // true if trigger on call/return of any method of specified class
    public static String   tClass;    // class name to trigger on
    public static String   tMethod;   // method name to trigger on
    
    public static void init() {
      tEnable   = 0;
      tAuto     = false;
      tAnyMeth  = false;
      tClass    = "";
      tMethod   = "";
      tCount    = 1;
      tRefCount = 1;
      tRange    = 0;
      tInsNum   = 1;
      tRefIndex = 0;
    }
    
    public static void setFromProperties(PropertiesFile props) {
      tEnable = 0;
      tEnable |= getPropBoolean(props, "TriggerOnError"     , false) ? TRG_ERROR    : 0;
      tEnable |= getPropBoolean(props, "TriggerOnException" , false) ? TRG_EXCEPT   : 0;
      tEnable |= getPropBoolean(props, "TriggerOnInstr"     , false) ? TRG_INSTRUCT : 0;
      tEnable |= getPropBoolean(props, "TriggerOnCall"      , false) ? TRG_CALL     : 0;
      tEnable |= getPropBoolean(props, "TriggerOnReturn"    , false) ? TRG_RETURN   : 0;
      tEnable |= getPropBoolean(props, "TriggerOnRefWrite"  , false) ? TRG_REF_W    : 0;

      tAuto     = getPropBoolean(props, "TriggerAuto"       , false);
      tAnyMeth  = getPropBoolean(props, "TriggerAnyMeth"    , false);
      tClass    = getPropString (props, "TriggerClass"      , "");
      tMethod   = getPropString (props, "TriggerMethod"     , "");
      tCount    = getPropInteger(props, "TriggerCount"      , 1, 1, 10000);
      tRefCount = getPropInteger(props, "TriggerRefCount"   , 1, 1, 10000);
      tRange    = getPropInteger(props, "TriggerRange"      , 0, 0, 100000);
      tInsNum   = getPropInteger(props, "TriggerInstruction", 1, 1, 1000000);
      tRefIndex = getPropInteger(props, "TriggerRefIndex"   , 0, 0, 1000);
    }
    
    public static void saveToProperties(PropertiesFile props) {
      props.setPropertiesItem("TriggerOnError"     , (tEnable & TRG_ERROR)    != 0  ? "1" : "0");
      props.setPropertiesItem("TriggerOnException" , (tEnable & TRG_EXCEPT)   != 0  ? "1" : "0");
      props.setPropertiesItem("TriggerOnInstr"     , (tEnable & TRG_INSTRUCT) != 0  ? "1" : "0");
      props.setPropertiesItem("TriggerOnCall"      , (tEnable & TRG_CALL)     != 0  ? "1" : "0");
      props.setPropertiesItem("TriggerOnReturn"    , (tEnable & TRG_RETURN)   != 0  ? "1" : "0");
      props.setPropertiesItem("TriggerOnRefWrite"  , (tEnable & TRG_REF_W)    != 0  ? "1" : "0");

      props.setPropertiesItem("TriggerAuto"       , tAuto    ? "1" : "0");
      props.setPropertiesItem("TriggerAnyMeth"    , tAnyMeth ? "1" : "0");
      props.setPropertiesItem("TriggerClass"      , tClass);
      props.setPropertiesItem("TriggerMethod"     , tMethod);
      props.setPropertiesItem("TriggerCount"      , tCount.toString());
      props.setPropertiesItem("TriggerRefCount"   , tRefCount.toString());
      props.setPropertiesItem("TriggerRange"      , tRange.toString());
      props.setPropertiesItem("TriggerInstruction", tInsNum.toString());
      props.setPropertiesItem("TriggerRefIndex"   , tRefIndex.toString());
    }
    
    public static String makeList() {
      String content = "";
      content += "TriggerOnError: "      + ((tEnable & TRG_ERROR)    != 0 ? "1" : "0") + NEWLINE;
      content += "TriggerOnException: "  + ((tEnable & TRG_EXCEPT)   != 0 ? "1" : "0") + NEWLINE;
      content += "TriggerOnInstr: "      + ((tEnable & TRG_INSTRUCT) != 0 ? "1" : "0") + NEWLINE;
      content += "TriggerOnCall: "       + ((tEnable & TRG_CALL)     != 0 ? "1" : "0") + NEWLINE;
      content += "TriggerOnReturn: "     + ((tEnable & TRG_RETURN)   != 0 ? "1" : "0") + NEWLINE;
      content += "TriggerOnRefWrite: "   + ((tEnable & TRG_REF_W)    != 0 ? "1" : "0") + NEWLINE;
      
      content += "TriggerAuto: "         + (tAuto    ? "1" : "0") + NEWLINE;
      content += "TriggerAnyMeth: "      + (tAnyMeth ? "1" : "0") + NEWLINE;
      content += "TriggerClass: "        + tClass + NEWLINE;
      content += "TriggerMethod: "       + tMethod + NEWLINE;
      content += "TriggerCount: "        + tCount.toString() + NEWLINE;
      content += "TriggerRefCount: "     + tRefCount.toString() + NEWLINE;
      content += "TriggerRange: "        + tRange.toString() + NEWLINE;
      content += "TriggerInstruction: "  + tInsNum.toString() + NEWLINE;
      content += "TriggerRefIndex: "     + tRefIndex.toString() + NEWLINE;
      return content;
    }
    
    public static boolean setFromList(String type, String value) {
      int ival = 0;
      switch(type) {
        case "TriggerOnInstr":
          tEnable |= "1".equals(value) ? TRG_INSTRUCT : 0;
          break;
        case "TriggerOnCall":
          tEnable |= "1".equals(value) ? TRG_CALL : 0;
          break;
        case "TriggerOnReturn":
          tEnable |= "1".equals(value) ? TRG_RETURN : 0;
          break;
        case "TriggerOnError":
          tEnable |= "1".equals(value) ? TRG_ERROR : 0;
          break;
        case "TriggerOnException":
          tEnable |= "1".equals(value) ? TRG_EXCEPT : 0;
          break;
        case "TriggerOnRefWrite":
          tEnable |= "1".equals(value) ? TRG_REF_W : 0;
          break;
        case "TriggerAuto":
          tAuto    = "1".equals(value);
          break;
        case "TriggerAnyMeth":
          tAnyMeth = "1".equals(value);
          break;
        case "TriggerClass":
          tClass = value;
          break;
        case "TriggerMethod":
          tMethod = value;
          break;
        case "TriggerCount":
          try {
            ival = Integer.parseUnsignedInt(value);
          } catch (NumberFormatException ex) {
            // ignore (we dedault to 0)
          }
          tCount = ival;
          break;
        case "TriggerRefCount":
          try {
            ival = Integer.parseUnsignedInt(value);
          } catch (NumberFormatException ex) {
            // ignore (we dedault to 0)
          }
          tRefCount = ival;
          break;
        case "TriggerRange":
          try {
            ival = Integer.parseUnsignedInt(value);
          } catch (NumberFormatException ex) {
            // ignore (we dedault to 0)
          }
          tRange = ival;
          break;
        case "TriggerInstruction":
          try {
            ival = Integer.parseUnsignedInt(value);
          } catch (NumberFormatException ex) {
            // ignore (we dedault to 0)
          }
          tInsNum = ival;
          break;
        case "TriggerRefIndex":
          try {
            ival = Integer.parseUnsignedInt(value);
          } catch (NumberFormatException ex) {
            // ignore (we dedault to 0)
          }
          tRefIndex = ival;
          break;
        default:
          System.err.println("ERROR: <DebugUtil.setFromList> Invalid type: " + type);
          return false;
      }
      System.out.println(type + " set to: " + value);
      return true;
    }

    public static int isTriggerEnabled(int type) {
      return (tEnable & type) != 0 ? 1 : 0;
    }

    public static boolean isTriggered() {
      return tEnable != 0;
    }
  }
  
}
