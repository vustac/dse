/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danalyzer.gui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * @author dmcd2356
 */
public class PropertiesFile {
  // the location of the properties file for this application
  final static private String PROPERTIES_PATH = ".danalyzer/"; // System.getProperty("user.home") + "/.danalyzer/";
  final static private String PROPERTIES_FILE = "site.properties";

  private Properties   props;

  PropertiesFile () {
      
    props = null;
    FileInputStream in = null;
    File propfile = new File(PROPERTIES_PATH + PROPERTIES_FILE);
    if (propfile.exists()) {
      try {
        // property file exists, read it in
        in = new FileInputStream(PROPERTIES_PATH + PROPERTIES_FILE);
        props = new Properties();
        props.load(in);
        return; // success!
      } catch (FileNotFoundException ex) {
        System.err.println(ex + " <" + PROPERTIES_PATH + PROPERTIES_FILE + ">");
      } catch (IOException ex) {
        System.err.println(ex + " <" + PROPERTIES_PATH + PROPERTIES_FILE + ">");
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch (IOException ex) {
            System.err.println(ex + " <" + PROPERTIES_PATH + PROPERTIES_FILE + ">");
          }
        }
      }
    }

    // property file does not exist - create a default (empty) one
    props = new Properties();
    try {
      // first, check if properties directory exists
      File proppath = new File (PROPERTIES_PATH);
      if (!proppath.exists()) {
        proppath.mkdir();
      }

      // now save properties file
      System.out.println("Creating new empty site.properties file.");
      File file = new File(PROPERTIES_PATH + PROPERTIES_FILE);
      try (FileOutputStream fileOut = new FileOutputStream(file)) {
        props.store(fileOut, "Initialization");
      }
    } catch (IOException ex) {
      System.err.println(ex + " <" + PROPERTIES_PATH + PROPERTIES_FILE + ">");
      props = null;
    }
  }
    
  public String getPropertiesItem (String tag, String dflt) {
    if (props == null || tag == null || tag.isEmpty()) {
      return dflt;
    }

    String value = props.getProperty(tag);
    if (value == null || (value.isEmpty() && !dflt.isEmpty())) {
      System.err.println("site.properties <" + tag + "> : not found, setting to " + dflt);
      return dflt;
    }

    //System.out.println("site.properties <" + tag + "> = " + value);
    return value;
  }
  
  public void setPropertiesItem (String tag, String value) {
    // save changes to properties file
    if (props == null || tag == null || tag.isEmpty()) {
      return;
    }

    // make sure the properties file exists
    File propsfile = new File(PROPERTIES_PATH + PROPERTIES_FILE);
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
          System.out.println("site.properties <" + tag + "> set to " + value);
        }
        props.setProperty(tag, value);
        FileOutputStream out = new FileOutputStream(PROPERTIES_PATH + PROPERTIES_FILE);
        props.store(out, "---No Comment---");
        out.close();
      } catch (FileNotFoundException ex) {
        System.err.println(ex + "- site.properties");
      } catch (IOException ex) {
        System.err.println(ex + "- site.properties");
      }
    }
  }  
}
