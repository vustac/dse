package edu.vanderbilt.isis.strings.symbolic_equals;

public class SymbolicStringEquals {

  public static void symbolicEqualsTest() {
    String str = "123abc";
    if (str.equals("abc123"))
      System.out.println("Symbolic path!");
    else
      System.out.println("Concrete path!");
  }
  
  public static void main(String[] args) {
    System.out.println("Beginning symbolic string length test");
    symbolicEqualsTest();
    System.out.println("Ending symbolic string length test");
  }
    
}
