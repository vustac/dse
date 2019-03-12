package edu.vanderbilt.isis.uninstrumented_return.ref_return_double;

import edu.vanderbilt.isis.uninstrumented_return.lib.LibReturnObject;

public class RefReturnDouble {

  public static void testReturn() {
    int index = 5;
    Double value = (Double) LibReturnObject.getDoubleObject(index);

    System.out.println("object = " + value);

    if (value == 1.0)
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