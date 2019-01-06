package com.github.churtado.flink.window;

import com.github.churtado.flink.utils.MinMaxTemp;
import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class MinMaxTempPerWindowProcessFunction extends ProcessWindowFunction<SensorReading, MinMaxTemp, String, TimeWindow> {

    @Override
    public void process(String s, Context context, Iterable<SensorReading> elements, Collector<MinMaxTemp> out) throws Exception {

        MinMaxTemp minMaxTemp = new MinMaxTemp();
        minMaxTemp.id = s;

        minMaxTemp.min = elements.iterator().next().temperature;
        minMaxTemp.max = elements.iterator().next().temperature;

        for(SensorReading reading: elements) {
            minMaxTemp.max = Double.max(minMaxTemp.max, reading.temperature);
            minMaxTemp.min = Double.min(minMaxTemp.min, reading.temperature);
        }

        minMaxTemp.endTs = context.window().getEnd();

        out.collect(minMaxTemp);

    }
}

