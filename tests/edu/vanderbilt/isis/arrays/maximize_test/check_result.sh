#!/bin/bash

TESTNAME="SymbolicMaximizeTest"
echo "Debug info: ${TESTNAME} database entry"
mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'
ans=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].value'`
expected="2"

# there may be additional answers (out-of-bounds) so just verify one of the answers matches
IFS=$'\n'
for item in $ans
do
  if [ "$item" == "$expected" ]; then
    echo "${TESTNAME} passed!";
    mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
    echo "Cleared database"
    exit 0
  fi
done

echo "${TESTNAME} failed!";
echo "Result was: ${ans}";
echo "Expected  : ${expected}";
