package edu.vanderbilt.isis.arrays.symbolic_index;

public class SymbolicIndexArray {

  public static void testBasic() {
    int index = 0;
    int[] arr = new int[5];
    arr[0] = 1;
    arr[1] = 2;
    arr[2] = 3;
    arr[3] = 4;
    arr[4] = 5;
    
    if (arr[index] == 5) {
      System.out.println("Concrete path!");
    } else {
      System.out.println("Symbolic path!");
    }
  }
  
  public static void main(String[] args) {
    System.out.println("Beginning symbolic index array test");
    testBasic();
    System.out.println("Ending symbolic index array test");
  }
    
}
