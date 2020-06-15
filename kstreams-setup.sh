#!/bin/bash -xe

KAFKA=$1
UPDATE_STRATEGY=$2

MAVEN_VERSION="3.6.0"
MAVEN_URL="http://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"

# Download Maven to build and run our KStreams app
wget -q -O maven.tar.gz $MAVEN_URL
tar -xzvf maven.tar.gz -C /opt/
ln -s /opt/apache-maven-$MAVEN_VERSION /opt/maven

# Create the Kafka topic for the merged documents
/opt/kafka/bin/kafka-topics.sh --zookeeper localhost:2181 --partitions 1 --replication-factor 1 --create --topic product

sh -c "cd /vagrant/kstreams && /opt/maven/bin/mvn compile exec:java -Dexec.mainClass=com.github.gh_mlfowler.mongocdcdemo.MongoCDCKStream \
  -Dexec.cleanupDaemonThreads=false \
  -Dexec.args=\" \
  --in=mongocdc.wishlist.product \
  --out=product --update=$UPDATE_STRATEGY \
  --kafka=$KAFKA:9092\" > /home/vagrant/kstreams.out 2> /home/vagrant/kstreams.err & "
