/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import main.LauncherMain;
import util.Utils;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author dan
 */
public class ParamTable {
  
  private static final String[] TABLE_COLUMNS = new String [] {
    "Name", "Type", "Slot", "Start", "End"
  };

  private static boolean  bSortOrder;
  private static int      colSortSelection;
  private static int      rowSelection;
  private static JTable   table;
  private static final ArrayList<TableListInfo> paramList = new ArrayList<>();
  private static String   methodname;

  // same as TableListInfo, but for exporting values publicly
  public static class LocalParamInfo {
    String  name;
    String  type;
    int     slot;
    int     start;
    int     end;
  }
    
  private static class TableListInfo {
    String  name;     // the moniker to call the parameter by (unique entry)
    String  type;     // data type of the parameter
    String  slot;     // slot within the method for the parameter
    String  start;    // byte offset in method that specifies the starting range of the parameter
    String  end;      // byte offset in method that specifies the ending   range of the parameter
    
    public TableListInfo(String id, String typ, String slt, String strt, String len) {
      name  = id   == null ? "" : id;
      type  = typ  == null ? "" : typ;
      slot  = slt  == null ? "" : slt;
      start = strt == null ? "" : strt;
      end   = len  == null ? "" : len;
    }
  } 
  
  public ParamTable (JTable tbl) {
    table = tbl;
    
    // init the params
    bSortOrder = false;
    rowSelection = -1;
    colSortSelection = 0;
    
    table.setModel(new DefaultTableModel(new Object [][]{ }, TABLE_COLUMNS) {
      Class[] types = new Class [] {
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class, java.lang.String.class,
      };
      boolean[] canEdit = new boolean [] {
        false, false, false, false, false
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

    table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
    table.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        tableMouseClicked(evt);
      }
    });
//    table.addKeyListener(new java.awt.event.KeyAdapter() {
//      @Override
//      public void keyPressed(java.awt.event.KeyEvent evt) {
//        tableKeyPressed(evt);
//      }
//    });
        
    // align columns in database table to center
    DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
    centerRenderer.setHorizontalAlignment( SwingConstants.CENTER );
    table.setDefaultRenderer(String.class, centerRenderer);
    TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
    JLabel headerLabel = (JLabel) headerRenderer;
    headerLabel.setHorizontalAlignment( SwingConstants.CENTER );
        
    // create up & down key handlers for cloud row selection
    AbstractAction tableUpArrowAction = new UpArrowAction();
    AbstractAction tableDnArrowAction = new DnArrowAction();
    table.getInputMap().put(KeyStroke.getKeyStroke("UP"), "upAction");
    table.getActionMap().put( "upAction", tableUpArrowAction );
    table.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "dnAction");
    table.getActionMap().put( "dnAction", tableDnArrowAction );
        
