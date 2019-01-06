package com.github.churtado.flink.basics.test;

import com.github.churtado.flink.basics.AverageFunction;
import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SensorSource;
import com.github.churtado.flink.utils.SensorTimeAssigner;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

public class BasicsTest {

    Logger logger = LoggerFactory.getLogger(BasicsTest.class);

    /**
     * We're now looking into how to use
     * operators that keep state. Sometimes the state is
     * keyed, if you use key by to partition among operators.
     * Each operator will maintain its own keyed state.
     */

    private DataStream<SensorReading> readings;
    private StreamExecutionEnvironment env;
    DataStream<SensorReading> celsius;

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

        // convert to celsius
        celsius = readings.map(new MapFunction<SensorReading, SensorReading>() {
            @Override
            public SensorReading map(SensorReading reading) throws Exception {
                SensorReading result = new SensorReading();
                result.id = reading.id;
                result.timestamp = reading.timestamp;
                result.temperature = (reading.temperature - 32) * (5.0 / 9.0);

                return result;
            }
        });
    }

    /**
     * this is to test flatmap and filter
     * @throws Exception
     */
    @Test
    @DisplayName("Simple ValueState example using flatmap")
    public void TestFlatMap() throws Exception {

        celsius
                .flatMap(new FlatMapFunction<SensorReading, String>() {
                    @Override
                    public void flatMap(SensorReading sensorReading, Collector<String> collector) throws Exception {
                        String[] results = sensorReading.id.split("_");
                        for(String result: results) {
                            if(result != "sensor") {
                                collector.collect(result);
                            }
                        }
                    }
                })
                .returns(String.class)  // forgot what this is for
                .print();

        env.execute();
    }

    /**
     * 5-second window averages
     * @throws Exception
     */
    @Test
    @DisplayName("Simple ValueState example using flatmap")
    public void TestTimeWindow() throws Exception {

        celsius
                .keyBy(new KeySelector<SensorReading, String>() {
                    @Override
                    public String getKey(SensorReading reading) throws Exception {
                        return reading.id;
                    }
                })
                .timeWindow(Time.seconds(5))
                .apply(new AverageFunction())
                .map(new MapFunction<SensorReading, Tuple3<String, Double, Long>>() {
                    @Override
                    public Tuple3<String, Double, Long> map(SensorReading sensorReading) throws Exception {
                        return new Tuple3<String, Double, Long>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
                    }
                })
                .print();

        env.execute();
    }

    /**
     * rolling aggregators with keyed stream
     * @throws Exception
     */
    @Test
    @DisplayName("Simple ValueState example using flatmap")
    public void TestRollingAggregator() throws Exception {

        celsius
                .keyBy(new KeySelector<SensorReading, String>() {
                    @Override
                    public String getKey(SensorReading reading) throws Exception {
                        return reading.id;
                    }
                })
                .map(new MapFunction<SensorReading, Tuple3<String, Double, Long>>() {
                    @Override
                    public Tuple3<String, Double, Long> map(SensorReading sensorReading) throws Exception {
                        return new Tuple3<String, Double, Long>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
                    }
                })
                .keyBy(0)
                .sum(2) // maintains a rolling sum for each of the 10 keys
                .print();

        env.execute();
    }

    /**
     * reduce function is a generalization of rolling aggregators
     * exposes lower level
     * constructs
     */
    /**
     * rolling aggregators with keyed stream
     * @throws Exception
     */
    @Test
    @DisplayName("Simple ValueState example using flatmap")
    public void TestReduceFunction() throws Exception {

        celsius
                .filter(new FilterFunction<SensorReading>() {
                    @Override
                    public boolean filter(SensorReading sensorReading) throws Exception {
                        return sensorReading.id.equals("sensor_9");
                    }
                })
                .keyBy(new KeySelector<SensorReading, String>() {
                    @Override
                    public String getKey(SensorReading reading) throws Exception {
                        return reading.id;
                    }
                })
                .reduce(new ReduceFunction<SensorReading>() {
                    @Override
                    public SensorReading reduce(SensorReading r1, SensorReading r2) throws Exception {

                        Double highestTemp = Math.max(r1.temperature, r2.temperature);
                        SensorReading result = new SensorReading();
                        result.id = r1.id;
                        result.temperature = highestTemp;

                        if(highestTemp == r1.temperature) {
                            result.timestamp = r1.timestamp;
                        } else {
                            result.timestamp = r2.timestamp;
                        }

                        return result;

                    }
                })
                .map(new MapFunction<SensorReading, Tuple3<String, Double, Long>>() {
                    @Override
                    public Tuple3<String, Double, Long> map(SensorReading sensorReading) throws Exception {
                        return new Tuple3<String, Double, Long>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
                    }
                })
                .print();

        env.execute();
    }



}
