/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import main.LauncherMain;

/**
 *
 * @author dan
 */
public class Utils {
  
  public static final String NEWLINE = System.getProperty("line.separator");
  
  public static void printCommandMessage(String message) {
    LauncherMain.printCommandError(message);
    //System.out.println(message);
  }
  
  public static void printCommandError(String message) {
    LauncherMain.printCommandError(message);
    //System.err.println(message);
  }
  
  public static String readTextFile(String filename) {

    String content = "";
    File file = new File(filename);
    if (!file.isFile()) {
      printCommandError("ERROR: file not found: " + filename);
    } else {
      try {
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          content += line + NEWLINE;
        }
      } catch (IOException ex) {
        printCommandError("ERROR: " + ex.getMessage());
      }
    }

    return content;
  }

  public static void saveTextFile(String filename, String content) {
    // make sure all dir paths exist
    int offset = filename.lastIndexOf("/");
    if (offset > 0) {
      String newpathname = filename.substring(0, offset);
      File newpath = new File(newpathname);
      if (!newpath.isDirectory()) {
        newpath.mkdirs();
      }
    }
    
    // delete file if it already exists
    File file = new File(filename);
    if (file.isFile()) {
      file.delete();
    }
    
    // create a new file and copy text contents to it
    BufferedWriter bw = null;
    try {
      FileWriter fw = new FileWriter(file);
      bw = new BufferedWriter(fw);
      bw.write(content);
    } catch (IOException ex) {
      printCommandError("ERROR: " + ex.getMessage());
    } finally {
      if (bw != null) {
        try {
          bw.close();
        } catch (IOException ex) {
          printCommandError("ERROR: " + ex.getMessage());
        }
      }
    }
  }
  
  /**
   * reads the CallGraph.graphMethList entries and saves to file
   * 
   * @param object - the object to save as a JSON formatted file
   * @param file - name of file to save content to
   */  
  public static void saveAsJSONFile(Object object, File file) {
    // open the file to write to
    BufferedWriter bw;
    try {
      bw = new BufferedWriter(new FileWriter(file));
    } catch (IOException ex) {
      printCommandError("ERROR: " + ex.getMessage());
      return;
    }

    // convert to json and save to file
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting().serializeNulls();
    //builder.excludeFieldsWithoutExposeAnnotation().create();
    Gson gson = builder.create();
    gson.toJson(object, bw);

    try {
      bw.close();
    } catch (IOException ex) {
      printCommandError("ERROR: " + ex.getMessage());
    }
  }
  
  public static String convertDataType(String dtype) {
    String valtype = "REF";
      
    // standardize the data type entry
    if (dtype.startsWith("L")) {
      dtype = dtype.substring(1);
    }
    if (dtype.endsWith(";")) {
      dtype = dtype.substring(0, dtype.length() - 1);
    }
    dtype = dtype.replaceAll("/", ".");
      
    // now convert it to what we require
    switch (dtype) {
      case "Z":
      case "java.lang.Boolean":
        valtype = "Z";
        break;
      case "C":
      case "java.lang.Char":
        valtype = "C";
        break;
      case "B":
      case "java.lang.Byte":
        valtype = "B";
        break;
      case "S":
      case "java.lang.Short":
        valtype = "S";
        break;
      case "I":
      case "java.lang.Integer":
        valtype = "I";
        break;
      case "J":
      case "java.lang.Long":
        valtype = "J";
        break;
      case "F":
      case "java.lang.Float":
        valtype = "F";
        break;
      case "D":
      case "java.lang.Double":
        valtype = "D";
        break;
      case "java.lang.String":
        valtype = "STR";
        break;
      default:
        break;
    }

    return valtype;
  }
    
  public static class ClassMethodName {
    public String className;    // name of the class along with the package path
    public String methName;     // name of the method - no class or signature info
    public String signature;    // the signature of the method (arguments passed & return type)

    public ClassMethodName(String fullName) {
      // eliminate the leading L that is sometimes attached to the start of the method
      if (fullName.startsWith("L")) {
        fullName = fullName.substring(1);
      }

      // next, extract the signature so we don't confuse any "/" chars in it with the class path
      className = fullName;
      int offset = className.lastIndexOf("(");
      if (offset >= 0) {
        signature = className.substring(offset);
        className = className.substring(0, offset);
      }

      // now find the method name and extract it
      if (className.contains(".")) {
        // the case where the fullname seperates the method from the class with a "."
        offset = className.lastIndexOf(".");
      } else {
        offset = className.lastIndexOf("/");
      }
      if (offset >= 0) {
        methName = className.substring(offset + 1);
        className = className.substring(0, offset);
      } else {
        methName = className;
        className = "";
      }
    }
  }

  /**
   * Checks to see if a server is running on the specified port.
   * 
   * @param port - the port number to check if in use.
   * @return true if server is running
   */
  public static boolean isServerRunning(int port) {
    ServerSocket ss = null;
    try {
      ss = new ServerSocket(port);
      return false;
    } catch (IOException e) {
      // ignore error
    } finally {
      if (ss != null) {
        try {
          ss.close();
        } catch (IOException e) {
          // ignore error
        }
      }
    }

    return true;
}

}
