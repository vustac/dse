/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package logging;

import main.PropertiesFile;
import util.Utils;
import gui.GuiControls;

/**
 *
 * @author dan
 */
public class DebugParams {
  private static PropertiesFile props;  // the associated properties file for these parameters
  private static GuiControls    frame;  // the associated jFrame containing the control widgets
  
  private boolean  change;              // true if something changed
  
  // Debug parameters
  private String   debugFlags;
  
  // Trigger parameters
  private boolean  tInstr;    // true if trigger when instruction count reached
  private boolean  tError;    // true if trigger when an ERROR (or WARN) msg is received
  private boolean  tExcept;   // true if trigger when an Exception occurs
  private boolean  tCall;     // true if trigger on call to specified method
  private boolean  tReturn;   // true if trigger on return from method as well (auto must be on)
  private boolean  tRefW;     // true if trigger on write to reference parameter
  private boolean  tAuto;     // true if trigger will occur every time conditions are met (tRange must not be 0)
  private boolean  tAnyMeth;  // true if trigger on call/return of any method of specified class
  private String   tClass;    // class name to trigger on
  private String   tMethod;   // method name to trigger on
  private int      tCount;    // number of condition occurrances before call/return trigger (e.g. 1 = 1st occurrance)
  private int      tRefCount; // number of condition occurrances before reference trigger (e.g. 1 = 1st occurrance)
  private int      tRange;    // number of debug lines to capture before disabling trigger (0 = never)
  private int      tRefIndex; // the index for the reference parameter selection
  private int      tInsNum;   // instruction count number to trigger on

  // this should be called when creating the control panel after the GuiControls has been setup.
  // this assumes that the panel is created on startup, but hidden and the properties file that
  // corresponds to these parameters is setup at a later time.
  public DebugParams(GuiControls cpanel) {
    // save associated control panel
    frame = cpanel;
    
    debugFlags = "";
    
    // set default values
    tInstr   = false;
    tError   = false;
    tExcept  = false;
    tCall    = false;
    tReturn  = false;
    tRefW    = false;
    tAuto    = false;
    tAnyMeth = false;
    tClass   = "";
    tMethod  = "";
    tCount   = 1;
    tRefCount = 1;
    tInsNum  = 1;
    tRange   = 0;
    tRefIndex = 0;
  }
  
  /**
   * this specifies the properties file that contains the DebugParams settings.
   * 
   * @param propsFile - the properties file for these settings
   */
  public void setPropertiesFile(PropertiesFile propsFile) {
    if (propsFile == null) {
      Utils.printStatusError("DebugParams: Missing Properties file");
      System.exit(1);
    }

    // init parameters from properties file
    props = propsFile;
    setFromProperties();
  }
  
  /**
   * this sets up the control values on the panel from the DebugParams settings
   */
  public void setupPanel() {
    // make sure we set range to 0 if Auto is disabled
    if (!tAuto) {
      tRange = 0;
    }
    
//    frame.getCheckbox("TRIG_ON_INSTR").setSelected(tInstr);
    frame.getCheckbox("TRIG_ON_ERROR").setSelected(tError);
    frame.getCheckbox("TRIG_ON_EXCEPT").setSelected(tExcept);
    frame.getCheckbox("TRIG_ON_CALL").setSelected(tCall);
    frame.getCheckbox("TRIG_ON_RETURN").setSelected(tReturn);
    frame.getCheckbox("TRIG_ON_REF_W").setSelected(tRefW);
    frame.getCheckbox("AUTO_RESET").setSelected(tAuto);
    frame.getCheckbox("ANY_METHOD").setSelected(tAnyMeth);

    frame.getSpinner("SPIN_COUNT").getModel().setValue(tCount);
    frame.getSpinner("SPIN_REFCOUNT").getModel().setValue(tRefCount);
    frame.getSpinner("SPIN_RANGE").getModel().setValue(tRange);
//    frame.getSpinner("SPIN_INSTR").getModel().setValue(tInsNum);
    frame.getSpinner("SPIN_REF").getModel().setValue(tRefIndex);
    
    frame.getCombobox("COMBO_CLASS").setSelectedItem(tClass);
    frame.getCombobox("COMBO_METHOD").setSelectedItem(tMethod);

    frame.getCheckbox("DBG_WARNING").setSelected(debugFlags.contains("WARN"));
    frame.getCheckbox("DBG_CALL"   ).setSelected(debugFlags.contains("CALLS"));
    frame.getCheckbox("DBG_INFO"   ).setSelected(debugFlags.contains("INFO"));
    frame.getCheckbox("DBG_OBJECTS").setSelected(debugFlags.contains("OBJECTS"));
    frame.getCheckbox("DBG_ARRAYS" ).setSelected(debugFlags.contains("ARRAYS"));
    frame.getCheckbox("DBG_COMMAND").setSelected(debugFlags.contains("COMMAND"));
    frame.getCheckbox("DBG_STACK"  ).setSelected(debugFlags.contains("STACK"));
    frame.getCheckbox("DBG_AGENT"  ).setSelected(debugFlags.contains("AGENT"));
    frame.getCheckbox("DBG_LOCALS" ).setSelected(debugFlags.contains("LOCAL"));
    frame.getCheckbox("DBG_BRANCH" ).setSelected(debugFlags.contains("BRANCH"));
    frame.getCheckbox("DBG_CONSTR" ).setSelected(debugFlags.contains("CONSTR"));
    frame.getCheckbox("DBG_SOLVER" ).setSelected(debugFlags.contains("SOLVE"));
  }

