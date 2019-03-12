package danalyzer.instrumenter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 *
 * @author zzk
 */
public class Writer {
  public static void writeClasses(String path, Collection<ClassNode> clsNodeSet) {
    String className = null;
    int lastDot = path.lastIndexOf('.');
    String newPath = path.substring(0, lastDot) + "-dan-ed" + path.substring(lastDot);
    //File oldFile = new File(path);
    //File newFile = new File(renamePath);
    //oldFile.renameTo(newFile);
    
    try {
      JarOutputStream newJar = new JarOutputStream(new FileOutputStream(newPath));
      
      // copy things other than class files from the original jar file
      JarFile oldJar = new JarFile(path);
      Enumeration enumEnt = oldJar.entries();
      while (enumEnt.hasMoreElements()) {
        JarEntry entry = (JarEntry)enumEnt.nextElement();
        if (entry.getName().endsWith(".class")) {
          continue;
        }
        newJar.putNextEntry(new JarEntry(entry));
        InputStream inStream = oldJar.getInputStream(entry);
        byte[] bytes = IOUtils.toByteArray(inStream);
        newJar.write(bytes);
        newJar.closeEntry();
      }
      oldJar.close();
      
      // write instrumented class files
      for (ClassNode clsNode : clsNodeSet) {
        className = clsNode.name + ".class";
        JarEntry entry = new JarEntry(className);
        newJar.putNextEntry(new JarEntry(entry));
        // use automatical frame and max computation!
        // although ASM documentation says "COMPUTE_FRAMES implies COMPUTE_MAXS", 
        // we use COMPUTE_MAXS, since ClassWriter.getCommonSuperClass() throws exceptions sometimes
        ClassWriter clsWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        clsNode.accept(clsWriter);
        byte[] bytes = clsWriter.toByteArray();
        newJar.write(bytes);
        newJar.closeEntry();
      }
      
      // make sure we are running from instrumented jar file
      File dan = new File(Writer.class.getProtectionDomain().
          getCodeSource().getLocation().getPath());
      if (!dan.isFile()) {
        System.err.println("Must be run from jar file to be able to extract symbolic executor");
        return;
      }

      // this adds the danalyzer's symbolic Executor and GUI classes
//      JarFile danJar = new JarFile(dan);
//      enumEnt = danJar.entries();
//      while (enumEnt.hasMoreElements()) {
//        JarEntry entry = (JarEntry)enumEnt.nextElement();
//        String entryName = entry.getName();
//        if (entryName.startsWith("danalyzer/executor") ||
//            entryName.startsWith("danalyzer/gui") ||
//            entryName.startsWith("org/apache/commons") ||   // commons-io library
//            entryName.startsWith("org/objectweb/asm") ||    // ASM library
//            entryName.startsWith("com/google") ||           // guava library
//            entryName.startsWith("com/microsoft/z3")) {     // z3 library
//          newJar.putNextEntry(new JarEntry(entry));
//          InputStream inStream = danJar.getInputStream(entry);
//          byte[] bytes = IOUtils.toByteArray(inStream);
//          newJar.write(bytes);
//          newJar.closeEntry();
//        }
//      }
//      danJar.close();
      
      newJar.close();
    } catch (IOException | RuntimeException ex) {
      if (className != null) {
        System.err.println("ERROR on :" + className);
      }
      System.err.println(ex.getMessage());
    }
  }
}
