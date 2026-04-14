# Message Queues & Event-Driven Architecture

> **Difficulty:** Easy-Medium | **Time:** 2 hours | **Priority:** Must Know

---

## Why Message Queues

Queues decouple producers from consumers, enabling async processing, load leveling, and fault tolerance.

```
WITHOUT Queue (Tight Coupling):        WITH Queue (Decoupled):

  Service A ──sync call──► Service B    Service A ──► [Queue] ──► Service B
     │                        │            │                         │
     │ Waits for response     │            │ Fire & forget           │
     │ If B is down → A fails │            │ If B is down →          │
     │ If B is slow → A slow  │            │   messages wait         │
                                           │ B processes at own pace │

  A and B must be UP together           A and B are independent
  A and B must handle same throughput   Queue absorbs traffic spikes
```

---

## 1. Core Concepts

```
┌────────────────────────────────────────────────────────────────┐
│                    MESSAGE QUEUE ANATOMY                        │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌──────────┐    ┌─────────────────────┐    ┌──────────────┐  │
│  │ Producer │    │      Queue          │    │   Consumer   │  │
│  │ (Sender) │───►│ [msg1][msg2][msg3]  │───►│  (Receiver)  │  │
│  └──────────┘    └─────────────────────┘    └──────────────┘  │
│                                                                │
│  Producer: Publishes messages to the queue                     │
│  Queue: Stores messages until consumed (durable)               │
│  Consumer: Pulls/receives messages and processes them          │
│                                                                │
│  Delivery Guarantees:                                          │
│  ┌────────────────┬──────────────────────────────────────┐    │
│  │ At-most-once   │ Message delivered 0 or 1 time        │    │
│  │                │ (may lose messages)                   │    │
│  ├────────────────┼──────────────────────────────────────┤    │
│  │ At-least-once  │ Message delivered 1+ times            │    │
│  │                │ (may have duplicates — MOST COMMON)   │    │
│  ├────────────────┼──────────────────────────────────────┤    │
│  │ Exactly-once   │ Message delivered exactly 1 time      │    │
│  │                │ (very hard to achieve, Kafka supports) │    │
│  └────────────────┴──────────────────────────────────────┘    │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 2. Message Queue vs Pub/Sub vs Event Streaming

```
1. MESSAGE QUEUE (Point-to-Point)
   ──────────────────────────────
   One message → one consumer
   
   Producer ──► [Queue] ──► Consumer A
                         (message removed after consumption)
   
   Examples: RabbitMQ, Amazon SQS, ActiveMQ
   Use: Task queues, email sending, order processing


2. PUB/SUB (Publish-Subscribe)
   ───────────────────────────
   One message → multiple subscribers
   
                          ┌──► Subscriber A (notifications)
   Publisher ──► [Topic] ─┼──► Subscriber B (analytics)
                          └──► Subscriber C (search index)
   
   Examples: Redis Pub/Sub, Google Pub/Sub, SNS
   Use: Notifications, event broadcasting, fan-out


3. EVENT STREAMING (Log-Based)
   ────────────────────────────
   Durable, ordered, replayable log of events
   
   Producer ──► [Topic: Partition 0] [msg1][msg2][msg3]...
                [Topic: Partition 1] [msg4][msg5][msg6]...
                
   Consumer groups can read independently
   Messages are NOT deleted after consumption (retained by time/size)
   
   Examples: Apache Kafka, Amazon Kinesis, Apache Pulsar
   Use: Event sourcing, CDC, real-time analytics, log aggregation
```

---

## 3. Apache Kafka Deep Dive

Kafka is the most important message system for interviews.

```
KAFKA ARCHITECTURE:

  ┌──────────────────────────────────────────────────────────┐
  │                    KAFKA CLUSTER                          │
  │                                                          │
  │  ┌─────────────────────────────────────────────────┐    │
  │  │ Topic: "orders"                                  │    │
  │  │                                                  │    │
  │  │  Partition 0: [msg0][msg1][msg2][msg3]──►        │    │
  │  │  Partition 1: [msg4][msg5][msg6]──►              │    │
  │  │  Partition 2: [msg7][msg8]──►                    │    │
  │  │                                                  │    │
  │  └─────────────────────────────────────────────────┘    │
  │                                                          │
  │  Broker 1        Broker 2        Broker 3                │
  │  (P0-leader)     (P1-leader)     (P2-leader)             │
  │  (P1-replica)    (P2-replica)    (P0-replica)            │
  │                                                          │
  └──────────────────────────────────────────────────────────┘

  ┌──────────┐                              ┌──────────────────┐
  │Producers │──► Partition by key ──►      │ Consumer Group A │
  │          │    hash(order_id) % 3        │  C1 ← P0         │
  └──────────┘    = partition number        │  C2 ← P1         │
                                            │  C3 ← P2         │
                                            └──────────────────┘
                                            ┌──────────────────┐
                                            │ Consumer Group B │
                                            │  C4 ← P0,P1     │
                                            │  C5 ← P2        │
                                            └──────────────────┘
```

### Key Kafka Concepts
| Concept | Meaning |
|---------|---------|
| Topic | A category/feed of messages (like a table) |
| Partition | Ordered, immutable sequence within a topic |
| Offset | Position of a message within a partition |
| Consumer Group | Set of consumers that share partitions (each partition → 1 consumer) |
| Replication Factor | Number of copies per partition (typically 3) |
| Retention | How long messages are kept (default 7 days) |

---

## 4. RabbitMQ vs Kafka

| Feature | RabbitMQ | Kafka |
|---------|----------|-------|
| Model | Message queue (push) | Event log (pull) |
| Message Deletion | After consumption | After retention period |
| Ordering | Per queue | Per partition |
| Replay | No | Yes (any offset) |
| Throughput | ~50K msg/s | ~1M msg/s |
| Use Case | Task queues, RPC | Event streaming, analytics |
| Routing | Complex (exchanges, bindings) | Simple (topic + partition) |

---

## 5. Common Patterns

```
1. WORK QUEUE (Load Distribution)
   
   ┌──────────┐     ┌───────┐     ┌──────────┐
   │ Producer │────►│ Queue │────►│ Worker 1 │
   └──────────┘     │       │────►│ Worker 2 │
                    │       │────►│ Worker 3 │
                    └───────┘     └──────────┘
   Each message processed by ONE worker.


2. FAN-OUT (Broadcast)
   
   ┌──────────┐     ┌─────────┐     ┌──────────────┐
   │ Producer │────►│Exchange │────►│ Queue A → C1 │
   └──────────┘     │(fanout) │────►│ Queue B → C2 │
                    │         │────►│ Queue C → C3 │
                    └─────────┘     └──────────────┘
   Each consumer gets EVERY message.


3. DEAD LETTER QUEUE (Error Handling)
   
   ┌───────┐     ┌──────────┐     Failed 3x     ┌─────────┐
   │ Queue │────►│ Consumer │ ──────────────────►│  DLQ    │
   └───────┘     │ (retry)  │                    │(inspect)│
                 └──────────┘                    └─────────┘
   Messages that fail processing go to DLQ for investigation.
```

---

## 6. Key Takeaways for Interviews

1. **Use Kafka** for event streaming, log aggregation, real-time analytics
2. **Use RabbitMQ/SQS** for task queues, job processing, email sending
3. **Mention delivery guarantees**: at-least-once + idempotent consumers is the standard pattern
4. **Consumer groups** in Kafka allow parallel processing and independent consumption
5. **Dead letter queues** for handling failures gracefully
6. Always mention queues when you need to **decouple services** or **handle traffic spikes**
