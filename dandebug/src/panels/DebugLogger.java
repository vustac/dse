/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import callgraph.CallGraph;
import callgraph.MethodInfo;
import logging.FontInfo;
import logging.Logger;
import util.Utils;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

/**
 *
 * @author dan
 */
public class DebugLogger {

  // types of messages
  private enum MsgType { TSTAMP, NORMAL, INFO, WARN, ERROR, DUMP, START, ENTRY, AGENT, OBJECT, ARRAY,
        CALL, RETURN, UNINST, STATS, STACK, STACKS, STACKI, LOCAL, LOCALS, SOLVE, BRANCH, CONSTR }

  private enum ScrollPosition { START, END }
  
  private static JTextPane       panel;
  private static JScrollPane     scrollPanel;
  private static Logger          logger;
  private static String          tabName;
  private static String          lastMessage;
  private static int             curLine;
  private static int             endLine;
  private static int             linesRead;
  private static int             threadCount;
  private static int             errorCount;
  private static boolean         paused;
  private static boolean         tabSelected;
  private static boolean         lineError;
  private static ScrollPosition  scrollPosition;
  private static final ArrayList<Integer> threadList = new ArrayList<>();
  private static final HashMap<String, FontInfo> fontmap = new HashMap<>();
  private static LinkedBlockingQueue<MessageInfo> pauseQueue;
  
  public DebugLogger(String name) {
    curLine = 0;
    endLine = 0;
    linesRead = 0;
    errorCount = 0;
    threadCount = 0;
    paused = false;
    tabSelected = false;
    lineError = false;
    lastMessage = "";
    tabName = name;
    scrollPosition = ScrollPosition.START;

    pauseQueue = new LinkedBlockingQueue<>();
    
    String fonttype = "Courier";
    FontInfo.setTypeColor (fontmap, MsgType.TSTAMP.toString(), FontInfo.TextColor.Black,  FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.ERROR.toString(),  FontInfo.TextColor.Red,    FontInfo.FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.WARN.toString(),   FontInfo.TextColor.Orange, FontInfo.FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.INFO.toString(),   FontInfo.TextColor.Black,  FontInfo.FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.NORMAL.toString(), FontInfo.TextColor.Black,  FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.DUMP.toString(),   FontInfo.TextColor.Orange, FontInfo.FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.START.toString(),  FontInfo.TextColor.Black,  FontInfo.FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.ENTRY.toString(),  FontInfo.TextColor.Brown,  FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.AGENT.toString(),  FontInfo.TextColor.Violet, FontInfo.FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.OBJECT.toString(), FontInfo.TextColor.DkVio,  FontInfo.FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.ARRAY.toString(),  FontInfo.TextColor.DkVio,  FontInfo.FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.CALL.toString(),   FontInfo.TextColor.Gold,   FontInfo.FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.RETURN.toString(), FontInfo.TextColor.Gold,   FontInfo.FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.UNINST.toString(), FontInfo.TextColor.Gold,   FontInfo.FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STATS.toString(),  FontInfo.TextColor.Gold,   FontInfo.FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STACK.toString(),  FontInfo.TextColor.Blue,   FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STACKS.toString(), FontInfo.TextColor.Blue,   FontInfo.FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.STACKI.toString(), FontInfo.TextColor.Blue,   FontInfo.FontType.Bold,   14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.LOCAL.toString(),  FontInfo.TextColor.Green,  FontInfo.FontType.Normal, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.LOCALS.toString(), FontInfo.TextColor.Green,  FontInfo.FontType.Italic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.BRANCH.toString(), FontInfo.TextColor.LtBlue, FontInfo.FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.CONSTR.toString(), FontInfo.TextColor.DkVio,  FontInfo.FontType.BoldItalic, 14, fonttype);
    FontInfo.setTypeColor (fontmap, MsgType.SOLVE.toString(),  FontInfo.TextColor.DkVio,  FontInfo.FontType.Bold,   14, fonttype);

    // create the text panel and assign it to the logger
    logger = new Logger(name, Logger.PanelType.TEXTPANE, true, fontmap);
    panel = (JTextPane) logger.getTextPanel();
    scrollPanel = logger.getScrollPanel();
  }
  
  public void activate() {
    if (scrollPanel != null) {
      // add a listener to the scroll bar
      scrollPanel.getVerticalScrollBar().addAdjustmentListener(new DebugScrollListener());
    }

    // add key listener for the debug viewer
    panel.addKeyListener(new DebugKeyListener());
  }
  
