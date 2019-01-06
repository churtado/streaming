package com.github.churtado.flink.state;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.queryablestate.client.QueryableStateClient;
import org.apache.flink.table.api.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is a client application that queries
 * a stateful operator sink in written in
 * TestQueryableStateSink in the test class
 * of this module. You'll need to go to the front
 * end web app to get the id of the running job,
 * plug that in, and then run this after you've
 * had the test running first.
 */
public class TemperatureDashboard {

    // assume local setup and TM runs on same machine as client
    private static String proxyHost = "127.0.0.1";
    private static int proxyPort = 9069;

    // jobId of running queryable state job
    // can be looked up on the web UI
    private static String jobId = "88ab59ca3764878dd32fd461567a2778";

    // how many sensors to query
    private static int numSensors = 5;
    // how often to query state
    private static int refreshInterval = 10000;

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException, IOException {

        Logger logger = LoggerFactory.getLogger(TemperatureDashboard.class);

        // configure client with host and port of queryable state proxy
        QueryableStateClient client = new QueryableStateClient(proxyHost, proxyPort);

        List<CompletableFuture<ValueState<Tuple2<String, Double>>>> futures = new ArrayList
                <CompletableFuture<ValueState<Tuple2<String, Double>>>>();

        Map<String, Double> results = new HashMap<>();


        for(int i = 0; i < numSensors; i++) {
            futures.add(new CompletableFuture<ValueState<Tuple2<String, Double>>>());
            results.put("sensor_"+i, 0.0);
        }

        // print header line of the dashboard table
        String header = "";
        for(int i=0; i< numSensors; i++){
            header = header + "sensor_"+i+"\t|";
        }

        System.out.println(header);

        // loop forever
        while(true) {
            int i = 0;
            // send out async queries
            for(CompletableFuture<ValueState<Tuple2<String, Double>>> future: futures){
                future = queryState("sensor_" + i, client);

                future.thenAccept(response -> {
                    Tuple2<String, Double> res = null;
                    try {
                        res = response.value();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    results.put(res.f0, res.f1);
                });
                i ++;
            }

            // print result
            i = 0;
            String line = "";
            while (i < numSensors) {
                line = line + results.get("sensor_"+i) + "\t|";
                i++;
            }
            System.out.println(line);

            // wait to send out next queries
            try {
                Thread.sleep(refreshInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private static CompletableFuture<ValueState<Tuple2<String, Double>>> queryState(String key, QueryableStateClient client) {

        return  client
                .getKvState(
                        JobID.fromHexString(jobId),
                        "maxTemp",
                        key,
                        BasicTypeInfo.STRING_TYPE_INFO,
                        // state name isn't relevant here
                        new ValueStateDescriptor<Tuple2<String, Double>>(
                                "maxTemp",
                                TypeInformation.of(new TypeHint<Tuple2<String, Double>>() {})
                        )
                );

    }
}
