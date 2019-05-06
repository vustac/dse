package edu.vanderbilt.isis.strings.symbolic_length;

public class SymbolicStringLength {

  public static void symbolicLengthTest() {
    String str = new String();
    int len = str.length();
    if (len == 7)
      System.out.println("Symbolic path!");
    else
      System.out.println("Concrete path!");
  }
  
  public static void main(String[] args) {
    System.out.println("Beginning symbolic string length test");
    symbolicLengthTest();
    System.out.println("Ending symbolic string length test");
  }
    
}
