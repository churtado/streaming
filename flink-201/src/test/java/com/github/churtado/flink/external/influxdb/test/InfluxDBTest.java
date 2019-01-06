package com.github.churtado.flink.external.influxdb.test;

import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SensorSource;
import com.github.churtado.flink.utils.SensorTimeAssigner;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBConfig;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBPoint;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class InfluxDBTest {

    static Logger logger = LoggerFactory.getLogger(InfluxDBTest.class.getName());

    private StreamExecutionEnvironment env;
    private InfluxDBConfig influxDBConfig;
    private DataStream<SensorReading> readings;

    @BeforeEach
    public void setup() {

        // set up flink
        env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Important to enable checkpointing in order to recover from failure
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);


        // setup influxdb
        influxDBConfig = InfluxDBConfig.builder("http://localhost:8086", "flux", "password", "sensor_data")
                .batchActions(1000)
                .flushDuration(100, TimeUnit.MILLISECONDS)
                .enableGzip(true)
                .build();

        // generate some fake data
        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5))); // you need this if using event time

    }

    /**
     * InfluxDB sink
     */
    @Test
    @DisplayName("Testing influxdb sink")
    public void TestInfluxDB() throws Exception {


        int N = 10000;

        List<String> dataList = new ArrayList<>();
        for (int i = 0; i < N; ++i) {
            String id = "server" + String.valueOf(i);
            dataList.add("cpu#" + id);
            dataList.add("mem#" + id);
            dataList.add("disk#" + id);
        }

        DataStream<String> source = env.fromElements(dataList.toArray(new String[0]));

        DataStream<InfluxDBPoint> dataStream = source.map(
                new RichMapFunction<String, InfluxDBPoint>() {

                    @Override
                    public InfluxDBPoint map(String s) throws Exception {
                        String[] input = s.split("#");

                        String measurement = input[0];
                        long timestamp = System.currentTimeMillis();

                        HashMap<String, String> tags = new HashMap<>();
                        tags.put("host", input[1]);
                        tags.put("region", "region#" + String.valueOf(input[1].hashCode() % 20));

                        HashMap<String, Object> fields = new HashMap<>();
                        fields.put("value1", input[1].hashCode() % 100);
                        fields.put("value2", input[1].hashCode() % 50);

                        return new InfluxDBPoint(measurement, timestamp, tags, fields);
                    }
                }
        );

//        dataStream.print();

        dataStream.addSink(new InfluxDBSink(influxDBConfig));

        env.execute();
    }

    /**
     * InfluxDB sink with our sensor reading source
     */
    @Test
    @DisplayName("Testing influxdb sink")
    public void TestSensorReadingInfluxDB() throws Exception {

        DataStream<InfluxDBPoint> dataStream = readings.map(
                new RichMapFunction<SensorReading, InfluxDBPoint>() {

                    @Override
                    public InfluxDBPoint map(SensorReading s) throws Exception {

                        String measurement = "temperature_readings";

                        HashMap<String, String> tags = new HashMap<>();
                        tags.put("sensor", s.id);

                        HashMap<String, Object> fields = new HashMap<>();
                        fields.put("temperature", s.temperature);

                        return new InfluxDBPoint(measurement, s.timestamp, tags, fields);
                    }
                }
        );

//        dataStream.print();

        dataStream.addSink(new InfluxDBSink(influxDBConfig));

        env.execute();
    }
}
