/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.io.File;
import java.util.ArrayList;
import static main.LauncherMain.printCommandMessage;
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
    EXPECTED_PARAM,     // specify the expected parameter
    SHOW_RESULTS,       // show the final pass/fail status
    SYMBOLIC_PARAM,     // define a symbolic parameter
  }
  
  private static boolean         recordState;
  private static long            recordStartTime;
  private static RecordID        recordLastCommand;
  private static RecordInfo      recording;
  
  public Recorder() {
    recordState = false;
    recordLastCommand = RecordID.STOP;
    recording = new RecordInfo();
  }
  
  public boolean isRecording() {
    return recordState;
  }

  public void startRecording() {
    recording.commandlist.clear();
    recordState = true;
  }
  
  public void startRecording(String testname, String arglist) {
    recording.commandlist.clear();
    recordState = true;
    recording.setTestName(testname, arglist);
  }
  
  public void stopRecording() {
    recordState = false;
  }
  
  public void clearRecording() {
    recording.commandlist.clear();
  }
  
  public void clearSymbolics() {
    recording.symbolicList.clear();
  }
  
  public void addCommand(RecordID cmd, String arg) {
    if (recordState) {
      recording.addCommand(cmd, arg);
    }
  }

  public void addExpectedParam(String name, String value) {
    if (recordState) {
      recording.addExpectedParam(name, value);
    }
  }
  
  public void addSymbolicParam(String method, String name, String type, String slot, String start, String end) {
    if (recordState) {
      recording.addSymbolic(method, name, type, slot, start, end);
    }
  }
  
  public void addDelay() {
    if (recordState) {
      recording.addDelay();
    }
  }
  
  public void addHttpDelay(String port) {
    if (recordState) {
      recording.addHttpDelay(port);
    }
  }
  
  public void generateConfigFile(String filename) {
      Utils.saveAsJSONFile(recording, new File(filename));
  }
  
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
      if (objitem instanceof RecordExpectedSingle) {
        // TODO: this does not accomodate multiple params in a solution
        RecordExpectedSingle param = (RecordExpectedSingle) objitem;
        content += "check_solution \"" + param.name + "\" \"" + param.value + "\"" + Utils.NEWLINE;
      } else {
        RecordCommand entry = (RecordCommand) objitem;
        switch(entry.command) {
          case STOP:
            content += "# terminate process (this one doesn't auto-terminate)" + Utils.NEWLINE;
            content += "kill -15 ${PID} > /dev/null 2>&1" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case DELAY:
            content += "# wait a short period" + Utils.NEWLINE;
            content += "sleep " + entry.argument + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case WAIT_FOR_TERM:
            content += "# wait for application to complete" + Utils.NEWLINE;
            content += "wait_for_app_completion 5 ${PID}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case WAIT_FOR_SERVER:
            content += "# wait for server to start then post message to it" + Utils.NEWLINE;
            content += "wait_for_server 10 ${PID} ${SERVERPORT}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SET_HTTP:
            httpport = entry.argument;
            content += "# http port and message to send" + Utils.NEWLINE;
            content += "SERVERPORT=\"" + httpport + "\"" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SEND_HTTP_POST:
            content += "# send the HTTP POST" + Utils.NEWLINE;
            content += "echo \"Sending message to application\"" + Utils.NEWLINE;
            content += "curl -d \"" + entry.argument + "\" http://localhost:${SERVERPORT}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SEND_HTTP_GET:
            content += "# send the HTTP GET" + Utils.NEWLINE;
            content += "echo \"Sending message to application\"" + Utils.NEWLINE;
            content += "curl http://localhost:${SERVERPORT}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case SEND_STDIN:
            content += "# send the message to STDIN" + Utils.NEWLINE;
            content += "echo \"Sending message to application\"" + Utils.NEWLINE;
            content += "echo \"" + entry.argument + "\" > /proc/${PID}/fd/0" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
          case EXTRACT_SOLUTIONS:
            content += "# get solver response and check against expected solution" + Utils.NEWLINE;
            content += "echo \"Debug info: ${TESTNAME} database entry\"" + Utils.NEWLINE;
            content += "extract_solutions" + Utils.NEWLINE;
            break;
          case SHOW_RESULTS:
            content += "show_results ${TESTNAME}" + Utils.NEWLINE;
            content += Utils.NEWLINE;
            break;
        }
      }
    }

    return content;
  }
  
  public boolean isCommandFound(RecordID cmd) {
    return recording.isCommandFound(cmd);
  }
  
  public boolean isEmpty() {
    return recording.commandlist.isEmpty();
  }
  
  private class RecordInfo {
    String testname;
    String arguments;
    String debugFlags;
    String debugMode;
    String debugPort;
    String debugAddr;
    ArrayList<RecordSymbolicParam> symbolicList;
    ArrayList<Object> commandlist;
    
    private RecordInfo() {
      testname = "";
      arguments = "";
      debugFlags = "";
      debugMode = "STDIN";
      debugPort = "5000";
      debugAddr = "localhost";
      symbolicList = new ArrayList<>();
      commandlist = new ArrayList<>();
    }
    
    public boolean isCommandFound(RecordID cmd) {
      for (Object objitem : commandlist) {
        if (objitem instanceof RecordExpectedSingle) {
          if (cmd == RecordID.EXPECTED_PARAM) {
            return true;
          }
        } else {
          RecordCommand entry = (RecordCommand) objitem;
          if (cmd == entry.command) {
            return true;
          }
        }
      }
      return false;
    }
    
    private void setTestName(String name, String args) {
      testname = name;
      arguments = args;
    }
    
    private void addSymbolic(String method, String name, String type, String slot, String start, String end) {
      symbolicList.add(new RecordSymbolicParam(method, name, type, slot, start, end));
    }
    
    private void addCommand(RecordID cmd, String arg) {
      // if command is not RUN command, then only add if RUN was previously saved
      if (testname == null || testname.isEmpty()) {
        printCommandMessage("Igoring command " + cmd.toString() + " until RUN command received");
        return;
      }

      // if last command was EXPECTED_PARAM and this command isn't, let's terminate the expected tests
      int count = commandlist.size();
      if (count > 1 && RecordID.SHOW_RESULTS != cmd
                    && RecordID.EXPECTED_PARAM != cmd
                    && RecordID.EXPECTED_PARAM == recordLastCommand) {
        printCommandMessage("--- RECORDED: SHOW_RESULTS");
        commandlist.add(new RecordCommand(RecordID.SHOW_RESULTS, ""));
      }

      // add the command
      printCommandMessage("--- RECORDED: " + cmd.toString() + " " + arg);
      commandlist.add(new RecordCommand(cmd, arg));
      recordLastCommand = cmd; // save last command

      // get start of delay to next command
      recordStartTime = System.currentTimeMillis();
    }
  
    private void addExpectedParam(String name, String value) {
      // if command is not RUN command, then only add if RUN was previously saved
      if (testname == null || testname.isEmpty()) {
        printCommandMessage("Igoring command " + RecordID.EXPECTED_PARAM.toString() + " until RUN command received");
        return;
      }

      // add the command
      printCommandMessage("--- RECORDED: " + RecordID.EXPECTED_PARAM.toString() + " " + name + " = " + value);
      commandlist.add(new RecordExpectedSingle(name, value));
      recordLastCommand = RecordID.EXPECTED_PARAM; // save last command

      // get start of delay to next command
      recordStartTime = System.currentTimeMillis();
    }
  
    private void addDelay() {
      // determine the delay time (ignore if it was < 1 sec or start wasn't initialized)
      if (recordStartTime != 0) {
        long elapsed = 1 + (System.currentTimeMillis() - recordStartTime) / 1000;
        if (elapsed > 1) {
          addCommand(RecordID.DELAY, "" + elapsed);
        }
      }
    }
  
    private void addHttpDelay(String port) {
      if (isCommandFound(RecordID.SET_HTTP)) {
        // if we have already established the server port and waited for it, just do a delay
        addDelay();
      } else {
        // if 1st time on HTTP, indicate the port selection and wait till server is up
        addCommand(RecordID.SET_HTTP, port);
        addCommand(RecordID.WAIT_FOR_SERVER, null);
      }
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
    public RecordID command;
    public String   name;
    public String   value;
    
    private RecordExpectedSingle(String name, String value) {
      this.command = RecordID.EXPECTED_PARAM;
      this.name = name;
      this.value = value;
    }
  }

  // the class for EXPECTED_PARAM command
  private static class RecordSymbolicParam {
    public RecordID command;
    public String   method;
    public String   name;
    public String   type;
    public String   slot;
    public String   start;
    public String   end;
    
    private RecordSymbolicParam(String method, String name, String type, String slot, String start, String end) {
      this.command = RecordID.EXPECTED_PARAM;
      this.method = method;
      this.name = name;
      this.type = type;
      this.slot = slot;
      this.start = start;
      this.end = end;
    }
  }

}
