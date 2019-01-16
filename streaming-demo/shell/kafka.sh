# list kafka topics
kafka-topics --zookeeper zookeeper:2181 --list

# delete topics
kafka-topics --zookeeper zookeeper:2181 --delete --topic text-event
kafka-topics --zookeeper zookeeper:2181 --delete --topic profile-event

# delete connectors
curl -X DELETE http://localhost:8083/connectors/source-text
curl -X DELETE http://localhost:8083/connectors/source_event
curl -X DELETE http://localhost:8083/connectors/sink_event

############# DB to Text ###########
# create the topic
kafka-topics --create --topic text-event --zookeeper zookeeper:2181 --replication-factor 1 --partitions 6
# create the connector on non-docker
confluent load text_test -d ~/source-text.properties
# create the connector on docker
curl -X POST -H "Content-Type: application/json" --data '{ "name": "source-text", "config": { "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector", "tasks.max": 1, "connection.url": "jdbc:postgresql://192.168.1.151/source?user=postgres&password=password", "mode": "timestamp+incrementing", "timestamp.column.name":"event_date", "incrementing.column.name": "event_id", "topic.prefix": "text-", "value.converter": "org.apache.kafka.connect.storage.StringConverter", "table.whitelist":"event_data" } }' http://192.168.1.151:8083/connectors


# create the consumer
kafka-console-consumer --bootstrap-server 192.168.1.151:9092 --topic text-event --from-beginning


############# DB to DB #############
kafka-topics --create --topic profile-event --zookeeper zookeeper:2181 --replication-factor 1 --partitions 6
confluent load source_event -d source-event.properties
confluent load sink_event -d sink-event.properties

