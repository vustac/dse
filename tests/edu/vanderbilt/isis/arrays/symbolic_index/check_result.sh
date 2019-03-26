#!/bin/bash

TESTNAME="SymbolicIndexArray"

echo "Debug info: ${TESTNAME} database entry"
mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'
value=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].value'`
name=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].name'`

# there may be additional answers (out-of-bounds) so just verify one of the answers matches
expname="index"
expvalue="4"

IFS=$'\n'
for item in ${value}
do
  if [ "${item}" == "${expvalue}" ]; then
    echo "${TESTNAME} passed!";
    mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
    echo "Cleared database"
    exit 0
  fi
done

echo "${TESTNAME} failed!";
echo "Result was: ${value}";
echo "Expected  : ${expvalue}";
