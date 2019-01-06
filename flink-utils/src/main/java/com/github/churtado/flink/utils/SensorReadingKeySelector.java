package com.github.churtado.flink.utils;

import org.apache.flink.api.java.functions.KeySelector;

public class SensorReadingKeySelector implements KeySelector<SensorReading, String> {

    @Override
    public String getKey(SensorReading value) throws Exception {
        return value.id;
    }

}
