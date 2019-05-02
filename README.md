# VUSTAC Dynamic Symbolic Execution Engine

The DSE consists of 5 seperate projects contained in separate subdirectories:

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

 * `dandebug`: a Java program that runs as a server for monitoring the debug output
   from an instrumented test (using either UDP or TCP) and allows viewing and saving
   the call graph of the captured data (as long as CALL/RETURN debug messages are enabled).

 * `dansolver`: a Java program that runs as a server for receiving the symbolic
   constraints output by danalyzer on a TCP port and generating solutions for them.
   The results are then saved to a database (mongodb).

 * `danlauncher`: a Java program for consolidating the execution and monitoring of
   the above programs. It provides the user with a GUI interface for selecting a Java
   application to test, instruments it for danalyzer, then monitors its debug output
   similarly to dandebug. It also provides an interface for examining the bytecode of
   the application and allows the user to specify the parameters he wishes to declare
   as symbolic, and monitors the solutions that are placed in the database by dansolver.

The following scripts provide an easier process for running the STAC Challenge
problems and Canonicals.

   * `setup.sh`: will create the DSETests folder that will contain the tests that
     you wish to instrument and run. This command is specified with a single
     argument to define which test(s) to copy from the engagement repos.
     It is assumed that the engagement repo (and public-el-information-mirror repo
     for the Canonicals) have been setup and the path locations are defined in
     setpaths.sh. The tests can be copied over individually or the entire list
     from the specified engagement (e1, e2, e4, e5), or the Canonicals (canon),
     or all tests (all). Running without an argument will display the usage info.

   * `make.sh`: will perform the instrumentation of the specified test that is
     contained in the DSETests directory. The format is simply "make.sh <Testname>"
     It will build danalyzer (to make sure the latest version is being used), then
     instruments the specified test file using it, and strips out all debug content
     so that danhelper can interpret the input correctly.
     Adding the -t option will cause no commands to actually run, but will output
     the commands that would be used otherwise.

   * `run.sh`: will run the specified test, assuming it has previously been instrumented
     using make.sh. It also takes as an argument the test to run. If you want to
     force a build of danhelper prior to running, add the -f option.
     Adding the -t option will cause no commands to actually run, but will output
     the commands that would be used otherwise.

   * `runorig.sh`: will run the original (un-instrumented) version of the specified test.
     This can be helpful for comparing results to the instrumented version.
     Adding the -t option will cause no commands to actually run, but will output
     the commands that would be used otherwise.

The following scripts are not meant to be executed by the user - they are called by
the above scripts. You may, however need to adapt them.

   * `setpaths.sh`: sets up the paths for your directory structure. Modify this file to
     conform to your needs, or setup your paths to conform to its defined paths.
     Your choice.

   * `projinfo.sh`: defines the name of the application jar file and the mainclass name
     for it, as well as which engagement it is connected with. Any test project name
     that is not found in here assumes that the jar file name and the mainclass name
     will be the same as the project name.

   * `projargs.sh`: defines the default arguments for each test. These can be overridden
     by adding them to the end of the run.sh command.

