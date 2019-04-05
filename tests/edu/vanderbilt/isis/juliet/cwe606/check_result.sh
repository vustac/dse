TESTNAME="SimpleCWE606"

# initialize the pass/fail status to pass and save the pid of the running process"
STATUS=0
PID=$1
echo "pid = ${PID}"

# send the message
echo "Sending message to application"
echo "1" > /proc/${PID}/fd/0

# wait till process completes
wait_for_app_completion 5 ${PID}

# get solver response and check against expected solution
echo "Debug info: ${TESTNAME} database entry"
extract_solutions
check_solution "parsedInt" "0"
show_results ${TESTNAME}
