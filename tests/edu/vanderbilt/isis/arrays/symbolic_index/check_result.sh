#!/bin/bash

TESTNAME="SymbolicIndexArray"

# single anwser (may be others, but this is the only one we want to verify)
expname="index"
expvalue="4"

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
# wait for application to complete
pid=$1
echo "pid = ${pid}"
wait_for_app_completion

# get solver response and check against expected solution
check_single_solution