  public JTextPane getTextPanel() {
    return panel;
  }
  
  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  private void printError(String message) {
    System.err.println("ERROR: " + message);
  }
  
  public int getThreadCount() {
    return threadCount;
  }
  
  public int getLinesRead() {
    return linesRead;
  }
  
  public int getErrorCount() {
    return errorCount;
  }
  
  public void setMaxBufferSize(int bufSize) {
    logger.setMaxBufferSize(bufSize);
  }
  
  public void clear() {
    logger.clear();
    curLine = 0;
    endLine = 0;
    linesRead = 0;
    errorCount = 0;
    threadCount = 0;
    paused = false;
    lineError = false;
    lastMessage = "";
    pauseQueue.clear();
  }

  /**
   * outputs the various types of messages to the status display.
   * all messages will guarantee the previous line was terminated with a newline,
   * and will preceed the message with a timestamp value and terminate with a newline.
   * 
   * @param linenum  - the line number
   * @param elapsed  - the elapsed time
   * @param threadid - the thread id
   * @param typestr  - the type of message to display (all caps)
   * @param content  - the message content
   */
  private static void printDebug(int linenum, String elapsed, String threadid, String typestr, String content) {
    if (linenum >= 0 && elapsed != null && typestr != null && content != null && !content.isEmpty()) {
      // make sure the linenum is 8-digits in length and the type is 6-chars in length
      String linestr = "00000000" + linenum;
      linestr = linestr.substring(linestr.length() - 8);
      typestr = (typestr + "      ").substring(0, 6);
      if (!threadid.isEmpty()) {
        threadid = "<" + threadid + ">";
      }
      
      // print message (seperate into multiple lines if ASCII newlines are contained in it)
      if (!content.contains(Utils.NEWLINE)) {
        logger.printField("INFO", linestr + "  ");
        logger.printField("INFO", elapsed + " ");
        logger.printField("INFO", threadid + " ");
        logger.printField(typestr, typestr + ": " + content + Utils.NEWLINE);
      }
      else {
        // seperate into lines and print each independantly
        String[] msgarray = content.split(Utils.NEWLINE);
        for (String msg : msgarray) {
          logger.printField("INFO", linestr + "  ");
          logger.printField("INFO", elapsed + " ");
          logger.printField("INFO", threadid + " ");
          logger.printField(typestr, typestr + ": " + msg + Utils.NEWLINE);
        }
      }
    }
  }
  
  private static void printDebug(String content) {
    logger.printLine(content);
  }
  
  private void addToQueue(MessageInfo msgInfo) {
    try {
      // add message to queue. if limit exceeded, remove oldest entry
      pauseQueue.put(msgInfo);
      if (pauseQueue.size() > 50) {
        pauseQueue.take();
      }
    } catch (InterruptedException ex) { /* ignore */ }
  }
  
