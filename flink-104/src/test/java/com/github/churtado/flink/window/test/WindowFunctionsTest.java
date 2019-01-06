package com.github.churtado.flink.window.test;

import com.github.churtado.flink.utils.*;
import com.github.churtado.flink.window.*;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class WindowFunctionsTest {

    /**
     * Two types of windowing. One maintains one element of state,
     * and a reduce function takes the new and existing element and
     * applies some logic to both to output a new element of the same
     * type.
     *
     * The other is more flexible, allowing to take in collections
     * of elements belonging to that window and applying more
     * sophisticated logic. We'll see that later.
     */

    private DataStream<SensorReading> readings;
    private StreamExecutionEnvironment env;


    @BeforeEach
    public void GetReadings() {
        // set up the streaming execution environment
        env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(1);

        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5))); // you need this if using event time
    }

    @Test
    @DisplayName("Min temp per sensor with reduce function")
    public void TestReduce() throws Exception {

         readings
                .keyBy(new SensorReadingKeySelector())
                .timeWindow(Time.seconds(15))
                .reduce(new MinReduceFunction())
                .map(new MapSensorReadingToTuple())
                .print();

        env.execute();
    }

    /**
     * Aggregate functions are harder to implement but more flexible
     * @throws Exception
     */
    @Test
    @DisplayName("Avg temp per sensor with aggregate function")
    public void TestAggregate() throws Exception {

        readings
                .keyBy(new SensorReadingKeySelector())
                .timeWindow(Time.seconds(15))
                .aggregate(new AvgAggregateFunction())
                .print();

        env.execute();
    }

    /**
     * ProcessWindow functions are probably the most flexible, but quite hard
     * to implement. You can acccess the context of a window, and a global per key
     * state, both as a type of store. You may have to clear the window manually in
     * a method to make sure you garbage-collect after yourself, and define a process
     * function
     *
     * Below we use a process window function to determine the highest and lowest
     * temperatures for a sensor in a 5 second window.
     * @throws Exception
     */
    @Test
    @DisplayName("Min and max temp per 5-second window")
    public void TestProcessWindow() throws Exception {

        readings
                .keyBy(new SensorReadingKeySelector())
                .timeWindow(Time.seconds(5))
                .process(new MinMaxTempPerWindowProcessFunction())
                .map(new MapFunction<MinMaxTemp, Tuple4<String, Double, Double, Long>>() {
                    @Override
                    public Tuple4<String, Double, Double, Long> map(MinMaxTemp value) throws Exception {
                        return new Tuple4<>(value.id, value.min, value.max, value.endTs);
                    }
                })
                .print();

        env.execute();
    }

    /**
     * Process window is very powerful, but you have to retain a lot of state.
     * Sometimes you can save on state retention and get access to the extra bits
     * of state from the ProcessWindowFunction by combining reduce/aggregate with
     * a process function, putting process at the end so you can access that bit of
     * state that you need. Here's an example of the above test, but with the
     * suggested optimization
     *
     * Curiously, you can pass a process function as a second argument of a
     * reduce call, but it's not well documented.
     */
    @Test
    @DisplayName("Min and max temp per 5-second window")
    public void TestProcessWithReduceWindow() throws Exception {

        readings
                .map(new MapFunction<SensorReading, Tuple3<String, Double, Double>>() {
                    @Override
                    public Tuple3<String, Double, Double> map(SensorReading value) throws Exception {
                        return new Tuple3<>(value.id, value.temperature, value.temperature);
                    }
                })
                .keyBy(new KeySelector<Tuple3<String, Double, Double>, String>() {
                    @Override
                    public String getKey(Tuple3<String, Double, Double> value) throws Exception {
                        return value.f0;
                    }
                })
                .timeWindow(Time.seconds(5))
                .reduce(new ReduceFunction<Tuple3<String, Double, Double>>() {
                    @Override
                    public Tuple3<String, Double, Double> reduce(Tuple3<String, Double, Double> value1, Tuple3<String, Double, Double> value2) throws Exception {
                        return new Tuple3<>(value1.f0, Double.min(value1.f1, value2.f1), Double.max(value1.f2, value2.f2));
                    }
                }, new AssignWindowEndProcessFunction())
                .print();

        env.execute();
    }

    /**
     * Redirect late sensor readings to side outputs. It's going to be hard
     * to spot late records on such a simple example
    */
    @Test
    @DisplayName("Side outputs for late readings")
    public void TestLateSideOuput() throws Exception {

        OutputTag<SensorReading> lateReadingsOutput = new OutputTag<SensorReading>("late-readings", TypeInformation.of(SensorReading.class));

        DataStream<SensorReading> countPer10Secs = readings
                .keyBy(new SensorReadingKeySelector())
                .timeWindow(Time.seconds(10))
                // emit late readings to a side ouput
                .sideOutputLateData(lateReadingsOutput)
                // process readings per window
                .process(new ProcessWindowFunction<SensorReading, SensorReading, String, TimeWindow>() {
                    @Override
                    public void process(String s, Context context, Iterable<SensorReading> elements, Collector<SensorReading> out) throws Exception {
                        out.collect(elements.iterator().next());
                    }
                });

        DataStream<SensorReading> lateStream = ((SingleOutputStreamOperator<SensorReading>) countPer10Secs)
                .getSideOutput(lateReadingsOutput);

        DataStream<SensorReading> filteredReadings = readings
                .process(new ProcessFunction<SensorReading, SensorReading>() {

            @Override
            public void processElement(SensorReading value, Context ctx, Collector<SensorReading> out) throws Exception {
                // compare record timestamp with current watermark
                if(value.timestamp < ctx.timerService().currentWatermark()) {
                    // this is a late reading => redirect it to the side output
                    ctx.output(lateReadingsOutput, value);
                } else {
                    out.collect(value);
                }
            }
        });

        DataStream<SensorReading> lateReadings = ((SingleOutputStreamOperator<SensorReading>) filteredReadings)
                .getSideOutput(lateReadingsOutput);

        lateReadings.map(new MapSensorReadingToTuple())
                .print();

        env.execute();
    }

    /**
     * Define windows with allowed lateness of up to 5 seconds. Again,
     * not many late records coming in.
     */
    @Test
    @DisplayName("Side outputs for late readings")
    public void TestAllowedLateness() throws Exception {

        readings
                .keyBy(new SensorReadingKeySelector())
                .timeWindow(Time.seconds(10))
                //process late readings for 5 additional seconds
                .allowedLateness(Time.seconds(5))
                // count readings and update results if late readings arrive
                .process(new UpdatingWindowCountFunction())
                .print();

        env.execute();
    }

}

