package edu.vanderbilt.isis.paper_example;

import java.io.IOException;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD;

public class SimpleServer extends NanoHTTPD {

  public SimpleServer() throws IOException {
    super(8080);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    System.out.println("Server running on http://localhost:8080/");
  }
  
  public static void main(String[] args) {
    try {
      new SimpleServer();
    } catch (IOException e) {
      System.err.println("Error starting nano server:\n" + e);
    }
  }

  @Override
  public Response serve(IHTTPSession session) {
    Map<String, String> parms = session.getParms();
    String res;
    String a = parms.get("a");
    String b = parms.get("b");
    if (a != null && b != null) {
      int i_a = Integer.parseInt(a);
      int i_b = Integer.parseInt(b);
      res = getResponse(i_a, i_b);
    } else {
      res = "<html><body><h1>Missing arguments!</h1>\n";
    }

    return newFixedLengthResponse(res + "</body></html>\n");
  }
  
  public String getResponse(int a, int b) {
     int x = 1 , y = 0;
     if (a != 0) {
       y = 3 + x;
       if (b == 0)
	 x = 2 * (a + b);
     }
     if (x - y == 0)
       return "Error!";
     else
       return "Result of x-y: " + (x - y);
  }
}
