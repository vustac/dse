/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danalyzer.executor;

import danalyzer.gui.DebugUtil;
import danalyzer.gui.GuiPanel;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author dan
 */
public class SolverInterface {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  private static String    serverAddress = "localhost";
  private static int       serverPort = 4000;
  private static Socket           tcpDataSocket = null;
  private static BufferedOutputStream tcpDataOut = null;
  
  public static final String ENTRY_BEGIN = "@#";
  public static final String ENTRY_END = "#@";
  
  public SolverInterface() {
//  public SolverInterface(String address, int port) {
//    serverPort = port;
//    serverAddress = address.isEmpty() ? "localhost" : address;

    serverAddress = GuiPanel.getSolverAddress();
    serverPort = GuiPanel.getSolverPort();
    
    try {
      tcpDataSocket = new Socket(serverAddress, serverPort);
      tcpDataOut = new BufferedOutputStream(tcpDataSocket.getOutputStream());
      System.out.println("Started SolverInterface to " + serverAddress + " port " + serverPort);
    } catch (SecurityException | IOException ex) {
      System.err.println("ERROR: <SolverInterface.initSocket>" + ex.getMessage());
//      System.exit(1);
    }
  }
  
  private void exit() {
    try {
      if (tcpDataSocket != null) {
        tcpDataSocket.close();
      }
      if (tcpDataOut != null) {
        tcpDataOut.close();
      }
    } catch (IOException ex) { }
  }
  
  private String encloseEntry(String id, String value) {
    return ENTRY_BEGIN + id + ":" + value + ENTRY_END;
  }
  
  private String encloseEntry(String id, int value) {
    return ENTRY_BEGIN + id + ":" + value + ENTRY_END;
  }
  
  private String encloseEntry(String id, boolean value) {
    return ENTRY_BEGIN + id + ":" + (value ? "1" : "0") + ENTRY_END;
  }
  
  public void sendMessage(int threadId, String method, int opcodeOffset, boolean pathsel, 
      String ctype, String formula) {
    if (tcpDataOut == null) {
      System.err.println("ERROR: <SolverInterface.sendMessage>: no connection (method: " + method + ")");
      return;
    }
    
    // get a timestamp value and the cost in terms of instructions executed
    String datestr = new SimpleDateFormat("YYYY-MM-dd  HH:mm:ss").format(new Date());

    // format the message to send (the ids MUST be 4 chars in length)
    String msg = encloseEntry("TID ", threadId) +
                 encloseEntry("TIME", datestr) +
                 encloseEntry("METH", method) +
                 encloseEntry("BOFF", opcodeOffset) +
                 encloseEntry("PATH", pathsel) +
                 encloseEntry("COST", DebugUtil.getInstructionCount()) + // the cost
                 encloseEntry("CTYP", ctype) +
                 encloseEntry("FORM", formula) + NEWLINE;

    // now send the message
    try {
      tcpDataOut.write(msg.getBytes());
      tcpDataOut.flush();
    } catch (IOException | NullPointerException ex) {
      System.err.println("ERROR: <SolverInterface.sendMessage>" + ex.getMessage());
    }
  }
}
