/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import logging.FileSaver;
import main.LauncherMain.Visitor;
import util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

// provides callback interface
interface NetworkListener{
  void exit();
}

/**
 *
 * @author dan
 */
public final class NetworkServer extends Thread implements NetworkListener {
 
  protected static ServerSocket   serverSocket = null;
  protected static Socket         connectionSocket = null;

  private static int              serverPort;
  private static boolean          running;
  private static int              pktsRead;
  private static String           storageFileName;
  private static FileSaver        fileSaver = null;
  private static BufferedReader   inFromClient = null;    // TCP input reader
  private static Visitor          guiCallback = null;

  public NetworkServer(int port, String fname, Visitor connection) throws IOException {
    super("ServerThread");
    
    serverPort = port;
    storageFileName = fname;
    guiCallback = connection;
    pktsRead = 0;

    try {
      // open the communications socket
      serverSocket = new ServerSocket(serverPort);
      Utils.printStatusInfo("TCP server started on port: " + serverPort + ", waiting for connection");

      // remove any previously existing debug file
      File file = new File(fname);
      if (file.isFile()) {
        file.delete();
      }

      // create the file cache and setup the file to save to
      fileSaver = new FileSaver();
      fileSaver.setStorageFile(storageFileName);
      running = true;

    } catch (IOException ex) {
      Utils.printStatusError("port " + serverPort + "failed to start");
      Utils.printStatusError(ex.getMessage());
      System.exit(1);
    }
  }
  
  public void clear() {
    // reset statistics and empty the buffer
    pktsRead = 0;
    fileSaver.resetInput();
    fileSaver.clear();
  }
  
  public int getQueueSize() {
    return fileSaver.getQueueSize();
  }
  
  public int getPktsRead() {
    return pktsRead;
  }
  
  public int getPort() {
    return serverPort;
  }
  
  public String getNextMessage() {
    return fileSaver.getNextMessage();
  }

  public void setStorageFile(String fname) {
    // ignore if name was not changed or empty name given
    if (fname == null || fname.isEmpty() || fname.equals(storageFileName)) {
      return;
    }

    // remove any existing file and save the full path of the assigned file name
    File file = new File(fname);
    storageFileName = file.getAbsolutePath();
    if (file.isFile()) {
      file.delete();
    }

    // setup the file to save to
    fileSaver.setStorageFile(storageFileName);
    fileSaver.resetInput();
  }

  /**
   * this is the callback to run when exiting
   */
  @Override
  public void exit() {
    running = false;
    fileSaver.exit();
  }

  @Override
  public void run() {
    Utils.printStatusInfo("NetworkServer thread started!");

    // start the thread for copying input to file
    Thread t = new Thread(fileSaver);
    t.start();

    while (running) {
      try {
        // TCP socket connection...
        // accept connection if no connection yet
        // we only handle 1 connection at a time - any prev connection must first be closed
        if (connectionSocket == null) {
          try {
            connectionSocket = serverSocket.accept();
            String connection = connectionSocket.getInetAddress().getHostAddress() + ":" +
                                connectionSocket.getPort();
            Utils.printStatusInfo("connected to client: " + connection);
            if (NetworkServer.guiCallback != null) {
              NetworkServer.guiCallback.showConnection(connection);
            }
            InputStream cis = connectionSocket.getInputStream();
            inFromClient = new BufferedReader(new InputStreamReader(cis));
            //DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
          } catch (IOException ex) {
            Utils.printStatusError("NetworkServer: " + ex.getMessage());
            try {
              serverSocket.close();
              serverSocket = null;
            } catch (IOException ex1) {
              // ignore - we are exiting anyway
            } finally {
              System.exit(1);
            }
          }
        }
        
        // read input from client and add to buffer
        String message = inFromClient.readLine();
        if (message != null) {
          fileSaver.saveMessage(message);
          ++pktsRead;
        } else {
          // client disconnected - close the socket so a new connection can be made
          connectionSocket.shutdownInput();
          connectionSocket.close();
          connectionSocket = null;
          if (NetworkServer.guiCallback != null) {
            NetworkServer.guiCallback.resetConnection();
          }
        }
      } catch (IOException ex) {
        Utils.printStatusError("NetworkServer: " + ex.getMessage());
      }
    }

    // terminating loop - close all sockets
    try {
      if (connectionSocket != null) {
        Utils.printStatusInfo("closing TCP connection socket");
        connectionSocket.close();
        connectionSocket = null;
        if (NetworkServer.guiCallback != null) {
          NetworkServer.guiCallback.resetConnection();
        }
      }
      if (serverSocket != null) {
        Utils.printStatusInfo("closing TCP server socket for port: " + serverPort);
        serverSocket.close();
        serverSocket = null;
      }
    } catch (IOException ex) {
      // ignore
    }
  }

 
}
