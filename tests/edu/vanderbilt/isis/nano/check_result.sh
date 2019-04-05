TESTNAME="SimpleNano"

# initialize the pass/fail status to pass and save the pid of the running process"
STATUS=0
PID=$1
echo "pid = ${PID}"

# http port and message to send
SERVERPORT="8080"

# wait for server to start then post message to it
wait_for_server 10 ${PID} ${SERVERPORT}

# send the message
echo "Sending message to application"
curl -d "1" http://localhost:${SERVERPORT}

# get solver response and check against expected solution
echo "Debug info: ${TESTNAME} database entry"
extract_solutions
check_solution "parsedInt" "4"
show_results ${TESTNAME}

# terminate process (this one doesn't auto-terminate)
kill -15 ${PID} > /dev/null 2>&1
