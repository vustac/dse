/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.io.IOException;

/**
 *
 * @author dan
 */
public class DebugMain {

  // init these to defaults
  public static int SERVER_PORT = 5000;
  
  /**
   * @param args the command line arguments
   * @throws java.io.IOException
   */
  public static void main(String[] args) throws IOException {
    // get any arguments passed
    // -t     = use TCP for connection (default)
    // -u     = use UDP for connection
    // <port> = the specified port to use (default is 5000)
    int port = SERVER_PORT;
    boolean bTcp = true;
    for (String arg : args) {
      switch (arg) {
        case "-t":
          bTcp = true;
          break;
        case "-u":
          bTcp = false;
          break;
        default:
          try {
            port = Integer.parseInt(arg);
            if (port < 100 || port > 65535) {
              System.out.println("Invalid port selection: " + port);
              System.exit(1);
            }
          } catch (Exception ex) {
            System.out.println("Invalid option: " + arg);
            System.exit(1);
          }
          break;
      }
    }
    
    // start the debug message panel
    GuiPanel gui = new GuiPanel();
    gui.createDebugPanel(port, bTcp);
  }
  
}
