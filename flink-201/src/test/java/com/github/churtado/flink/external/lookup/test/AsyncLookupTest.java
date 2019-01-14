package com.github.churtado.flink.external.lookup.test;

import com.github.churtado.flink.external.lookup.PostgresAsyncFunction;
import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SensorSource;
import com.github.churtado.flink.utils.SensorTimeAssigner;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


public class AsyncLookupTest {
    static Logger logger = LoggerFactory.getLogger(AsyncLookupTest.class.getName());

    private StreamExecutionEnvironment env;
    private InfluxDBConfig influxDBConfig;
    private DataStream<SensorReading> readings;

    @BeforeEach
    public void setup() {

        // set up flink
        env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Important to enable checkpointing in order to recover from failure
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        // generate some fake data
        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5))); // you need this if using event time

    }

    /**
     * Async lookup to an external data source
     * to enrich it. Because it's async, the stream won't wait to keep
     * processing data, and once it gets back the external result it
     * will keep pushing the enriched data. External systems must be able
     * to handle async requests, but if not, then flink can spawn multiple
     * threads that send requests.
     *
     * The order of the data can be maintained in flink, or you can disregard it
     *
     * I believe the table is just a list of the sensors by name, and a color field.
     * So, every sensor (in the case of our data source it has 10 at the moment of
     * writing) has a unique color: (sensor_1, red), (sensor_2, blue),....
     */
    @Test
    @DisplayName("Testing influxdb sink")
    public void TestAsyncLookup() throws Exception {

        /**
         * We're using ordered wait to conserve the order of input events,
         * but we could use an unordered version of the method that just
         * makes sure the data is checkpointed as it's waiting, so it can
         * recover from failure, etc.
         */
        DataStream<Tuple2<String, String>> sensorColors = AsyncDataStream
                .orderedWait(
                        readings,
                        new PostgresAsyncFunction(),
                        5, TimeUnit.SECONDS,  // timeout requests after 5 seconds
                        100                   // at most 100 concurrent requests
                );

        sensorColors.print();

        env.execute();

    }

}
