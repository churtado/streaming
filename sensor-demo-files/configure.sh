#!/usr/bin/env bash
# Create the database in postgres using the scripts.sql file
# Hook up the ETL's to the database

########### Source to text ###########
# Purge the topic with legacy commands
kafka-topics --zookeeper zookeeper:2181 --alter --topic text-sensor_reading --config retention.ms=1000
kafka-topics --zookeeper zookeeper:2181 --alter --topic text-sensor_reading --config retention.ms=86400000

# Purge the topic with current commands
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name text-sensor_reading --add-config retention.ms=1000
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name text-sensor_reading --add-config retention.ms=86400000

# Create a connector
# See insomnia

# Create a console consumer
kafka-console-consumer --bootstrap-server localhost:9092 --topic text-sensor_reading --from-beginning
kafka-console-consumer --bootstrap-server localhost:9092 --topic postgres_event_avro_sensor_reading --from-beginning

########### DB pipeline ##############
kafka-topics.sh --zookeeper zookeeper:2181 --alter --topic pipeline-event --config retention.ms=1000

# Create connectors
# See insomnia: text, source, sink and avro connectors need to be created

# Purge the topics
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name postgres_event_sensor_reading --add-config retention.ms=1000
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name postgres_event_sensor_reading --add-config retention.ms=86400000

kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name postgres_event_avro_sensor_reading --add-config retention.ms=1000
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name postgres_event_avro_sensor_reading --add-config retention.ms=300000
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name postgres_event_avro_sensor_reading --add-config retention.ms=86400000

kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name json_sensor_readings --add-config retention.ms=1000
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name smoke_alerts --add-config retention.ms=300000


########### NOTE: if the container exists, use docker start

########### Setup influxdb ##############
# run influxdb if not running
docker run -d -p 8086:8086 --net=influxdb influxdb

# run influx
docker exec -it influxdb influx
# create a user
CREATE USER admin WITH PASSWORD 'password' WITH ALL PRIVILEGES;
# create the database
CREATE DATABASE sensor_readings;

########### Setup twitter topic ##############
kafka-topics --zookeeper zookeeper:2181 --create --topic twitter_tweets --partitions 6 --replication-factor 1

########### Setup flink
# See this and run: https://github.com/churtado/sensor_reading.git

########### Retention policy on influxdb
CREATE RETENTION POLICY one_hour ON sensor_readings DURATION 1h0m0s REPLICATION 1 SHARD DURATION 1h0m0s
CREATE RETENTION POLICY autogen ON sensor_readings DURATION 1h0m0s REPLICATION 1 SHARD DURATION 1h0m0s DEFAULT


########### Misc
# In case you want to only look at logs for one Confluent component
docker-compose up | grep connect > /var/log/connect.log &
tail -f /var/log/connect.log