  /**
   * this sets up the DebugParams settings and properties file from the panel control values.
   * this is normally done when exiting the control panel.
   * 
   * @return true if one or more entries have changed
   */
  public boolean updateFromPanel() {
    change = false;
    String oldDebugFlags = debugFlags;
    
//    tInstr   = getCheckboxValue("TRIG_ON_INSTR", tInstr);
    tError   = getCheckboxValue("TRIG_ON_ERROR", tError);
    tExcept  = getCheckboxValue("TRIG_ON_EXCEPT", tExcept);
    tCall    = getCheckboxValue("TRIG_ON_CALL", tCall);
    tReturn  = getCheckboxValue("TRIG_ON_RETURN", tReturn);
    tRefW    = getCheckboxValue("TRIG_ON_REF_W", tRefW);
    tAuto    = getCheckboxValue("AUTO_RESET", tAuto);
    tAnyMeth = getCheckboxValue("ANY_METHOD", tAnyMeth);

    // make sure we set range to 0 if Auto is disabled
    if (!tAuto) {
      tRange = 0;
    }
    
    tCount  = getSpinnerValue("SPIN_COUNT", tCount);
    tRefCount = getSpinnerValue("SPIN_REFCOUNT", tRefCount);
    tRange  = getSpinnerValue("SPIN_RANGE", tRange);
//    tInsNum = getSpinnerValue("SPIN_INSTR", tInsNum);
    tRefIndex   = getSpinnerValue("SPIN_REF", tRefIndex);
    
    tClass  = getComboboxValue("COMBO_CLASS", tClass);
    tMethod = getComboboxValue("COMBO_METHOD", tMethod);
    
    // get the current debug selections
    debugFlags = "";
    debugFlags += frame.getCheckbox("DBG_WARNING").isSelected() ? "WARN "    : "";
    debugFlags += frame.getCheckbox("DBG_CALL"   ).isSelected() ? "CALLS "   : "";
    debugFlags += frame.getCheckbox("DBG_INFO"   ).isSelected() ? "INFO "    : "";
    debugFlags += frame.getCheckbox("DBG_OBJECTS").isSelected() ? "OBJECTS " : "";
    debugFlags += frame.getCheckbox("DBG_ARRAYS" ).isSelected() ? "ARRAYS "  : "";
    debugFlags += frame.getCheckbox("DBG_COMMAND").isSelected() ? "COMMAND " : "";
    debugFlags += frame.getCheckbox("DBG_STACK"  ).isSelected() ? "STACK "   : "";
    debugFlags += frame.getCheckbox("DBG_AGENT"  ).isSelected() ? "AGENT "   : "";
    debugFlags += frame.getCheckbox("DBG_LOCALS" ).isSelected() ? "LOCAL "   : "";
    debugFlags += frame.getCheckbox("DBG_BRANCH" ).isSelected() ? "BRANCH "  : "";
    debugFlags += frame.getCheckbox("DBG_CONSTR" ).isSelected() ? "CONSTR "  : "";
    debugFlags += frame.getCheckbox("DBG_SOLVER" ).isSelected() ? "SOLVE "   : "";
    debugFlags = debugFlags.trim();
    if (!oldDebugFlags.equals(debugFlags)) {
      change = true;
    }

    // update properties file
    saveToProperties();

    return change;
  }
  
  /**
   * this returns the value of a specified DebugParams settings based on the value to save in danfig.
   * 
   * @param tag - the danfig param tag
   * @return the corresponding parameter value
   */
  public String getValue(String tag) {
    // make sure we set range to 0 if Auto is disabled
    if (!tAuto) {
      tRange = 0;
    }
    
    switch (tag) {
      case "TriggerOnInstr":      return tInstr   ? "1" : "0";
      case "TriggerOnError":      return tError   ? "1" : "0";
      case "TriggerOnException":  return tExcept  ? "1" : "0";
      case "TriggerOnCall":       return tCall    ? "1" : "0";
      case "TriggerOnReturn":     return tReturn  ? "1" : "0";
      case "TriggerOnRefWrite"  : return tRefW    ? "1" : "0";
      case "TriggerAuto":         return tAuto    ? "1" : "0";
      case "TriggerAnyMeth":      return tAnyMeth ? "1" : "0";
      
      case "TriggerCount":        return "" + tCount;
      case "TriggerRefCount":     return "" + tRefCount;
      case "TriggerRange":        return "" + tRange;
      case "TriggerRefIndex":     return "" + tRefIndex;
      case "TriggerInstruction":  return "" + tInsNum;

      case "TriggerClass":        return tClass;
      case "TriggerMethod":       return tMethod;
      
      case "DebugFlags":          return debugFlags;
    }

    return "";
  }
  
