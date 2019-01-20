Create the database in postgres using the scripts.sql file
Hook up the ETL's to the database

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

########### DB pipeline ##############
kafka-topics.sh --zookeeper zookeeper:2181 --alter --topic pipeline-event --config retention.ms=1000

# Create connectors
# See insomnia

# Purge the topic
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name postgres_event_sensor_reading --add-config retention.ms=1000
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name postgres_event_sensor_reading --add-config retention.ms=86400000

########### Setup influxdb ##############
# run influxdb if not running
docker run -d -p 8086:8086 --net=influxdb influxdb

# run influx
docker exec -it influxdb influx
# create a user
CREATE USER admin WITH PASSWORD 'password' WITH ALL PRIVILEGES;

########### Setup twitter topic ##############
kafka-topics --zookeeper zookeeper:2181 --create --topic twitter_tweets --partitions 6 --replication-factor 1




########### Misc
To purge a kafka topic, set its retention policy to 1 second:
kafka-topics.sh --zookeeper zookeeper:2181 --alter --topic pipeline-event --config retention.ms=1000
Set it to one day:
kafka-topics.sh --zookeeper zookeeper:2181 --alter --topic pipeline-event --config retention.ms=86400000