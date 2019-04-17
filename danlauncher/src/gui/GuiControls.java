/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import logging.FontInfo;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import util.Utils;

/**
 *
 * @author dan
 */
public class GuiControls {
  
  private static final int GAPSIZE = 4; // gap size to place on each side of each widget
  
  private static final Dimension SCREEN_SIZE = Toolkit.getDefaultToolkit().getScreenSize();
    
  public enum Orient { NONE, LEFT, RIGHT, CENTER }
  
  public enum FrameSize { NOLIMIT, FIXEDSIZE, FULLSCREEN }

  public enum DimType { WIDTH, HEIGHT, BOTH }
  
  public enum Expand { NONE, HORIZONTAL, VERTICAL, BOTH }
  
  // types of controls for user-entry
  public enum InputControl { Label, TextField, CheckBox, RadioButton, ComboBox, Spinner, Button }
  
  private JFrame         mainFrame = null;
  private GridBagLayout  mainLayout = null;
  private Dimension      framesize = null;
  
  // panels
  private final HashMap<String, PanelInfo>     gPanel = new HashMap<>();
  // components
  private final HashMap<String, JTextPane>     gTextPane = new HashMap();
  private final HashMap<String, JTextArea>     gTextArea = new HashMap();
  private final HashMap<String, JTable>        gTable = new HashMap<>();
  private final HashMap<String, JList>         gList = new HashMap();
  private final HashMap<String, JLabel>        gLabel = new HashMap();
  private final HashMap<String, JButton>       gButton = new HashMap();
  private final HashMap<String, JCheckBox>     gCheckbox = new HashMap();
  private final HashMap<String, JComboBox>     gCombobox = new HashMap();
  private final HashMap<String, JTextField>    gTextField = new HashMap();
  private final HashMap<String, JRadioButton>  gRadiobutton = new HashMap();
  private final HashMap<String, JSpinner>      gSpinner = new HashMap();
  // groups
  private final HashMap<String, ArrayList<WidgetInfo>>  groupMap = new HashMap();
  
  public class PanelInfo {
    public Component     panel;   // can be JPanel, JTabbedPane, JSplitPane, JScrollPane
    public int           index;   // for TABBED and SPLIT panels, defines the index of next panel
    
    public PanelInfo(Component panel) {
      this.panel = panel;
      this.index = 0;
    }
  }
  
  public class WidgetInfo {
    public String        name;
    public Component     comp;
    
    public WidgetInfo(String name, Component comp) {
      this.name = name;
      this.comp = comp;
    }
  }
  
  public GuiControls() {
    // use this if you don't want to allocate a JFrame yet (or at all)
    // if no frame created, you can only use the makeRawxxx calls for panels.
  }
  
  public GuiControls(String title, int width, int height) {
    // limit height and width to max of screen dimensions
    width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;
    height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;

    framesize = new Dimension(width, height);
    mainFrame = new JFrame(title);
    mainFrame.setPreferredSize(framesize);
    mainFrame.setMinimumSize(framesize);

    // setup the layout for the frame
    mainLayout = new GridBagLayout();
    mainFrame.setFont(new Font("SansSerif", Font.PLAIN, 14));
    mainFrame.setLayout(mainLayout);
  }
  
  public JFrame newFrame(String title, double portion) {
    if (mainFrame == null) {
      if (portion < 0.2) {
        portion = 0.2;
      }
      if (portion > 1.0) {
        portion = 1.0;
      }
      framesize = new Dimension((int)(portion * (double)SCREEN_SIZE.height),
                                (int)(portion * (double)SCREEN_SIZE.width));
      mainFrame = new JFrame(title);
      mainFrame.setPreferredSize(framesize);
      mainFrame.setMinimumSize(framesize);
      mainFrame.setMaximumSize(framesize);
      mainFrame.setResizable(false);

      // setup the layout for the frame
      mainLayout = new GridBagLayout();
      mainFrame.setFont(new Font("SansSerif", Font.PLAIN, 14));
      mainFrame.setLayout(mainLayout);
    }
    return mainFrame;
  }

  public JFrame newFrame(String title, int width, int height, FrameSize size) {
    if (mainFrame == null) {
      // limit height and width to max of screen dimensions
      width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;
      height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;

      framesize = new Dimension(width, height);
      mainFrame = new JFrame(title);
      mainFrame.setPreferredSize(framesize);
      mainFrame.setMinimumSize(framesize);
      switch (size) {
        case FIXEDSIZE:
          mainFrame.setMaximumSize(framesize);
          mainFrame.setResizable(false);
          break;
        case FULLSCREEN:
          mainFrame.setState(Frame.MAXIMIZED_BOTH);
          break;
      }

      // setup the layout for the frame
      mainLayout = new GridBagLayout();
      mainFrame.setFont(new Font("SansSerif", Font.PLAIN, 14));
      mainFrame.setLayout(mainLayout);
    }
    return mainFrame;
  }
  
  public Dimension getFrameSize() {
    return mainFrame.getSize();
  }
  
  public void pack() {
    if (mainFrame != null) {
      mainFrame.pack();
    }
  }
    
  public void display() {
    if (mainFrame != null) {
      mainFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      mainFrame.setLocationRelativeTo(null);
      mainFrame.setVisible(true);
    }
  }

  public void hide() {
    if (mainFrame != null) {
      mainFrame.setVisible(false);
    }
  }

  public void update() {
    if (mainFrame != null) {
      mainFrame.repaint();
    }
  }
  
  public void repack() {
    if (mainFrame != null) {
      mainFrame.pack();
      mainFrame.setPreferredSize(framesize);
    }
  }
  
  public void close() {
    groupMap.clear();
    gPanel.clear();
    
    gTextPane.clear();
    gTextArea.clear();
    gList.clear();
    gLabel.clear();
    gButton.clear();
    gCheckbox.clear();
    gCombobox.clear();
    gTextField.clear();
    gRadiobutton.clear();
    gSpinner.clear();
    
    if (mainFrame != null) {
      mainFrame.dispose();
      mainFrame = null;
    }
  }
  
