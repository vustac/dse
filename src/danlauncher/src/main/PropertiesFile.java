/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import util.Utils;

/**
 *
 * @author dmcd2356
 */
public class PropertiesFile {
  // the location of the properties file for this application
  private String propertiesName;
  private String propPath;
  private String propFile;
  private Properties props;

  PropertiesFile (String path, String name) {

    propertiesName = name;
    propPath = path.endsWith("/") ? path : path + "/";
    propFile = propPath + "site.properties";
    props = null;

    FileInputStream in = null;
    File propfile = new File(propFile);
    if (propfile.exists()) {
      try {
        // property file exists, read it in
        in = new FileInputStream(propFile);
        props = new Properties();
        props.load(in);
        Utils.printStatusInfo(propertiesName + " found at: " + propPath);
        return; // success!
      } catch (FileNotFoundException ex) {
        Utils.printStatusError(propertiesName + " not found for: " + propPath);
      } catch (IOException ex) {
        Utils.printStatusError("PropertiesFile: IOException on FileInputStream - " + propFile);
        return;
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch (IOException ex) {
            Utils.printStatusError("PropertiesFile: IOException on close - " + propFile);
          }
        }
      }
    }

    // property file does not exist - create a default (empty) one
    props = new Properties();
    try {
      // first, check if properties directory exists
      File proppath = new File (propPath);
      if (!proppath.exists()) {
        proppath.mkdir();
      }

      // now save properties file
      Utils.printStatusInfo(propertiesName + " - created new site.properties file");
      File file = new File(propFile);
      try (FileOutputStream fileOut = new FileOutputStream(file)) {
        props.store(fileOut, "Initialization");
      }
    } catch (IOException ex) {
      Utils.printStatusError("PropertiesFile: IOException on FileOutputStream - " + propFile);
      props = null;
    }
  }
    
  public boolean isPropertiesDefined (String tag) {
    if (props != null && tag != null && !tag.isEmpty()) {
      String value = props.getProperty(tag);
      if (value != null) {
        return true;
      }
    }
    return false;
  }
  
  public String getPropertiesItem (String tag, String dflt) {
    if (props == null || tag == null || tag.isEmpty()) {
      return dflt;
    }

    String value = props.getProperty(tag);
    if (value == null) {
      Utils.msgLogger(Utils.LogType.INFO, propertiesName + " site.properties <" + tag + "> : not found, setting to '" + dflt + "'");
      setPropertiesItem (tag, dflt);
      return dflt;
    }

    Utils.msgLogger(Utils.LogType.INFO, propertiesName + " <" + tag + "> = '" + value + "'");
    return value;
  }
  
  public void setPropertiesItem (String tag, String value) {
    // save changes to properties file
    if (props == null || tag == null || tag.isEmpty()) {
      return;
    }

    // make sure the properties file exists
    File propsfile = new File(propFile);
    if (propsfile.exists()) {
      try {
        if (value == null) {
          value = "";
        }
        String old_value = props.getProperty(tag);
        if (old_value == null) {
          old_value = "";
        }
        if (!old_value.equals(value)) {
          Utils.printStatusInfo(propertiesName + " <" + tag + "> set to '" + value + "'");
        }
        props.setProperty(tag, value);
        FileOutputStream out = new FileOutputStream(propFile);
        props.store(out, "---No Comment---");
        out.close();
      } catch (FileNotFoundException ex) {
        Utils.printStatusError("PropertiesFile: FileNotFoundException - " + propFile);
      } catch (IOException ex) {
        Utils.printStatusError("PropertiesFile: IOException on FileOutputStream - " + propFile);
      }
    }
  }  

  public int getIntegerProperties(String tag, int dflt, int min, int max) {
    int retval;
    String value = getPropertiesItem(tag, "" + dflt);
    try {
      retval = Integer.parseUnsignedInt(value);
      if (retval >= min && retval <= max) {
        return retval;
      }
    } catch (NumberFormatException ex) { }

    Utils.printStatusError("PropertiesFile: invalid value for SystemProperties " + tag + ": " + value);
    setPropertiesItem(tag, "" + dflt);
    return dflt;
  }
  
}
