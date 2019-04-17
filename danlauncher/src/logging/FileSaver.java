/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package logging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingQueue;
import util.Utils;

/**
 *
 * @author dan
 */
  public class FileSaver extends Thread {

    private static PrintWriter storageWriter;
    private static PrintWriter writer;
    private static BufferedReader reader;
    private static final LinkedBlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();

    public FileSaver() {
      reader = null;
      writer = null;
      storageWriter = null;

      // setup the file to save the data in
      startBufferFile();
      Utils.printStatusInfo("FileSaver: created file cache");
    }
    
    @Override
    public void run() {
      // wait for input and copy to file
      Utils.printStatusInfo("FileSaver thread started!");
      while(true) {
        synchronized (QUEUE) {
          try {
            // wait for input if none
            while (QUEUE.isEmpty()) {
              QUEUE.wait();
            }
            // read next message from input buffer
            String message = QUEUE.take();

            // append message to buffer file
            if (writer != null) {
              writer.write(message + Utils.NEWLINE);
              writer.flush();
            }

            // append message to storage file
            if (storageWriter != null) {
              storageWriter.write(message + Utils.NEWLINE);
              storageWriter.flush();
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            //Utils.printStatusError("FileSaver: " + ex.getMessage());
          }
        }
      }
    }

    /**
     * close up shop
     */
    public void exit() {
      Utils.printStatusInfo("FileSaver: closing");
      if (writer != null) {
        writer.close();
        writer = null;
      }
      if (storageWriter != null) {
        storageWriter.close();
        storageWriter = null;
      }
    }
    
    /**
     * returns the number of messages currently in the queue
     * 
     * @return number of messages in queue
     */
    public int getQueueSize() {
      return QUEUE.size();
    }
  
    /**
     * erases the contents of the log queue
     */
    public void clear() {
      QUEUE.clear();
    }

    /**
     * place a message in the queue for output to the log display.
     * 
     * @param message - the message to display
     */
    public void saveMessage(String message) {
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
     * get the next message from the queue to display.
     * 
     * @return - the message to display
     */
    public String getNextMessage() {
      String message = null;
      if (reader != null) {
        try {
          message = reader.readLine();
        } catch (IOException ex) {
          Utils.printStatusError("FileSaver.getNextMessage: " + ex.getMessage());
        }
      }

      return message;
    }
    
    /**
     * initializes the log file messages
     */
    public void resetInput() {
      String message = "# Logfile started: " + LocalDateTime.now();
      message = message.replace('T', ' ');
      String separator = "----------------------------------------------------------------------";

      if (writer != null) {
        startBufferFile();
        writer.write(separator + Utils.NEWLINE);
        writer.write(message + Utils.NEWLINE);
        writer.flush();
      }

      if (storageWriter != null) {
        storageWriter.write(separator + Utils.NEWLINE);
        storageWriter.write(message + Utils.NEWLINE);
        storageWriter.flush();
      }
    }
    
    /**
     * change the location of the file to use for caching the log information
     * 
     * @param fname - path and name of the logfile to use
     */
    public void setStorageFile(String fname) {
      // ignore if name was not changed
      if (fname == null || fname.isEmpty()) {
        return;
      }

      try {
        // close any current storage writer
        if (storageWriter != null) {
          Utils.printStatusInfo("FileSaver: closing storage writer");
          storageWriter.close();
        }

        // remove any existing file
        File file = new File(fname);
        if (file.isFile()) {
          Utils.printStatusInfo("FileSaver: deleting old file");
          file.delete();
        }

        // set the buffer file to use for saving to project
        Utils.printStatusInfo("FileSaver: writing to " + fname);
        storageWriter = new PrintWriter(new FileWriter(fname, true));

//        // output time log started
//        String message = "# Logfile started: " + LocalDateTime.now();
//        message = message.replace('T', ' ');
//        storageWriter.write(message + Utils.NEWLINE);
//        storageWriter.flush();
      } catch (IOException ex) {  // includes FileNotFoundException
        Utils.printStatusError("FileSaver: " + ex.getMessage());
      }
    }

    private void startBufferFile() {
      // make sure the path exists for the buffer file
      String bufferPath = System.getProperty("user.home") + "/.danlauncher/";
      File file = new File(bufferPath);
      if (!file.isDirectory()) {
        Utils.printStatusInfo("FileSaver: creating path: " + bufferPath);
        file.mkdirs();
      }
      
      // remove any existing buffer file
      String fname = bufferPath + "debug.log";
      file = new File(fname);
      if (file.isFile()) {
        Utils.printStatusInfo("FileSaver: deleting buffer file");
        file.delete();
      }

      try {
        // set the buffer file to use for using in launcher
        Utils.printStatusInfo("FileSaver: buffer at: " + fname);
        writer = new PrintWriter(new FileWriter(fname, true));

        // attach new file reader for output to gui
        reader = new BufferedReader(new FileReader(new File(fname)));
        Utils.printStatusInfo("FileSaver: reader status: " + reader.ready());

      } catch (IOException ex) {  // includes FileNotFoundException
        Utils.printStatusError("FileSaver: " + ex.getMessage());
      }
    }

  }  