  public JFrame getFrame() {
    return mainFrame;
  }

  public boolean isValidFrame() {
    return mainFrame != null;
  }

  public PanelInfo getPanelInfo(String name) {
    return gPanel.get(name);
  }
  
  public JTable getTable(String name) {
    if (gTable == null) {
      return null;
    }
    return gTable.get(name);
  }

  public JTextPane getTextPane(String name) {
    if (gTextPane == null) {
      return null;
    }
    return gTextPane.get(name);
  }

  public JTextArea getTextArea(String name) {
    if (gTextArea == null) {
      return null;
    }
    return gTextArea.get(name);
  }

  public JList getList(String name) {
    if (gList == null) {
      return null;
    }
    return gList.get(name);
  }

  public JLabel getLabel(String name) {
    if (gLabel == null) {
      return null;
    }
    return gLabel.get(name);
  }

  public JButton getButton(String name) {
    if (gButton == null) {
      return null;
    }
    return gButton.get(name);
  }

  public JCheckBox getCheckbox(String name) {
    if (gCheckbox == null) {
      return null;
    }
    return gCheckbox.get(name);
  }

  public JComboBox getCombobox(String name) {
    if (gCombobox == null) {
      return null;
    }
    return gCombobox.get(name);
  }

  public JTextField getTextField(String name) {
    if (gTextField == null) {
      return null;
    }
    return gTextField.get(name);
  }

  public JRadioButton getRadiobutton(String name) {
    if (gRadiobutton == null) {
      return null;
    }
    return gRadiobutton.get(name);
  }

  public JSpinner getSpinner(String name) {
    if (gSpinner == null) {
      return null;
    }
    return gSpinner.get(name);
  }

  private void printError(String message) {
    Utils.printStatusWarning(message);
  }
  
  private void printFatal(String message) {
    Utils.printStatusError(message);
    System.exit(1);
  }
  
  private boolean isPanel(Component comp) {
    if (comp == null) {
      return false;
    }
    return comp instanceof JPanel ||
           comp instanceof JTabbedPane ||
           comp instanceof JSplitPane ||
           comp instanceof JScrollPane;
  }
  
  private boolean isComponent(Component comp) {
    if (comp == null) {
      return false;
    }
    return comp instanceof JTable ||
           comp instanceof JList ||
           comp instanceof JTextPane ||
           comp instanceof JTextArea ||
           comp instanceof JButton ||
           comp instanceof JCheckBox ||
           comp instanceof JComboBox ||
           comp instanceof JTextField ||
           comp instanceof JRadioButton ||
           comp instanceof JSpinner ||
           comp instanceof JLabel;
  }
  
  private void savePanel(String name, Component comp) {
    if (isPanel(comp)) {
      gPanel.put(name, new PanelInfo(comp));
    } else {
      printFatal("ERROR: savePanel - invalid panel type: " + comp.getClass().getName());
    }
  }
  
  private void saveComponent(String name, Component comp) {
    if (comp instanceof JTable) {
      gTable.put(name, (JTable) comp);
    } else if (comp instanceof JList) {
      gList.put(name, (JList) comp);
    } else if (comp instanceof JTextPane) {
      gTextPane.put(name, (JTextPane) comp);
    } else if (comp instanceof JTextArea) {
      gTextArea.put(name, (JTextArea) comp);
    } else if (comp instanceof JButton) {
      gButton.put(name, (JButton) comp);
    } else if (comp instanceof JCheckBox) {
      gCheckbox.put(name, (JCheckBox) comp);
    } else if (comp instanceof JComboBox) {
      gCombobox.put(name, (JComboBox) comp);
    } else if (comp instanceof JTextField) {
      gTextField.put(name, (JTextField) comp);
    } else if (comp instanceof JRadioButton) {
      gRadiobutton.put(name, (JRadioButton) comp);
    } else if (comp instanceof JSpinner) {
      gSpinner.put(name, (JSpinner) comp);
    } else if (comp instanceof JLabel) {
      gLabel.put(name, (JLabel) comp);
    } else {
      printFatal("ERROR: saveComponent - invalid component type: " + comp.getClass().getName());
    }
  }

  private Component getGroupComponent(String name) {
    if (gButton.containsKey(name)) {
      return gButton.get(name);
    } else if (gCheckbox.containsKey(name)) {
      return gCheckbox.get(name);
    } else if (gCombobox.containsKey(name)) {
      return gCombobox.get(name);
    } else if (gTextField.containsKey(name)) {
      return gTextField.get(name);
    } else if (gRadiobutton.containsKey(name)) {
      return gRadiobutton.get(name);
    } else if (gSpinner.containsKey(name)) {
      return gSpinner.get(name);
    } else if (gLabel.containsKey(name)) {
      return gLabel.get(name);
    }
    
    printFatal("ERROR: getGroupComponent - component not found: " + name);
    return null;
  }
  
  public void makeGroup(String grpname) {
    if (groupMap.containsKey(grpname)) {
      printFatal("ERROR: makeGroup - group '" + grpname + "' already exists!");
    }
    
    groupMap.put(grpname, new ArrayList<>());
  }
  
  public void addGroupComponent(String grpname, String name) {
    if (!groupMap.containsKey(grpname)) {
      printFatal("ERROR: addGroupComponent - group '" + grpname + "' not found!");
    }

    // make sure group doesn't already have the component
    ArrayList<WidgetInfo> list = groupMap.get(grpname);
    for (WidgetInfo winfo : list) {
      if(winfo.name.equals(name)) {
        printFatal("ERROR: addGroupComponent - group '" + grpname + "' not found!");
      }
    }
    
    Component widget = getGroupComponent(name);
    list.add(new WidgetInfo(name, widget));
  }
  
