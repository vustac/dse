TESTNAME="SymbolicIndexArray"

PID=$1
echo "pid = ${PID}"

# wait for application to complete
wait_for_app_completion 5 ${PID}

# get solver response and check against expected solution
# (may be other solutions, but this is the only one we want to verify)
echo "Debug info: ${TESTNAME} database entry"
check_single_solution "index" "4"
retcode=$?
show_results ${TESTNAME} ${retcode}
