package com.github.churtado.test;

import org.apache.flink.api.common.functions.MapFunction;

/**
 * a simple map function to unit test
 */

public class MultiplyByTwoMapFunction implements MapFunction<Long, Long> {
    @Override
    public Long map(Long value) throws Exception {
        return value * 2;
    }
}
