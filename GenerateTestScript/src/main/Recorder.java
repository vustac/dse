/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import util.Utils;

/**
 *
 * @author dan
 */
public class Recorder {
  
  // Commands that can be saved in record session
  public enum RecordID {
    STOP,               // terminate the application
    DELAY,              // delay in seconds
    WAIT_FOR_TERM,      // wait until application has terminated
    WAIT_FOR_SERVER,    // wait until server has come up
    SET_HTTP,           // set the HTTP port to use
    SEND_HTTP_POST,     // send POST-formatted message to HTTP port
    SEND_HTTP_GET,      // send GET-formatted  message to HTTP port
    SEND_STDIN,         // send message to standard input
    EXTRACT_SOLUTIONS,  // extract the solutions to be chacked
    EXPECTED_PARAM,     // specify the expected parameter (single or multiple param name & value)
    SHOW_RESULTS,       // show the final pass/fail status
    SYMBOLIC_PARAM,     // define a symbolic parameter
  }
  
  private static boolean         recordState;
  private static long            recordStartTime;
  private static RecordID        recordLastCommand;
  private static List<RecordID>  recordCommandList;
  private static RecordInfo      recording;
  
  public Recorder() {
    recordState = false;
    recordStartTime = 0;
    recordLastCommand = RecordID.STOP;
    recordCommandList = new ArrayList<>();
    recording = new RecordInfo();
  }

  /**
   * check if we have recorded anything
   * 
   * @return true if we have recorded content
   */
  public boolean isEmpty() {
    return recording.commandlist.isEmpty();
  }
  
  /**
   * start the recorder
   */
  public void startRecording() {
    if (recordState) {
      Utils.printCommandMessage("--- previous RECORD session terminated");
    }
    Utils.printCommandMessage("--- RECORD session started ---");
    recording.commandlist.clear();
    recordCommandList.clear();
    recordStartTime = 0;
    recordState = true;
  }
  
  /**
   * stop the recorder
   */
  public void stopRecording() {
    if (recordState) {
      // if we haven't added the show status results command yet, do it now
      addCommand(RecordID.SHOW_RESULTS, null);
      
      Utils.printCommandMessage("--- RECORD session terminated ---");
      recordState = false;
    }
  }
  
  /**
   * begin the test.
   * (the RUN button was pressed or the test was already running when recording started)
   * 
   * @param testname - name of the test being run
   * @param arglist  - argument list for the test
   */
  public void beginTest(String testname, String arglist) {
    if (recordState) {
      // init the start time for the next command
      recordStartTime = System.currentTimeMillis();
      recording.commandlist.clear();
      recordCommandList.clear();
      recording.setTestName(testname, arglist);
    }
  }

  /**
   * clear the list of symbolic parameters
   */
  public void clearSymbolics() {
    recording.symbolicList.clear();
  }
  
  /**
   * add the specified symbolic parameter.
   * 
   * @param method - the method for the parameter
   * @param name   - the nae of the parameter
   * @param type   - the data type
   * @param slot   - the slot it occupies
   * @param start  - the starting instruction offset from the start of method where param is in scope
   * @param end    - the ending instruction offset from the start of method where param is in scope
   */
  public void addSymbolicParam(String method, String name, String type, String slot, String start, String end) {
    if (recordState) {
      recording.addSymbolic(method, name, type, slot, start, end);
    }
  }
  
  /**
   * add the specified command and argument that was executed (1 argument).
   * 
   * @param cmd - the command executed
   * @param arg - the associated argument (or null if none)
   */
  public void addCommand(RecordID cmd, String arg) {
    if (recordState && recordStartTime != 0) {
      // if last command was EXPECTED_PARAM and this command isn't, let's terminate the expected tests
      if (RecordID.EXPECTED_PARAM == recordLastCommand && RecordID.EXPECTED_PARAM != cmd) {
        RecordID newcmd = RecordID.SHOW_RESULTS;
        recording.addCommand(newcmd, new RecordCommand(newcmd, null));
      }

      recording.addCommand(cmd, new RecordCommand(cmd, arg));
    }
  }

