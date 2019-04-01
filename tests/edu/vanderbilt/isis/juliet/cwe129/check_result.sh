#!/bin/bash

TESTNAME="SimpleCWE129"

# single anwser
expname="parsedInt"
expvalue="6"

# waits for specified process pid to no longer be valid (when process terminates)
# $1 = max number of seconds to wait
#
function wait_for_app_completion
{
  echo "Waiting for application to complete..."
  local count=0
  local status=0
  while [ ${status} -eq 0 ] && [ ${count} -lt $1 ]; do
    kill -0 ${pid} > /dev/null 2>&1
    status=$?
    count=`expr ${count} + 1`
    sleep 1
  done
  if [ ${status} -eq 0 ]; then
    echo "Application completed!"
  else
    echo "Application not terminated after $1 secs"
  fi
}

# extracts the solutions from the database and verifies that the single solution given by
# $expname and $expvalue are found within the results. Note that this allows more than one
# solution to have been generated, as long as one of them was the specified name and value.
function check_single_solution
{
  # get solver response and extract param name, type and value from it
  echo "Debug info: ${TESTNAME} database entry"
  local solution=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'`
  echo ${solution}
  local type=`echo ${solution} | jq -r '.solution[0].type'`
  local name=`echo ${solution} | jq -r '.solution[0].name'`
  local value=`echo ${solution} | jq -r '.solution[0].value'`

  # if there are multiple solutions, just verify the one we are expecting
  IFS=$'\n'
  read -r -d '' -a t_array <<< "${type}"
  read -r -d '' -a n_array <<< "${name}"
  read -r -d '' -a v_array <<< "${value}"
  local count=${#v_array[@]}
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
      return 0
    fi
  done

  echo "${TESTNAME} failed!";
  echo "Results : ${n_array[0]} = ${v_array[0]}";
  for ((ix=1; ix<${count}; ix++)); do
    echo "        : ${n_array[ix]} = ${v_array[ix]}"
  done
  echo "Expected: ${expname} = ${expvalue}";
  return 1
}

#--------------------------------------------------------------------------------------------------
# THIS TEST SENDS COMMAND TO STDIN OF APPL AND WAITS FOR APPL TO TERMINATE BEFORE CHECKING RESULT.
#--------------------------------------------------------------------------------------------------

pid=$1
echo "pid = ${pid}"

# send the message
echo "Sending message to application"
echo "2" > /proc/${pid}/fd/0

# wait till process completes
wait_for_app_completion 5

# get solver response and check against expected solution
check_single_solution
