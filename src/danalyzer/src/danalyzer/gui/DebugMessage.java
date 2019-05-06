/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danalyzer.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

/**
 *
 * @author dan
 */
public class DebugMessage {
    
  /**
   * these are the types of messages that can be displayed
   */
  public enum DebugType {
    // these are for local use
    Tstamp,   // the timestamp value

    // these are for external use
    Normal,   // plain vanilla text (black, no bold or italics)
    Info,     // general information
    Warn,     // warnings
    Error,    // error messages (always output)
    Dump,     // a trace and stack dump (always output)

    Start,    // issued by the DSE when the 1st instrumented method is called (always printed)
    Entry,    // entry to an Executor command
    Agent,    // agent command output
    Object,   // object (REF type) info
    Array,    // array info
    Call,     // Executor command to call method
    Return,   // Executor command to return from method
    Uninst,   // Executor command to call uninstrumented method
    Stats,    // additional method call statistics
    Stack,    // stack push/pop (concrete value)
    StackS,   // stack push/pop (symbolic value)
    StackI,   // stack new (indicates entryto a method call)
    Local,    // local parameter load/store (concrete value)
    LocalS,   // local parameter load/store (symbolic value)
    Branch,   // branch info
    Constr,   // constraint info
    Solve;    // solver output
  }
    
  private enum FontType {
    Normal, Bold, Italic, BoldItalic;
  }
    
  private enum TextColor {
    Black, DkGrey, DkRed, Red, LtRed, Orange, Brown, Gold, Green, Cyan,
    LtBlue, Blue, Violet, DkVio;
  }
    
  // these are used for limiting the amount of text displayed in the logger display to limit
  // memory use. MAX_TEXT_BUFFER_SIZE defines the upper memory usage when the reduction takes
  // place and REDUCE_BUFFER_SIZE is the minimum number of bytes to reduce it by (it will look
  // for the next NEWLINE char).
  private final static int MAX_TEXT_BUFFER_SIZE = 150000;
  private final static int REDUCE_BUFFER_SIZE   = 50000;
  
  // used in formatting printArray
  private static final String NEWLINE = System.getProperty("line.separator");

  private static JTextPane debugTextPane;
  private static long      startTime;
  private static boolean   showHours = false;
  private static boolean   showMsecs = true;
  private static int       msgIx = 0;
  private static int       serverPort = -1;
  private static String    serverAddress;
  private static DatagramSocket udpDataSocket = null;
  private static Socket         tcpDataSocket = null;
  private static DataOutputStream tcpDataOut;
  private static final HashMap<DebugType, FontInfo> messageTypeTbl = new HashMap<>();

  public DebugMessage (JTextPane textpane) {
    debugTextPane = textpane;
      
    // if panel was passed, set it up
    if (textpane != null) {
      // setup message type colors
      setColors();

      msgIx = 0;
      serverPort = -1;
      serverAddress = "localhost";
      udpDataSocket = null;
      tcpDataSocket = null;
      startTime = System.currentTimeMillis();
    }
  }

  public DebugMessage (String address, int port, boolean tcp) {
    debugTextPane = null;
    msgIx = 0;
    startTime = System.currentTimeMillis();
      
    // setup message type colors
    setColors();

    // setup port
    initSocket(address, port, tcp);
  }

  /**
   * clears the display.
   */
  public static final void clear() {
    if (debugTextPane != null) {
      debugTextPane.setText("");
    }
  }

  /**
   * updates the display immediately
   */
  public static final void updateDisplay () {
    if (debugTextPane != null) {
      Graphics graphics = debugTextPane.getGraphics();
      if (graphics != null) {
        debugTextPane.update(graphics);
      }
    }
  }

  private static enum OutputType { TCPPORT, UDPPORT, GUI, STDOUT };
  
  private static void initSocket(String address, int port, boolean tcp) {
    serverPort = port;
    serverAddress = address.isEmpty() ? "localhost" : address;
    
    udpDataSocket = null;
    tcpDataSocket = null;

    if (tcp) {
      try {
        tcpDataSocket = new Socket(address, port);
        tcpDataOut = new DataOutputStream(tcpDataSocket.getOutputStream());
      } catch (IOException ex) {
        System.err.println("ERROR: <DebugMessage.init>" + ex.getMessage());
        tcpDataSocket = null;
      }
    } else {
      try {
        udpDataSocket = new DatagramSocket();
      } catch (SocketException ex) {
        System.err.println("ERROR: <DebugMessage.init> " + ex.getMessage());
        udpDataSocket = null;
      }
    }
  }
  
