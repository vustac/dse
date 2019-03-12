package edu.vanderbilt.isis.uninstrumented_return.lib;

public class LibReturnObject {

  public static Object getArrayObject() {
    Integer[] obj = { 10, 20, 30, 40, 50 };
    return obj;
  }

  public static Object getMultiArrayObject() {
    Integer[][] obj = {
      { 10, 20, 30, 40, 50 },
      { 15, 25, 35, 45, 55 }
    };
    return obj;
  }

  public static Object getDoubleObject(int input) {
    Double obj = (double) input;
    return obj;
  }

  public static Object getFloatObject(int input) {
    Float obj = (float) input;
    return obj;
  }

  public static Object getLongObject(int input) {
    Long obj = (long) input;
    return obj;
  }

  public static Object getIntegerObject(int input) {
    Integer obj = input;
    return obj;
  }

  public static Object getShortObject(int input) {
    Short obj = (short) (input & 0xffff);
    return obj;
  }

  public static Object getByteObject(int input) {
    Byte obj = (byte) (input & 0xff);
    return obj;
  }

  public static Object getCharObject(int input) {
    Character obj = (char) (input & 0xffff);
    return obj;
  }

  public static Object getBooleanObject(int input) {
    Boolean obj = input != 0;
    return obj;
  }

  public static Object getStringObject(int input) {
    String obj = "" + input;
    return obj;
  }

}