package edu.vanderbilt.isis.uninstrumented_return.ref_return_multiarray;

import edu.vanderbilt.isis.uninstrumented_return.lib.LibReturnObject;

public class RefReturnMultiArray {

  public static void testReturn() {
    Integer[][] multiarr = (Integer[][]) LibReturnObject.getMultiArrayObject();

    int index1 = 1;
    int index2 = 3;
    int value = multiarr[index1][index2];
    System.out.println("multiarr[1][3] = " + value);

    if (value == 45)
      System.out.println("Concrete!");
    else
      System.out.println("Symbolic!");

  }
 
  public static void main(String[] args) {
    System.out.println("Beginning library return array test");
    testReturn();
    System.out.println("Ending library return array test");
  }
}