package com.github.churtado.flink.basics;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.ArrayList;
import java.util.List;

public class HelloList {

    public static void main(String[] args) throws Exception {

        // set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        List<String> elements = new ArrayList<>();
        elements.add("I'm");
        elements.add("a");
        elements.add("list");

        DataStream<String> data = env.fromCollection(elements);

        data.print();

        env.execute();

    }
}
