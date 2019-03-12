/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package logging;

import gui.GuiControls;
import util.Utils;

import java.awt.Component;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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
public class Logger {
  
  public static enum PanelType { TEXTPANE, TEXTAREA }
  
  private final static String MAX_PADDING = "                    ";
  
  // the default point size and font types to use
  private static final int    DEFAULT_POINT = 14;
  private static final String DEFAULT_FONT = "Courier";

  // this sets the limit for the amount of text displayed in the logger display to prevent
  // memory overruns. 'maxBufferSize' defines the buffer size of when to reduce it and it will
  // eliminate the oldest 25% of text plus any additional until a NEWLINE char is reached.
  private static int      maxBufferSize = 200000;
  private static String   pnlname;
  private PanelType       pnltype;
  private boolean         scroll;
  private boolean         bufferTruncated;
  private JTextPane       textPane = null;
  private JTextArea       textArea = null;
  private JScrollPane     scrollPanel = null;
  private GuiControls     gui;
  private final HashMap<String, FontInfo> messageTypeTbl = new HashMap<>();

  public Logger (String name, PanelType type, boolean scrollable, HashMap<String, FontInfo> map) {
    pnlname = name;
    pnltype = type;
    scroll = scrollable;
    gui = new GuiControls();
    bufferTruncated = false;
    
    switch (type) {
      case TEXTPANE:
        if (scrollable) {
          scrollPanel= gui.makeRawScrollTextPane(name);
          textPane = gui.getTextPane(name);
        } else {
          scrollPanel = null;
          textPane = gui.makeRawTextPane(name, "");
        }
        break;
      case TEXTAREA:
        if (scrollable) {
          scrollPanel= gui.makeRawScrollTextArea(name);
          textArea = gui.getTextArea(name);
        } else {
          scrollPanel = null;
          textArea = gui.makeRawTextArea(name, "");
        }
        textArea.setWrapStyleWord(true);
        textArea.setAutoscrolls(true);
        break;
      default:
        System.err.println("ERROR: Invalid component type for Logger!");
        System.exit(1);
    }

    // copy the font mapping info over (use deep-copy loop instead of shallow-copy putAll)
    if (map != null) {
      for (Map.Entry<String, FontInfo> entry : map.entrySet()) {
        messageTypeTbl.put(entry.getKey(), entry.getValue());
      }
    }
  }

  public final String getName() {
    return pnlname;
  }

  public Component getTextPanel() {
    return (pnltype == PanelType.TEXTPANE) ? textPane : textArea;
  }
  
  public int getTextLength() {
    return (pnltype == PanelType.TEXTPANE) ? textPane.getText().length() : textArea.getText().length();
  }
  
  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void setColorMapping(HashMap<String, FontInfo> map) {
    // copy the font mapping info over (use deep-copy loop instead of shallow-copy putAll)
    if (map != null) {
      for (Map.Entry<String, FontInfo> entry : map.entrySet()) {
        messageTypeTbl.put(entry.getKey(), entry.getValue());
      }
    }
  }
  
  public void setMaxBufferSize(int bufSize) {
    // limit the min size to 100k, which is appx 1000 lines at 100 chars/line
    if (bufSize < 100000) {
      bufSize = 100000;
    }

    maxBufferSize = bufSize;
  }
  
  public boolean isBufferTruncated() {
    return bufferTruncated;
  }
  
  /**
   * clears the display.
   */
  public final void clear() {
    if (textPane != null) {
      textPane.setText("");
    } else {
      textArea.setText("");
    }
  }

  /**
   * updates the display immediately
   */
  public final void updateDisplay () {
    if (textPane != null) {
      Graphics graphics = textPane.getGraphics();
      if (graphics != null) {
        textPane.update(graphics);
      }
    }
  }

  /**
   * adds ASCII space padding to right of string up to the specified length
   * 
   * @param content  - the message content
   */
  private String padToRight(String content, int length) {
    return (content + MAX_PADDING).substring(0, length);
  }
  
