package com.github.churtado.flink.utils;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;

public class MapSensorReadingToTuple implements MapFunction<SensorReading, Tuple2<String, Double>> {

    @Override
    public Tuple2<String, Double> map(SensorReading value) throws Exception {
        return new Tuple2<String, Double>(value.id, value.temperature);
    }

}