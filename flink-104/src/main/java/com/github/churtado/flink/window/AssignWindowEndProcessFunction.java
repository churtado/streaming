package com.github.churtado.flink.window;

import com.github.churtado.flink.utils.MinMaxTemp;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class AssignWindowEndProcessFunction extends ProcessWindowFunction<Tuple3<String, Double, Double>, Tuple4<String, Double, Double, Long>, String, TimeWindow> {

    @Override
    public void process(String s, Context context, Iterable<Tuple3<String, Double, Double>> elements, Collector<Tuple4<String, Double, Double, Long>> out) throws Exception {
        MinMaxTemp minMaxTemp = new MinMaxTemp();
        Tuple3<String, Double, Double> data = elements.iterator().next();
        long ts = context.window().getEnd();
        Tuple4<String, Double, Double, Long> tuple = new Tuple4<>(data.f0, data.f1, data.f2, ts);
        out.collect(tuple);
    }
}

