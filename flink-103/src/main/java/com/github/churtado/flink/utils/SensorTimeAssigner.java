package com.github.churtado.flink.utils;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;

public class SensorTimeAssigner extends BoundedOutOfOrdernessTimestampExtractor<SensorReading> {

    public SensorTimeAssigner(Time maxOutOfOrderness) {
        super(maxOutOfOrderness);
    }

    @Override
    public long extractTimestamp(SensorReading reading) {
        return reading.timestamp;
    }

}
