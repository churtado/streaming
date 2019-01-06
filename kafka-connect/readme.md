This is an example that uses kafka connect to establish a connector that produces twitter data into a kafka topic.

How the files were set up:

First, the connector itself was downloaded as a release from its github page, and the contents of the release tar
were extracted into the kafka-connect-twitter directory.

Second, the connect-standalone.properties file was copied over from an install of kafka and the last line was added and
the variable specified was made to point to the connectors directory.

Once that was done, the twitter.properties file was built from an example in the github page of the twitter connector.

Make sure you get the proper authentication values in the properties file.



After that was done, a few topics were created in kafka:
kafka-topics --zookeeper localhost:2181 --create --topic twitter_status_connect --partitions 3 --replication-factor 1
kafka-topics --zookeeper localhost:2181 --create --topic twitter_deletes_connect --partitions 3 --replication-factor 1

After that, in order to test, a console consumer was started up to check on the data being pushed:
kafka-console-consumer --bootstrap-server localhost:9092 --topic twitter_status_connect --from-beginning

Finally, the connector itself was created:
cd <root>/streaming/kafka-connect/connectors
connect-standalone connect-standalone.properties twitter.properties

So, to sum up, just need to have the connect script in your path somewhere, define a file for general connector
properties and finally define your own connector properties and call the script and that should do.

Optionally you could configure all this in a shell script and have this start as a service in linux/mac.

The above only works if you have kafka installed and may vary if you are using confluent.

With confluent, just run the following:
confluent load twitter_source -d confluent_twitter.properties

If you need to manipulate the connector in confluent, you can do so through the REST API
delete connector:   curl -X DELETE localhost:8083/connectors/twitter_source
list plugins:       curl localhost:8083/connector-plugins | jq
list connectors:    curl localhost:8083/connectors
pause connector:    curl -X PUT localhost:8083/connectors/twitter_source/pause
resume connector:   curl -X PUT localhost:8083/connectors/twitter_source/resume
status:             curl localhost:8083/connectors/twitter_source/status | jq