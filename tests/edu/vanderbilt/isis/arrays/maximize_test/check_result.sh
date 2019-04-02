TESTNAME="SymbolicMaximizeTest"

PID=$1
echo "pid = ${PID}"

# wait for application to complete
wait_for_app_completion 5 ${PID}

# get solver response and check against expected solution
echo "Debug info: ${TESTNAME} database entry"
check_single_solution "index" "2"
retcode=$?
show_results ${TESTNAME} ${retcode}
