package com.tcs.edu;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaTest {
    /**
     * @startuml
     * package "Test Scope 1" {
     *     component Sender <<app>><<sut>>
     *     interface Producer <<mock>>
     *     Sender -> Producer: send
     * }
     * @enduml
     */
    @Test
    public void shouldSendMessageWithMocks() {
        try(final var factory = Serdes.String();
            final var stringSerializer = factory.serializer();
            final var mockProducer = new MockProducer<>(true, stringSerializer, stringSerializer)) {

            //region SUT
            mockProducer.send(new ProducerRecord<>("test-topic", UUID.randomUUID().toString(), "Hello World!"));
            //endregion

            //region Asserts
            assertThat(mockProducer.history())
                    .extracting(ProducerRecord::value)
                    .containsExactly("Hello World!");
            //endregion
        }
    }

    /**
     * <a href="https://www.baeldung.com/kafka-mockconsumer">REF</a>
     * @startuml
     * package "Test Scope 2" {
     *     component Receiver <<app>><<sut>>
     *     interface Consumer <<mock>>
     *     Receiver -> Consumer: subscribe
     *     Receiver -> Consumer: poll
     * }
     * @enduml
     */
    @Test
    public void shouldReceiveMessageWithMocks() {
        try(final var mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST)) {
            mockConsumer.updateBeginningOffsets(Collections.singletonMap(new TopicPartition("test-topic", 0), 0L));
            mockConsumer.schedulePollTask(() -> {
                mockConsumer.rebalance(Collections.singletonList(new TopicPartition("test-topic", 0)));
                mockConsumer.addRecord(new ConsumerRecord<>(
                        "test-topic", 0, 0,
                        UUID.randomUUID().toString(),
                        "Hello World 0!"));
                mockConsumer.addRecord(new ConsumerRecord<>(
                        "test-topic", 0, 1,
                        UUID.randomUUID().toString(),
                        "Hello World 1!"));
            });

            //region тут должен быть SUT
            mockConsumer.subscribe(Collections.singletonList("test-topic"));
            final var consumerRecords = mockConsumer.poll(Duration.ofMillis(0));
            //endregion

            //region Asserts
            assertThat(consumerRecords)
                    .extracting(ConsumerRecord::value)
                    .containsExactly("Hello World 0!", "Hello World 1!");
            //endregion
        }
    }

    /**
     * <a href="https://docs.confluent.io/platform/current/streams/developer-guide/test-streams.html">REF</a>
     * @startuml
     * package "Test Scope 3" {
     *     component Receiver <<app>><<sut>>
     *     component Topic <<mock>>
     *     interface KafkaStreams <<lib>>
     *     Topic <.. KafkaStreams
     *     KafkaStreams -> Receiver
     * }
     * @enduml
     */
    @Test
    public void shouldSendAndReceiveMessageWithKafkaStreamsMocks() {
        //region SUT
        final var builder = new StreamsBuilder();
        builder.stream("test-topic", Consumed.with(Serdes.String(), Serdes.String()))
                .peek((k, v) -> System.out.println("Observed event: " + v))
                .mapValues(s -> s.toUpperCase())
                .peek((k, v) -> System.out.println("Transformed event: " + v))
                .to("output-topic", Produced.with(Serdes.String(), Serdes.String()));
//                .foreach((k, v) -> System.out.println("Sent event: " + v));
        final var topology = builder.build();
        //endregion

        try(final var testDriver = new TopologyTestDriver(topology);
            final var stringSerdeFactory = Serdes.String();
            final var deserializer = stringSerdeFactory.deserializer();
            final var stringSerializer = stringSerdeFactory.serializer()) {

            final var inputTopic = testDriver.createInputTopic("test-topic", stringSerializer, stringSerializer);
            final var outputTopic = testDriver.createOutputTopic("output-topic", deserializer, deserializer);

            inputTopic.pipeInput("Hello World!");
            //topology working for a while
            assertThat(outputTopic.readValuesToList())
                    .containsExactly("HELLO WORLD!");
        }
    }
}
