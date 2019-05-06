package edu.vanderbilt.isis.arrays.maximize_test;

public class SymbolicMaximizeTest {

  public static void testMaximize() {
    int index = 0;
    int[] arr = new int[10];

    arr[0] = 0;
    arr[1] = 10;
    arr[2] = 200;
    arr[3] = 30;
    arr[4] = 40;
    arr[5] = 50;
    arr[6] = 45;
    arr[7] = 100;
    arr[8] = 10;
    arr[9] = 0;

    int value = arr[index];
    System.out.println("arr[0] = " + value);

    if (value > 60)
      System.out.println("Concrete!");
    else
      System.out.println("Symbolic!");

  }
  
  public static void main(String[] args) {
    System.out.println("Beginning symbolic array maximize test");
    testMaximize();
    System.out.println("Ending symbolic array maximize test");
  }
}
