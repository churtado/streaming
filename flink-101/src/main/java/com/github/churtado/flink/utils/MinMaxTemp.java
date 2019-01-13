package com.github.churtado.flink.utils;

import java.io.Serializable;

public class MinMaxTemp implements Serializable {

    public MinMaxTemp(){ }

    public String id;
    public double min;
    public double max;
    public long endTs;
}
