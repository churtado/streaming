package com.github.churtado.flink.window;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.Collection;

public /** A counting WindowProcessFunction that distinguishes between
 * first results and updates. */
class UpdatingWindowCountFunction extends ProcessWindowFunction<SensorReading, Tuple4<String, Long, Integer, String>, String, TimeWindow> {

    @Override
    public void process(String id, Context ctx, Iterable<SensorReading> elements, Collector<Tuple4<String, Long, Integer, String>> out) throws Exception {

        // count number of readings
        int cnt = ((Collection<?>)elements).size();

        // state to check if this is the first evaluation of the window or not
        ValueState<Boolean> isUpdate = ctx.windowState().getState(new ValueStateDescriptor<Boolean>("isUpdate", Types.BOOLEAN));

        if(isUpdate.value() == null) {
            // first evaluation, emit first result
            out.collect(new Tuple4<>(id, ctx.window().getEnd(), cnt, "first"));
            isUpdate.update(true);
        } else {
            // not the first evaluation, emit an update
            out.collect(new Tuple4<>(id, ctx.window().getEnd(), cnt, "update"));
        }
    }
}