package com.github.churtado.flink.processfunction.test;

import com.github.churtado.flink.processfunction.FreezingAlarmProcessFunction;
import com.github.churtado.flink.processfunction.RaiseAlertFlatmapFunction;
import com.github.churtado.flink.processfunction.ReadingFilterCoProcessFunction;
import com.github.churtado.flink.utils.*;
import com.github.churtado.flink.window.utils.*;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.collector.selector.OutputSelector;
import org.apache.flink.streaming.api.datastream.ConnectedStreams;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SplitStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.OutputTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ProcessFunctionTest {
    Logger logger = LoggerFactory.getLogger(ProcessFunctionTest.class);

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

        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5))); // you need this if using event time
                // .assign timestamps and watermarks required for event time
                // .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5)))
                // .assignTimestampsAndWatermarks(new PeriodicAssigner()); // if you want to create a periodic assigner
                // assignAscendingWatermarks not available???

        DataStream<SensorReading> readings = env
                .addSource(new SensorSource())

                .assignTimestampsAndWatermarks(new PunctuatedAssigner());
        //assingAscendingWatermarks not available???

        smokeLevel = env
                .addSource(new SmokeLevelSource())
                .setParallelism(1); // warning: no checkpointing
    }

    @Test
    public void TestCoProcess() throws Exception {
        DataStream<Tuple2<String, Long>> filterSwitches = env.fromElements(
                new Tuple2<String, Long>("sensor_2", 10*1000L), // forward sensor_2 for 10 seconds
                new Tuple2<String, Long>("sensor_7", 20*1000L)  // forward sensor_7 for 20 seconds)
        );

        ConnectedStreams<SensorReading, Tuple2<String, Long>> connectedStream =
                readings.connect(filterSwitches);

        connectedStream
                .keyBy(new KeySelector<SensorReading, Object>() {
                    @Override
                    public Object getKey(SensorReading sensorReading) throws Exception {
                        return sensorReading.id;
                    }
                }, new KeySelector<Tuple2<String, Long>, Object>() {
                    @Override
                    public Object getKey(Tuple2<String, Long> stringLongTuple2) throws Exception {
                        return stringLongTuple2.f0;
                    }
                })
                .process(new ReadingFilterCoProcessFunction()).map(new MapFunction<SensorReading, Tuple3<String, Double, Long>>() {
            @Override
            public Tuple3<String, Double, Long> map(SensorReading sensorReading) throws Exception {
                return new Tuple3<>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
            }
        }).print();

        env.execute();
    }

    @Test
    public void TestSideOutput() throws Exception {
        // defining an output tag for a side output for freezing temperatures
        final OutputTag<String> freezingAlarmOutput = new OutputTag<String>("freezing-alarms"){};

        // using a process function to emit warnings if temp is freezing
        DataStream<String> freezingAlarmsStream = readings
                .keyBy(new SensorReadingKeySelector())
                .process(new FreezingAlarmProcessFunction())
                .getSideOutput(freezingAlarmOutput);

        // retrieve and print the freezing alarms
        freezingAlarmsStream.print();

        // print the main output
        readings.map(new MapFunction<SensorReading, Tuple3<String, Double, Long>>() {
            @Override
            public Tuple3<String, Double, Long> map(SensorReading sensorReading) throws Exception {
                return new Tuple3<>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
            }
        }).print();

        env.execute();
    }

    /**
     * this example uses co flatmap to join 2 streams, one with temp, and the other
     * with a smoke level stream. When the smoke level and temp is too high, then it emits an alarm
     * key sensors by id
     * @throws Exception
     */
    @Test
    public void TestCoFlatMap() throws Exception {
        readings
                .keyBy(new SensorReadingKeySelector())
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
                })
                .print();

        env.execute();
    }

    /**
     * Testing splitting streams and rescaling
     */
    @Test
    public void TestSplitStream() throws Exception {
        readings.filter(new FilterFunction<SensorReading>() {
            @Override
            public boolean filter(SensorReading sensorReading) throws Exception {
                return false;
            }
        }).setParallelism(2); // this will override the global parallelism of 4

        SplitStream<SensorReading> split =  readings
                .shuffle()  // random
                .rebalance() // balance it out to successor tasks (round robin)
                .rescale()   // almost round robin, only distributed to some, not all tasks
                .broadcast() // send everything all copies of the downstream operator
                .global()    // send everything to first copy of downstream operator
                // also you can define your own, I won't bother for now
                .split(new OutputSelector<SensorReading>() {
                    @Override
                    public Iterable<String> select(SensorReading sensorReading) {

                        // you can put lots of conditions here
                        List<String> output = new ArrayList<String>();
                        if(sensorReading.temperature < 100) {
                            output.add("low");
                        } else {
                            output.add("high");
                        }
                        return output;
                    }
                });

        DataStream<SensorReading> low = split.select("low");
        DataStream<SensorReading> high = split.select("high");

        high.map(new MapFunction<SensorReading, Tuple3<String, Double, Long>>() {
            @Override
            public Tuple3<String, Double, Long> map(SensorReading sensorReading) throws Exception {
                return new Tuple3<String, Double, Long>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
            }
        }).print().setParallelism(1);

        low.map(new MapFunction<SensorReading, Tuple3<String, Double, Long>>() {
            @Override
            public Tuple3<String, Double, Long> map(SensorReading sensorReading) throws Exception {
                return new Tuple3<String, Double, Long>(sensorReading.id, sensorReading.temperature, sensorReading.timestamp);
            }
        }).print();

        env.execute();
    }
}
