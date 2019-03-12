/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 *
 * @author dan
 */
public class UserParams {
  private static String groupName;          // name of parameter group
  private static PropertiesFile propTable;  // the PropertiesFile that holds the group
  private boolean       changed;            // true if any parameter values were changed by guiCtrl
  
  // this contains all the parameters and their respective references and default values
  private Map<String, ParameterItem> paramTable = new HashMap<>();
  
  private enum ParamType { INTEGER, BOOLEAN, STRING }
  
  private class ParameterItem {
    public String         propTag;    // name assigned to entry in properties file (also key used for lookup)
    public Component      guiCtrl;    // the gui control widget for the parameter
    public ParamType      paramType;  // the type of parameter
    public String         defVal;     // default value for parameter
    public int            idefVal;    // the default value saved as an integer for JSpinner types
    public Object         value;      // the parameter
    public boolean        changed;    // true if the parameter value was changed by guiCtrl

    private ParameterItem(String ptag, Component control, String dflt) {
      // verify gui component is valid and specify data type
      idefVal = 0;
      if (control instanceof JLabel || control instanceof JTextField || control instanceof JComboBox) {
        paramType = ParamType.STRING;
        value = dflt;
      } else if (control instanceof JCheckBox || control instanceof JRadioButton) {
        paramType = ParamType.BOOLEAN;
        value = Boolean.valueOf(dflt);
      } else if (control instanceof JSpinner) {
        paramType = ParamType.INTEGER;
        value = new Integer(dflt);
        try {
          idefVal = Integer.parseInt(dflt);
        } catch (NumberFormatException ex) {
          printFatal("ParameterItem: " + ptag + " invalid default value (must be int): " + dflt);
        }
      } else {
        printFatal("ParameterItem: " + ptag + " not a valid gui control type");
        return;
      }
      
      propTag = ptag;
      guiCtrl = control;
      defVal = dflt;
      changed = false;
    }
    
  }
  
  public UserParams(String name, PropertiesFile proptbl) {
    // save associated control panel
    groupName = name;
    propTable = proptbl;
    
    // define the parameters
    // TODO: these are called externally
//    addParam("SolverMethod", control, "None");
  }
  
  /**
   * allows setting the PropertiesFile associated with a group after the group has been created.
   * 
   * @param proptbl - the properties file associated with this group of parameters
   */
  public void setPropertiesTable(PropertiesFile proptbl) {
    propTable = proptbl;
  }
  
  /**
   * initialize a parameter value
   * 
   * @param ptag    - the tag to use for accessing the parameter
   * @param control - the gui control associated with the parameter
   * @param dflt    - the default value for the parameter
   */
  public void addParam(String ptag, Component control, String dflt) {
    if (paramTable.containsKey(ptag)) {
      printError("defineParam: " + ptag + " already defined in param group " + groupName);
      return;
    }
    paramTable.put(ptag, new ParameterItem(ptag, control, dflt));
  }
  
  private void printError(String message) {
    LauncherMain.printCommandError("ERROR: UserParams." + message);
  }
  
  private void printFatal(String message) {
    printError("FATAL " + message);
    System.exit(1);
  }
  
  /**
   * this returns the value of a specified DebugParams settings based on the value to save in danfig.
   * 
   * @param tag - the param tag
   * @return the corresponding parameter value
   */
  public String getValue(String tag) {
    String retval = "";
    if (paramTable.containsKey(tag)) {
      ParameterItem item = paramTable.get(tag);
      switch (item.paramType) {
        case INTEGER:
          retval = ((Integer) item.value).toString();
          break;
        case BOOLEAN:
          retval = ((Boolean) item.value) ? "1" : "0";
          break;
        case STRING:
          retval = (String) item.value;
          break;
      }
    }

    return retval;
  }

