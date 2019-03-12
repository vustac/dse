/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package panels;

import gui.GuiControls;
import logging.FontInfo;
import main.LauncherMain;
import util.Utils;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import util.Utils.ClassMethodName;

/**
 *
 * @author dan
 */
/**
 *
 * @author dan
 */
public class SymbolTable {

  private static final String[] TABLE_COLUMNS = new String [] {
    "Method", "Slot", "Start", "End", "Name", "Type"
  };

  private static final java.awt.Color DEFAULT_BUTTON_COLOR = new JButton().getBackground();
  
  private static boolean  bSortOrder;
  private static int      colSortSelection;
  private static int      rowSelection;
  private static JTable   table;
  private static ArrayList<TableListInfo> paramList = new ArrayList<>();
  private static ArrayList<String> paramNameList = new ArrayList<>();
  private static GuiControls optionsPanel = new GuiControls();
  private static boolean  bEdited;

  
  public static class ConstraintInfo {
    public String comptype;  // comparison type { EQ, NE, GT, GE, LT, LE }
    public String compvalue; // the comparison value
    
    public ConstraintInfo(String type, String value) {
      comptype = type;
      compvalue = value;
    }
  }
  
  public static class TableListInfo {
    public String  method;   // the name of the method that the local parameter belongs to
    public String  name;     // the moniker to call the parameter by (unique entry)
    public String  type;     // data type of the parameter
    public String  slot;     // slot within the method for the parameter
    public String  boStart;  // byte offset in method that specifies the starting range of the parameter
    public String  boEnd;    // byte offset in method that specifies the ending   range of the parameter
    
    // entries that are not placed in the table
    public String  opStart;  // starting opcode entry in method (cause danalyzer can't determine byte offset)
    public String  opEnd;    // ending   opcode entry in method (cause danalyzer can't determine byte offset)
    public ArrayList<ConstraintInfo> constraints; // the user-defined constraint values for the symbolic entry
    
    public TableListInfo(String meth, String id, String typ, String slt, String strt, String last,
                         int opstrt, int oplast) {
      method = meth == null ? "" : meth;
      name    = id   == null ? "" : id;
      type    = typ  == null ? "" : typ;
      slot    = slt  == null ? "" : slt;
      opStart = "" + opstrt;
      opEnd   = "" + oplast;

      boStart = strt == null ? "" : strt;
      boEnd   = last == null ? "" : last;
      
      constraints = new ArrayList<>();
    }
    
    public TableListInfo(String meth, String id, String typ, String slt, int opstrt, int oplast) {
      method  = meth == null ? "" : meth;
      name    = id   == null ? "" : id;
      type    = typ  == null ? "" : typ;
      slot    = slt  == null ? "" : slt;
      opStart = "" + opstrt;
      opEnd   = "" + oplast;

      boStart = opstrt == 0 ? "0" : ""; // can't convert to byte offsets except for a value of 0
      boEnd   = oplast == 0 ? "0" : "";
      
      constraints = new ArrayList<>();
    }
    
    public void clearConstraints() {
      constraints = new ArrayList<>();
    }
  } 
  