  public void setGroupSameMinSize(String grpname, DimType which) {
    int height = 0;
    int width = 0;

    // get the list of components in the group
    ArrayList<WidgetInfo> list = groupMap.get(grpname);

    // get the max width and height of all of the components
    for (WidgetInfo widget : list) {
      Dimension dim = widget.comp.getPreferredSize();
      if (dim.width > 0 && dim.height > 0) {
        width = (width < dim.width) ? dim.width : width;
        height = (height < dim.height) ? dim.height : height;
      }
    }

    // exit if we couldn't get the size
    if (width <= 0 || height <= 0) {
      return;
    }
    
    // now modify the specified dimension of each component
    for (WidgetInfo widget : list) {
      Component comp = widget.comp;
      Dimension dim;
      switch (which) {
        case WIDTH:
          dim = new Dimension(width, comp.getPreferredSize().height);
          break;
        case HEIGHT:
          dim = new Dimension(comp.getPreferredSize().width, height);
          break;
        default:
        case BOTH:
          dim = new Dimension(width, height);
          break;
      }
      comp.setMinimumSize(dim);
      comp.setPreferredSize(dim);
    }
  }
  
  public Component getInputDevice(String name, InputControl type) {
    switch(type) {
      case Label:
        return getLabel(name);
      case TextField:
        return getTextField(name);
      case CheckBox:
        return getCheckbox(name);
      case ComboBox:
        return getCombobox(name);
      case Spinner:
        return getSpinner(name);
      case Button:
        return getButton(name);
      default:
        printError("ERROR: getInputDevice - Unhandled control type: " + type.toString());
        break;
    }
    return null;
  }
  
  public String getInputControl(String name, InputControl type) {
    // get the selected device
    Component control = getInputDevice(name, type);
    if (control == null) {
      printFatal("ERROR: getInputControl '" + name + "' of type " + type.toString() + " not found");
      return "";
    }
    
    // read the value from it and return as a String
    switch(type) {
      case Label:
        return ((JLabel)control).getText();
      case TextField:
        return ((JTextField)control).getText();
      case CheckBox:
        return "" + ((JCheckBox)control).isSelected();
      case ComboBox:
        String val = (String) ((JComboBox)control).getSelectedItem();
        return (val == null) ? "" : val;
      case Spinner:
        return (String) ((JSpinner)control).getValue();
      default:
        printFatal("ERROR: getInputControl - Unhandled control type: " + type.toString());
        break;
    }
    return "";
  }
  
  public void setInputControl(String name, InputControl type, String value) {
    // get the selected device
    Component control = getInputDevice(name, type);
    if (control == null) {
      printFatal("ERROR: setInputControl '" + name + "' of type " + type.toString() + " not found");
      return;
    }
    
    switch(type) {
      case Label:
        ((JLabel)control).setText(value);
        break;
      case TextField:
        ((JTextField)control).setText(value);
        break;
      case CheckBox:
        ((JCheckBox)control).setSelected(value.equals("true"));
        break;
      case ComboBox:
        ((JComboBox)control).setSelectedItem(value);
        break;
      case Spinner:
        // NOTE: this only handles integer spinners
        Integer intval;
        try {
          intval = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
          printError("ERROR: setInputControl '" + name + "' set to invalid numeric value: " + value);
          return;
        }
        ((JSpinner)control).setValue(intval);
        break;
      default:
        printFatal("ERROR: setInputControl - Unhandled control type: " + type.toString());
        break;
    }
  }
  
  private Dimension getPanelSize(String panelname) {
    if (panelname == null || panelname.isEmpty()) {
      return mainFrame.getSize();
    }
    PanelInfo panelInfo = gPanel.get(panelname);
    if (panelInfo == null) {
      printFatal("ERROR: getPanelSize: '" + panelname + "' panel not found!");
    }
    
//    return panelInfo.panel.getSize();
    return panelInfo.panel.getPreferredSize();
  }
  
  /**
   * This modifies the dimensions of the specified JPanel.
   * 
   * @param panelname - the name of the jPanel container to modify
   * @param height  - minimum height of panel
   * @param width   - minimum width of panel
   * @return the panel
   */
  public Component setPanelSize(String panelname, int width, int height) {
    // limit height and width to max of screen dimensions
    height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;
    width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;

    Component panel = gPanel.get(panelname).panel;
    Dimension fsize = new Dimension(height, width);
    panel.setPreferredSize(fsize);
    panel.setMinimumSize(fsize);
    return panel;
  }
  
  /**
   * This sets up the gridbag constraints for a simple element
   * 
   * @param pos - the orientation on the line
   * @param end - true if this is the last (or only) entry on the line
   * @param fillStype - NONE, HORIZONTAL, VERTICAL, BOTH
   * @return the constraints
   */
  private GridBagConstraints setGbagConstraints(Orient pos, boolean end, Expand fillStype) {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(GAPSIZE, GAPSIZE, GAPSIZE, GAPSIZE);

    switch(pos) {
      case LEFT:
        c.anchor = GridBagConstraints.LINE_START;
        break;
      case RIGHT:
        c.anchor = GridBagConstraints.LINE_END;
        break;
      case CENTER:
        c.anchor = GridBagConstraints.CENTER;
        break;
      case NONE:
        // this will attempt to fill out the container & expand both vertically & horizontally
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth  = GridBagConstraints.REMAINDER;
        // since only 1 component, these both have to be non-zero for grid bag to work
        c.weightx = 1.0;
        c.weighty = 1.0;
        return c;
    }
    
    switch(fillStype) {
      default:
      case NONE:
        c.fill = GridBagConstraints.NONE;
        break;
      case HORIZONTAL:
        c.fill = GridBagConstraints.HORIZONTAL;
        break;
      case VERTICAL:
        c.fill = GridBagConstraints.VERTICAL;
        break;
      case BOTH:
        c.fill = GridBagConstraints.BOTH;
        break;
    }

    c.gridheight = 1;
    if (end) {
      c.gridwidth = GridBagConstraints.REMAINDER; //end row
    }
    return c;
  }

