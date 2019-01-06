package com.github.churtado.flink.window;

import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SensorReadingAccumulator;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;

public /**
 * Pretty easy stuff. Create an accumulator. Assume this will always run on a keyed stream
 * The accumulator gets a default value. Then specify how to add incoming elements
 * Specify how to merge the different keyed streams. Specify how to get a result.
 * I made the result a tuple to make it easier to print.
 */
class AvgAggregateFunction implements AggregateFunction<SensorReading, SensorReadingAccumulator, Tuple2<String, Double>> {

    @Override
    public SensorReadingAccumulator createAccumulator() {
        SensorReadingAccumulator accumulator = new SensorReadingAccumulator();
        accumulator.id = "";
        accumulator.count = 0;
        accumulator.sum  = 0;
        return accumulator;
    }

    @Override
    public SensorReadingAccumulator add(SensorReading value, SensorReadingAccumulator accumulator) {
        accumulator.id = value.id;
        accumulator.sum += value.temperature;
        accumulator.count++;

        return accumulator;
    }

    @Override
    public Tuple2<String, Double> getResult(SensorReadingAccumulator accumulator) {
        SensorReading sensorReading = new SensorReading();
        sensorReading.id = accumulator.id;
        sensorReading.temperature = accumulator.sum / accumulator.count;
        return new Tuple2<String, Double>(accumulator.id, accumulator.sum / accumulator.count);
    }

    @Override
    public SensorReadingAccumulator merge(SensorReadingAccumulator a, SensorReadingAccumulator b) {
        a.count += b.count;
        a.sum += b.sum;
        return a;
    }
}

