package com.github.churtado.flink.state.test;

import com.github.churtado.flink.state.*;
import com.github.churtado.flink.utils.*;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

public class StatefulOperatorsTest {

    Logger logger = LoggerFactory.getLogger(StatefulOperatorsTest.class);

    /**
     * We're now looking into how to use
     * operators that keep state. Sometimes the state is
     * keyed, if you use key by to partition among operators.
     * Each operator will maintain its own keyed state.
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
        env.setParallelism(4);

        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5))); // you need this if using event time
    }

    /**
     * Here we're using ValueSTate to compare sensor temperature
     * and raise an alert if the temperature increased significantly
     * between current and last measurement
     * @throws Exception
     */
    @Test
    @DisplayName("Simple ValueState example using flatmap")
    public void TestFlatMapReduce() throws Exception {

        KeyedStream<SensorReading, String> keyed =  readings
                .keyBy(new SensorReadingKeySelector());

        // apply a stateful FlatMapFunction on the keyed stream which
        // compares the temperature readings and raises alerts.

        DataStream<Tuple3<String, Double, Double>> alerts = keyed
                .flatMap(new TemperatureAlertRichFlatmapFunction(1.1));

        alerts.print();


        env.execute();
    }

    /**
     * This test is similar to the above, except this operator
     * will clean its state. See the massive comment in the
     * class definition.
     * @throws Exception
     */
    @Test
    @DisplayName("Testing process function that cleans its own state")
    public void TestStateCleaning() throws Exception {

        KeyedStream<SensorReading, String> keyed =  readings
                .keyBy(new SensorReadingKeySelector());

        // apply a stateful FlatMapFunction on the keyed stream which
        // compares the temperature readings and raises alerts.

        DataStream<Tuple3<String, Double, Double>> alerts = keyed
                .process(new StateCleaningTemperatureFunction(1.1));

        alerts.print();


        env.execute();
    }

    /**
     * This is how to set up a stream with that sinks
     * to a queryable state store, use the client to
     * interact with this state store: TemperatureDashboard
     */
    @Test
    public void TestQueryableStateSink() throws Exception {
        DataStream<Tuple2<String, Double>> tenSecsMaxTemps = readings
                // project to sensor id and temperature
                .map(new RichMapFunction<SensorReading, Tuple2<String, Double>>() {
                    @Override
                    public Tuple2<String, Double> map(SensorReading sensorReading) throws Exception {
                        return new Tuple2<String, Double>(sensorReading.id, sensorReading.temperature);
                    }
                })
                .keyBy(0)
                // compute every ten seconds
                .timeWindow(Time.seconds(10))
                .max(1);

                // store max temperature of the last 10 secs for each sensor
                tenSecsMaxTemps
                        // key by sensor id
                        .keyBy(0)
                        .asQueryableState("maxTemp");
                // if you need to do custom aggregations, use the overload
                // of the above method that uses a ReducingStateDescriptor
                // it will allow you to use a reduce function, instead of an
                // upsert as is the default way. There's one more overload
                // that allows you to define custom serialization

        //readings.map(new MapSensorReadingToTuple()).print();

        env.execute();
    }

    /**
     * We're now going to learn how to use a ListCheckpointedInterface
     * for a function that counts temperature measurements that exceed
     * a threshold per partition, or in other words, each parallel instance
     * of that operator.
     */
    @Test
    @DisplayName("RichFlatMapFunction with operator list state")
    public void TestOperatorListState() throws Exception {
        KeyedStream<SensorReading, String> keyed =  readings
                .keyBy(new SensorReadingKeySelector());

        keyed
                .flatMap(new HighTempCounterRichFlatmapFunction(30.0))
                .print();

        env.execute();
    }

    /**
     * Now we're going to test out broadcast state. This is useful for example, when
     * rules need to be streamed to parallel instances of operators. Those operators
     * apply rules to incoming events. So, operators have 2 inputs: events and rules.
     *
     * This is interesting because when debugging you have to make sure you're aware
     * that from one loop to another state changes because you've shifted to another
     * key.
     */
    @Test
    @DisplayName("Testing broadcast state")
    public void TestBroadcastState() throws Exception {
        KeyedStream<SensorReading, String> keyed = readings
                .keyBy(new SensorReadingKeySelector());

        // create a simple stream to broadcast
        DataStream<ThresholdUpdate> thresholds = env.fromElements(
                new ThresholdUpdate("sensor_0", 1.001),
                new ThresholdUpdate("sensor_1", 1.001),
                new ThresholdUpdate("sensor_2", 1.001),
                new ThresholdUpdate("sensor_3", 1.001),
                new ThresholdUpdate("sensor_4", 1.001),
                new ThresholdUpdate("sensor_5", 1.3),
                new ThresholdUpdate("sensor_6", 0.4),
                new ThresholdUpdate("sensor_7", 1.01),
                new ThresholdUpdate("sensor_8", 0.7),
                new ThresholdUpdate("sensor_9", 1.03)
        );

        // the descriptor of the broadcast state
        MapStateDescriptor<String, Double> broadcastStateDescriptor =
                new MapStateDescriptor<String, Double>("thresholds", String.class, Double.class);

        // creating a broadcast stream
        BroadcastStream<ThresholdUpdate> broadcastThresholds = thresholds.broadcast(broadcastStateDescriptor);

        // connect keyed sensor stream and broadcasted rules stream
        DataStream<Tuple3<String, Double, Double>> alerts = keyed
                .connect(broadcastThresholds)
                .process(new UpdateableTempAlertKeyedBroadcastFunction(4.0d));

        alerts.print();

        env.execute();
    }

    /**
     * Now we'll use the checkpointed function interface. It's the
     * lowest level interface, and uses most of the concepts from
     * other examples.
     */
    @Test
    @DisplayName("Testing checkpointed function interface")
    public void TestCheckpointedFunction() throws Exception {
        readings.keyBy(new SensorReadingKeySelector())
                /**
                 * note the uid added. That was done as a best practice in case
                 * the application is updated by a new one and the state of the
                 * application can be restored based on operator id's that are
                 * not the default ones. These helps to plan the job out for
                 * future updates.
                 */
                .flatMap(new HighTempCounterCheckpointedFlatmapFunction(60.0)).uid("alertFunc")
                .print();
        env.execute();
    }

}
