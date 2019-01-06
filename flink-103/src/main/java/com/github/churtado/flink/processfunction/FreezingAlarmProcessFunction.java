package com.github.churtado.flink.processfunction;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

public class FreezingAlarmProcessFunction extends KeyedProcessFunction<String, SensorReading, String> {

    // defining an output tag for a side output for freezing temperatures
    private final OutputTag<String> freezingAlarmOutput = new OutputTag<String>("freezing-alarms"){};

    @Override
    public void processElement(SensorReading sensorReading, Context context, Collector<String> collector) throws Exception {
        if (sensorReading.temperature < 32.0) {
            // output the alarm value to side output
            context.output(freezingAlarmOutput, "detected a freezing temperature of " + sensorReading.temperature + " from sensor " + sensorReading.id);
        }
    }
}
