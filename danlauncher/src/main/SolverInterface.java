/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 *
 * @author dan
 */
public class SolverInterface {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  private static String    serverAddress = "localhost";
  private static int       serverPort = 4000;
  private static Socket    tcpDataSocket;
  private static BufferedOutputStream tcpDataOut;
  private static boolean   valid;
  
  public SolverInterface(int port) {
    serverPort = port;
    
    try {
      tcpDataSocket = new Socket(serverAddress, serverPort);
      tcpDataOut = new BufferedOutputStream(tcpDataSocket.getOutputStream());
      System.out.println("Started SolverInterface to " + serverAddress + " port " + serverPort);
      valid = true;
    } catch (SecurityException | IOException ex) {
      System.err.println("ERROR: <SolverInterface.initSocket>" + ex.getMessage());
      valid = false;
//      System.exit(1);
    }
  }
  
  public void exit() {
    try {
      if (tcpDataSocket != null) {
        tcpDataSocket.close();
      }
      if (tcpDataOut != null) {
        tcpDataOut.close();
      }
    } catch (IOException ex) {
      System.err.println(ex.getMessage());
    }
  }
  
  public int getPort() {
    return serverPort;
  }
  
  public boolean isValid() {
    return valid;
  }
  
  public void sendClearAll() {
    sendMessage("!CLEAR_ALL");
  }
  
  public void sendClearExcept(int connect) {
    sendMessage("!CLEAR_EXCEPT " + connect);
  }
  
  public void sendMessage(String msg) {
    if (!valid) {
      System.err.println("ERROR: <SolverInterface.sendMessage> - port failure");
      return;
    }
    
    // format the message to send (the ids MUST be 4 chars in length)
    //System.out.println("Sending msg to Solver: " + msg);
    msg += NEWLINE;

    // now send the message
    try {
      tcpDataOut.write(msg.getBytes());
      tcpDataOut.flush();
    } catch (IOException | NullPointerException ex) {
      System.err.println("ERROR: <SolverInterface.sendMessage>" + ex.getMessage());
    }
  }
}
