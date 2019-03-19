#!/bin/bash

echo "Debug info: ByteRangeTest database entry"
mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})'
ans=`mongo mydb --quiet --eval 'db.dsedata.find({}, {_id:0})' | jq -r '.solution[0].value'`

if [ "$ans" -eq "127" ];
then
    echo "ByteRangeTest passed!";
    mongo mydb --quiet --eval 'db.dsedata.deleteMany({})'
    echo "Cleared database"
else
    echo "ByteRangeTest failed!";
fi;
    
