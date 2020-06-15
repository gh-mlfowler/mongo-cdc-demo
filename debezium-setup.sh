#!/bin/bash -xe
DEBEZIUM_VERSION=1.1.2
DBZ_MONGO_URL=https://repo1.maven.org/maven2/io/debezium/debezium-connector-mongodb/$DEBEZIUM_VERSION.Final/debezium-connector-mongodb-$DEBEZIUM_VERSION.Final-plugin.tar.gz

MONGODB=$1
KAFKA=$2

mkdir -p /opt/debezium

wget -q -O debezium.tar.gz $DBZ_MONGO_URL
tar -xzvf debezium.tar.gz -C /opt/debezium/

chown -R kafka:kafka /opt/debezium

cp connect-distributed.properties /opt/kafka/config
chown -R kafka:kafka /opt/kafka/config/connect-distributed.properties
sed -i "s/bootstrap.servers=localhost:9092/bootstrap.servers=$KAFKA:9092/" /opt/kafka/config/connect-distributed.properties

# Creating systemd service for kafka connect
echo "[Unit]
Description=Kafka Connect Service
Documentation=http://kafka.apache.org
Requires=network.target
After=network.target

[Service]
Type=forking
User=kafka
Group=kafka
Environment=LOG_DIR='/opt/kafka/logs'
ExecStart=/opt/kafka/bin/connect-distributed.sh -daemon /opt/kafka/config/connect-distributed.properties

[Install]
WantedBy=default.target
" | tee /etc/systemd/system/kafka-connect.service

# Enabling the service
systemctl daemon-reload

service kafka-connect start

sleep 30

echo "{
  \"name\": \"product-source\",
  \"config\": {
    \"connector.class\": \"io.debezium.connector.mongodb.MongoDbConnector\",
    \"mongodb.hosts\": \"mongo-replica-set/$MONGODB:27017\",
    \"mongodb.name\": \"mongocdc\",
    \"mongodb.ssl.enabled\": false,
    \"mongodb.user\": \"debezium\",
    \"mongodb.password\": \"debezium\",
    \"collection.whitelist\": \"wishlist.product\",
    \"key.converter\": \"org.apache.kafka.connect.json.JsonConverter\",
    \"value.converter\": \"org.apache.kafka.connect.json.JsonConverter\"
  }
}" | curl -X POST -d @- http://localhost:8083/connectors --header "Content-Type:application/json"
