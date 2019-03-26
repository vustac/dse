#!/bin/bash

TESTNAME="ThreadTestArrays"
expname="x"
expvalue="16"

echo "Debug info: ${TESTNAME} database entry"
mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'
value=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].value'`
name=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].name'`

# there may be additional answers (out-of-bounds) so just verify one of the answers matches
if [ "${name}" == "${expname}" ] && [ "${value}" == "${expvalue}" ]; then
  echo "${TESTNAME} passed!";
  mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
  echo "Cleared database"
else
  echo "${TESTNAME} failed!";
  echo "Result was: ${name} = ${value}";
  echo "Expected  : ${expname} = ${expvalue}";
fi
