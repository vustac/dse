/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import util.Utils;

/**
 *
 * @author dan
 */
public class BCExtractorInterface {
  
  private static String    serverAddress;
  private static int       serverPort;
  private static Socket    tcpDataSocket;
  private static BufferedOutputStream tcpDataOut;
  private static boolean   valid;
  
  public BCExtractorInterface() {
    // set network port selections
    serverAddress = "localhost";
    serverPort = 6000;
    
    try {
      tcpDataSocket = new Socket(serverAddress, serverPort);
      tcpDataOut = new BufferedOutputStream(tcpDataSocket.getOutputStream());
      Utils.printStatusInfo("BCExtractorInterface started on " + serverAddress + " port " + serverPort);
      valid = true;
    } catch (SecurityException | IOException ex) {
      Utils.printStatusError("BCExtractorInterface: " + ex.getMessage());
      valid = false;
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
      Utils.printStatusError("BCExtractorInterface: " + ex.getMessage());
    }
  }

  public void restart() {
    Utils.printStatusError("BCExtractorInterface: restarting...");
    try {
      tcpDataSocket = new Socket(serverAddress, serverPort);
      tcpDataOut = new BufferedOutputStream(tcpDataSocket.getOutputStream());
      Utils.printStatusInfo("BCExtractorInterface started on " + serverAddress + " port " + serverPort);
      valid = true;
    } catch (SecurityException | IOException ex) {
      Utils.printStatusError("BCExtractorInterface: " + ex.getMessage());
      valid = false;
    }
  }
  
  public int getPort() {
    return serverPort;
  }
  
  public boolean isValid() {
    return valid;
  }
  
  public void sendMessage(String message) {
    if (!valid) {
      Utils.printStatusError("BCExtractorInterface.sendMessage: port failure");
      return;
    }

    Utils.printStatusMessage(message);
    
    // send the message
    try {
      message += util.Utils.NEWLINE;
      tcpDataOut.write(message.getBytes());
      tcpDataOut.flush();
    } catch (IOException | NullPointerException ex) {
      Utils.printStatusError("BCExtractorInterface.sendMessage: " + ex.getMessage());
    }
  }
  
}
