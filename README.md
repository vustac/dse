# VUSTAC Dynamic Symbolic Execution Engine

This contains the following directories:

- `src` directory contains the source code for each of the sub-components of the DSE.
The details of each of these projects is provided in the **README** file in its directory.
- `test` directory contains source code and scripts for running the Continuous Integration
testing performed by Travis. More information for these tests and how to run them are
provided by the **README** file in its directory.
- `scripts` directory contains scripts to simplify the process of setting up the environment
for the DSE and the process of instrumenting and running a user-provided application.

One script, *startup.sh* can be used to:

1. verify what required dependencies are missing and what versions are installed
1. install any missing dependencies needed for running the DSE
1. build all components of the DSE, start the database server, start the symbolic constraint solver

The other script, *make.sh* is used to:

1. instrument the original application to prepare it for running
1. run the instrumented application (*-r* option specified)
1. (optionally) perform some automated sending of messages to the application and verification
of receiving expected solutions of the symbolic parameters (*commandlist* defined in JSON file)

The prerequisites for make.sh are:

- the jar file containing the application code to be instrumented
- a *lib* folder containing all library files needed to run the application
- *testcfg.json*, a JSON-formatted file defining certain elements of the application. More information
is provided by the **README** file in its directory, but at a minimum this must define the Main Class
of the application.

This material is based on research sponsored by DARPA under agreement number FA8750-15-2-0087.
