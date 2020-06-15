# Debezium Demo: MongoDB -> Kafka Streams

[Debezium](https://debezium.io/) allows us to use the oplog of [MongoDB](https://www.mongodb.com/) as a Change Data Capture stream into [Kafka](https://kafka.apache.org/). However, unlike most CDC systems, MongoDB only provides the resulting change, not a complete record of before and after the change. By using [Kafka Streams](https://kafka.apache.org/documentation/streams/), we can manipulate the data and produce the before and after records - in fact we can go a step further and compute the delta too.

This demo relates to a blog post authored for Confluent which itself is based on a presentation that was to be given at the cancelled London Kafka Summit 2020.

## Overview

This project builds a two node cluster:

* MongoDB
* Kafka

To begin, run `vagrant up`. The boxes are created and configured in order such that once they are all running, Debezium will be reading the data from MongoDB, writing it to Kafka and Kafka Streams will be merging the data to produce a stream of complete documents. You can then connect to individual nodes (e.g. `vagrant ssh mongodb`) and explore. Once you're finised, run `vagrant destroy` to clear everything down.

### MongoDB

MongoDB 4.2 is installed by with the `mongodb-setup.sh` script. A replica set is created and loaded with a couple of documents. A second shell script, `mongodb-updates.sh` is forked which makes a randomised changed to a `price` every minute in order to provide a continuous stream of changes to Kafka.

### Kafka

This box takes a little while to provison owing to the amount of Java dependencies that are fetched as part of compiling some of the components. The long sequence of Bash scripts perform the following:

#### `kafka-setup.sh`

* Install JDK with apt
* Download & configure Apache Kafka
* Write systemd service files for ZooKeeper and Kafka
* Start ZooKeeper & Kafka services

#### `schema-registry-setup.sh`

* Download & configure Schema Registry
* Uncompress precompiled Confluent tools
* Write systemd service for Schema Registry and start the service

#### `debezium-setup.sh`

* Download the Debezium releases and place in the filesystem
* Overwrite the default `connect-distributed.properties`
* Write systemd service file for Kafka Connect and start the service
* Configure the MongoDB source through the Kafka Connect web service

## Results

The moment the MongoDB source is configured, Debezium will connect to the database and fetch the existing documents for the replica set, and write them to a topic in Kafka and then start reading changes as they occur. The log file `connect-distributed.out` is quite detailed and notes the time it takes for each stage to complete.

There are three different output formats determined by the `UPDATE_STRATEGY` declared in the `Vagrantfile`. The first produces the final document after each change has been applied. The second produces a document with a before and after image containing the old and new version of the document after each change. The third is the same as the second with the addition of a delta that show how much numeric and date/time fields have changed.

To read the final documents, connect to the Kafka node and run the following, pressing CTRL-C to terminate:

```
$ vagrant ssh kafka -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server 192.168.100.10:9092 --from-beginning --topic product"
{"_id": {"$oid": "5ee6c263635cdee38c1232e1"},"item": "Software Engineering at Google: Lessons Learned from Programming Over Time","target_prize": 20,"store": "bookdepository.com","price": 31.99,"last_check": "2020-05-24T16:05:45Z","url": "https://www.bookdepository.com/Software-Engineering-at-Google-Titus-Winters-Hyrum-Wright-Tom-Manshrek/9781492082798"}
{"_id": {"$oid": "5ee6c263635cdee38c1232e2"},"item": "Software Engineering at Google: Lessons Learned from Programming Over Time","target_prize": 20,"store": "amazon.co.uk","price": 31.99,"last_check": "2020-05-24T16:05:43Z","url": "https://www.amazon.co.uk/dp/1492082791/"}
{"_id":{"$oid":"5ee6c263635cdee38c1232e2"},"item":"Software Engineering at Google: Lessons Learned from Programming Over Time","target_prize":20,"store":"amazon.co.uk","price":"23.92","last_check":"2020-06-15T00:41:47Z","url":"https://www.amazon.co.uk/dp/1492082791/"}
^CProcessed a total of 3 messages
Connection to 127.0.0.1 closed.
```
