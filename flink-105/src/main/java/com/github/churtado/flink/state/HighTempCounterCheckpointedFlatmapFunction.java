package com.github.churtado.flink.state;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.util.Collector;

import java.util.Iterator;

public  /**
 * This is a lower level implementation of the high temp counter class
 * It's a flatmap function but with more lower level constructs exposed
 * to it.
 */
class HighTempCounterCheckpointedFlatmapFunction implements FlatMapFunction<SensorReading, Tuple3<String, Long, Long>>, CheckpointedFunction {

    private Double threshold;

    public HighTempCounterCheckpointedFlatmapFunction(Double threshold){
        this.threshold = threshold;
    }

    // local variable for the operator high temperature cnt
    Long opHighTempCnt = 0L;
    ValueState<Long> keyedCntState = null;
    ListState<Long> opCntState = null;

    @Override
    public void flatMap(SensorReading sensorReading, Collector<Tuple3<String, Long, Long>> collector) throws Exception {
        // check if temperature is high
        if (sensorReading.temperature > threshold) {
            // update local operator high temp counter
            opHighTempCnt ++;
            // update keyed temp counter
            Long keyHighTempCnt = 0L;
            if(keyedCntState.value() != null){
                keyHighTempCnt = keyedCntState.value() + 1;
            }
            keyedCntState.update(keyHighTempCnt);
            // emit new counts
            collector.collect(new Tuple3<>(sensorReading.id, keyHighTempCnt, opHighTempCnt));
        }
    }

    /**
     * Remember, this is called right before the snapshot is
     * taken, so usually this is to make sure state is updated
     * prior to the snapshot being taken.
     * @param snapshotContext
     * @throws Exception
     */
    @Override
    public void snapshotState(FunctionSnapshotContext snapshotContext) throws Exception {
        // update operator state with local state
        opCntState.clear();
        opCntState.add(opHighTempCnt);
    }

    @Override
    public void initializeState(FunctionInitializationContext initContext) throws Exception {
        // initialize keyed state
        ValueStateDescriptor<Long> keyCntDescriptor =
                new ValueStateDescriptor<Long>("keyedCnt", TypeInformation.of(Long.class));
        keyedCntState = initContext.getKeyedStateStore().getState(keyCntDescriptor);

        // initialize operator state
        ListStateDescriptor<Long> opCntDescriptor =
                new ListStateDescriptor<Long>("opCnt", TypeInformation.of(Long.class));
        opCntState = initContext.getOperatorStateStore().getListState(opCntDescriptor);

        // initialize local variable with state
        for (Iterator<Long> it = opCntState.get().iterator(); it.hasNext(); ) {
            opHighTempCnt += it.next();
        }
    }
}
