# danlauncher

Graphical user interface for instrumenting a jar file with danalyzer. It allows the user to 
provide an input value to test, then run the instrumented jar file and collect the cost in
terms of instrumented instructions executed. If parameters were marked as symbolic, it then
allows the user to run the solver to solve for new paths and provides the bytecode for a
selected method to view which paths have been explored and the corresponding cost for each.

**NOTE:** This project relies on the following DSE projects to have been built and their output placed in the appropriate directories:

- danhelper: the danhelper.so  file should be in the xxx/dse/danhelper path
- danalyzer: the danalyzer.jar file should be in the xxx/dse/danalyzer/dist path

These file locations are referenced directly, so missing files (or outdated files)
could cause problems. Make sure that all 3 of these have been built recently (and
make sure the danhelper.so has been copied to the main dir, since simply running make
may leave it in the src subdir). These projects are used jointly running the instrumented
code (danalyzer is also used in creating the instrumented code from the original application).

It also assumes that the additional DSE project has also been built and is currently running.

- dansolver: the dansolver.jar file should be in the xxx/dse/dansolver/dist path

This tool receives TCP packets from the instrumented code and places the contents in the
database. It also handles solving the constraints and placing that data in the database as well.
Failure to start dansolver will prevent being able to observe the symbolic solutions.

**NOTE:** The dansolver project relies on the Mongo Database being active. MongoDB server should be running
prior to starting both dansolver and danlauncher (e.g. running the command 'mongod' to start
the daemon).

