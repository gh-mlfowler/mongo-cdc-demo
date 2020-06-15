#!/bin/bash -ex

# From https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/

SERVER_IP=$1

sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 4B7C549A058F8B6B
echo "deb [arch=amd64] http://repo.mongodb.org/apt/ubuntu bionic/mongodb-org/4.2 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-4.2.list
sudo apt-get update
sudo apt-get install -y mongodb-org

sudo sed -i "s/bindIp: 127.0.0.1/bindIp: ${SERVER_IP}/g" /etc/mongod.conf

tee -a /etc/mongod.conf <<-"EOF"
replication:
  oplogSizeMB: 10
  replSetName: mongo-replica-set
EOF

sudo systemctl enable mongod
sudo systemctl start mongod

sleep 20

mongo --host ${SERVER_IP} << EOF
config = { _id: "mongo-replica-set", members:[
          { _id : 0, host : "${SERVER_IP}:27017"} ]
         };
rs.initiate(config);

rs.status();
EOF

mongo admin --host mongo-replica-set/${SERVER_IP}:27017 << 'EOF'
db.createUser(
  {
    user: "debezium",
    pwd: "debezium",
    roles: [ {role: "readWrite", db: "wishlist"} ]
  }
);
EOF

mongoimport --host mongo-replica-set/${SERVER_IP} --db wishlist --collection product --authenticationDatabase admin --username debezium --password debezium --drop --file wishlist.json

sh -c "/vagrant/mongodb-updates.sh ${SERVER_IP} > /home/vagrant/mongodb-updates.out 2> /home/vagrant/mongodb-updates.err & "
