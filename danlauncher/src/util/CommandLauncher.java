/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import logging.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import javax.swing.JTextArea;

/**
 * @author dmcd2356
 *
 */
public class CommandLauncher {
    private static final JTextArea response = new JTextArea(); // output for stdout & stderr
    private final Logger logger;
    
    public CommandLauncher (Logger log) {
        this.logger = log;
    }
    
    public String getResponse () {
        return response.getText();
    }
    
    private void logMessage(String type, String message) {
      if (logger == null) {
        if (type.equals("ERROR")) {
          System.err.println("ERROR: " + message);
        } else {
          System.out.println(message);
        }
      } else {
        logger.printLine(type, message);
      }
    }
    
    /**
     * starts the specified command in the current thread.
     * 
     * @param command - an array of the command and each argument it requires
     * @param workdir - the path from which to launch the command
     * 
     * @return the status of the command (0 = success) 
     */
    public int start(String[] command, String workdir) {
        int retcode = 0;

        logMessage("NORMAL", "--------------------------------------------------------------------------------");
        if (workdir == null) {
          logMessage("NORMAL", "CommandLauncher running from current directory");
        } else {
          logMessage("NORMAL", "CommandLauncher running from path: " + workdir);
        }
        logMessage("COMMAND", String.join(" ", command));
        
        // build up the command and argument string
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workdir != null) {
            File workingdir = new File(workdir);
            if (workingdir.isDirectory()) {
                builder.directory(workingdir);
            }
        }
        
        PrintStream standardOut = System.out;
        PrintStream standardErr = System.err;         

        // re-direct stdout and stderr to the text window
        // merge stderr into stdout so both go to the specified text area
        builder.redirectErrorStream(true);
        PrintStream printStream = new PrintStream(new RedirectOutputStream(response)); 
        System.setOut(printStream);
        System.setErr(printStream);
            
        // run the command
        Process p;
        try {
            p = builder.start();
        } catch (IOException ex) {
            logMessage("ERROR", "builder failure: " + ex);
            return -1;
        }

        String status;
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while (p.isAlive()) {
            try {
                while ((status = br.readLine()) != null) {
                    System.out.println(status);
                }
            } catch (IOException ex) {
                logMessage("ERROR", "BufferedReader failure: " + ex);
                retcode = -1;
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
//                System.err.println("ERROR: Thread.sleep failure: " + ex);
                break;
            }
        }

        if (retcode == 0) {
            retcode = p.exitValue();
        }
        p.destroy();

        // restore the stdout and stderr
        System.setOut(standardOut);
        System.setErr(standardErr);

        return retcode;
    }
    
}

