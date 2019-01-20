package com.github.churtado.sensor;

import com.github.churtado.sensor.avro.SensorReading;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.influxdb.InfluxDBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SensorReadingStream {

    static Logger logger = LoggerFactory.getLogger(SensorReadingStream.class.getName());

    public static void main(String[] args) {

        StreamExecutionEnvironment env = setupFlink();
        InfluxDBConfig influxDBConfig = setupInfluxDBConfig();
        DataStream<SensorReading> readings;

    }

    private static StreamExecutionEnvironment setupFlink() {

        // set up flink
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Important to enable checkpointing in order to recover from failure
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        return env;

    }

    private static InfluxDBConfig setupInfluxDBConfig() {

        // setup influxdb
        InfluxDBConfig influxDBConfig = InfluxDBConfig.builder("http://localhost:8086", "admin", "password", "sensor_readings")
                .batchActions(1000)
                .flushDuration(100, TimeUnit.MILLISECONDS)
                .enableGzip(true)
                .build();

        return influxDBConfig;

    }



//    private static void FlinkKafkaConsumer

}
