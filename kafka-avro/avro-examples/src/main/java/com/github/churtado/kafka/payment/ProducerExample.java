package com.github.churtado.kafka.payment;

import com.githubt.churtado.avro.Payment;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * The key thing to remember here is that I got this working for a confluent docker setup
 * In order to get that working look at the yaml file in the avro folder at the root of
 * this project, specifically cp-docker-images/examples/cp-all-in-one
 * There, I changed the <ADVERTISED*> variables in the yaml file to point to the ip
 * address of the actual host, not the container. It's most important for the broker
 */

public class ProducerExample {

    public static void main(String[] args) {

        Properties properties = new Properties();
        // normal producer
        properties.setProperty("bootstrap.servers", "192.168.1.151:9092");
        properties.setProperty("acks", "all");
        properties.setProperty("retries", "10");
        // avro part
        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", KafkaAvroSerializer.class.getName());
        properties.setProperty("schema.registry.url", "http://localhost:8081");

        Producer<String, Payment> producer = new KafkaProducer<String, Payment>(properties);

        String topic = "transactions";

        // copied from avro examples
        Payment payment = Payment.newBuilder()
                .setAmount(1000l)
                .setId("id")
                .build();

        ProducerRecord<String, Payment> producerRecord = new ProducerRecord<String, Payment>(
                topic, payment
        );
        producerRecord.key();

        System.out.println(payment);
        producer.send(producerRecord, new Callback() {
            @Override
            public void onCompletion(RecordMetadata metadata, Exception exception) {
                if (exception == null) {
                    System.out.println("topic: " + metadata.topic());
                } else {
                    exception.printStackTrace();
                }
            }
        });

        producer.flush();
        producer.close();
    }
}