  private static void closeSocket() {
    try {
      if(tcpDataOut != null) {
        tcpDataOut.close();
        tcpDataOut = null;
      }
      if(tcpDataSocket != null) {
        tcpDataSocket.close();
        tcpDataSocket = null;
      }
      if(udpDataSocket != null) {
        udpDataSocket.close();
        udpDataSocket = null;
      }
    } catch (IOException ex) { }
  }
  
  /**
   * outputs the various types of messages to the status display.
   * all messages will guarantee the previous line was terminated with a newline,
   * and will preceed the message with a timestamp value and terminate with a newline.
   * 
   * @param dest    - where to send the output
   * @param tid     - the thread id
   * @param type    - the type of message
   * @param message - the message to display
   */
  private static int printLine(OutputType dest, int tid, DebugType type, String message) {
    if (message == null || message.isEmpty()) {
      return 0;
    }
    // limit the type to a fixed length (pad with spaces if necessary)
    String typestr = formatStringLength(type);

    // get the current thread id
    String threadStr = tid < 10 ? "0" + tid : "" + tid;

    // compose the header to the message
    String header = getCounter() + " [" + getElapsedTime() + "] <" + threadStr + "> ";

    switch (dest) {
      case UDPPORT:
        try {
          final ByteArrayOutputStream boStream = new ByteArrayOutputStream();
          // write the data to the packet
          try (DataOutputStream udpDataOut = new DataOutputStream(boStream)) {
            udpDataOut.writeUTF(header + typestr + ": " + message);
          }

          byte[] buf = boStream.toByteArray();
          DatagramPacket packet = new DatagramPacket(buf, buf.length);
          packet.setAddress(InetAddress.getByName(serverAddress));
          packet.setPort(serverPort);
          udpDataSocket.send(packet);
        } catch (SocketException | UnknownHostException ex) {
          System.err.println("ERROR: <DebugMessage.printLine> " + ex.getMessage());
          udpDataSocket = null;
          return 1;
        } catch (IOException ex) {
          System.err.println("ERROR: <DebugMessage.printLine> " + ex.getMessage());
          udpDataSocket = null;
          return 1;
        }
        break;

      case TCPPORT:
        try {
          tcpDataOut.writeBytes(header + typestr + ": " + message + NEWLINE);
        } catch (IOException | NullPointerException ex) {
          System.err.println("ERROR: <DebugMessage.printLine>" + ex.getMessage());
          tcpDataSocket = null;
          return 1;
        }
        break;

      case GUI:
        printHeader(header);
        printType(type);
        printRaw(type, message + NEWLINE);
        break;

      default:
        // send message to terminal
        System.out.println(header + typestr + ": " + message);
        break;
    }

    return 0;
  }

  /**
   * outputs the various types of messages to the status display.
   * all messages will guarantee the previous line was terminated with a newline,
   * and will preceed the message with a timestamp value and terminate with a newline.
   * 
   * @param tid     - the thread id
   * @param type    - the type of message
   * @param message - the message to display
   */
  public static final void print(int tid, DebugType type, String message) {
    if (message == null || message.isEmpty()) {
      return;
    }

    // limit the type to a fixed length (pad with spaces if necessary)
    String typestr = formatStringLength(type);

    // determine output location
    OutputType dest = OutputType.STDOUT;
    if (udpDataSocket != null && serverPort >= 0) {
      dest = OutputType.UDPPORT;
    } else if (tcpDataSocket != null && tcpDataOut != null && serverPort >= 0) {
      dest = OutputType.TCPPORT;
    } else if (debugTextPane != null) {
      dest = OutputType.GUI;
    }
    
    // seperate into lines and print each independantly
    if (message.contains(NEWLINE)) {
      String[] msgarray = message.split(NEWLINE);
      for (String msg : msgarray) {
        printLine(dest, tid, type, msg);
      }
    } else {
      printLine(dest, tid, type, message);
    }
  }
    
