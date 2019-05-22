/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import java.util.ArrayList;

/**
 *
 * @author dmcd2356
 */
public class JsonMethod {
  
  public String  fullName;          // full name of method (package, class, method + signature)
  public ArrayList<String> parent;  // list of caller methods (full names)

  public JsonMethod(String fullname, ArrayList<String> plist) {
    this.fullName = fullname;
    this.parent = new ArrayList<>(plist);
  }

}
