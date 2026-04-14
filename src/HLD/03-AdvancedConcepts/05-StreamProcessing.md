# Stream Processing

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Good to Know

---

## Batch vs Stream Processing

```
BATCH PROCESSING:                     STREAM PROCESSING:
  Collect data → Process all at once    Process data as it arrives

  [data]──►[data]──►[data]──►         [data]──►[process]──►[output]
  [data]──►[data]──►[data]──►             immediately!
  [data]──►[data]──►[data]──►
            │                          Latency: milliseconds to seconds
   [batch process all]                 
            │                          Examples:
        [results]                      - Fraud detection (real-time)
                                       - Live dashboards
  Latency: minutes to hours            - Trending topics
                                       - Real-time recommendations
  Examples:
  - Daily reports
  - ETL pipelines
  - ML model training

LAMBDA ARCHITECTURE (combines both):

            ┌──────────────────┐
  Events ──►│   Kafka (Log)    │
            └──────┬───────────┘
                   │
          ┌────────┴────────┐
          ▼                 ▼
   ┌─────────────┐  ┌──────────────┐
   │ Batch Layer │  │ Speed Layer  │
   │ (Spark,     │  │ (Flink,      │
   │  Hadoop)    │  │  Kafka       │
   │             │  │  Streams)    │
   │ Accurate    │  │ Fast but     │
   │ but slow    │  │ approximate  │
   └──────┬──────┘  └──────┬───────┘
          │                │
          ▼                ▼
   ┌─────────────────────────────┐
   │      Serving Layer          │
   │  (merge batch + real-time)  │
   └─────────────────────────────┘
```

---

## 1. Stream Processing Frameworks

```
┌──────────────────┬───────────────┬──────────────┬───────────────┐
│ Feature          │ Kafka Streams │ Apache Flink │ Apache Spark  │
│                  │               │              │ Streaming     │
├──────────────────┼───────────────┼──────────────┼───────────────┤
│ Processing Model │ Record-at-a-  │ Record-at-a- │ Micro-batch   │
│                  │ time          │ time         │               │
│ Latency          │ Low (ms)      │ Very low (ms)│ Medium (sec)  │
│ State Management │ Built-in      │ Built-in     │ External      │
│ Exactly-Once     │ Yes           │ Yes          │ Yes           │
│ Deployment       │ Just a library│ Cluster      │ Cluster       │
│ Best For         │ Kafka-centric │ Complex event│ Unified batch │
│                  │ pipelines     │ processing   │ + streaming   │
└──────────────────┴───────────────┴──────────────┴───────────────┘
```

---

## 2. Common Stream Processing Patterns

```
1. EVENT SOURCING
   ────────────────
   Store every state change as an immutable event.
   
   Events:
   [OrderCreated] → [ItemAdded] → [ItemAdded] → [OrderPaid] → [OrderShipped]
   
   Current state = replay all events from beginning
   
   Pros: Full audit trail, can rebuild state, time-travel debugging
   Cons: Event store grows forever, replay can be slow
   Used by: Banking systems, e-commerce (order history)


2. CQRS (Command Query Responsibility Segregation)
   ─────────────────────────────────────────────────
   Separate the write model from the read model.

   ┌──────────┐     ┌──────────┐
   │ Commands │────►│  Write   │────► Event Store ────► [Events]
   │ (writes) │     │  Model   │                            │
   └──────────┘     └──────────┘                            │
                                                   ┌────────▼──────┐
   ┌──────────┐     ┌──────────┐                   │  Projection   │
   │ Queries  │◄────│  Read    │◄──────────────────│  (transform   │
   │ (reads)  │     │  Model   │                   │   events to   │
   └──────────┘     └──────────┘                   │   read views) │
                                                   └───────────────┘

   Write model: Optimized for writes (normalized, event store)
   Read model: Optimized for reads (denormalized, materialized views)
   
   Often combined with Event Sourcing.


3. CHANGE DATA CAPTURE (CDC)
   ──────────────────────────
   Capture database changes and stream them to other systems.

   ┌────────────┐    ┌──────────┐    ┌──────────────────────┐
   │ PostgreSQL │───►│ Debezium │───►│ Kafka Topic          │
   │ (source)   │    │ (CDC)    │    │ [INSERT user_1]      │
   │            │    │          │    │ [UPDATE user_2]      │
   └────────────┘    └──────────┘    │ [DELETE user_3]      │
                                     └────────┬─────────────┘
                                         ┌────┴────┐
                                         ▼         ▼
                                   Elasticsearch  Data
                                   (search index) Warehouse
   
   Use: Keep search index in sync, build analytics pipelines,
        replicate data across microservices
```

---

## 3. Windowing (Processing Time Windows)

```
TUMBLING WINDOW:
  Fixed-size, non-overlapping windows.
  
  |──Window 1──|──Window 2──|──Window 3──|
  |  events    |  events    |  events    |
  0           5s           10s          15s

  Use: "Count clicks per 5-second window"


SLIDING WINDOW:
  Fixed-size windows that overlap.
  
  |──Window 1──────|
     |──Window 2──────|
        |──Window 3──────|
  0    2s   4s   6s   8s  10s

  Use: "Moving average over last 5 seconds, updated every 2 seconds"


SESSION WINDOW:
  Dynamic windows based on activity gaps.
  
  |events|  gap  |events events|  gap  |events|
  |─Session 1─|       |──Session 2──|      |─S3─|

  Use: "Group user activity into sessions (5min inactivity = new session)"
```

---

## 4. Key Takeaways for Interviews

1. **Kafka + Flink/Kafka Streams** is the standard answer for real-time processing
2. **Event Sourcing** when you need audit trail or undo capability
3. **CQRS** when read and write patterns are very different
4. **CDC (Debezium)** to keep derived data stores in sync
5. Mention stream processing for: fraud detection, trending topics, real-time analytics, notifications
6. **Lambda architecture** when you need both real-time and batch accuracy
