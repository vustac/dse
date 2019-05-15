# SCRIPTS

The following script (in the scripts directory) provides an easy process for
instrumenting and running an application provided by the user:

### startup.sh

`startup.sh [options]`

where [options] are:

**-v** or **--version** - display the installed versions (no install or build will be performed).

**-i** or **--install** - perform an install of all necessary tools and libraries needed by the system.

**-b** or **--build**   - stop dansolver (if running), build all DSE components, startup mongodb (if not running)
and start dansolver to allow it to start receiving symbolic constraints to be solved from the instrumented code.

**-f** or **--force** - force pulling in and building z3 even if already installed.

**-h** or **--help**    - print this help message.

If no options are specified, it will behave as if the **-b** option was selected.

This script is used to setup the environment for the DSE so that applications can be instrumented and run without a lot of effort.

### make.sh

`make.sh [options] <app.jar> [arglist]`

where [options] are:

**-t** or **--test**  - don't actually build anything, dut display the commands used to build

**-r** or **--run**   - after building the test, run it

**<app.jar>** = the name (and path) of the application jar file (path can be relative)

**[arglist]** = *(optional)* argument list to pass to application (if no testcfg.json file defined)

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

The content of these last 2 entries is defined in the *CREATING A TESTCFG.JSON MANUALLY* section of the
README.md file in the *test* directory.
