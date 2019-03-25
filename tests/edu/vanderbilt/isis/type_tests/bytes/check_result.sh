#!/bin/bash

TESTNAME="ByteRangeTest"
echo "Debug info: ${TESTNAME} database entry"
mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'
ans=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].value'`

if [ "$ans" -eq "127" ];
then
    echo "${TESTNAME} passed!";
    mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
    echo "Cleared database"
else
    echo "${TESTNAME} failed!";
fi;
    
