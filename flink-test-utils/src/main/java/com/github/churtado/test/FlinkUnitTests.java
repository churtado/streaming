package com.github.churtado.test;

import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.shaded.curator.org.apache.curator.shaded.com.google.common.collect.Lists;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The static variable in CollectSink is used here because Flink serializes
 * all operators before distributing them across a cluster. Communicating with
 * operators instantiated by a local Flink mini cluster via static variables is
 * one way around this issue. Alternatively, you could for example write the
 * data to files in a temporary directory with your test sink. You can also
 * implement your own custom sources for emitting watermarks.
 */


public class FlinkUnitTests {

    /**
     * You can use junit to test the business logic built into a pipeline
     * @throws Exception
     */
    @Test
    @DisplayName("reduce function: 40 + 2 = 42")
    public void testSum() throws Exception {
        // Instantiate sum function
        SumReduceFunction sumReduceFunction = new SumReduceFunction();

        // test it out
        assertEquals(new Long(42), sumReduceFunction.reduce(40L, 2L));
    }

    /**
     * But you may want to do integration tests to test how a stream behaves
     */
    @Test
    @DisplayName("Flink integration test")
    public void testMultiply() throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /**
         *
         * you could use this along with an operator that you
         * can force to throw an exception to see how that behaves.
         * Look to a more complicated example in this project.
         */
        env.enableCheckpointing(500);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));

        // configure test env
        env.setParallelism(1);

        // values are collected in a static variable
        CollectSink.values.clear();

        env.fromElements(1L, 21L, 22L)
                .map(new MultiplyByTwoMapFunction())
                .addSink(new CollectSink());

        env.execute();

        assertEquals(Lists.newArrayList(2L, 42L, 44L), CollectSink.values);
    }

    // create a testing sink
    private static class CollectSink implements SinkFunction<Long> {

        // must be static
        public static final List<Long> values = new ArrayList();

        @Override
        public synchronized void invoke(Long value, SinkFunction.Context context) throws Exception {
            values.add(value);
        }
    }

}