  private void processFromQueue() {
    while (!pauseQueue.isEmpty()) {
      try {
        // read next message from input buffer
        MessageInfo msgInfo = pauseQueue.take();
        if (msgInfo.valid) {
          printDebug(msgInfo.linenum, msgInfo.timestr, msgInfo.threadid, msgInfo.type, msgInfo.content);
        }
        curLine++;

      } catch (InterruptedException ex) { /* ignore */ }
    }
  }
  public String processMessage(String message, CallGraph callGraph) {
    if (message == null) {
      return null;
    }
    
    if (message.startsWith("# Logfile started: ") || (message.startsWith("----------"))) {
      printDebug(message);
      return null;
    }

    // seperate message into the message type and the message content
    MessageInfo msgInfo = new MessageInfo(message);
    if (!msgInfo.valid) {
      // NOTE: because a separate thread is feeding the BufferedReader we are reading from, the line
      // read is not guaranteed to be a full line. If it is not and the previous line was valid,
      // save it in case the next read returns the rest of the line. If the previous line was also
      // invalid, concatenate this line to the previous one and see if we have a valid line now.
      // If not, let's assume we can't correct it.
      System.err.println("ERROR (" + msgInfo.error + "): processMessage: invalid input: " + message);
      if (!lineError) {
        // no previous line error - save current data for concatenation to next line read
        lastMessage = message;
        lineError = true;
        return null;
      } else {
        // prev line error - attempt to piece current line with prev to see if we have complete line
        msgInfo = new MessageInfo(lastMessage + message);
        if (!msgInfo.valid) {
          // nope - just indicate we lost the line
          lineError = true;
          printError("invalid input syntax: " + lastMessage + message);
          return null;
        }
        System.err.println("FIXED !");
      }
    }

    // else, message was valid
    if (paused) {
      addToQueue(msgInfo);
    } else {
      printDebug(msgInfo.linenum, msgInfo.timestr, msgInfo.threadid, msgInfo.type, msgInfo.content);
      curLine++;
    }
    linesRead++;
    lineError = false;
    lastMessage = "";
          
    // check if we have thread id value embedded in message contents
    if (!threadList.contains(msgInfo.tid)) {
      threadList.add(msgInfo.tid);
      threadCount = threadList.size();
    }

    // get the current method that is being executed
    MethodInfo mthNode = callGraph.getLastMethod(msgInfo.tid);
    
    // extract call processing info and send to CallGraph
    String content = msgInfo.content.trim();
    switch (msgInfo.type.trim()) {
      case "CALL":
        callGraph.methodEnter(msgInfo.tid, msgInfo.tstamp, msgInfo.instrCount, msgInfo.method, msgInfo.linenum);
        return msgInfo.method;
      case "RETURN":
        callGraph.methodExit(msgInfo.tid, msgInfo.tstamp, content);
        break;
      case "WARN":
        if (mthNode != null && msgInfo.exceptLine >= 0) {
          // find mthNode for this method
          mthNode = CallGraph.getMethodInfo(msgInfo.method);
          if (mthNode != null) {
            mthNode.setExecption(msgInfo.tid, msgInfo.exceptLine, msgInfo.exception);
          }
        }
        break;
      case "ERROR":
        // increment the error count
        errorCount++;
        if (mthNode != null) {
          mthNode.setError(msgInfo.tid, msgInfo.linenum, content);
        }
        break;
      default:
        break;
    }
    
    return null;
  }
  
  public class MessageInfo {
    public boolean valid = false;
    public int     error = 0;
    public int     linenum = 0;
    public long    tstamp = 0L;
    public int     tid = -1;
    public int     instrCount = -1;
    public int     exceptLine = -1;

    public String lineval = "";
    public String timestr = "";
    public String threadid = "";
    public String type = "";
    public String content = "";
    public String method = "";
    public String exception = "";
    
    public MessageInfo(String message) {
      if (message == null || message.length() < 30) {
        error = 1; // message length incorrect
        return;
      }
      String[] array = message.split("\\s+", 3);
      if (array.length < 3) {
        error = 2; // message doesn't contain minimal required number of words
        return;
      }

      lineval = array[0].trim();
      timestr = array[1].trim();
      message = array[2];
      // check if we have thread id value embedded in message contents
      if (message.startsWith("<") && message.contains(">")) {
        array = message.split("\\s+", 2);
        threadid = array[0];
        message = array[1];
        threadid = threadid.substring(1, threadid.indexOf(">"));
      }

      // make sure we have a valid time stamp is valid: [00:00.000]
      if (timestr.length() < 11 || !timestr.startsWith("[") || !timestr.endsWith("]")) {
        error = 3; // invalid timestamp format
        return;
      }
      String timeMin = timestr.substring(1, 3);
      String timeSec = timestr.substring(4, 6);
      String timeMs  = timestr.substring(7, 10);

      // verify timestamp entry
      try {
        tstamp = ((Integer.parseInt(timeMin) * 60) + Integer.parseInt(timeSec)) * 1000;
        tstamp += Integer.parseInt(timeMs);
        if (!threadid.isEmpty()) {
          tid = Integer.parseInt(threadid);
        }
      } catch (NumberFormatException ex) {
        error = 3; // invalid timestamp entry
        return;
      }

      // verify line number entry
      if (lineval.length() != 8) {
        error = 4; // invalid line number entry
        return;
      }
      try {
        linenum = Integer.parseInt(lineval);
      } catch (NumberFormatException ex) {
        error = 4; // invalid line number entry
        return;
      }

      // now extract the 6-char message type (may contain space)
      if (message.length() < 8 || message.charAt(6) != ':') {
        error = 5; // invalid message type entry
        return;
      }
      type = message.substring(0, 6).toUpperCase();
      content = message.substring(8);        // (skip past the ': ' chars to the message content)
      
      // verify the type selections
      if (!fontmap.containsKey(type.trim())) {
        error = 5; // invalid message type entry
        return;
      }
      
      // verify the message content is valid
      if (content.isEmpty()) {
        error = 6; // invalid (empty) message entry
        return;
      }

      // special check for certain command types
      switch (type.trim()) {
        case "CALL":
          String[] splited = content.trim().split(" ");
          if (splited.length < 2) {
            error = 6; // invalid message entry (must have instruction count and method name)
            return;
          }

          String icount = splited[0].trim();
          method = splited[1].trim();
          try {
            instrCount = Integer.parseUnsignedInt(icount);
          } catch (NumberFormatException ex) {
            error = 7; // invalid instruction count value
            return;
          }
        
          if (method.isEmpty() || !method.contains(".") || !method.contains("(") || !method.contains(")")) {
            error = 7; // invalid method name
            return;
          }
          break;
        case "WARN":
          if (content.startsWith("Exception: ")) {
            exception = content.substring("Exception: ".length());
            // find the name of the exception name and the method and line it occurred in
            int start = exception.indexOf("(");
            int end = exception.lastIndexOf(")"); // because the method will also contain a () pair
            if (start >= 0 && end > start) {
              method = exception.substring(start + 1, end);
              exception = exception.substring(end + 1).trim();
              try {
                start = method.indexOf(",");
                exceptLine = Integer.parseUnsignedInt(method.substring(0, start));
                method = method.substring(start + 1).trim();
              } catch (NumberFormatException ex) { // ignore error
                break;
              }
            }
          }
          break;
      }
      
      valid = true;
    }
  }
  
  
  private class DebugScrollListener implements AdjustmentListener {

