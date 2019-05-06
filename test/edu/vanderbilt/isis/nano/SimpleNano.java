package edu.vanderbilt.isis.nano;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import fi.iki.elonen.NanoHTTPD;

public class SimpleNano extends NanoHTTPD {

    public SimpleNano() throws IOException {
        super(8080);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nServer running on http://localhost:8080/\n");
    }

    public static void main(String[] args) {
        try {
            new SimpleNano();
        } catch (IOException e) {
            System.err.println("Error starting nano server:\n" + e);
        }
    }

    private static String encapsulateMessage(String msg) {
        return "<html><body>" + "<h1>" + msg + "</h1>\n" + "</body></html>\n";
    }
    
    @Override
    public Response serve(IHTTPSession session) {
      Map<String, String> files = new HashMap<String, String>();
      Method method = session.getMethod();
      String response = "completed";
      int value = 4;

      System.out.println("Method received was type: " + method.toString());

      if (Method.PUT.equals(method) || Method.POST.equals(method)) {
          try {
              session.parseBody(files);
          } catch (IOException ioe) {
              return newFixedLengthResponse(encapsulateMessage("SERVER INTERNAL ERROR: IOException: " + ioe.getMessage()));
          } catch (ResponseException re) {
              return newFixedLengthResponse(encapsulateMessage("SERVER INTERNAL ERROR: ResponseException: " + re.getMessage()));
          }
          
          // get the POST body
          String postBody = session.getQueryParameterString();
          // get the POST request's parameters
          //String postParameter = session.getParms().get("parameter");
          System.out.println(method.toString() + " received: " + postBody);
          response = "Hello, " + postBody;

          try {
              value = Integer.parseInt(postBody);
          } catch (NumberFormatException ex) {
              // ignore
          }
      }

      testSymbolic(value);

      return newFixedLengthResponse(encapsulateMessage(response));
    }

    public static void testSymbolic(int y) {
        int x;
        x = -4;
        if (x + y == 0) {
            System.out.println("Sum was 0");
        } else {
            System.out.println("Sum was not 0!");
        }
    }
}
