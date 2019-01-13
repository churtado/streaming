package com.github.churtado.flink.utils;

import java.io.Serializable;

public class ThresholdUpdate implements Serializable {

    public String id;
    public Double threshold;

    public ThresholdUpdate(String id, Double threshold) {
        this.id = id;
        this.threshold = threshold;
    }

    public ThresholdUpdate(){

    }

}
