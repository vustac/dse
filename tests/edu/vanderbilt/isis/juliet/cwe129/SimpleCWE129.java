package edu.vanderbilt.isis.juliet.cwe129;

import java.io.*;
import java.io.InputStreamReader;

public class SimpleCWE129 {

  public static void badSink(Object dataObject) {
    String data = (String)dataObject;

    int index;
    try {
      index = Integer.parseInt(data);
    } catch (NumberFormatException exceptNumberFormat) {
      System.err.println("Invalid response. Numeric input expected. Assuming 1.");
      index = 1;
    }

    int array[] = { 0, 1, 2, 3, 4 };

    /* POTENTIAL FLAW: Verify that data < array.length, but don't verify that data > 0, so may be attempting to read out of the array bounds */
    if (index < array.length) {
      System.out.println("array[" + index + "] = " + array[index]);
    } else {
      System.err.println("Array index out of bounds");
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
    System.out.println("Beginning CWE129 console readline vulnerable test!");
    test();
    System.out.println("Ending CWE129 console readline vulnerable test!");
  }
}
