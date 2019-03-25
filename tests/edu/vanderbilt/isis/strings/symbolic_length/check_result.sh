#!/bin/bash

TESTNAME="SymbolicStringLength"
echo "Debug info: ${TESTNAME} database entry"
mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'
ans=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].value'`
expected="\"\""
# when an empty string is returned, the result includes the enclosing quotes

if [ "$ans" == "$expected" ];
then
    echo "${TESTNAME} passed!";
    mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
    echo "Cleared database"
else
    echo "${TESTNAME} failed!";
    echo "Result was: ${ans}";
    echo "Expected  : ${expected}";
fi;
