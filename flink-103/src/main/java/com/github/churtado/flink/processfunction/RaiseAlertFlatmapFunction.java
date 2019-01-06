package com.github.churtado.flink.processfunction;

import com.github.churtado.flink.utils.Alert;
import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SmokeLevel;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.util.Collector;

public class RaiseAlertFlatmapFunction implements CoFlatMapFunction<SensorReading, SmokeLevel, Alert> {

    SmokeLevel level = SmokeLevel.Low;

    @Override
    public void flatMap1(SensorReading sensorReading, Collector<Alert> collector) throws Exception {
        if(level.equals(SmokeLevel.Low) && sensorReading.temperature > 100) {

            Alert alert = new Alert();
            alert.timestamp = sensorReading.timestamp;
            alert.message = "Risk of fire!";
            collector.collect(alert);
        }
    }

    @Override
    public void flatMap2(SmokeLevel smokeLevel, Collector<Alert> collector) throws Exception {
        level = smokeLevel;
    }
}