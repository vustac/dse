package danalyzer.instrumenter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 *
 * @author zzk
 */
public class Reader {
  public static Map<String, ClassNode> readClasses(String path) {    
    Map<String, ClassNode> clsNodeMap = new HashMap<>();
    
    Set<InputStream> inStreamSet = new HashSet<>();
    try {
      JarFile jar = new JarFile(path);
      Enumeration enumEnt = jar.entries();
      while (enumEnt.hasMoreElements()) {
        JarEntry entry = (JarEntry)enumEnt.nextElement();
        if (!entry.getName().endsWith(".class")) {
          continue;
        }
        InputStream inStream = jar.getInputStream(entry);
        inStreamSet.add(inStream);
      }
    } catch (IOException ex) {
      Logger.getLogger(Reader.class.getName()).log(Level.SEVERE, null, ex);
    }
    
    for (InputStream inStream : inStreamSet) {
      try {
        ClassNode clsNode = new ClassNode();
        ClassReader clsReader = new ClassReader(inStream);
        clsReader.accept(clsNode, 0);
        String clsName = clsNode.name;
        clsNodeMap.put(clsName, clsNode);
      } catch (IOException ex) {
        Logger.getLogger(Reader.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    
    return clsNodeMap;
  }
}
