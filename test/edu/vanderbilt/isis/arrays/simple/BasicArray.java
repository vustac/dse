package edu.vanderbilt.isis.arrays.simple;

public class BasicArray {

  public static void testBasic() {
    int size = 2;
    int[] arr = new int[size];
    arr[0] = 3;
    if (arr[0] == 3 && arr[1] == 7) {
      System.out.println("Symbolic path!");
    } else {
      System.out.println("Concrete path!");
    }
  }
  
  public static void main(String[] args) {
    System.out.println("Beginning simple array test");
    testBasic();
    System.out.println("Ending simple array test");
  }
    
}