  /**
   * this saves the DebugParams settings to the properties file
   */
  private void saveToProperties() {
    if (props == null) {
      Utils.printStatusError("DebugParams.saveToProperties: no properties file defined!");
      return;
    }
    
    // make sure we set range to 0 if Auto is disabled
    if (!tAuto) {
      tRange = 0;
    }
    
    props.setPropertiesItem("TriggerOnInstr"     , tInstr   ? "1" : "0");
    props.setPropertiesItem("TriggerOnError"     , tError   ? "1" : "0");
    props.setPropertiesItem("TriggerOnException" , tExcept  ? "1" : "0");
    props.setPropertiesItem("TriggerOnCall"      , tCall    ? "1" : "0");
    props.setPropertiesItem("TriggerOnReturn"    , tReturn  ? "1" : "0");
    props.setPropertiesItem("TriggerOnRefWrite"  , tRefW    ? "1" : "0");
    props.setPropertiesItem("TriggerAuto"        , tAuto    ? "1" : "0");
    props.setPropertiesItem("TriggerAnyMeth"     , tAnyMeth ? "1" : "0");
    props.setPropertiesItem("TriggerClass"       , tClass);
    props.setPropertiesItem("TriggerMethod"      , tMethod);
    props.setPropertiesItem("TriggerCount"       , ((Integer) tCount).toString());
    props.setPropertiesItem("TriggerRefCount"    , ((Integer) tRefCount).toString());
    props.setPropertiesItem("TriggerRange"       , ((Integer) tRange).toString());
    props.setPropertiesItem("TriggerRefIndex"    , ((Integer) tRefIndex).toString());
    props.setPropertiesItem("TriggerInstruction" , ((Integer) tInsNum).toString());

    props.setPropertiesItem("DebugFlags", debugFlags);
  }

  /**
   * this loads the DebugParams settings from the properties file
   */
  private void setFromProperties() {
    if (props == null) {
      Utils.printStatusError("DebugParams.setFromProperties: no properties file defined!");
      return;
    }
    
//    tInstr    = props.getPropertiesItem("TriggerOnInstr"    , "0").equals("1");
    tError    = props.getPropertiesItem("TriggerOnError"    , "0").equals("1");
    tExcept   = props.getPropertiesItem("TriggerOnException", "0").equals("1");
    tCall     = props.getPropertiesItem("TriggerOnCall"     , "0").equals("1");
    tReturn   = props.getPropertiesItem("TriggerOnReturn"   , "0").equals("1");
    tRefW     = props.getPropertiesItem("TriggerOnRefWrite" , "0").equals("1");
    tAuto     = props.getPropertiesItem("TriggerAuto"       , "0").equals("1");
    tAnyMeth  = props.getPropertiesItem("TriggerAnyMeth"    , "0").equals("1");

    // make sure we set range to 0 if Auto is disabled
    if (!tAuto) {
      tRange = 0;
    }
    
    tClass    = props.getPropertiesItem   ("TriggerClass"      , "");
    tMethod   = props.getPropertiesItem   ("TriggerMethod"     , "");
    tCount    = props.getIntegerProperties("TriggerCount"      , 1, 1, 10000);
    tRefCount = props.getIntegerProperties("TriggerRefCount"   , 1, 1, 10000);
    tRange    = props.getIntegerProperties("TriggerRange"      , 0, 0, 100000);
    tRefIndex = props.getIntegerProperties("TriggerRefIndex"   , 0, 0, 9999);
//    tInsNum   = props.getIntegerProperties("TriggerInstruction" , 1, 1, 10000);

    debugFlags  = props.getPropertiesItem("DebugFlags", "");
  }
    
  private boolean getCheckboxValue(String widget, boolean value) {
    if (frame == null) {
      Utils.printStatusError("getCheckboxValue <" + widget + "> - frame is null");
      return false;
    }
    
    boolean newval = frame.getCheckbox(widget).isSelected();
    if (newval != value) {
      change = true;
    }
    return newval;
  }

  private int getSpinnerValue(String widget, int value) {
    if (frame == null) {
      Utils.printStatusError("getSpinnerValue <" + widget + "> - frame is null");
      return 1;
    }
    
    int newval = (int) frame.getSpinner(widget).getModel().getValue();
    if (newval != value) {
      change = true;
    }
    return newval;
  }

  private String getComboboxValue(String widget, String value) {
    if (frame == null) {
      Utils.printStatusError("getComboboxValue <" + widget + "> - frame is null");
      return "";
    }
    
    String newval = (String) frame.getCombobox(widget).getSelectedItem();
    if (!newval.equals(value)) {
      change = true;
    }
    return newval;
  }
  
}
