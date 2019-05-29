/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import main.LauncherMain;
import gui.GuiControls;
import util.Utils;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 *
 * @author dan
 */
public class DatabaseTable {
  
  private static final String[] TABLE_COLUMNS = new String [] {
    "Run", "Count", "Time", "Method", "Offset", "Path", "Cost", "Solvable", "Type", "Solution"
  };

  private static String   tabName;
  private static boolean  tabSelected;
  private static JTable   dbTable;
  private static JScrollPane scrollPanel;
  private static JFrame   solutionPanel;
  private static JFrame   constraintPanel;
  private static GuiControls gui;
  private static Timer    databaseTimer;
  private static boolean  bSortOrder;
  private static int      colSortSelection;
  private static int      rowSelection;
  private static int      rowsDisplayed;
  private static ArrayList<DatabaseInfo> dbList = new ArrayList<>();

  private static MongoClient                 mongoClient;
  private static MongoDatabase               database;
  private static MongoCollection<Document>   collection;
  private static boolean  mongoFailure;

  
  private static class DatabaseInfo {
    // for simplicity we always use String type for each column of the JTable
    String  id;             // the database id
    String  time;           // time/date the branchpoint occurred
    String  method;         // the method for the symbolic parameter
    String  constraint;     // the constraints for the symbolic parameter
    String  ctype;          // type of constraint
    String  solution;       // list of parameters & values that would lead to a different branch
    String  offset;         // (I) the opcode offset into the method for the symbolic parameter
    String  cost;           // (I) number of instructions executed at the branch point
    String  connection;     // (I) index of the solver connection (to identify the running application)
    String  msgCount;       // (I) message count for the specified connection
    String  threadId;       // (I) thread id that was running the method
    String  calctime;       // (I) the time the solver took to solve the formula
    String  lastpath;       // (B) indication of whether the branch was taken or not
    String  solvable;       // (B) true if constraint was solvable
    
    Integer iOpOffset;      // int version of offset
    Integer iCost;          // int version of cost
    Integer iConnect;       // int version of connection
    Integer iCount;         // int version of msgCout
    Integer iThread;        // int version of threadId
    Integer iCalcTime;      // int version of calctime
    
    Boolean bPath;          // boolean version of lastpath
    Boolean bSolvable;      // boolean version of solvable
    
    ArrayList<String> solutionList; // full list of solution entries (may be multiple params)
    
    public DatabaseInfo(Document doc) {
      // extract the entries from the database document (if entry not found, result is null)
      ObjectId idn = doc.getObjectId("_id");
      time         = doc.getString ("time");      
      method       = doc.getString ("method");
      constraint   = doc.getString ("constraint");
      ctype        = doc.getString ("ctype");
      iOpOffset    = doc.getInteger("offset");
      iCost        = doc.getInteger("cost");
      iConnect     = doc.getInteger("connection");
      iCount       = doc.getInteger("index");
      iThread      = doc.getInteger("thread");
      iCalcTime    = doc.getInteger("calctime");
      bPath        = doc.getBoolean("lastpath");
      bSolvable    = doc.getBoolean("solvable");
      
      // convert them all to String for display (to make table entries simple)
      id         = idn        == null ? "" : idn.toHexString();
      time       = time       == null ? "" : time;
      method     = method     == null ? "" : method;
      constraint = constraint == null ? "" : constraint;
      ctype      = ctype      == null ? "" : ctype;
      lastpath   = bPath      == null ? "" : bPath.toString();
      solvable   = bSolvable  == null ? "" : bSolvable.toString();
      offset     = iOpOffset  == null ? "" : iOpOffset.toString();
      cost       = iCost      == null ? "" : iCost.toString();
      connection = iConnect   == null ? "" : iConnect.toString();
      msgCount   = iCount     == null ? "" : iCount.toString();
      threadId   = iThread    == null ? "" : iThread.toString();
      calctime   = iCalcTime  == null ? "" : iCalcTime.toString();
      
      // determine if we have any solutions
      this.solutionList = new ArrayList<>();
      List<Document> solutionlist = (List<Document>) doc.get("solution");
      if (solutionlist != null) {
        for (Document entry : solutionlist) {
          // String type = (String) entry.get("type");
          String paramName = (String) entry.get("name");
          String value = (String) entry.get("value");
          solutionList.add(paramName + " = " + value);
        }
      }
      // sort the list to make it easier to read
      Collections.sort(solutionList);

      // need to do this for displaying the list of solutions because I can't get multi-line
      // display of jTable to work correctly.
      if (solutionList.isEmpty()) {
        solution = "";
      } else if (solutionList.size() == 1) {
        solution = solutionList.get(0);
      } else {
        solution = solutionList.get(0) + " ...";
      }
      
      tabSelected = false;
    }
  } 
  
