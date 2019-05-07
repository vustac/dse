# SCRIPTS

The following script (in the scripts directory) provides an easy process for
instrumenting and running an application provided by the user:

`make.sh [options] <app.jar> [arglist]`

where [options] are:

-t or --test  - don't actually build anything, dut display the commands used to build
-r or --run   - after building the test, run it

<app.jar> = the name (and path) of the application jar file (path can be relative)

[arglist] = (optional) argument list to pass to application (if no testcfg.json file defined)

This script will perform the following actions:

- build the instrumented jar file

If the '-r' option is specified, it will additionally:

- generate a default 'danfig' file containing the specified symbolic parameters if 'testcfg.json'
- run the instrumented jar file
     
It will also perform the following if a 'commandlist' is defined in the 'testcfg.json' file

- generate a 'check_results.sh' script file to verify the solution
- read the dansolver solution from the mongo database
- validate the solution based on the entries in 'testcfg.json'

This requires the following files be present in the directory of the jar application file:

- lib/*            - contains all libraries needed by the application to run
- testcfg.json     - the JSON config file that defines conditions for the application (only required for -r option)

This will produce the following files (all contained in the results/TEST directory):

- danfig           - the configuration file used when running the instrumented code
- check_results.sh - contains the script for running the test results validation
- test_script.sh   - same as check_results.sh but excludes the base_check.sh functions
- TEST-strip.jar   - the un-instrumented test application code stripped of debug info
- TEST-dan-ed.jar  - the instrumented test application
- classlist.txt    - list of classes in the application (used when running application)
- methodlist.txt   - list of methods in the application (used when running application)
    
The JSON file that is required to instrument and run the application MUST contain the
following entries (all values are defined as Strings):

  "testname" : the name of the java file (minus the extension)
  "runargs" : the argument list to pass to the application when it is run
  "mainclass" : the Main Class for the application

The following entries MAY optionally be included to define symbolic parameters and commands
to run:

  "symbolicList" : an array of symbolic parameters to define, each containing:
  "commandlist" : an array of commands to implement an automated test (see below)

The `symbolicList` must consist of the following entries for each parameter to define
  "command" : "SYMBOLIC_PARAMETER"
  "method" : the method the symbolic is defined in (contains full path and signature)
  "name": the name to assign to the parameter (will be used in the solution)
  "type": the data type (e.g. I, J, [D, java/lang/String, etc.)
  "slot": the slot the parameter occupies in the method
  "start": the instruction offset in the method for the start scope of the parameter (0 if not known)
  "end": the instruction offset in the method for the end scope of the parameter (0 if not known)

The `commandlist` consists of and array of commands which can be of 3 different types:
a normal command, an expected solution having a single parameter, or an expected solution
having multiple parameters. The expected solution entries are used to define the test
verification of entries in the database. An "EXTRACT_SOLUTIONS" command must precede it
and a "SHOW_RESULTS" command must follow it. Multiple instances can be listed sequentially
if more than 1 solution is to be verified.
    
`normal` commands consist of 2 entries: "command" and "argument". If no argument defined, use ""

  -------------------+-----------+------------------------------------------------------------
  command            | argument  | description
  -------------------+-----------+------------------------------------------------------------
  "STOP"             | ---       | stops the application
  "DELAY"            | seconds   | delay for a specified time
  "WAIT_FOR_TERM"    | ---       | wait for application to terminate
  "WAIT_FOR_SERVER"  | ---       | wait for the server (specified by SET_HTTP) to be up
  "SET_HTTP"         | port      | set the port the application is waiting for input from
  "SEND_HTTP_GET"    |           | send GET request to HTTP port
  "SEND_HTTP_POST"   | message   | send POST message to HTTP port
  "SEND_STDIN"       | message   | send message to application via stdin
  "EXTRACT_SOLUTIONS"| ---       | extract the solutions from mongo database
  "SHOW_RESULTS"     | ---       | display pass/fail status based on EXPECTED_PARAM entries
    
`expected solution (single parameter)` commands consist of a "command" and 3 "arguments":

  "command": "EXPECTED_PARAM"
  "name": parameter name (same as the value defined in SymbolicList)
  "value" : the expected value in the solution
  "ctype" : the type of constraint { PATH, ARRAY, LOOPBOUND }
    
`expected solution (multiple parameters)` commands consist of a "command" and an array of
paramlist items each consisting of 3 "arguments":

  "command": "EXPECTED_PARAM"
  "paramlist": an array of parameters that are expected for a single solution, each containing:
      "name": parameter name (same as the value defined in SymbolicList)
      "value" : the expected value in the solution
      "ctype" : the type of constraint { PATH, ARRAY, LOOPBOUND }
