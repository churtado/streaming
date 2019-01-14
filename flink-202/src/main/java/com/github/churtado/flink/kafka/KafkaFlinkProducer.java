package com.github.churtado.flink.kafka;

import com.example.Customer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaFlinkProducer {


    public static void main(String[] args) {

        DataStream<Customer> customers = getCustomers();

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "192.168.1.151:9092");
        properties.setProperty("acks", "1");
        properties.setProperty("retries", "10");

        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", KafkaAvroSerializer.class.getName());
        properties.setProperty("schema.registry.url", "http://192.168.1.151:8081");

//        KafkaProducer<String, Customer> kafkaProducer = new KafkaProducer<String, Customer>(properties);
//        String topic = "customer-flink";
//
//        Customer customer = customers
//

//        ProducerRecord<String, Customer> producerRecord = new ProducerRecord<String, Customer>(
//                topic, customer
//        );
//
//        kafkaProducer.send(producerRecord, new Callback() {
//            @Override
//            public void onCompletion(RecordMetadata recordMetadata, Exception e) {
//                if (e == null) {
//                    System.out.println("Success");
//                    System.out.println(recordMetadata.toString());
//                } else {
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        kafkaProducer.flush();
//        kafkaProducer.close();
    }

    public static DataStream<Customer> getCustomers() {
        StreamExecutionEnvironment env;
        FlinkKafkaConsumer011<Customer> consumer011;
        DataStreamSource<Customer> customers;

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

        return customers;
    }
}
