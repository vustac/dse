package edu.vanderbilt.isis.path_browsing;

public class PathBrowser {

  public static int testInput(int value) {
    int code;
    if (value < 0) {
      code = checkInputNegative(value);           // value = { -32, ..., -1 }
    }
    else {
      code = checkInputPositive(value);           // value = { 0, ..., 31 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputNegative(int value) {
    int code;
    if (value < -16) {
      code = checkInputBelowNeg16(value);         // value = { -32, ..., -17 }
    }
    else {
      code = checkInputAboveNeg16(value);         // value = { -16, ..., -1 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowNeg16(int value) {
    int code;
    if (value < -24) {
      code = checkInputBelowNeg24(value);         // value = { -32, ..., -25 }
    }
    else {
      code = checkInputAboveNeg24(value);         // value = { -24, ..., -17 }
    }
    return code;
  }

  public static int checkInputAboveNeg16(int value) {
    int code;
    if (value < -8) {
      code = checkInputBelowNeg8(value);         // value = { -16, ..., -9 }
    }
    else {
      code = checkInputAboveNeg8(value);         // value = { -8, ..., -1 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowNeg24(int value) {
    int code;
    if (value < -28) {
      code = checkInputBelowNeg28(value);         // value = { -32, ..., -29 }
    }
    else {
      code = checkInputAboveNeg28(value);         // value = { -28, ..., -25 }
    }
    return code;
  }

  public static int checkInputAboveNeg24(int value) {
    int code;
    if (value < -20) {
      code = checkInputBelowNeg20(value);         // value = { -24, ..., -21 }
    }
    else {
      code = checkInputAboveNeg20(value);         // value = { -20, ..., -17 }
    }
    return code;
  }

  public static int checkInputBelowNeg8(int value) {
    int code;
    if (value < -12) {
      code = checkInputBelowNeg12(value);         // value = { -16, ..., -13 }
    }
    else {
      code = checkInputAboveNeg12(value);         // value = { -12, ..., -9 }
    }
    return code;
  }

  public static int checkInputAboveNeg8(int value) {
    int code;
    if (value < -4) {
      code = checkInputBelowNeg4(value);         // value = { -8, ..., -5 }
    }
    else {
      code = checkInputAboveNeg4(value);         // value = { -4, ..., -1 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowNeg28(int value) {
    int code;
    if (value < -30) {
      code = checkInputBelowNeg30(value);         // value = { -32, -31 }
    }
    else {
      code = checkInputAboveNeg30(value);         // value = { -30, -29 }
    }
    return code;
  }

  public static int checkInputAboveNeg28(int value) {
    int code;
    if (value < -26) {
      code = checkInputBelowNeg26(value);         // value = { -28, -27 }
    }
    else {
      code = checkInputAboveNeg26(value);         // value = { -26, -25 }
    }
    return code;
  }

  public static int checkInputBelowNeg20(int value) {
    int code;
    if (value < -22) {
      code = checkInputBelowNeg22(value);         // value = { -24, -23 }
    }
    else {
      code = checkInputAboveNeg22(value);         // value = { -22, -21 }
    }
    return code;
  }

  public static int checkInputAboveNeg20(int value) {
    int code;
    if (value < -18) {
      code = checkInputBelowNeg18(value);         // value = { -20, -19 }
    }
    else {
      code = checkInputAboveNeg18(value);         // value = { -18, -17 }
    }
    return code;
  }

  public static int checkInputBelowNeg12(int value) {
    int code;
    if (value < -14) {
      code = checkInputBelowNeg14(value);         // value = { -16, -15 }
    }
    else {
      code = checkInputAboveNeg14(value);         // value = { -14, -13 }
    }
    return code;
  }

  public static int checkInputAboveNeg12(int value) {
    int code;
    if (value < -10) {
      code = checkInputBelowNeg10(value);         // value = { -12, -11 }
    }
    else {
      code = checkInputAboveNeg10(value);         // value = { -10, -9 }
    }
    return code;
  }

  public static int checkInputBelowNeg4(int value) {
    int code;
    if (value < -6) {
      code = checkInputBelowNeg6(value);         // value = { -8, -7 }
    }
    else {
      code = checkInputAboveNeg6(value);         // value = { -6, -5 }
    }
    return code;
  }

  public static int checkInputAboveNeg4(int value) {
    int code;
    if (value < -2) {
      code = checkInputBelowNeg2(value);         // value = { -4, -3 }
    }
    else {
      code = checkInputAboveNeg2(value);         // value = { -2, -1 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowNeg30(int value) {
    if (value < -31) {
      return 0;         // value = -32
    }
    else {
      return 1;         // value = -31
    }
  }

  public static int checkInputAboveNeg30(int value) {
    if (value < -29) {
      return 2;         // value = -30
    }
    else {
      return 3;         // value = -29
    }
  }

  public static int checkInputBelowNeg26(int value) {
    if (value < -27) {
      return 4;         // value = -28
    }
    else {
      return 5;         // value = -27
    }
  }

  public static int checkInputAboveNeg26(int value) {
    if (value < -25) {
      return 6;         // value = -26
    }
    else {
      return 7;         // value = -25
    }
  }

  public static int checkInputBelowNeg22(int value) {
    if (value < -23) {
      return 8;         // value = -24
    }
    else {
      return 9;         // value = -23
    }
  }

  public static int checkInputAboveNeg22(int value) {
    if (value < -21) {
      return 10;        // value = -22
    }
    else {
      return 11;        // value = -21
    }
  }

  public static int checkInputBelowNeg18(int value) {
    if (value < -19) {
      return 12;        // value = -20
    }
    else {
      return 13;        // value = -19
    }
  }

  public static int checkInputAboveNeg18(int value) {
    if (value < -17) {
      return 14;        // value = -18
    }
    else {
      return 15;        // value = -17
    }
  }

  public static int checkInputBelowNeg14(int value) {
    if (value < -15) {
      return 16;        // value = -16
    }
    else {
      return 17;        // value = -15
    }
  }

  public static int checkInputAboveNeg14(int value) {
    if (value < -13) {
      return 18;        // value = -14
    }
    else {
      return 19;        // value = -13
    }
  }

  public static int checkInputBelowNeg10(int value) {
    if (value < -11) {
      return 20;        // value = -12
    }
    else {
      return 21;        // value = -11
    }
  }

  public static int checkInputAboveNeg10(int value) {
    if (value < -9) {
      return 22;        // value = -10
    }
    else {
      return 23;        // value = -9
    }
  }
  
  public static int checkInputBelowNeg6(int value) {
    if (value < -7) {
      return 24;        // value = -8
    }
    else {
      return 25;        // value = -7
    }
  }

  public static int checkInputAboveNeg6(int value) {
    if (value < -5) {
      return 26;        // value = -6
    }
    else {
      return 27;        // value = -5
    }
  }
  
  public static int checkInputBelowNeg2(int value) {
    if (value < -3) {
      return 28;        // value = -4
    }
    else {
      return 29;        // value = -3
    }
  }

  public static int checkInputAboveNeg2(int value) {
    if (value < -1) {
      return 30;        // value = -2
    }
    else {
      return 31;        // value = -1
    }
  }
  
  public static int checkInputPositive(int value) {
    int code;
    if (value < 16) {
      code = checkInputBelowPos16(value);
    }
    else {
      code = checkInputAbovePos16(value);
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowPos16(int value) {
    int code;
    if (value < 8) {
      code = checkInputBelowPos8(value);         // value = { 0, ..., 7 }
    }
    else {
      code = checkInputAbovePos8(value);         // value = { 8, ..., 15 }
    }
    return code;
  }

  public static int checkInputAbovePos16(int value) {
    int code;
    if (value < 24) {
      code = checkInputBelowPos24(value);         // value = { 16, ..., 23 }
    }
    else {
      code = checkInputAbovePos24(value);         // value = { 24, ..., 31 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowPos8(int value) {
    int code;
    if (value < 4) {
      code = checkInputBelowPos4(value);         // value = { 0, ..., 3 }
    }
    else {
      code = checkInputAbovePos4(value);         // value = { 4, ..., 7 }
    }
    return code;
  }

  public static int checkInputAbovePos8(int value) {
    int code;
    if (value < 12) {
      code = checkInputBelowPos12(value);         // value = { 8, ..., 11 }
    }
    else {
      code = checkInputAbovePos12(value);         // value = { 12, ..., 15 }
    }
    return code;
  }

  public static int checkInputBelowPos24(int value) {
    int code;
    if (value < 20) {
      code = checkInputBelowPos20(value);         // value = { 16, ..., 19 }
    }
    else {
      code = checkInputAbovePos20(value);         // value = { 20, ..., 23 }
    }
    return code;
  }

  public static int checkInputAbovePos24(int value) {
    int code;
    if (value < 28) {
      code = checkInputBelowPos28(value);         // value = { 24, ..., 27 }
    }
    else {
      code = checkInputAbovePos28(value);         // value = { 28, ..., 31 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowPos4(int value) {
    int code;
    if (value < 2) {
      code = checkInputBelowPos2(value);         // value = { 0, 1 }
    }
    else {
      code = checkInputAbovePos2(value);         // value = { 2, 3 }
    }
    return code;
  }

  public static int checkInputAbovePos4(int value) {
    int code;
    if (value < 6) {
      code = checkInputBelowPos6(value);         // value = { 4, 5 }
    }
    else {
      code = checkInputAbovePos6(value);         // value = { 6, 7 }
    }
    return code;
  }

  public static int checkInputBelowPos12(int value) {
    int code;
    if (value < 10) {
      code = checkInputBelowPos10(value);         // value = { 8, 9 }
    }
    else {
      code = checkInputAbovePos10(value);         // value = { 10, 11 }
    }
    return code;
  }

  public static int checkInputAbovePos12(int value) {
    int code;
    if (value < 14) {
      code = checkInputBelowPos14(value);         // value = { 12, 13 }
    }
    else {
      code = checkInputAbovePos14(value);         // value = { 14, 15 }
    }
    return code;
  }

  public static int checkInputBelowPos20(int value) {
    int code;
    if (value < 18) {
      code = checkInputBelowPos18(value);         // value = { 16, 17 }
    }
    else {
      code = checkInputAbovePos18(value);         // value = { 18, 19 }
    }
    return code;
  }

  public static int checkInputAbovePos20(int value) {
    int code;
    if (value < 22) {
      code = checkInputBelowPos22(value);         // value = { 20, 21 }
    }
    else {
      code = checkInputAbovePos22(value);         // value = { 22, 23 }
    }
    return code;
  }

  public static int checkInputBelowPos28(int value) {
    int code;
    if (value < 26) {
      code = checkInputBelowPos26(value);         // value = { 24, 25 }
    }
    else {
      code = checkInputAbovePos26(value);         // value = { 26, 27 }
    }
    return code;
  }

  public static int checkInputAbovePos28(int value) {
    int code;
    if (value < 30) {
      code = checkInputBelowPos30(value);         // value = { 28, 29 }
    }
    else {
      code = checkInputAbovePos30(value);         // value = { 30, 31 }
    }
    return code;
  }

  //-------------------------------

  public static int checkInputBelowPos2(int value) {
    int code;
    if (value < 1) {
      return 32;         // value = 0
    }
    else {
      return 33;         // value = 1
    }
  }

  public static int checkInputAbovePos2(int value) {
    int code;
    if (value < 3) {
      return 34;         // value = 2
    }
    else {
      return 35;         // value = 3
    }
  }

  public static int checkInputBelowPos6(int value) {
    int code;
    if (value < 5) {
      return 36;         // value = 4
    }
    else {
      return 37;         // value = 5
    }
  }

  public static int checkInputAbovePos6(int value) {
    int code;
    if (value < 7) {
      return 38;         // value = 6
    }
    else {
      return 39;         // value = 7
    }
  }

  public static int checkInputBelowPos10(int value) {
    int code;
    if (value < 9) {
      return 40;         // value = 8
    }
    else {
      return 41;         // value = 9
    }
  }

  public static int checkInputAbovePos10(int value) {
    int code;
    if (value < 11) {
      return 42;         // value = 10
    }
    else {
      return 43;         // value = 11
    }
  }

  public static int checkInputBelowPos14(int value) {
    int code;
    if (value < 13) {
      return 44;         // value = 12
    }
    else {
      return 45;         // value = 13
    }
  }

  public static int checkInputAbovePos14(int value) {
    int code;
    if (value < 15) {
      return 46;         // value = 14
    }
    else {
      return 47;         // value = 15
    }
  }

  public static int checkInputBelowPos18(int value) {
    int code;
    if (value < 17) {
      return 48;         // value = 16
    }
    else {
      return 49;         // value = 17
    }
  }

  public static int checkInputAbovePos18(int value) {
    int code;
    if (value < 19) {
      return 50;         // value = 18
    }
    else {
      return 51;         // value = 19
    }
  }

  public static int checkInputBelowPos22(int value) {
    int code;
    if (value < 21) {
      return 52;         // value = 20
    }
    else {
      return 53;         // value = 21
    }
  }

  public static int checkInputAbovePos22(int value) {
    int code;
    if (value < 23) {
      return 54;         // value = 22
    }
    else {
      return 55;         // value = 23
    }
  }

  public static int checkInputBelowPos26(int value) {
    int code;
    if (value < 25) {
      return 56;         // value = 24
    }
    else {
      return 57;         // value = 25
    }
  }

  public static int checkInputAbovePos26(int value) {
    int code;
    if (value < 27) {
      return 58;         // value = 26
    }
    else {
      return 59;         // value = 27
    }
  }

  public static int checkInputBelowPos30(int value) {
    int code;
    if (value < 29) {
      return 60;         // value = 28
    }
    else {
      return 61;         // value = 29
    }
  }

  public static int checkInputAbovePos30(int value) {
    int code;
    if (value < 31) {
      return 62;         // value = 30
    }
    else {
      return 63;         // value = 31
    }
  }

  //-------------------------------

  public static void main(String[] args) {
    System.out.println("Beginning path test");
    int value = 0;
    try {
      value = Integer.parseInt(args[0]);
    } catch (NumberFormatException ex) {
      System.out.println("Invalid user input");
    }
    int retval = testInput(value);
    System.out.println("Ending path test. Returned: " + retval);
  }
    
}
