# RabbitMQ — The Complete Deep Dive

> **Difficulty:** Medium-Hard | **Time:** 6-8 hours | **Priority:** Must Know  
> **Sources:** RabbitMQ Official Docs, RabbitMQ Internals (GitHub), Erlang/OTP Docs, CloudAMQP, AlgoMaster.io, Arpit Bhayani, InfoQ, ScaleGrid  
> **For:** Senior Engineers (7+ years) preparing for System Design interviews

---

## Table of Contents

1. [What Is RabbitMQ](#1-what-is-rabbitmq)
2. [Core Architecture — AMQP Model](#2-core-architecture--amqp-model)
3. [Exchange Types Deep Dive](#3-exchange-types-deep-dive)
4. [Queue Internals — How Messages Are Stored](#4-queue-internals--how-messages-are-stored)
5. [Quorum Queues & Raft Consensus](#5-quorum-queues--raft-consensus)
6. [Streams — Kafka-Like Replay in RabbitMQ](#6-streams--kafka-like-replay-in-rabbitmq)
7. [Producers Deep Dive — Confirms & Transactions](#7-producers-deep-dive--confirms--transactions)
8. [Consumers Deep Dive — Prefetch, Ack, QoS](#8-consumers-deep-dive--prefetch-ack-qos)
9. [Flow Control & Backpressure — The Credit System](#9-flow-control--backpressure--the-credit-system)
10. [Dead Letter Exchanges & Retry Patterns](#10-dead-letter-exchanges--retry-patterns)
11. [Clustering & Network Partitions](#11-clustering--network-partitions)
12. [Federation & Shovel — Multi-DC Replication](#12-federation--shovel--multi-dc-replication)
13. [Real-World Usage at Scale](#13-real-world-usage-at-scale)
14. [When RabbitMQ Failed — Production Incidents](#14-when-rabbitmq-failed--production-incidents)
15. [When NOT to Use RabbitMQ](#15-when-not-to-use-rabbitmq)
16. [Anti-Patterns That Kill RabbitMQ](#16-anti-patterns-that-kill-rabbitmq)
17. [RabbitMQ vs Kafka — When to Pick Which](#17-rabbitmq-vs-kafka--when-to-pick-which)
18. [Performance Tuning Cheat Sheet](#18-performance-tuning-cheat-sheet)
19. [Interview Questions — Medium](#19-interview-questions--medium)
20. [Interview Questions — Hard](#20-interview-questions--hard)
21. [Quick Reference Card](#21-quick-reference-card)

---

## 1. What Is RabbitMQ

RabbitMQ is a **message broker** — a middleman that accepts messages from producers and routes them to consumers. Originally developed by Rabbit Technologies Ltd (acquired by VMware, now Broadcom) in 2007, written in **Erlang/OTP**.

It implements the **Advanced Message Queuing Protocol (AMQP 0-9-1)** and since v4.0 also natively supports **AMQP 1.0**, MQTT, and STOMP.

It is NOT a distributed commit log (like Kafka). It is a **smart broker / dumb consumer** system.

```
Traditional HTTP:                          RabbitMQ (Message Broker):

  Service A ──HTTP──→ Service B             Service A → [Exchange→Queue] → Service B
                                            
  ✗ Tight coupling (A must know B)          ✓ Decoupled (A doesn't know B exists)
  ✗ B must be online                        ✓ B can be offline (queue buffers)
  ✗ No retry built in                       ✓ Retry, DLQ, TTL built in
  ✗ No fan-out                              ✓ Fan-out to N consumers via exchanges
  ✗ Backpressure = errors                   ✓ Backpressure = queue depth grows
  ✗ No priority                             ✓ Priority queues supported
```

### The Broker Model vs The Log Model

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                                                                               │
│   RABBITMQ (Smart Broker)              KAFKA (Dumb Broker / Smart Consumer)   │
│                                                                               │
│   ┌──────────┐                         ┌──────────────────┐                   │
│   │ Exchange  │─routes─→ Queue A       │ Partition 0 [|||||||] │              │
│   │ (routing  │─routes─→ Queue B       │ Partition 1 [|||||||] │              │
│   │  logic)   │─routes─→ Queue C       │ Partition 2 [|||||||] │              │
│   └──────────┘                         └──────────────────┘                   │
│                                                                               │
│   • Broker decides WHERE                • Consumer decides WHERE              │
│   • Message DELETED after ack           • Message RETAINED (time/size)        │
│   • Complex routing (topic, headers)    • Simple partition routing             │
│   • Per-message delivery tracking       • Offset-based tracking               │
│   • Push model (broker → consumer)      • Pull model (consumer → broker)      │
│   • Lower throughput (~40K-100K/s)      • Higher throughput (~1M+/s)          │
│   • Better latency (sub-ms possible)    • Latency 2-50ms typical             │
│   • Built for task queues               • Built for event streams             │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Three Core Capabilities

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                     RABBITMQ = THREE SYSTEMS IN ONE                           │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  1. MESSAGE ROUTING          2. WORK DISTRIBUTION       3. ASYNC DECOUPLING  │
│  ┌────────────────────┐    ┌─────────────────────┐   ┌──────────────────┐    │
│  │ Sophisticated       │    │ Load balance tasks   │   │ Temporal           │    │
│  │ exchange routing    │    │ across workers with  │   │ decoupling —       │    │
│  │ (topic, headers,   │    │ prefetch & priority  │   │ producer and       │    │
│  │  fanout, direct)   │    │ queues               │   │ consumer run       │    │
│  └────────────────────┘    └─────────────────────┘   │ independently      │    │
│                                                       └──────────────────┘    │
│  → Route by pattern,          → Competing consumers     → Buffer spikes,     │
│    headers, binding keys        with fair dispatch         retry on failure   │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Core Architecture — AMQP Model

### The Full Message Flow — Master Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                          RABBITMQ ARCHITECTURE — FULL PICTURE                         │
│                                                                                       │
│   PRODUCER                     BROKER (Erlang VM / BEAM)                   CONSUMER   │
│  ┌────────┐              ┌─────────────────────────────────────┐         ┌────────┐  │
│  │        │──publish──→  │                                     │ ←─sub── │        │  │
│  │ App /  │              │   ┌──────────┐     ┌──────────┐    │         │ App /  │  │
│  │ Service│   AMQP       │   │ Exchange │────→│ Queue A  │────│────→    │ Worker │  │
│  │        │   0-9-1      │   │          │     └──────────┘    │         │        │  │
│  └────────┘   or         │   │ (routes  │     ┌──────────┐    │         └────────┘  │
│               1.0        │   │  via     │────→│ Queue B  │────│────→   ┌────────┐  │
│  ┌────────┐              │   │ bindings)│     └──────────┘    │        │ Worker │  │
│  │        │──publish──→  │   │          │     ┌──────────┐    │        │   2    │  │
│  │ App 2  │              │   └──────────┘────→│ Queue C  │────│────→   └────────┘  │
│  │        │              │                     └──────────┘    │                     │
│  └────────┘              │                                     │                     │
│                          │   ┌────────────────────────────┐   │                     │
│                          │   │ Virtual Host (vhost)       │   │                     │
│                          │   │ • Namespace isolation       │   │                     │
│                          │   │ • Own exchanges, queues     │   │                     │
│                          │   │ • Own permissions           │   │                     │
│                          │   └────────────────────────────┘   │                     │
│                          │                                     │                     │
│                          │   Mnesia DB (metadata store)        │                     │
│                          │   • Exchange definitions             │                     │
│                          │   • Queue definitions                │                     │
│                          │   • Bindings                         │                     │
│                          │   • Users, vhosts, permissions       │                     │
│                          └─────────────────────────────────────┘                     │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

### AMQP 0-9-1 Entities

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AMQP 0-9-1 MODEL                                    │
│                                                                             │
│  CONNECTION (TCP socket, long-lived)                                        │
│  ├── CHANNEL 1 (lightweight virtual connection)                             │
│  │   ├── Declare exchange "orders.topic"                                    │
│  │   ├── Declare queue "payment.process"                                    │
│  │   ├── Bind queue to exchange with routing key "order.created.#"          │
│  │   └── Publish / Consume on this channel                                  │
│  ├── CHANNEL 2                                                              │
│  │   └── ... independent operations ...                                     │
│  └── CHANNEL N                                                              │
│                                                                             │
│  KEY INSIGHT: Channels multiplex over a single TCP connection.              │
│  Creating a new TCP connection per operation is an ANTI-PATTERN.            │
│  Each channel is single-threaded in the broker (Erlang process).            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### The Internal Message Pipeline (Erlang Processes)

```
                    Each arrow = Erlang message passing
                    
  TCP Socket            Channel               Queue Process          Disk / RAM
  ┌────────┐          ┌──────────┐           ┌──────────────┐      ┌───────────┐
  │ rabbit  │ ──msg──→│ rabbit   │ ──msg──→  │ rabbit       │ ───→ │ message   │
  │ _reader │         │ _channel │           │ _amqqueue    │      │ _store    │
  └────────┘          └──────────┘           │ _process     │      └───────────┘
       ↑                   ↑                  └──────────────┘           │
       │                   │                        │                    │
       └── credit flow ────┴── credit flow ─────────┴── credit flow ────┘
       
  If message_store is slow (disk I/O), backpressure propagates ALL the way
  back to the TCP reader, throttling the producer. This is the credit system.
```

### Virtual Hosts — Multi-Tenancy

```
┌─────────────────────────────────────────────────────────────┐
│  RabbitMQ Node                                               │
│  ┌─────────────────────────────┐                            │
│  │  vhost: /production         │                            │
│  │  ├── exchange: orders.topic │                            │
│  │  ├── queue: payment.process │                            │
│  │  └── user: prod_app (rw)   │                            │
│  └─────────────────────────────┘                            │
│  ┌─────────────────────────────┐                            │
│  │  vhost: /staging            │                            │
│  │  ├── exchange: orders.topic │  ← Same name, different   │
│  │  ├── queue: payment.process │    namespace. Fully        │
│  │  └── user: stage_app (rw)  │    isolated.               │
│  └─────────────────────────────┘                            │
│  ┌─────────────────────────────┐                            │
│  │  vhost: /team-analytics     │                            │
│  │  └── user: analytics (read) │                            │
│  └─────────────────────────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Exchange Types Deep Dive

### Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          EXCHANGE TYPE DECISION TREE                             │
│                                                                                 │
│                        "How should messages be routed?"                          │
│                                   │                                              │
│              ┌────────────────────┼────────────────────┐                        │
│              ▼                    ▼                     ▼                        │
│       Exact match?         Pattern match?        Broadcast?                     │
│              │                    │                     │                        │
│         ┌────┘               ┌───┘                ┌────┘                        │
│         ▼                    ▼                     ▼                             │
│     DIRECT              TOPIC                  FANOUT                           │
│  routing_key =       routing_key             Ignores key,                       │
│  binding_key         matches pattern         copies to ALL                      │
│                      (*, #)                  bound queues                        │
│                                                                                 │
│                           Also:  HEADERS (match on headers, not routing key)    │
│                                  CONSISTENT-HASH (partition across queues)      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Direct Exchange

```
  Producer publishes with routing_key = "payment.success"
  
  ┌──────────┐     routing_key = "payment.success"
  │  Direct   │─────────────────────────────────────→  Queue: payment-handlers  ✓
  │ Exchange  │     binding_key = "payment.success"
  │           │
  │           │     routing_key = "payment.success"
  │           │─────────────────────────────────────→  Queue: audit-log         ✓
  │           │     binding_key = "payment.success"
  │           │
  │           │─────────────────────────────── ✗ →     Queue: refund-handlers
  └──────────┘     binding_key = "payment.failed"      (no match, not routed)

  USE CASE: Task distribution, point-to-point, RPC reply queues
  ROUTING: O(1) — hash lookup on routing key
```

### Topic Exchange

```
  Producer publishes with routing_key = "order.created.us-east"
  
  ┌──────────┐
  │  Topic    │  Binding: "order.created.*"    → Queue: order-processors     ✓
  │ Exchange  │  Binding: "order.#"            → Queue: order-audit          ✓
  │           │  Binding: "order.cancelled.*"  → Queue: refund-service       ✗
  │           │  Binding: "#"                  → Queue: all-events           ✓
  └──────────┘
  
  WILDCARD RULES:
    *  = matches exactly ONE word          "order.*.us-east" matches "order.created.us-east"
    #  = matches ZERO OR MORE words        "order.#" matches "order" and "order.a.b.c"
    
  PERFORMANCE WARNING:
    Topic exchanges evaluate bindings LINEARLY. With 10,000 bindings,
    routing becomes CPU-bound. Use direct exchanges or consistent-hash
    when binding count is high.
    
  USE CASE: Event fan-out with filtering (audit, notifications, analytics)
```

### Fanout Exchange

```
  Producer publishes (routing_key is IGNORED)
  
  ┌──────────┐
  │  Fanout   │────→  Queue: email-service          ✓  (copy)
  │ Exchange  │────→  Queue: push-notification       ✓  (copy)
  │           │────→  Queue: analytics-pipeline      ✓  (copy)
  │           │────→  Queue: audit-log               ✓  (copy)
  └──────────┘
  
  Every bound queue gets a COPY. No filtering.
  
  ROUTING: O(n) where n = number of bindings, but no pattern matching overhead
  FASTEST exchange type for broadcast scenarios
  
  USE CASE: Pub/sub, event broadcasting, cache invalidation
```

### Headers Exchange

```
  Message headers: { "format": "pdf", "type": "report" }
  
  ┌──────────┐
  │ Headers   │  Binding: format=pdf, x-match=any    → Queue: pdf-processor    ✓
  │ Exchange  │  Binding: format=pdf, type=report,    
  │           │           x-match=all                 → Queue: report-archive   ✓
  │           │  Binding: format=csv, x-match=any     → Queue: csv-processor    ✗
  └──────────┘
  
  x-match = "any"  →  ANY header must match (OR)
  x-match = "all"  →  ALL headers must match (AND)
  
  USE CASE: Content-based routing when routing key is insufficient
  RARELY USED in practice — topic exchange covers most needs
```

### Exchange-to-Exchange Binding (Often Missed in Interviews)

```
  ┌───────────────┐        ┌────────────────┐        ┌──────────┐
  │ Primary        │──bind─→│ Regional        │──bind─→│ Queue:   │
  │ Exchange       │        │ Exchange        │        │ us-east  │
  │ (topic)        │        │ (direct)        │        │ workers  │
  │                │        └────────────────┘        └──────────┘
  │                │        ┌────────────────┐        ┌──────────┐
  │                │──bind─→│ Analytics       │──bind─→│ Queue:   │
  │                │        │ Exchange        │        │ events   │
  └───────────────┘        │ (fanout)        │        │ lake     │
                            └────────────────┘        └──────────┘
  
  Exchanges can bind to OTHER exchanges, creating routing DAGs.
  Powerful for multi-level routing topologies.
```

---

## 4. Queue Internals — How Messages Are Stored

### Queue Types in RabbitMQ 4.x

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        QUEUE TYPES — EVOLUTION                                   │
│                                                                                 │
│  CLASSIC QUEUES (v1.0+)                                                         │
│  ├── Single leader process, replicated via "mirrored queues" (REMOVED in 4.0)  │
│  ├── gen_server2 Erlang process per queue                                       │
│  └── Still available as non-replicated queues                                   │
│                                                                                 │
│  QUORUM QUEUES (v3.8+) ← RECOMMENDED DEFAULT                                   │
│  ├── Raft consensus for replication                                             │
│  ├── Replicated across N nodes (typically 3 or 5)                               │
│  ├── WAL (write-ahead log) for durability                                       │
│  └── Automatic leader election on failure                                       │
│                                                                                 │
│  STREAMS (v3.9+) ← FOR LOG/REPLAY USE CASES                                    │
│  ├── Append-only log (Kafka-like)                                               │
│  ├── Non-destructive reads (message not deleted on consume)                     │
│  ├── Offset tracking, consumer groups                                           │
│  └── Dedicated binary protocol (bypasses AMQP framing overhead)                │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Classic Queue Internal Message States

Each classic queue runs as a single Erlang `gen_server2` process. Messages flow through internal sub-queues based on memory pressure:

```
  publish ──→ [  q1  →  q2  →  delta  →  q3  →  q4  ] ──→ consumer
              
              ┌────────────────────────────────────────────────────────────┐
              │                                                            │
              │  ALPHA STATE (q1, q4):                                     │
              │    Message body + metadata FULLY IN RAM                    │
              │    Fastest access. Used when memory is available.          │
              │                                                            │
              │  BETA STATE (q2, q3):                                      │
              │    Metadata in RAM, body ON DISK                           │
              │    Requires one disk read to deliver.                      │
              │                                                            │
              │  DELTA STATE:                                               │
              │    EVERYTHING ON DISK (metadata + body)                    │
              │    Slowest. Triggered under memory pressure.               │
              │    Requires TWO disk reads (metadata + body).              │
              │                                                            │
              └────────────────────────────────────────────────────────────┘

  Memory pressure increases → messages demote: alpha → beta → delta
  Memory pressure decreases → messages promote: delta → beta → alpha
  
  CRITICAL INSIGHT: Deep queues (millions of messages) will push messages
  to delta state, causing MASSIVE disk I/O and throughput collapse.
  RabbitMQ is designed for FLOW, not STORAGE.
```

### Message Persistence vs Durability — The Full Picture

```
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  WHAT SURVIVES A BROKER RESTART?                                        │
  │                                                                         │
  │  You need ALL THREE:                                                    │
  │                                                                         │
  │  1. DURABLE EXCHANGE     →  exchange survives restart                   │
  │     exchange_declare(durable=true)                                      │
  │                                                                         │
  │  2. DURABLE QUEUE        →  queue definition survives restart           │
  │     queue_declare(durable=true)                                         │
  │                                                                         │
  │  3. PERSISTENT MESSAGE   →  message body written to disk                │
  │     delivery_mode = 2                                                   │
  │                                                                         │
  │  MISSING ANY ONE = MESSAGES LOST ON RESTART                             │
  │                                                                         │
  │  ⚠ Even with all three, messages can be lost between disk flushes.     │
  │    For TRUE safety, you also need PUBLISHER CONFIRMS.                   │
  │                                                                         │
  └──────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Quorum Queues & Raft Consensus

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         QUORUM QUEUE — RAFT REPLICATION                          │
│                                                                                 │
│   Node 1 (Leader)          Node 2 (Follower)        Node 3 (Follower)          │
│  ┌───────────────┐        ┌───────────────┐        ┌───────────────┐           │
│  │  QQ Process   │        │  QQ Process   │        │  QQ Process   │           │
│  │  ┌─────────┐  │        │  ┌─────────┐  │        │  ┌─────────┐  │           │
│  │  │  WAL    │  │◄──────►│  │  WAL    │  │◄──────►│  │  WAL    │  │           │
│  │  │ (Write  │  │  Raft  │  │ (Write  │  │  Raft  │  │ (Write  │  │           │
│  │  │ Ahead   │  │  Proto │  │ Ahead   │  │  Proto │  │ Ahead   │  │           │
│  │  │  Log)   │  │        │  │  Log)   │  │        │  │  Log)   │  │           │
│  │  └─────────┘  │        │  └─────────┘  │        │  └─────────┘  │           │
│  │       ↓       │        │       ↓       │        │       ↓       │           │
│  │  ┌─────────┐  │        │  ┌─────────┐  │        │  ┌─────────┐  │           │
│  │  │ Segment │  │        │  │ Segment │  │        │  │ Segment │  │           │
│  │  │  Files  │  │        │  │  Files  │  │        │  │  Files  │  │           │
│  │  └─────────┘  │        │  └─────────┘  │        │  └─────────┘  │           │
│  └───────────────┘        └───────────────┘        └───────────────┘           │
│                                                                                 │
│  WRITE PATH:                                                                    │
│  1. Producer sends message to LEADER                                            │
│  2. Leader writes to WAL                                                        │
│  3. Leader replicates to FOLLOWERS (in parallel)                                │
│  4. MAJORITY (2 of 3) confirms write → message committed                       │
│  5. Leader sends publisher confirm to producer                                  │
│                                                                                 │
│  READ PATH:                                                                     │
│  • Only the LEADER serves consumers                                             │
│  • Followers are for replication and leader election only                        │
│                                                                                 │
│  LEADER FAILURE:                                                                │
│  1. Followers detect leader heartbeat timeout                                   │
│  2. Most up-to-date follower starts election                                    │
│  3. Majority vote elects new leader                                             │
│  4. Clients reconnect to new leader (automatic with client libraries)           │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Quorum Queues vs Classic Mirrored Queues

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│  FEATURE                    CLASSIC MIRRORED (REMOVED)    QUORUM QUEUES           │
│  ─────────────────────────  ──────────────────────────    ───────────────────      │
│  Consensus protocol         Homegrown chain repl.         Raft consensus          │
│  Replication                Sequential chain              Parallel to followers   │
│  Data safety                Fails Jepsen tests            Passes Jepsen tests     │
│  Network partition          Unpredictable, msg loss        Minority stops, safe   │
│  Leader election            No formal election             Raft voting process    │
│  Disk persistence           Optional (RAM possible)        Always (WAL + disk)   │
│  Message ordering           Weaker guarantees              Strong ordering        │
│  Performance overhead       Lower per-message              Higher (Raft + WAL)   │
│  Memory usage               Lower                          ~35% lower in 4.1     │
│  Status in 4.0              REMOVED                        Default recommended   │
│  Poison message handling    No built-in                    delivery-limit header  │
│  Availability               Available in v1.0+             Available in v3.8+    │
└───────────────────────────────────────────────────────────────────────────────────┘
```

### Quorum Queue Write — Step by Step

```
  Producer                Leader (Node 1)         Follower (Node 2)      Follower (Node 3)
     │                         │                        │                       │
     │── publish(msg) ────────→│                        │                       │
     │                         │── append WAL ──────→   │                       │
     │                         │── replicate ──────────→│                       │
     │                         │── replicate ───────────────────────────────→   │
     │                         │                        │                       │
     │                         │◄── ack (WAL written) ──│                       │
     │                         │                        │    (still writing)    │
     │                         │                        │                       │
     │                         │  MAJORITY = 2/3 ✓      │                       │
     │                         │  Message COMMITTED      │                       │
     │                         │                        │                       │
     │◄── publisher confirm ───│                        │                       │
     │                         │                        │                       │
     │                         │◄── ack (WAL written) ──────────────────────────│
     │                         │                        │   (late, already      │
     │                         │                        │    committed)         │
```

---

## 6. Streams — Kafka-Like Replay in RabbitMQ

### Streams Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           RABBITMQ STREAMS (v3.9+)                               │
│                                                                                 │
│  APPEND-ONLY LOG (like Kafka partitions, but single partition per stream)        │
│                                                                                 │
│  Stream: "events"                                                                │
│  ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐                 │
│  │ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │ 8  │ 9  │ 10 │ 11 │  ← offsets   │
│  └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘                 │
│    ↑                    ↑                         ↑                              │
│    │                    │                         │                              │
│    Consumer A           Consumer B                Consumer C                     │
│    (offset: 0,          (offset: 3,               (offset: 9,                    │
│     replay all)          replay partial)            latest only)                  │
│                                                                                 │
│  KEY DIFFERENCES FROM QUEUES:                                                    │
│  • Messages NOT deleted on consumption (retained by time/size policy)            │
│  • Multiple consumers read independently at different offsets                    │
│  • Non-destructive reads                                                         │
│  • Dedicated binary protocol (NOT AMQP) for high performance                    │
│  • Replicated using same Raft mechanism as quorum queues                         │
│                                                                                 │
│  LIMITATIONS:                                                                    │
│  • Single stream = single partition (no built-in partitioning like Kafka)        │
│  • Super streams = manual sharding across multiple streams                       │
│  • No compaction (unlike Kafka log compaction)                                   │
│  • Newer, less battle-tested than Kafka streams                                  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Super Streams — Manual Partitioning

```
  Super Stream: "orders" (partitioned into 3 streams)
  
  ┌─────────────────────────────────────────────────────────────────┐
  │                                                                 │
  │  orders-0  [|||||||||||||||||]  ← routing_key hash % 3 == 0    │
  │  orders-1  [|||||||||||||||||]  ← routing_key hash % 3 == 1    │
  │  orders-2  [|||||||||||||||||]  ← routing_key hash % 3 == 2    │
  │                                                                 │
  │  Single Active Consumer per partition (like Kafka consumer      │
  │  group), or multiple consumers reading independently.           │
  │                                                                 │
  └─────────────────────────────────────────────────────────────────┘
```

---

## 7. Producers Deep Dive — Confirms & Transactions

### Publisher Confirms — The Reliable Publish Path

```
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │  THREE MODES OF PUBLISHING RELIABILITY                                      │
  │                                                                             │
  │  1. FIRE-AND-FORGET (default)                                               │
  │     Producer ──publish──→ Broker                                            │
  │     • No guarantee of receipt                                               │
  │     • Fastest, but messages can be SILENTLY LOST                            │
  │     • Network blip, broker crash = message gone                             │
  │                                                                             │
  │  2. PUBLISHER CONFIRMS (recommended)                                        │
  │     Producer ──publish──→ Broker ──confirm/nack──→ Producer                 │
  │     • Broker sends basic.ack when message is:                               │
  │       - Written to disk (persistent msg), OR                                │
  │       - Accepted by quorum (quorum queue), OR                               │
  │       - Routed to at least one queue (non-mandatory)                        │
  │     • Broker sends basic.nack on internal error                             │
  │     • Can be synchronous (wait per msg) or async (batch + callback)         │
  │                                                                             │
  │  3. TRANSACTIONS (tx.select, tx.commit)                                     │
  │     Producer ──tx.select──→ ──publish──→ ──tx.commit──→ Broker              │
  │     • AMQP transactions wrap multiple publishes atomically                  │
  │     • EXTREMELY SLOW (~250x slower than confirms)                           │
  │     • Almost never used in production. Prefer confirms.                     │
  │                                                                             │
  └──────────────────────────────────────────────────────────────────────────────┘
```

### Publisher Confirm Flow

```
  Producer                              Broker (Queue)
     │                                       │
     │── confirm.select ───────────────────→ │  (enable confirm mode on channel)
     │◄── confirm.select-ok ────────────────│
     │                                       │
     │── publish(msg1, delivery_tag=1) ────→ │  
     │── publish(msg2, delivery_tag=2) ────→ │  (pipeline publishes)
     │── publish(msg3, delivery_tag=3) ────→ │  
     │                                       │
     │                                       │── write to disk / replicate
     │                                       │
     │◄── basic.ack(delivery_tag=3,          │  (BATCH ACK: confirms 1,2,3)
     │         multiple=true) ──────────────│
     │                                       │
     │  If broker can't persist:             │
     │◄── basic.nack(delivery_tag=4) ───────│  (producer must re-publish)
     │                                       │

  MANDATORY FLAG:
  If mandatory=true and message can't be routed to ANY queue,
  broker returns basic.return to producer (instead of silently dropping).
```

---

## 8. Consumers Deep Dive — Prefetch, Ack, QoS

### Consumer Acknowledgment Modes

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         ACKNOWLEDGMENT MODES                                     │
│                                                                                 │
│  1. MANUAL ACK (basic.consume with auto_ack=false)  ← RECOMMENDED              │
│     ┌──────────┐        ┌────────┐        ┌──────────┐                          │
│     │  Broker   │──msg──→│Consumer│──ack──→│  Broker   │  (message deleted)      │
│     │          │        │        │        │          │                           │
│     │          │        │ crash! │──nack─→│          │  (message requeued)       │
│     └──────────┘        └────────┘        └──────────┘                          │
│                                                                                 │
│     • basic.ack    → success, delete message                                    │
│     • basic.nack   → failure, requeue=true (retry) or false (DLX/discard)      │
│     • basic.reject → same as nack but single message only                       │
│                                                                                 │
│  2. AUTO ACK (basic.consume with auto_ack=true)                                 │
│     ┌──────────┐        ┌────────┐                                              │
│     │  Broker   │──msg──→│Consumer│  (message deleted IMMEDIATELY on send)       │
│     └──────────┘        └────────┘                                              │
│                                                                                 │
│     • Message deleted the moment broker SENDS it (fire-and-forget)              │
│     • Consumer crash = MESSAGE LOST                                             │
│     • Only use for non-critical, loss-tolerant workloads                        │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Prefetch Count — QoS

```
  WITHOUT PREFETCH (prefetch_count = 0 = unlimited):
  
  ┌──────────┐   msg1,msg2,msg3,...msg1000
  │  Broker   │ ════════════════════════════════→  Consumer
  └──────────┘   (dumps ALL unacked messages)     (overwhelmed, OOM)
  
  Broker's unacked buffer grows unbounded.
  Consumer RAM explodes. Processing stalls.
  
  ─────────────────────────────────────────────────────────────────
  
  WITH PREFETCH (prefetch_count = 10):
  
  ┌──────────┐   msg1..msg10                       Consumer processes
  │  Broker   │ ═══════════════→  Consumer ──ack──→ 1 msg, gets 1 more
  └──────────┘                                      
  
  At most 10 unacked messages in flight to this consumer.
  Broker waits for ack before sending more.
  
  TUNING GUIDE:
  ┌───────────────────────────────────────────────────────────────┐
  │  prefetch = 1    → Fair dispatch, high latency (round-trip)  │
  │  prefetch = 10   → Good balance for most workloads           │
  │  prefetch = 50   → High throughput, risk of uneven load      │
  │  prefetch = 250+ → Maximum throughput, consumer must be fast │
  │  prefetch = 0    → UNLIMITED — almost always wrong           │
  └───────────────────────────────────────────────────────────────┘
```

### Competing Consumers Pattern

```
  Queue: "tasks"  (1000 messages waiting)
  
  ┌──────────┐     prefetch=10
  │          │ ═══════════════→  Consumer 1 (fast)    → processes 600 msgs
  │  Queue:  │     prefetch=10
  │  tasks   │ ═══════════════→  Consumer 2 (medium)  → processes 300 msgs
  │          │     prefetch=10
  │          │ ═══════════════→  Consumer 3 (slow)    → processes 100 msgs
  └──────────┘
  
  RabbitMQ round-robins delivery across consumers.
  With prefetch, slow consumers don't starve fast ones.
  This is WORK QUEUE pattern — RabbitMQ's bread and butter.
  
  CONTRAST WITH KAFKA:
  Kafka partitions are statically assigned to consumers.
  RabbitMQ dynamically dispatches to available consumers.
  RabbitMQ wins for heterogeneous consumer speeds.
```

### Single Active Consumer

```
  Queue: "orders" (with x-single-active-consumer = true)
  
  ┌──────────┐
  │  Queue:  │ ═══════════════→  Consumer 1 (ACTIVE)     → gets ALL messages
  │  orders  │                   Consumer 2 (STANDBY)     → gets NOTHING
  │          │                   Consumer 3 (STANDBY)     → gets NOTHING
  └──────────┘
  
  If Consumer 1 dies:
  
  ┌──────────┐
  │  Queue:  │ ═══════════════→  Consumer 2 (now ACTIVE)  → gets ALL messages
  │  orders  │                   Consumer 3 (STANDBY)     → gets NOTHING
  └──────────┘
  
  USE CASE: Strict ordering — only one consumer processes at a time.
  Failover is automatic. No split-brain between consumers.
```

---

## 9. Flow Control & Backpressure — The Credit System

### How the Credit System Works

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                      CREDIT FLOW — INTERNAL BACKPRESSURE                         │
│                                                                                 │
│  Each process in the message path grants "credits" to its upstream sender:      │
│                                                                                 │
│    reader ──(200 credits)──→ channel ──(200 credits)──→ queue ──→ disk          │
│                                                                                 │
│  FLOW:                                                                          │
│  1. Reader starts with 200 credits from channel                                 │
│  2. Each message sent = 1 credit consumed                                       │
│  3. After 200 messages, reader is BLOCKED (no more credits)                     │
│  4. Channel processes messages, after ~50 processed → grants 50 more credits    │
│  5. Reader unblocks, sends more messages                                        │
│                                                                                 │
│  CASCADING BACKPRESSURE:                                                        │
│                                                                                 │
│                       disk slow?                                                 │
│                           │                                                      │
│                    queue blocks                                                  │
│                           │                                                      │
│                channel runs out of credits                                        │
│                           │                                                      │
│                reader runs out of credits                                         │
│                           │                                                      │
│              TCP SOCKET STOPS READING                                             │
│                           │                                                      │
│          PRODUCER GETS TCP BACKPRESSURE (can't send)                             │
│                                                                                 │
│  In management UI: connection shows "flow" state                                 │
│  This is NORMAL and HEALTHY — it prevents broker OOM                             │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Memory Alarms and Disk Alarms

```
  ┌───────────────────────────────────────────────────────────────────┐
  │  MEMORY WATERMARK (default: 0.4 = 40% of system RAM)             │
  │                                                                   │
  │  0%          40%                                        100%      │
  │  ├───────────┤══════════════════════════════════════════┤         │
  │  │  Normal    │  ALARM: All publishers BLOCKED globally  │         │
  │  │  operation │  Consumers still work (drain the queue)  │         │
  │  └───────────┴──────────────────────────────────────────┘         │
  │                                                                   │
  │  DISK WATERMARK (default: 50MB free)                              │
  │                                                                   │
  │  If free disk < 50MB → ALL publishing blocked                     │
  │  Broker enters "disk alarm" state                                 │
  │                                                                   │
  │  PAGING: Before alarm, broker PAGES messages from RAM to disk     │
  │  to try to free memory. This is expensive and slows throughput.   │
  │                                                                   │
  └───────────────────────────────────────────────────────────────────┘
```

---

## 10. Dead Letter Exchanges & Retry Patterns

### When Messages Get Dead-Lettered

```
  A message is sent to the Dead Letter Exchange (DLX) when:
  
  1. Consumer REJECTS with requeue=false       basic.nack(requeue=false)
  2. Message TTL expires                        x-message-ttl on queue or per-msg
  3. Queue LENGTH LIMIT exceeded                x-max-length or x-max-length-bytes
  4. Quorum queue DELIVERY LIMIT exceeded       x-delivery-limit header
```

### Retry with Exponential Backoff Using DLX + TTL

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   RETRY PATTERN — DLX + TTL CHAINING                             │
│                                                                                 │
│  ┌──────────┐   fail    ┌────────────────┐  TTL=5s   ┌──────────┐              │
│  │  Main    │ ────nack──→│  Retry Queue   │ ──expire──→│  Main    │  (attempt 2) │
│  │  Queue   │           │  (wait 5s)     │           │  Queue   │              │
│  └──────────┘           └────────────────┘           └──────────┘              │
│       │                                                    │                    │
│       │ fail again                                         │ fail again         │
│       ▼                                                    ▼                    │
│  ┌────────────────┐  TTL=30s  ┌──────────┐         ┌────────────────┐          │
│  │  Retry Queue 2 │ ──expire──→│  Main    │         │  Retry Queue 3 │          │
│  │  (wait 30s)    │           │  Queue   │         │  (wait 120s)   │          │
│  └────────────────┘           └──────────┘         └────────────────┘          │
│                                                          │                     │
│                                                          │ fail (attempt > 3)  │
│                                                          ▼                     │
│                                                    ┌──────────────┐            │
│                                                    │  PARKING LOT  │            │
│                                                    │  (dead msgs)  │            │
│                                                    │  Manual review │            │
│                                                    └──────────────┘            │
│                                                                                 │
│  TRACKING RETRIES: Use x-death header (auto-populated by RabbitMQ)             │
│  x-death[0].count = number of times dead-lettered                               │
│  Check count in consumer → if count > MAX_RETRIES → route to parking lot        │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Poison Message Handling

```
  ┌───────────────────────────────────────────────────────────────────────────┐
  │  POISON MESSAGE: A message that ALWAYS causes consumer failure            │
  │                                                                           │
  │  WITHOUT PROTECTION:                                                      │
  │                                                                           │
  │  Queue ──msg──→ Consumer ──crash──→ msg requeued ──→ Consumer ──crash──→  │
  │                     ↑                                    │                 │
  │                     └────────────── INFINITE LOOP ───────┘                │
  │                                                                           │
  │  Result: Consumer stuck, queue blocked, all other messages delayed.       │
  │                                                                           │
  │  WITH PROTECTION (Quorum Queues):                                         │
  │                                                                           │
  │  Set x-delivery-limit = 3 on queue declaration                            │
  │  After 3 delivery attempts → message auto-dead-lettered                   │
  │                                                                           │
  │  WITH PROTECTION (Classic Queues):                                        │
  │  Track attempts in consumer via x-death header or custom header           │
  │  After N attempts → publish to DLX manually, ack original                 │
  │                                                                           │
  └───────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Clustering & Network Partitions

### RabbitMQ Cluster Topology

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                       RABBITMQ CLUSTER (3 NODES)                                 │
│                                                                                 │
│  ┌────────────────┐    Erlang       ┌────────────────┐    Erlang                │
│  │  rabbit@node1  │◄──distribution──►│  rabbit@node2  │◄──distribution──►       │
│  │                │    protocol      │                │    protocol       │      │
│  │  • Queue A     │   (port 25672)   │  • Queue B     │   (port 25672)   │      │
│  │    (leader)    │                  │    (leader)    │                  │      │
│  │  • Queue B     │                  │  • Queue A     │  ┌────────────────┐     │
│  │    (follower)  │                  │    (follower)  │  │  rabbit@node3  │     │
│  │  • Queue C     │                  │  • Queue C     │  │                │     │
│  │    (follower)  │                  │    (follower)  │  │  • Queue C     │     │
│  └────────────────┘                  └────────────────┘  │    (leader)    │     │
│                                                          │  • Queue A     │     │
│                                                          │    (follower)  │     │
│                                                          │  • Queue B     │     │
│                                                          │    (follower)  │     │
│                                                          └────────────────┘     │
│                                                                                 │
│  WHAT IS SHARED ACROSS ALL NODES:                                               │
│  • Exchange definitions (metadata in Mnesia DB, replicated)                     │
│  • Queue definitions (metadata)                                                  │
│  • Binding definitions                                                           │
│  • Users, vhosts, permissions                                                    │
│                                                                                 │
│  WHAT IS NOT SHARED (for classic queues):                                        │
│  • Queue MESSAGE DATA lives on the node where queue was declared                │
│  • Other nodes proxy requests to the owner node                                  │
│                                                                                 │
│  FOR QUORUM QUEUES: message data IS replicated (Raft)                            │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Network Partition — Split Brain

```
  BEFORE PARTITION:
  
  [Node 1] ◄──────► [Node 2] ◄──────► [Node 3]
                    (healthy cluster)
  
  ─────────────────────────────────────────────────────
  
  NETWORK PARTITION OCCURS:
  
  [Node 1] ◄──────► [Node 2]     ✗     [Node 3]
         (Partition A)            │    (Partition B)
                               network
                               failure
  
  SPLIT BRAIN:
  • Node 1 & 2 think Node 3 is dead
  • Node 3 thinks Node 1 & 2 are dead
  • Both sides continue accepting messages independently
  • Queues, exchanges, bindings diverge
  • When network heals → CONFLICTING STATE
```

### Partition Handling Strategies

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    PARTITION HANDLING STRATEGIES                                  │
│                                                                                 │
│  1. IGNORE (default)                                                             │
│  ┌─────────────────────────────────────────────────────────────────┐            │
│  │  Both sides keep running. Maximum availability.                  │            │
│  │  RISK: Data diverges. Manual intervention needed to recover.    │            │
│  │  USE: Never in production. Only for dev/test.                   │            │
│  └─────────────────────────────────────────────────────────────────┘            │
│                                                                                 │
│  2. PAUSE-MINORITY                                                               │
│  ┌─────────────────────────────────────────────────────────────────┐            │
│  │  Nodes in the MINORITY partition automatically pause.           │            │
│  │  They stop accepting connections and drain.                     │            │
│  │  When network heals → they rejoin automatically.                │            │
│  │                                                                  │            │
│  │  [Node 1] ◄──► [Node 2]     |     [Node 3] ← PAUSED            │            │
│  │    (majority=2/3, keeps      |     (minority=1/3, stops)         │            │
│  │     running)                 |                                   │            │
│  │                                                                  │            │
│  │  BEST FOR: Data consistency. Requires odd node count.           │            │
│  │  RISK: Minority side becomes unavailable.                        │            │
│  └─────────────────────────────────────────────────────────────────┘            │
│                                                                                 │
│  3. AUTOHEAL                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐            │
│  │  When partition heals, broker automatically picks a "winner."   │            │
│  │  The LOSING side restarts, syncing from the winner.             │            │
│  │  Automatic recovery, but brief unavailability during restart.   │            │
│  │  RISK: Messages on the losing side are LOST.                    │            │
│  │  BEST FOR: Availability-first systems where msg loss is ok.     │            │
│  └─────────────────────────────────────────────────────────────────┘            │
│                                                                                 │
│  RECOMMENDATION FOR PRODUCTION:                                                  │
│  • Quorum queues + pause-minority                                                │
│  • Odd number of nodes (3 or 5)                                                  │
│  • Quorum queues handle partitions at the queue level via Raft                   │
│    (minority replicas stop, majority continues)                                  │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Node Detection Timing

```
  Node A                               Node B
    │                                     │
    │── heartbeat ──────────────────────→ │
    │◄── heartbeat ──────────────────────│
    │                                     │
    │     ... network failure ...         │
    │                                     │
    │── heartbeat ─── ✗ (lost)            │
    │                    60 seconds        │  (net_ticktime default)
    │                    tick timeout      │
    │                                     │
    │  "Node B is DOWN"                   │  "Node A is DOWN"
    │                                     │
    
  60 seconds is a LONG time. During this window:
  • Quorum queue followers on the disconnected side can't vote
  • Classic queues continue operating independently (split brain risk)
  • Clients may experience connection failures
  
  Tuning net_ticktime lower (e.g., 15s) reduces detection time
  but increases risk of false positives on loaded systems.
```

---

## 12. Federation & Shovel — Multi-DC Replication

### When Clustering Doesn't Work

```
  CLUSTERING REQUIRES:
  • Low-latency network (same datacenter / same AZ)
  • Erlang distribution protocol (chatty, not WAN-friendly)
  • Shared Erlang cookie (security boundary)
  
  FOR CROSS-DC / CROSS-REGION / WAN:
  Use FEDERATION or SHOVEL instead.
  
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  FEATURE                SHOVEL                     FEDERATION            │
  │  ───────────────────    ─────────────────────      ───────────────────   │
  │  Direction              Unidirectional              Bidirectional        │
  │  Topology               Point-to-point              Mesh / star          │
  │  Message flow           Always-on pump              Demand-based (lazy)  │
  │  Loop prevention        Manual                      Built-in             │
  │  Setup complexity       Low                         Medium               │
  │  Latency tolerance      Excellent (WAN)             Good (WAN)           │
  │  Use case               DR replication,             Multi-DC pub/sub,    │
  │                         migration                   global distribution  │
  │  Protocol               AMQP client                 Plugin protocol      │
  │  Message transform      Supported                   Not supported        │
  └──────────────────────────────────────────────────────────────────────────┘
```

### Federation Architecture

```
  DC: US-EAST                              DC: EU-WEST
  ┌──────────────────┐                    ┌──────────────────┐
  │  RabbitMQ Cluster │                    │  RabbitMQ Cluster │
  │                   │    Federation      │                   │
  │  exchange:        │    Link (AMQP)     │  exchange:        │
  │  "events"         │◄═════════════════►│  "events"         │
  │  (upstream)       │    over WAN        │  (downstream)     │
  │                   │                    │                   │
  │  Queue: us-east   │                    │  Queue: eu-west   │
  │  consumers        │                    │  consumers        │
  └──────────────────┘                    └──────────────────┘
  
  When a consumer in EU-WEST subscribes to "events" exchange:
  1. Federation link pulls messages from US-EAST
  2. Messages are re-published to EU-WEST exchange
  3. EU-WEST consumers get local-speed delivery
  
  DEMAND-BASED: Messages only flow when there are consumers on downstream.
  No consumers = no replication = no wasted bandwidth.
```

### Shovel Architecture

```
  SOURCE BROKER                           DESTINATION BROKER
  ┌──────────────────┐                    ┌──────────────────┐
  │                   │                    │                   │
  │  Queue:           │     Shovel         │  Queue:           │
  │  "orders.pending" │ ══════════════════→│  "orders.process" │
  │                   │   (AMQP client     │                   │
  │                   │    consuming from   │                   │
  │                   │    source, publish  │                   │
  │                   │    to destination)  │                   │
  └──────────────────┘                    └──────────────────┘
  
  ACK MODES:
  • on-confirm:  Ack source AFTER destination confirms (safest, slowest)
  • on-publish:  Ack source AFTER publishing to destination (faster)
  • no-ack:      Auto-ack on source (fastest, risk of loss)
```

---

## 13. Real-World Usage at Scale

### Companies Using RabbitMQ

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                     RABBITMQ IN PRODUCTION — REAL WORLD                           │
│                                                                                 │
│  COMPANY              USE CASE                        SCALE                      │
│  ─────────────────    ──────────────────────────      ────────────────────       │
│  lastminute.com       Search result aggregation       50M requests/day          │
│                       via RPC pattern + RabbitMQ       600 req/s peak            │
│                                                                                 │
│  Softonic             Event bus between microservices  100M+ monthly users      │
│                       File upload → scan → process     2M downloads/day         │
│                                                                                 │
│  Zepto                Order processing pipeline        High-throughput e-comm   │
│                       Inventory, payment, delivery                               │
│                                                                                 │
│  Meesho               Async task processing            Indian e-commerce scale  │
│                       Notification dispatch                                      │
│                                                                                 │
│  Mozilla              Pulse — event notification       Millions of events/day   │
│                       CI/CD build events                                         │
│                                                                                 │
│  Instagram (early)    Task queue for async processing  Before switching to      │
│                       Celery + RabbitMQ                 custom solution          │
│                                                                                 │
│  Reddit (early)       Async job processing             Before switching to      │
│                       Celery + RabbitMQ                 Kafka at scale           │
│                                                                                 │
│  CloudAMQP (hosting)  Managed RabbitMQ as a service    100K+ instances          │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Architecture Patterns in Production

```
  PATTERN 1: WORK QUEUE (Task Distribution)
  
  ┌─────────┐
  │ Web API  │──publish──→ Queue: "email.send" ──→ Worker 1 (sends email)
  │ Server   │                                 ──→ Worker 2 (sends email)
  └─────────┘                                 ──→ Worker 3 (sends email)
  
  API returns 202 Accepted immediately. Email sent async.
  Workers auto-scale based on queue depth.

  ──────────────────────────────────────────────────────────────────────

  PATTERN 2: PUB/SUB (Event Broadcasting)
  
  ┌──────────┐                    ┌───────────────┐
  │  Order    │──publish──→       │ Fanout        │──→ Queue: inventory-service
  │  Service  │  "order.created"  │ Exchange      │──→ Queue: email-service
  └──────────┘                    │ "order.events"│──→ Queue: analytics-service
                                   └───────────────┘──→ Queue: audit-log

  ──────────────────────────────────────────────────────────────────────

  PATTERN 3: RPC (Request-Reply)
  
  Client                          Server
  ┌──────────┐                    ┌──────────┐
  │          │──request(          │          │
  │          │   reply_to=        │          │
  │          │   "amq.gen-xxx",   │          │
  │          │   correlation_id=  │          │
  │          │   "abc-123")──────→│          │
  │          │                    │          │──process──→ result
  │          │◄──response(        │          │
  │          │   to "amq.gen-xxx",│          │
  │          │   correlation_id=  │          │
  │          │   "abc-123")───────│          │
  └──────────┘                    └──────────┘
  
  Used by lastminute.com for search aggregation.
  Anti-pattern if latency-sensitive — prefer HTTP/gRPC for sync RPC.

  ──────────────────────────────────────────────────────────────────────

  PATTERN 4: PRIORITY QUEUE
  
  Queue: "tasks" (x-max-priority = 10)
  
  ┌──────────────────────────────────────────┐
  │  priority=10  │  VIP order processing     │  ← consumed first
  │  priority=5   │  Normal order processing  │
  │  priority=1   │  Batch analytics          │  ← consumed last
  └──────────────────────────────────────────┘
  
  RabbitMQ supports up to 255 priority levels.
  Keep it ≤ 10 levels — each level = separate internal sub-queue.
```

---

## 14. When RabbitMQ Failed — Production Incidents

### Incident 1: Infinite Redelivery Loop (Redocly, Jan 2026)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Background cleanup job enters infinite redelivery loop                │
│  IMPACT: 3.5 hours of platform-wide outage                                      │
│  ROOT CAUSE: Missing error handling + no DLX configured                          │
│                                                                                 │
│  TIMELINE:                                                                       │
│                                                                                 │
│  Queue: "cleanup.jobs"                                                           │
│       │                                                                         │
│       ▼                                                                         │
│  Consumer: cleanup-worker                                                        │
│       │                                                                         │
│       ▼                                                                         │
│  ERROR: Unhandled exception (e.g., invalid data)                                │
│       │                                                                         │
│       ▼                                                                         │
│  Message NACK'd with requeue=true (default behavior)                            │
│       │                                                                         │
│       ▼                                                                         │
│  Message IMMEDIATELY redelivered to same consumer                               │
│       │                                                                         │
│       ▼                                                                         │
│  Retry every 20 SECONDS without backoff                                         │
│       │                                                                         │
│       ▼                                                                         │
│  Database HAMMERED with failing queries → connection pool exhausted             │
│       │                                                                         │
│       ▼                                                                         │
│  API servers OOM from backed-up requests → crash                                │
│       │                                                                         │
│       ▼                                                                         │
│  Secret engine exhausted during restart attempts → cascading failure            │
│                                                                                 │
│  LESSONS:                                                                        │
│  1. ALWAYS configure DLX on every queue                                          │
│  2. ALWAYS set x-delivery-limit on quorum queues                                │
│  3. NEVER nack with requeue=true without retry limit                            │
│  4. Implement exponential backoff (DLX + TTL pattern)                           │
│  5. Monitor DLQ depth with alerts                                                │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Incident 2: Acknowledgment Bug (NestJS + RabbitMQ)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Messages silently lost in NestJS microservice                         │
│  IMPACT: 2 days of debugging, data inconsistency                                │
│  ROOT CAUSE: auto_ack=true by default in framework                              │
│                                                                                 │
│  What happened:                                                                  │
│  ┌──────────┐       ┌──────────────┐       ┌──────────┐                         │
│  │  Queue    │──msg──│  NestJS       │──crash│  Message  │                        │
│  │          │       │  Consumer     │       │  LOST     │                        │
│  └──────────┘       │  (auto_ack)  │       │  (already │                        │
│                      └──────────────┘       │  deleted) │                        │
│                                              └──────────┘                        │
│                                                                                 │
│  Framework auto-acked BEFORE processing completed.                              │
│  Consumer crashed mid-processing → message already deleted from queue.          │
│                                                                                 │
│  LESSONS:                                                                        │
│  1. ALWAYS use manual acknowledgment for critical workloads                     │
│  2. Ack AFTER processing completes, not before                                  │
│  3. Understand your framework's default ack behavior                            │
│  4. Test with consumer crashes during processing                                │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Incident 3: Network Partition — Split Brain in Production

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Network partition in 3-node cluster with partition_handling=ignore     │
│  IMPACT: Duplicate messages, inconsistent queue state, hours of manual recovery  │
│                                                                                 │
│  Before:                                                                         │
│  [Node1] ◄──► [Node2] ◄──► [Node3]   (healthy)                                │
│                                                                                 │
│  Network blip (router failure, ~3 minutes):                                     │
│  [Node1] ◄──► [Node2]  ✗  [Node3]                                              │
│                                                                                 │
│  With partition_handling = ignore:                                                │
│  • Both sides accepted publishes to classic queues independently                │
│  • Queue "orders" had DIFFERENT messages on each side                           │
│  • Bindings were modified on one side, not the other                            │
│  • When network healed: Mnesia detected inconsistency                           │
│  • Manual restart required: stop all nodes, start one by one                    │
│  • SOME MESSAGES WERE LOST in the reconciliation                                │
│                                                                                 │
│  LESSONS:                                                                        │
│  1. NEVER use ignore in production                                               │
│  2. Use pause-minority for consistency-first workloads                           │
│  3. Use quorum queues — they handle partitions via Raft majority                │
│  4. Monitor partition status via /api/nodes endpoint                             │
│  5. Test partition scenarios BEFORE production (Chaos Engineering)               │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Incident 4: Queue Depth Explosion — Throughput Collapse

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Classic queue grows to 50M messages, throughput drops to near-zero    │
│  IMPACT: Consumer processing grinds to halt despite healthy consumers           │
│                                                                                 │
│  WHAT HAPPENED:                                                                  │
│                                                                                 │
│  Producer rate: 10,000 msg/s                                                     │
│  Consumer rate:  8,000 msg/s  (20% slower than producer)                        │
│  Queue grows at:  2,000 msg/s                                                    │
│                                                                                 │
│  Day 1:  172M messages → queue enters DELTA state (all on disk)                 │
│  Day 2:  Broker paging aggressively → disk I/O saturated                        │
│  Day 3:  Memory alarm triggered → ALL publishers blocked globally               │
│  Day 3:  Consumer throughput drops from 8K to 200 msg/s (disk reads)            │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐            │
│  │  Queue Depth vs Throughput (Classic Queue)                      │            │
│  │                                                                 │            │
│  │  Throughput                                                     │            │
│  │  ▲                                                              │            │
│  │  │  ████████                                                    │            │
│  │  │          ████                                                │            │
│  │  │              ███                                             │            │
│  │  │                 ██                                           │            │
│  │  │                   ██                                         │            │
│  │  │                     ████████████████  ← throughput collapse  │            │
│  │  └────────────────────────────────────────→ Queue Depth         │            │
│  │       0     1M    5M    10M    50M                              │            │
│  └─────────────────────────────────────────────────────────────────┘            │
│                                                                                 │
│  LESSONS:                                                                        │
│  1. Set x-max-length or x-max-length-bytes on queues                            │
│  2. Use DLX for overflow (drop-head or reject-publish)                          │
│  3. Auto-scale consumers based on queue depth metrics                           │
│  4. Alert on queue depth thresholds (e.g., >100K messages)                      │
│  5. RabbitMQ is for FLOW, not STORAGE. Use Kafka/S3 for retention.             │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 15. When NOT to Use RabbitMQ

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  SCENARIO                           WHY NOT RABBITMQ         USE INSTEAD         │
│  ─────────────────────────────      ──────────────────       ───────────────     │
│                                                                                 │
│  High-throughput event streaming    ~40-100K msg/s ceiling   Kafka, Redpanda    │
│  (>500K msg/s)                      vs Kafka's 1M+                              │
│                                                                                 │
│  Event replay / reprocessing        Messages deleted on ack  Kafka              │
│  (audit, rebuild state)             (Streams exist but less  (append-only log)  │
│                                      mature)                                     │
│                                                                                 │
│  Long-term event storage            Not a storage system.    Kafka + S3,        │
│  (days/weeks retention)             Queue depth kills perf.  Event Store        │
│                                                                                 │
│  Big data pipelines                 No ecosystem for batch   Kafka + Spark,     │
│  (ETL, analytics)                   processing               Flink, Beam        │
│                                                                                 │
│  Simple cloud task queue            Over-engineered.         SQS, Cloud Tasks   │
│  (no complex routing needed)        Operational overhead.                        │
│                                                                                 │
│  Sub-millisecond latency            Network + Erlang GC      ZeroMQ, shared     │
│  (HFT, game servers)               add overhead              memory, LMAX       │
│                                                                                 │
│  Global-scale pub/sub               Not built for global     Google Pub/Sub,    │
│  (cross-continent, millions         distribution              Kafka + MirrorMaker│
│   of subscribers)                                                                │
│                                                                                 │
│  Log aggregation                    Queue per source is       ELK, Loki,        │
│                                     unsustainable              Fluentd → Kafka  │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Anti-Patterns That Kill RabbitMQ

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  ANTI-PATTERN                    WHY IT KILLS                 FIX                │
│  ──────────────────────────      ─────────────────────       ────────────────    │
│                                                                                 │
│  1. Connection-per-publish       TCP handshake per msg.      Connection pool    │
│                                  Thousands of connections.   + channels.        │
│                                  Erlang process overhead.    1 conn, N channels │
│                                                                                 │
│  2. No prefetch (unlimited)      Broker dumps ALL msgs to   Set prefetch       │
│                                  consumer. Consumer OOM.     = 10-50            │
│                                                                                 │
│  3. Using queue as storage       Deep queues → delta state   Set max-length,   │
│     (millions of messages)       → disk I/O → throughput     use DLX overflow,  │
│                                  collapse.                   use Kafka instead  │
│                                                                                 │
│  4. Auto-ack on critical         Consumer crash = msg lost.  Manual ack always  │
│     queues                       Silent data loss.           for critical data  │
│                                                                                 │
│  5. No DLX configured            Poison msg → infinite       DLX + delivery     │
│                                  redelivery loop.            limit on every Q   │
│                                                                                 │
│  6. Nack with requeue=true       Immediate redelivery loop.  Use DLX+TTL for   │
│     without retry limit          CPU spinning on bad msg.    delayed retry      │
│                                                                                 │
│  7. WAN clustering               Erlang distribution is      Use Federation    │
│                                  chatty + latency-sensitive. or Shovel for WAN  │
│                                  Heartbeat timeouts.                             │
│                                                                                 │
│  8. Too many exchanges/bindings  Topic exchange: O(n) eval.  Consolidate       │
│     (>10K bindings)              CPU-bound on publish path.  routing topology   │
│                                                                                 │
│  9. Large messages (>128KB)      Memory pressure per msg.    Store payload in   │
│                                  Replication overhead.       S3/blob store,     │
│                                  GC pressure in Erlang.      send reference     │
│                                                                                 │
│  10. Ignoring memory/disk        Broker enters alarm state.  Monitor vm_memory  │
│      alarms                      ALL publishers blocked.     _high_watermark,   │
│                                  Silent production freeze.   disk_free_limit    │
│                                                                                 │
│  11. Not setting message TTL     Unconsumed messages pile    Set per-queue or   │
│                                  up forever. Queue grows     per-message TTL    │
│                                  unbounded.                                      │
│                                                                                 │
│  12. Exclusive queues in         Exclusive queue = deleted   Use non-exclusive  │
│      production services         when connection drops.      durable queues     │
│                                  Service restart = queue     with proper naming │
│                                  gone.                                           │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 17. RabbitMQ vs Kafka — When to Pick Which

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│  DIMENSION                 RABBITMQ                    KAFKA                      │
│  ────────────────────      ───────────────────         ───────────────────        │
│  Model                     Smart broker/dumb consumer  Dumb broker/smart consumer │
│  Protocol                  AMQP (0-9-1, 1.0)          Custom binary protocol     │
│  Message deletion          On acknowledgment           Retained (time/size)       │
│  Routing                   Exchanges + bindings        Topic + partitions         │
│  Consumer model            Push (broker → consumer)    Pull (consumer → broker)   │
│  Ordering                  Per-queue                   Per-partition               │
│  Replay                    Streams only (newer)        Native (offset-based)      │
│  Throughput                40K-100K msg/s              1M+ msg/s                  │
│  Latency                   Sub-ms possible             2-50ms typical             │
│  Priority queues           Native support              Not supported              │
│  Complex routing           4+ exchange types           Partition routing only     │
│  Consumer flexibility      Dynamic dispatch            Static partition assign    │
│  Delivery guarantee        At-most-once / at-least     At-least / exactly-once   │
│  Operational complexity    Medium                      Higher (ZK/KRaft + brokers)│
│  Language                  Erlang/OTP                  Java/Scala                 │
│  Ecosystem                 Messaging focused            Big data (Streams,Connect)│
│                                                                                   │
│  ─────────────────────────────────────────────────────────────────────────────    │
│                                                                                   │
│  PICK RABBITMQ WHEN:                    PICK KAFKA WHEN:                          │
│  • Complex routing needed               • High-throughput event streaming         │
│  • Task/work queue distribution         • Event sourcing / replay needed          │
│  • Priority queues needed               • Big data pipelines (Spark, Flink)      │
│  • Request-reply (RPC) patterns         • Long-term event retention              │
│  • Heterogeneous consumer speeds        • Multiple consumer groups on same data  │
│  • Low latency requirement              • Log aggregation at scale               │
│  • Small-medium scale (<100K msg/s)     • Large scale (>100K msg/s)              │
│  • Microservice task offloading         • Cross-team data sharing                │
│                                                                                   │
└───────────────────────────────────────────────────────────────────────────────────┘
```

---

## 18. Performance Tuning Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                      PERFORMANCE TUNING — QUICK REFERENCE                        │
│                                                                                 │
│  PUBLISHERS                                                                      │
│  ───────────────────────────────────────────────────────────────────             │
│  • Use publisher confirms (async batch, not sync per-message)                    │
│  • Batch publishes (multiple msgs per channel round-trip)                        │
│  • Keep messages < 128KB (store large payloads externally)                       │
│  • Use persistent delivery_mode=2 only when needed                               │
│  • Reuse connections (1 connection, multiple channels)                            │
│                                                                                 │
│  CONSUMERS                                                                       │
│  ───────────────────────────────────────────────────────────────────             │
│  • Set prefetch_count = 10-50 (never 0/unlimited)                                │
│  • Use manual ack (auto_ack = false)                                             │
│  • Ack in batches where possible (multiple=true)                                 │
│  • Process messages concurrently (thread pool behind consumer)                   │
│  • Monitor unacked message count                                                 │
│                                                                                 │
│  QUEUES                                                                          │
│  ───────────────────────────────────────────────────────────────────             │
│  • Use quorum queues for replicated workloads                                    │
│  • Set x-max-length to prevent unbounded growth                                  │
│  • Set x-message-ttl for expiration                                              │
│  • Configure DLX on every queue                                                  │
│  • Keep queue depth low (< 100K messages ideally)                                │
│  • Use lazy queues (x-queue-mode=lazy) if you MUST buffer to disk               │
│                                                                                 │
│  CLUSTER                                                                         │
│  ───────────────────────────────────────────────────────────────────             │
│  • 3 or 5 nodes (odd for quorum)                                                 │
│  • SSD storage (CRITICAL for quorum queues + WAL)                                │
│  • Separate disk for WAL (reduce I/O contention)                                 │
│  • vm_memory_high_watermark = 0.4 (default, tune up to 0.6 carefully)           │
│  • disk_free_limit = {mem_relative, 1.5} (1.5x RAM as free disk)               │
│  • Enable partition_handling = pause_minority                                    │
│                                                                                 │
│  MONITORING                                                                      │
│  ───────────────────────────────────────────────────────────────────             │
│  • Queue depth (per queue)              → alert > 100K                           │
│  • Unacked messages (per consumer)      → alert if growing                       │
│  • Connection count                      → alert > 1000                          │
│  • Memory usage                          → alert > 70% watermark                 │
│  • Disk free space                       → alert < 2GB                           │
│  • Consumer utilization                  → alert < 50% (consumers idle)          │
│  • Published / delivered rate            → track ratio                           │
│  • Flow control connections              → any = investigate                     │
│                                                                                 │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 19. Interview Questions — Medium

### Q1: Explain the difference between exchanges and queues. Why doesn't the producer publish directly to a queue?

```
Answer:

EXCHANGES handle ROUTING. QUEUES handle STORAGE + DELIVERY.

  Producer → Exchange → (routing logic) → Queue(s) → Consumer(s)

Why the separation:

1. DECOUPLING: Producer doesn't know which queues exist.
   Adding a new consumer = add a binding. No producer change.

2. FAN-OUT: One message can be routed to multiple queues
   via fanout exchange. Direct queue publishing = one destination.

3. FILTERING: Topic exchange routes by pattern matching.
   Same stream, different queues get different subsets.

4. EXCHANGE-TO-EXCHANGE: Build routing DAGs for complex topologies.

If producer published directly to queue:
• Producer must know ALL consumers (tight coupling)
• Adding a consumer = change producer code
• No broadcast / pattern routing
• You've rebuilt a point-to-point system (like direct HTTP)
```

### Q2: How do you ensure no messages are lost in RabbitMQ?

```
Answer — FIVE LAYERS OF SAFETY:

1. DURABLE EXCHANGE        exchange_declare(durable=True)
2. DURABLE QUEUE           queue_declare(durable=True)
3. PERSISTENT MESSAGE      delivery_mode=2
4. PUBLISHER CONFIRMS      confirm.select + wait for basic.ack
5. MANUAL CONSUMER ACK     auto_ack=False, ack AFTER processing

  Producer                     Broker                    Consumer
     │                           │                          │
     │── publish(persistent) ──→ │                          │
     │                           │── write to disk          │
     │◄── publisher confirm ────│                          │
     │                           │── deliver ──────────────→│
     │                           │                          │── process
     │                           │◄── basic.ack ───────────│
     │                           │── delete from queue      │

Missing ANY layer = potential message loss.

BONUS: For quorum queues, Raft replication adds cross-node durability.
Even if the leader node crashes, messages survive on followers.
```

### Q3: What is prefetch count and what happens if you set it wrong?

```
  prefetch = 0 (unlimited):
  ┌──────────┐   ALL messages dumped
  │  Broker   │ ════════════════════════→ Consumer (10GB RAM? OOM crash)
  └──────────┘   
  • Consumer buffer grows unbounded
  • Other consumers starve (one consumer hogs all messages)
  • Memory alarm on consumer → crash → messages requeued → cascade

  prefetch = 1 (too low):
  ┌──────────┐   msg1        ack      msg2        ack
  │  Broker   │ ═══════→ Consumer ═══→ ═══════→ Consumer ═══→ 
  └──────────┘   
  • Every message = 1 network round-trip
  • Throughput drops dramatically (latency-bound)
  • Good only for very expensive tasks (e.g., video encoding)
  
  prefetch = 10-50 (sweet spot):
  • Consumer has buffer of work ready
  • Network round-trips amortized
  • If consumer crashes, at most 10-50 msgs redelivered
  
  INTERVIEW KEY POINT: prefetch is NOT "batch size."
  It's the max number of UNACKNOWLEDGED messages in flight.
  Broker sends up to prefetch count, then waits for acks.
```

### Q4: Explain dead letter exchanges. Design a retry system with exponential backoff.

```
  A DLX is just a regular exchange marked as the "dead letter" destination
  for a queue. Messages are routed there when:
  • Consumer rejects (nack with requeue=false)
  • TTL expires
  • Queue max-length exceeded

  EXPONENTIAL BACKOFF DESIGN:

  ┌──────────┐   fail    ┌──────────────┐  5s TTL    ┌──────────┐
  │  Main Q  │ ──nack──→ │  Retry-5s Q  │ ──expire──→│  Main Q  │  (attempt 2)
  └──────────┘           │  DLX→main-ex │            └──────────┘
       │                  └──────────────┘
       │ fail (attempt 2)
       ▼
  ┌──────────────┐  30s TTL   ┌──────────┐
  │  Retry-30s Q │ ──expire──→│  Main Q  │  (attempt 3)
  └──────────────┘            └──────────┘
       │ fail (attempt 3)
       ▼
  ┌──────────────┐  120s TTL  ┌──────────┐
  │ Retry-120s Q │ ──expire──→│  Main Q  │  (attempt 4)
  └──────────────┘            └──────────┘
       │ fail (attempt 4, max retries exceeded)
       ▼
  ┌──────────────┐
  │  Parking Lot │  ← manual review, alerting
  └──────────────┘

  Track attempts via x-death header:
  x-death[0].count → number of dead-letter cycles
  In consumer: if count > MAX_RETRIES → publish to parking-lot, ack original
```

### Q5: How does RabbitMQ handle message ordering?

```
  WITHIN A SINGLE QUEUE: Messages are delivered in FIFO order
  to a single consumer (assuming prefetch=1).

  ORDERING BREAKS WHEN:

  1. Multiple competing consumers (prefetch > 1):
     Consumer A gets msg1, Consumer B gets msg2.
     B processes faster → msg2 "completes" before msg1.
     
  2. Message redelivery:
     msg1 delivered → consumer crashes → msg1 requeued at HEAD.
     Meanwhile msg2 was already delivered to another consumer.
     
  3. Priority queues:
     Higher priority messages jump ahead of lower priority ones.

  SOLUTIONS FOR STRICT ORDERING:
  
  1. Single consumer (x-single-active-consumer = true)
     + prefetch = 1
     Slowest but guarantees order.
  
  2. Consistent hash exchange:
     Route messages with same key to same queue.
     One consumer per queue.
     Similar to Kafka's partition-key model.
  
  3. Application-level sequencing:
     Include sequence_number in message.
     Consumer buffers and reorders.
     Complex but handles redeliveries.

  INTERVIEW KEY POINT: RabbitMQ does NOT guarantee ordering by default
  across competing consumers. This is different from Kafka, which
  guarantees ordering within a partition.
```

### Q6: What are virtual hosts and when would you use them?

```
  Vhosts are namespaces within a single RabbitMQ instance.
  Each vhost has its own:
  • Exchanges
  • Queues
  • Bindings
  • User permissions
  • Policies

  USE CASES:
  1. Multi-tenancy: Each customer gets their own vhost
  2. Environment isolation: /production, /staging, /dev on same cluster
  3. Team isolation: /team-payments, /team-notifications
  4. Security boundaries: Users can only access their vhost

  NOT a vhost:
  • It's NOT a separate Erlang node
  • It's NOT a performance boundary (shares CPU, memory, disk)
  • It's NOT a cluster partition
```

### Q7: Compare quorum queues vs classic queues. When would you pick each?

```
  PICK QUORUM QUEUES WHEN:
  • Data safety matters (payments, orders, critical events)
  • You need replicated queues (survive node failure)
  • You need poison message protection (x-delivery-limit)
  • You're on RabbitMQ 3.8+ (4.x preferred)

  PICK CLASSIC QUEUES WHEN:
  • Temporary/ephemeral queues (RPC reply queues)
  • Non-replicated queues where speed > safety
  • Very high queue churn (thousands of short-lived queues)
  • Queue-per-connection patterns

  KEY DIFFERENCE:
  Classic: single process, optional (removed) mirroring
  Quorum:  Raft consensus, majority-based replication, WAL on disk
  
  Quorum queues use more disk I/O and memory per queue,
  but provide dramatically better data safety guarantees.
```

---

## 20. Interview Questions — Hard

### Q1: You're designing an order processing system. Orders must be processed exactly once, in order, and survive broker failures. Design the RabbitMQ topology.

```
  CHALLENGE: Exactly-once is IMPOSSIBLE in distributed systems (FLP).
  We aim for EFFECTIVELY exactly-once via idempotency.

  DESIGN:

  ┌──────────────┐
  │ Order Service │
  │               │── publish (persistent, mandatory, publisher confirm)
  │ • Generate    │      routing_key = "order.{region}"
  │   idempotency │      headers: { idempotency_key: uuid }
  │   key (UUID)  │
  └──────────────┘
         │
         ▼
  ┌──────────────────┐
  │ Consistent Hash  │  ← routes by order_id hash to N queues
  │ Exchange         │     (ensures same customer's orders go to same queue)
  └──────────────────┘
         │
    ┌────┼────┐
    ▼    ▼    ▼
  ┌────┐┌────┐┌────┐
  │ Q0 ││ Q1 ││ Q2 │  ← Quorum queues (3 replicas each)
  └────┘└────┘└────┘     x-single-active-consumer = true
    │    │    │          x-delivery-limit = 3
    ▼    ▼    ▼          DLX configured
  ┌────┐┌────┐┌────┐
  │ C0 ││ C1 ││ C2 │  ← Single active consumer per queue
  └────┘└────┘└────┘     prefetch = 1 (strict ordering)
         │
         ▼
  ┌──────────────────────────────────────────────────────┐
  │  Consumer Logic (Idempotent Processing):              │
  │                                                       │
  │  1. Extract idempotency_key from message              │
  │  2. Check DB: SELECT processed FROM idempotency_log   │
  │     WHERE key = ?                                     │
  │  3. If already processed → ACK (skip, dedup)          │
  │  4. If new:                                           │
  │     BEGIN TRANSACTION                                 │
  │       INSERT INTO orders (...)                        │
  │       INSERT INTO idempotency_log (key, timestamp)    │
  │     COMMIT                                            │
  │  5. ACK message                                       │
  │                                                       │
  │  If processing fails → NACK (requeue=false) → DLX     │
  │  DLX → retry queue (TTL=30s) → back to main queue    │
  │  After 3 DLX cycles → parking lot queue               │
  └──────────────────────────────────────────────────────┘

  WHY THIS WORKS:
  • Consistent hash → order for same customer hits same queue
  • Single active consumer → one processor per queue (ordering)
  • Quorum queue → survives node failures (Raft majority)
  • Idempotency key → handles redeliveries without duplicate processing
  • DLX + delivery limit → handles poison messages
  • Publisher confirms → ensures broker received the message
```

### Q2: RabbitMQ is processing 50K msg/s but the team needs 200K msg/s. How do you scale?

```
  SCALING RABBITMQ — STEP BY STEP:

  STEP 1: IDENTIFY THE BOTTLENECK
  ┌──────────────────────────────────────────────────────────────────┐
  │  Is it?                                                         │
  │  • Publisher rate? → check flow control, connection count       │
  │  • Consumer rate?  → check prefetch, processing time            │
  │  • Broker CPU?     → check exchange routing overhead            │
  │  • Broker disk?    → check queue depth, persistence mode        │
  │  • Broker memory?  → check unacked msgs, queue paging          │
  │  • Network?        → check NIC utilization, msg size            │
  └──────────────────────────────────────────────────────────────────┘

  STEP 2: CONSUMER-SIDE OPTIMIZATIONS
  • Increase prefetch_count (e.g., 50 → 250)
  • Add more consumers (competing consumers pattern)
  • Process concurrently (thread pool per consumer)
  • Batch ack (ack every N messages with multiple=true)
  • Check: is processing doing I/O? DB? Can it be async?

  STEP 3: PUBLISHER-SIDE OPTIMIZATIONS
  • Async publisher confirms (don't wait per message)
  • Batch publishes on single channel
  • Use transient messages (delivery_mode=1) if loss acceptable
  • Compress large payloads

  STEP 4: BROKER TOPOLOGY
  • Shard queues: instead of 1 queue, use N queues
    (consistent hash exchange or application-level sharding)
  • Each queue on different node → distribute I/O
  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
  │ Queue-0 │   │ Queue-1 │   │ Queue-2 │   │ Queue-3 │
  │ Node-1  │   │ Node-2  │   │ Node-3  │   │ Node-1  │
  │ 50K/s   │   │ 50K/s   │   │ 50K/s   │   │ 50K/s   │
  └─────────┘   └─────────┘   └─────────┘   └─────────┘
                     TOTAL: 200K/s

  STEP 5: HARDWARE
  • SSD (NVMe preferred) → critical for quorum queue WAL
  • More RAM → keep messages in alpha state
  • 10Gbps NIC → RabbitMQ is network-chatty

  STEP 6: CONSIDER ALTERNATIVES
  If none of the above works AND you need sustained 200K+ msg/s:
  • Switch to Kafka for that workload
  • Keep RabbitMQ for complex routing, use Kafka for high-volume streaming
  • Hybrid architecture is common in production
  
  INTERVIEW KEY POINT: RabbitMQ doesn't horizontally scale like Kafka.
  You can't just "add nodes." You must shard at the queue level.
```

### Q3: Explain how RabbitMQ's credit flow system prevents broker OOM. What happens when a producer is faster than a consumer?

```
  THE CREDIT SYSTEM — INTERNAL BACKPRESSURE:

  ┌────────────────────────────────────────────────────────────────────────┐
  │                                                                        │
  │  TCP Reader ──(200 credits)──→ Channel ──(200 credits)──→ Queue       │
  │                                                                        │
  │  FLOW:                                                                 │
  │  1. TCP reader gets 200 credits from channel process                   │
  │  2. Each message forwarded = 1 credit used                             │
  │  3. At 0 credits → reader BLOCKS (stops reading TCP socket)            │
  │  4. Channel grants 50 more credits after processing 50 messages        │
  │  5. Reader resumes                                                     │
  │                                                                        │
  │  CASCADING SCENARIO (producer >> consumer):                            │
  │                                                                        │
  │  t=0   Queue depth growing (consumer can't keep up)                    │
  │  t=1   Queue process memory increasing                                 │
  │  t=2   Queue starts paging to disk (beta → delta state)               │
  │  t=3   Disk I/O becomes bottleneck                                     │
  │  t=4   Queue process stops granting credits to channel                 │
  │  t=5   Channel process stops granting credits to TCP reader            │
  │  t=6   TCP reader BLOCKS → TCP buffer fills → OS backpressure         │
  │  t=7   Producer TCP send() blocks → producer effectively throttled     │
  │                                                                        │
  │  In management UI: connection shows "flow" state                       │
  │                                                                        │
  │  If queue keeps growing despite flow control:                          │
  │  t=N   Memory exceeds vm_memory_high_watermark (40% of RAM)           │
  │  t=N+1 MEMORY ALARM → ALL publishers blocked globally                 │
  │         (even on other queues, other vhosts)                           │
  │  t=N+2 Only consumers can run (drain the queues)                      │
  │  t=N+3 Once memory drops below threshold → publishers unblocked       │
  │                                                                        │
  │  NUCLEAR OPTION:                                                       │
  │  If memory alarm doesn't resolve AND free disk < disk_free_limit:     │
  │  → DISK ALARM → broker refuses ALL operations (read + write)          │
  │  → Manual intervention required                                        │
  │                                                                        │
  └────────────────────────────────────────────────────────────────────────┘

  INTERVIEW KEY POINT: Credit flow is per-connection backpressure.
  Memory alarm is cluster-wide nuclear option.
  They are two different mechanisms working at different granularities.
```

### Q4: Design a multi-datacenter RabbitMQ deployment for a payment processing system. What are the tradeoffs?

```
  REQUIREMENTS:
  • Active-active in 2 DCs (US-East, EU-West)
  • Payment orders processed locally for latency
  • No message loss
  • Handle DC failure gracefully

  DESIGN:

  DC: US-EAST                                DC: EU-WEST
  ┌──────────────────────────────┐          ┌──────────────────────────────┐
  │  RabbitMQ Cluster (3 nodes)  │          │  RabbitMQ Cluster (3 nodes)  │
  │                               │          │                               │
  │  Exchange: payments.topic     │          │  Exchange: payments.topic     │
  │  Queue: us.payments.process  │          │  Queue: eu.payments.process  │
  │  Queue: us.payments.audit    │          │  Queue: eu.payments.audit    │
  │  Queue: global.settlements   │          │  Queue: global.settlements   │
  │                               │  Shovel  │                               │
  │  ┌───────────────────────┐   │◄════════►│  ┌───────────────────────┐   │
  │  │ Shovel: settlements   │   │  (TLS)   │  │ Shovel: settlements   │   │
  │  │ ack-mode: on-confirm  │   │          │  │ ack-mode: on-confirm  │   │
  │  └───────────────────────┘   │          │  └───────────────────────┘   │
  │                               │          │                               │
  │  ┌───────────────────────┐   │Federation│  ┌───────────────────────┐   │
  │  │ Federation: audit     │   │◄════════►│  │ Federation: audit     │   │
  │  │ (demand-based)        │   │  (TLS)   │  │ (demand-based)        │   │
  │  └───────────────────────┘   │          │  └───────────────────────┘   │
  └──────────────────────────────┘          └──────────────────────────────┘

  MESSAGE FLOW:
  1. US payment → published to US cluster → processed locally (low latency)
  2. EU payment → published to EU cluster → processed locally (low latency)
  3. Settlement data → shoveled to other DC (on-confirm for safety)
  4. Audit data → federated (demand-based, both DCs can query)

  TRADEOFFS:
  ┌───────────────────────────────────────────────────────────────────┐
  │  CHOICE                  TRADEOFF                                 │
  │  ───────────────────     ─────────────────────────────────        │
  │  Shovel on-confirm       Safest, but WAN latency per msg         │
  │  Shovel on-publish       Faster, tiny window of msg loss          │
  │  Federation              Demand-based (efficient), but            │
  │                          complex failure modes                    │
  │  Active-Active           Low latency, but settlement              │
  │                          reconciliation is complex                 │
  │  Active-Passive          Simpler, but failover latency            │
  │                          and manual switchover                    │
  └───────────────────────────────────────────────────────────────────┘

  DC FAILURE HANDLING:
  • DNS/LB routes traffic to surviving DC
  • Shovel reconnects automatically when DC recovers
  • Messages buffered in source queue during outage
  • On recovery: shovel drains backlog
  • Idempotency keys prevent duplicate processing

  WHY NOT CROSS-DC CLUSTERING:
  Erlang distribution protocol is CHATTY and latency-sensitive.
  Cross-DC heartbeat failures → false partition detection → split brain.
  NEVER cluster RabbitMQ across WANs.
```

### Q5: A team reports their RabbitMQ consumers are processing messages but queue depth keeps growing. Diagnose and fix.

```
  SYSTEMATIC DIAGNOSIS:

  STEP 1: CHECK THE MATH
  ┌─────────────────────────────────────────────────────────────┐
  │  rabbitmqctl list_queues name messages message_stats        │
  │                                                             │
  │  Publish rate:  10,000 msg/s                                │
  │  Deliver rate:   7,000 msg/s                                │
  │  Ack rate:       7,000 msg/s                                │
  │  Queue depth:    growing at 3,000 msg/s                     │
  │                                                             │
  │  → Consumer throughput is 30% below publish rate            │
  └─────────────────────────────────────────────────────────────┘

  STEP 2: CHECK CONSUMER HEALTH
  ┌─────────────────────────────────────────────────────────────┐
  │  rabbitmqctl list_consumers                                 │
  │  Check: consumer_utilization metric                         │
  │                                                             │
  │  If utilization < 100%:                                     │
  │    → Consumer is idle sometimes → prefetch too low          │
  │    → Or consumer is slow (check processing time)            │
  │                                                             │
  │  If utilization = 100%:                                     │
  │    → Consumer is saturated → need more consumers            │
  └─────────────────────────────────────────────────────────────┘

  STEP 3: CHECK FOR FLOW CONTROL
  ┌─────────────────────────────────────────────────────────────┐
  │  Management UI → Connections tab                            │
  │                                                             │
  │  Any connections in "flow" state?                           │
  │  → Broker is backpressuring publishers (good sign,          │
  │    means broker is protecting itself)                       │
  │  → But if consumers are also in flow: network I/O issue    │
  └─────────────────────────────────────────────────────────────┘

  STEP 4: COMMON ROOT CAUSES & FIXES

  CAUSE 1: Prefetch too low
  Fix: Increase from 1 to 50
  
  CAUSE 2: Consumer doing synchronous DB call per message
  Fix: Batch DB writes, or use async I/O
  
  CAUSE 3: Not enough consumers
  Fix: Add competing consumers (horizontal scale)
  
  CAUSE 4: Consumer processing includes HTTP call to slow service
  Fix: Circuit breaker, async processing, or separate queue
  
  CAUSE 5: Unacked messages building up (consumer not acking)
  Fix: Check for bugs in ack logic, add ack timeout
  
  CAUSE 6: Message redelivery storm (poison messages)
  Fix: Add DLX + delivery limit, check for nack loops
```

### Q6: Explain Raft consensus in quorum queues. What happens during a leader failure? What about a network partition with 5 nodes?

```
  RAFT IN QUORUM QUEUES:

  NORMAL OPERATION (5 nodes, quorum = 3):
  
  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
  │ Node 1 │  │ Node 2 │  │ Node 3 │  │ Node 4 │  │ Node 5 │
  │ LEADER │  │FOLLOWER│  │FOLLOWER│  │FOLLOWER│  │FOLLOWER│
  │  WAL   │  │  WAL   │  │  WAL   │  │  WAL   │  │  WAL   │
  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘
      │            │            │
      └──────┬─────┘            │
             │                  │
         Majority = 3        (4,5 can lag)
         confirms commit

  LEADER FAILURE:
  
  t=0:  Leader (Node 1) crashes
  t=1:  Followers detect missing heartbeat
  t=2:  Election timeout triggers (randomized 150-300ms)
  t=3:  Most up-to-date follower becomes CANDIDATE
  t=4:  Candidate requests votes from all nodes
  t=5:  Majority (3 of 4 remaining) vote → new LEADER elected
  t=6:  New leader starts serving reads/writes
  t=7:  Clients reconnect (RabbitMQ client library handles this)
  
  Total failover time: typically 5-30 seconds
  (depends on net_ticktime and election timeout config)

  NETWORK PARTITION SCENARIO (5 nodes: 3 vs 2):
  
  [Node1] [Node2] [Node3]  ✗  [Node4] [Node5]
       Majority (3)         │    Minority (2)
                          partition
  
  MAJORITY SIDE (Nodes 1,2,3):
  • Has quorum (3/5) → continues operating
  • Leader election if old leader was on minority side
  • Accepts publishes, delivers to consumers
  • Messages committed when 3/5 agree
  
  MINORITY SIDE (Nodes 4,5):
  • Cannot form quorum (2/5 < 3)
  • Stops accepting writes
  • Stops delivering messages
  • Followers are READ-ONLY (can't even elect leader)
  • Clients on this side see errors → reconnect to majority
  
  PARTITION HEALS:
  • Nodes 4,5 rejoin cluster
  • Sync missed WAL entries from leader
  • Resume as followers
  • NO DATA LOSS (minority side didn't accept writes)

  EDGE CASE — 3-WAY PARTITION (2 vs 2 vs 1):
  • No side has majority → entire queue is UNAVAILABLE
  • Safety preserved (no writes accepted)
  • Availability sacrificed (CP in CAP terms)
```

### Q7: Design a system where RabbitMQ and Kafka coexist. What goes through RabbitMQ? What goes through Kafka? How do they interact?

```
  HYBRID ARCHITECTURE — E-COMMERCE PLATFORM:

  ┌───────────────────────────────────────────────────────────────────────────────┐
  │                                                                               │
  │                          TRAFFIC ENTRY                                        │
  │                              │                                                │
  │                    ┌─────────┴──────────┐                                    │
  │                    │    API Gateway      │                                    │
  │                    └────────┬───────────┘                                    │
  │                             │                                                │
  │              ┌──────────────┼──────────────┐                                │
  │              ▼              ▼               ▼                                │
  │  ┌──────────────┐  ┌─────────────┐  ┌──────────────┐                       │
  │  │ Order Service │  │ User Service│  │ Search Service│                       │
  │  └──────┬───────┘  └──────┬──────┘  └──────┬───────┘                       │
  │         │                  │                 │                                │
  │    ┌────┘                  │                 │                                │
  │    ▼                       │                 │                                │
  │  ┌──────────────────┐     │                 │                                │
  │  │    RABBITMQ       │     │                 │                                │
  │  │ (task routing)    │     │                 │                                │
  │  │                   │     │                 │                                │
  │  │ • Order validation│     │                 │                                │
  │  │ • Payment process │     │                 │                                │
  │  │ • Email dispatch  │     │                 │                                │
  │  │ • SMS notify      │     │                 │                                │
  │  │ • PDF generation  │     │                 │                                │
  │  │ • Inventory check │     │                 │                                │
  │  └────────┬─────────┘     │                 │                                │
  │           │                │                 │                                │
  │           │   ┌────────────┘                 │                                │
  │           │   │                              │                                │
  │           ▼   ▼                              │                                │
  │  ┌──────────────────┐                       │                                │
  │  │     KAFKA          │                       │                                │
  │  │ (event stream)     │                       │                                │
  │  │                    │◄──────────────────────┘                               │
  │  │ • order.completed  │                                                       │
  │  │ • user.signup      │   ┌──────────────────────────────────┐               │
  │  │ • search.query     │──→│ Downstream Consumers:            │               │
  │  │ • payment.settled  │   │ • Analytics (Spark)              │               │
  │  │ • inventory.updated│   │ • ML training pipeline           │               │
  │  │                    │   │ • Data warehouse (Snowflake)     │               │
  │  │ Retained 7 days    │   │ • Audit trail                   │               │
  │  └──────────────────┘   │ • Search index rebuild           │               │
  │                          │ • Recommendation engine          │               │
  │                          └──────────────────────────────────┘               │
  │                                                                               │
  └───────────────────────────────────────────────────────────────────────────────┘

  THROUGH RABBITMQ:                        THROUGH KAFKA:
  ─────────────────────                    ─────────────────────
  • Task dispatch (work queues)            • Event streaming (domain events)
  • Complex routing (topic exchange)       • Event sourcing (replay state)
  • Priority tasks (VIP orders first)      • Analytics pipelines (batch + stream)
  • RPC (request-reply for inventory)      • Cross-team data sharing
  • Short-lived, process-and-delete        • Long retention (7+ days)
  • Needs sub-ms latency                   • Needs replay capability
  • Retry with DLX/backoff                 • Needs consumer groups

  BRIDGE PATTERN:
  Order Service publishes to RabbitMQ for immediate task processing.
  After task completes, a "bridge consumer" on RabbitMQ publishes 
  the domain event to Kafka for long-term streaming.
  
  RabbitMQ Consumer → process order → publish event → Kafka Producer
```

### Q8: You're given a RabbitMQ cluster that's been running for 2 years with no quorum queues, no DLX, default configs. Migrate it to production-grade with zero downtime.

```
  MIGRATION PLAN — PHASED APPROACH:

  PHASE 1: ASSESSMENT (Week 1)
  ┌──────────────────────────────────────────────────────────────────┐
  │  1. Inventory all queues, exchanges, bindings                    │
  │     rabbitmqctl list_queues name type durable messages consumers │
  │                                                                  │
  │  2. Identify critical vs non-critical queues                     │
  │     Critical: payments, orders, user data                        │
  │     Non-critical: logs, analytics, temp queues                   │
  │                                                                  │
  │  3. Measure current metrics                                      │
  │     • Message rates per queue                                    │
  │     • Queue depths                                               │
  │     • Consumer counts and prefetch settings                      │
  │     • Memory and disk usage                                      │
  │                                                                  │
  │  4. Check RabbitMQ version (must be 3.8+ for quorum queues)     │
  └──────────────────────────────────────────────────────────────────┘

  PHASE 2: ADD MONITORING (Week 2)
  ┌──────────────────────────────────────────────────────────────────┐
  │  • Deploy Prometheus + Grafana with RabbitMQ exporter            │
  │  • Set up alerts: queue depth, memory, disk, consumer lag        │
  │  • Baseline metrics for 1 week before changes                    │
  └──────────────────────────────────────────────────────────────────┘

  PHASE 3: ADD DLX TO EXISTING QUEUES (Week 3)
  ┌──────────────────────────────────────────────────────────────────┐
  │  • Create DLX exchanges and dead-letter queues                   │
  │  • Apply policy (no queue restart needed):                       │
  │    rabbitmqctl set_policy DLX ".*"                               │
  │      '{"dead-letter-exchange":"dlx.default"}' --apply-to queues │
  │  • Policies apply to existing queues without restart ✓           │
  └──────────────────────────────────────────────────────────────────┘

  PHASE 4: MIGRATE TO QUORUM QUEUES (Week 4-6)
  ┌──────────────────────────────────────────────────────────────────┐
  │  Cannot convert classic → quorum in-place.                       │
  │  Must use BLUE-GREEN migration:                                  │
  │                                                                  │
  │  For each critical queue:                                        │
  │                                                                  │
  │  1. Create NEW quorum queue: "orders-v2" (quorum type)          │
  │     - x-queue-type: quorum                                      │
  │     - x-delivery-limit: 3                                       │
  │     - DLX configured                                            │
  │                                                                  │
  │  2. Update PRODUCERS to publish to both "orders" and "orders-v2"│
  │     (dual-write phase)                                          │
  │                                                                  │
  │  3. Start consumers on "orders-v2"                               │
  │     Verify processing works correctly                            │
  │                                                                  │
  │  4. Stop consumers on old "orders" queue                         │
  │     Wait for old queue to drain                                  │
  │                                                                  │
  │  5. Update producers to publish ONLY to "orders-v2"             │
  │                                                                  │
  │  6. Delete old "orders" queue                                    │
  │                                                                  │
  │  7. (Optional) Rename "orders-v2" → "orders" via exchange+bind  │
  │     Or just update all configs to use new name                   │
  └──────────────────────────────────────────────────────────────────┘

  PHASE 5: CLUSTER CONFIG (Week 6-7)
  ┌──────────────────────────────────────────────────────────────────┐
  │  • Set partition_handling = pause_minority                       │
  │  • Tune vm_memory_high_watermark = 0.5                          │
  │  • Set disk_free_limit = {mem_relative, 1.5}                    │
  │  • Enable TLS for inter-node communication                      │
  │  • Rotate default guest credentials                              │
  │  • Set appropriate prefetch on all consumers                     │
  └──────────────────────────────────────────────────────────────────┘

  ROLLBACK PLAN:
  During dual-write phase, old queue is still active.
  If quorum queue has issues → stop consumers on v2,
  resume consumers on old queue. Zero message loss.
```

---

## 21. Quick Reference Card

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    RABBITMQ — INTERVIEW QUICK REFERENCE                   │
│                                                                          │
│  "What is RabbitMQ?"                                                     │
│  → Message broker implementing AMQP. Smart broker / dumb consumer model. │
│    Routes messages through exchanges to queues via bindings.             │
│                                                                          │
│  "Why not just use HTTP?"                                                │
│  → Decoupling, buffering, fan-out, retry, priority queues, async.       │
│    Producer doesn't need to know consumers exist.                        │
│                                                                          │
│  "How does it route messages?"                                           │
│  → Exchanges: direct (exact match), topic (wildcard), fanout (broadcast),│
│    headers (attribute match). Bindings connect exchanges to queues.      │
│                                                                          │
│  "How does it ensure durability?"                                        │
│  → Durable exchange + durable queue + persistent message +               │
│    publisher confirms + quorum queues (Raft replication).                │
│                                                                          │
│  "How does it handle failures?"                                          │
│  → Quorum queues: Raft leader election. DLX for poison messages.         │
│    Credit flow for backpressure. Memory/disk alarms as safety net.       │
│                                                                          │
│  "Exactly-once?"                                                         │
│  → Not natively. Use idempotency keys + manual ack +                     │
│    publisher confirms for effectively-once semantics.                    │
│                                                                          │
│  "How does it scale?"                                                    │
│  → Queue sharding (consistent hash exchange), competing consumers,       │
│    Federation/Shovel for cross-DC. NOT horizontal like Kafka.            │
│                                                                          │
│  "When NOT RabbitMQ?"                                                    │
│  → High-throughput streaming (>100K/s), event replay/sourcing,           │
│    big data pipelines, long-term retention, log aggregation.             │
│                                                                          │
│  "RabbitMQ vs Kafka?"                                                    │
│  → RabbitMQ: complex routing, task queues, priority, low latency.        │
│    Kafka: high throughput, replay, event sourcing, big data.             │
│    Often used TOGETHER in production.                                    │
│                                                                          │
│  NUMBERS TO QUOTE:                                                       │
│  • Throughput: ~40K-100K msg/s per node (classic)                        │
│  • Latency: sub-ms to low ms (non-persistent, same node)                │
│  • Cluster: 3-5 nodes typical (odd for quorum)                           │
│  • Quorum: majority-based (3/5, 2/3)                                     │
│  • Prefetch: 10-50 recommended for most workloads                        │
│  • Memory watermark: 40% default                                         │
│  • Net tick time: 60s default (partition detection)                       │
│  • Protocols: AMQP 0-9-1, AMQP 1.0, MQTT, STOMP                        │
│  • Written in: Erlang/OTP                                                │
│  • Created: 2007, open-source under MPL 2.0                              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Sources & Further Reading

| Source | URL | Topic |
|--------|-----|-------|
| RabbitMQ Official Docs | rabbitmq.com/docs | Architecture, exchanges, queues, clustering |
| RabbitMQ Internals (GitHub) | github.com/rabbitmq/internals | Credit flow, queue internals, message store |
| RabbitMQ Blog — Quorum Queues | blog.rabbitmq.com/posts/2020/04/quorum-queues | Raft consensus, QQ design |
| RabbitMQ Blog — Streams | blog.rabbitmq.com/posts/2021/07/rabbitmq-streams | Stream architecture, offset tracking |
| RabbitMQ Partitions Docs | rabbitmq.com/docs/partitions | Network partition handling strategies |
| RabbitMQ Federation | rabbitmq.com/docs/federation | Multi-DC replication |
| CloudAMQP Blog | cloudamqp.com/blog | Production patterns, best practices |
| AlgoMaster.io — RabbitMQ | algomaster.io/learn/system-design-interviews/rabbitmq | System design interview prep |
| Arpit Bhayani — System Design | arpitbhayani.me/system-design-for-beginners | Message queue fundamentals |
| InfoQ — lastminute.com | infoq.com/news/2024/01/lastminute-search-rabbitmq-redis | RabbitMQ at scale case study |
| ScaleGrid — RabbitMQ Guide | scalegrid.io/blog/rabbitmq-quick-guide | Clustering, scaling, deployment |
| Redocly Post-Mortem (Jan 2026) | redocly.com/blog/jan-2026-outage-postmortem | RabbitMQ redelivery loop incident |
| RabbitMQ Jepsen Analysis | rabbitmq.com/blog/2020/06/quorum-queues-and-why-dislike-the-word-ha | Quorum queue safety guarantees |
| Martin Kleppmann — DDIA | dataintensive.net | Distributed systems fundamentals |
