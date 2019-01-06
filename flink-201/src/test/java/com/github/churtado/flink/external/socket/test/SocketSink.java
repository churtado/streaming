package com.github.churtado.flink.external.socket.test;

import com.github.churtado.flink.utils.SensorReading;
import com.github.churtado.flink.utils.SensorSource;
import com.github.churtado.flink.utils.SensorTimeAssigner;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;

public class SocketSink {

    private DataStream<SensorReading> readings;
    private StreamExecutionEnvironment env;

    @BeforeEach
    public void GetReadings() {
        // set up the streaming execution environment
        env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        readings = env
                .addSource(new SensorSource())
                .setParallelism(1) // just so I can run this on my local cluster
                .assignTimestampsAndWatermarks(new SensorTimeAssigner(Time.seconds(5))); // you need this if using event time
    }

    /**
     * Any operator can write to an external system, but flink
     * provides a sink function interface anyway.
     *
     * Run nc -l localhost 9191 to listen on the port first
     */
    @Test
    @DisplayName("Application that writes data to a socket sink")
    public void TestSocketSink() throws Exception {
        readings.addSink(new SimpleSocketSink("localhost", 9191))
                .setParallelism(1);
        env.execute();
    }

    class SimpleSocketSink extends RichSinkFunction<SensorReading> {

        String host;
        int port;
        Socket socket;
        PrintStream writer;


        public SimpleSocketSink(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void open(Configuration parameters) throws IOException {
            // open socket and writer
            socket = new Socket(InetAddress.getByName(host), port);
            writer = new PrintStream(socket.getOutputStream());
        }

        @Override
        public void invoke(SensorReading value, Context context) {
            // write sensor reading to socket
            writer.println(value.toString());
            writer.flush();
        }

        @Override
        public void close() throws IOException {
            // close the writer and socket
            writer.close();
            socket.close();
        }

    }
}
