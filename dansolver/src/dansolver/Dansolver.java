package dansolver;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.ArraySort;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.FuncInterp;
import com.microsoft.z3.FuncInterp.Entry;
import com.microsoft.z3.Model;
import com.microsoft.z3.Params;
import com.microsoft.z3.Pattern;
import com.microsoft.z3.Quantifier;
import com.microsoft.z3.SeqSort;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Sort;
import com.microsoft.z3.Status;
import com.microsoft.z3.enumerations.Z3_decl_kind;
import com.microsoft.z3.enumerations.Z3_parameter_kind;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.sun.org.apache.bcel.internal.generic.AALOAD;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class Dansolver {
  private static Context                     z3Context;
  private static Optimize                    z3Optimize;
  
  private static NetworkServer               server;
  private static SolverThread                solver;
  private static long                        solvedCount;
  private static MongoClient                 mongoClient;
  private static MongoDatabase               database;
  public  static MongoCollection<Document>   collection;
  
  public Dansolver(int port) {
    mongoClient = MongoClients.create();
    database = mongoClient.getDatabase("mydb");
    collection = database.getCollection("dsedata");
    
    HashMap<String, String> map = new HashMap<>();
    map.put("model_compress", "false"); // for reading arrays
    z3Context = new Context(map);
    z3Optimize = z3Context.mkOptimize();

    try {
      server = new NetworkServer(port);
      solver = new SolverThread();
    } catch (IOException ex) {
      System.err.println("ERROR: creating network server: " + ex.getMessage());
      System.exit(1);
    }
  }
  
  private void start() {
    // start the network server for reading input from danalyzer and the solver threads
    solver.start();
    server.start();
  }

  public static long documentCount() {
    return collection.countDocuments();
  }
  
  public static void documentInsert(Document mongoDoc) {
    Dansolver.collection.insertOne(mongoDoc);
  }
  
  private static void documentAmend(Document mongoDoc) {
    collection.replaceOne((Bson) new BasicDBObject("_id", (ObjectId)mongoDoc.get("_id")), mongoDoc);
  }
  
  public static void documentClearAll() {
    collection.deleteMany(new Document());
    System.out.println("Cleared all documents from database");
    solvedCount = 0;
  }
  
  public static void documentClearExcept(int conn) {
    List<Document> list = collection.find().noCursorTimeout(true).sort((Bson) new BasicDBObject("_id", 1))
          .into(new ArrayList<Document>());
    for (Document mongoDoc : list) {
      Integer connection = mongoDoc.getInteger("connection");
      if (connection != conn) {
        collection.deleteOne(new BasicDBObject("connection", connection));
      }
    }
    System.out.println("Cleared all documents except connection " + conn);
    solvedCount = 0; // even though it's not 0, run() will determine correctly on next pass
  }
  
  private class SolverThread extends Thread {
    /**
     * the run process for solving the constraints in the database
     */
    @Override
    public void run() {
      // get the iterator for the database (sort based on _id, which will place entries in order)
      System.out.println("Starting solver thread");
      FindIterable<Document> iterdocs = collection.find().noCursorTimeout(true).sort((Bson) new BasicDBObject("_id", 1));

      // have to iterate because the database may be constantly added to
      while (true) {
        solvedCount = 0;
        for(Document mongoDoc : iterdocs) {
          // skip if entry has already been solved
          if (mongoDoc.getBoolean("solvable") != null) {
            ++solvedCount;
            continue;
          }
        
          // skip if no constraint (error)
          String constraint = mongoDoc.getString("constraint");
          if (constraint == null) {
            ++solvedCount;
            System.out.println("ERROR: Invalid or missing constraint in collection!");
            continue;
          }

          Integer connection = mongoDoc.getInteger("connection");
          Integer index = mongoDoc.getInteger("index");
        
          System.out.println("Solving connection " + connection + " count: " + index);
              // + "\n" + constraint + "\n");

          //BoolExpr expr = z3Context.parseSMTLIB2String(constraint, null, null, null, null)[0];
        
          // update the document entry with the "solvable" and "solution" entries
          z3Optimize.Push();
          z3Optimize.fromString(constraint);
          check(mongoDoc);
          ++solvedCount;
          z3Optimize.Pop();
        }
        
        // this will check if any new docs were added. if not, just wait on NetworkServer for more.
        long newEntries;
        try {
          newEntries = server.getDocumentCount(solvedCount) - solvedCount;
          System.out.println(newEntries + " new documents, restarting database search...");
        } catch (InterruptedException ex) {
          System.err.println(ex.getMessage());
        }
      }
    }

    private void check(Document doc) {
      long startTime = System.currentTimeMillis();
      //Solver s = z3Context.mkSolver();
      //s.add(formula);
      BoolExpr assump[] = new BoolExpr[0]; // no assumptions here
      Status status = z3Optimize.Check(assump);

      if (status == Status.UNSATISFIABLE) {
        // update the document entry
        doc = doc.append("solvable", false);
        doc = doc.append("calctime", (int) (System.currentTimeMillis() - startTime) / 1000);

        collection.replaceOne((Bson) new BasicDBObject("_id", (ObjectId)doc.get("_id")), doc);
        System.out.println("Constraints not satisfiable");
        return;
      }
      
      if (status != Status.SATISFIABLE) {
        System.err.println("Constraints unknown");
        return;
      }
      
      Model model = z3Optimize.getModel();

      // the constraint was solvable,
      // there may be multiple parameters in the solution. we will place them all in an List
      doc = doc.append("solvable", true);

      List<Document> solutions = new ArrayList<>();
      for (FuncDecl fd : model.getConstDecls()) {
        String paramName = fd.getName().toString().replace('.', '/');
        // arrays
        if (fd.getDeclKind() == Z3_decl_kind.Z3_OP_AS_ARRAY &&
            fd.getNumParameters() == 1 &&
            fd.getParameters()[0].getParameterKind() == Z3_parameter_kind.Z3_PARAMETER_FUNC_DECL) {
          FuncDecl arrayInter = fd.getParameters()[0].getFuncDecl();
          System.out.println("Array interpretation: " + model.getFuncInterp(arrayInter));
        } else if (fd.getDeclKind() == Z3_decl_kind.Z3_OP_UNINTERPRETED) {
          if (fd.getRange() instanceof ArraySort) {
            ArraySort arraySort = (ArraySort)fd.getRange();
            Sort arrDomain = arraySort.getDomain();
            Sort arrRange = arraySort.getRange();
            if (arrDomain instanceof BitVecSort && arrRange  instanceof BitVecSort) {
              Expr arrayEval = model.eval(fd.apply(), true);
              FuncDecl inner = arrayEval.getFuncDecl();
              HashMap<String, String> arrayVals = new HashMap<>();
              
              while (inner.getDeclKind() == Z3_decl_kind.Z3_OP_STORE) {
                arrayVals.put(arrayEval.getArgs()[1].toString(), arrayEval.getArgs()[2].toString());
                arrayEval = arrayEval.getArgs()[0];
                inner = arrayEval.getFuncDecl();
              }

              if (inner.getDeclKind() == Z3_decl_kind.Z3_OP_CONST_ARRAY) {
                arrayVals.put("else", arrayEval.getArgs()[0].toString());
              } else if (inner.getDeclKind() == Z3_decl_kind.Z3_OP_AS_ARRAY) {
                // check parameters
                if (inner.getNumParameters() == 1 &&
                    inner.getParameters()[0].getParameterKind() == Z3_parameter_kind.Z3_PARAMETER_FUNC_DECL) {
                  FuncDecl arrayInter = fd.getParameters()[0].getFuncDecl();
                  System.out.println("Array interpretation: " + model.getFuncInterp(arrayInter));
                  // TODO: put these values into arrayVals
                }
              }
              
              System.out.println("Values in array:");
              for (Map.Entry<String, String> entry : arrayVals.entrySet()) {
                System.out.println("  [" + entry.getKey() + "]: " + entry.getValue());
              }

              doc = doc.append(fd.getName().toString(), arrayVals);
            }
          } else if (fd.getRange() instanceof BitVecSort && fd.getDomainSize() == 0) {
            // add the Integral solution
            Expr expr = model.getConstInterp(fd);
            String eval = model.eval(expr, false).toString();
            System.out.println("Solution: " + fd.getName() + ", value = " + eval);
            solutions.add(new Document("type", "integral")
                               .append("name", paramName)
                               .append("value", eval));
          } else if (fd.getRange() instanceof SeqSort && fd.getDomainSize() == 0) {
            // add the String solution
            Expr expr = model.getConstInterp(fd);
            String eval = model.eval(expr, false).toString();
            System.out.println("Solution: " + fd.getName() + ", value = " + eval);
            solutions.add(new Document("type", "string")
                               .append("name", paramName)
                               .append("value", eval));
          } else if (fd.getRange() instanceof com.microsoft.z3.RealSort && fd.getDomainSize() == 0) {
            // add the String solution
            Expr expr = model.getConstInterp(fd);
            String eval = model.eval(expr, false).toString();
            System.out.println("Solution: " + fd.getName() + ", value = " + eval);
            solutions.add(new Document("type", "real")
                               .append("name", paramName)
                               .append("value", eval));
          } else {
            System.out.println("Don't know how to handle uninterpreted function with range " +
                fd.getRange().toString() + ".");
          }
        } else {
          System.err.println("Don't know how to handle FuncDecl of kind " + fd.getDeclKind());
        }
        
        if (!solutions.isEmpty()) {
          doc = doc.append("solution", solutions);
        }
      }

      // indicate amount of time used to solve
      doc = doc.append("calctime", (int) (System.currentTimeMillis() - startTime) / 1000);

      // update the document entry
      documentAmend(doc);
    }        
  }
  
  public static void main(String[] args) {
    int port = 0;
    if (args.length > 0) {
      try {
        port = Integer.parseUnsignedInt(args[0]);
      } catch (NumberFormatException ex) {
        /* ignore */
      }
    }
    if (port < 1 || port > 65535) {
      port = 4000;
      System.err.println("Unspecified or invalid server port selection - using default: " + port);
    }

    Dansolver dansolver = new Dansolver(port);
    dansolver.start();
  }
    
}
