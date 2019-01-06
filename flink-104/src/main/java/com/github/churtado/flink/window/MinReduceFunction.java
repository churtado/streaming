package com.github.churtado.flink.window;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.functions.ReduceFunction;

public class MinReduceFunction implements ReduceFunction<SensorReading> {

    @Override
    public SensorReading reduce(SensorReading value1, SensorReading value2) throws Exception {
        double min = Double.min(value1.temperature, value2.temperature);
        if ( min == value1.temperature){
            return value1;
        }
        return value2;
    }

}


