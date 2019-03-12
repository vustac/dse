package edu.vanderbilt.isis.uninstrumented_return.ref_return_array;

import edu.vanderbilt.isis.uninstrumented_return.lib.LibReturnObject;

public class RefReturnArray {

  public static void testReturn() {
    Integer[] arr = (Integer[]) LibReturnObject.getArrayObject();

    int index = 0;
    int value = arr[index];
    System.out.println("arr[0] = " + value);

    if (value == 10)
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