  /**
   * outputs the various types of messages to the status display.
   * all messages will guarantee the previous line was terminated with a newline,
   * and will preceed the message with a timestamp value and terminate with a newline.
   * 
   * @param type    - the destination to verify
   * @param address - address of host to send message to
   * @param port    - port to send to
   * @return 0 if successful, 1 if not
   */
  public static final int outputCheck(String type, String address, int port) {
    // determine output location
    boolean tcp = type.equals("TCPPORT");
    OutputType dest = OutputType.STDOUT;
    String message;
    switch (type) {
      case "UDPPORT":
        dest = OutputType.UDPPORT;
        message = "testing UDP port " + port + " on " + address;
        initSocket(address, port, tcp);
        break;
      case "TCPPORT":
        dest = OutputType.TCPPORT;
        message = "testing TCP port " + port + " on " + address;
        initSocket(address, port, tcp);
        break;
      default:
        message = "testing STDOUT";
        break;
    }

    msgIx = 0;
    startTime = System.currentTimeMillis();
    System.out.println(message);

    int retcode = printLine(dest, 0, DebugType.Info, "# TEST, TEST, TEST");
    closeSocket();
    return retcode;
  }
  
  /**
   * a simple test of the colors
   */
  public final static void testColors () {
    if (debugTextPane == null) {
      return;
    }
    appendToPane("-----------------------------------------" + NEWLINE, TextColor.Black,
                                                                        FontType.Normal);
    for (TextColor color : TextColor.values()) {
      appendToPane("This is a sample of the color: " + color + NEWLINE, color,
                                                                        FontType.Bold);
    }
    appendToPane("-----------------------------------------" + NEWLINE, TextColor.Black,
                                                                        FontType.Normal);
  }
    
  private void setColors () {
    setTypeColor (DebugType.Tstamp, TextColor.Black,  FontType.Normal);

    setTypeColor (DebugType.Error,  TextColor.Red,    FontType.Bold);
    setTypeColor (DebugType.Warn,   TextColor.Orange, FontType.Bold);
    setTypeColor (DebugType.Info,   TextColor.Black,  FontType.Italic);
    setTypeColor (DebugType.Normal, TextColor.Black,  FontType.Normal);
    setTypeColor (DebugType.Dump,   TextColor.Orange, FontType.Bold);

    setTypeColor (DebugType.Start,  TextColor.Black,  FontType.BoldItalic);
    setTypeColor (DebugType.Entry,  TextColor.Brown,  FontType.Normal);
    setTypeColor (DebugType.Agent,  TextColor.Violet, FontType.Italic);
    setTypeColor (DebugType.Object, TextColor.DkVio,  FontType.Italic);
    setTypeColor (DebugType.Array,  TextColor.DkVio,  FontType.Italic);
    setTypeColor (DebugType.Call,   TextColor.Gold,   FontType.Bold);
    setTypeColor (DebugType.Return, TextColor.Gold,   FontType.Bold);
    setTypeColor (DebugType.Uninst, TextColor.Gold,   FontType.BoldItalic);
    setTypeColor (DebugType.Stats,  TextColor.Gold,   FontType.BoldItalic);
    setTypeColor (DebugType.Stack,  TextColor.Blue,   FontType.Normal);
    setTypeColor (DebugType.StackS, TextColor.Blue,   FontType.Italic);
    setTypeColor (DebugType.StackI, TextColor.Blue,   FontType.Bold);
    setTypeColor (DebugType.Local,  TextColor.Green,  FontType.Normal);
    setTypeColor (DebugType.LocalS, TextColor.Green,  FontType.Italic);
    setTypeColor (DebugType.Branch, TextColor.LtBlue, FontType.BoldItalic);
    setTypeColor (DebugType.Constr, TextColor.DkVio,  FontType.BoldItalic);
    setTypeColor (DebugType.Solve,  TextColor.DkVio,  FontType.Bold);
  }
  
  private static String getCounter() {
    String countstr = "00000000" + ((Integer) msgIx).toString();
    countstr = countstr.substring(countstr.length() - 8);
    msgIx++;
    return countstr;
  }
  
