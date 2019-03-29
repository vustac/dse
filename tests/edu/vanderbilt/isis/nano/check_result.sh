#!/bin/bash
set -o nounset

TESTNAME="SimpleNano"

# single anwser
expname="parsedInt"
expvalue="4"

# http port and message to send
SERVERPORT="8080"
MESSAGE="1"

function wait_for_app_completion
{
  echo "Waiting for application to complete..."
  status=0
  while [ ${status} -eq 0 ]; do
    kill -0 ${pid} > /dev/null 2>&1
    status=$?
  done
  echo "Application completed!"
}

function wait_for_server
{
  echo "Waiting for server to come up..."
  success=0
  for i in {1..10}
  do
    # this will get all processes that are listening on the specified tcp port
    # (the cut command extracts only the 'Local Address' and 'PID/Program name' column data so that
    # the grep will only find the port specification in the Local Address).
    serverstat=`netstat -pln 2>/dev/null | grep "^tcp" | grep "LISTEN" | cut -c 21-42,78- | grep ":${SERVERPORT} "`
    #echo "serverstat = '${serverstat}'"
    if [ "${serverstat}" != "" ]; then
      proc=${serverstat:25}
      proc="$(echo -e ${proc} | sed -e 's/[[:space:]]*$//')"
      #echo "proc = '${proc}'"
      if [ "${proc}" == "${pid}/java" ]; then
        echo "Server listening ($i secs)"
        success=1
        break;
      else
        echo "FAILURE: Another server listening on port ${SERVERPORT}: ${proc}"
        exit 1
      fi
    fi
    sleep 1
  done

  if [ ${success} -eq 0 ]; then
    echo "FAILURE: Server not found for port ${SERVERPORT} !"
    #exit 1
  fi
}

function check_single_solution
{
  # get solver response and extract param name, type and value from it
  echo "Debug info: ${TESTNAME} database entry"
  solution=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'`
  echo ${solution}
  type=`echo ${solution} | jq -r '.solution[0].type'`
  name=`echo ${solution} | jq -r '.solution[0].name'`
  value=`echo ${solution} | jq -r '.solution[0].value'`

  # if there are multiple solutions, just verify the one we are expecting
  IFS=$'\n'
  read -r -d '' -a t_array <<< "${type}"
  read -r -d '' -a n_array <<< "${name}"
  read -r -d '' -a v_array <<< "${value}"
  count=${#v_array[@]}
  for ((ix=0; ix<${count}; ix++)); do
    # when a string is returned, the result includes the enclosing quotes, so include them in the
    # expected value.
    if [ "${t_array[ix]}" == "string" ]; then
      expvalue="\"${expvalue}\""
    fi

    #echo "${ix}: name: '${n_array[ix]}' value: '${v_array[ix]}'"
    if [ "${n_array[ix]}" == "${expname}" ] && [ "${v_array[ix]}" == "${expvalue}" ]; then
      echo "${TESTNAME} passed!";
      echo "Result was: ${expname} = ${expvalue}"
      mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
      echo "Cleared database"
      exit 0
    fi
  done

  echo "${TESTNAME} failed!";
  echo "Results : ${n_array[0]} = ${v_array[0]}";
  for ((ix=1; ix<${count}; ix++)); do
    echo "        : ${n_array[ix]} = ${v_array[ix]}"
  done
  echo "Expected: ${expname} = ${expvalue}";
}

#--------------------------------------------------------------------------------------------------
# wait for server to start then post message to it
pid=$1
echo "pid = ${pid}"
wait_for_server

# send the message
echo "Sending message to application"
curl -d ${MESSAGE} http://localhost:${SERVERPORT}
kill -15 ${pid}

# get solver response and check against expected solution
check_single_solution
