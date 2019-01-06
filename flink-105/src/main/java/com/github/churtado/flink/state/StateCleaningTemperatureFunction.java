package com.github.churtado.flink.state;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.io.IOException;


/**
 * This is a ProcessFunction that compares two subsequent temperature measurements and raises an
 * alert if the difference is greater than a threshold. This is the same use case as in
 * the keyed state example before, but the ProcessFunction also clears the state for keys
 * (i.e., sensors) that have not provided any new temperature measurement within one hour
 * of event-time.

 * The state-cleaning mechanism implemented by the above ProcessFunction works as follows.
 * For each input event, the processElement() method is called. Before comparing the
 * temperature measurements and updating the last temperature, the method registers a
 * clean-up timer. The clean-up time is computed by reading the timestamp of the current
 * watermark and adding one hour. The timestamp of the latest registered timer is held in
 * an additional ValueState[Long] named lastTimerState. After that, the method compares the
 * temperatures, possibly emits an alert, and updates its state.

 * While being executed, a ProcessFunction maintains a list of all registered timers, i.e.,
 * registering a new timer does not override previously registered timers5. As soon as the
 * internal event-time clock of the operator (driven by the watermarks) exceeds the timestamp
 * of a registered timer, the onTimer() method is called. The method validates if the fired
 * timer was the last registered timer by comparing the timestamp of the fired timer with
 * the timestamp held in the lastTimerState. If this is the case, the method removes all
 * managed state, i.e., the last temperature and the last timer state.

 * Keeping state clean is very important because if you don't
 * at some point it will grow too large and cause the application
 * to fail, especially if you're using files or mem-based backends
 *
 * So, for example, if sensor 9 doesn't emit anything for an hour, it's state gets cleaned.
 * Remember, this means that this function assumes state is being keyed somehow.
 */
public class StateCleaningTemperatureFunction extends KeyedProcessFunction<String, SensorReading, Tuple3<String, Double, Double>> {

    // keyed state handle for last temperature
    private ValueState<Double> lastTempState = null;
    // keyed state handle for the last registered timer
    private ValueState<Long> lastTimerState = null;

    private double threshold = 0;

    public StateCleaningTemperatureFunction(Double threshold) {
        this.threshold = threshold;
    }

    @Override
    public void open(Configuration config) {
        // register state for last temperature
        ValueStateDescriptor<Double> lastTempDescriptor = new ValueStateDescriptor<Double>("lastTemp", Double.class);
        /**
         * We'll make this piece of state queryable by external
         * applications, and set its external identifier
         */
        lastTempDescriptor.setQueryable("lastTemperature");

        lastTempState = getRuntimeContext().getState(lastTempDescriptor);

        // register state for the last timer
        ValueStateDescriptor<Long> timerDescriptor = new ValueStateDescriptor<Long>("timerState", Long.class);
        lastTimerState = getRuntimeContext().getState(timerDescriptor);
    }

    @Override
    public void processElement(SensorReading sensorReading, Context context, Collector<Tuple3<String, Double, Double>> collector) throws Exception {
        // get current watermark and add one hour
        Long checkTimestamp = context.timerService().currentWatermark() + (3600 * 1000);
        // register new timer
        // only one timer per timestamp will be registered
        context.timerService().registerEventTimeTimer(checkTimestamp);
        // update the timestamp of the last timer
        lastTimerState.update(checkTimestamp);

        // fetch the last temperature from state
        Double lastTemp = lastTempState.value();
        // check if we need to emit an alert
        if (lastTemp != null && lastTemp > 0.0d && (sensorReading.temperature / lastTemp) > threshold) {
            // temperature increased by more than the threshold
            collector.collect(new Tuple3<>(sensorReading.id, sensorReading.temperature, lastTemp));
        }

        // update last temperature state
        this.lastTempState.update(sensorReading.temperature);
    }

    @Override
    public void onTimer(long ts, OnTimerContext context, Collector<Tuple3<String, Double, Double>> collector) throws IOException {
        // get timestamp of last registered timer
        Long lastTimer =  lastTimerState.value();

        // check if last registered timer fired
        if(lastTimer != null && lastTimer == ts) {
            // clear state for the key
            lastTempState.clear();
            lastTimerState.clear();
        }
    }
}
