# SCRIPTS

The following script (in the scripts directory) provides an easy process for
instrumenting and running an application provided by the user:

## startup.sh

**Description:**

> This script is used to setup the environment for the DSE so that applications can be
> instrumented and run without a lot of effort.

**Command:**

`startup.sh [options]`

> where [options] are:
> 
> **-i** | **--install** - perform an install of all necessary tools and libraries not currently installed.
> 
> **-b** | **--build**   - build all DSE components and startup mongodb and dansolver so that it is ready
> for creating and running instrumented applications.
> 
> **-f** | **--force**   - force installation of all tools, even if already installed.
> 
> **-v** | **--version** - display the installed versions (no install or build will be performed).
> 
> **-h** | **--help**    - print this help message.

**Notes:**

It installs Open JDK 8 on Linux systems and whatever version brew defaults to
(currently JDK 10) on Darwin (mac).

If no options are specified, it will behave as if the **-b** option was selected.

## make.sh

**Description:**

> This script is used to instrument an application jar file, and to run the instrumented file.
> An *instrumented* file consists of the original jar file that has had all debug information
> stripped out of it and then additional instructions inserted for each opcode in order to
> maintain a *symbolic* stack that allows tracking constraints encountered during execution
> on user-specified parameters in the application. These constraints are sent through a network
> connection to the *solver* application that will attempt to find values for the parameters
> that can lead to unexplored paths, maximizing a loop bound, or exceeding an array bounds.

**Command:**

`make.sh [options] <app.jar> [arglist]`

> where [options] are:
> 
> **-t** | **--test**  - don't actually build anything, dut display the commands used to build
> 
> **-r** | **--run**   - after building the test, run it
> 
> **<app.jar>** = the name (and path) of the application jar file (path can be relative)
> 
> **[arglist]** = *(optional)* argument list to pass to application (if no testcfg.json file defined)

**Notes:**

This script will perform the following actions:

- build the instrumented jar file

If the *-r* option is specified, it will additionally:

- generate a default *danfig* file containing the specified symbolic parameters if *testcfg.json* specified them
- run the instrumented jar file
     
It will also perform the following if a *commandlist* is defined in the *testcfg.json* file

- generate a *check_results.sh* script file to verify the solution
- read the dansolver solution from the mongo database
- validate the solution based on the entries in *testcfg.json*

This requires the following files be present in the directory of the jar application file:

- __lib/*__            - contains all libraries needed by the application to run
- __testcfg.json__     - the JSON config file that defines conditions for the application (only required for *-r* option)

This will produce the following files (all contained in the results/TEST directory):

- __danfig__           - the configuration file used when running the instrumented code
- __check_results.sh__ - contains the script for running the test results validation
- __test_script.sh__   - same as *check_results.sh* but excludes the base_check.sh functions
- __TEST-strip.jar__   - the un-instrumented test application code stripped of debug info
- __TEST-dan-ed.jar__  - the instrumented test application
- __classlist.txt__    - list of classes in the application (used when running application)
- __methodlist.txt__   - list of methods in the application (used when running application)
    
The JSON file that is required to instrument and run the application MUST contain the
following entries (all values are defined as Strings):

  "**testname**" : the name of the java file (minus the extension)
  "**runargs**" : the argument list to pass to the application when it is run
  "**mainclass**" : the Main Class for the application

The following entries MAY optionally be included to define symbolic parameters and commands
to run:

  "**symboliclist**" : an array of symbolic parameters to define, each containing:
  "**commandlist**" : an array of commands to implement an automated test (see below)

The content of these last 2 entries is defined in the *CREATING A TESTCFG.JSON MANUALLY* section of https://github.com/vustac/dse/tree/master/test.
