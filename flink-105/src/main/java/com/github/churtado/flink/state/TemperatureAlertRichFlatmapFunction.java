package com.github.churtado.flink.state;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;

public class TemperatureAlertRichFlatmapFunction extends RichFlatMapFunction<SensorReading, Tuple3<String, Double, Double>> {

    private Double threshold;
    private ValueState<Double> lastTempState = null;

    // the state handle object
    public TemperatureAlertRichFlatmapFunction(Double threshold) {
        this.threshold = threshold;
    }

    @Override
    public void open(Configuration parameters){
        // create the state descriptor
        ValueStateDescriptor<Double> lastTempDescriptor = new ValueStateDescriptor<Double>("lastTemp", Double.class);
        // obtain the state handle
        lastTempState = getRuntimeContext().getState(lastTempDescriptor);
    }

    @Override
    public void flatMap(SensorReading value, Collector<Tuple3<String, Double, Double>> out) throws Exception {
        // fetch the last temperature from state
        Double lastTemp = lastTempState.value();
        // check if we need to emit an alert
        if (lastTemp!=null && lastTemp > 0d && (value.temperature / lastTemp) > threshold) {
            // temperature increased by more than the threshold
            out.collect(new Tuple3<String, Double, Double>(value.id, value.temperature, lastTemp));
        }
        // update lastTemp state
        this.lastTempState.update(value.temperature);
    }
}
