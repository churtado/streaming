//package com.github.churtado.flink;
//
//import com.example.Customer;
//import org.apache.avro.Schema;
//import org.apache.avro.io.BinaryEncoder;
//import org.apache.avro.io.EncoderFactory;
//import org.apache.avro.specific.SpecificDatumWriter;
//import org.apache.avro.specific.SpecificRecord;
//import org.apache.flink.api.common.serialization.DeserializationSchema;
//import org.apache.flink.formats.avro.AvroDeserializationSchema;
//import org.apache.flink.streaming.api.datastream.DataStreamSource;
//import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
//import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
//import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.util.Properties;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
////import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
//
//
//public class AvroTest {
//
//    Logger logger = LoggerFactory.getLogger(AvroTest.class.getName());
//
//    private StreamExecutionEnvironment env;
//
//    private FlinkKafkaConsumer<Customer> consumer;
//    String schemaRegistryUrl = "http://127.0.0.1:8081";
//
//    /**
//     * Remember to create the topic if you haven't already:
//     *
//     *  kafka-topics --zookeeper zookeeper:2181 --create --topic customer-flink --replication-factor 1 --partitions 6
//     *  look to the producer in the java package for avro "com.github.churtado.flink.external.avro"
//     * @throws Exception
//     */
//    @BeforeEach
//    public void setupKafkaAndFlink() throws Exception {
//
//        // set up flink
//        env = StreamExecutionEnvironment.getExecutionEnvironment();
//
//        // Important to enable checkpointing in order to recover from failure
////        env.enableCheckpointing(5000);
////        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 100));
////        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
////        env.getConfig().setAutoWatermarkInterval(1000L);
//        env.setParallelism(1);
//        env.getConfig().disableSysoutLogging();
//
//        // set up kafka consumer
//
//        Properties properties = new Properties();
//        properties.setProperty("bootstrap.servers", "192.168.1.151:9092");
//
//        // only required for Kafka 0.8
//        properties.setProperty("zookeeper.connect", "localhost:2181");
//        properties.setProperty("group.id", "customer-consumer-group-v2");
//
//
//        Properties config = new Properties();
//        config.setProperty("bootstrap.servers", "192.168.1.151:9092");
//        config.setProperty("group.id", "customer-consumer-group-v2");
//        config.setProperty("zookeeper.connect", "localhost:2181");
//
////        ConfluentRegistryAvroDeserializationSchema
//    }
//
//    @Test
//    public void testSpecificRecordWithConfluentSchemaRegistry() throws Exception {
//        DeserializationSchema<Customer> deserializer = AvroDeserializationSchema.forSpecific(Customer.class);
//
//        Customer customer = Customer.newBuilder()
//                .setWeight(1)
//                .setHeight(1)
//                .setAge(1)
//                .setLastName("1")
//                .setFirstName("1")
//                .setEmail("")
//                .setPhoneNumber("")
//                .build();
//
//        byte[] encodedAddress = writeRecord(customer, Customer.getClassSchema());
//        Customer deserializedAddress = deserializer.deserialize(encodedAddress);
//        assertEquals(customer, deserializedAddress);
//    }
//
//    /**
//     * Testing processing avro data from kafka
//     */
//    @Test
//    @DisplayName("Testing consuming avro rows from kafka")
//    public void TestAvro() throws Exception {
//        DataStreamSource<Customer> input = env
//                .addSource(
//                        new FlinkKafkaConsumer011<>(
//                                "customer-flink",
//                                ConfluentRegistryAvroDeserializationSchema.forSpecific(Customer.class, schemaRegistryUrl),
//                                config).setStartFromEarliest());
//    }
//
//    /**
//     * Writes given record using specified schema.
//     * @param record record to serialize
//     * @param schema schema to use for serialization
//     * @return serialized record
//     */
//    public static byte[] writeRecord(SpecificRecord record, Schema schema) throws IOException {
//        ByteArrayOutputStream stream = new ByteArrayOutputStream();
//        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(stream, null);
//
//        new SpecificDatumWriter<>(schema).write(record, encoder);
//        encoder.flush();
//        return stream.toByteArray();
//    }
//}
