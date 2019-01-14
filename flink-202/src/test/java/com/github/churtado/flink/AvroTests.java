package com.github.churtado.flink;

import com.example.Customer;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.formats.avro.typeutils.AvroSerializer;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer011;
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
    private FlinkKafkaConsumer011<Customer> consumer011;
    private DataStreamSource<Customer> customers;
    private FlinkKafkaProducer011<String> producer011;

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
        config.setProperty("specific.avro.reader", "true");

        String schemaRegistryUrl = "http://192.168.1.151:8081";

        consumer011 = new FlinkKafkaConsumer011<Customer>(
                "customer-flink",
                ConfluentRegistryAvroDeserializationSchema.forSpecific(Customer.class, schemaRegistryUrl),
                config);
        consumer011.setStartFromEarliest();

        customers = env.addSource(consumer011);

        AvroSerializer<Customer> serializer = new AvroSerializer<>(Customer.class);

    }

    @Test
    @DisplayName("Testing consuming avro data from kafka")
    public void testConsumeKafkaAvroData() throws Exception {

        SingleOutputStreamOperator<String> mapToString = customers
                .map((MapFunction<Customer, String>) SpecificRecordBase::toString);

        mapToString.print();

        env.execute();

    }

    @Test
    @DisplayName("Testing consuming avro data from kafka")
    public void testProduceKafkaAvroData() throws Exception {

        /**
         * This is fucking easy. Create an actual kafka producer and
         * produce the data that you've consumed out using a
         * conventional kafka producer. You can make it use transactions
         * and all. What you would have to do is make sure you put
         * it in a process step or something like that, or create
         * your own custom sink to Kafka using avro.
         */
    }
}