  /**
   * this sets up the control values on the panel from the saved parameter settings
   */
  public void saveToPanel() {
    // save each value from the paramTable value to the gui control for it
    for (Map.Entry<String, ParameterItem> entry : paramTable.entrySet()) {
      ParameterItem item = entry.getValue();
      if (item.guiCtrl instanceof JLabel) {
        ((JLabel)item.guiCtrl).setText((String) item.value);
      } else if (item.guiCtrl instanceof JTextField) {
        ((JTextField)item.guiCtrl).setText((String) item.value);
      } else if (item.guiCtrl instanceof JComboBox) {
        ((JComboBox)item.guiCtrl).setSelectedItem(((String) item.value));
      } else if (item.guiCtrl instanceof JCheckBox) {
        ((JCheckBox)item.guiCtrl).setSelected((Boolean) item.value);
      } else if (item.guiCtrl instanceof JRadioButton) {
        ((JRadioButton)item.guiCtrl).setSelected((Boolean) item.value);
      } else if (item.guiCtrl instanceof JSpinner) {
        ((JSpinner)item.guiCtrl).getModel().setValue((Integer) item.value);
      } else {
        printFatal("saveToPanel: " + item.propTag + " not a valid gui control type");
      }
    }
  }

  /**
   * this sets up the parameter settings and properties file from the panel control values.
   * this is normally done when exiting the control panel.
   * 
   * @return true if one or more entries have changed
   */
  public boolean loadFromPanel() {
    changed = false;

//    tInstr   = getCheckboxValue("TRIG_ON_INSTR", tInstr);

    // read each value from the gui control and save to the paramTable
    for (Map.Entry<String, ParameterItem> entry : paramTable.entrySet()) {
      ParameterItem item = entry.getValue();
      if (item.guiCtrl instanceof JLabel) {
        item.value = ((JLabel)item.guiCtrl).getText();
      } else if (item.guiCtrl instanceof JTextField) {
        item.value = ((JTextField)item.guiCtrl).getText();
      } else if (item.guiCtrl instanceof JComboBox) {
        item.value = ((JComboBox)item.guiCtrl).getSelectedItem();
      } else if (item.guiCtrl instanceof JCheckBox) {
        item.value = ((JCheckBox)item.guiCtrl).isSelected();
      } else if (item.guiCtrl instanceof JRadioButton) {
        item.value = ((JRadioButton)item.guiCtrl).isSelected();
      } else if (item.guiCtrl instanceof JSpinner) {
        item.value = ((JSpinner)item.guiCtrl).getModel().getValue();
      } else {
        printFatal("saveToPanel: " + item.propTag + " not a valid gui control type");
      }
    }

    // update properties file
    saveToProperties();

    return changed;
  }
  
  /**
   * this saves the DebugParams settings to the properties file
   */
  private void saveToProperties() {
    if (propTable == null) {
      printError("saveToProperties: no properties file defined!");
      return;
    }
    
//    props.setPropertiesItem("SolverMethod", solverMethod);
    for (Map.Entry<String, ParameterItem> entry : paramTable.entrySet()) {
      ParameterItem item = entry.getValue();

      String value;
      switch (item.paramType) {
        case INTEGER:
          value = ((Integer) item.value).toString();
          break;
        case BOOLEAN:
          value = ((Boolean) item.value) ? "1" : "0";
          break;
        default:
        case STRING:
          value = (String) item.value;
          break;
      }
      propTable.setPropertiesItem(item.propTag, value);
    }
  }

  /**
   * this loads the DebugParams settings from the properties file
   */
  private void loadFromProperties() {
    if (propTable == null) {
      printError("loadFromProperties: no properties file defined!");
      return;
    }

//    solverMethod = props.getPropertiesItem("SolverMethod", "None");
    for (Map.Entry<String, ParameterItem> entry : paramTable.entrySet()) {
      ParameterItem item = entry.getValue();
      String value = propTable.getPropertiesItem(item.propTag, item.defVal);
      switch (item.paramType) {
        case INTEGER:
          try {
            item.value = Integer.parseInt(value);
          } catch (NumberFormatException ex) {
            printError("loadFromProperties: invalid numeric for '" + item.propTag + "' = " + value);
            propTable.setPropertiesItem(item.propTag, item.defVal);
            item.value = item.idefVal;
          }
          break;
        case BOOLEAN:
          item.value = "1".equals(value);
          break;
        default:
        case STRING:
          item.value = value;
          break;
      }
    }
  }
  
}