    @Override
    public void adjustmentValueChanged(AdjustmentEvent evt) {
      // ignore if this tab is not active
      if (!tabSelected) {
        return;
      }
      
      if (evt.getValueIsAdjusting()) {
        // user is dragging scroll bar make sure we pause
        paused = true;
        // we prpbably also need to determine if he scrolls to begining or end, but I'm guessing
        // we will get another notification after he stops dragging the bar that can handle this.
        return;
      }
      
      JScrollBar scrollBar = scrollPanel.getVerticalScrollBar();
      // check if end or start of buffer reached
      if ((scrollBar.getValue() + scrollBar.getVisibleAmount() >= scrollBar.getMaximum()) &&
                  scrollPosition != ScrollPosition.END) {
        //System.out.println("* Reached end of buffer - unpause *");
        scrollPosition = ScrollPosition.END;
        paused = false;
      } else if (scrollBar.getValue() == 0 && scrollPosition != ScrollPosition.START) {
        scrollPosition = ScrollPosition.START;
        //if (logger.isBufferTruncated()) {
        //  System.out.println("* Reached start of data - nothing to do *");
        //} else {
        //  System.out.println("* Reached start of buffer - need to pull from file *");
        //}
      }
    }
    
  }

  private class DebugKeyListener implements KeyListener {

    @Override
    public void keyPressed(KeyEvent ke) {
      // when the key is initially pressed
      //System.out.println("DebugKeyListener: keyPressed: " + ke.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent ke) {
      // follows keyPressed and preceeds keyReleased when entered key is character type
      //System.out.println("DebugKeyListener: keyTyped: " + ke.getKeyCode() + " = '" + ke.getKeyChar() + "'");
    }

    @Override
    public void keyReleased(KeyEvent ke) {
      if (tabSelected) {
        // when the key has been released
        //System.out.println("DebugKeyListener: keyReleased: " + ke.getKeyCode());
        //int curpos = panel.getCaretPosition();
        switch (ke.getKeyCode()) {
          case KeyEvent.VK_UP:
            if (!paused) {
              endLine = curLine;
            }
            paused = true;
            curLine = curLine - 1;
            if (curLine < 0) {
              curLine = 0;
            }
            break;
          case KeyEvent.VK_DOWN:
            curLine += 1;
            if (curLine >= endLine) {
              curLine = endLine;
              processFromQueue();
              paused = false;
            }
            break;
          case KeyEvent.VK_PAGE_UP:
            if (!paused) {
              endLine = curLine;
            }
            paused = true;
            curLine = curLine - 20;
            if (curLine < 0) {
              curLine = 0;
            }
            break;
          case KeyEvent.VK_PAGE_DOWN:
            curLine += 20;
            if (curLine >= endLine) {
              curLine = endLine;
              processFromQueue();
              paused = false;
            }
            break;
          default:
            break;
        }
      }
    }
  }

}
