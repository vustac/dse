TESTNAME="ThreadTestArrays"

PID=$1
echo "pid = ${PID}"

# wait for application to complete
wait_for_app_completion 5 ${PID}

# get solver response and check against expected solution
# (there may be other solutions, but this is the only one we are interested in)
echo "Debug info: ${TESTNAME} database entry"
check_single_solution "x" "16"
retcode=$?
show_results ${TESTNAME} ${retcode}
