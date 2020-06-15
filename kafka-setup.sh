#!/bin/bash -xe

SERVER_IP=$1

KAFKA_VERSION="2.1.0"
SCALA_VERSION="2.12"

KAFKA_URL="http://archive.apache.org/dist/kafka/$KAFKA_VERSION/kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz"

apt-get -y update
apt-get -y install openjdk-11-jdk

wget -q -O kafka.tar.gz $KAFKA_URL

useradd -d /opt/kafka kafka
rm -rf /opt/kafka

tar -xzvf kafka.tar.gz -C /opt/
ln -s /opt/kafka_$SCALA_VERSION-$KAFKA_VERSION /opt/kafka

mkdir /opt/kafka/logs

chown -R kafka:kafka /opt/kafka /opt/kafka_$SCALA_VERSION-$KAFKA_VERSION

sed -i "s/#listeners=PLAINTEXT:\/\/:9092/listeners=PLAINTEXT:\/\/$SERVER_IP:9092/" /opt/kafka/config/server.properties

# Creating systemd service for ZooKeeper
echo "[Unit]
Description=ZooKeeper Service
Documentation=http://kafka.apache.org
Requires=network.target
After=network.target

[Service]
Type=simple
User=kafka
Group=kafka
Environment=LOG_DIR='/opt/kafka/logs'
ExecStart=/opt/kafka/bin/zookeeper-server-start.sh /opt/kafka/config/zookeeper.properties
ExecStop=/opt/kafka/bin/zookeeper-server-stop.sh

[Install]
WantedBy=default.target
" | tee /etc/systemd/system/zookeeper.service


# Creating systemd service for kafka
echo "[Unit]
Description=Kafka Service
Documentation=http://kafka.apache.org
Requires=network.target
After=network.target

[Service]
Type=simple
User=kafka
Group=kafka
Environment=LOG_DIR='/opt/kafka/logs'
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties
ExecStop=/opt/kafka/bin/kafka-server-stop.sh

[Install]
WantedBy=default.target
" | tee /etc/systemd/system/kafka.service

# Enabling the service
systemctl daemon-reload

service zookeeper start
service kafka start
