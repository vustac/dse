TESTNAME="ThreadTestArrays"

# initialize the pass/fail status to pass and save the pid of the running process"
STATUS=0
PID=$1
echo "pid = ${PID}"

# wait for application to complete
wait_for_app_completion 5 ${PID}

# get solver response and check against expected solution
# (there may be other solutions, but this is the only one we are interested in)
echo "Debug info: ${TESTNAME} database entry"
extract_solutions
check_solution "x" "16"
show_results ${TESTNAME}
