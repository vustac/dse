# TEST

This document describes the scripts for running the tests and describes how to generate a test.
Each test can be generated and run using 'make_tests.sh' and 'run_tests.sh' scripts, specifying
as the 'Name' the name of the source file for the test (these are defined in testlist.txt
for each test).

## SCRIPT USAGE

### make_tests.sh

**Description:**

> This script will build the specified test file (from source code), and then generate an
> instrumented version of it for running.

**Command:**

`make_tests.sh [options] [Name]`

> where [options] are:
> 
> **-t | --test**  - don't actually build anything, dut display the commands used to build
> **-r | --run**   - after building the test, run it (and verify results)

**Notes:**

This script will perform the following actions:

- build the executable jar file (with full debugging enabled) from the source file
- generate a debug-stripped version of the file (can only instrument files with no debugging)
- build the instrumented jar file

If the '-r' option is specified and a 'testcfg.json' file is defined, it will additionally:

- generate a 'danfig' file containing the specified symbolic parameters
- generate a 'check\_results.sh' script file to verify the solution
- run the instrumented jar file
- read the dansolver solution from the mongo database
- validate the solution based on the entries in 'testcfg.json'
    
This will produce the following files (all contained in the *results/TEST* directory):

- testcfg.json     - the JSON config file (copied from the source location)
- danfig           - the danalyzer configuration file to use when running the instrumented code
- check_results.sh - contains the script for running the test results validation
- test\_script.sh   - same as check\_results.sh but excludes the base\_check.sh functions
- TEST.jar         - the original un-instrumented test application (with full debug enabled)
- TEST-strip.jar   - the un-instrumented test application code stripped of debug info
- TEST-dan-ed.jar  - the instrumented test application
- classlist.txt    - list of classes in the application (used when running application)
- methodlist.txt   - list of methods in the application (used when running application)
    
If no test name is specified it will build and run all tests found in the edu subdirectory.

### run_tests.sh

**Description:**

> This script will run and perform the verification on an instrumented test file.

**Command:**

`run_tests.sh [options] [Name]`

> where [options] are:
> 
> **-t | --test**   - don't run the test, dut display the commands that would be used to build it.
> **-v | --verify** - after running the test, verify the results (if danfig & check_results.sh found)

**Notes:**

If the -v option is specified and both a danfig and check_results.sh file were found, it will
then use the check_results.sh script to validate the dansolver solution found.
    
If no test name is specified it will run all tests found in the results subdirectory.

## CREATING A TESTCFG.JSON MANUALLY

A test is created by defining a new entry in the edu/vanderbilt/isis directory structure in this
tests directory. The first level of directories here define the category of test to run, which
is currently broken down into:

- Structure or object type being tested (arrays, buffered_image, constructors, string, threads, type_tests)
- Juliette tests
- Special tests (nano, paper_example, uninstrumented_return)
  
Additional directory levels may be defined to uniquely define the test, and in the base level
a single java file is placed to perform the test. Additionally, a testcfg.json file must be
created here to define the arguments to be passed to the application (if any), the symbolic
parameters to define, and a list of commands to define the test verification procedure.
In detail, this JSON file must consist of the following (all values are defined as Strings):
  
  "**testname**" : the name of the java file (minus the extension)
  "**runargs**" : the argument list to pass to the application when it is run
  "**symboliclist**" : an array of symbolic parameters to define, each containing:
  "**commandlist**" : an array of commands to implement an automated test (see below)

