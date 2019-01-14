package com.github.churtado.flink.external.file.test;

import com.github.churtado.flink.external.file.TransactionalFileSink;
import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SensorSource;
import com.github.churtado.flink.utils.SensorTimeAssigner;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionalSinkTest {

    static Logger logger = LoggerFactory.getLogger(TransactionalSinkTest.class.getName());

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
     * 2PC file sink with our sensor reading source
     */
    @Test
    @DisplayName("Testing 2PC file sink")
    public void TestTransactionalFileSink() throws Exception {
        readings
                .map(new MapFunction<SensorReading, Tuple2<String, Double>>() {
                    @Override
                    public Tuple2<String, Double> map(SensorReading value) throws Exception {
                        return new Tuple2<>(value.id, value.temperature);
                    }
                })
                .addSink(new TransactionalFileSink(
                        "/tmp/tsx_sink_tmp/",
                        "/tmp/tsx_sink_trgt",
                        new KryoSerializer<String>(String.class, new ExecutionConfig()),
                        new KryoSerializer<Void>(Void.class, new ExecutionConfig())
                ));
    }
}
