#!/bin/bash -ex

SERVER_IP=$1

sleep 300

while true
do
  sleep 60

  pounds=`shuf -i 19-35 -n 1`
  pence=`shuf -i 10-99 -n 1`

  mongo wishlist --host mongo-replica-set/${SERVER_IP} --eval "db.product.update({\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"store\":\"amazon.co.uk\"},{\$set:{\"price\":${pounds}.${pence}},\$currentDate:{\"last_check\":true}})"
done
