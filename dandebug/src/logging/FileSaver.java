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
    private static LinkedBlockingQueue<String> queue;

    public FileSaver(LinkedBlockingQueue<String> recvBuffer) {
      queue = recvBuffer;
      reader = null;
      writer = null;
      storageWriter = null;

      // setup the file to save the data in
      startBufferFile();
      System.out.println("FileSaver: started");
    }
    
    @Override
    public void run() {
      // wait for input and copy to file
      while(true) {
        if (!queue.isEmpty()) {
          try {
            // read next message from input buffer
            String message = queue.take();

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
            System.out.println("FileSaver: " + ex.getMessage());
          }
        }
      }
    }

    public void exit() {
      System.out.println("FileSaver: closing");
      if (writer != null) {
        writer.close();
        writer = null;
      }
      if (storageWriter != null) {
        storageWriter.close();
        storageWriter = null;
      }
    }
    
    public String getNextMessage() {
      String message = null;
      if (reader != null) {
        try {
          message = reader.readLine();
        } catch (IOException ex) {
          System.out.println("getNextMessage: " + ex.getMessage());
        }
      }

      return message;
    }
    
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
    
    public void setStorageFile(String fname) {
      // ignore if name was not changed
      if (fname == null || fname.isEmpty()) {
        return;
      }

      try {
        // close any current storage writer
        if (storageWriter != null) {
          System.out.println("FileSaver: closing storage writer");
          storageWriter.close();
        }

        // remove any existing file
        File file = new File(fname);
        if (file.isFile()) {
          System.out.println("FileSaver: deleting file");
          file.delete();
        }

        // set the buffer file to use for saving to project
        System.out.println("FileSaver: writing to " + fname);
        storageWriter = new PrintWriter(new FileWriter(fname, true));

//        // output time log started
//        String message = "# Logfile started: " + LocalDateTime.now();
//        message = message.replace('T', ' ');
//        storageWriter.write(message + Utils.NEWLINE);
//        storageWriter.flush();
      } catch (IOException ex) {  // includes FileNotFoundException
        System.out.println(ex.getMessage());
      }
    }

    private void startBufferFile() {
      // make sure the path exists for the buffer file
      String bufferPath = System.getProperty("user.home") + "/.danlauncher/";
      File file = new File(bufferPath);
      if (!file.isDirectory()) {
        file.mkdirs();
      }
      
      // remove any existing buffer file
      String fname = bufferPath + "debug.log";
      file = new File(fname);
      if (file.isFile()) {
        System.out.println("FileSaver: deleting buffer file");
        file.delete();
      }

      try {
        // set the buffer file to use for using in launcher
        System.out.println("FileSaver: buffer at: " + fname);
        writer = new PrintWriter(new FileWriter(fname, true));

        // attach new file reader for output to gui
        reader = new BufferedReader(new FileReader(new File(fname)));
        System.out.println("FileSaver: reader status: " + reader.ready());

      } catch (IOException ex) {  // includes FileNotFoundException
        System.out.println(ex.getMessage());
      }
    }

  }  
