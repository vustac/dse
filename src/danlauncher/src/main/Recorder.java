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
      Utils.printStatusMessage("--- previous RECORD session terminated");
    }
    Utils.printStatusMessage("--- RECORD session started ---");
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
      
      Utils.printStatusMessage("--- RECORD session terminated ---");
      recordState = false;
    }
  }
  
  /**
   * begin the test.
   * (the RUN button was pressed or the test was already running when recording started)
   * 
   * @param testname - name of the test being run
   * @param arglist  - argument list for the test
   * @param mainClass - main class of program
   */
  public void beginTest(String testname, String arglist, String mainClass) {
    if (recordState) {
      // init the start time for the next command
      recordStartTime = System.currentTimeMillis();
      recording.commandlist.clear();
      recordCommandList.clear();
      recording.setTestName(testname, arglist, mainClass);
    }
  }

  /**
   * clear the list of symbolic parameters
   */
  public void clearSymbolics() {
    recording.symboliclist.clear();
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
   * @param paramlist  - list of the parameters defined for a solution
   */
  public void addExpectedParams(ArrayList<ParameterInfo> paramlist) {
    if (recordState && recordStartTime != 0) {
      // if EXPECTED_PARAM not in record list (this is the first),  preceed it with EXTRACT command
      if (!recordCommandList.contains(RecordID.EXPECTED_PARAM)) {
        addCommand(RecordID.EXTRACT_SOLUTIONS, null);
      }
    
      if (paramlist.size() == 1) {
        ParameterInfo entry = paramlist.get(0);
        recording.addCommand(RecordID.EXPECTED_PARAM, new RecordExpectedSingle(entry.name, entry.value, entry.ctype));
      } else {
        recording.addCommand(RecordID.EXPECTED_PARAM, new RecordExpectedMulti(paramlist));
      }
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
      Utils.printStatusError("Recorder: JSON file not found: " + file.getAbsolutePath());
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
    content += "TESTNAME=\"" + recording.testname + "\"" + Utils.NEWLINE;
    content += Utils.NEWLINE;
    content += "# initialize the pass/fail status to pass and save the pid of the running process" + Utils.NEWLINE;
    content += "STATUS=0" + Utils.NEWLINE;
    content += "PID=$1" + Utils.NEWLINE;
    content += "echo \"pid = ${PID}\"" + Utils.NEWLINE;
    content += Utils.NEWLINE;

    for (Object objitem : recording.commandlist) {
      ArrayList<String> args = new ArrayList<>();
      if (objitem instanceof RecordCommand) {
        // this is a normal command (0 or 1 arguments)
        RecordCommand entry = (RecordCommand) objitem;
        args.add(entry.argument);
        content += getCommandOutput(entry.command.toString(), args);
      } else if (objitem instanceof RecordExpectedSingle) {
        // this is for single param in a solution
        RecordExpectedSingle expected = (RecordExpectedSingle) objitem;
        args.add(expected.name);
        args.add(expected.value);
        args.add(expected.ctype);
        content += getCommandOutput(expected.command.toString(), args);
      } else if (objitem instanceof RecordExpectedMulti) {
        // this is for multiple params in a solution
        RecordExpectedMulti param = (RecordExpectedMulti) objitem;
        content += getExpectedParamList(param.paramlist);
      } else if (objitem instanceof LinkedTreeMap) {
        // this handles the case of reading the structure in from the json file
        LinkedTreeMap map = (LinkedTreeMap) objitem;
        if (map.size() < 2 || !map.containsKey("command")) {
          Utils.printStatusError("Invalid map type: " + objitem.getClass().getName());
          continue;
        }
        String cmd = (String) map.get("command");
        if (map.containsKey("argument")) {
          // the "normal" commands (1 argument)
          args.add((String) map.get("argument"));
          content += getCommandOutput(cmd, args);
        } else if (map.containsKey("name")) {
          // the EXPECTED_PARAM command - single value
          args.add((String) map.get("name"));
          args.add((String) map.get("value"));
          args.add((String) map.get("ctype"));
          content += getCommandOutput(cmd, args);
        } else if (map.containsKey("paramlist")) {
          // the EXPECTED_PARAM command - multi value
          ArrayList<ParameterInfo> paramlist = new ArrayList<>();
          ArrayList<LinkedTreeMap> list = (ArrayList<LinkedTreeMap>) map.get("paramlist");
          if (list.size() == 1) {
            LinkedTreeMap entry = list.get(0);
            args.add((String)entry.get("name"));
            args.add((String)entry.get("value"));
            args.add((String)entry.get("ctype"));
            content += getCommandOutput(cmd, args);
          } else {
            for (LinkedTreeMap entry : list) {
              ParameterInfo exparam = new ParameterInfo((String)entry.get("name"),
                                                        (String)entry.get("value"),
                                                        (String)entry.get("ctype"));
              paramlist.add(exparam);
            }
          }
          content += getExpectedParamList(paramlist);
        } else {
          // if no arguments...
          content += getCommandOutput(cmd, args);
        }
      } else {
        Utils.printStatusError("Unhandled command class type: " + objitem.getClass().getName());
      }
    }

    return content;
  }

  private static String getCommandOutput(String id, ArrayList<String> args) {
    String content = "";
    switch(id) {
      case "STOP":
        content += "# terminate process (this one doesn't auto-terminate)" + Utils.NEWLINE;
        content += "kill -15 ${PID} > /dev/null 2>&1" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "DELAY":
        if (args == null || args.size() != 1) {
          Utils.printStatusError("Invalid args for : " + id);
          return content;
        }
        content += "# wait a short period" + Utils.NEWLINE;
        content += "sleep " + args.get(0) + Utils.NEWLINE;
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
        if (args == null || args.size() != 1) {
          Utils.printStatusError("Invalid args for : " + id);
          return content;
        }
        content += "# http port and message to send" + Utils.NEWLINE;
        content += "SERVERPORT=\"" + args.get(0) + "\"" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "SEND_HTTP_POST":
        if (args == null || args.size() != 1) {
          Utils.printStatusError("Invalid args for : " + id);
          return content;
        }
        content += "# send the HTTP POST" + Utils.NEWLINE;
        content += "echo \"Sending message to application\"" + Utils.NEWLINE;
        content += "curl -d \"" + args.get(0) + "\" http://localhost:${SERVERPORT}" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "SEND_HTTP_GET":
        content += "# send the HTTP GET" + Utils.NEWLINE;
        content += "echo \"Sending message to application\"" + Utils.NEWLINE;
        content += "curl http://localhost:${SERVERPORT}" + Utils.NEWLINE;
        content += Utils.NEWLINE;
        break;
      case "SEND_STDIN":
        if (args == null || args.size() != 1) {
          Utils.printStatusError("Invalid args for : " + id);
          return content;
        }
        content += "# send the message to STDIN" + Utils.NEWLINE;
        content += "echo \"Sending message to application\"" + Utils.NEWLINE;
        content += "echo \"" + args.get(0) + "\" > /proc/${PID}/fd/0" + Utils.NEWLINE;
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
        if (args == null || args.size() < 2) {
          Utils.printStatusError("Invalid args for : " + id);
          return content;
        }
        content += "check_solution";
        for (String arg : args) {
          content += " \"" + arg + "\"";
        }
        content += Utils.NEWLINE;
        break;
      default:
        Utils.printStatusMessage("Unknown RECORD command: " + id);
        break;
    }

    return content;
  }

  private static String getExpectedParamList(ArrayList<ParameterInfo> paramlist) {
    String content = "check_solution ";
    for (ParameterInfo entry : paramlist) {
      content += "\"" + entry.name + "\" \"" + entry.value + "\" \"" + entry.ctype + "\" ";
    }
    content += Utils.NEWLINE;
    return content;
  }

  // this defines the data for expected parameters so we can create an array of them for the case
  // of solutions that have more than one parameter defined.
  public static class ParameterInfo {
    private final String name;    // the name of the parameter
    private final String value;   // the value of the parameter
    private final String ctype;   // the constraint type of the param
    
    public ParameterInfo(String name, String value, String ctype) {
      this.name = name;
      this.value = value;
      this.ctype = ctype;
    }
  }

  // this defines the recorded information that can be saved as a config file.
  // NOTE: this file is saved as a JSON file for the test configuration, so nothing should be
  // in here that isn't relavant to the test.
  private class RecordInfo {
    private String testname;
    private String runargs;
    private String mainclass;
    private final ArrayList<RecordSymbolicParam> symboliclist;
    private final ArrayList<Object> commandlist;
    
    private RecordInfo() {
      testname = "";
      runargs = "";
      mainclass = "";

      // the lists that are saved (symbolic params and the commands to run)
      symboliclist = new ArrayList<>();
      commandlist = new ArrayList<>();
    }
    
    private void setTestName(String name, String args, String maincls) {
      testname = name;
      runargs = args;
      mainclass = maincls;
    }
    
    private void addSymbolic(String method, String name, String type, String slot, String start, String end) {
      symboliclist.add(new RecordSymbolicParam(method, name, type, slot, start, end));
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
      Utils.printStatusMessage("--- RECORDED: " + cmd.toString());
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
    private final RecordID      command;
    private final String        name;    // the name of the parameter
    private final String        value;   // the value of the parameter
    private final String        ctype;   // the constraint type of the param
    
    private RecordExpectedSingle(String name, String value, String ctype) {
      this.command = RecordID.EXPECTED_PARAM;
      this.name  = name;
      this.value = value;
      this.ctype = ctype;
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