  /**
   * This is used for placing a plane in a container (either JFrame or another panel)
   * 
   * @param panelname - name of the container the panel is being placed in (null for the JFrame)
   * @param comp - the panel being placed
   * @param pos  - location within the container to place (LEFT, RIGHT, CENTER or NONE to fill container)
   * @param end  - true if no more panels being placed in the container
   * @param fillStyle - whether to expand the panel being placed VERTICALly, HORIZONTALly, BOTH, or NONE
   */
  public void setGridBagLayout(String panelname, Component comp, Orient pos, boolean end, Expand fillStyle) {
    GridBagLayout gridbag;

    // handle the case of a panel being placed in the main JFrame
    if (panelname == null || panelname.isEmpty()) {
      gridbag = mainLayout;
      gridbag.setConstraints(comp, setGbagConstraints(pos, end, fillStyle));
      return;
    }
    
    // else, panel in a panel - get the container panel
    PanelInfo panelInfo;
    panelInfo = getPanelInfo(panelname);
    if (panelInfo == null) {
      printFatal("ERROR: '" + panelname + "' container panel not found!");
    }

    Component panel = gPanel.get(panelname).panel;
    if (panel instanceof JPanel) {
      gridbag = (GridBagLayout) ((JPanel)panel).getLayout();
      gridbag.setConstraints(comp, setGbagConstraints(pos, end, fillStyle));
    }
  }
  
  public void addPanelToPanel(String panelname, Component newPanel) {
    if (panelname == null || panelname.isEmpty()) {
      // adding panel to main frame
      if (mainFrame == null) {
        printFatal("ERROR: addPanelToPanel: mainFrame not created!");
      }

      mainFrame.add(newPanel);
      return;
    }

    // adding panel to another panel
    PanelInfo panelInfo = gPanel.get(panelname);
    if (panelInfo == null) {
      printFatal("ERROR: addPanelToPanel: '" + panelname + "' panel not found!");
    }
    
    // determine the type of panel we are adding to
    Component panel = panelInfo.panel;
    if (panel instanceof JPanel) {
      ((JPanel) panel).add(newPanel);
    } else if (panel instanceof JScrollPane) {
      ((JScrollPane) panel).add(newPanel);
    } else if (panel instanceof JTabbedPane) {
      ((JTabbedPane) panel).add(newPanel);
      panelInfo.index++; // bump ptr to next location in container panel
    } else if (panel instanceof JSplitPane) {
      if (panelInfo.index == 0) {
        ((JSplitPane) panel).setLeftComponent(newPanel);
        panelInfo.index = 1;
      } else {
        ((JSplitPane) panel).setRightComponent(newPanel);
        panelInfo.index = 0;
      }
    } else {
      printFatal("ERROR: addPanelToPanel: '" + panelname + "' is invalid type: " + panel.getClass().getName());
    }
  }
  
  private void addPanelToPanel(String panelname, String name) {
    PanelInfo newPanel = getPanelInfo(name);
    if (newPanel == null) {
      printFatal("ERROR: addPanelToPanel: '" + name + "' panel not found!");
    }
    
    addPanelToPanel(panelname, newPanel.panel);
  }
    
