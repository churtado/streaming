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

public class ConnectToKafkaTest {

    @Test
    public void TestKafkaConnection() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /**
         *
         * you could use this along with an operator that you
         * can force to throw an exception to see how that behaves.
         * Look to a more complicated example in this project.
         */
        env.enableCheckpointing(500);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));

        // configure AsyncLookupTest env
        env.setParallelism(1);

        // values are collected in a static variable
        CollectSink.values.clear();

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:29092");

        // only required for Kafka 0.8
//        properties.setProperty("zookeeper.connect", "localhost:2181");
        properties.setProperty("group.id", "AsyncLookupTest");

        DataStream<String> stream = env
                .addSource(new FlinkKafkaConsumer<String>("twitter_tweets", new SimpleStringSchema(), properties));

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
