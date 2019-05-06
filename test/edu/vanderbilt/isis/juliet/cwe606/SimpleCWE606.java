package edu.vanderbilt.isis.juliet.cwe606;

import java.io.*;
import java.io.InputStreamReader;

public class SimpleCWE606 {

  public static void badSink(Object dataObject) {
    String data = (String)dataObject;

    int numberOfLoops;
    try {
      numberOfLoops = Integer.parseInt(data);
    } catch (NumberFormatException exceptNumberFormat) {
      System.err.println("Invalid response. Numeric input expected. Assuming 1.");
      numberOfLoops = 1;
    }

    for (int i=0; i < numberOfLoops; i++) {
      /* POTENTIAL FLAW: user supplied input used for loop counter test */
      System.out.println("hello world");
    }
    
  }
  
  public static void test() {

    String data = "";

    InputStreamReader readerInputStream = null;
    BufferedReader readerBuffered = null;

    /* read user input from console with readLine */
    try {
      readerInputStream = new InputStreamReader(System.in, "UTF-8");
      readerBuffered = new BufferedReader(readerInputStream);

      /* POTENTIAL FLAW: Read data from the console using readLine */
      data = readerBuffered.readLine();
      System.out.println("read input: '" + data + "'");
    } catch (IOException exceptIO) {
      System.err.println("Error with stream reading");
    } finally {
      try {
        if (readerBuffered != null) {
          readerBuffered.close();
        }
      } catch (IOException exceptIO) {
        System.err.println("Error closing BufferedReader");
      }

      try {
        if (readerInputStream != null) {
          readerInputStream.close();
        }
      } catch (IOException exceptIO) {
        System.err.println("Error closing InputStreamReader");
      }
    }

    badSink((Object)data);
  }

  public static void main(String[] args) {
    System.out.println("Beginning CWE606 console readline vulnerable test!");
    test();
    System.out.println("Ending CWE606 console readline vulnerable test!");
  }
}