  /**
   * add the specified command and argument that was executed (no arguments).
   * 
   * @param cmd - the command executed
   */
  public void addCommand(RecordID cmd) {
    addCommand(cmd, null);
  }

  /**
   * add the specified expected parameter command specifying the name and value of the parameter.
   * 
   * @param name  - the name of the parameter
   * @param value - the value of the parameter
   */
  public void addExpectedParam(String name, String value) {
    if (recordState && recordStartTime != 0) {
      // if EXPECTED_PARAM not in record list (this is the first), preceed it with EXTRACT command
      if (!recordCommandList.contains(RecordID.EXPECTED_PARAM)) {
        addCommand(RecordID.EXTRACT_SOLUTIONS, null);
      }
    
      // add the command
      recording.addCommand(RecordID.EXPECTED_PARAM, new RecordExpectedSingle(name, value));
    }
  }
  
  /**
   * add the specified expected parameter command specifying the name and value of the parameter.
   * 
   * @param paramlist  - list of the parameters defined for a solution
   */
  public void addExpectedParams(ArrayList<ParameterInfo> paramlist) {
    if (recordState && recordStartTime != 0) {
      // if EXPECTED_PARAM not in record list (this is the first),  preceed it with EXTRACT command
      if (!recordCommandList.contains(RecordID.EXPECTED_PARAM)) {
        addCommand(RecordID.EXTRACT_SOLUTIONS, null);
      }
    
      recording.addCommand(RecordID.EXPECTED_PARAM, new RecordExpectedMulti(paramlist));
    }
  }
  
  /**
   * add a delay command (based on the elapsed time from the last command)
   */
  public void addDelay() {
    if (recordState && recordStartTime != 0) {
      // determine the delay time (ignore if it was < 1 sec or start wasn't initialized)
      if (recordStartTime != 0) {
        long elapsed = 1 + (System.currentTimeMillis() - recordStartTime) / 1000;
        if (elapsed > 1) {
          addCommand(RecordID.DELAY, "" + elapsed);
        }
      }
    }
  }
  
  /**
   * add a delay for writing to http port (will either do a normal delay or wait for the server
   * to be listening on the specified port.
   * 
   * @param port - the port to check
   */
  public void addHttpDelay(String port) {
    if (recordState && recordStartTime != 0) {
      if (recordCommandList.contains(RecordID.SET_HTTP)) {
        // if we have already established the server port and waited for it, just do a delay
        addDelay();
      } else {
        // if 1st time on HTTP, indicate the port selection and wait till server is up
        addCommand(RecordID.SET_HTTP, port);
        addCommand(RecordID.WAIT_FOR_SERVER, null);
      }
    }
  }
  
  /**
   * generate the test configuration file (JSON) from the list of commands and symbolics.
   * 
   * @param filename - the name of the file to save the data to
   */
  public void generateConfigFile(String filename) {
      Utils.saveAsJSONFile(recording, new File(filename));
  }
  
