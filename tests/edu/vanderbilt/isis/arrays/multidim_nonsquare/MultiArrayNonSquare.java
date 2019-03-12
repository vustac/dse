package edu.vanderbilt.isis.arrays.multidim_nonsquare;

public class MultiArrayNonSquare {

  public static void testNonSquare() {
    int rowsize = 1;
    int colsize = 3;
    int[][] arr = new int[rowsize][colsize];
    
    // set the values of the array
    byte value = 1; // make this symbolic to find value that passes the symbolic test
    int ix = 1;
    for (int row = 0; row < rowsize; row++) {
      for (int col = 0; col < colsize; col++) {
        byte entry = (byte) (ix + value);
        arr[row][col] = entry;
        ++ix;
      }
    }
    
    // check for match
    if (arr[0][0] > arr[rowsize-1][colsize-1]) {
      System.out.println("Symbolic path!");
    } else {
      System.out.println("Concrete path!");
    }
  }
  
  public static void main(String[] args) {
    System.out.println("Beginning multi-dimensional non-square array test");
    testNonSquare();
    System.out.println("Ending simple array test");
  }
    
}
