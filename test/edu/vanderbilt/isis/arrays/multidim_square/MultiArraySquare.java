package edu.vanderbilt.isis.arrays.multidim_square;

public class MultiArraySquare {

  public static void testSquare() {
    int rowsize = 3;
    int colsize = rowsize;
    byte[][] arr = new byte[rowsize][colsize];
    
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
    System.out.println("Beginning multi-dimensional square array test");
    testSquare();
    System.out.println("Ending simple array test");
  }
    
}
