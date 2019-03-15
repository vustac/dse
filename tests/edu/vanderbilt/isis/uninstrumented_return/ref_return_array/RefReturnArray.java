package edu.vanderbilt.isis.uninstrumented_return.ref_return_array;

import edu.vanderbilt.isis.uninstrumented_return.lib.LibReturnObject;

public class RefReturnArray {

  public static void testReturn() {
    String[] arr = (String[]) LibReturnObject.getArrayObject();

    int index = 0;
    String value = arr[index];
    //System.out.println("arr[0] = " + value);

    if (value.equals("c"))
      System.out.println("Symbolic!");
    else
      System.out.println("Concrete!");
  }
 
  public static void main(String[] args) {
    System.out.println("Beginning library return array test");
    testReturn();
    System.out.println("Ending library return array test");
  }
}