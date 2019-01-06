package com.github.churtado.flink.basics;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class AverageFunction implements WindowFunction<SensorReading, SensorReading, String, TimeWindow> {

    @Override
    public void apply(String s, TimeWindow timeWindow, Iterable<SensorReading> iterable, Collector<SensorReading> collector) throws Exception {

        Double sum = 0.0;
        Double count = 0.0;

        for(SensorReading r: iterable) {
            sum += r.temperature;
            count ++;
        }

        Double avg = sum / count;
        SensorReading result = new SensorReading();
        result.id = s;
        result.timestamp = timeWindow.getEnd();
        result.temperature = avg;
        collector.collect(result);

    }
}