package com.github.churtado.flink.window;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.windowing.triggers.Trigger;
import org.apache.flink.streaming.api.windowing.triggers.TriggerResult;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;

public class OneSecondIntervalTrigger extends Trigger<SensorReading, TimeWindow> {

    private transient ValueState<Boolean> firstSeen;

    @Override
    public TriggerResult onElement(SensorReading element, long timestamp, TimeWindow window, TriggerContext ctx) throws Exception {

        // firstSeen will be false if not set yet
        ValueState<Boolean> firstSeen = ctx.getPartitionedState(new ValueStateDescriptor<Boolean>("firstSeen", TypeInformation.of(Boolean.class)));

        // register initial timer only for first element
        if(firstSeen.value()) {
            // compute time for next early firing by rounding watermark to second
            Long t = ctx.getCurrentWatermark() + (1000 - (ctx.getCurrentWatermark() % 1000));
            ctx.registerEventTimeTimer(t);
            // register timer for window end
            ctx.registerEventTimeTimer(window.getEnd());
            firstSeen.update(true);
        }
        // Continue, do not evaluate per element
        return TriggerResult.CONTINUE;
    }

    @Override
    public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
        // continue, we don't use processing time timers
        return TriggerResult.CONTINUE;
    }

    @Override
    public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
        if(time == window.getEnd()) {
            // final evaluation and purge window state
            return TriggerResult.FIRE_AND_PURGE;
        } else {
            // register next early firing timer
            Long t = ctx.getCurrentWatermark() + (1000 - (ctx.getCurrentWatermark() % 1000));
            if(t < window.getEnd()){
                ctx.registerEventTimeTimer(t);
            }
            return TriggerResult.FIRE;
        }
    }

    @Override
    public void clear(TimeWindow window, TriggerContext ctx) throws Exception {
        // clear trigger state
        firstSeen.clear();
    }
}

