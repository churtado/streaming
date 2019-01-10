package com.github.churtado.kafka.payment;

import com.githubt.churtado.avro.Payment;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class ConsumerExample {

    private static final String TOPIC = "transactions";

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) {
        final Properties properties = new Properties();
        // normal consumer
        properties.setProperty("bootstrap.servers","localhost:9092");
        properties.put("group.id", "transaction-consumer-group-v1");
        properties.put("auto.commit.enable", "false");
        properties.put("auto.offset.reset", "earliest");

        // avro part (deserializer)
        properties.setProperty("key.deserializer", StringDeserializer.class.getName());
        properties.setProperty("value.deserializer", KafkaAvroDeserializer.class.getName());
        properties.setProperty("schema.registry.url", "http://127.0.0.1:8081");
        properties.setProperty("specific.avro.reader", "true");

        KafkaConsumer<String, Payment> kafkaConsumer = new KafkaConsumer<>(properties);
        String topic = "transactions";
        kafkaConsumer.subscribe(Collections.singleton(topic));

        System.out.println("Waiting for data...");

        while (true){
//            System.out.println("Polling");
            ConsumerRecords<String, Payment> records = kafkaConsumer.poll(Duration.ofMillis(100));

            for (ConsumerRecord<String, Payment> record : records){
                String key = record.key();
                Payment payment = record.value();
                System.out.println(payment);
            }

            kafkaConsumer.commitSync();
        }

    }
}