  public DatabaseTable (String name) {
    tabName = name;
    gui = new GuiControls();
    scrollPanel = gui.makeRawScrollTable(name);
    dbTable = gui.getTable(name);

    // we currently have no solution or constraint sub-panels yet
    solutionPanel = null;
    constraintPanel = null;
      
    // init the access to mongo
    mongoClient = MongoClients.create();
    database = mongoClient.getDatabase("mydb");
    collection = database.getCollection("dsedata");
    mongoFailure = false;
    
    // init the params
    bSortOrder = false;
    rowSelection = -1;
    colSortSelection = 0;
    rowsDisplayed = 0;
    
    dbTable.setModel(new DefaultTableModel(new Object [][]{ }, TABLE_COLUMNS) {
      Class[] types = new Class [] {
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class
      };
      boolean[] canEdit = new boolean [] {
        false, false, false,
        false, false, false,
        false, false, false,
        false
      };

      @Override
      public Class getColumnClass(int columnIndex) {
        return types [columnIndex];
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
      }
    });

    dbTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
  }

  public void activate() {
    dbTable.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        dbTableMouseClicked(evt);
      }
    });
//    dbTable.addKeyListener(new java.awt.event.KeyAdapter() {
//      @Override
//      public void keyPressed(java.awt.event.KeyEvent evt) {
//        dbTableKeyPressed(evt);
//      }
//    });
        
    // align columns in database table to center
    DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
    centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
    dbTable.setDefaultRenderer(String.class, centerRenderer);
    
    // do the same for the header columns
    TableCellRenderer headerRenderer = dbTable.getTableHeader().getDefaultRenderer();
    JLabel headerLabel = (JLabel) headerRenderer;
    headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
    // create a timer for updating the cloud job information
    databaseTimer = new Timer(4000, new DatabaseUpdateListener());
    Utils.printStatusInfo("START - Database Timer (4 sec)");
    databaseTimer.start();

    // create up & down key handlers for cloud row selection
    AbstractAction tableUpArrowAction = new UpArrowAction();
    AbstractAction tableDnArrowAction = new DnArrowAction();
    dbTable.getInputMap().put(KeyStroke.getKeyStroke("UP"), "upAction");
    dbTable.getActionMap().put( "upAction", tableUpArrowAction );
    dbTable.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "dnAction");
    dbTable.getActionMap().put( "dnAction", tableDnArrowAction );
        
    // add a mouse listener for the cloud table header selection
    JTableHeader tableHeader = dbTable.getTableHeader();
    tableHeader.addMouseListener(new HeaderMouseListener());
  }
  
  public JTable getPanel() {
    return dbTable;
  }
  
  public JScrollPane getScrollPanel() {
    return scrollPanel;
  }
  
  public void setTabSelection(String selected) {
    tabSelected = selected.equals(tabName);
  }
  
  public void exit() {
    Utils.printStatusInfo("STOP - Database Timer");
    databaseTimer.stop();
  }
  
  public void resetDatabase() {
    rowsDisplayed = 0;
  }
  
  public int getCurrentConnection() {
    // find most recent Run entries
    int currRun = 0;
    for (DatabaseInfo entry : new ArrayList<>(dbList)) {
      currRun = (entry.iConnect > currRun) ? entry.iConnect : currRun;
    }
    return currRun;
  }
  
  public void saveDatabaseToExcel(String filename) {
    Utils.printStatusMessage("Saving Database to Excel file: " + filename);
    try {
      Workbook wb = new XSSFWorkbook();
      Sheet sheet = wb.createSheet();
      TableModel model = dbTable.getModel();

      // Create a Font for styling header cells
      Font headerFont = wb.createFont();
      headerFont.setBold(true);
      headerFont.setItalic(true);
      headerFont.setFontHeightInPoints((short) 10);
      headerFont.setColor(IndexedColors.BLUE.getIndex());

      // Create a CellStyle with the font
      CellStyle headerCellStyle = wb.createCellStyle();
      headerCellStyle.setFont(headerFont);

      // write column header names (row 0)solvePC
      Row headerRow = sheet.createRow(0);
      for(int headings = 0; headings < model.getColumnCount(); headings++) {
        Cell cell = headerRow.createCell(headings);
        cell.setCellValue(model.getColumnName(headings));
        cell.setCellStyle(headerCellStyle);
      }

      // keep track of column width required
      int[] charlen = new int[model.getColumnCount()];
      for(int cols = 0; cols < model.getColumnCount(); cols++){
        charlen[cols] = model.getColumnName(cols).length() + 2; // to account for the up/dn char)
      }
      
      // now write the cell data for each row, column by column
      int xlsRow = 1; // the row in the spreadsheet output (0 was used for the header)
      Row row = sheet.createRow(xlsRow++);
      for(int rows = 0; rows < model.getRowCount(); rows++){
        int lines = 1;
        for(int cols = 0; cols < model.getColumnCount(); cols++){
          int length = 1;
          if (getColumnName(cols).equals("Solution")) {
            ArrayList<String> list = dbList.get(rows).solutionList;
            String entry = formatSolutionArrayToString(list);
            row.createCell(cols).setCellValue(entry);
            lines = list.size();
            length = getSolutionMaxWidth(list);
          } else {
            String entry = model.getValueAt(rows, cols).toString();
            row.createCell(cols).setCellValue(entry);
            length = entry.length();
          }
          if (charlen[cols] < length) {
            charlen[cols] = length;
          }
        }

        // auto-size the row height to account for multi-lines in Solutions column
        row.setHeight((short)(lines * row.getHeight())); // the height is in 1/20 of a point
        for(int cols = 0; cols < model.getColumnCount(); cols++){
          sheet.setColumnWidth(cols, charlen[cols] * 256); // the width is in 1/256 of a char
        }
        
        // Set the row to the next one in the sequence 
        row = sheet.createRow(xlsRow++); 
      }

      // write the file
      wb.write(new FileOutputStream(filename));
      wb.close();
      Utils.printStatusMessage(model.getRowCount() + " solutions saved");
    } catch (IOException ex) {
      Utils.printStatusError(ex.getMessage());
    }
  }

  private static boolean readDatabase() {
    if (mongoFailure) {
      return false;
    }
    
    boolean changed = false;
    int newEntries = 0;
    int oldSize = dbList.size();
    try {
      // read data base for solutions to specified parameter that are solvable
      FindIterable<Document> iterdocs = collection.find() //(Bson) new BasicDBObject("solvable", true))
          .sort((Bson) new BasicDBObject("_id", -1)); // sort in descending order (oldest first)

      // save list of the unique id values from the current list
      ArrayList<String> dbCopy = new ArrayList<>();
      for (int ix = 0; ix < oldSize; ix++) {
        dbCopy.add(dbList.get(ix).id);
      }
      
      // clear the current list and copy mongo docs into it
      dbList.clear();
      for (Document doc : iterdocs) {
        DatabaseInfo entry = new DatabaseInfo(doc);
        dbList.add(entry);
        if (!dbCopy.contains(entry.id)) {
          ++newEntries;
          // if new entry is solvable, log the method it was found in
          if (entry.bSolvable) {
            // convert the method name to use dot between class and method names
            String methname = entry.method;
            int offset = methname.lastIndexOf("/");
            if (offset >= 0) {
              methname = methname.substring(0, offset) + "." + methname.substring(offset + 1);
            }
            
            // if this is a normal PATH constraint solution, add it to the method info
            LauncherMain.newSolutionReceived(methname, entry.iOpOffset, entry.solution, entry.ctype, entry.bPath);
          }
        }
      }
    } catch (MongoTimeoutException ex) {
      Utils.printStatusError("Mongo Timeout - make sure mongodb is running.");
      mongoFailure = true;
      return false;
    }

    // determine if database entries have changed
    int newSize = dbList.size();
    if (newSize != oldSize || newEntries != 0) {
      Utils.printStatusInfo(newSize + " documents read from database: " + newEntries + " new, "
          + (oldSize + newEntries - newSize) + " removed");
      changed = true;
    }
    
    return changed;
  }
  
  private static String formatSolutionArrayToString(ArrayList<String> list) {
    String entry = "";
    if (!list.isEmpty()) {
      entry = list.get(0);
      for (int ix = 1; ix < list.size(); ix++) {
        entry += Utils.NEWLINE + list.get(ix);
      }
    }
    return entry;
  }
  
  private static int getSolutionMaxWidth(ArrayList<String> list) {
    int length = 1;
    if (!list.isEmpty()) {
      for (int ix = 0; ix < list.size(); ix++) {
        if (length < list.get(ix).length()) {
          length = list.get(ix).length();
        }
      }
    }
    return length;
  }
  
  private String getColumnName(int col) {
    if (col < 0 || col >= TABLE_COLUMNS.length) {
      col = 0;
    }
    return TABLE_COLUMNS[col];
  }
  
  private String getColumnParam(int col, DatabaseInfo dbinfo) {
    switch(getColumnName(col)) {
      default: // fall through...
      case "Run":
        return dbinfo.connection;
      case "Count":
        return dbinfo.msgCount;
      case "Time":
        return dbinfo.time;
      case "Method":
        return dbinfo.method;
      case "Offset":
        return dbinfo.offset;
      case "Path":
        return dbinfo.lastpath;
      case "Cost":
        return dbinfo.cost;
      case "Solvable":
        return dbinfo.solvable;
      case "Type":
        return dbinfo.ctype;
      case "Solution":
        return dbinfo.solution;
    }
  }
  
  // NOTE: the order of the entries should match the order of the columns
  private Object[] makeRow(DatabaseInfo tableEntry) {
    return new Object[]{
        tableEntry.connection,
        tableEntry.msgCount,
        tableEntry.time,
        tableEntry.method,
        tableEntry.offset,
        tableEntry.lastpath,
        tableEntry.cost,
        tableEntry.solvable,
        tableEntry.ctype,
        tableEntry.solution,
    };
  }
  
  private static int getColumnIndex(String colname) {
    for (int col = 0; col < TABLE_COLUMNS.length; col++) {
      String entry = TABLE_COLUMNS[col];
      if(entry != null && entry.equals(colname)) {
        return col;
      }
    }
    return 0;
  }
  
  /**
   * This is used to resize the colums of a table to accomodate some columns
   * requiring more width than others.
   * 
   * @param table - the table to resize
   */
  private void resizeColumnWidth (JTable table) {
    final TableColumnModel columnModel = table.getColumnModel();
    for (int column = 0; column < table.getColumnCount(); column++) {
      int width = 15; // Min width
      for (int row = 0; row < table.getRowCount(); row++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component comp = table.prepareRenderer(renderer, row, column);
        width = Math.max(comp.getPreferredSize().width +1 , width);
      }
      if(width > 300) // Max width
        width=300;
      columnModel.getColumn(column).setPreferredWidth(width);
    }
  }

  /**
   * do an integer compare of the 2 objects
   * 
   * @param val1 - the value of the 1st entry
   * @param val2 - the value of the 2nd entry
   * @return 1 if val1 > val2, -1 if val1 < val2, 0 if val1 == val2
   * 
   * @throws NumberFormatException 
   */
  private int integerCompare(String val1, String val2) throws NumberFormatException {
    Integer int1, int2;
    int1 = Integer.parseInt(val1);
    int2 = Integer.parseInt(val2);
    return int1 > int2 ? 1 : int1 < int2 ? -1 : 0;
  }
  
  /**
   * do an integer compare of the 2 objects
   * 
   * @param order - false if compare val1 to val2, true if compare val2 to val1
   * @param val1 - the value of the 1st entry
   * @param val2 - the value of the 2nd entry
   * @return 1 if 1st entry > 2nd entry, -1 if 1st entry < 2nd entry, 0 if they are equal
   * 
   * @throws NumberFormatException 
   */
  private int integerCompare(boolean order, String val1, String val2) throws NumberFormatException {
    Integer int1, int2;
    if (!order) {
      int1 = Integer.parseInt(val1);
      int2 = Integer.parseInt(val2);
    } else {
      int1 = Integer.parseInt(val2);
      int2 = Integer.parseInt(val1);
    }
    return int1 > int2 ? 1 : int1 < int2 ? -1 : 0;
  }
  
  /**
   * This performs a sort on the selected table column and updates the table display.
   *
   * @param columnSel - specifies the column on which to sort
   * @param descending - specifies either ascending (false) or descending (true) order.
   */
  private synchronized void tableSortAndDisplay(int columnSel, boolean descending) {
    // sort the table entries
    Collections.sort(dbList, new Comparator<DatabaseInfo>() {
      @Override
      public int compare(DatabaseInfo job1, DatabaseInfo job2) {
        try {
          int compare;
          switch (getColumnName(columnSel)) {
            case "Count":
              // sort as integer but use Run column to sort first
              // (that is, data will be sorted in groups of Run value first, then by Count value for each Run)
              compare = integerCompare(job1.connection, job2.connection);
              if (compare == 0) {
                compare = integerCompare(descending, job1.msgCount, job2.msgCount);
              }
              return compare;

            case "Run":
              // sort as integer but use Count column to sort after Run has been sorted
              // (that is, always sort Count from low to high, but allow selection of Row sort order)
              compare = integerCompare(descending, job1.connection, job2.connection);
              if (compare == 0) {
                compare = integerCompare(job1.msgCount, job2.msgCount);
              }
              return compare;

            case "Offset":
            case "Cost":
              // sort as integers
              return integerCompare(descending, job1.msgCount, job2.msgCount);
            default:
              break;
          }
        } catch (NumberFormatException ex) {
          // fall through to use the string comparison if something went wrong.
        }

        // sort as Strings
        String object1 = getColumnParam(columnSel, job1);
        String object2 = getColumnParam(columnSel, job2);
        return descending ? object2.compareTo(object1) : object1.compareTo(object2);
//        if (!order) {
//          return object1.compareTo(object2);
//        }
//        return object2.compareTo(object1);
      }
    });

    // clear out the table entries
    DefaultTableModel model = (DefaultTableModel) dbTable.getModel();
    model.setRowCount(0); // this clears all the entries from the table

    // reset the names of the columns and modify the currently selected one
    String[] columnNames = new String[TABLE_COLUMNS.length];
    System.arraycopy(TABLE_COLUMNS, 0, columnNames, 0, TABLE_COLUMNS.length);
    for (int ix = 0; ix < TABLE_COLUMNS.length; ix++) {
      if (ix == columnSel) {
        if (descending) {
          columnNames[ix] += " " + "\u2193".toCharArray()[0]; // DOWN arrow
        }
        else {
          columnNames[ix] += " " + "\u2191".toCharArray()[0]; // UP arrow
        }
      }
    }
    model.setColumnIdentifiers(columnNames);
        
    // now copy the entries to the displayed table
    for (int ix = 0; ix < dbList.size(); ix++) {
      DatabaseInfo tableEntry = dbList.get(ix);
      model.addRow(makeRow(tableEntry));
    }

    // update the number of rows displayed
    rowsDisplayed = dbList.size();
    
    // auto-resize column width
    resizeColumnWidth(dbTable);
  }
    
  /**
   * action event when the mouse is clicked in the table.
   */
  private void dbTableMouseClicked(java.awt.event.MouseEvent evt) {                                            
    int row = dbTable.rowAtPoint(evt.getPoint());
    int col = dbTable.columnAtPoint(evt.getPoint());
    String colname = getColumnName(col);
    DatabaseInfo info = dbList.get(row);

    switch(colname) {
      case "Method":
      case "Offset":
      case "Path":
        String meth   = (String)dbTable.getValueAt(row, getColumnIndex("Method"));
        String line   = (String)dbTable.getValueAt(row, getColumnIndex("Offset"));
        String branch = (String)dbTable.getValueAt(row, getColumnIndex("Path"));

        // need to seperate the name into a class and a method
        Utils.ClassMethodName classmeth = new Utils.ClassMethodName(meth);
        meth = classmeth.methName + classmeth.signature;
        String cls = classmeth.className;

        int ret = LauncherMain.runBytecodeViewer(cls, meth, false);
        if (ret == 0) {
          LauncherMain.setBytecodeSelections(cls, meth);
          LauncherMain.highlightBranch(Integer.parseInt(line), branch.equals("true"));
        }
        break;
      case "Solution":
        int count = info.solutionList.size();
        if (count > 0) {
          String contents = info.solutionList.get(0);
          for (int ix = 1; ix < count; ix++) {
            contents += Utils.NEWLINE + info.solutionList.get(ix);
          }
          solutionPanel = GuiControls.makeFrameWithText(solutionPanel, "Solution for Run " +
              info.iConnect + ", Count " + info.iCount, contents, 400, 300);

          // inform Launcher recorder of solution selection
          LauncherMain.checkSelectedSolution(contents, info.ctype);
        }
        break;
      default:
        // all other columns...
        // pop up a panel that contains the full context string
        constraintPanel = GuiControls.makeFrameWithText(constraintPanel, "Constraint for Run " +
            info.iConnect + ", Count " + info.iCount, info.constraint, 500, 300);
        break;
    }
  }                                           

  /**
   * action event when a key is pressed in the table.
   */
  private void dbTableKeyPressed(java.awt.event.KeyEvent evt) {                                          
    switch (evt.getKeyCode()) {
      case KeyEvent.VK_ENTER: // ENTER key
        break;
      default:
        break;
    }
  }                                         

  /**
   * handles the updating of the table from the database.
   * This is run from a timer to keep the table updated.
   */
  private class DatabaseUpdateListener implements ActionListener{

  @Override
    public void actionPerformed(ActionEvent e) {
      // request the database list and save as list entries
      readDatabase();

      // sort the table entries based on current selections
      if (dbList.size() != rowsDisplayed) {
        tableSortAndDisplay(colSortSelection, bSortOrder);
      }

      // count the solved equations
      int solved = 0;
      for (int ix = 0; ix < dbList.size(); ix++) {
        if (!dbList.get(ix).solution.isEmpty()) {
          ++solved;
        }
      }
      
      // update front panel counter
      LauncherMain.setSolutionsReceived(dbList.size(), solved);
          
      // re-mark the selected row (if any)
//      if (rowSelection >= 0) {
//        dbTable.setRowSelectionInterval(rowSelection, rowSelection);
//      }
    }
  }    

  /**
   * handles cursoring with the UP arrow key when a table row is selected..
   */
  private class UpArrowAction extends AbstractAction {

    @Override
    public void actionPerformed (ActionEvent evt) {
      //Utils.printStatusInfo("DatabaseTable.UpArrowAction: old row = " + rowSelection);
      JTable table = (JTable) evt.getSource();

      // if selection is not at the min, decrement the row selection
      if (rowSelection > 0) {
        --rowSelection;

        // highlight the new selection
        table.setRowSelectionInterval(rowSelection, rowSelection);

        // now scroll if necessary to make selection visible
        Rectangle rect = new Rectangle(table.getCellRect(rowSelection, 0, true));
        table.scrollRectToVisible(rect);
      }
    }
  }
    
  /**
   * handles cursoring with the DOWN arrow key when a table row is selected..
   */
  private class DnArrowAction extends AbstractAction {

    @Override
    public void actionPerformed (ActionEvent evt) {
      //Utils.printStatusError("DatabaseTable.DnArrowAction: old row = " + rowSelectionrowSelection);
      JTable table = (JTable) evt.getSource();
      int maxrow = table.getRowCount();

      // if selection is valid & not at the max, increment the row selection
      if (rowSelection >= 0 && rowSelection < maxrow - 1) {
        ++rowSelection;

        // highlight the new selection
        table.setRowSelectionInterval(rowSelection, rowSelection);

        // now scroll if necessary to make selection visible
        Rectangle rect = new Rectangle(table.getCellRect(rowSelection, 0, true));
        table.scrollRectToVisible(rect);
      }
    }
  }

  /**
   * handles sorting of the table based on the header column clicked.
   * alternate clicks on same column reverse the sort order.
   */
  private class HeaderMouseListener extends MouseAdapter {

    @Override
    public void mouseClicked (MouseEvent evt) {
      // get the selected header column
      int newSelection = dbTable.columnAtPoint(evt.getPoint());
      if (newSelection >= 0) {
        // invert the order selection if the same column is specified
        if (newSelection == colSortSelection) {
          bSortOrder = !bSortOrder;
        }
                    
        colSortSelection = newSelection;
        //Utils.printStatusInfo("HeaderMouseListener: (" + evt.getX() + "," + evt.getY() + ") -> col "
        //+ newSelection + " = " + getColumnName(newSelection));

        // sort the table entries based on current selections
        tableSortAndDisplay(colSortSelection, bSortOrder);
      }
    }
  }
    
}
