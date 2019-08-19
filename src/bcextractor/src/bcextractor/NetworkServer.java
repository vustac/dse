/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bcextractor;

import bcextractor.BCExtractor.Visitor;
import util.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

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
  private static BufferedReader   inFromClient = null;    // TCP input reader
  private static Visitor          guiCallback = null;
  private static final LinkedBlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();

  public NetworkServer(int port, Visitor connection) throws IOException {
    super("ServerThread");
    
    serverPort = port;
    guiCallback = connection;

    try {
      // open the communications socket
      serverSocket = new ServerSocket(serverPort);
      Utils.msgLogger(Utils.LogType.INFO, "TCP server started on port: " + serverPort + ", waiting for connection");

      running = true;

    } catch (IOException ex) {
      Utils.msgLogger(Utils.LogType.ERROR, "port " + serverPort + "failed to start");
      Utils.msgLogger(Utils.LogType.INFO, ex.getMessage());
      System.exit(1);
    }
  }
  
  public String getNextMessage() {
    synchronized (QUEUE) {
      try {
        // wait for input if none
        while (QUEUE.isEmpty()) {
          QUEUE.wait();
        }
        // read next message from input buffer
        return QUEUE.take();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
  }

  private void saveMessage(String message) {
    // add message to queue
    Utils.msgLogger(Utils.LogType.INFO, "RCVD: " + message);
    synchronized (QUEUE) {
      try {
        QUEUE.put(message);
        QUEUE.notifyAll();
      } catch (InterruptedException ex) {
        // ignore
      }
    }
  }
  
  /**
   * this is the callback to run when exiting
   */
  @Override
  public void exit() {
    running = false;
  }

  @Override
  public void run() {
    Utils.msgLogger(Utils.LogType.INFO, "NetworkServer thread started!");

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
            Utils.msgLogger(Utils.LogType.INFO, "connected to client: " + connection);
            if (NetworkServer.guiCallback != null) {
              NetworkServer.guiCallback.showConnection(connection);
            }
            InputStream cis = connectionSocket.getInputStream();
            inFromClient = new BufferedReader(new InputStreamReader(cis));
            //DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
          } catch (IOException ex) {
            Utils.msgLogger(Utils.LogType.ERROR, "NetworkServer: " + ex.getMessage());
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
          saveMessage(message);
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
        Utils.msgLogger(Utils.LogType.ERROR, "NetworkServer: " + ex.getMessage());
      }
    }

    // terminating loop - close all sockets
    try {
      if (connectionSocket != null) {
        Utils.msgLogger(Utils.LogType.INFO, "closing TCP connection socket");
        connectionSocket.close();
        connectionSocket = null;
        if (NetworkServer.guiCallback != null) {
          NetworkServer.guiCallback.resetConnection();
        }
      }
      if (serverSocket != null) {
        Utils.msgLogger(Utils.LogType.INFO, "closing TCP server socket for port: " + serverPort);
        serverSocket.close();
        serverSocket = null;
      }
    } catch (IOException ex) {
      // ignore
    }
  }

}