The `symboliclist` must consist of the following entries for each parameter to define
  "**command**" : "*SYMBOLIC_PARAMETER*"
  "**method**" : the method the symbolic is defined in (contains full path and signature)
  "**name**": the name to assign to the parameter (will be used in the solution)
  "**type**": the data type (e.g. I, J, [D, java/lang/String, etc.)
  "**slot**": the slot the parameter occupies in the method
  "**start**": the instruction offset in the method for the start scope of the parameter (0 if not known)
  "**end**": the instruction offset in the method for the end scope of the parameter (0 if not known)

The `commandlist` consists of and array of commands which can be of 3 different types:
a normal command, an expected solution having a single parameter, or an expected solution
having multiple parameters. The expected solution entries are used to define the test
verification of entries in the database. An "EXTRACT_SOLUTIONS" command must precede it
and a "SHOW_RESULTS" command must follow it. Multiple instances can be listed sequentially
if more than 1 solution is to be verified.

### Normal commands

Normal commands consist of 2 entries: "command" and "argument". If no argument defined, use ""

  command             | argument    | description
  ------------------- | ----------- | ------------------------------------------------------------
  "STOP"              | ---         | stops the application
  "DELAY"             | seconds     | delay for a specified time
  "WAIT_FOR_TERM"     | ---         | wait for application to terminate
  "WAIT_FOR_SERVER"   | ---         | wait for the server (specified by SET_HTTP) to be up
  "SET_HTTP"          | port        | set the port the application is waiting for input from
  "SEND_HTTP_GET"     |             | send GET request to HTTP port
  "SEND_HTTP_POST"    | message     | send POST message to HTTP port
  "SEND_STDIN"        | message     | send message to application via stdin
  "EXTRACT_SOLUTIONS" | ---         | extract the solutions from mongo database
  "SHOW_RESULTS"      | ---         | display pass/fail status based on EXPECTED_PARAM entries
    
### Expected Solution (single parameters) commands

These commands consist of a "command" and 3 "arguments":

  "**command**": "*EXPECTED_PARAM*"
  "**name**": parameter name (same as the value defined in SymbolicList)
  "**value**" : the expected value in the solution
  "**ctype**" : the type of constraint { PATH, ARRAY, LOOPBOUND }
    
### Expected Solution (multiple parameters) commands

These commands consist of a "command" and an array of *paramlist* items each consisting of 3 "arguments":

  "**command**": "*EXPECTED_PARAM*"
  "**paramlist**": an array of parameters that are expected for a single solution, each containing:
      "**name**": parameter name (same as the value defined in SymbolicList)
      "**value**" : the expected value in the solution
      "**ctype**" : the type of constraint { *PATH, ARRAY, LOOPBOUND* }
    
## CREATING A TESTCFG.JSON WITH DANLAUNCHER

The danlauncher application can be used to automatically generate the 'testcfg.json' file quite
easily. Run the following steps after creating the un-instrumented test using make_tests.sh
(without the "-r" option, since you have no JSON file yet).
  
1. Load the test Using '**Project**' '**Load jar File**'
1. Find the parameters you wish to make symbolic and add them. This may be done by either bringing
up the Bytecode for the selected method and then selecting the parameter to make symbolic
from the frame in the lower right panel.
    
1. To get the bytecode information for a method you may do either of the following:
  - specify directly from the Bytecode Panel. First enable the Bytecode panel using
'**Project**' and select '**Show Bytecode Panel**' then select the class and method you wish to
observe and click the '**Get Bytecode**' button (must *STOP* application if it is running)
  - if project has been run with *CALLS* enabled in Debug mode so that a Call Graph was obtained,
simply click on the desired method in the graph and the method will be displayed in the
Bytecode Panel.
       
1. if you already know the result you are expecting, you can begin recording by Selecting '**Record**' and '**Start Recording**'.
1. Press the 'RUN' button to begin the application.
1. If input is required to be sent to the application, specify the '**Input mode**' for the correct
type (*HTTP_RAW, HTTP_POST, HTTP_GET, STDIN*) and specify the message to send, then press the '**SEND**' button.
1. You should see the value in the '*Solutions*' box at the top of the frame indicate a non-zero
value when solutions have been obtained. You may select the Solutions tab at the bottom to
observe them. Select the solution(s) to wish to use as a verification one at a time.
1. Click the '**Record**' '**Stop Recording**' to terminate the test recording process.
1. Click the '**Record**' '**Save Recording**' and select the location of the source file for the test and press '**Save**'.

You should now have a 'testcfg.json' file located in the source directory, allowing you to
run the test by running make_tests.sh with a "-" option. This file can be manually adjusted
if necessary according to the manual procedure above.