  /**
   * returns the elapsed time in seconds.
   * The format of the String is: "HH:MM:SS"
   * 
   * @return a String of the formatted time
   */
  private static String getElapsedTime () {
    // get the elapsed time in secs
    long currentTime = System.currentTimeMillis();
    long elapsedTime = currentTime - startTime;
    if (elapsedTime < 0) {
      elapsedTime = 0;
    }
        
    // split value into hours, min and secs
    Long msecs = elapsedTime % 1000;
    Long secs = (elapsedTime / 1000);
    Long hours = 0L;
    if (!showMsecs) {
      secs += msecs >= 500 ? 1 : 0;
    }
    if (showHours) {
      hours = secs / 3600;
    }
    secs %= 3600;
    Long mins = secs / 60;
    secs %= 60;

    // now stringify it
    String elapsed = "";
    if (showHours) {
      if (hours < 10) {
        elapsed = "0";
      }
      elapsed += hours.toString();
      elapsed += ":";
    }
    elapsed += (mins < 10) ? "0" + mins.toString() : mins.toString();
    elapsed += ":";
    elapsed += (secs < 10) ? "0" + secs.toString() : secs.toString();
    if (showMsecs) {
      String msecstr = "00" + msecs.toString();
      msecstr = msecstr.substring(msecstr.length() - 3);
      elapsed += "." + msecstr;
    }
    return elapsed;
  }

  /**
   * sets the specified input string to a fixed length all uppercase version
   * 
   * @param value - the string value
   * @return the properly formatted version
   */
  private static String formatStringLength (DebugType type) {
    String value = type.toString();
    value += "      ";
    return value.substring(0, 6).toUpperCase();
  }
    
  /**
   * A generic function for appending formatted text to a JTextPane.
   * 
   * @param tp    - the TextPane to append to
   * @param msg   - message contents to write
   * @param color - color of text
   * @param font  - the font selection
   * @param size  - the font point size
   * @param ftype - type of font style
   */
  private static void appendToPane(String msg, TextColor color, String font, int size,
                                   FontType ftype) {
    if (debugTextPane == null) {
      return;
    }
        
    AttributeSet aset = setTextAttr(color, font, size, ftype);
    int len = debugTextPane.getDocument().getLength();

    // trim off earlier data to reduce memory usage if we exceed our bounds
    if (len > MAX_TEXT_BUFFER_SIZE) {
      try {
        int oldlen = len;
        int start = REDUCE_BUFFER_SIZE;
        String text = debugTextPane.getDocument().getText(start, 500);
        int offset = text.indexOf(NEWLINE);
        if (offset >= 0) {
          start += offset + 1;
        }
        debugTextPane.getDocument().remove(0, start);
        len = debugTextPane.getDocument().getLength();
        //System.out.println("Reduced text from " + oldlen + " to " + len);
      } catch (BadLocationException ex) {
        System.err.println("ERROR: <DebugMessage.appendToPane>" + ex.getMessage());
      }
    }

    debugTextPane.setCaretPosition(len);
    debugTextPane.setCharacterAttributes(aset, false);
    debugTextPane.replaceSelection(msg);
  }

  /**
   * A generic function for appending formatted text to a JTextPane.
   * 
   * @param tp    - the TextPane to append to
   * @param msg   - message contents to write
   * @param color - color of text
   * @param ftype - type of font style
   */
  private static void appendToPane(String msg, TextColor color, FontType ftype) {
    appendToPane(msg, color, "Courier", 11, ftype);
  }

  /**
   * sets the association between a type of message and the characteristics
   * in which to print the message.
   * 
   * @param type  - the type to associate with the font characteristics
   * @param color - the color to assign to the type
   * @param ftype - the font attributes to associate with the type
   */
  private void setTypeColor (DebugType type, TextColor color, FontType ftype) {
    setTypeColor (type, color, ftype, 11, "Courier");
  }
    
  /**
   * same as above, but lets user select font family and size as well.
   * 
   * @param type  - the type to associate with the font characteristics
   * @param color - the color to assign to the type
   * @param ftype - the font attributes to associate with the type
   * @param size  - the size of the font
   * @param font  - the font family (e.g. Courier, Ariel, etc.)
   */
  private void setTypeColor (DebugType type, TextColor color, FontType ftype, int size, String font) {
    FontInfo fontinfo = new FontInfo(color, ftype, size, font);
    if (messageTypeTbl.containsKey(type)) {
      messageTypeTbl.replace(type, fontinfo);
    }
    else {
      messageTypeTbl.put(type, fontinfo);
    }
  }

  /**
   * outputs the header info to the debug window.
   */
  private static void printHeader(String header) {
    printRaw(DebugType.Tstamp, header);
  }
  
