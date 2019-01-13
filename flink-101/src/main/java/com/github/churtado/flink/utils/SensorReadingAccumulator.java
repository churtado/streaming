package com.github.churtado.flink.utils;

import java.io.Serializable;

public class SensorReadingAccumulator implements Serializable {

    public SensorReadingAccumulator() {
    }

    public int count;
    public double sum;
    public String id;
}
