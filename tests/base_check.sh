#!/bin/bash
set -o nounset

# waits for specified process pid to no longer be valid (when process terminates)
#
# $1 = max number of seconds to wait
# $2 = pid of process to wait on
#
# returns 0 on success, 1 on failure
#
function wait_for_app_completion
{
  local timeout=$1
  local pid=$2

  echo "Waiting for application to complete..."
  local count=0
  local status=0
  while [ ${status} -eq 0 ] && [ ${count} -lt ${timeout} ]; do
    kill -0 ${pid} > /dev/null 2>&1
    status=$?
    count=`expr ${count} + 1`
    sleep 1
  done
  if [ ${status} -eq 0 ]; then
    echo "Application completed!"
    return 0
  fi

  echo "Application not terminated after ${timeout} secs"
  return 1
}

# waits for the server to be listening on the specified port
#
# $1 = max number of seconds to wait
# $2 = pid of process to wait on
# $3 = listening port of server to wait on
#
# returns 0 on success, 1 on failure
#
function wait_for_server
{
  local timeout=$1
  local pid=$2
  local port=$3
  
  echo "Waiting for server to come up on port ${port}..."
  for (( i=0; i < ${timeout}; i++ ));
  do
    # this will get all processes that are listening on the specified tcp port
    # (the cut command extracts only the 'Local Address' and 'PID/Program name' column data so that
    # the grep will only find the port specification in the Local Address).
    serverstat=`netstat -pln 2>/dev/null | grep "^tcp" | grep "LISTEN" | cut -c 21-42,78- | grep ":${port} "`
    #echo "serverstat = '${serverstat}'"
    if [ "${serverstat}" != "" ]; then
      proc=${serverstat:25}
      proc="$(echo -e ${proc} | sed -e 's/[[:space:]]*$//')"
      #echo "proc = '${proc}'"
      if [ "${proc}" == "${pid}/java" ]; then
        echo "Server listening ($i secs)"
        return 0;
      else
        echo "FAILURE: Another server listening on port ${port}: ${proc}"
        echo
        STATUS=1
        exit 1
      fi
    fi
    sleep 1
  done

  # ignore this error since the macOS can't use netstat to find the server
  echo "FAILURE: Server not found for port ${port} (but trying anyway)"
  return 0
}

# extracts the solutions from the database and saves them as 3 arrays:
# 't_array' will contain the type for each solution,
# 'n_array' will contain the parameter names,
# 'v_array' will contain the values
# $SOLUTIONS will contain the number of solutions found
#
function extract_solutions
{
  # get solver response and extract param name, type and value from it
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

  SOLUTIONS=${#v_array[@]}
  echo "${SOLUTIONS} solutions received"
  echo
}

# verifies that the solution given by the passed parameter name and value are found within the
# solution results (assumes extract_solutions was run prior).
# This can be used to verify additional solutions for a test that produces multiple tests.
#
# $1 = name of parameter expected in solution
# $2 = value of parameter expected in solution
#
# returns 0 on success, 1 on failure.
# $STATUS will be set to 1 on failure (no change on success)
#
function check_solution
{
  local expname=$1
  local expvalue=$2
  
  for ((ix=0; ix<${count}; ix++)); do
    # when a string is returned, the result includes the enclosing quotes, so include them in the
    # expected value.
    if [ "${t_array[ix]}" == "string" ]; then
      expvalue="\"${expvalue}\""
    fi

    #echo "${ix}: name: '${n_array[ix]}' value: '${v_array[ix]}'"
    if [ "${n_array[ix]}" == "${expname}" ] && [ "${v_array[ix]}" == "${expvalue}" ]; then
      echo "Result found: ${expname} = ${expvalue}"
      return 0
    fi
  done

  echo "Expected result not found: ${expname} = ${expvalue}"
  STATUS=1
}

# extracts the solutions from the database and verifies that the single solution given by
# the passed parameter name and value are found within the results. Note that this allows more
# than one solution to have been generated, as long as one of them was the specified name and value.
#
# $1 = name of parameter expected in solution
# $2 = value of parameter expected in solution
#
# returns 0 on success, 1 on failure
#
function check_single_solution
{
  extract_solutions
  check_next_solution $@
}

# this displays the status results for the specified test
#
# $1 = name of test
# $STATUS = test status: 0 = passed, 1 = failed
#
function show_results
{
  if [ ${STATUS} -ne 0 ]; then
    echo "----- $1 FAILED -----"
    echo
    echo "Results : ${n_array[0]} = ${v_array[0]}";
    for ((ix=1; ix<${count}; ix++)); do
      echo "        : ${n_array[ix]} = ${v_array[ix]}"
    done
    echo
    exit 1
  else
    echo "----- $1 PASSED -----"
    echo
  fi
}

#--------------------------------------------------------------------------------------------------

