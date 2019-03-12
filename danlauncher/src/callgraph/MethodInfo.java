/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

//import com.google.gson.annotations.Expose;
import java.util.ArrayList;
import java.util.HashMap;
import util.Utils;

/**
 *
 * @author dmcd2356
 */
public class MethodInfo {
  private String  fullName;       // full name of method (package, class, method + signature)
  private String  className;      // class name (no package info or method name)
  private String  methName;       // method name (no class info)
  private ArrayList<String> parent; // list of caller methods (full names - for compatibility with ImportGraph)
  private ArrayList<Integer> threadId; // the list of threads it was run in
  private HashMap<Integer, ThreadInfo> threadInfo; // the info for each individual thread
  private ThreadInfo total;        // contains the stats for all threads combined

  private ThreadInfo getThreadInfo(int tid) {
    if (tid < 0) {
      return null;
    }

    return threadInfo.get(tid);
  }
  
  public MethodInfo(int tid, String method, String parent, long tstamp, int insCount, int line) {
    this.parent = new ArrayList<>();
    threadId = new ArrayList<>();
    total = new ThreadInfo(tid, tstamp, insCount, line, parent);
    if (threadInfo == null) {
      threadInfo = new HashMap<>();
    }
    if (tid >= 0 && !threadId.contains(tid)) {
      ThreadInfo entry = new ThreadInfo(tid, tstamp, insCount, line, parent);
      threadId.add(tid);
      threadInfo.put(tid, entry);
    }

    fullName = className = methName = "";
    if (method != null && !method.isEmpty()) {
      // fullName should be untouched - it is used for comparisons
      fullName = method;
      String cleanName = method.replace("/", ".");
      if (cleanName.contains("(")) {
        cleanName = cleanName.substring(0, cleanName.lastIndexOf("("));
      }
      if (!cleanName.contains(".")) {
        methName = cleanName;
      } else {
        methName = cleanName.substring(cleanName.lastIndexOf(".") + 1);
        className = cleanName.substring(0, cleanName.lastIndexOf("."));
        if (className.contains(".")) {
          className = className.substring(className.lastIndexOf(".") + 1);
        }
      }
    }
  }
  
  public void convert() {
    // these should be the only valid entries read
    String cleanName = fullName;
    ArrayList<String> parentList = parent;

    // let's get the 1st parent entry (if any) and set the thread id to 0, since we have no idea
    int tid = 0;
    String firstParent = null;
    if (parentList != null && !parentList.isEmpty()) {
      firstParent = parentList.get(0);
    }

    // the thread list will only contain the 1 thread id
    threadId = new ArrayList<>();
    threadId.add(tid);

    // now setup the links to the parents
    total = new ThreadInfo(tid, 0, 0, 0, firstParent);
    for (int pix = 1; pix < parent.size(); pix++) {
      total.addParent(parent.get(pix));
    }
    threadInfo = new HashMap<>();
    threadInfo.put(tid, total);
    
    // extract method and class names from fullName
    className = methName = "";
    if (cleanName != null && !cleanName.isEmpty()) {
      cleanName = cleanName.replace("/", ".");
      if (cleanName.contains("(")) {
        cleanName = cleanName.substring(0, cleanName.lastIndexOf("("));
      }
      if (!cleanName.contains(".")) {
        methName = cleanName;
      } else {
        methName = cleanName.substring(cleanName.lastIndexOf(".") + 1);
        className = cleanName.substring(0, cleanName.lastIndexOf("."));
        if (className.contains(".")) {
          className = className.substring(className.lastIndexOf(".") + 1);
        }
      }
    }
  }
  
  public void repeatedCall(int tid, String parent, long tstamp, int insCount, int line) {
    if (tid < 0) {
      return;
    }

    // get the method info for the specified thread id
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      // this is 1st time this method was called on this thread - create a new entry for it
      tinfo = new ThreadInfo(tid, tstamp, insCount, line, parent);
      threadInfo.put(tid, tinfo);
      threadId.add(tid);
    } else {
      // if found, reset the reference values for elapsed time and instruction counts,
      // increment the # times called, and add the parent if not already listed
      tinfo.resetReference(tstamp, insCount);
      tinfo.incCount();
      if (!tinfo.parents.contains(parent)) {
        tinfo.addParent(parent);
      }
    }
    
    // update total info for method
    total.incCount();
    if (!total.parents.contains(parent)) {
      total.addParent(parent);
    }
  }
  
  public void exit(int tid, long tstamp, int insCount) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      long elapsed = tinfo.getElapsedTime(tstamp);
      int  insdelta = tinfo.getElapsedCount(insCount);
      
      // update elapsed time and instruction count for specified thread
      tinfo.exit(elapsed, insdelta);

      // update the total elapsed and instructions
      total.exit(elapsed, insdelta);
    }
  }
  
  public void setExecption(int tid, int line, String exception) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      tinfo.addException(line, exception);
    }
    total.addException(line, exception);
  }
  
  public void setError(int tid, int line, String error) {
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo != null) {
      tinfo.addError(line, error);
    }
    total.addError(line, error);
  }
  
  public String getFullName() {
    return fullName;
  }
  
  public String getClassAndMethod() {
    return className.isEmpty() ? methName : className + "." + methName;
  }
  
  public String getCGName() {
    return className.isEmpty() ? methName : className + Utils.NEWLINE + methName;
  }
  
  public String getClassName() {
    return fullName.substring(0, fullName.lastIndexOf("."));
  }

  public String getMethodName() {
    return methName;
  }

  public String getMethodSignature() {
    int offset = fullName.indexOf("(");
    if (offset <= 0) {
      return "";
    }
    return fullName.substring(offset);
  }

  public ArrayList<Integer> getThread() {
    return threadId;
  }
  
  public ArrayList<String> getParents(int tid) {
    if (tid < 0) {
      return total.parents;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return new ArrayList<>();
    }
    return tinfo.parents;
  }
  
  public int getInstructionCount(int tid) {
    if (tid < 0) {
      return total.instrCount;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.instrCount;
  }
  
  public boolean isReturned(int tid) {
    if (tid < 0) {
      return total.exit;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return false;
    }
    return tinfo.exit;
  }
  
  public int getCount(int tid) {
    if (tid < 0) {
      return total.callCount;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.callCount;
  }
  
  public ArrayList<String> getExecption(int tid) {
    if (tid < 0) {
      return total.exceptions;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return null;
    }
    return tinfo.exceptions;
  }
  
  public ArrayList<String> getError(int tid) {
    if (tid < 0) {
      return total.errors;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return null;
    }
    return tinfo.errors;
  }
  
  public int getFirstLine(int tid) {
    if (tid < 0) {
      return total.lineFirst;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.lineFirst;
  }
  
  public long getDuration(int tid) {
    if (tid < 0) {
      return total.duration_ms;
    }
    ThreadInfo tinfo = getThreadInfo(tid);
    if (tinfo == null) {
      return 0;
    }
    return tinfo.duration_ms;
  }

}
