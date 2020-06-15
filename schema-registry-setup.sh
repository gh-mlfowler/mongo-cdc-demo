#!/bin/bash -xe
CONFLUENT_VERSION="5.0.1"
SCHEMA_REG_URL="https://github.com/confluentinc/schema-registry/archive/v$CONFLUENT_VERSION.tar.gz"

wget -q -O schema-registry.tar.gz $SCHEMA_REG_URL
tar -xzvf schema-registry.tar.gz -C /opt/
ln -s /opt/schema-registry-$CONFLUENT_VERSION /opt/schema-registry

tar -zxvf confluent.tgz -C /
chown -R kafka:kafka /opt/schema-registry

# Creating systemd service for schema registry
echo "[Unit]
Description=Kafka Schema Registry Service
Documentation=https://docs.confluent.io/current/schema-registry/docs/index.html
Requires=kafka.service
After=kafka.service

[Service]
Type=simple
User=kafka
Group=kafka
Environment=LOG_DIR='/opt/kafka/logs'
ExecStart=/opt/schema-registry/bin/schema-registry-start /opt/schema-registry/config/schema-registry.properties
ExecStop=/opt/schema-registry/bin/schema-registry-stop

[Install]
WantedBy=default.target
" | tee /etc/systemd/system/kafka-schema.service

# Enabling the service
systemctl daemon-reload

service kafka-schema start
