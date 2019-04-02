TESTNAME="SimpleCWE129"

PID=$1
echo "pid = ${PID}"

# send the message
echo "Sending message to application"
echo "2" > /proc/${PID}/fd/0

# wait till process completes
wait_for_app_completion 5 ${PID}

# get solver response and check against expected solution
echo "Debug info: ${TESTNAME} database entry"
check_single_solution "parsedInt" "6"
retcode=$?
show_results ${TESTNAME} ${retcode}
