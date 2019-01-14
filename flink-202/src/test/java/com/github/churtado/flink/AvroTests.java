package com.github.churtado.flink;

import com.example.Customer;
import com.github.churtado.flink.kafka.ConfluentAvroSerializationSchema;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
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
    }

    @Test
    @DisplayName("Testing consuming avro data from kafka")
    public void testConsumeKafkaAvroData() throws Exception {

        // setup kafka consumer
        Properties config = new Properties();
        config.setProperty("bootstrap.servers", "192.168.1.151:9092");
        config.setProperty("group.id", "customer-consumer-group-v2");
        config.setProperty("zookeeper.connect", "localhost:2181");
        config.setProperty("specific.avro.reader", "true");

        String schemaRegistryUrl = "http://192.168.1.151:8081";

        FlinkKafkaConsumer011<Customer> consumer011 = new FlinkKafkaConsumer011<Customer>(
                "customer-flink",
                ConfluentRegistryAvroDeserializationSchema.forSpecific(Customer.class, schemaRegistryUrl),
                config);
        consumer011.setStartFromEarliest();



        // create a stream and consume from kafka using schema registry

        DataStreamSource<Customer> customers = env.addSource(consumer011);

        SingleOutputStreamOperator<String> mapToString = customers
                .map((MapFunction<Customer, String>) SpecificRecordBase::toString);

        mapToString.print();

        env.execute();

    }

    @Test
    @DisplayName("Testing consuming avro data from kafka")
    public void testProduceKafkaAvroData() throws Exception {

        // setup producer
        String topic = "customer-flink";
        String brokerList = "192.168.1.151:9092";
        String registryUrl = "http://192.168.1.151:8081";
        int identityMapCapacity = 1000;

        //schema registry
        Properties registryProperties = new Properties();
        registryProperties.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);

        // set an ID field at some point
        Customer customer1 = Customer.newBuilder()
                .setPhoneNumber("123456")
                .setAge(1)
                .setFirstName("Carlos")
                .setLastName("Hurtado")
                .setWeight(1)
                .setHeight(1)
                .setEmail("charliepapa@gmail.com")
                .build();

        Customer customer2 = Customer.newBuilder()
                .setPhoneNumber("654321")
                .setAge(2)
                .setFirstName("Bob")
                .setLastName("Robertson")
                .setWeight(2)
                .setHeight(2)
                .setEmail("charliepapa2@gmail.com")
                .build();

        DataStream<Customer> customerStream = env.fromElements(customer1, customer2);

        //serialize avro
        ConfluentAvroSerializationSchema serializationSchema = new ConfluentAvroSerializationSchema<Customer>(topic, "http://192.168.1.151:8081", 1000);

        //write to kafka
        FlinkKafkaProducer011<Customer> producer011 = new FlinkKafkaProducer011<Customer>(brokerList, topic, serializationSchema);

        customerStream.addSink(producer011);

        // execute flink
        env.execute();
    }
}
