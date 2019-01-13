package com.github.churtado.flink.utils;

import java.io.Serializable;

public class SensorReading implements Serializable {

    // making this a POJO

    public SensorReading(){
    }

    public String id;
    public Long timestamp;
    public Double temperature;
    public String location;
}