  /**
   * adds ASCII space padding to left of string up to the specified length
   * 
   * @param content  - the message content
   */
  private String padToLeft(String content, int length) {
    return (MAX_PADDING + content).substring(MAX_PADDING.length() + content.length() - length);
  }
  
  public final void printLine(String type, String message) {
    printField(type, message + Utils.NEWLINE);
  }
  
  public final void printLine(String message) {
    printField(null, message + Utils.NEWLINE);
  }
  
  public final void printFieldAlignLeft(String type, String message, int fieldlen) {
    printField(type, padToRight(message, fieldlen));
  }
  
  public final void printFieldAlignRight(String type, String message, int fieldlen) {
    printField(type, padToLeft(message, fieldlen));
  }
  
  /**
   * displays a message in the debug window (no termination).
   * 
   * @param type  - the type of message to display
   * @param message - message contents to display
   */
  public final void printField(String type, String message) {
    if (message != null && !message.isEmpty()) {
      // set default values (if type was not found)
      FontInfo.TextColor color = FontInfo.TextColor.Black;
      FontInfo.FontType ftype = FontInfo.FontType.Normal;
      int size = DEFAULT_POINT;
      String font = DEFAULT_FONT;

      if (type == null) {
        type = "";
      } else {
        type = type.trim();
      }
      
      // get the color and font for the specified type
      if (!type.isEmpty() && messageTypeTbl.containsKey(type)) {
        FontInfo fontinfo = messageTypeTbl.get(type);
        if (fontinfo != null) {
          color = fontinfo.color;
          ftype = fontinfo.fonttype;
          font  = fontinfo.font;
          size  = fontinfo.size;
        }
      }

      appendToPane(message, color, font, size, ftype);
    }
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
  private void appendToPane(String msg, FontInfo.TextColor color, String font, int size,
                                   FontInfo.FontType ftype) {
    AttributeSet aset = setTextAttr(color, font, size, ftype);
    if (textPane != null) {
      int len = textPane.getDocument().getLength();

      // trim off earlier data to reduce memory usage if we exceed our bounds
      if (len > maxBufferSize) {
        try {
          int oldlen = len;
          int start = maxBufferSize / 4; // reduce size by 25%
          String text = textPane.getDocument().getText(start, 500);
          int offset = text.indexOf(Utils.NEWLINE);
          if (offset >= 0) {
            start += offset + 1;
          }
          textPane.getDocument().remove(0, start);
          len = textPane.getDocument().getLength();
//          System.out.println("Reduced text from " + oldlen + " to " + len);
          bufferTruncated = true;
        } catch (BadLocationException ex) {
          System.out.println(ex.getMessage());
        }
      }

      textPane.setCaretPosition(len);
      textPane.setCharacterAttributes(aset, false);
      textPane.replaceSelection(msg);
    } else {
      String current = textArea.getText();
      if (!current.endsWith(Utils.NEWLINE)) {
        current += Utils.NEWLINE;
      }
      textArea.setText(current + msg);
    }
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
  private AttributeSet setTextAttr(FontInfo.TextColor color, String font, int size, FontInfo.FontType ftype) {
    boolean bItalic = false;
    boolean bBold = false;
    if (ftype == FontInfo.FontType.Italic || ftype == FontInfo.FontType.BoldItalic) {
      bItalic = true;
    }
    if (ftype == FontInfo.FontType.Bold || ftype == FontInfo.FontType.BoldItalic) {
      bBold = true;
    }

    StyleContext sc = StyleContext.getDefaultStyleContext();
    AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground,
                                        FontInfo.getFontColor(color));

    aset = sc.addAttribute(aset, StyleConstants.FontFamily, font);
    aset = sc.addAttribute(aset, StyleConstants.FontSize, size);
    aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);
    aset = sc.addAttribute(aset, StyleConstants.Italic, bItalic);
    aset = sc.addAttribute(aset, StyleConstants.Bold, bBold);
    return aset;
  }
    
}
