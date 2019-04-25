/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dansolver;

import org.bson.Document;

/**
 *
 * @author dan
 */
public class SolverInterface {
  // constants
  private static final String NEWLINE = System.getProperty("line.separator");

  private static final String ENTRY_BEGIN = "@#";
  private static final String ENTRY_END = "#@";
    
  // data to be saved in database
  // these are added upon receipt of the msg
  private final Integer connectionId;  // index of the solver connection
  private Integer msgCount;      // message count for the specified connection
  // the following are values contained in the msg received
  private Integer threadId;      // thread id that was running
  private String  datestr;       // time/date the branchpoint occurred
  private String  method;        // method selection
  private Integer offset;        // opcode line offset within the method
  private Boolean pathsel;       // indication of whether the branch was taken or not
  private Integer cost;          // number of instructions executed at the branch point
  private String  ctype;         // type of constraint: PATH, ARRAY, LOOPBOUND
  private String  constraint;    // constraint value

  // these are flags to communicate status to caller
  private boolean complete;     // true if data in this class is complete (constrainnt sent in pieces)
  private boolean invalid;      // true if the current msg is incomplete & new one arrived
  private SolverCommandInfo commandInfo;
  
  public SolverInterface(int id) {
    connectionId = id;

    complete = true;
    invalid = false;
    constraint = "";
    commandInfo = null;
  }

  // this defines the commands that can be sent to dansolver while it is parsing data from danalyzer.
  public enum SolverCommand { CLEAR_ALL, CLEAR_OLD }
  
  public class SolverCommandInfo {
    public SolverCommand command;   // the command value
    public int argument;            // the argument associated with the command (if any)
    
    SolverCommandInfo(SolverCommand cmd) {
      command = cmd;
      argument = -1; // use -1 to indicate no arg
    }
    
    SolverCommandInfo(SolverCommand cmd, int arg) {
      command = cmd;
      argument = arg;
    }
  }
  
  /**
   * clear the collected solver information
   */
  public void clear() {
    complete = true;
    invalid = false;
    constraint = "";
    commandInfo = null;
  }
  
  /**
   * test if solver information is valid (if it was interrupted by a command or another constraint)
   * @return true if data is not valid
   */
  public boolean isValid() {
    return !invalid;
  }
  
  /**
   * test if solver information is complete (constraint may be contained in multiple lines)
   * @return true if complete
   */
  public boolean isComplete() {
    return complete;
  }
  
  /**
   * return command if one was received
   * @return command received (null if none)
   */
  public SolverCommandInfo getCommand() {
    return commandInfo;
  }
  
  /**
   * creates a mongodb document from the solver data received
   * @return - the document created
   */
  public Document makeDocument() {
    // create the document to add to the database
    Document mongoDoc = new Document();
    mongoDoc = mongoDoc.append("connection", connectionId);
    mongoDoc = mongoDoc.append("index", msgCount);
    mongoDoc = mongoDoc.append("thread", threadId);
    mongoDoc = mongoDoc.append("time", datestr);
    mongoDoc = mongoDoc.append("constraint", constraint);
    mongoDoc = mongoDoc.append("ctype", ctype);
    mongoDoc = mongoDoc.append("method", method);
    mongoDoc = mongoDoc.append("offset", offset);
    mongoDoc = mongoDoc.append("lastpath", pathsel);
    mongoDoc = mongoDoc.append("cost", cost);
    return mongoDoc;
  }
  
  /**
   * this is called to enter the start of a new message.
   * (note that is may not contain the full constraint, which can be added in piecemeal fashion)
   * 
   * @param count   - the number of full messages received so far
   * @param message - the start of text that is to be parsed & placed in the constraint formula
   */
  public void newMessage(int count, String message) {
    msgCount = count;
    complete = true;
    commandInfo = null;

    // read the entries from the received message and places in SolverInterface parameters
    while (!message.isEmpty()) {
      int off = getNextEntry(message);
      if (off > 0) {
        message = message.substring(off).trim();
      } else {
        break;
      }
    }
  }
  
  /**
   * this is called to add the next piece of a FORMula when it is being received on multiple lines.
   * 
   * @param message - the next line of text that is to be placed in the constraint formula
   */
  public void appendConstraint(String message) {
    // check for clear commands
    if (checkIfCommand(message)) {
      invalid = true;
      return;
    }
    
    if (message.contains(ENTRY_BEGIN)) {
      // this indicates the sender didn't complete prev message & started a new one
      invalid = true;
      System.err.println("Constraint incomplete - ignoring");
    } else if (message.contains(ENTRY_END)) {
      constraint = constraint + message.substring(0, message.indexOf(ENTRY_END)) + NEWLINE;
      complete = true;
    } else {
      constraint = constraint + NEWLINE + message.trim();
    }
  }
    
