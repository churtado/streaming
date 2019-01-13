package com.github.churtado.flink;

import com.example.Customer;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

/**
 * Here we've created a flink application that reads from a kafka topic
 * with Avro serialization and deserializes it into its POJO class.
 */
public class AvroTests {

    private StreamExecutionEnvironment env;
    private FlinkKafkaConsumer010<Customer> consumer010;
    private DataStreamSource<Customer> customers;

    @BeforeEach
    public void setup(){
        // setup flink
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().disableSysoutLogging();
        env.enableCheckpointing(5000);
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.getConfig().setAutoWatermarkInterval(1000L);
        env.setParallelism(4);

        // setup kafka consumer
        Properties config = new Properties();
        config.setProperty("bootstrap.servers", "192.168.1.151:9092");
        config.setProperty("group.id", "customer-consumer-group-v2");
        config.setProperty("zookeeper.connect", "localhost:2181");
        String schemaRegistryUrl = "http://192.168.1.151:8081";
        config.setProperty("specific.avro.reader", "true");

        consumer010 = new FlinkKafkaConsumer010<Customer>(
                "customer-flink",
                ConfluentRegistryAvroDeserializationSchema.forSpecific(Customer.class, schemaRegistryUrl),
                config);
        consumer010.setStartFromEarliest();

        customers = env.addSource(consumer010);

        // setup kafka producer
    }

    @Test
    @DisplayName("Testing consuming avro data from kafka")
    public void testAvro() throws Exception {

        customers.print();
        env.execute();

    }
}
