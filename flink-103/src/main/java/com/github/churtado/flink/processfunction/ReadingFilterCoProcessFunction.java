package com.github.churtado.flink.processfunction;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;

public class ReadingFilterCoProcessFunction extends CoProcessFunction<SensorReading, Tuple2<String, Long>, SensorReading> {

        // switch to enable forwarding
        private transient ValueState<Boolean> forwardingEnabled;

        // hold timestamp of currently active disable timer
        private transient ValueState<Long> disableTimer;

        @Override
        public void open(Configuration config) throws Exception {

            forwardingEnabled = getRuntimeContext().getState(new ValueStateDescriptor<Boolean>("filterSwitch", Types.BOOLEAN));
            disableTimer = getRuntimeContext().getState(new ValueStateDescriptor<Long>("timer", Types.LONG));
        }

        @Override
        public void processElement1(SensorReading sensorReading, Context context, Collector<SensorReading> collector) throws Exception {
            if(forwardingEnabled.value() != null && forwardingEnabled.value() == true) {
                collector.collect(sensorReading);
            }
        }

        @Override
        public void processElement2(Tuple2<String, Long> switchTuple, Context context, Collector<SensorReading> collector) throws Exception {
            // enable reading forwarding
            forwardingEnabled.update(true);

            // set disable forward timer
            Long timerTimestamp = context.timerService().currentProcessingTime() + switchTuple.f1;
            context.timerService().registerProcessingTimeTimer(timerTimestamp);
            disableTimer.update(timerTimestamp);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext context , Collector<SensorReading> out) throws Exception {
            if(timestamp == disableTimer.value()) {
                // remove all state. Forward switch will be false by default
                forwardingEnabled.clear();
                disableTimer.clear();
            }
        }
    }

