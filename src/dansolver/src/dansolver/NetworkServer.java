/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dansolver;

import java.io.BufferedReader;
import java.io.IOException;
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
  private   static int            serverPort;
  private   static boolean        running = false;
  private   static int            idCount = 0;
  private   static Dansolver.SolverCallback solverCallback;
  
  public NetworkServer(int port, Dansolver.SolverCallback callback) throws IOException {
    super("ServerThread");
    
    serverPort = port;
    solverCallback = callback;

    try {
      // open the communications socket
      serverSocket = new ServerSocket(serverPort);
      System.out.println("TCP server started on port: " + serverPort);
      running = true;

    } catch (IOException ex) {
      System.out.println("port " + serverPort + "failed to start");
      System.out.println(ex.getMessage());
      System.exit(1);
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
    System.out.println("Server waiting for connections...");
    while (running) {
      // accept new TCP connection requests in this thread
      try {
        Socket connectionSocket = serverSocket.accept();
        ClientThread client = new ClientThread(connectionSocket);
        client.start();
      } catch (IOException ex) {
        System.err.println("ERROR connecting to client: " + ex.getMessage());
      }
    }
  }

  private class ClientThread extends Thread {
    protected Socket socket;
    private int socketId;
    private int msgCount;
    private BufferedReader brin;
    private SolverInterface info;

    public ClientThread(Socket clientSocket) {
      this.socket = clientSocket;
      
      int clientPort = clientSocket.getPort();
      String clientAddr = clientSocket.getInetAddress().getHostAddress();
      
      msgCount = 0;
      socketId = idCount++;
      System.out.println("ID " + socketId + " - connected to client: " + clientAddr + ":" + clientPort);

      // create a SolverInterface for parsing the input from the port
      info = new SolverInterface(socketId);
      
      // get a buffered reader for reading from the socket input stream
      try {
        brin = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      } catch (IOException ex) {
        System.err.println(ex.getMessage());
        System.exit(1);
      }
    }

    /**
     * the run process for each connected client thread
     */
    @Override
    public void run() {
      try {
        String message;
        while ((message = brin.readLine()) != null) {
          // add message to solver info (the constraint may contain NEWLINEs)
          if (info.isComplete() || !info.isValid()) {
            info.newMessage(msgCount, message);
            ++msgCount;
          } else {
            info.appendConstraint(message);
          }
        
          // check if we received a command or a constraint message
          SolverInterface.SolverCommandInfo command = info.getCommand();
          if (command != null) {
            // for now, the only commands are CLEAR, with or without an argument)
            info.clear();
            solverCallback.documentClear(command.argument);
          } else if (info.isComplete() && info.isValid()) {
            // when info is complete, place it in buffer
            solverCallback.documentInsert(info.makeDocument());
            info.clear();
          } else if (!info.isValid()) {
            // if prev constraint was overwritten, reset it
            info.clear();
          }
        }
      } catch (IOException ex) {
        System.err.println(ex.getMessage());
        System.exit(1);
      }
    }
  }
  
}
