/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.io.File;
import util.Utils;

/**
 *
 * @author dan
 */
public class GenerateTestScript {

  private GenerateTestScript(String jsonfname, String outfname) {
    Recorder recorder = new Recorder();
    recorder.loadFromJSONFile(new File(jsonfname));
    String content = recorder.generateTestScript();
    Utils.saveTextFile(outfname, content);
  }
  
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // generate content
    // TODO: get filename arguments
    if (args.length < 2) {
      Utils.printStatusMessage("Usage:");
      Utils.printStatusMessage("  GenerateTestScript <JSON filename> <script filename>");
      Utils.printStatusMessage("Where:");
      Utils.printStatusMessage("  <JSON filename>   = the name of the JSON file to read");
      Utils.printStatusMessage("  <script filename> = the name of the file to produce");
      return;
    }
    
    String currentDir = System.getProperty("user.dir");
    String jsonFile = args[0];
    if (!jsonFile.startsWith("/")) {
      jsonFile = currentDir + "/" + jsonFile;
    }
    Utils.printStatusMessage("Reading from: " + jsonFile);
    
    String scriptFile = args[1];
    if (!scriptFile.startsWith("/")) {
      scriptFile = currentDir+ "/"  + scriptFile;
    }
    Utils.printStatusMessage("Writing to: " + scriptFile);
    
    GenerateTestScript script = new GenerateTestScript(jsonFile, scriptFile);
  }
  
}
