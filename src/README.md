# DSE Software

This directory contains the source code for the following sub-projects of the DSE:

### danalyzer
This is a Java program that performs the instrumentation of the bytecode of a Java application
(a jar file) by supplementing most of the opcodes with calls to additionally supplied methods that,
when executed, will drive the JVM to produce a copy of the running stack that is augmented with
*symbolic* parameters.

When this instrumented code is executed, these symbolic parameters will maintain a list of the
constraints placed in it during the course of execution, and these constraints are reported
through a network connection to a solver process that runs as a daemon server. The *dansolver*
program is responsible for providing potentially useful information about what values of the
symbolic parameters can cause certain outcomes. Running the instrumentation phase of this program
will produce an instrumented version of the original application, plus two text files one containing
all of the classes in the application and another containing all of the methods.

The configuration of how the instrumented code runs (the symbolic parameters to define, additional
user-defined constraints to place on them, and what debug information to output and where to send
it) can be provided in a *danfig* file placed in the same directory as the jar file. If not found,
a GUI interface will appear to allow the user to set these when the instrumented code is first started.

This program requires the *danhelper* program be run as an agent at the same time the instrumented
code is launched. There are also some methods that were very expensive to execute in Java that
were delegated to a C++ program, *danpatch*, so this is also a part of the DSE. Since *danalyzer*
communicates with the *dansolver* server, it also requires that the server is started prior to running.

### danhelper
A native agent (written in C++) that monitors running processes to allow running callbacks
(in danalyzer) when methods from the instrumented program are entered and exited. This works in
conjunction with and simplifies the danalyzer code.
   
### danpatch
A native library (written in C++ and using JNI interface) used by the symbolic execution engine
at runtime to speed-up certain operations.
   
### dansolver
A Java program that runs as a daemon server process for receiving the symbolic constraints output
through a TCP connection by *danalyzer* and generating solutions for them using the z3 solver.
The results are then saved to a *MongoDB* database.

# DSE Tools

The following projects are tools that can be used in conjunction with the DSE, but are independant
from it.
 
### dandebug
A Java program that runs as a server for monitoring the debug output from an instrumented test
(using either UDP or TCP) and allows viewing and saving the *Call Graph* of the captured data.
This graph displays the methods that have been traversed and how they appear in the call hierarchy,
as well as presenting statistics gathered for each method (number of times called, duration within
the call, number of instructions executed within the call). The methods can then be displayed showing
those that consume the most elapsed time, number of instructions executed, or number of times
called using a colorization scheme to highlight the usage. Note that this is simply a passive
reader - it does not allow selection of what debug information is presented. That is under
control of the instrumented application (set by the danfig file or the GUI). This runs independantly
of the application, merely capturing the information sent by it and displaying what was received.

### danlauncher
A Java program for consolidating the execution and monitoring of the above programs. It provides
the user with a GUI interface for selecting a Java application to test, performs the instrumentation
on it for danalyzer, and runs the instrumented file. These are the same processes that are
performed using the *make.sh* script with the *-b* option. It also has an interface for setting the
parameters the user wishes to be made symbolic and allows the user to look at the bytecode of
each method in the application to allow easier selection of the parameters. The user can also
specify any debugging information he wishes to capture prior to running the application, which is
displayable in this program similarly to the *dandebug* application. The symbolic constraints and
the solutions provided by *dansolver* are also presented in tabular form and can be saved to a file.
It will generate a *danfig* file using the parameters set by the user, which can then be used
with the *make.sh* script. A test script can also be generated using the *RECORD* feature of
danlauncher.

This program replaces the functionality of running the *make.sh* script and using the
*dandebug* for monitoring debug output, along with additional features mentioned above.

### dantestgen
A Java program that uses information from a JSON-formatted file to generate a test script that
will run and verify a test program. The JSON file contains details about the program and the steps
required to test it (including the parameters to make symbolic and issuing commands to the program
as it is running) and then to verify the expected symbolic solutions. This is used to generate the
scripts for all of the tests in the **test** directory, so that the user only needs to provide a
source file and a JSON file in order to define a test. The scripts use the functions provided
by the *base_check.sh* script in the test directory and the JSON files cal all be auto-generated
by *danlauncher* using the *RECORD* feature.

This program is used in running the built-in tests using the *make_tests.sh* and run_tests.sh*
scripts (in the 'test' directory). The user can create his own JSON file (manually or using
*danlauncher*) and run this automated script of his own, since the *make.sh* script in the
'scripts' directory also uses this JSON file. The *commandlist* section of the JSON file is
omitted if no commands are to be issued after the application is started.