  public SymbolTable (JTable tbl) {
    table = tbl;
    
    // init the params
    bSortOrder = false;
    rowSelection = -1;
    colSortSelection = 0;
    bEdited = false;
    
    table.setModel(new DefaultTableModel(new Object [][]{ }, TABLE_COLUMNS) {
      Class[] types = new Class [] {
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
        java.lang.String.class, java.lang.String.class, java.lang.String.class,
      };
      boolean[] canEdit = new boolean [] {
        false, false, false, false, false, false
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
    
  public void clear() {
    paramList.clear();
    paramNameList.clear();
    DefaultTableModel model = (DefaultTableModel) table.getModel();
    model.setRowCount(0); // this clears all the entries from the table
  }
  
  public int getSize() {
    return paramList.size();
  }
  
  public void exit() {
  }

  public String addEntry(String meth, String name, String type, String slot, String start, String end,
                       int opstrt, int oplast) {
    // if no name given, pick a default one
    name = getUniqueName(name);
    TableListInfo entry = new TableListInfo(meth, name, type, slot, start, end, opstrt, oplast);
    if (isMatch(entry)) {
      LauncherMain.printStatusError("This symbolic value already exists");
      return null;
    }

    // disallow constraints for non-numeric types
    if (!isValidDataType(type)) {
      entry.constraints.clear();
    }

    paramList.add(entry);
    paramNameList.add(name);
    tableSortAndDisplay();
    return name;
  }
  
  // this adds the entry using only opcode line numbers for the start and end range,
  // because the byte offset values are not known at the time (reading from danfig).
  public String addEntryByLine(String meth, String name, String type, String slot, int start, int end) {
    // if no name given, pick a default one
    name = getUniqueName(name);
    TableListInfo entry = new TableListInfo(meth, name, type, slot, start, end);
    if (isMatch(entry)) {
      LauncherMain.printStatusError("This symbolic value already exists");
      return null;
    }

    // disallow constraints for non-numeric types
    if (!isValidDataType(type)) {
      entry.constraints.clear();
    }

    paramList.add(entry);
    paramNameList.add(name);
    tableSortAndDisplay();
    return name;
  }
  
  public void addConstraint(String id, String type, String value) {
    for (TableListInfo entry : paramList) {
      if (entry.name.equals(id)) {
        entry.constraints.add(new ConstraintInfo(type, value));
        return;
      }
    }
  }
  
  public TableListInfo getSymbolicEntry(int ix) {
    if (ix < paramList.size()) {
      return paramList.get(ix);
    }
    return null;
  }
  
  private boolean isValidDataType(String type) {
    // allow primitives only for constraints
    type = Utils.convertDataType(type);
    return !(type.equals("STR") || type.equals("REF"));
  }
  
  private boolean isMatch(TableListInfo entry) {
    String entryMethod = entry.method.replaceAll("/", ".");
    for (TableListInfo tblval : paramList) {
      String tblMethod = tblval.method.replaceAll("/", ".");
      if (tblMethod.equals(entryMethod) &&
          tblval.slot.equals(entry.slot) &&
          tblval.opStart.equals(entry.opStart) &&
          tblval.opEnd.equals(entry.opEnd)) {
        return true;
      }
    }
    return false;
  }
  
  private String getUniqueName(String name) {
    // if no name given, pick a default one
    if (name == null || name.isEmpty()) {
      name = "P_0";
    }
    
    // name must be unique. if not, make it so
    if (paramNameList.contains(name)) {
      if (name.endsWith("_0")) {
        name = name.substring(0, name.lastIndexOf("_0"));
      }
      String newname = name;
      for (int index = 0; paramNameList.contains(newname); index++) {
        newname = name + "_" + index;
      }
      name = newname;
    }

    return name;
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
      case "Method":
        return tblinfo.method;
      case "Name":
        return tblinfo.name;
      case "Type":
        return tblinfo.type;
      case "Slot":
        return tblinfo.slot;
      case "Start":
        return tblinfo.opStart;
      case "End":
        return tblinfo.opEnd;
    }
  }
  
  // NOTE: the order of the entries should match the order of the columns
  private Object[] makeRow(TableListInfo tableEntry) {
    return new Object[]{
        tableEntry.method,
        tableEntry.slot,
        tableEntry.opStart,
        tableEntry.opEnd,
        tableEntry.name,
        tableEntry.type,
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
    int row = table.rowAtPoint(evt.getPoint());
    int col = table.columnAtPoint(evt.getPoint());
    String colname = getColumnName(col);

    rowSelection = row;
    showMenuSelection();
  }                                           

  private void addButtonListener(String name, ActionListener listener) {
    JButton button = optionsPanel.getButton(name);
    button.addActionListener(listener);
  }
  
  private String getEditorButtonSelection(String title) {
    String basenane = title.toUpperCase();
    JButton button = optionsPanel.getButton("BTN_EDIT_" + basenane);
    if (button == null) {
      System.err.println("editorButtonSelect: not found: BTN_EDIT_" + basenane);
      System.exit(1);
    }

    JTextField text = optionsPanel.getTextField("TXT_EDIT_" + basenane);
    if (text == null) {
      System.err.println("editorButtonSelect: not found: TXT_EDIT_" + basenane);
      System.exit(1);
    }
    
    // determine mode of editor
    boolean bdone = button.getText().equals("Done");
    if (bdone) {
      // restore button name and disable make text writable
      button.setText(title);
      button.setBackground(DEFAULT_BUTTON_COLOR);
      text.setEditable(false);
    } else {
      // set button name to "Save" and enable make text writable
      button.setText("Done");
      button.setBackground(Color.pink);
      text.setEditable(true);
    }
    return text.getText();
  }
  
  private void restoreEditorSelection(String title, String value) {
    String basenane = title.toUpperCase();
    optionsPanel.getTextField("TXT_EDIT_" + basenane).setText(value);
  }
  
  private void updateConstraintMenu(boolean bConstraints) {
    // if no constraints, add the no constraints msg, else don't
    if (bConstraints) {
      optionsPanel.getLabel ("LBL_CON_NONE").setText("");
    } else {
      optionsPanel.getLabel ("LBL_CON_NONE").setText("There are no user-defined constraints for this selection");
    }
    
    // if constraints, add the edit & show buttons else remove
    optionsPanel.getButton("BTN_CON_SHOW").setEnabled(bConstraints);
    optionsPanel.getLabel ("LBL_CON_SHOW").setEnabled(bConstraints);
    optionsPanel.getButton("BTN_CON_REM").setEnabled(bConstraints);
    optionsPanel.getLabel ("LBL_CON_REM").setEnabled(bConstraints);
  }
  
  private void showMenuSelection() {
    JFrame frame = optionsPanel.newFrame("Symbolic Parameter Modification", 650, 550,
        GuiControls.FrameSize.NOLIMIT);
    frame.addWindowListener(new Window_ExitListener());

    // seperate the class and method names for display
    TableListInfo pinfo = paramList.get(rowSelection);
    Utils.ClassMethodName classmeth = new ClassMethodName(pinfo.method);
    LauncherMain.runBytecodeViewer(classmeth.className, classmeth.methName + classmeth.signature);
    
    // indicate nothing has been edited yet
    bEdited = false;
    
    // determine if there are any user-defined constraints for the entry
    boolean bConstraints = false;
    if (!pinfo.constraints.isEmpty()) {
      bConstraints = true;
    }

    // set the font style for the labels
    Font labelFont = new Font("Ariel", 0, 12);
    GuiControls.Orient LEFT = GuiControls.Orient.LEFT;
   
    String panel = null;
    optionsPanel.makePanel (panel, "PNL_INFO"       , LEFT, true, "");
    optionsPanel.makePanel (panel, "PNL_EDIT_SYMB"  , LEFT, true, "Edit Symbolic");
    optionsPanel.makePanel (panel, "PNL_REMOVE_SYMB", LEFT, true, "Remove Symbolic");
    optionsPanel.makePanel (panel, "PNL_CONSTRAINTS", LEFT, true, "Setup Constraint");

    panel = "PNL_INFO";
    optionsPanel.makeLabel (panel, "", LEFT, false, "Class:");
    optionsPanel.makeLabel (panel, "", LEFT, true , classmeth.className, labelFont, FontInfo.TextColor.Blue);
    optionsPanel.makeLabel (panel, "", LEFT, false, "Method:");
    optionsPanel.makeLabel (panel, "", LEFT, true , classmeth.methName, labelFont, FontInfo.TextColor.Blue);
    optionsPanel.makeLabel (panel, "", LEFT, false, "Slot:");
    optionsPanel.makeLabel (panel, "", LEFT, true , pinfo.slot, labelFont, FontInfo.TextColor.Blue);

    panel = "PNL_EDIT_SYMB";
    optionsPanel.makeButton   (panel, "BTN_EDIT_NAME" , LEFT, false, "Name");
    optionsPanel.makeTextField(panel, "TXT_EDIT_NAME" , LEFT, true , pinfo.name , 20, false);
    optionsPanel.makeButton   (panel, "BTN_EDIT_TYPE" , LEFT, false, "Type");
    optionsPanel.makeTextField(panel, "TXT_EDIT_TYPE" , LEFT, true , pinfo.type , 20, false);
    optionsPanel.makeButton   (panel, "BTN_EDIT_START", LEFT, false, "Start");
    optionsPanel.makeTextField(panel, "TXT_EDIT_START", LEFT, false, pinfo.opStart, 5 , false);
    optionsPanel.makeLabel    (panel, "LBL_EDIT_NOTE" , LEFT, true , "(line number, not byte offset)",
                              labelFont, FontInfo.TextColor.Blue);
    optionsPanel.makeButton   (panel, "BTN_EDIT_END"  , LEFT, false, "End");
    optionsPanel.makeTextField(panel, "TXT_EDIT_END"  , LEFT, true , pinfo.opEnd  , 5 , false);

    panel = "PNL_REMOVE_SYMB";
    optionsPanel.makeButton(panel, "BTN_REMOVE_ONE"   , LEFT, false, "Remove");
    optionsPanel.makeLabel (panel, ""                 , LEFT, true , "Removes this symbolic",
                            labelFont, FontInfo.TextColor.Black);
    optionsPanel.makeButton(panel, "BTN_REMOVE_ALL"   , LEFT, false, "Remove all");
    optionsPanel.makeLabel (panel, ""                 , LEFT, true , "Removes all symbolics",
                            labelFont, FontInfo.TextColor.Black);

    panel = "PNL_CONSTRAINTS";
    optionsPanel.makeLabel (panel, "LBL_CON_NONE" , LEFT, true , "", labelFont, FontInfo.TextColor.Red);
    optionsPanel.makeButton(panel, "BTN_CON_SHOW" , LEFT, false, "Show");
    optionsPanel.makeLabel (panel, "LBL_CON_SHOW" , LEFT, true , "Show added constraints for selection",
                            labelFont, FontInfo.TextColor.Black);
    optionsPanel.makeButton(panel, "BTN_CON_REM"  , LEFT, false, "Remove");
    optionsPanel.makeLabel (panel, "LBL_CON_REM"  , LEFT, true , "Remove all constraints for this selection",
                            labelFont, FontInfo.TextColor.Black);
    optionsPanel.makeButton(panel, "BTN_CON_ADD"  , LEFT, false, "Add");
    optionsPanel.makeLabel (panel, "LBL_CON_ADD"  , LEFT, true , "Add constraint to selection",
                            labelFont, FontInfo.TextColor.Black);

    // set these buttons to the same width
    optionsPanel.makeGroup("GRP_EDIT_SYM");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_NAME");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_TYPE");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_START");
    optionsPanel.addGroupComponent("GRP_EDIT_SYM", "BTN_EDIT_END");
    optionsPanel.setGroupSameMinSize("GRP_EDIT_SYM", GuiControls.DimType.WIDTH);

    // set these buttons to the same width
    optionsPanel.makeGroup("GRP_REMOVE");
    optionsPanel.addGroupComponent("GRP_REMOVE", "BTN_REMOVE_ONE");
    optionsPanel.addGroupComponent("GRP_REMOVE", "BTN_REMOVE_ALL");
    optionsPanel.setGroupSameMinSize("GRP_REMOVE", GuiControls.DimType.WIDTH);
    
    // set these buttons to the same width
    optionsPanel.makeGroup("GRP_CONSTRAINTS");
    optionsPanel.addGroupComponent("GRP_CONSTRAINTS", "BTN_CON_SHOW");
    optionsPanel.addGroupComponent("GRP_CONSTRAINTS", "BTN_CON_REM");
    optionsPanel.addGroupComponent("GRP_CONSTRAINTS", "BTN_CON_ADD");
    optionsPanel.setGroupSameMinSize("GRP_CONSTRAINTS", GuiControls.DimType.WIDTH);
    
    // remove the edit & show buttons if there are none to do
    updateConstraintMenu(bConstraints);
    
    // add each button to the group and set the listener
    addButtonListener("BTN_EDIT_NAME" , new Action_EditName());
    addButtonListener("BTN_EDIT_TYPE" , new Action_EditType());
    addButtonListener("BTN_EDIT_START" , new Action_EditStart());
    addButtonListener("BTN_EDIT_END"  , new Action_EditEnd());
    addButtonListener("BTN_REMOVE_ONE", new Action_Remove());
    addButtonListener("BTN_REMOVE_ALL", new Action_RemoveAll());
    addButtonListener("BTN_CON_SHOW", new Action_ConstraintShow());
    addButtonListener("BTN_CON_REM" , new Action_ConstraintRemove());
    addButtonListener("BTN_CON_ADD"   , new Action_ConstraintAdd());

    optionsPanel.pack();
    optionsPanel.display();
  }

  private class Window_ExitListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      // update the danfig file before closing (if something changed)
      if (bEdited) {
        LauncherMain.updateDanfigFile();
        bEdited = false;
      }
      optionsPanel.close();
    }
  }

  private class Action_EditName implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo entry = paramList.get(rowSelection);
      String curval = entry.name;
      String type = "Name";
      
      // allow user to modify value & determine if done
      String newval = getEditorButtonSelection(type);
      if (newval.equals(curval)) { // skip if no change
        return;
      }

      // check if name already used in symbolic list
      if (paramNameList.contains(newval)) {
        // restore previous value
        restoreEditorSelection(type, curval);
        LauncherMain.printStatusError("Symbolic name is already used: " + newval);
      } else if (newval.isEmpty()) {
        // restore previous value
        restoreEditorSelection(type, curval);
        LauncherMain.printStatusError("Must define a valid name");
      } else {
        // remove previous name entry and add the new one
        paramNameList.remove(curval);
        entry.name = newval;
        paramNameList.add(newval);
        tableSortAndDisplay();
        bEdited = true;
      }
    }
  }
  
  private class Action_EditType implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo entry = paramList.get(rowSelection);
      String curval = entry.type;
      String type = "Type";
      
      // allow user to modify value & determine if done
      String newval = getEditorButtonSelection(type);
      if (newval.equals(curval)) { // skip if no change
        return;
      }

      if (newval.isEmpty()) {
        // restore previous value
        restoreEditorSelection(type, curval);
        LauncherMain.printStatusError("Must define a valid type");
      } else {
        entry.type = newval;
        tableSortAndDisplay();
        bEdited = true;
        
        // TODO: disallow constraints for non-numeric types
        if (!isValidDataType(entry.type) && !entry.constraints.isEmpty()) {
          entry.constraints.clear();
          updateConstraintMenu(false);
        }
      }
    }
  }
  
  private class Action_EditStart implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo entry = paramList.get(rowSelection);
      String curval = entry.opStart;
      String type = "Start";
      
      // allow user to modify value & determine if done
      String newval = getEditorButtonSelection(type);
      if (newval.equals(curval)) { // skip if no change
        return;
      }

      try {
        int value = Integer.parseUnsignedInt(newval);
        entry.opStart = newval;
        tableSortAndDisplay();
        bEdited = true;
      } catch (NumberFormatException ex) {
        // restore previous value
        restoreEditorSelection(type, curval);
        LauncherMain.printStatusError("Invalid start offset value: " + newval);
      }
    }
  }
  
  private class Action_EditEnd implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo entry = paramList.get(rowSelection);
      String curval = entry.opEnd;
      String type = "End";
      
      // allow user to modify value & determine if done
      String newval = getEditorButtonSelection(type);
      if (newval.equals(curval)) { // skip if no change
        return;
      }

      try {
        int value = Integer.parseUnsignedInt(newval);
        entry.opEnd = newval;
        tableSortAndDisplay();
        bEdited = true;
      } catch (NumberFormatException ex) {
        // restore previous value
        restoreEditorSelection(type, curval);
        LauncherMain.printStatusError("Invalid end offset value: " + newval);
      }
    }
  }

  private class Action_Remove implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;
      String[] selection = {"Yes", "No" };
      int which = JOptionPane.showOptionDialog(null,
        "Remove entry from list?",
        "Remove entry", // title of pane
        JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
        null, // icon
        selection, selection[1]);

      if (which >= 0 && selection[which].equals("Yes")) {
        // remove selected symbolic parameter
        String name = paramList.get(row).name;
        paramList.remove(row);
        paramNameList.remove(name);
      
        // update table display
        tableSortAndDisplay();

        // update the danfig file before closing
        bEdited = false;
        LauncherMain.updateDanfigFile();
        optionsPanel.close();
      }
    }
  }
  
  private class Action_RemoveAll implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      String[] selection = {"Yes", "No" };
      int which = JOptionPane.showOptionDialog(null,
        "Remove all symbolics from list?",
        "Remove all", // title of pane
        JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
        null, // icon
        selection, selection[1]);

      if (which >= 0 && selection[which].equals("Yes")) {
        // remove all symbolic parameters
        paramList.clear();
        paramNameList.clear();
      
        // update table display
        tableSortAndDisplay();

        // update the danfig file before closing
        bEdited = false;
        LauncherMain.updateDanfigFile();
        optionsPanel.close();
      }
    }
  }
  
  private class Action_ConstraintShow implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo tbl = paramList.get(rowSelection);
      String pname = "'" + tbl.name + "'";
      if (tbl.name.isEmpty()) {
        pname = "The selected parameter";
      }

      String message = pname + " has the following user-defined constraints:" + Utils.NEWLINE + Utils.NEWLINE;
      for (int ix = 0; ix < tbl.constraints.size(); ix++) {
        ConstraintInfo con = tbl.constraints.get(ix);
        message += con.comptype + " " + con.compvalue + Utils.NEWLINE;
      }

      JOptionPane.showMessageDialog(null, message, "Message Dialog", JOptionPane.INFORMATION_MESSAGE);
    }
  }
  
  private class Action_ConstraintRemove implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      TableListInfo tbl = paramList.get(rowSelection);
      String[] selection = {"Yes", "No" };
      int which = JOptionPane.showOptionDialog(null,
        "Remove all constraints from '" + tbl.name + "' ?",
        "Remove constraints", // title of pane
        JOptionPane.YES_NO_CANCEL_OPTION, // DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE, // PLAIN_MESSAGE
        null, // icon
        selection, selection[1]);

      if (which >= 0 && selection[which].equals("Yes")) {
        // remove all constraints for the selected symbolic
        tbl.constraints.clear();
        bEdited = true;
        
        // update menu if there are no more
        updateConstraintMenu(false);
      }
    }
  }

  private class Action_ConstraintAdd implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      int row = rowSelection;

      String result = JOptionPane.showInputDialog(null, "Enter the constraint (e.g. GE 23):");
      TableListInfo entry = paramList.get(row);

      int offset = result.indexOf(" ");
      if (offset < 0) {
        LauncherMain.printStatusError("missing numeric comparison value");
        return;
      }

      String comptype = result.substring(0, offset).trim().toUpperCase();
      String compval  = result.substring(offset + 1).trim();

      if (!entry.type.equals("D") && !entry.type.equals("F") && compval.contains(".")) {
        LauncherMain.printStatusMessage("comparison value will ignore decimal entries");
        compval = compval.substring(0, compval.indexOf("."));
      }

      long lvalue = 0;
      try {
        if (entry.type.equals("D") || entry.type.equals("F")) {
          double dvalue = Double.parseDouble(compval);
        } else {
          lvalue = Long.parseLong(compval);
        }
      } catch (NumberFormatException ex) {
        LauncherMain.printStatusError("invalid comparison value (must be numeric)");
        return;
      }

      // verify the numeric value is within limits for integer types
      long minval, maxval;
      switch (entry.type) {
        case "Z":
          minval = 0;
          maxval = 1;
          if (lvalue < minval || lvalue > maxval) {
            LauncherMain.printStatusError("out-of-range comparison value: " + lvalue +
                    " [" + minval + ", " + maxval + "]");
            return;
          }
          break;
        case "C":
          minval = Character.MIN_VALUE;
          maxval = Character.MAX_VALUE;
          if (lvalue < minval || lvalue > maxval) {
            LauncherMain.printStatusError("out-of-range comparison value: " + lvalue +
                    " [" + minval + ", " + maxval + "]");
            return;
          }
          break;
        case "B":
          minval = Byte.MIN_VALUE;
          maxval = Byte.MAX_VALUE;
          if (lvalue < minval || lvalue > maxval) {
            LauncherMain.printStatusError("out-of-range comparison value: " + lvalue +
                    " [" + minval + ", " + maxval + "]");
            return;
          }
          break;
        case "S":
          minval = Short.MIN_VALUE;
          maxval = Short.MAX_VALUE;
          if (lvalue < minval || lvalue > maxval) {
            LauncherMain.printStatusError("out-of-range comparison value: " + lvalue +
                    " [" + minval + ", " + maxval + "]");
            return;
          }
          break;
        case "I":
          minval = Integer.MIN_VALUE;
          maxval = Integer.MAX_VALUE;
          if (lvalue < minval || lvalue > maxval) {
            LauncherMain.printStatusError("out-of-range comparison value: " + lvalue +
                    " [" + minval + ", " + maxval + "]");
            return;
          }
          break;
        case "J":
          minval = Long.MIN_VALUE;
          maxval = Long.MAX_VALUE;
          if (lvalue < minval || lvalue > maxval) {
            LauncherMain.printStatusError("out-of-range comparison value: " + lvalue +
                    " [" + minval + ", " + maxval + "]");
            return;
          }
          break;
        default:
          break;
      }
      
      // if user enters symbolic entry, convert to standard nomenclature
      switch (comptype) {
        case "==":  comptype = "EQ";  break;
        case "!=":  comptype = "NE";  break;
        case ">":   comptype = "GT";  break;
        case ">=":  comptype = "GE";  break;
        case "<":   comptype = "LT";  break;
        case "<=":  comptype = "LE";  break;
        default:
          break;
      }
      if (!comptype.equals("EQ") && !comptype.equals("GT") && !comptype.equals("LT") &&
          !comptype.equals("NE") && !comptype.equals("GE") && !comptype.equals("LE")) {
        LauncherMain.printStatusError("constraint must begin with { EQ, NE, GT, GE, LT, LE }");
        return;
      }

      // add constraint to symbolic
      entry.constraints.add(new ConstraintInfo(comptype, compval));
      bEdited = true;
        
      // update menu if there are no more
      updateConstraintMenu(true);
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
