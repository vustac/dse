# VUSTAC Dynamic Symbolic Execution Engine

The `src` directory contains the source code for the following sub-projects of the DSE:

 * `danalyzer`: consists of a Java program that performs the instrumentation of the
   bytecode of a Java program, and provides the executed methods that are added to
   the bytecode. Executing the instrumented code will provide the user with a GUI
   interface for defining the parameters in the code that the user wishes to
   symbolize and allows enabling messages to be output for debugging and monitoring
   the process.

 * `danhelper`: consists of a native agent (written in C++) that monitors running
   processes to allow running callbacks (in danalyzer) when methods from the
   instrumented program are entered and exited. This simplifies the danalyzer code
   and is run in conjunction with it.
   
 * `danpatch`: consists of a native library (written in C++) used by the symbolic
   execution engine at runtime to speed-up certain operations.
   
 * `dansolver`: a Java program that runs as a server for receiving the symbolic
   constraints output by danalyzer on a TCP port and generating solutions for them.
   The results are then saved to a database (mongodb).

 * `danlauncher`: a Java program for consolidating the execution and monitoring of
   the above programs. It provides the user with a GUI interface for selecting a Java
   application to test, instruments it for danalyzer, then monitors its debug output
   similarly to dandebug. It also provides an interface for examining the bytecode of
   the application and allows the user to specify the parameters he wishes to declare
   as symbolic, and monitors the solutions that are placed in the database by dansolver.

 * `dantestgen`: a Java program that is uses information from a JSON-formatted file
   to generate a test script that will run and verify a test program. The JSON file
   contains details about the program and the steps required to test it (including
   the parameters to make symbolic and issuing commands to the program as it is running)
   and then to verify the expected symbolic solutions. This is used to generate the
   scripts for all of the tests in the 'test' directory, so that the user only needs to
   provide a source file and a JSON file in order to add a test. The scripts use the
   functions provided by the base_check.sh script in the test directory and the JSON
   files are all auto-generated using the danlauncher RECORD feature.
 
 * `dandebug`: a Java program that runs as a server for monitoring the debug output
   from an instrumented test (using either UDP or TCP) and allows viewing and saving
   the call graph of the captured data (as long as CALL/RETURN debug messages are enabled).

The `test` directory contains source code and scripts for running the Continuous Integration
testing performed by Travis. More information for these tests and how to run them are
provided by the `README` in the test folder.

The `scripts` directory contains scripts to simplify the process of instrumenting and
running a user-provided application. The requirements are the jar file containing the
application to instrument, all library files needed to run the application, and a
JSON-formatted file defining certain elements of the application. More information
is provided by the `README` in the scripts folder.