  /**
   * read from JSON file into recording
   * 
   * @param file - name of file to load data from
   */  
  public void loadFromJSONFile(File file) {
    // open the file to read from
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(file));
    } catch (FileNotFoundException ex) {
      System.err.println(ex.getMessage());
      return;
    }
    
    // load the method list info from json file
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
    recording = gson.fromJson(br, new TypeToken<RecordInfo>() {}.getType());
  }
  
  /**
   * generate a test script from the list of commands.
   * 
   * @return a String consisting of the test script
   */
  public String generateTestScript() {
    // create a panel containing the script to run for testing
    String content = "";
    String httpport = "";
    content += "TESTNAME=\"" + recording.testname + "\"" + Utils.NEWLINE;
    content += Utils.NEWLINE;
    content += "# initialize the pass/fail status to pass and save the pid of the running process" + Utils.NEWLINE;
    content += "STATUS=0" + Utils.NEWLINE;
    content += "PID=$1" + Utils.NEWLINE;
    content += "echo \"pid = ${PID}\"" + Utils.NEWLINE;
    content += Utils.NEWLINE;

    for (Object objitem : recording.commandlist) {
      if (objitem instanceof RecordCommand) {
        // this is a normal command (0 or 1 arguments)
        RecordCommand entry = (RecordCommand) objitem;
        content += getCommandOutput(entry.command.toString(), entry.argument, "");
      } else if (objitem instanceof RecordExpectedSingle) {
        // this is for single param in a solution
        RecordExpectedSingle param = (RecordExpectedSingle) objitem;
        content += getCommandOutput(param.command.toString(), param.name, param.value);
      } else if (objitem instanceof RecordExpectedMulti) {
        // this is for multiple params in a solution
        content += "check_solution ";
        RecordExpectedMulti param = (RecordExpectedMulti) objitem;
        for (ParameterInfo entry : param.paramlist) {
          content += "\"" + entry.name + "\" \"" + entry.value + "\" ";
        }
        content += Utils.NEWLINE;
      } else if (objitem instanceof LinkedTreeMap) {
        // this handles the case of reading the structure in from the json file
        LinkedTreeMap map = (LinkedTreeMap) objitem;
        if (map.size() < 2 || !map.containsKey("command")) {
          Utils.printCommandError("ERROR: Invalid map type: " + objitem.getClass().getName());
          continue;
        }
        String cmd = (String) map.get("command");
        String arg1 = "";
        String arg2 = "";
        if (map.containsKey("argument")) {
          arg1 = (String) map.get("argument");
        } else if (map.containsKey("name")) {
          arg1 = (String) map.get("name");
        }
        if (map.containsKey("value")) {
          arg2 = (String) map.get("value");
        }
        content += getCommandOutput(cmd, arg1, arg2);
      } else {
        Utils.printCommandError("ERROR: Unhandled command class type: " + objitem.getClass().getName());
      }
    }

    return content;
  }

  private static String getCommandOutput(String id, String arg1, String arg2) {
    String content = "";
    switch(id) {
      case "STOP":
        content += "# terminate process (this one doesn't auto-terminate)" + Utils.NEWLINE;
        content += "kill -15 ${PID} > /dev/null 2>&1" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "DELAY":
        content += "# wait a short period" + Utils.NEWLINE;
        content += "sleep " + arg1 + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "WAIT_FOR_TERM":
        content += "# wait for application to complete" + Utils.NEWLINE;
        content += "wait_for_app_completion 5 ${PID}" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "WAIT_FOR_SERVER":
        content += "# wait for server to start then post message to it" + Utils.NEWLINE;
        content += "wait_for_server 10 ${PID} ${SERVERPORT}" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "SET_HTTP":
        content += "# http port and message to send" + Utils.NEWLINE;
        content += "SERVERPORT=\"" + arg1 + "\"" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "SEND_HTTP_POST":
        content += "# send the HTTP POST" + Utils.NEWLINE;
        content += "echo \"Sending message to application\"" + Utils.NEWLINE;
        content += "curl -d \"" + arg1 + "\" http://localhost:${SERVERPORT}" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "SEND_HTTP_GET":
        content += "# send the HTTP GET" + Utils.NEWLINE;
        content += "echo \"Sending message to application\"" + Utils.NEWLINE;
        content += "curl http://localhost:${SERVERPORT}" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "SEND_STDIN":
        content += "# send the message to STDIN" + Utils.NEWLINE;
        content += "echo \"Sending message to application\"" + Utils.NEWLINE;
        content += "echo \"" + arg1 + "\" > /proc/${PID}/fd/0" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "EXTRACT_SOLUTIONS":
        content += "# get solver response and check against expected solution" + Utils.NEWLINE;
        content += "echo \"Debug info: ${TESTNAME} database entry\"" + Utils.NEWLINE;
        content += "extract_solutions" + Utils.NEWLINE;
        break;
      case "SHOW_RESULTS":
        content += "show_results ${TESTNAME}" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "EXPECTED_PARAM":
        content += "check_solution \"" + arg1 + "\" \"" + arg2 + "\"" + Utils.NEWLINE;
        break;
    }

    return content;
  }
  
  // this defines the data for expected parameters so we can create an array of them for the case
  // of solutions that have more than one parameter defined.
  public static class ParameterInfo {
    private final String name;
    private final String value;
    
    public ParameterInfo(String name, String value) {
      this.name = name;
      this.value = value;
    }
  }

  // this defines the recorded information that can be saved as a config file.
  // NOTE: this file is saved as a JSON file for the test configuration, so nothing should be
  // in here that isn't relavant to the test.
  private class RecordInfo {
    private String testname;
    private String runargs;
    private final ArrayList<RecordSymbolicParam> symbolicList;
    private final ArrayList<Object> commandlist;
    
    private RecordInfo() {
      testname = "";
      runargs = "";

      // the lists that are saved (symbolic params and the commands to run)
      symbolicList = new ArrayList<>();
      commandlist = new ArrayList<>();
    }
    
    private void setTestName(String name, String args) {
      testname = name;
      runargs = args;
    }
    
    private void addSymbolic(String method, String name, String type, String slot, String start, String end) {
      symbolicList.add(new RecordSymbolicParam(method, name, type, slot, start, end));
    }
    
    private void addCommand(RecordID cmd, Object item) {
      // skip termination indication if user issued the STOP cmd (we don't need to wait)
      if (RecordID.WAIT_FOR_TERM == cmd && recordCommandList.contains(RecordID.STOP)) {
        return;
      }

      // make sure we do not duplicate a SHOW_RESULTS command
      if (RecordID.SHOW_RESULTS == cmd && recordCommandList.contains(RecordID.SHOW_RESULTS)) {
        return;
      }
      
      // add the command
      Utils.printCommandMessage("--- RECORDED: " + cmd.toString());
      commandlist.add(item);
      recordCommandList.add(cmd);
      recordLastCommand = cmd; // save last command

      // get start of delay to next command
      recordStartTime = System.currentTimeMillis();
    }
  }

  
  // The class for standard commands
  private static class RecordCommand {
    public RecordID command;
    public String   argument;
    
    private RecordCommand(RecordID cmd, String arg) {
      this.command = cmd;
      this.argument = (arg == null) ? "" : arg;
    }
  }

  // the class for EXPECTED_PARAM command
  private static class RecordExpectedSingle {
    private final RecordID command;
    private final String   name;
    private final String   value;
    
    private RecordExpectedSingle(String name, String value) {
      this.command = RecordID.EXPECTED_PARAM;
      this.name = name;
      this.value = value;
    }
  }

  // the class for EXPECTED_PARAM command
  private static class RecordExpectedMulti {
    private final RecordID command;
    private final ArrayList<ParameterInfo> paramlist;
    
    private RecordExpectedMulti(ArrayList<ParameterInfo> paramlist) {
      this.command = RecordID.EXPECTED_PARAM;
      this.paramlist = paramlist;
    }
  }

  // the class for defining Symbolic Parameters
  private static class RecordSymbolicParam {
    private final RecordID command;
    private final String   method;
    private final String   name;
    private final String   type;
    private final String   slot;
    private final String   start;
    private final String   end;
    
    private RecordSymbolicParam(String method, String name, String type, String slot, String start, String end) {
      this.command = RecordID.SYMBOLIC_PARAM;
      this.method = method;
      this.name = name;
      this.type = type;
      this.slot = slot;
      this.start = start;
      this.end = end;
    }
  }

}