    // add a mouse listener for the cloud table header selection
    JTableHeader cloudTableHeader = table.getTableHeader();
    cloudTableHeader.addMouseListener(new HeaderMouseListener());
  }
    
  public void clear(String fullmeth) {
    paramList.clear();
    DefaultTableModel model = (DefaultTableModel) table.getModel();
    model.setRowCount(0); // this clears all the entries from the table
    methodname = fullmeth;
  }
  
  public void exit() {
  }
  
  public void addEntry(String name, String type, String slot, String start, String end) {
    TableListInfo entry = new TableListInfo(name, type, slot, start, end);
    paramList.add(entry);
    tableSortAndDisplay();
  }
  
  public static LocalParamInfo getParam(int slot, int offset) {
    for (TableListInfo entry : paramList) {
      if (entry.slot.equals(slot + "")) {
        try {
          Integer start = Integer.parseUnsignedInt(entry.start);
          Integer end = Integer.parseUnsignedInt(entry.end);
          if (start <= offset & offset <= end) {
            LocalParamInfo retval = new LocalParamInfo();
            retval.name = entry.name;
            retval.type = entry.type;
            retval.start = start;
            retval.end   = end;
            retval.slot  = slot;
            return retval;
          }
        } catch (NumberFormatException ex) {
          Utils.printStatusError("Invalid format for start and end values: " +
              entry.start + ", " + entry.end);
        }
      }
    }
    return null;
  }
  
  private String getColumnName(int col) {
    if (col < 0 || col >= TABLE_COLUMNS.length) {
      col = 0;
    }
    return TABLE_COLUMNS[col];
  }
  
  private String getColumnParam(int col, TableListInfo tblinfo) {
    switch(getColumnName(col)) {
      default: // fall through...
      case "Name":
        return tblinfo.name;
      case "Type":
        return tblinfo.type;
      case "Slot":
        return tblinfo.slot;
      case "Start":
        return tblinfo.start;
      case "End":
        return tblinfo.end;
    }
  }
  
  // NOTE: the order of the entries should match the order of the columns
  private Object[] makeRow(TableListInfo tableEntry) {
    return new Object[]{
        tableEntry.name,
        tableEntry.type,
        tableEntry.slot,
        tableEntry.start,
        tableEntry.end,
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
   * This performs a sort on the selected table column and updates the table display.
   * The sort based on the current criteria of:
   * 'colSortSelection' which specifies the column on which to sort and
   * 'bSortOrder' which specifies either ascending (false) or descending (true) order.
   */
  private void tableSortAndDisplay () {
    // sort the table entries
    Collections.sort(paramList, new Comparator<TableListInfo>() {
      @Override
      public int compare(TableListInfo job1, TableListInfo job2) {
        String object1 = getColumnParam(colSortSelection, job1);
        String object2 = getColumnParam(colSortSelection, job2);
        if (!bSortOrder)
          return  object1.compareTo(object2);
        else
          return  object2.compareTo(object1);
      }
    });

    // clear out the table entries
    DefaultTableModel model = (DefaultTableModel) table.getModel();
    model.setRowCount(0); // this clears all the entries from the table

    // reset the names of the columns and modify the currently selected one
    String[] columnNames = new String[TABLE_COLUMNS.length];
    System.arraycopy(TABLE_COLUMNS, 0, columnNames, 0, TABLE_COLUMNS.length);
    for (int ix = 0; ix < model.getColumnCount(); ix++) {
      if (ix == colSortSelection) {
        if (bSortOrder) {
          columnNames[ix] += " " + "\u2193".toCharArray()[0]; // DOWN arrow
        }
        else {
          columnNames[ix] += " " + "\u2191".toCharArray()[0]; // UP arrow
        }
      }
    }
    model.setColumnIdentifiers(columnNames);
        
    // now copy the entries to the displayed table
    for (int ix = 0; ix < paramList.size(); ix++) {
      TableListInfo tableEntry = paramList.get(ix);
      model.addRow(makeRow(tableEntry));
    }

    // auto-resize column width
    resizeColumnWidth(table);
  }
    
  /**
   * action event when the mouse is clicked in the table.
   */
  private void tableMouseClicked(java.awt.event.MouseEvent evt) {                                            
    Utils.printStatusClear();

    int row = table.rowAtPoint(evt.getPoint());
    //int col = table.columnAtPoint(evt.getPoint());
    //String colname = getColumnName(col);
    // no column-specific actions here - the user is simply selecting a row to add to symbolics

    // ask user if he wants to copy the variable to the symbolic list
    String[] selection = {"Yes", "No" };
    int which = JOptionPane.showOptionDialog(null,
      "Add Bytecode local variable to symbolic parameter list?",
      "Add Symbolic Parameter", // title of pane
      JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
      JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
      null, // icon
      selection, selection[1]);

    if (which >= 0 && selection[which].equals("Yes")) {
      String name  = (String)table.getValueAt(row, getColumnIndex("Name"));
      String type  = (String)table.getValueAt(row, getColumnIndex("Type"));
      String slot  = (String)table.getValueAt(row, getColumnIndex("Slot"));
      String start = (String)table.getValueAt(row, getColumnIndex("Start"));
      String end   = (String)table.getValueAt(row, getColumnIndex("End"));
      int startVal = 0;
      int endVal = 0;
      try {
        startVal = Integer.parseUnsignedInt(start);
        endVal   = Integer.parseUnsignedInt(end);
      } catch (NumberFormatException ex) {
        Utils.printStatusError("Invalid format for start and end values: " + start + ", " + end);
        return;
      }
      Integer opstrt = BytecodeViewer.byteOffsetToLineNumber(startVal);
      Integer oplast = BytecodeViewer.byteOffsetToLineNumber(endVal);
      if (opstrt == null || oplast == null) {
        Utils.printStatusError("No line found for start and end values: " + start + ", " + end);
      } else {
        LauncherMain.addSymbVariable(methodname, name, type, slot, start, end, opstrt, oplast);
      }
    }
  }                                           

  /**
   * action event when a key is pressed in the table.
   */
  private void tableKeyPressed(java.awt.event.KeyEvent evt) {                                          
    switch (evt.getKeyCode()) {
      case KeyEvent.VK_ENTER: // ENTER key
        break;
      default:
        break;
    }
  }                                         

  /**
   * handles cursoring with the UP arrow key when a table row is selected..
   */
  private class UpArrowAction extends AbstractAction {

    @Override
    public void actionPerformed (ActionEvent evt) {
//      debugDisplayEvent ("UpArrowAction: old row = " + rowSelection);
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
//      debugDisplayEvent ("DnArrowAction: old row = " + rowSelectionrowSelection);
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
      int newSelection = table.columnAtPoint(evt.getPoint());
      if (newSelection >= 0) {
        int oldSelection = colSortSelection;
        colSortSelection = newSelection;
//        debugDisplayEvent ("HeaderMouseListener: (" + evt.getX() + "," + evt.getY() + ") -> col "
//                          + newSelection + " = " + getColumnName(newSelection));

        // invert the order selection if the same column is specified
        if (oldSelection == newSelection)
          bSortOrder = !bSortOrder;
                    
        // sort the table entries based on current selections
        tableSortAndDisplay();
      } else {
//        debugDisplayEvent ("HeaderMouseListener: (" + evt.getX() + "," + evt.getY() + ") -> col "
//                          + newSelection);
      }
    }
  }
    
}
