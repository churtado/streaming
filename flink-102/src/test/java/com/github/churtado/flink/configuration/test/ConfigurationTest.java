package com.github.churtado.flink.configuration.test;

import com.github.churtado.flink.configuration.RaiseAlertFlatmapFunction;
import com.github.churtado.flink.configuration.TempIncreaseAlertFunction;
import com.github.churtado.flink.utils.*;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

public class ConfigurationTest {

    Logger logger = LoggerFactory.getLogger(ConfigurationTest.class);

    /**
     * We're now looking into how to use
     * operators that keep state. Sometimes the state is
     * keyed, if you use key by to partition among operators.
     * Each operator will maintain its own keyed state.
     */

    private DataStream<SensorReading> readings;
    private StreamExecutionEnvironment env;
    private DataStream<SmokeLevel> smokeLevel;

    @BeforeEach
    public void GetReadings() {
        // set up the streaming execution environment
        env = StreamExecutionEnvironment.getExecutionEnvironment();

        // use event time for the application
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        env.enableCheckpointing(5000); // I think this can be configured per stream or env-wide
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        // configure the watermark interval
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                // assign timestamps and watermarks required for event time semantics and processing
                // make sure to do this as close to the source as possible
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5)));

         smokeLevel = env
                .addSource(new SmokeLevelSource())
                .setParallelism(1);

    }

    @Test
    public void TestSmokeAlerts() throws Exception {
        // this example uses co flatmap to join 2 streams, one with temp, and the other
        // with a smoke level stream. When the smoke level and temp is too high, then it emits an alarm
        // key sensors by id
        readings
                .keyBy(new KeySelector<SensorReading, String>() {
                    @Override
                    public String getKey(SensorReading sensorReading) throws Exception {
                        return sensorReading.id;
                    }
                })
                // connecting temp readings with smoke level
                .connect(smokeLevel.broadcast())
                // raising alerts on temp and smoke level via co flatmap
                .flatMap(new RaiseAlertFlatmapFunction())
                // mapping to tuple and outputting
                .map(new MapFunction<Alert, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(Alert alert) throws Exception {
                        return new Tuple2<>(alert.message, alert.timestamp);
                    }
                }).print();

        env.execute();
    }

    @Test
    public void TestPeriodicWaterMarks() throws Exception {
        // using a process function to emit warnings if temp monotonically increases within a second
        readings.keyBy(new KeySelector<SensorReading, String>() {
            @Override
            public String getKey(SensorReading sensorReading) throws Exception {
                return sensorReading.id;
            }
        }).process(new TempIncreaseAlertFunction())
                .print();

        env.execute();
    }
}