  private Component getSelectedPanel(String panelname) {
    // get container panel if specified
    Component panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = gPanel.get(panelname).panel;
      if (panel == null) {
        printFatal("ERROR: '" + panelname + "' panel not found!");
      }
    }
    return panel;
  }
  
  /**
   * This adds the specified panel component to the main frame.
   * 
   * @param component - component to place the main frame
   */
  public void addToPanelFrame(JComponent component) {
    if (mainFrame == null || mainLayout == null) {
      return;
    }
    GridBagLayout gridbag = mainLayout;
    GridBagConstraints gc = setGbagConstraints(Orient.NONE, true, Expand.BOTH);
    gridbag.setConstraints(component, gc);
    mainFrame.add(component);
  }

  /**
   * This creates an empty JLabel and places it in the container to add a vertical gap between components.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param height    - the height of the gap (in number of pixels)
   */
  public void makeLineGap(String panelname, int height) {
    if (mainFrame == null || mainLayout == null) {
      return;
    }

    JLabel label = new JLabel(" ");
    label.setPreferredSize(new Dimension(10, height));
    Component cpanel = getSelectedPanel(panelname);

    GridBagLayout gridbag;
    if (panelname == null) {
      gridbag = mainLayout;
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, true, Expand.NONE));
      mainFrame.add(label);
    } else if (cpanel != null && cpanel instanceof JPanel) {
      JPanel panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, true, Expand.NONE));
      panel.add(label);
    } else {
      printFatal("ERROR: makeLineGap: Invalid panel type for: " + panelname);
    }
  }
  
  /**
   * This creates an empty JLabel and places it in the container to add a horizontal gap between components.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param width - width in pixels for the gap (the width of the dummy label)
   */
  public void makeGap(String panelname, int width) {
    if (mainFrame == null || mainLayout == null) {
      return;
    }

    JLabel label = new JLabel("");
    Dimension dim = new Dimension(width, 25);
    label.setPreferredSize(dim);
    label.setMinimumSize(dim);
    Component cpanel = getSelectedPanel(panelname);

    GridBagLayout gridbag;
    if (panelname == null) {
      gridbag = mainLayout;
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, false, Expand.NONE));
      mainFrame.add(label);
    } else if (cpanel != null && cpanel instanceof JPanel) {
      JPanel panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, false, Expand.NONE));
      panel.add(label);
    } else {
      printFatal("ERROR: makeGap: Invalid panel type for : " + panelname);
    }
  }
  
  /**
   * This creates a JLabel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component (optional)
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientation on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the label
   */
  public JLabel makeLabel(String panelname, String name, Orient pos, boolean end, String title) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (name != null && !name.isEmpty() && gLabel.containsKey(name)) {
      printFatal("ERROR: '" + name + "' label already added to container!");
    }

    // create the component
    JLabel widget = new JLabel(title);

    JPanel panel;
    GridBagLayout gridbag;
    Component cpanel = getSelectedPanel(panelname);
    if (cpanel == null) {
      gridbag = mainLayout;
      mainFrame.add(widget);
    } else if (cpanel instanceof JPanel) {
      panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      panel.add(widget);
    } else {
      printFatal("ERROR: makeLabel: Invalid panel type: " + cpanel.getClass().getName());
      return null;
    }

    // set the layout of the component in the container
    gridbag.setConstraints(widget, setGbagConstraints(pos, end, Expand.NONE));
    
    // add entry to components list
    if (name != null && !name.isEmpty()) {
      saveComponent(name, widget);
    }
    
    return widget;
  }

  /**
   * This creates a JLabel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component (optional)
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientation on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param font    - font selection (String family, int Font.BOLD / Font.ITALIC, int point)
   * @param color   - text color
   * @return the label
   */
  public JLabel makeLabel(String panelname, String name, Orient pos, boolean end, String title, 
                          Font font, FontInfo.TextColor color) {
    JLabel label = makeLabel(panelname, name, pos, end, title);
    label.setForeground(FontInfo.getFontColor(color));
    if (font != null) {
      label.setFont(font);
    }
    return label;
  }
  
  /**
   * This creates a JLabel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   */
  public void makePlaceholder(String panelname, Orient pos, boolean end) {
    makeLabel(panelname, "", pos, end, "    ");
  }
  
  /**
   * This creates a JButton and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the button widget
   */
  public JButton makeButton(String panelname, String name, Orient pos, boolean end, String title) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gButton.containsKey(name)) {
      printFatal("ERROR: '" + name + "' button already added to container!");
    }

    // create the component
    JButton widget = new JButton(title);

    JPanel panel;
    GridBagLayout gridbag;
    Component cpanel = getSelectedPanel(panelname);
    if (cpanel == null) {
      gridbag = mainLayout;
      mainFrame.add(widget);
    } else if (cpanel instanceof JPanel) {
      panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      panel.add(widget);
    } else {
      printFatal("ERROR: makeLabel: Invalid panel type: " + cpanel.getClass().getName());
      return null;
    }

    // set the layout of the component in the container
    gridbag.setConstraints(widget, setGbagConstraints(pos, end, Expand.NONE));
    
    // add entry to components list
    saveComponent(name, widget);
    return widget;
  }

  /**
   * This creates a JCheckBox and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param value   - 0 to have checkbox initially unselected, any other value for selected
   * @return the checkbox widget
   */
  public JCheckBox makeCheckbox(String panelname, String name, Orient pos, boolean end, String title,
         int value) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gCheckbox.containsKey(name)) {
      printFatal("ERROR: '" + name + "' checkbox already added to container!");
    }

    // create the component
    JCheckBox widget = new JCheckBox(title);
    widget.setSelected(value != 0);

    JPanel panel;
    GridBagLayout gridbag;
    Component cpanel = getSelectedPanel(panelname);
    if (cpanel == null) {
      gridbag = mainLayout;
      mainFrame.add(widget);
    } else if (cpanel instanceof JPanel) {
      panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      panel.add(widget);
    } else {
      printFatal("ERROR: makeLabel: Invalid panel type: " + cpanel.getClass().getName());
      return null;
    }

    // set the layout of the component in the container
    gridbag.setConstraints(widget, setGbagConstraints(pos, end, Expand.NONE));
    
    // add entry to components list
    saveComponent(name, widget);
    return widget;
  }

  /**
   * This creates a JRadioButton and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param value   - 0 to have checkbox initially unselected, any other value for selected
   * @return the checkbox widget
   */
  public JRadioButton makeRadiobutton(String panelname, String name, Orient pos, boolean end,
        String title, int value) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gRadiobutton.containsKey(name)) {
      printFatal("ERROR: '" + name + "' radiobutton already added to container!");
    }

    // create the component
    JRadioButton widget = new JRadioButton(title);
    widget.setSelected(value != 0);

    JPanel panel;
    GridBagLayout gridbag;
    Component cpanel = getSelectedPanel(panelname);
    if (cpanel == null) {
      gridbag = mainLayout;
      mainFrame.add(widget);
    } else if (cpanel instanceof JPanel) {
      panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      panel.add(widget);
    } else {
      printFatal("ERROR: makeLabel: Invalid panel type: " + cpanel.getClass().getName());
      return null;
    }

    // set the layout of the component in the container
    gridbag.setConstraints(widget, setGbagConstraints(pos, end, Expand.NONE));
    
    // add entry to components list
    saveComponent(name, widget);
    return widget;
  }

  /**
   * This creates a JTextField and places it in the container.
   * These are single line String displays.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param value   - 0 to have checkbox initially unselected, any other value for selected
   * @param length  - length of text field in chars
   * @param writable - true if field is writable by user, false if display only
   * @return the checkbox widget
   */
  public JTextField makeTextField(String panelname, String name, Orient pos, boolean end,
                String value, int length, boolean writable) {
    
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gTextField.containsKey(name)) {
      printFatal("ERROR: '" + name + "' textfield already added to container!");
    }

    // create the component
    Dimension size = new Dimension(length * 8, 25); // assume 6 pixels per char
    JTextField widget = new JTextField();
    widget.setText(value);
    widget.setPreferredSize(size);
    widget.setMinimumSize(size);
    widget.setEditable(writable);

    JPanel panel = null;
    GridBagLayout gridbag;
    Component cpanel = getSelectedPanel(panelname);
    if (cpanel == null) {
      gridbag = mainLayout;
      mainFrame.add(widget);
    } else if (cpanel instanceof JPanel) {
      panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      panel.add(widget);
    } else {
      printFatal("ERROR: makeLabel: Invalid panel type: " + cpanel.getClass().getName());
      return null;
    }

    // set the layout of the component in the container
    GridBagConstraints c = setGbagConstraints(pos, end, Expand.NONE);
    gridbag.setConstraints(widget, c);

    // add entry to components list
    saveComponent(name, widget);
    return widget;
  }

  /**
   * This creates a JComboBox and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the combo widget
   */
  public JComboBox makeCombobox(String panelname, String name, Orient pos, boolean end) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gCombobox.containsKey(name)) {
      printFatal("ERROR: '" + name + "' combobox already added to container!");
    }

    // create the component
    JComboBox widget = new JComboBox();

    JPanel panel = null;
    GridBagLayout gridbag;
    Component cpanel = getSelectedPanel(panelname);
    if (cpanel == null) {
      gridbag = mainLayout;
      mainFrame.add(widget);
    } else if (cpanel instanceof JPanel) {
      panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      panel.add(widget);
    } else {
      printFatal("ERROR: makeLabel: Invalid panel type: " + cpanel.getClass().getName());
      return null;
    }

    // set the layout of the component in the container
    GridBagConstraints c = setGbagConstraints(pos, end, Expand.NONE);
    gridbag.setConstraints(widget, c);
    
    // add entry to components list
    saveComponent(name, widget);
    return widget;
  }
  
  /**
   * this sets the combobox selections to the specified list of values.
   * it initializes the selection to the 1st entry
   * 
   * @param widget - name of the combobox widget to set
   * @param list   - the list of entries for it
   */
  public void setComboboxSelections(String widget, ArrayList<String> list) {
    JComboBox combobox = getCombobox(widget);
    if (combobox == null) {
      return;
    }

    combobox.removeAllItems();
    for (int ix = 0; ix < list.size(); ix++) {
      String entry = list.get(ix);
      combobox.addItem(entry);
    }

    // init class selection to 1st item
    if (list.isEmpty()) {
      printError("ERROR: setComboboxSelections <" + widget + "> - list is null");
    } else {
      combobox.setSelectedIndex(0);
    }
  }
  
  /**
   * This creates an integer JSpinner and places it in the container.
   * Step size (increment/decrement value) is always set to 1.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param minval  - the min range limit for the spinner
   * @param maxval  - the max range limit for the spinner
   * @param step    - step size for the spinner
   * @param curval  - the current value for the spinner
   * @return the spinner widget
   */
  public JSpinner makeSpinner(String panelname, String name, Orient pos, boolean end,
          int minval, int maxval, int step, int curval) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gSpinner.containsKey(name)) {
      printFatal("ERROR: '" + name + "' spinner already added to container!");
    }

    // create the component
    JSpinner widget = new JSpinner();
    widget.setModel(new SpinnerNumberModel(curval, minval, maxval, step));
    // this size will haldle 10 digits plus sign, enough to cover any 32-bit value
    widget.setPreferredSize(new Dimension(110, 23));

    JPanel panel = null;
    GridBagLayout gridbag;
    Component cpanel = getSelectedPanel(panelname);
    if (cpanel == null) {
      gridbag = mainLayout;
      mainFrame.add(widget);
    } else if (cpanel instanceof JPanel) {
      panel = (JPanel) cpanel;
      gridbag = (GridBagLayout) panel.getLayout();
      panel.add(widget);
    } else {
      printFatal("ERROR: makeLabel: Invalid panel type: " + cpanel.getClass().getName());
      return null;
    }

    // set the layout of the component in the container
    GridBagConstraints c = setGbagConstraints(pos, end, Expand.NONE);
    gridbag.setConstraints(widget, c);
    
    // add entry to components list
    saveComponent(name, widget);
    return widget;
  }

  public void resizePanelHeight(String panelname, int height) {
    PanelInfo panelInfo = getPanelInfo(panelname);
    if (panelInfo != null) {
      Dimension size = panelInfo.panel.getSize();
      if (size.height != height) {
        size.height = height;
        panelInfo.panel.setPreferredSize(size);
        panelInfo.panel.setMinimumSize(size);
      }
    }
  }
  
  public void resizePanelWidth(String panelname, int width) {
    PanelInfo panelInfo = getPanelInfo(panelname);
    if (panelInfo != null) {
      Dimension size = panelInfo.panel.getSize();
      if (size.width != width) {
        size.width = width;
        panelInfo.panel.setPreferredSize(size);
        panelInfo.panel.setMinimumSize(size);
      }
    }
  }
  
  public JTable makeRawTable(String name) {
    if (gTable.containsKey(name)) {
      printFatal("ERROR: '" + name + "' table already exists!");
    }

    // create the table and save it to list
    JTable table = new JTable();
    saveComponent(name, table);

    return table;
  }
  
  public JTextPane makeRawTextPane(String name, String title) {
    if (gTextPane.containsKey(name)) {
      printFatal("ERROR: '" + name + "' textpanel already exists!");
    }

    // create a text pane component and add to scroll panel
    JTextPane textpane = new JTextPane();
    saveComponent(name, textpane);

    return textpane;
  }

  public JTextArea makeRawTextArea(String name, String title) {
    if (gTextPane.containsKey(name)) {
      printFatal("ERROR: '" + name + "' textpanel already exists!");
    }

    // create a text pane component and add to scroll panel
    JTextArea textarea = new JTextArea();
    saveComponent(name, textarea);

    return textarea;
  }

  /**
   * This creates an empty JPanel.
   * 
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget (null if no border)
   * @return the panel
   */
  public JPanel makeRawPanel(String name, String title) {
    if (gPanel.containsKey(name)) {
      printFatal("ERROR: '" + name + "' panel already added to container!");
    }

    // create the panel and apply constraints
    JPanel panel = new JPanel();
    if (title != null) {
      panel.setBorder(BorderFactory.createTitledBorder(title));
    }

    // create a layout for inside the panel
    GridBagLayout gbag = new GridBagLayout();
    panel.setFont(new Font("SansSerif", Font.PLAIN, 14));
    panel.setLayout(gbag);

    // add new panel info to list
    savePanel(name, panel);

    // add entry to components list
    return panel;
  }

  public JSplitPane makeRawSplitPanel(String name, boolean horiz, double divider) {
    // make sure split pane not already defined
    if (gPanel.containsKey(name)) {
      printFatal("ERROR: Split pane already has entry: " + name);
    }
    
    // create the pane
    int type;
    if (horiz) {
      type = JSplitPane.HORIZONTAL_SPLIT;
    } else {
      type = JSplitPane.VERTICAL_SPLIT;
    }
    JSplitPane panel = new JSplitPane(type);
    panel.setOneTouchExpandable(true);
    panel.setDividerLocation(divider);

    // add new panel info to list
    savePanel(name, panel);
    return panel;
  }

  public JTabbedPane makeRawTabbedPanel(String name) {
    if (gPanel.containsKey(name)) {
      printFatal("ERROR: '" + name + "' panel already added to container!");
    }

    // create the panel and apply constraints
    JTabbedPane panel = new JTabbedPane();

    // add new panel info to list
    savePanel(name, panel);
    return panel;
  }

  public JScrollPane makeRawScrollPanel(String name, Component comp) {
    if (gPanel.containsKey(name)) {
      printFatal("ERROR: '" + name + "' panel already added to container!");
    }

    // create the panel place it in a scroll pane
    JScrollPane scroll;
    if (comp != null) {
      scroll = new JScrollPane(comp);
    } else {
      scroll = new JScrollPane();
    }
    scroll.setBorder(BorderFactory.createTitledBorder(""));

    // add new scroll panel info
    savePanel(name, scroll);

    return scroll;
  }
  
  public JScrollPane makeRawScrollTable(String name) {
    if (gPanel.containsKey(name) || gTable.containsKey(name)) {
      printFatal("ERROR: '" + name + "' panel already added to container!");
    }

    // create the table place it in a scroll pane
    JTable table = new JTable();
    JScrollPane panel = new JScrollPane(table);
    panel.setBorder(BorderFactory.createTitledBorder(""));

    // add new panel info and associated table to list
    savePanel(name, panel);
    saveComponent(name, table);

    return panel;
  }
  
  /**
   * This creates a JScrollPane containing a JList of Strings (does not place it in container).
   * A List of String entries is passed to it that can be manipulated (adding and removing entries
   * that will be automatically reflected in the scroll pane.
   * 
   * @param name    - the name id of the component
   * @param list    - the list of entries to associate with the panel
   * @return the JScrollPane created
   */
  public JScrollPane makeRawScrollList(String name, DefaultListModel list) {
    if (gPanel.containsKey(name) || gList.containsKey(name)) {
      printFatal("ERROR: '" + name + "' scrolling list panel already added to container!");
    }

    // create the scroll panel and title
    JScrollPane panel = new JScrollPane();
    panel.setBorder(BorderFactory.createTitledBorder(""));

    // create a list component for the scroll panel and assign the list model to it
    JList scrollList = new JList();
    panel.setViewportView(scrollList);
    scrollList.setModel(list);

    // add new panel info and associated scroll list to list
    savePanel(name, panel);
    saveComponent(name, scrollList);

    return panel;
  }

  public JScrollPane makeRawScrollTextPane(String name) {
    if (gPanel.containsKey(name) || gTextPane.containsKey(name)) {
      printFatal("ERROR: '" + name + "' scrolling textpanel already added to container!");
    }

    // create the scroll panel and apply constraints
    JScrollPane panel = new JScrollPane();
    panel.setBorder(BorderFactory.createTitledBorder(""));

    // create a text pane component and add to scroll panel
    JTextPane textpane = new JTextPane();
    panel.setViewportView(textpane);
    
    // add new panel info and associated text pane to list
    savePanel(name, panel);
    saveComponent(name, textpane);

    return panel;
  }

  public JScrollPane makeRawScrollTextArea(String name) {
    if (gPanel.containsKey(name) || gTextPane.containsKey(name)) {
      printFatal("ERROR: '" + name + "' scrolling textpanel already added to container!");
    }

    // create the scroll panel and apply constraints
    JScrollPane panel = new JScrollPane();
    panel.setBorder(BorderFactory.createTitledBorder(""));

    // create a text pane component and add to scroll panel
    JTextArea textarea = new JTextArea();
    panel.setViewportView(textarea);
    
    // add new panel info and associated text pane to list
    savePanel(name, panel);
    saveComponent(name, textarea);

    return panel;
  }

  /**
   * This creates an empty JPanel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget (null if no border)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param fillStyle - NONE, HORIZONTAL, VERTICAL, BOTH
   * @return the panel
   */
  public JPanel makePanel(String panelname, String name, Orient pos, boolean end, String title, Expand fillStyle) {
    // create the panel
    JPanel panel = makeRawPanel(name, title);

    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, pos, end, fillStyle);

    // place component in container
    addPanelToPanel(panelname, name);
    return panel;
  }

  /**
   * This creates an empty JPanel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget (null if no border)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the panel
   */
  public JPanel makePanel(String panelname, String name, Orient pos, boolean end, String title) {
    // create the panel
    JPanel panel = makeRawPanel(name, title);

    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, pos, end, Expand.NONE);

    // place component in container
    addPanelToPanel(panelname, name);
    return panel;
  }

  /**
   * This creates an empty JPanel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget (null if no border)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param width   - desired width of panel
   * @param height  - desired height of panel
   * @return the panel
   */
  public JPanel makePanel(String panelname, String name, Orient pos, boolean end, String title,
                          int width, int height) {
    // create the panel
    JPanel panel = makeRawPanel(name, title);

    // determine if we want to fill container's width or height
    Dimension dim = getPanelSize(panelname);
    //Utils.printStatusInfo("Panel size = { " + dim.width + ", " + dim.height + " }");
    width  = (width == 0) ? dim.width : width;
    height = (height == 0) ? dim.height : height;

    Expand expand = Expand.NONE;
    if (width >= dim.width && height >= dim.height) {
      width = dim.width;
      height = dim.height;
      expand = Expand.BOTH;
    } else if (width >= dim.width) {
      width = dim.width;
      expand = Expand.HORIZONTAL;
    } else if (height >= dim.height) {
      height = dim.height;
      expand = Expand.VERTICAL;
    }

    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, pos, end, expand);

    // place component in container
    addPanelToPanel(panelname, name);
      
    // limit height and width to max of screen dimensions
    width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;
    height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;

    Dimension fsize = new Dimension(width, height);
    panel.setPreferredSize(fsize);
    panel.setMinimumSize(fsize);

    return panel;
  }

  public JSplitPane makeSplitPanel(String panelname, String name, Orient pos, boolean end,
                                  Expand expand, boolean horiz, double divider) {

    // create the split pane
    JSplitPane panel = makeRawSplitPanel(name, horiz, divider);
    
    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, pos, end, expand);

    // place split pane in container
    addPanelToPanel(panelname, name);
    return panel;
  }

  /**
   * This creates an empty JTabbedPanel and places it in the container.
   * 
   * @param panelname - the name of the container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @return the panel
   */
  public JTabbedPane makeTabbedPanel(String panelname, String name) {
    // create the panel
    JTabbedPane panel = makeRawTabbedPanel(name);

    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, Orient.NONE, true, Expand.BOTH);

    // place component in container
    addPanelToPanel(panelname, name);
    return panel;
  }

  /**
   * This creates an empty JTable and places it in the container.
   * 
   * @param panelname - the name of the container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @return the panel
   */
  public JTable makeScrollTable(String panelname, String name) {
    // create the scroll panel containing a JTable
    JScrollPane panel = makeRawScrollTable(name);
    
    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, Orient.NONE, true, Expand.BOTH);
    
    // place component in container
    addPanelToPanel(panelname, name);
    return getTable(name);
  }
  
  /**
   * This creates a JScrollPane containing a JList of Strings and places it in the container.
   * A List of String entries is passed to it that can be manipulated (adding and removing entries
   * that will be automatically reflected in the scroll pane.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param list    - the list of entries to associate with the panel
   * @return the JList corresponding to the list passed
   */
  public JList makeScrollList(String panelname, String name, DefaultListModel list) {
    // create the scroll panel and list
    JScrollPane panel = makeRawScrollList(name, list);
    
    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, Orient.NONE, true, Expand.BOTH);

    // place scroll panel in container
    addPanelToPanel(panelname, name);
    return getList(name);
  }

  /**
   * This creates a JScrollPane containing a JTextPane for text and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @return the text panel contained in the scroll panel
   */
  public JTextPane makeScrollTextPane(String panelname, String name) {
    // create the scroll panel containing the text pane
    JScrollPane panel = makeRawScrollTextPane(name);
    
    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, Orient.NONE, true, Expand.BOTH);

    // place scroll panel in container
    addPanelToPanel(panelname, name);
    return getTextPane(name);
  }

  /**
   * This creates a JScrollPane containing a JTextPane for text and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @return the text panel contained in the scroll panel
   */
  public JTextPane makeScrollTextArea(String panelname, String name) {
    // create the scroll panel containing the text pane
    JScrollPane panel = makeRawScrollTextArea(name);
    
    // setup layout for panel in the container
    setGridBagLayout(panelname, panel, Orient.NONE, true, Expand.BOTH);

    // place scroll panel in container
    addPanelToPanel(panelname, name);
    return getTextPane(name);
  }

  public void setSplitDivider(String splitname, double ratio) {
    // get the specified panel and verify it is a split pane
    PanelInfo pinfo = getPanelInfo(splitname);
    if (pinfo != null && pinfo.panel instanceof JSplitPane) {

      if (ratio >= 1.0 || ratio <= 0.0) {
        printError("ERROR: setSplitDivider - Invalid ratio for split divider: " + ratio + " (setting to 0.5)");
        ratio = 0.5;
      }

      JSplitPane splitpanel = (JSplitPane) pinfo.panel;
      splitpanel.setDividerLocation(ratio);
    } else {
      printFatal("ERROR: setSplitDivider - Split pane not found: " + splitname);
    }
  }

  public void addSplitComponent(String splitname, int index, String compname, Component panel, boolean scrollable) {
    // get the specified panel and verify it is a split pane
    PanelInfo pinfo = getPanelInfo(splitname);
    if (pinfo != null && pinfo.panel instanceof JSplitPane) {
      JSplitPane splitpanel = (JSplitPane) pinfo.panel;
      
      // the only valid values are 0 and 1
      index = index == 0 ? 0 : 1;
      
      // if scrollable, make a scroll pane and insert component in it
      Component compPanel = panel;
      if (scrollable) {
        compPanel = new JScrollPane(panel);
      }
    
      // add component to split pane
      splitpanel.add(compPanel, index);

      // add new panel info to list
      if (panel instanceof JTable ||
          panel instanceof JList ||
          panel instanceof JTextPane ||
          panel instanceof JTextArea) {
        saveComponent(compname, panel);
      } else {
        savePanel(compname, panel);
      }
    } else {
      printFatal("ERROR: addSplitComponent - Split pane not found: " + splitname);
    }
  }

  public static JFrame makeFrameWithText(JFrame frame, String title, String text, int width, int height) {
    // define size of panel
    Dimension dim = new Dimension(width, height);

    // create a text panel component and place the text message in it
    JTextArea tpanel = new JTextArea();
    tpanel.setText(text);

    // create the scroll panel and place text panel in it
    JScrollPane spanel = new JScrollPane(tpanel);
    spanel.setBorder(BorderFactory.createEmptyBorder());

    // apply constraints
    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(GAPSIZE, GAPSIZE, GAPSIZE, GAPSIZE);
    c.fill = GridBagConstraints.BOTH;
    c.gridwidth  = GridBagConstraints.REMAINDER;
    // since only 1 component, these both have to be non-zero for grid bag to work
    c.weightx = 1.0;
    c.weighty = 1.0;
    gridbag.setConstraints(spanel, c);

    // if frame not supplied, create it
    if (frame == null) {
      frame = new JFrame();
      frame.setPreferredSize(dim);
      frame.setMinimumSize(dim);
      frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      frame.setLocationRelativeTo(null);
    }
    
    // now add title & put scroll panel in it
    frame.setTitle(title);
    frame.setContentPane(spanel);
    frame.setVisible(true);
    return frame;
  }

}
