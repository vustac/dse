# dandebug

Graphical debug monitor for viewing the debug log messages output from danalyzer in a color-highlighted
format for discerning the different types of content and providing a method of viewing the call tree graphically.

The command line has the following format:

  `java -jar dandebug.jar [-t] [-u] [port]`

>  Where:
> 
>  -t = use TCP (default)
>  -u = use UDP
>  port = the port selection (5000 is default)


This program uses the network to receive live debug messages issued from a running Java program that has been
instrumented with danalyzer and has been configured to output messages using either a UDP or TCP port.
The instrumented code always sends to localhost port 5000, so dandebug must run on the same machine
as the program being debugged. 

## Statistics panel

This displays information about the lines processed from either the port or a file. It contains the following entries:

- **Elapsed** shows the time elapsed from the start of the trace (the timer is started and re-started whenever a debug message is received that has a line count value of 0).
- **Errors** indicates the number of ERROR messages received from either the port or file.
- **Lines Read** indicates the number of debug message lines received from the port.
- **Lines Lost** indicates the number of debug message lines lost from the port (based on the line counter value in the messages received).
- **Processed** shows the number of lines that have been displayed and saved in the call graph list (see discussion below on Call Graph).
- **Methods** shows the number of unique method calls that were received (only if CALL and RETURN debug messages are being sent).
- **Threads** shows the number of different threads that were received in the messages.

- **Load File** loads a saved capture of the debug information for re-processing. Will also re-generate the Call Graph.
- **Load Graph** loads a saved Call Graph (JSON file format). Does NOT contain any debug messages for display in **Live** panel.
- **Save Graph** saves the current Call Graph to a JSON file for viewing later. (Note - a graph must be previously viewed in order to save it.)
- **Clear Log** clears the data from the **Live** panel (does NOT affect the data saved to the log file.
- **Pause** allows the user to pause processing of the port input so older data can viewed in the **Live** panel.
- **Resume** (only displayed when paused) allows the processing of messages from the port to continue.
- **Erase Log** clears the data from both the display and the log file.
- **Set Logfile** allows the user to specify a location to store the debug information to.
- **Auto-Reset on Start** if enabled, will clear the Log display and add a timestamp line when the next input is received from the network.

## Graph Highlighting panel

Allows viewing the Call Graph with color coding to highlight certain characteristics:

- **Thread** highlights all methods of the specified thread (must be multi-threaded data for this feature).
- **Elapsed Time** highlights the methods that use the most total elapsed time.
- **Instructions** highlights the methods that use the most number of instructions.
- **Iterations Used** highlights the methods that had the most number of calls to it.
- **Status** highlights the methods that either didn't complete or had an error in them. (Those methods where an ERROR message occurred will be marked as PINK and those methods that never reached a RETURN message will be marked in LIGHT BLUE).
- **Off** disables the highlighting.

## Thread Select panel

This is used when //Thread// highlighting is selected (for multi-threaded programs) and allows
selecting which methods were run in which threads.

- **Thread selection** displays the current thread selection (disabled if multiple threads not found).
- **UP** increases the thread selection value.
- **DN** decreases the thread selection value.

## Highlight Range panel

The //Range// is a value between 1 and 20 and represents the step size used in the highlighting of
Elapsed Time, Instructions, or Iterations Used highlighting selections.
The highlighting color intensity uses 5 discreet ranges of percentage of the
measured parameter as compared with the min and max values. For instance, if Elapsed Time is the selected
parameter and the max elapsed time for a method is 250 ms and the min is 50 ms, the percentage for each method
is its elapsed time - 50 ms then divided by 200 ms. A //Range// of 20 will highlight in the darkest color those
methods ranging from 80 to 100 %, the next highest will be for 60 to 80 %, the next 40 to 60 %, the
next 20 to 40 %, and those under 20 % will not be highlighted. Smaller //Range// values will highlight fewer
methods having the largest value (only the top 5% will be highlighted for a value of 1).

- **Range selection** displays the current highlight range selection.
- **UP** increases the highlight range selection value.
- **DN** decreases the highlight range selection value.

## Messages panel

Displays the listening port it uses and helpful messages when needed.

## Scrollable Panel

The two tabs in the bottom panel allow switching between viewing the debug messages and the Call Graph.

The **Log** tab window displays messages that have been processed (from either the port or a file) and is scrollable.
It will only hold around 2000 lines of text (between 100k and 150k of characters) before it chops off older information,
since this can cause the display to slow down drastically when the file size gets very large. If you need
to view older information, open the specified log file in an editor.
Note that the data is never eliminated in the log file unless the user presses the Erase button.

The **Call Graph** tab selection sorts through only the CALL and RETURN log entries to monitor
the call stack flow for instrumented calls (uninstrumented methods are not tracked).
If the debug log does not include the CALL and RETURN messages, the call graph cannot be generated.
A List is created of each method call along with several useful characteristics is uses in the graph,
such as the list of methods that call it (parents), the number of times the method was called,
total time elapsed between the call and return and the corresponding number of instructions executed
between the call and return. It also logs the line number corresponding to the last entry in that
method where an ERROR message or an exception (ENTRY message type) occurred, if any.
A call graph is generated that graphically connects the methods indicating the call flow is
generated and displayed when the Call Graph tab is selected. Selecting one of the methods in this
display will show the details of the data gathered on that method. Once the call graph has been displayed,
clicking the Save Graph button will save both the graphical image (as a PNG file) and the List
information (as a JSON file). The JSON file allows the user to be able to re-load a file that has
previously been saved (using the Load Graph button) and be able to view the call graph and again
get information about each method by clicking on it. The Graph Highlighting section of the panel
allows you to color code the method blocks to highlight those that consumed the most resources
based on: elapsed time, instruction count or the number of times it was called.

## Debug Message Format

**Example line:**

  00015621 [08:05.281] <01> CALL  : 3850 com/ainfosec/STACPoker/Card.getSuitAsString()Ljava/lang/String; com/ainfosec/STACPoker/Card.getImage()Ljava/awt/image/BufferedImage;

  offset | length  | contents
  ------ | --------| ------------------------------------------------------------
  0      | 8-digit | line number starting at 00000000
  9      | 11-char | elapsed time value formatted as minutes:seconds.milliseconds from the start of log collection, enclosed in []
  21     | 2-digit | thread id, enclosed in <>
  26     | 6-char  | message type followed by colon
  34     | any     | the message contents

It is assumed that the contents of the RETURN message is a numeric value representing the current
instruction count and that the contents of the CALL message are (3) ASCII space seperated entries
consisting of: the current instruction count, the method being called, adn the caller of the method.
Note that the method names are the full name including the class path, the argument list and the return value type.
