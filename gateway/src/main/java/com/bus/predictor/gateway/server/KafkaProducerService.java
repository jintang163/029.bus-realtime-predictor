package com.bus.predictor.gateway.server;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.Future;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaProducer<String, String> producer;

    public KafkaProducerService(
            @Value("${kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${kafka.producer.acks:all}") String acks,
            @Value("${kafka.producer.retries:3}") int retries,
            @Value("${kafka.producer.batch-size:16384}") int batchSize,
            @Value("${kafka.producer.linger-ms:5}") int lingerMs,
            @Value("${kafka.producer.buffer-memory:33554432}") long bufferMemory) {

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        this.producer = new KafkaProducer<>(props);
        log.info("Kafka producer initialized, brokers={}", bootstrapServers);
    }

    public Future<RecordMetadata> send(String topic, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        return producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Kafka send failed, topic={}, key={}", topic, key, exception);
            }
        });
    }

    public void sendSync(String topic, String key, String value) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            producer.send(record).get();
        } catch (Exception e) {
            log.error("Kafka sync send failed, topic={}, key={}", topic, key, e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    @PreDestroy
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
            log.info("Kafka producer closed");
        }
    }
}
