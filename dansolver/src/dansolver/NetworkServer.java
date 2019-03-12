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
import org.bson.Document;

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
  
  public synchronized long getDocumentCount(long processed) throws InterruptedException {
    notify();
    long count;
    if ((count = Dansolver.documentCount()) <= processed) {
      System.out.println("All " + count + " documents solved, waiting for new");
      wait();
    }
    return count;
  }
  
  private synchronized void insertDocument(Document mongoDoc) {
    Dansolver.documentInsert(mongoDoc);
    notify();
//    System.out.println("added document: count = " + Dansolver.documentCount());
  }
  
  private synchronized void newCommand() {
    System.out.println("Received CLEAR command");
    notify();
  }
  
  public NetworkServer(int port) throws IOException {
    super("ServerThread");
    
    serverPort = port;
    
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
    private int socketId = idCount++;
    private int msgCount = 0;

    public ClientThread(Socket clientSocket) {
      this.socket = clientSocket;
      int clientPort = clientSocket.getPort();
      String clientAddr = clientSocket.getInetAddress().getHostAddress();
      System.out.println("ID " + socketId + " - connected to client: " + clientAddr + ":" + clientPort);
    }

    /**
     * the run process for each connected client thread
     */
    @Override
    public void run() {
      try {
        BufferedReader brin = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        SolverInterface info = new SolverInterface();
        String message;

        while ((message = brin.readLine()) != null) {
          // add message to solver info (the constraint may contain NEWLINEs)
          if (info.isComplete() || !info.isValid()) {
            info = new SolverInterface(socketId, msgCount, message);
            ++msgCount;
          } else {
            info.appendConstraint(message);
          }
        
          // check if we received a command or a constraint message
          if (info.isReset()) {
            newCommand();
            int connect = info.getResetException();
            if (connect < 0) {
              Dansolver.documentClearAll();
            } else {
              Dansolver.documentClearExcept(connect);
            }
          } else if (info.isComplete()) {
            // when info is complete, place it in buffer
            if (info.isValid()) {
              insertDocument(info.makeDocument());
            }
            // we are done with the info
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
