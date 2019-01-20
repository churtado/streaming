package com.github.churtado.flink.external.twitter.test;

import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The consumer is capable of discovering topics based on a regex.
 * That's easy to do so I won't do it.
 *
 * Makes sure that the consumer is configured to commit offsets stored
 * in checkpointed states. This is easy to do.
 *
 * Flink can be configured to show metrics
 *
 * Flink can be configured to use Kerberos
 */

public class ConfigureStartingPointTest {

    @Test
    public void TestKafkaConnection() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /**
         * Important to enable checkpointing in order to recover from failure
         */
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));

        // configure AsyncLookupTest env
        env.setParallelism(1);

        // values are collected in a static variable
        CollectSink.values.clear();

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:29092");

        // only required for Kafka 0.8
        // properties.setProperty("zookeeper.connect", "localhost:2181");
        properties.setProperty("group.id", "AsyncLookupTest");

        FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<String>("twitter_tweets", new SimpleStringSchema(), properties);

        consumer.setStartFromEarliest();
//        consumer.setStartFromLatest();
//        consumer.setStartFromTimestamp();
//        consumer.setStartFromGroupOffsets(); // default


        DataStream<String> stream = env
                .addSource(consumer);

        stream.print();

        env.execute();

//        assertEquals(Lists.newArrayList(2L, 42L, 44L), CollectSink.values);
    }

    // create a testing sink
    private static class CollectSink implements SinkFunction<String> {

        // must be static
        public static final List<String> values = new ArrayList();

        @Override
        public synchronized void invoke(String value, SinkFunction.Context context) throws Exception {
            values.add(value);
        }
    }
}
