package com.github.churtado.flink.basics.test;

import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SensorSource;
import com.github.churtado.flink.utils.SensorTimeAssigner;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.ConnectedStreams;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoMapFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

public class ConnectStreamTest {

    Logger logger = LoggerFactory.getLogger(ConnectStreamTest.class);

    /**
     * We're now looking into how to use
     * operators that keep state. Sometimes the state is
     * keyed, if you use key by to partition among operators.
     * Each operator will maintain its own keyed state.
     */

    private DataStream<SensorReading> readings;
    private StreamExecutionEnvironment env;
    DataStream<SensorReading> first;
    DataStream<SensorReading> second;

    @BeforeEach
    public void GetReadings() {
        // set up the streaming execution environment
        env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5))); // you need this if using event time

        first = readings.map(new MapFunction<SensorReading, SensorReading>() {
            @Override
            public SensorReading map(SensorReading reading) throws Exception {
                SensorReading result = new SensorReading();
                result.id = reading.id;
                result.timestamp = reading.timestamp;
                result.temperature = (reading.temperature - 32) * (5.0 / 9.0);
                result.location = "paris";

                return result;
            }
        });

        second = readings.map(new MapFunction<SensorReading, SensorReading>() {
            @Override
            public SensorReading map(SensorReading reading) throws Exception {
                SensorReading result = new SensorReading();
                result.id = reading.id;
                result.timestamp = reading.timestamp;
                result.temperature = (reading.temperature - 32) * (5.0 / 9.0) - 10;
                result.location = "tokyo";

                return result;
            }
        });
    }

    @Test
    public void TestNonDeterministicConnect() throws Exception {

        // connect is useful when you want to connect to streams without any condition, such as a key
        // this is non-deterministic
        ConnectedStreams<SensorReading, SensorReading> connected = first.connect(second);
        connected.map(new CoMapFunction<SensorReading, SensorReading, Tuple3<String, Double, Long>>() {
            @Override
            public Tuple3<String, Double, Long> map1(SensorReading sensorReading) throws Exception {
                return new Tuple3<>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
            }

            @Override
            public Tuple3<String, Double, Long> map2(SensorReading sensorReading) throws Exception {
                return new Tuple3<>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
            }
        })
                .print();
        env.execute();
    }

    @Test
    public void TestDeterministicConnect() throws Exception {
        // if done this way, all partitions by key will be sent to same operator instance
        // operator instance would have access to keyed state
        ConnectedStreams<SensorReading, SensorReading> connected = first
                .connect(second)
                .keyBy("id", "id");

        connected.map(new CoMapFunction<SensorReading, SensorReading, Tuple4<String, String, Double, Long>>() {
            @Override
            public Tuple4<String, String, Double, Long> map1(SensorReading sensorReading) throws Exception {
                return new Tuple4<String, String, Double, Long>(sensorReading.id, sensorReading.location, sensorReading.temperature, sensorReading.timestamp);
            }

            @Override
            public Tuple4<String, String, Double, Long> map2(SensorReading sensorReading) throws Exception {
                return new Tuple4<String, String, Double, Long>(sensorReading.id, sensorReading.location, sensorReading.temperature, sensorReading.timestamp);
            }
        })
                .print();
        env.execute();
    }

    @Test
    public void TestBroadcastConnect() throws Exception {
        // In this instance, we broadcast the second, so it would be sent out to all operator instances
        ConnectedStreams<SensorReading, SensorReading> connected = first
                // broadcasting
                .connect(second.broadcast())
                .keyBy("id", "id");

        connected.map(new CoMapFunction<SensorReading, SensorReading, Tuple4<String, String, Double, Long>>() {
            @Override
            public Tuple4<String, String, Double, Long> map1(SensorReading sensorReading) throws Exception {
                return new Tuple4<String, String, Double, Long>(sensorReading.id, sensorReading.location, sensorReading.temperature, sensorReading.timestamp);
            }

            @Override
            public Tuple4<String, String, Double, Long> map2(SensorReading sensorReading) throws Exception {
                return new Tuple4<String, String, Double, Long>(sensorReading.id, sensorReading.location, sensorReading.temperature, sensorReading.timestamp);
            }
        })
                .print();

        env.execute();
    }
}