  /**
   * outputs the message type to the debug window.
   * 
   * @param type - the message type to display
   */
  private static void printType(DebugType type) {
    printRaw(type, formatStringLength(type) + ": ");
  }
    
  /**
   * displays a message in the debug window (no termination).
   * 
   * @param type  - the type of message to display
   * @param message - message contents to display
   */
  private static void printRaw(DebugType type, String message) {
    if (message != null && !message.isEmpty()) {
      if (debugTextPane == null) {
        System.out.print(message);
        return;
      }
        
      // set default values (if type was not found)
      TextColor color = TextColor.Black;
      FontType ftype = FontType.Normal;
      String font = "Courier";
      int size = 11;

      // get the color and font for the specified type
      FontInfo fontinfo = messageTypeTbl.get(type);
      if (fontinfo != null) {
        color = fontinfo.color;
        ftype = fontinfo.fonttype;
        font  = fontinfo.font;
        size  = fontinfo.size;
      }

      appendToPane(message, color, font, size, ftype);
    }
  }

  /**
   * generates the specified text color for the debug display.
   * 
   * @param colorName - name of the color to generate
   * @return corresponding Color value representation
   */
  private static Color generateColor (TextColor colorName) {
    float hue, sat, bright;
    switch (colorName) {
      default:
      case Black:
        return Color.BLACK;
      case DkGrey:
        return Color.DARK_GRAY;
      case DkRed:
        hue    = (float)0;
        sat    = (float)100;
        bright = (float)66;
        break;
      case Red:
        hue    = (float)0;
        sat    = (float)100;
        bright = (float)90;
        break;
      case LtRed:
        hue    = (float)0;
        sat    = (float)60;
        bright = (float)100;
        break;
      case Orange:
        hue    = (float)20;
        sat    = (float)100;
        bright = (float)100;
        break;
      case Brown:
        hue    = (float)20;
        sat    = (float)80;
        bright = (float)66;
        break;
      case Gold:
        hue    = (float)40;
        sat    = (float)100;
        bright = (float)90;
        break;
      case Green:
        hue    = (float)128;
        sat    = (float)100;
        bright = (float)45;
        break;
      case Cyan:
        hue    = (float)190;
        sat    = (float)80;
        bright = (float)45;
        break;
      case LtBlue:
        hue    = (float)210;
        sat    = (float)100;
        bright = (float)90;
        break;
      case Blue:
        hue    = (float)240;
        sat    = (float)100;
        bright = (float)100;
        break;
      case Violet:
        hue    = (float)267;
        sat    = (float)100;
        bright = (float)100;
        break;
      case DkVio:
        hue    = (float)267;
        sat    = (float)100;
        bright = (float)66;
        break;
    }
    hue /= (float)360.0;
    sat /= (float)100.0;
    bright /= (float) 100.0;
    return Color.getHSBColor(hue, sat, bright);
  }

  /**
   * A generic function for appending formatted text to a JTextPane.
   * 
   * @param color - color of text
   * @param font  - the font selection
   * @param size  - the font point size
   * @param ftype - type of font style
   * @return the attribute set
   */
  private static AttributeSet setTextAttr(TextColor color, String font, int size, FontType ftype) {
    boolean bItalic = false;
    boolean bBold = false;
    if (ftype == FontType.Italic || ftype == FontType.BoldItalic) {
      bItalic = true;
    }
    if (ftype == FontType.Bold || ftype == FontType.BoldItalic) {
      bBold = true;
    }

    StyleContext sc = StyleContext.getDefaultStyleContext();
    AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground,
                                        generateColor(color));

    aset = sc.addAttribute(aset, StyleConstants.FontFamily, font);
    aset = sc.addAttribute(aset, StyleConstants.FontSize, size);
    aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);
    aset = sc.addAttribute(aset, StyleConstants.Italic, bItalic);
    aset = sc.addAttribute(aset, StyleConstants.Bold, bBold);
    return aset;
  }
    
  public class FontInfo {
    TextColor  color;      // the font color
    FontType   fonttype;   // the font attributes (e.g. Italics, Bold,..)
    String     font;       // the font family (e.g. Courier)
    int        size;       // the font size
        
    FontInfo (TextColor col, FontType type, int fsize, String fontname) {
      color = col;
      fonttype = type;
      font = fontname;
      size = fsize;
    }
  }
}
