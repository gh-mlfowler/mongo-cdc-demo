# -*- mode: ruby -*-
# vi: set ft=ruby :

MONGO_HOST = "192.168.100.8"
KAFKA_HOST = "192.168.100.10"

UPDATE_STRATEGY = 1 # Resulting document
# UPDATE_STRATEGY = 2 # Before & After
# UPDATE_STRATEGY = 3 # Before, After & Delta

Vagrant.configure(2) do |config|

  config.vm.box = "ubuntu/bionic64"

  config.vm.define "mongodb" do |mongodb|
    mongodb.vm.network "private_network", ip: MONGO_HOST
    mongodb.vm.network "forwarded_port", guest: 27107, host: 27107
    mongodb.vm.provision "file", source: "wishlist.json", destination: "wishlist.json"
    mongodb.vm.provision "shell", path: "mongodb-setup.sh", args: MONGO_HOST
  end

  config.vm.define "kafka" do |kafka|
    kafka.vm.provider "virtualbox" do |v|
      v.memory = 2048
    end
    kafka.vm.network "private_network", ip: KAFKA_HOST
    kafka.vm.network "forwarded_port", guest: 9092, host: 9092
    kafka.vm.provision "file", source: "connect-distributed.properties", destination: "connect-distributed.properties"
    kafka.vm.provision "file", source: "confluent.tgz", destination: "confluent.tgz"
    kafka.vm.provision "shell", path: "kafka-setup.sh", args: KAFKA_HOST
    kafka.vm.provision "shell", path: "schema-registry-setup.sh"
    kafka.vm.provision "shell", path: "debezium-setup.sh", args: [MONGO_HOST, KAFKA_HOST]
    kafka.vm.provision "shell", path: "kstreams-setup.sh", args: [KAFKA_HOST, UPDATE_STRATEGY]
  end

end
