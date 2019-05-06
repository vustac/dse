package edu.vanderbilt.isis.uninstrumented_return.lib;

public class LibReturnObject {

  public static Object getArrayObject() {
    String[] obj = { "a", "b", "c", "d", "e" };
    return obj;
  }

  public static Object getMultiArrayObject() {
    Integer[][] obj = {
      { 10, 20, 30, 40, 50 },
      { 15, 25, 35, 45, 55 }
    };
    return obj;
  }

}