package com.github.churtado.flink.external.twitter.test;

import com.github.churtado.flink.external.twitter.Tweet;
import com.github.churtado.flink.external.twitter.TweetSerializer;
import com.github.churtado.flink.external.postgres.TwitterPostgresSink;
import com.github.churtado.flink.external.twitter.TwitterTimestampAssigner;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.io.jdbc.JDBCOutputFormat;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer011;
import org.apache.flink.streaming.util.serialization.JSONKeyValueDeserializationSchema;
import org.junit.jupiter.api.Test;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Deserialize json from tweet into an object
 * Remember: if you want to read in parallel in a consumer group,
 * you have to make sure the topic has as many partitions as there
 * are parallel instances of the source operator
 *
 * Flink sources maintain the offset of their respective partition
 * in case it needs to recover it from a crash. Consumer groups aren't
 * necessary for Flink.
 */

public class TwitterToPostgresTest {


    Logger logger = LoggerFactory.getLogger(TwitterToPostgresTest.class.getName());

    @Test
    public void ConvertStringToDate() {
        String dateText = "Sun Dec 23 14:46:44 +0000 2018";
        String formatMask = "EEE MMM dd HH:mm:ss XXXX yyyy";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatMask);

        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateText, formatter);
            Long a = 0L;
            a = zonedDateTime.toEpochSecond();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void TwitterToPostgres() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /**
         * Important to enable checkpointing in order to recover from failure
         */
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));

        // configure AsyncLookupTest env
        env.setParallelism(1);

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");

        // only required for Kafka 0.8
        // properties.setProperty("zookeeper.connect", "localhost:2181");
        properties.setProperty("group.id", "AsyncLookupTest");

        /**
         * this can be a regex to match multiple topics. If this is the case,
         * flink kafka connector treats all partitions of all topics the same
         * and multiplexes their events into a single stream
         *
         * Next specify a DeserializationSchema or KeyedDeserializationSchema
         * to know how to process the raw byte messages received from Kafka.
         */
        FlinkKafkaConsumer<ObjectNode> consumer = new
                FlinkKafkaConsumer<ObjectNode>("twitter_tweets", new JSONKeyValueDeserializationSchema(true), properties);

        /**
         * Flink chan be set to automatically discover new partitions added to a topic!
         * See flink.partition-discovery.interval-millis property and how toe set it
         * in the Properties object
         */
        consumer.setStartFromEarliest()
                /*.assignTimestampsAndWatermarks()*/;

        DataStream<ObjectNode> stream = env
                .addSource(consumer).assignTimestampsAndWatermarks(new TwitterTimestampAssigner());


        KeyedStream<ObjectNode, String> tweets = stream.keyBy(new KeySelector<ObjectNode, String>() {
            @Override
            public String getKey(ObjectNode value) throws Exception {
                return value.get("key").asText();
            }
        });

        /**
         * Here we're taking the tweets and mapping them to POJO's
         */
        KeyedStream<Tweet, Long> processed = tweets.map(new MapFunction<ObjectNode, Tweet>() {

            @Override
            public Tweet map(ObjectNode value) throws Exception {

                Tweet tweet = new Tweet();
                tweet.id = value.get("value").get("id").asLong();
                tweet.createdAt = value.get("value").get("created_at").asText();
                tweet.text = value.get("value").get("text").asText();
                tweet.timestamp = value.get("value").get("timestamp_ms").asLong();

                return tweet;
            }
        }).keyBy(new KeySelector<Tweet, Long>() {
            @Override
            public Long getKey(Tweet value) throws Exception {
                return value.id;
            }
        });

        /**
         * Now let's make a Kafka Producer to sink this to
         */


        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "localhost:9092");
        // only required for Kafka 0.8
        props.setProperty("zookeeper.connect", "localhost:2181");
        props.setProperty("group.id", "AsyncLookupTest");

        FlinkKafkaProducer011<Tweet> producer = new FlinkKafkaProducer011<Tweet>(
                "localhost:9092",
                "processed_tweets",
                new TweetSerializer()
        );

        producer.setWriteTimestampToKafka(true);

        // processed.addSink(producer);

        /**
         * Let's try dumping this straight into postgresql
         */
        String query = "INSERT INTO tweets (id, text, created_at, timestamp_ms) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET text=?, created_at=?, timestamp_ms=?";

        JDBCOutputFormat jdbcOutput = JDBCOutputFormat.buildJDBCOutputFormat()
                .setDrivername("org.postgresql.Driver")
                .setDBUrl("jdbc:postgresql://localhost:5432/twitter?user=postgres&password=password")
                .setQuery(query)
                .setSqlTypes(new int[] { Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.BIGINT })
                .finish();


        processed.addSink(new TwitterPostgresSink());

        env.execute();

//        assertEquals(Lists.newArrayList(2L, 42L, 44L), CollectSink.values);
    }

}
