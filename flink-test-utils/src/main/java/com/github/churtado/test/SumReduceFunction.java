package com.github.churtado.test;

import org.apache.flink.api.common.functions.ReduceFunction;

public class SumReduceFunction implements ReduceFunction<Long> {

    @Override
    public Long reduce(Long value1, Long value2) throws Exception {
        return value1 + value2;
    }
}