  /**
   * checks to see if the message received is a command (sent by danlauncher) rather than a
   * formula message sent by danalyzer.
   * 
   * A flag value will be set to indicate the received command along with any parameter
   * associated with it.
   * 
   * @param message - the message received
   * @return - true if the message was a command.
   */
  private boolean checkIfCommand(String message) {
    if (message.charAt(0) != '!') {
      return false;
    }

    // check for clear commands
    if (message.equals("!CLEAR_ALL")) {
      commandInfo = new SolverCommandInfo(SolverCommand.CLEAR_ALL);
    } else if (message.startsWith("!CLEAR_EXCEPT")) {
      String index = message.substring("!CLEAR_EXCEPT".length()).trim();
      int select = -1;
      try {
        select = Integer.parseUnsignedInt(index);
      } catch (NumberFormatException ex) {
        // ignore - this will result in clearing all
      }
      commandInfo = new SolverCommandInfo(SolverCommand.CLEAR_OLD, select);
    } else {
      System.err.println("SolverInterface: received UNKNOWN command: " + message);
    }
    return true;
  }
  
  /**
   * Called iteratively to parse each parameter from the danlauncher message text and place
   * the contents in SolverInterface parameters.
   * 
   * Since this is only called when a SolverInterface instance is created, this is either the
   * start of a received solver formula from danalyzer or a command (probably sent by danlauncher).
   * Commands are handled by setting flags indicating the command received (processed by Dansolver).
   * This is called iteratively to read each parameter entry of the message data, except for
   * the FORMula itself, if it is contained in more that 1 message line.
   * 
   * If a multi-line FORMula is being read, the additional lines are read by appendConstraint.
   * 
   * This assumes that there are no NEWLINE chars except at the end of the message and possibly
   * within the FORMula parameter.
   * 
   * @param message - the line of text to parse
   * @return  0 : line was a command to execute instead of constraint data
   *         -1 : line contains start of FORMula or contained an invalid entry, which is ignored
   *         >0 : param was parsed. the value returned is the offset to the start of the next item
   */
  private int getNextEntry(String message) {
    // check for clear commands
    if (checkIfCommand(message)) {
      return 0;
    }
    
    // entries must be formatted as <XXXX:value> , where XXXX is the token and 'value' is its value
    // NOTE: the formula entry may be on multiple lines, so must be handled differently.
    int start = message.indexOf(ENTRY_BEGIN);
    int end   = message.indexOf(ENTRY_END);
    int sep   = message.indexOf(":");
    if (start < 0 || sep != start + ENTRY_BEGIN.length() + 4) {
      System.err.println("ERROR: getNextEntry: start = " + start + ", sep = " + sep);
      return -1;
    }

    // token is always 4 chars in length followed by ':'
    String token = message.substring(start + ENTRY_BEGIN.length(), sep);
    
    // check if end of entry found
    String value;
    if (end > start) {
      value = message.substring(sep + 1, end).trim();
    } else {
      // nope - if not a FORMula entry, this is an error
      if (!token.equals("FORM")) {
        System.err.println("ERROR: getNextEntry: start = " + start + ", end = " + end);
        return -1;
      }
      // if FORMula entry, the value will be the rest of this line (plus additional lines)
      value = message.substring(sep + 1).trim();
    }
    
    try {
      switch (token) {
        case "TID ":
          threadId = Integer.parseUnsignedInt(value);
          break;
        case "TIME":
          datestr = value;
          break;
        case "METH":
          method = value.replace(".", "/"); // mongo names can't use "."
          break;
        case "BOFF":
          offset = Integer.parseUnsignedInt(value);
          break;
        case "PATH":
          pathsel = value.equals("1");
          break;
        case "COST":
          cost = Integer.parseUnsignedInt(value);
          break;
        case "CTYP":
          ctype = value;
          break;
        case "FORM":
          constraint = value;
          if (end <= start) {
            // if end was -1, this is continued line, so don't mark as complete
            complete = false;
            return message.length();
          }
          // this is the case if the constraint fit all on 1 line
          complete = true;
          break;
        default:
          // ignore unknown entry
          break;
      }
    } catch (NumberFormatException ex) {
      System.err.println("getNextEntry: Invalid number for " + token + ": " + value);
      return -1;
    }
    return end + ENTRY_END.length();
  }
    
}
  
