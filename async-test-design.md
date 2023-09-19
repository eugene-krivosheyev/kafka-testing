Разработка тестов для асинхронных приложений с Kafka
====================================================

Синхронный вызов
----------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Синхронный/Блокирующий вызов 
hide footbox
skinparam lifelineStrategy solid
autonumber 1

control Client
control Service
|||
activate Client
Client -> Service: call operation
deactivate Client
activate Service

Service --> Client: result 
deactivate Service
activate Client

@enduml
```

Асинхронный вызов
-----------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Асинхронный/Неблокирующий вызов 
hide footbox
skinparam lifelineStrategy solid
autonumber 1

control Producer
control Consumer

activate Producer
Producer ->> Consumer: Async/Non Blocking call
activate Consumer
deactivate Consumer
@enduml
```

Отправка сообщения
------------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Отправка сообщения
hide footbox
skinparam lifelineStrategy solid
autonumber 1

control Producer
queue Broker

activate Producer
Producer -> Broker: Send message/event
deactivate Producer
activate Broker
Broker --> Producer: Received message
deactivate Broker
activate Producer

@enduml
```

Получение сообщения: опрос
-------------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Получение сообщения: опрос
hide footbox
skinparam lifelineStrategy solid
autonumber 1

control Producer
queue Topic
control Consumer

loop
activate Consumer
Consumer -> Topic: Get message
deactivate Consumer
activate Topic
Topic --> Consumer: nope
deactivate Topic
end

activate Producer
Producer -> Topic: Send message/event
deactivate Producer
activate Topic
Topic --> Producer: Received message
deactivate Topic
activate Producer

activate Consumer
Consumer -> Topic: Get message
deactivate Consumer
activate Topic
Topic --> Consumer: result message
deactivate Topic
@enduml
```

Получение сообщения: обратный вызов
-------------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Получение сообщения: обратный вызов
hide footbox
skinparam lifelineStrategy solid
autonumber 1

control Producer
queue Topic
boundary ConsumerAPI
control Consumer

activate Producer
Producer ->> Topic: Send message/event
deactivate Topic

Topic ->> ConsumerAPI: handle(message)
ConsumerAPI ->> Consumer
@enduml
```

Получение результата обработки: опрос
------------------------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Получение результата обработки: опрос
hide footbox
skinparam lifelineStrategy solid
autonumber 1

control Producer
queue Topic
boundary ConsumerAPI
control Consumer

Producer ->> Topic: Send message/event
Topic ->> Consumer
activate Consumer

loop
Producer -> ConsumerAPI: done?
ConsumerAPI -> Consumer
end

deactivate Consumer
Producer -> ConsumerAPI: get result
@enduml
```

Получение результата обработки: обратный вызов
------------------------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Получение результата обработки: обратный вызов
hide footbox
skinparam lifelineStrategy solid
autonumber 1

boundary ProducerAPI
control Producer
queue Topic
control Consumer

Producer ->> Topic: Send message/event
Topic ->> Consumer
activate Consumer
Consumer -> ProducerAPI
deactivate Consumer
ProducerAPI -> Producer
@enduml
```

Получение результата обработки: ответное сообщение
------------------------------
```plantuml
@startuml 'https://plantuml.com/sequence-diagram
title Получение результата обработки: ответное сообщение
hide footbox
skinparam lifelineStrategy solid
autonumber 1

control Producer
queue Topic
control Consumer

Producer ->> Topic: Send message/event
Topic ->> Consumer
activate Consumer
Consumer ->> Topic: Result
@enduml
```

Дизайн: варианты реализации
---------------------------
| Отправка сообщения | Получение сообщения | Получение результата | Получение ошибки   |
|--------------------|---------------------|----------------------|--------------------|
| Посылка сообщения  | Опрос               | Опрос                | Опрос              |
|                    | Обратный вызов      | Обратный вызов       | Обратный вызов     |
|                    |                     | Ответное сообщение   | Ответное сообщение |

---

Kafka
-----
- Broker
- Performance
- Scaling
- Topics
![](https://i.stack.imgur.com/zlpIN.png)
- "DB inside out" and durable log
- `offset`
![](https://datacadamia.com/_media/dit/kafka/log_consumer.png?fetcher=raw&tseed=1508935965)

Kafka @docker
-------------
- Images: `confluentinc` vs `bitnami`
- Externalized configuration
- Kafka client

Demo project structure
----------------------
- Dependencies
- Snippets

Тест-дизайн: варианты покрытия и тест-дублеры
------------------------------
```plantuml
@startuml 'https://plantuml.com/ru/component-diagram

component Sender <<app>>
interface Producer <<lib>>
component Topic <<lib>>
Sender -> Producer: send
interface Consumer <<lib>>
component Receiver <<app>>
interface KafkaStreams <<lib>>

Producer .> Topic
Topic <. Consumer
Consumer <- Receiver: poll
Topic <.. KafkaStreams
KafkaStreams -> Receiver

@enduml
```

---

```plantuml
@startuml 'https://plantuml.com/ru/component-diagram
package "Test Scope 1" {
    component Sender <<app>><<sut>>
    interface Producer <<mock>>

    Sender -> Producer: send
}
@enduml
```
```plantuml
@startuml 'https://plantuml.com/ru/component-diagram
package "Test Scope 2" {
    component Receiver <<app>><<sut>>

    interface Consumer <<mock>>
    Receiver -> Consumer: subscribe
    Receiver -> Consumer: poll
}
@enduml
```
```plantuml
@startuml 'https://plantuml.com/ru/component-diagram
package "Test Scope 3" {
    component Receiver <<app>><<sut>>
    component Topic <<mock>>
    interface KafkaStreams <<lib>>
    Topic <.. KafkaStreams
    KafkaStreams -> Receiver 
}
@enduml
```

```plantuml
@startuml 'https://plantuml.com/ru/component-diagram
package "Test Scope 4" {
    component Sender <<app>><<sut>>
    interface Producer <<lib>>
    component Topic <<lib>>
    Sender -> Producer: send
    interface Consumer <<lib>>
    component Receiver <<app>><<sut>>

    Producer .> Topic
    Topic <. Consumer
    Consumer <- Receiver: poll
}
@enduml
```
```plantuml
@startuml 'https://plantuml.com/ru/component-diagram
package "Test Scope 5" {
    interface Producer <<lib>>
    component Topic <<lib>>
    component Receiver <<app>><<sut>>
    interface KafkaStreams <<lib>>
    
    Producer .> Topic
    Topic <.. KafkaStreams
    KafkaStreams -> Receiver
}
@enduml
```
