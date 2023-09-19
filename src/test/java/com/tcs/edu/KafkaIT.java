package com.tcs.edu;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class KafkaIT {
    @Container
    @SuppressWarnings("rawtypes")
    /*
     * - [ ] KafkaContainer with Spring Boot
     * - [ ] ZooKeeper vs Kraft
     */
    GenericContainer kafka = new GenericContainer(DockerImageName.parse("bitnami/kafka:3.5.1"))
            .withNetworkMode("host")
            .withEnv("KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
            .withEnv("KAFKA_CFG_ADVERTISED_LISTENERS", "PLAINTEXT://127.0.0.1:9092")
            .withEnv("KAFKA_CFG_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_CFG_NODE_ID", "0")
            .withEnv("KAFKA_CFG_CONTROLLER_QUORUM_VOTERS", "0@:9093")
            .withEnv("KAFKA_CFG_LISTENERS", "PLAINTEXT://:9092,CONTROLLER://:9093")
            .withEnv("KAFKA_CFG_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_CFG_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true") //NB
            .withEnv("KAFKA_CREATE_TOPICS", "test-topic") //NB
            .withStartupTimeout(Duration.ofSeconds(120))
            .withReuse(true);

    /*
     * Consumer vs Producer settings.
     */
    final Map<String, Object> settings = Map.<String, Object>of(
            "bootstrap.servers", "localhost:9092",
            "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
            "value.serializer", "org.apache.kafka.common.serialization.StringSerializer",
            "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
            "group.id", "0", //NB for consumer tests
            "auto.offset.reset", "earliest", //NB for consumer tests
            "application.id", "test-app" //NB for kafka streams tests
//            "acks", "all" //REF https://developer.confluent.io/tutorials/creating-first-apache-kafka-producer-application/kafka.html
    );

    @BeforeEach
    public void createTopic() {
        try (var adminClient = AdminClient.create(settings)) {
            final var topic = new NewTopic("test-topic", 1, (short) 1);
            adminClient.createTopics(Collections.singletonList(topic));
        }
    }

    /**
     * <a href="https://docs.confluent.io/kafka-clients/java/current/overview.html">Java Kafka API</a>
     * @startuml
     * package "Test Scope 4" {
     *     component Sender <<app>><<sut>>
     *     interface Producer <<lib>>
     *     component Topic <<lib>>
     *     Sender -> Producer: send
     *     interface Consumer <<lib>>
     *     component Receiver <<app>><<sut>>
     *     Producer .> Topic
     *     Topic <. Consumer
     *     Consumer <- Receiver: poll
     * }
     * @enduml
     */
    @Test
    public void shouldSendAndReceiveMessageWithPolling() throws ExecutionException, InterruptedException {
        try (final var consumer = new KafkaConsumer<String, String>(settings);
             final var producer = new KafkaProducer<String, String>(settings)) {

            //region тут должен быть SUT Sender
            //NB asynchronous writes
            var resultPromise = producer.send(new ProducerRecord<>("test-topic", UUID.randomUUID().toString(), "Hello World!"));
//                    (metadata, exception) -> { //NB optional callback
//                        if (exception != null) exception.printStackTrace();
//                        else System.out.println("record sent: " + metadata);
//                    });
            //NB resultPromise.get() for synchronous writes
            //NB producer.flush() for batch writes
            //endregion

            //region тут должен быть SUT Receiver
            consumer.subscribe(Collections.singletonList("test-topic")); //NB timing + "auto.offset.reset"
            ConsumerRecords<String, String> records;
            do {
                records = consumer.poll(Duration.ofMillis(100));
                //NB consumer.commitSync(); consumer.commitAsync();
            } while (records.isEmpty());
            //endregion

            assertThat(records)
                    .extracting("value")
                    .containsExactly("Hello World!");
        }
    }

    /**
     * @startuml
     * package "Test Scope 5" {
     *     interface Producer <<lib>>
     *     component Topic <<lib>>
     *     component Receiver <<app>><<sut>>
     *     interface KafkaStreams <<lib>>
     *     Producer .> Topic
     *     Topic <.. KafkaStreams
     *     KafkaStreams -> Receiver
     * }
     * @enduml
     */
    @Test
    public void shouldSendAndReceiveMessageWithKafkaStreamsCallback() throws InterruptedException, ExecutionException {
        final var result = new AtomicReference<String>(); //NB not perfect option for latching

        //region SUT
        final var builder = new StreamsBuilder();
        builder.stream("test-topic", Consumed.with(Serdes.String(), Serdes.String()))
                .peek((k, v) -> System.out.println("Observed event: " + v))
                .mapValues(s -> s.toUpperCase())
                .peek((k, v) -> System.out.println("Transformed event: " + v))
                .foreach((k, v) -> result.set(v)); //.to("output-topic", Produced.with(Serdes.String(), Serdes.String()));
        final var topology = builder.build();
        //endregion

        final var properties = new Properties();
        properties.putAll(settings);
        try (final var streams = new KafkaStreams(topology, properties);
             final var producer = new KafkaProducer<String, String>(settings)) {

            final var started = new CountDownLatch(1);
            streams.setStateListener((newState, oldState) -> {
                if (newState == KafkaStreams.State.RUNNING) {
                    started.countDown();
                }
            });

            streams.start();
            started.await();
            producer.send(
                new ProducerRecord<>("test-topic", UUID.randomUUID().toString(), "Hello World!")
            ).get(); //NB synchronous write

            while (isNull(result.get())) {
                Thread.sleep(10);
            }
            assertThat(result.get()).isEqualTo("HELLO WORLD!");
        }
    }
}
