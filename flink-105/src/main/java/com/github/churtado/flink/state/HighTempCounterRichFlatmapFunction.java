package com.github.churtado.flink.state;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Math.toIntExact;

public class HighTempCounterRichFlatmapFunction extends RichFlatMapFunction<SensorReading, Tuple2<Integer, Long>> implements ListCheckpointed<Long> {

    private double threshold;
    private int subtaskIdx;         // index of the subtask
    private Long highTempCnt = 0L;  // local count variable

    public HighTempCounterRichFlatmapFunction(Double threshold) {
        this.threshold = threshold;
    }

    @Override
    public void open(Configuration parameters) {
        subtaskIdx = getRuntimeContext().getIndexOfThisSubtask();
    }

    @Override
    public void flatMap(SensorReading sensorReading, Collector<Tuple2<Integer, Long>> collector) throws Exception {
        if(sensorReading.temperature > threshold) {
            // increment the counter if the threshold is exceeded
            highTempCnt ++;
            // emit update with subtask index and counter
            collector.collect(new Tuple2<>(subtaskIdx, highTempCnt));
        }
    }

    /**
     * State is stored as a list of objects because the pipeline can either
     * fail or be rescaled. If it fails, then it will know how to restore state
     * If it's rescaled, then the state will need to be distributed among
     * several parallel instances, usually more than it had before. snapshotState
     * can be used to save this state and restore it. Initially, we saved the state
     * as a list with a single entry. That line is now commented. it makes more
     * sense to split thet state when we save it so that when we rescale it, the
     * pieces of state can be re-distributed by flink.
     *
     * restoreState, is what a parallel instance of an operator does to restore
     * the piece of state handed to it by the framework. In this case it's easy,
     * just add all the entries. This makes sense because we may want to scale up
     * but we may also want to scale down. That's why the restore adds up all the
     * elements in the list, in case it receives state that once belonged to many
     * different parallel instances.
     */

    @Override
    public List<Long> snapshotState(long chkpntId, long ts) throws Exception {
        // snapshot state as a list with a single count
        // return Collections.singletonList(highTempCnt);
        // split count into ten partial counts
        int div = toIntExact(highTempCnt / 10L);
        long  mod = highTempCnt % 10L;
        // return list as ten parts
        List<Long> l1 = new ArrayList<Long>(Collections.nCopies((div + 1), mod));
        List<Long> l2 = new ArrayList<Long>(Collections.nCopies(div, 10 - mod));
        l1.addAll(l2);
        return l1;
    }

    @Override
    public void restoreState(List<Long> state) throws Exception {
        highTempCnt = 0L; // setting to an initial state?
        // restore state by adding all the longs of the list
        for(Long cnt: state){
            highTempCnt += cnt;
        }
    }
}
