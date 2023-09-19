run-kafka:
    docker run -d --name kafka-server \
        --network host \
        -p 9092:9092 \
        -e KAFKA_CFG_NODE_ID=0 \
        -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
        -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@:9093 \
        -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
        -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:9092 \
        -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
        -e KAFKA_CFG_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
        -e KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true \
        -e ALLOW_PLAINTEXT_LISTENER=yes \
        -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
        bitnami/kafka:latest

create-topic:
    docker run -it --rm --network host \
    bitnami/kafka:latest \
    kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --create \
    --topic test-topic

list-topics:
    docker run -it --rm --network host \
    bitnami/kafka:latest \
    kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --list

run-producer:
    docker run -it --rm --network host \
    bitnami/kafka:latest \
    kafka-console-producer.sh \
    --producer.config /opt/bitnami/kafka/config/producer.properties \
    --bootstrap-server localhost:9092 \
    --topic test-topic

run-consumer:
    docker run -it --rm --network host \
    bitnami/kafka:latest \
    kafka-console-consumer.sh \
    --consumer.config /opt/bitnami/kafka/config/consumer.properties \
    --bootstrap-server localhost:9092 \
    --topic test-topic --from-beginning
