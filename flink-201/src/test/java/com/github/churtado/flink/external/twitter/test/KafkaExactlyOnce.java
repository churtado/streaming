package com.github.churtado.flink.external.twitter.test;

import com.github.churtado.flink.external.twitter.Tweet;
import com.github.churtado.flink.external.twitter.TweetSerializer;
import com.github.churtado.flink.external.postgres.TwitterPostgresSink;
import com.github.churtado.flink.external.twitter.TwitterTimestampAssigner;
import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer011;
import org.apache.flink.streaming.util.serialization.JSONKeyValueDeserializationSchema;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class KafkaExactlyOnce {

    static Logger logger = LoggerFactory.getLogger(KafkaExactlyOnce.class.getName());

    // use your own credentials - don't share them with anyone
    String consumerKey;
    String consumerSecret;
    String token;
    String secret;

    List<String> terms;

    Client client;
    BlockingQueue<String> msgQueue;
    KafkaProducer<String, String> producer;

    @BeforeEach
    public void setup() {

        // use your own credentials - don't share them with anyone
        consumerKey = "ilW2hGXZGk7ZqWbdBKu4ROuPy";
        consumerSecret = "hdE5rz0pc8fpLIb7sJqi3jXMWXlxSyQFHVm8pR6F721vjRoGWi";
        token = "29465706-inRbQ5ZD5NsGpIB2ZAxJHjZzmQSplPgRZtL9pHhvx";
        secret = "poJC6iwIwciza8nXI4WT3ANSFlHRTCN4SfsGxy5Nxour7";

        terms = Lists.newArrayList("bitcoin", "usa", "politics", "sport", "soccer");

        logger.info("Setup");

        /** Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
        msgQueue = new LinkedBlockingQueue<String>(1000);

        // create a twitter client
        client = createTwitterClient(msgQueue);
        // Attempts to establish a connection.
        client.connect();

        // create a kafka producer
        producer = createKafkaProducer();

        // add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("stopping application...");
            logger.info("shutting down client from twitter...");
            client.stop();
            logger.info("closing producer...");
            producer.close();
            logger.info("done!");
        }));
    }

    @Test
    public void TestString(){
        String key = "abd989";
        System.out.println(key.getBytes());
    }

    /**
     * Testing a simple kafka producer. We're going to be using
     * the latest version of kafka that provides exactly once semantics
     * for producers. Make sure of several things in order to make
     * sure exactly once semantics are met with Kafka producers:
     *
     * By default, the Flink Kafka sink sets transaction.timeout.ms to
     * one hour, which means that you probably need to adjust the
     * transaction.max.timeout.ms property of your Kafka setup, which
     * is set to 15 minutes by default.
     *
     * Other properties:
     * acks set to all
     *
     * See kafka docs to set properly:
     * log.flush.interval.messages set to 100k messages or so
     * log.flush.interval.ms set to maybe every 10 mins
     * log.flush.*
     *
     * in general it's actually recommended by Kafka to set up
     * replication and not override the log flush variables
     * and let the OS handle it as it's more efficient that way
     *
     * To make sure the logs are flushed to disk
     *
     * For this AsyncLookupTest we'll assume kafka is properly
     * configured
     *
     * log.retention.ms and log.retention.bytes will determine how long
     * messages are retained in kafka. These can be set in the broker
     * or in a topic
     *
     */
    @Test
    @DisplayName("Flink application with exactly once producer to Kafka")
    public void TestExactlyOnce() throws Exception {

        /**
         * Because we want to AsyncLookupTest everything end to end
         * we'll start the twitter producer in a separate thread.
         * This producer will feed our input topic. We'll then
         * produce to kafka into an output topic with exactly
         * once guarantees
          */
        Thread producerThread = new Thread(new TweetProducer ());
        producerThread.start();

        // set up flink
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Important to enable checkpointing in order to recover from failure
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        // ###############################  set up kafka consumer  ###############################
        Properties consumerProperties = new Properties();
        consumerProperties.setProperty("bootstrap.servers", "localhost:29092");

        // only required for Kafka 0.8
        // properties.setProperty("zookeeper.connect", "localhost:2181");
        consumerProperties.setProperty("group.id", "AsyncLookupTest");

        // because were using a keyed schema we can use exactly once semantics with the producer
        FlinkKafkaConsumer<ObjectNode> consumer = new
                FlinkKafkaConsumer<ObjectNode>(
                        "keyed_twitter_tweets",
                new JSONKeyValueDeserializationSchema(true),
                consumerProperties);

        consumer.setStartFromLatest();


        // ###############################  set up kafka producer  ###############################
        Properties producerProperties = new Properties();
        producerProperties.setProperty("bootstrap.servers","localhost:29092");
        producerProperties.setProperty("acks","all");
        producerProperties.setProperty("enable.idempotence","true");
        producerProperties.setProperty("retries","3");
        producerProperties.setProperty("transaction.timeout.ms","5000");

        FlinkKafkaProducer011<Tweet> producer011 = new FlinkKafkaProducer011<>(
                "flink_sink",
                new TweetSerializer(),
                producerProperties,
                FlinkKafkaProducer011.Semantic.EXACTLY_ONCE
        );

        /**
         *  Enabling this will let the producer only
         *  log failures instead of catching and rethrowing them.
         *  This essentially accounts the record to have succeeded,
         *  even if it was never written to the target Kafka topic.
         *  This must be disabled for at-least-once.
         */
        producer011.setLogFailuresOnly(true);


        // Set event time as timestamp sent to Kafka
        producer011.setWriteTimestampToKafka(true);

        KeyedStream<Tweet, Long> tweets =
                env
                .addSource(consumer)
                .assignTimestampsAndWatermarks(new TwitterTimestampAssigner())
                .keyBy(new KeySelector<ObjectNode, String>() {
                    @Override
                    public String getKey(ObjectNode value) throws Exception {
                        return value.get("key").asText();
                    }
                })
                .map(new MapFunction<ObjectNode, Tweet>() {
                    @Override
                    public Tweet map(ObjectNode value) throws Exception {

                        Tweet tweet = new Tweet();
                        tweet.id = value.get("value").get("id").asLong();
                        tweet.createdAt = value.get("value").get("created_at").asText();
                        tweet.text = value.get("value").get("text").asText();
                        tweet.timestamp = value.get("value").get("timestamp_ms").asLong();

                        return tweet;
                    }
                })
                .keyBy(new KeySelector<Tweet, Long>() {
                    @Override
                    public Long getKey(Tweet value) throws Exception {
                        return value.id;
                    }
                });


        // tweets.addSink(producer011);

        tweets.addSink(new TwitterPostgresSink());

        env.execute();
    }

    private Client createTwitterClient(BlockingQueue<String> msgQueue){

        /** Declare the host you want to connect to, the endpoint, and authentication (basic auth or oauth) */
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();

        hosebirdEndpoint.trackTerms(terms);

        // These secrets should be read from a config file
        Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);

        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-Client-01")                              // optional: mainly for the logs
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));

        Client hosebirdClient = builder.build();
        return hosebirdClient;
    }

    private KafkaProducer<String, String> createKafkaProducer(){
        String bootstrapServers = "127.0.0.1:29092";

        // create Producer properties
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // create safe Producer
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5"); // kafka 2.0 >= 1.1 so we can keep this as 5. Use 1 otherwise.

        // high throughput producer (at the expense of a bit of latency and CPU usage)
        properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32*1024)); // 32 KB batch size

        // create the producer
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);
        return producer;
    }

    class TweetProducer implements Runnable {

        @Override
        public void run() {
            // loop to send tweets to kafka
            // on a different thread, or multiple different threads....

            ObjectMapper mapper = new ObjectMapper();
            JsonNode tweet = null;

            while (!client.isDone()) {
                String msg = null;
                try {
                    msg = msgQueue.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    client.stop();
                }
                if (msg != null){

                    try {
                        tweet = mapper.readTree(msg);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String id = tweet.get("id").asText();

                    logger.info(msg);
                    producer.send(new ProducerRecord<>("keyed_twitter_tweets", id, msg), new Callback() {
                        @Override
                        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                            if (e != null) {
                                logger.error("Something bad happened", e);
                            }
                        }
                    });
                } else {
                    logger.info("nothing was returned from twitter");
                }
            }
            logger.info("End of AsyncLookupTest");
        }
    }
}
