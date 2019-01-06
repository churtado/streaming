package com.github.churtado.flink.state;

import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.ThresholdUpdate;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;

public /**
 * The BroadcastProcessFunction and KeyedBroadcastProcessFunction
 * differ from each other as well. The BroadcastProcessFunction does
 * not expose a timer service to register timers and consequently does
 * not offer an onTimer() method. Note that you should not access keyed
 * state from the processBroadcastElement() of the KeyedBroadcastProcessFunction.
 * Since the broadcast input does not specify a key, the state backend
 * cannot access a keyed value and will throw an exception.
 *
 * Instead, the context of the KeyedBroadcastProcessFunction.processBroadcastElement()
 * method provides a method applyToKeyedState(StateDescriptor, KeyedStateFunction)
 * to apply a KeyedStateFunction to the value of each key in the keyed state that
 * is referenced by the StateDescriptor.
 */
class UpdateableTempAlertKeyedBroadcastFunction extends KeyedBroadcastProcessFunction<String, SensorReading, ThresholdUpdate, Tuple3<String, Double, Double>> {

    // the descriptor of the broadcast state
    private MapStateDescriptor<String, Double> thresholdStateDescriptor  =
            new MapStateDescriptor<String, Double>("thresholds", String.class, Double.class);

    // the keyed state handle
    private ValueState lastTempState = null;

    Double defaultThreshold = 0d;

    public UpdateableTempAlertKeyedBroadcastFunction(Double threshold){
        defaultThreshold = threshold;
    }

    @Override
    public void open(Configuration parameters){
        // create keyed state descriptor
        ValueStateDescriptor<Double> lastTempDescriptor =  new ValueStateDescriptor<Double>("lastTemp", Double.class);

        // obtain the keyed state handle
        lastTempState = getRuntimeContext().getState(lastTempDescriptor);
    }

    @Override
    public void processBroadcastElement(
            ThresholdUpdate update,
            Context ctx,
            Collector<Tuple3<String, Double, Double>> out
    ) throws Exception {
        // get broadcasted state handle
        BroadcastState<String, Double> thresholds = ctx.getBroadcastState(thresholdStateDescriptor);

        if(update.threshold >= 1.0d) {
            //configure a new threshold for the sensor
            thresholds.put(update.id, update.threshold);
        } else {
            // remove sensor specific threshold
            thresholds.remove(update.id);
        }
    }

    @Override
    public void processElement(SensorReading value, ReadOnlyContext ctx, Collector<Tuple3<String, Double, Double>> out) throws Exception {

        // get read-only broadcast state
        ReadOnlyBroadcastState<String, Double> thresholds = ctx.getBroadcastState(thresholdStateDescriptor);

        // get threshold for sensor
        Double sensorThreshold;
        if(thresholds.contains(value.id)) {
            sensorThreshold = thresholds.get(value.id);
        } else {
            sensorThreshold = defaultThreshold;
        }

        // fetch the last temperature from keyed state
        Double lastTemp = (Double) lastTempState.value();

        Double delta = 0.0;

        if(lastTemp != null){
            // calculate the change in temperature
            delta = value.temperature / lastTemp;
        }


        // check if we need to emit an alert
        if(lastTemp != null && lastTemp > 0 && delta > sensorThreshold) {
            // temperature increased by more than the threshold
            out.collect(new Tuple3<>(value.id, value.temperature, lastTemp));
        }

        // update lastTemp state
        lastTempState.update(value.temperature);
    }

}
