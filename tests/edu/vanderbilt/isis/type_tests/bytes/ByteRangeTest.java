package edu.vanderbilt.isis.type_tests.bytes;

public class ByteRangeTest {

  public static void testRange() {
    byte b = 1;
    b++;
    if (b == -128)
      System.out.println("It rolled over!");
    else
      System.out.println("Concrete path");
  }

  public static void main(String[] args) {
    System.out.println("Beginning byte range test");
    testRange();
    System.out.println("Ending byte range test");
  }
    
}
