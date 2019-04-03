TESTNAME="SimpleServer"

# http port and message to send
SERVERPORT="8080"

PID=$1
echo "pid = ${PID}"

# wait for server to start then post message to it
wait_for_server 10 ${PID} ${SERVERPORT}

# send the message
echo "Sending message to application"
curl -d "a=1&b=0" http://localhost:${SERVERPORT}

# get solver response and check against expected solution
echo "Debug info: ${TESTNAME} database entry"
check_single_solution "a" "0"
retcode=$?

# terminate process (this one doesn't auto-terminate)
kill -15 ${PID} > /dev/null 2>&1

show_results ${TESTNAME} ${retcode}
