# Apache Kafka — The Complete Deep Dive

> **Difficulty:** Medium-Hard | **Time:** 6-8 hours | **Priority:** Must Know  
> **Sources:** Apache Kafka Design Docs, Confluent, LinkedIn Engineering, Uber Engineering, Netflix Tech Blog, PagerDuty Post-Mortems  
> **For:** Senior Engineers (7+ years) preparing for System Design interviews

---

## Table of Contents

1. [What Is Kafka](#1-what-is-kafka)
2. [Core Architecture](#2-core-architecture)
3. [Storage Engine — The Log](#3-storage-engine--the-log)
4. [Producers Deep Dive](#4-producers-deep-dive)
5. [Consumers Deep Dive](#5-consumers-deep-dive)
6. [Replication & Fault Tolerance](#6-replication--fault-tolerance)
7. [KRaft — ZooKeeper Removal](#7-kraft--zookeeper-removal)
8. [Exactly-Once Semantics (EOS)](#8-exactly-once-semantics-eos)
9. [Log Compaction](#9-log-compaction)
10. [Kafka Streams & Connect](#10-kafka-streams--connect)
11. [Real-World Usage at Scale](#11-real-world-usage-at-scale)
12. [When Kafka Failed — Production Incidents](#12-when-kafka-failed--production-incidents)
13. [When NOT to Use Kafka](#13-when-not-to-use-kafka)
14. [Anti-Patterns That Kill Kafka](#14-anti-patterns-that-kill-kafka)
15. [Performance Tuning Cheat Sheet](#15-performance-tuning-cheat-sheet)
16. [Interview Questions — Medium](#16-interview-questions--medium)
17. [Interview Questions — Hard](#17-interview-questions--hard)
18. [Quick Reference Card](#18-quick-reference-card)

---

## 1. What Is Kafka

Kafka is a **distributed event streaming platform** originally built at LinkedIn in 2010 by Jay Kreps, Neha Narkhede, and Jun Rao. Open-sourced under Apache in 2011. Named after the author Franz Kafka.

It is NOT a traditional message queue. It is a **distributed commit log**.

```
Traditional Message Queue:                Kafka (Distributed Commit Log):

  Producer → [Queue] → Consumer           Producer → [Append-Only Log] → Consumer
                                           
  ✗ Message deleted after consumption      ✓ Messages retained (time/size/compaction)
  ✗ Single consumer per message            ✓ Multiple consumer groups independently
  ✗ No replay                              ✓ Replay from any offset
  ✗ Ordering per queue only                ✓ Ordering per partition (guaranteed)
  ✗ Throughput ~50K msg/s                  ✓ Throughput ~1M+ msg/s per cluster
  ✗ Not designed for storage               ✓ Designed as durable storage layer
```

### Three Core Capabilities

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     KAFKA = THREE SYSTEMS IN ONE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. PUBLISH / SUBSCRIBE          2. STORE              3. PROCESS           │
│  ┌────────────────────┐    ┌─────────────────┐   ┌──────────────────┐      │
│  │ High-throughput     │    │ Durable,         │   │ Real-time stream │      │
│  │ pub/sub messaging   │    │ fault-tolerant   │   │ processing with  │      │
│  │ for event streams   │    │ event storage    │   │ Kafka Streams    │      │
│  └────────────────────┘    └─────────────────┘   └──────────────────┘      │
│                                                                             │
│  → Import/export data           → Retain events       → Transform streams   │
│    from any system                for days/forever       as they arrive      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Core Architecture

### How Everything Fits Together — The Master Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                             │
│                              KAFKA ECOSYSTEM — FULL PICTURE                                  │
│                                                                                             │
│  PRODUCERS (write data)                                                                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                       │
│  │ Order Service │ │ Payment Svc  │ │ Debezium CDC │ │ IoT Sensors  │                       │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘                       │
│         │                │                │                │                                 │
│         │  key="order-A" │ key="pay-X"    │ key="user-7"   │  key=null                      │
│         │                │                │                │                                 │
│         ▼                ▼                ▼                ▼                                 │
│  ═══════════════════════════════════════════════════════════════════════════                  │
│                                                                                             │
│   KAFKA CLUSTER                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────────────────────┐    │
│  │                                                                                     │    │
│  │  KRaft Controller Quorum (metadata brain)                                           │    │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐                           │    │
│  │  │ Controller 0 ★│  │ Controller 1  │  │ Controller 2  │  Manages: broker health,  │    │
│  │  │ (active)      │  │ (standby)     │  │ (standby)     │  leader election, topic   │    │
│  │  └───────────────┘  └───────────────┘  └───────────────┘  metadata, configs        │    │
│  │                                                                                     │    │
│  │  ┌───────────────────────────────────────────────────────────────────────────────┐  │    │
│  │  │                                                                               │  │    │
│  │  │   TOPIC: "orders"  (logical grouping — like a DB table)                       │  │    │
│  │  │                                                                               │  │    │
│  │  │   ┌─────────────────────────────────────────────────────────────────────────┐ │  │    │
│  │  │   │ Partition 0   [off:0][off:1][off:2][off:3][off:4] ──►                   │ │  │    │
│  │  │   │               order-A order-A order-A order-A order-A                   │ │  │    │
│  │  │   │                                                                         │ │  │    │
│  │  │   │   hash("order-A") % 4 = 0  →  all order-A events land here             │ │  │    │
│  │  │   ├─────────────────────────────────────────────────────────────────────────┤ │  │    │
│  │  │   │ Partition 1   [off:0][off:1][off:2] ──►                                 │ │  │    │
│  │  │   │               order-B order-B order-B                                   │ │  │    │
│  │  │   ├─────────────────────────────────────────────────────────────────────────┤ │  │    │
│  │  │   │ Partition 2   [off:0][off:1][off:2][off:3][off:4][off:5] ──►            │ │  │    │
│  │  │   │               order-C order-C order-C order-C order-C order-C           │ │  │    │
│  │  │   ├─────────────────────────────────────────────────────────────────────────┤ │  │    │
│  │  │   │ Partition 3   [off:0][off:1] ──►                                        │ │  │    │
│  │  │   │               order-D order-D                                           │ │  │    │
│  │  │   └─────────────────────────────────────────────────────────────────────────┘ │  │    │
│  │  │                                                                               │  │    │
│  │  │   TOPIC: "payments"  (another topic — completely independent)                 │  │    │
│  │  │   ┌────────────────────────────────────────────────────────┐                  │  │    │
│  │  │   │ Partition 0   [pay events...] ──►                      │                  │  │    │
│  │  │   │ Partition 1   [pay events...] ──►                      │                  │  │    │
│  │  │   └────────────────────────────────────────────────────────┘                  │  │    │
│  │  │                                                                               │  │    │
│  │  └───────────────────────────────────────────────────────────────────────────────┘  │    │
│  │                                                                                     │    │
│  │  BUT WHERE DO PARTITIONS PHYSICALLY LIVE? → On BROKERS (servers):                   │    │
│  │                                                                                     │    │
│  │  Broker 0 (server)      Broker 1 (server)      Broker 2 (server)                   │    │
│  │  ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐                │    │
│  │  │                  │   │                  │   │                  │                │    │
│  │  │  orders/P0 ★     │   │  orders/P0       │   │  orders/P0       │                │    │
│  │  │  (LEADER)        │   │  (follower/ISR)  │   │  (follower/ISR)  │                │    │
│  │  │                  │   │                  │   │                  │                │    │
│  │  │  orders/P1       │   │  orders/P1 ★     │   │  orders/P1       │                │    │
│  │  │  (follower/ISR)  │   │  (LEADER)        │   │  (follower/ISR)  │                │    │
│  │  │                  │   │                  │   │                  │                │    │
│  │  │  orders/P2       │   │  orders/P2       │   │  orders/P2 ★     │                │    │
│  │  │  (follower/ISR)  │   │  (follower/ISR)  │   │  (LEADER)        │                │    │
│  │  │                  │   │                  │   │                  │                │    │
│  │  │  orders/P3       │   │  orders/P3 ★     │   │  orders/P3       │                │    │
│  │  │  (follower/ISR)  │   │  (LEADER)        │   │  (follower/ISR)  │                │    │
│  │  │                  │   │                  │   │                  │                │    │
│  │  │  pay/P0 ★        │   │  pay/P0          │   │  pay/P1 ★        │                │    │
│  │  │  (LEADER)        │   │  (follower)      │   │  (LEADER)        │                │    │
│  │  │                  │   │                  │   │                  │                │    │
│  │  │ Disk:            │   │ Disk:            │   │ Disk:            │                │    │
│  │  │ /kafka-logs/     │   │ /kafka-logs/     │   │ /kafka-logs/     │                │    │
│  │  │  orders-0/       │   │  orders-0/       │   │  orders-0/       │                │    │
│  │  │   0000.log       │   │   0000.log       │   │   0000.log       │                │    │
│  │  │   0000.index     │   │   0000.index     │   │   0000.index     │                │    │
│  │  │  orders-1/       │   │  orders-1/       │   │  orders-1/       │                │    │
│  │  │   ...            │   │   ...            │   │   ...            │                │    │
│  │  └──────────────────┘   └──────────────────┘   └──────────────────┘                │    │
│  │                                                                                     │    │
│  │  ★ = Leader (handles all reads & writes for that partition)                          │    │
│  │  Replication Factor = 3 → every partition exists on 3 brokers                        │    │
│  │  Leaders spread across brokers → no single bottleneck                                │    │
│  │                                                                                     │    │
│  └─────────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                             │
│  ═══════════════════════════════════════════════════════════════════════════                  │
│         │                │                │                │                                 │
│         ▼                ▼                ▼                ▼                                 │
│  CONSUMERS (read data — pull model)                                                         │
│                                                                                             │
│  Consumer Group A ("order-processing")     Consumer Group B ("analytics")                   │
│  ┌────────────────────────────────────┐    ┌────────────────────────────────────┐           │
│  │ Consumer A-1  ◄── orders/P0       │    │ Consumer B-1  ◄── orders/P0, P1   │           │
│  │ Consumer A-2  ◄── orders/P1       │    │ Consumer B-2  ◄── orders/P2, P3   │           │
│  │ Consumer A-3  ◄── orders/P2       │    └────────────────────────────────────┘           │
│  │ Consumer A-4  ◄── orders/P3       │                                                     │
│  └────────────────────────────────────┘    Both groups see ALL messages independently.      │
│                                            Each group tracks its own offsets.               │
│                                                                                             │
│  Consumer Group C ("search-indexer")                                                        │
│  ┌────────────────────────────────────┐                                                     │
│  │ Consumer C-1  ◄── orders/P0,P1,   │    Three different teams consuming the               │
│  │                   P2, P3          │    same "orders" topic for different purposes.       │
│  └────────────────────────────────────┘                                                     │
│                                                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────────┘

READING THIS DIAGRAM — THE HIERARCHY:

  Cluster          1 cluster    = multiple brokers + controller quorum
    └── Broker     1 broker     = a physical/virtual server running Kafka
         └── Topic    1 topic   = a named stream ("orders", "payments")
              └── Partition     = an ordered log, the unit of parallelism
                   └── Segment  = a file on disk (e.g., 0000.log)
                        └── Record = key + value + timestamp + headers
```

### The Big Picture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA CLUSTER                                        │
│                                                                              │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐         │
│  │  Broker 0   │   │  Broker 1   │   │  Broker 2   │   │  Broker 3   │         │
│  │             │   │             │   │             │   │             │         │
│  │ P0-Leader   │   │ P1-Leader   │   │ P2-Leader   │   │ P3-Leader   │         │
│  │ P3-Follower │   │ P0-Follower │   │ P1-Follower │   │ P2-Follower │         │
│  │ P1-Follower │   │ P2-Follower │   │ P3-Follower │   │ P0-Follower │         │
│  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘   └──────┬──────┘         │
│         │                 │                 │                 │              │
│         └────────┬────────┴────────┬────────┴────────┬────────┘              │
│                  │                 │                 │                        │
│         ┌────────▼─────────────────▼─────────────────▼────────┐              │
│         │            Controller Quorum (KRaft)                 │              │
│         │        Manages metadata, leader election             │              │
│         └──────────────────────────────────────────────────────┘              │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
         ▲                                                    │
         │                                                    ▼
┌────────┴──────────┐                              ┌──────────────────────┐
│    Producers       │                              │   Consumer Groups     │
│                    │                              │                      │
│ App Servers        │                              │ Group A: Analytics   │
│ Microservices      │                              │ Group B: Search      │
│ CDC (Debezium)     │                              │ Group C: Warehouse   │
│ IoT Sensors        │                              │ Group D: Alerts      │
└────────────────────┘                              └──────────────────────┘
```

### 7-Broker Cluster — Complete Example Walkthrough

This is a real-world-style example matching the demo project. Read it top-to-bottom.

```
═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 0 — THE CLUSTER
═══════════════════════════════════════════════════════════════════════════════════

 A "cluster" is simply a GROUP OF BROKERS that know about each other.
 They coordinate through a KRaft controller quorum (metadata leader).

                       ┌──────────────────────────────────────┐
                       │           KAFKA CLUSTER               │
                       │         "my-kafka-cluster"            │
                       │                                      │
                       │   7 Brokers   ·   2 Topics           │
                       │   RF = 3      ·   min.insync = 2     │
                       └──────────────────────────────────────┘


═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 1 — THE BROKERS (7 physical/virtual servers)
═══════════════════════════════════════════════════════════════════════════════════

 Each broker is a JVM process with its own disk, port, and broker ID.
 3 of them also run the KRaft controller (metadata management).

 ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
 │  Broker 1     │ │  Broker 2     │ │  Broker 3     │
 │  port: 9092   │ │  port: 9192   │ │  port: 9292   │
 │               │ │               │ │               │
 │  ★ Controller │ │  ★ Controller │ │  ★ Controller │
 │  + Broker     │ │  + Broker     │ │  + Broker     │
 └──────────────┘ └──────────────┘ └──────────────┘

 ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
 │  Broker 4     │ │  Broker 5     │ │  Broker 6     │ │  Broker 7     │
 │  port: 9392   │ │  port: 9492   │ │  port: 9592   │ │  port: 9692   │
 │               │ │               │ │               │ │               │
 │  Pure Broker  │ │  Pure Broker  │ │  Pure Broker  │ │  Pure Broker  │
 │  (no ctrl)    │ │  (no ctrl)    │ │  (no ctrl)    │ │  (no ctrl)    │
 └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘

 Controller quorum: Brokers 1, 2, 3 run Raft consensus for metadata.
 Pure brokers: Brokers 4-7 only store data and serve clients.


═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 2 — THE TOPICS (logical groupings, like DB tables)
═══════════════════════════════════════════════════════════════════════════════════

 A topic is a NAMED STREAM of events. It doesn't live on one broker —
 it's split into partitions that are SPREAD across all brokers.

 ┌──────────────────────────────────────────────────────────────────────────────┐
 │                                                                              │
 │  Topic: "demo-orders"                   Topic: "demo-clicks"                │
 │  ┌──────────────────────────────┐       ┌──────────────────────────────┐    │
 │  │  6 partitions, RF=3         │       │  7 partitions, RF=3         │    │
 │  │  = 18 total replica copies  │       │  = 21 total replica copies  │    │
 │  │  on 7 brokers               │       │  on 7 brokers               │    │
 │  └──────────────────────────────┘       └──────────────────────────────┘    │
 │                                                                              │
 │  Total partition replicas in cluster: 18 + 21 = 39 copies across 7 brokers  │
 │                                                                              │
 └──────────────────────────────────────────────────────────────────────────────┘


═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 3 — THE PARTITIONS (physical ordered logs)
═══════════════════════════════════════════════════════════════════════════════════

 Each partition is an APPEND-ONLY LOG on disk.
 Each message gets a sequential OFFSET (0, 1, 2, 3...).
 Key determines partition: hash(key) % num_partitions.

 Topic: "demo-clicks" — 7 partitions, each replicated to 3 brokers

 Partition 0:  [off:0][off:1][off:2][off:3][off:4][off:5] ──► next write
 Partition 1:  [off:0][off:1][off:2] ──►
 Partition 2:  [off:0][off:1][off:2][off:3][off:4][off:5][off:6][off:7] ──►
 Partition 3:  [off:0][off:1] ──►
 Partition 4:  [off:0][off:1][off:2][off:3] ──►
 Partition 5:  [off:0][off:1][off:2][off:3][off:4] ──►
 Partition 6:  [off:0][off:1][off:2][off:3] ──►

 ✓ Messages with the same key always land in the same partition → guaranteed order
 ✗ No ordering guarantee across different partitions


═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 4 — REPLICA PLACEMENT (where partitions physically live)
═══════════════════════════════════════════════════════════════════════════════════

 RF=3 means each partition has 3 copies. The controller places them using a
 round-robin shift pattern across brokers. First broker = preferred leader (★).

 Topic: "demo-clicks" replica assignment (from Kafka UI):

 Partition │ Replicas   │ Leader │ Followers
 ──────────┼────────────┼────────┼──────────
    P0     │ [6, 7, 1]  │  B6 ★  │  B7, B1
    P1     │ [7, 1, 2]  │  B7 ★  │  B1, B2
    P2     │ [1, 2, 3]  │  B1 ★  │  B2, B3
    P3     │ [2, 3, 4]  │  B2 ★  │  B3, B4
    P4     │ [3, 4, 5]  │  B3 ★  │  B4, B5
    P5     │ [4, 5, 6]  │  B4 ★  │  B5, B6
    P6     │ [5, 6, 7]  │  B5 ★  │  B6, B7

 Notice: leaders are spread across 7 different brokers — no bottleneck.
 Each broker leads ~1 partition and follows ~2 others for this topic.

 PHYSICAL VIEW — which partitions each broker stores:

 Broker 1               Broker 2               Broker 3
 ┌────────────────┐     ┌────────────────┐     ┌────────────────┐
 │ P0 (follower)  │     │ P1 (follower)  │     │ P2 (follower)  │
 │ P1 (follower)  │     │ P2 (follower)  │     │ P3 (follower)  │
 │ P2 ★ (LEADER)  │     │ P3 ★ (LEADER)  │     │ P4 (follower)  │
 │                │     │                │     │                │
 │ 3 replicas     │     │ 3 replicas     │     │ 3 replicas     │
 │ 1 leader       │     │ 1 leader       │     │ 0 leaders      │
 └────────────────┘     └────────────────┘     └────────────────┘

 Broker 4               Broker 5               Broker 6
 ┌────────────────┐     ┌────────────────┐     ┌────────────────┐
 │ P3 (follower)  │     │ P4 (follower)  │     │ P0 ★ (LEADER)  │
 │ P4 (follower)  │     │ P5 (follower)  │     │ P5 (follower)  │
 │ P5 ★ (LEADER)  │     │ P6 ★ (LEADER)  │     │ P6 (follower)  │
 │                │     │                │     │                │
 │ 3 replicas     │     │ 3 replicas     │     │ 3 replicas     │
 │ 1 leader       │     │ 1 leader       │     │ 1 leader       │
 └────────────────┘     └────────────────┘     └────────────────┘

 Broker 7
 ┌────────────────┐
 │ P0 (follower)  │    Total: 7 × 3 = 21 replica copies
 │ P1 ★ (LEADER)  │    Leaders: one per partition = 7
 │ P6 (follower)  │    Each broker holds 3 replicas, leads ~1
 │                │
 │ 3 replicas     │
 │ 1 leader       │
 └────────────────┘


═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 5 — REPLICATION FLOW (how followers stay in sync)
═══════════════════════════════════════════════════════════════════════════════════

 Let's zoom into Partition 0: Replicas = [6, 7, 1]

 Producer (acks=all)
     │
     │ 1. Send "click-event-42" to P0
     ▼
 ┌──────────────────────────────────────────────────────────────────────────┐
 │                                                                          │
 │  Broker 6 — P0 LEADER ★                                                 │
 │  ┌─────────────────────────────────────────────────────────────┐        │
 │  │ Log: [off:0][off:1][off:2]...[off:41][off:42] ←— NEW      │        │
 │  │                                              ▲              │        │
 │  │                                     HW=42 (high watermark) │        │
 │  └─────────────────────────────────────────────────────────────┘        │
 │      ▲                     ▲                                            │
 │      │  2. Fetch           │  2. Fetch                                  │
 │      │                     │                                            │
 │  Broker 7 — P0 Follower    Broker 1 — P0 Follower                      │
 │  ┌─────────────────────┐   ┌─────────────────────┐                     │
 │  │ Log: [...][off:42]  │   │ Log: [...][off:42]  │                     │
 │  │ ✓ In-Sync (ISR)     │   │ ✓ In-Sync (ISR)     │                     │
 │  └─────────────────────┘   └─────────────────────┘                     │
 │                                                                          │
 │  3. All ISR have off:42 → HW advances → producer gets ACK               │
 │                                                                          │
 │  ISR = {6, 7, 1} — all 3 replicas in sync — healthy                    │
 │                                                                          │
 └──────────────────────────────────────────────────────────────────────────┘

 Timeline:
 ─────────────────────────────────────────────────────────────────
 t=0ms   Producer sends to Broker 6 (P0 leader)
 t=1ms   Broker 6 appends to local log, offset=42
 t=2ms   Broker 7 fetches, gets offset 42, appends
 t=3ms   Broker 1 fetches, gets offset 42, appends
 t=4ms   Broker 6 sees all ISR caught up → advances HW to 42
 t=5ms   Producer receives ack (success, acks=all satisfied)
 ─────────────────────────────────────────────────────────────────


═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 6 — BROKER FAILURE AND LEADER ELECTION
═══════════════════════════════════════════════════════════════════════════════════

 What happens when Broker 6 crashes? (it was leader for P0 and follower for P5, P6)

 BEFORE FAILURE:
                 P0: [6★, 7, 1]    P5: [4★, 5, 6]    P6: [5★, 6, 7]

 ┌──────────┐   ┌──────────┐   ┌──────────┐
 │ Broker 6  │   │ Broker 7  │   │ Broker 1  │
 │ P0 ★ Lead │   │ P0 Follow │   │ P0 Follow │
 │ P5 Follow │   │ P1 ★ Lead │   │ P2 ★ Lead │
 │ P6 Follow │   │ P6 Follow │   │ P1 Follow │
 │   ALIVE   │   │   ALIVE   │   │   ALIVE   │
 └──────────┘   └──────────┘   └──────────┘

 AFTER Broker 6 CRASHES:

 ┌──────────┐   ┌──────────┐   ┌──────────┐
 │ Broker 6  │   │ Broker 7  │   │ Broker 1  │
 │ ╳╳╳╳╳╳╳╳ │   │ P0 ★ LEAD │   │ P0 Follow │
 │ ╳╳ DEAD ╳ │   │ P1 ★ Lead │   │ P2 ★ Lead │
 │ ╳╳╳╳╳╳╳╳ │   │ P6 Follow │   │ P1 Follow │
 └──────────┘   └──────────┘   └──────────┘

 Step by step:
 ┌────────────────────────────────────────────────────────────────────────────┐
 │                                                                            │
 │  1. Controller detects Broker 6 is unresponsive (heartbeat timeout)        │
 │                                                                            │
 │  2. P0 lost its leader (Broker 6)                                         │
 │     → Controller picks NEXT IN ISR: Broker 7 becomes new P0 leader        │
 │     → P0 ISR shrinks: {6, 7, 1} → {7, 1}                                │
 │                                                                            │
 │  3. P5 and P6 lost a follower (Broker 6)                                  │
 │     → Leaders unchanged (Broker 4 and Broker 5 still alive)               │
 │     → P5 ISR: {4, 5, 6} → {4, 5}                                         │
 │     → P6 ISR: {5, 6, 7} → {5, 7}                                         │
 │                                                                            │
 │  4. URP (Under-Replicated Partitions) = 3                                  │
 │     P0, P5, P6 each have 2 ISR instead of 3                              │
 │     Data is SAFE (2 copies remain), but durability is reduced             │
 │                                                                            │
 │  5. Producers continue writing (min.insync.replicas=2, ISR=2 → OK)        │
 │     If ANOTHER broker dies → ISR could drop below 2 → writes FAIL         │
 │                                                                            │
 │  6. Broker 6 comes back online                                            │
 │     → Fetches missed data from current leaders                            │
 │     → Catches up → re-joins ISR for P0, P5, P6                           │
 │     → URP drops back to 0                                                 │
 │     → Preferred leader election: P0 leader moves back to Broker 6         │
 │                                                                            │
 └────────────────────────────────────────────────────────────────────────────┘


═══════════════════════════════════════════════════════════════════════════════════
 LEVEL 7 — CONSUMERS READING FROM THE CLUSTER
═══════════════════════════════════════════════════════════════════════════════════

 Topic: "demo-clicks" (7 partitions across 7 brokers)

 Consumer Group: "click-analytics" (3 consumers)

 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                                                                             │
 │  Broker 6        Broker 7        Broker 1        Broker 2                  │
 │  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐              │
 │  │ P0 ★     │   │ P1 ★     │   │ P2 ★     │   │ P3 ★     │              │
 │  └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘              │
 │       │              │              │              │                      │
 │       ▼              ▼              ▼              ▼                      │
 │  ┌──────────────────────────┐  ┌──────────────────────────┐              │
 │  │   Consumer C1             │  │   Consumer C2             │              │
 │  │   ← P0 (from B6)         │  │   ← P2 (from B1)         │              │
 │  │   ← P1 (from B7)         │  │   ← P3 (from B2)         │              │
 │  │   ← P6 (from B5)         │  │   ← P4 (from B3)         │              │
 │  └──────────────────────────┘  └──────────────────────────┘              │
 │                                                                             │
 │  Broker 4        Broker 5                                                  │
 │  ┌──────────┐   ┌──────────┐                                              │
 │  │ P5 ★     │   │ P6 ★     │                                              │
 │  └────┬─────┘   └────┬─────┘                                              │
 │       │              │                                                      │
 │       ▼              ▼                                                      │
 │  ┌──────────────────────────┐                                              │
 │  │   Consumer C3             │    7 partitions ÷ 3 consumers =             │
 │  │   ← P5 (from B4)         │    C1 gets 3, C2 gets 2, C3 gets 2         │
 │  └──────────────────────────┘                                              │
 │                                                                             │
 │  Each consumer talks DIRECTLY to the broker that is the partition leader.  │
 │  No routing layer — the client knows the leader map from metadata.         │
 │                                                                             │
 └─────────────────────────────────────────────────────────────────────────────┘

 If Consumer C2 crashes:
 ───────────────────────
 Rebalance! Partitions P2, P3, P4 are redistributed:
   C1: P0, P1, P6, P2  (gains P2)
   C3: P5, P3, P4      (gains P3, P4)


═══════════════════════════════════════════════════════════════════════════════════
 PUTTING IT ALL TOGETHER — HIERARCHY SUMMARY
═══════════════════════════════════════════════════════════════════════════════════

 Cluster "my-kafka-cluster"
 │
 ├── Controller Quorum [Broker 1, 2, 3] ← metadata + leader election via Raft
 │
 ├── Broker 1 (port 9092)  ─── stores: clicks/P0, clicks/P1, clicks/P2★
 ├── Broker 2 (port 9192)  ─── stores: clicks/P1, clicks/P2, clicks/P3★
 ├── Broker 3 (port 9292)  ─── stores: clicks/P2, clicks/P3, clicks/P4★ (ctrl-only no lead here)
 ├── Broker 4 (port 9392)  ─── stores: clicks/P3, clicks/P4, clicks/P5★
 ├── Broker 5 (port 9492)  ─── stores: clicks/P4, clicks/P5, clicks/P6★
 ├── Broker 6 (port 9592)  ─── stores: clicks/P0★, clicks/P5, clicks/P6
 ├── Broker 7 (port 9692)  ─── stores: clicks/P0, clicks/P1★, clicks/P6
 │
 ├── Topic "demo-clicks"
 │   ├── Partition 0  →  Replicas [6,7,1]  →  Leader: B6  →  Log: [0..N]
 │   ├── Partition 1  →  Replicas [7,1,2]  →  Leader: B7  →  Log: [0..N]
 │   ├── Partition 2  →  Replicas [1,2,3]  →  Leader: B1  →  Log: [0..N]
 │   ├── Partition 3  →  Replicas [2,3,4]  →  Leader: B2  →  Log: [0..N]
 │   ├── Partition 4  →  Replicas [3,4,5]  →  Leader: B3  →  Log: [0..N]
 │   ├── Partition 5  →  Replicas [4,5,6]  →  Leader: B4  →  Log: [0..N]
 │   └── Partition 6  →  Replicas [5,6,7]  →  Leader: B5  →  Log: [0..N]
 │
 ├── Topic "demo-orders"
 │   ├── Partition 0  →  Replicas [...]   →  Leader: ...  →  Log: [0..N]
 │   └── ... (6 partitions, similarly spread)
 │
 └── Consumer Groups
     ├── "click-analytics"  →  3 consumers  →  each reads from partition leaders
     ├── "order-processing" →  6 consumers  →  one per partition
     └── "search-indexer"   →  1 consumer   →  reads all partitions


 DATA FLOW IN ONE SENTENCE:
 ──────────────────────────
 Producer → picks partition via hash(key) → sends to LEADER broker for that
 partition → leader appends to log → followers fetch → all ISR caught up →
 high watermark advances → consumer group members pull from leader brokers →
 each consumer processes its assigned partitions → commits offset → done.
```

### Core Terminology

```
┌──────────────────────────────────────────────────────────────────────────┐
│                       KAFKA ANATOMY                                       │
├──────────────┬───────────────────────────────────────────────────────────┤
│ Broker       │ A single Kafka server. Stores data, serves clients.      │
│              │ A cluster has 3-30+ brokers.                              │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Topic        │ A named feed/category of events. Like a DB table.        │
│              │ e.g. "orders", "user-clicks", "payments"                  │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Partition    │ An ordered, immutable sequence of records within a topic. │
│              │ Unit of parallelism. Spread across brokers.               │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Offset       │ 64-bit integer. Unique position of a message in a        │
│              │ partition. Monotonically increasing. Never reused.        │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Record       │ Key + Value + Timestamp + Headers. The actual event.     │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Leader       │ The broker owning the primary replica of a partition.    │
│              │ All reads & writes go through the leader.                │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Follower     │ Broker holding a replica. Pulls from leader.             │
│              │ Can serve reads (KIP-392, Kafka 2.4+).                   │
├──────────────┼───────────────────────────────────────────────────────────┤
│ ISR          │ In-Sync Replicas. Followers that are caught up with the  │
│              │ leader. Only ISR members can be elected leader.           │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Consumer     │ Set of consumers sharing a group.id. Each partition is   │
│ Group        │ consumed by exactly ONE consumer in the group.            │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Controller   │ Special broker managing cluster metadata, leader         │
│              │ election. Uses KRaft (Raft consensus) since Kafka 3.3+.  │
├──────────────┼───────────────────────────────────────────────────────────┤
│ Retention    │ How long messages are kept. Time-based (default 7d),     │
│              │ size-based, or compaction-based.                          │
└──────────────┴───────────────────────────────────────────────────────────┘
```

### Topic, Partitions, and Offsets

```
Topic: "orders"   (Replication Factor = 3, Partitions = 4)

  Partition 0:  [0][1][2][3][4][5][6][7][8]──► writes append here
  Partition 1:  [0][1][2][3][4][5]──►
  Partition 2:  [0][1][2][3][4][5][6][7][8][9][10]──►
  Partition 3:  [0][1][2][3]──►
                 ▲
                 │
              Offsets (position in partition, 0-indexed, immutable)

  Key decides partition:   hash(order_id) % num_partitions = target partition
  No key → round-robin:   messages distributed evenly across partitions

  GUARANTEES:
  ✓ Order within a partition (offset 5 always before offset 6)
  ✗ NO global order across partitions
  ✓ Same key → same partition → ordered processing for that key
```

### Consumer Groups — The Key to Parallelism

```
Topic: "orders" with 4 partitions

CONSUMER GROUP A (Order Processing — 4 consumers):

  Partition 0 ──────► Consumer A-1
  Partition 1 ──────► Consumer A-2
  Partition 2 ──────► Consumer A-3
  Partition 3 ──────► Consumer A-4

  Each partition → exactly ONE consumer in the group
  Each consumer → can handle 1+ partitions
  Max useful consumers = number of partitions (extra consumers sit idle)

CONSUMER GROUP B (Analytics — 2 consumers):

  Partition 0, 1 ──────► Consumer B-1
  Partition 2, 3 ──────► Consumer B-2

  Fewer consumers → each handles more partitions

CONSUMER GROUP C (Search Indexing — 1 consumer):

  Partition 0, 1, 2, 3 ──────► Consumer C-1

  Single consumer handles all partitions

┌─────────────────────────────────────────────────────────────────────┐
│  CRITICAL INTERVIEW INSIGHT:                                         │
│  Consumer groups are INDEPENDENT. Group A, B, C all see ALL          │
│  messages. Each group maintains its own offset per partition.        │
│  This is how Kafka enables multiple downstream systems to            │
│  independently consume the same data stream.                         │
└─────────────────────────────────────────────────────────────────────┘
```

### Can a Consumer Take Multiple (or ALL) Partitions?

**Yes.** A single consumer can handle multiple partitions — even ALL partitions of a topic.

```
THE RULE:
─────────
  Within a consumer group, each PARTITION is assigned to exactly ONE consumer.
  But each CONSUMER can be assigned ONE or MANY partitions.

  1 partition  → at most 1 consumer in the group
  1 consumer   → can handle 1, 2, 5, or ALL partitions


EVERY POSSIBLE SCENARIO (Topic: "demo-clicks", 7 partitions):
──────────────────────────────────────────────────────────────

CASE 1:  7 consumers in group (ideal — 1:1 mapping)
─────────────────────────────────────────────────────
  P0 ──► C1       Each consumer handles exactly 1 partition.
  P1 ──► C2       Maximum parallelism.
  P2 ──► C3       Each consumer only processes ~14% of total traffic.
  P3 ──► C4
  P4 ──► C5
  P5 ──► C6
  P6 ──► C7

CASE 2:  3 consumers in group (common — consumers handle 2-3 partitions each)
──────────────────────────────────────────────────────────────────────────────
  P0, P1, P6  ──► C1    (3 partitions)
  P2, P3      ──► C2    (2 partitions)
  P4, P5      ──► C3    (2 partitions)

  7 partitions ÷ 3 consumers → some get 3, some get 2.
  Each consumer processes messages from ALL its assigned partitions
  by polling and receiving a batch that may contain records from
  multiple partitions in a single poll() call.

CASE 3:  1 consumer in group (handles ALL partitions)
──────────────────────────────────────────────────────
  P0, P1, P2, P3, P4, P5, P6  ──► C1    (all 7 partitions)

  Perfectly valid! One consumer reads the ENTIRE topic.
  Use case: small-volume topics, or simple aggregation services.
  Downside: no parallelism, single point of failure for processing.

CASE 4:  10 consumers in group (more consumers than partitions)
───────────────────────────────────────────────────────────────
  P0 ──► C1
  P1 ──► C2
  P2 ──► C3
  P3 ──► C4
  P4 ──► C5
  P5 ──► C6
  P6 ──► C7
         C8  ──► IDLE (no partition assigned)
         C9  ──► IDLE (no partition assigned)
         C10 ──► IDLE (no partition assigned)

  3 consumers sit idle doing NOTHING.
  They waste resources but stay as hot standby —
  if C1 crashes, C8 immediately picks up P0 (fast failover).


WHY THE "ONE PARTITION → ONE CONSUMER" RULE?
─────────────────────────────────────────────
  If two consumers in the SAME group read the same partition:
  → Both would process message at offset 5
  → Both would try to commit offset 5
  → Duplicate processing, conflicting offset commits, chaos

  Kafka avoids this by design:
  1 partition = 1 consumer per group = guaranteed ordering + no duplicates

  BUT: Two consumers in DIFFERENT groups CAN read the same partition.
  That's the whole point of consumer groups — independent processing.


DYNAMIC REBALANCING — WHAT HAPPENS WHEN CONSUMERS JOIN/LEAVE:
─────────────────────────────────────────────────────────────

  Start: 3 consumers, 7 partitions
  ┌──────────────────────────────────────────────┐
  │  C1: P0, P1, P6   C2: P2, P3   C3: P4, P5  │
  └──────────────────────────────────────────────┘
                       │
          C2 crashes   ▼
                       │
  ┌──────────────────────────────────────────────┐
  │  C1: P0, P1, P2, P6    C3: P3, P4, P5       │  ← P2, P3 redistributed
  └──────────────────────────────────────────────┘
                       │
     C4 joins group    ▼
                       │
  ┌──────────────────────────────────────────────┐
  │  C1: P0, P1   C3: P4, P5   C4: P2, P6, P3  │  ← rebalanced across 3
  └──────────────────────────────────────────────┘

  Rebalance triggers:
  • Consumer joins the group
  • Consumer leaves (crash or graceful shutdown)
  • New partition added to the topic
  • Consumer heartbeat timeout (session.timeout.ms)

  During rebalance: ALL consumers in the group PAUSE briefly.
  This is why large consumer groups can have rebalance storms.
  Kafka 3.1+ supports "cooperative incremental rebalance" to minimize pauses.


INTERVIEW QUESTION: "Can one consumer read all partitions of a topic?"
──────────────────────────────────────────────────────────────────────
  Answer: YES. If a consumer group has only 1 consumer,
  that consumer is automatically assigned ALL partitions.
  This is common for:
  - Low-throughput topics
  - Global aggregation (need to see all data in one place)
  - Development/debugging
  - Kafka Streams tasks with single instance

  You can also MANUALLY assign partitions using assign() instead of
  subscribe(), bypassing the group coordinator entirely:

    consumer.assign(Arrays.asList(
        new TopicPartition("demo-clicks", 0),
        new TopicPartition("demo-clicks", 1),
        new TopicPartition("demo-clicks", 2),
        // ... any subset or all partitions
    ));

  With assign(), you get full control but lose automatic rebalancing.
```

---

## 3. Storage Engine — The Log

This is what makes Kafka unique. From the official Kafka design documentation:

> "Rather than maintain as much as possible in-memory and flush it all out to the filesystem in a panic when we run out of space, we invert that."

### Segment-Based Storage

```
A single Partition on disk:

topic-orders/partition-0/
  ├── 00000000000000000000.log        ← Segment file (offsets 0–9999)
  ├── 00000000000000000000.index      ← Sparse offset → position index
  ├── 00000000000000000000.timeindex  ← Timestamp → offset index
  ├── 00000000000000010000.log        ← Next segment (offsets 10000–19999)
  ├── 00000000000000010000.index
  ├── 00000000000000010000.timeindex
  ├── 00000000000000020000.log        ← Active segment (currently writing)
  ├── 00000000000000020000.index
  └── 00000000000000020000.timeindex

Each segment file is named by its first offset.
Only the ACTIVE segment is open for writes.
Older segments are immutable → safe for concurrent reads.
```

### Why Sequential I/O Matters

```
RANDOM I/O vs SEQUENTIAL I/O (from Kafka design doc):

  Random writes to disk:    ~100 KB/sec   (10ms seek per operation)
  Sequential writes to disk: ~600 MB/sec   (6000x faster!)
  Sequential disk writes:    competitive with network speed
  Sequential disk reads:     can be faster than random memory access!

┌──────────────────────────────────────────────────────────────────────┐
│                   KAFKA'S WRITE PATH                                  │
│                                                                      │
│  Producer ─────► Broker ─────► OS Page Cache ─────► Disk             │
│                    │                                                  │
│                    │  1. Append to active segment (sequential write)  │
│                    │  2. OS page cache handles buffering              │
│                    │  3. No explicit fsync per message (fast!)        │
│                    │  4. OS flushes dirty pages to disk async         │
│                                                                      │
│  READ PATH (caught-up consumer):                                     │
│                                                                      │
│  Consumer ◄── Broker ◄── OS Page Cache ◄── (data already in cache)   │
│                    │                                                  │
│                    │  Zero disk reads! Served entirely from cache.    │
│                                                                      │
│  READ PATH (lagging consumer):                                       │
│                                                                      │
│  Consumer ◄── Broker ◄── sendfile() ◄── Disk                         │
│                    │                                                  │
│                    │  Zero-copy transfer: disk → NIC buffer           │
│                    │  Skips user-space entirely                       │
│                    │  Standard path: disk → pagecache → user-space    │
│                    │                 → socket buffer → NIC (4 copies) │
│                    │  sendfile path: disk → pagecache → NIC (2 copies)│
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Record Batch Format

```
┌────────────────────────────────────────────────────────────────────┐
│                      RECORD BATCH (v2+)                            │
├────────────────────────────────────────────────────────────────────┤
│ Base Offset (8 bytes)                                              │
│ Batch Length (4 bytes)                                              │
│ Partition Leader Epoch (4 bytes)                                   │
│ Magic (1 byte) — format version                                    │
│ CRC (4 bytes)                                                      │
│ Attributes (2 bytes) — compression, timestamp type, transactional  │
│ Last Offset Delta (4 bytes)                                        │
│ First Timestamp (8 bytes)                                          │
│ Max Timestamp (8 bytes)                                            │
│ Producer ID (8 bytes) — for idempotent/transactional               │
│ Producer Epoch (2 bytes)                                           │
│ First Sequence (4 bytes)                                           │
│ Number of Records (4 bytes)                                        │
│ ┌──────────────────────────────────────────────────────────────┐   │
│ │ Record 0: length | attributes | timestampDelta |             │   │
│ │           offsetDelta | key | value | headers               │   │
│ ├──────────────────────────────────────────────────────────────┤   │
│ │ Record 1: ...                                                │   │
│ ├──────────────────────────────────────────────────────────────┤   │
│ │ Record N: ...                                                │   │
│ └──────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────┘

CRITICAL: The exact same binary format is used by producer, broker,
and consumer. Records are transferred WITHOUT re-serialization.
This is a key reason Kafka is so fast.
```

---

## 4. Producers Deep Dive

### Producer Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        KAFKA PRODUCER INTERNALS                           │
│                                                                          │
│  Application Thread(s)               Sender Thread (background)          │
│  ┌─────────────────────┐             ┌──────────────────────────┐       │
│  │ producer.send(       │             │                          │       │
│  │   record)            │             │  Drains batches from     │       │
│  │      │               │             │  accumulator when:       │       │
│  │      ▼               │             │  • batch.size reached    │       │
│  │ ┌──────────┐         │             │  • linger.ms elapsed     │       │
│  │ │Serializer│         │             │  • memory pressure       │       │
│  │ │ key+value│         │             │                          │       │
│  │ └────┬─────┘         │             │  Sends ProduceRequest    │       │
│  │      ▼               │             │  to broker leaders       │       │
│  │ ┌──────────┐         │             │                          │       │
│  │ │Partitioner│        │     drain   │  Handles retries,        │       │
│  │ │hash(key) │         │   ────────► │  acks, timeouts          │       │
│  │ │% partitions│       │             │                          │       │
│  │ └────┬─────┘         │             └──────────────────────────┘       │
│  │      ▼               │                       │                        │
│  │ ┌──────────────────┐ │                       ▼                        │
│  │ │ RecordAccumulator│ │             ┌─────────────────────┐            │
│  │ │                  │ │             │   Broker (Leader)    │            │
│  │ │ P0: [batch]      │ │             │   Writes to log     │            │
│  │ │ P1: [batch]      │ │             │   Replicates to ISR │            │
│  │ │ P2: [batch]      │ │             │   Returns ack       │            │
│  │ └──────────────────┘ │             └─────────────────────┘            │
│  └─────────────────────┘                                                 │
│                                                                          │
│  buffer.memory = 32 MB (default)   — total memory for buffering          │
│  batch.size = 16 KB (default)      — target batch size per partition     │
│  linger.ms = 0 (default)           — wait time to fill batch             │
│  max.in.flight.requests.per.connection = 5 (default)                     │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Acknowledgement Modes (acks)

```
acks=0  "Fire and Forget"
─────────────────────────
  Producer ──send──► Broker        No response waited.
                                   Fastest. Risk of data loss.
  Latency: ~0ms                    Use: Metrics, logs (lossy OK)


acks=1  "Leader Acknowledged"
─────────────────────────────
  Producer ──send──► Broker Leader ──ack──► Producer
                       │
                       │  Message written to leader's log.
                       │  Followers may not have it yet.
                       │  If leader dies before replication → DATA LOST.
  Latency: ~1-5ms     Use: Most use cases (good tradeoff)


acks=all (-1)  "All ISR Acknowledged"
──────────────────────────────────────
  Producer ──send──► Leader ──replicate──► Follower 1 ──┐
                       │                  Follower 2 ──┤
                       │                               │
                       │◄──── all ISR ack ─────────────┘
                       │
                       └──ack──► Producer

  Latency: ~5-20ms    STRONGEST guarantee.
                       Combined with min.insync.replicas=2 → 
                       no data loss unless ALL replicas fail simultaneously.

┌────────────────────────────────────────────────────────────────────────┐
│  INTERVIEW GOTCHA:                                                      │
│  acks=all does NOT mean all replicas. It means all IN-SYNC replicas.   │
│  If ISR shrinks to just the leader, acks=all = acks=1.                 │
│  ALWAYS pair acks=all with min.insync.replicas=2 for true durability.  │
└────────────────────────────────────────────────────────────────────────┘
```

### Partitioning Strategies

```
1. KEY-BASED (default with key):
   hash(key) % num_partitions = target partition
   
   ✓ Same key always → same partition → ordering guaranteed
   ✗ Hot keys → hot partitions (skew)
   
   Use: order_id, user_id — when ordering per entity matters

2. ROUND-ROBIN (default without key):
   Messages distributed evenly across partitions
   
   ✓ Even distribution
   ✗ No ordering guarantee for related messages
   
   Use: Independent events, logging

3. STICKY PARTITIONING (default in Kafka 2.4+):
   Batches go to a single partition, then rotate on batch completion
   
   ✓ Better batching → fewer requests → higher throughput
   ✗ Slight imbalance during low traffic
   
   Use: Default for null keys since Kafka 2.4

4. CUSTOM PARTITIONER:
   Implement org.apache.kafka.clients.producer.Partitioner
   
   Use: Geo-routing, priority lanes, weighted distribution
```

---

## 5. Consumers Deep Dive

### Consumer Group Rebalancing

This is one of the most commonly asked hard-level topics.

```
WHEN DOES REBALANCE HAPPEN?
  • Consumer joins group (new instance, scaling up)
  • Consumer leaves group (shutdown, crash)
  • Consumer fails heartbeat (max.poll.interval.ms exceeded)
  • Partition count changes (topic altered)
  • Subscription pattern matches new topic

REBALANCE PROTOCOL — EAGER (Legacy):
──────────────────────────────────────
  Step 1: ALL consumers revoke ALL partitions (STOP processing)
  Step 2: Group coordinator assigns partitions to consumers
  Step 3: Consumers resume with new assignments

  Timeline:
  C1: [processing]──[STOP]──────────[resume P0,P1]
  C2: [processing]──[STOP]──────────[resume P2,P3]
  C3: (new)─────────[join]──────────[resume P4,P5]
                      ▲                    ▲
                      │                    │
                 FULL STOP            FULL RESUME
                 (downtime!)

  Problem: EVERY consumer stops even if its assignment doesn't change.
  With 100 partitions and 10 consumers, even adding 1 consumer
  causes ALL 10 to stop → seconds to minutes of processing pause.


REBALANCE PROTOCOL — COOPERATIVE INCREMENTAL (Kafka 2.4+):
────────────────────────────────────────────────────────────
  Step 1: Coordinator computes new assignment
  Step 2: Only AFFECTED consumers revoke SPECIFIC partitions
  Step 3: Revoked partitions reassigned in next rebalance round
  Step 4: Unaffected consumers NEVER stop

  Timeline:
  C1: [processing P0,P1]──────────────[processing P0,P1] (no change!)
  C2: [processing P2,P3,P4]──[revoke P4]──[processing P2,P3]
  C3: (new)─────────[join]──────────────────[resume P4]
                                 ▲
                                 │
                          ONLY P4 moved, minimal disruption

  partition.assignment.strategy=
    org.apache.kafka.clients.consumer.CooperativeStickyAssignor

┌────────────────────────────────────────────────────────────────────────┐
│  INTERVIEW GOTCHA — REBALANCE SPIRAL:                                   │
│                                                                        │
│  If a consumer's processing exceeds max.poll.interval.ms:              │
│  1. Consumer sends LeaveGroup                                          │
│  2. Rebalance triggered                                                │
│  3. CooperativeStickyAssignor re-assigns same partitions back          │
│  4. Consumer fetches → slow again → LeaveGroup again                   │
│  5. INFINITE LOOP → zero progress                                      │
│                                                                        │
│  Fix: Increase max.poll.interval.ms, reduce max.poll.records,          │
│       or move slow processing to a separate thread pool.               │
└────────────────────────────────────────────────────────────────────────┘
```

### Offset Management

```
┌──────────────────────────────────────────────────────────────────┐
│                    OFFSET COMMIT STRATEGIES                        │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  AUTO COMMIT (enable.auto.commit=true, auto.commit.interval.ms)  │
│  ─────────────────────────────────────────────────────────────── │
│  Consumer auto-commits last polled offset periodically.          │
│  ✗ Can lose messages (commit before processing)                  │
│  ✗ Can duplicate messages (crash before commit)                  │
│  Use: Low-stakes analytics, logging                              │
│                                                                  │
│  MANUAL SYNC COMMIT (commitSync())                               │
│  ─────────────────────────────────                               │
│  Blocks until broker confirms offset commit.                     │
│  ✓ Guaranteed commit                                             │
│  ✗ Higher latency (blocks consumer thread)                       │
│  Use: When exactly-once matters (with idempotent processing)     │
│                                                                  │
│  MANUAL ASYNC COMMIT (commitAsync())                             │
│  ──────────────────────────────────                              │
│  Non-blocking, callback on completion.                           │
│  ✓ No blocking                                                   │
│  ✗ Retry on failure is tricky (offset ordering)                  │
│  Use: High-throughput pipelines                                  │
│                                                                  │
│  BEST PRACTICE: commitAsync() in normal loop + commitSync()      │
│  in shutdown/error handler.                                      │
│                                                                  │
│  Offsets are stored in internal topic: __consumer_offsets          │
│  (50 partitions, compacted, replication factor 3)                │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 6. Replication & Fault Tolerance

### ISR (In-Sync Replicas) — The Core of Kafka's Reliability

```
Partition 0 (Replication Factor = 3):

  Broker 0 (LEADER):        [0][1][2][3][4][5][6][7]  ← Latest
  Broker 1 (FOLLOWER/ISR):  [0][1][2][3][4][5][6][7]  ← Caught up ✓
  Broker 2 (FOLLOWER/ISR):  [0][1][2][3][4][5][6]     ← Slightly behind ✓
  Broker 3 (FOLLOWER):      [0][1][2][3]               ← Too far behind ✗ (removed from ISR)

  ISR = {Broker 0, Broker 1, Broker 2}

  How follower stays in ISR:
  1. Sends fetch requests to leader (just like a consumer)
  2. Must stay within replica.lag.time.max.ms (default 30s) of leader
  3. Must maintain active session with controller

  HWM (High Water Mark):
  ─────────────────────
  The offset up to which ALL ISR members have replicated.
  Consumers can ONLY read up to HWM (not beyond).

  Broker 0:  [0][1][2][3][4][5][6][7]
                                  ▲   ▲
                                  │   └── LEO (Log End Offset) = 7
                                  └── HWM = 6 (all ISR have up to 6)

  Consumer can read offsets 0-6 only. Offset 7 is uncommitted.
```

### Replicas vs ISR vs Leader — The Full Picture

```
Replicas ⊃ ISR ⊃ Leader

┌─────────────────────────────────────────────────────────────┐
│                     ALL REPLICAS (RF=3)                       │
│                                                             │
│   ┌───────────────────────────────────────────────────┐     │
│   │              ISR (In-Sync Replicas)                │     │
│   │                                                   │     │
│   │   ┌─────────────┐   ┌─────────────┐              │     │
│   │   │   LEADER    │   │  FOLLOWER   │              │     │
│   │   │  Broker 0   │   │  Broker 1   │              │     │
│   │   │             │   │  (caught up)│              │     │
│   │   │ All reads & │   │             │              │     │
│   │   │ writes here │   │             │              │     │
│   │   └─────────────┘   └─────────────┘              │     │
│   │                                                   │     │
│   └───────────────────────────────────────────────────┘     │
│                                                             │
│   ┌─────────────┐                                           │
│   │  FOLLOWER   │  ← Replica but NOT in ISR                │
│   │  Broker 2   │  ← Too far behind (lagging)              │
│   │  (lagging)  │  ← NOT eligible for leader election       │
│   └─────────────┘                                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────┬───────────────────────────────────────────────────────┐
│ Concept     │ What it means                                         │
├─────────────┼───────────────────────────────────────────────────────┤
│ Replica     │ ANY copy of a partition (leader + all followers).     │
│             │ Fixed at topic creation time (replication.factor).    │
│             │ Does NOT change unless you manually reassign.        │
├─────────────┼───────────────────────────────────────────────────────┤
│ ISR         │ Subset of replicas that are caught up with leader.   │
│             │ DYNAMIC — shrinks/grows based on follower lag.       │
│             │ Only ISR members can become leader.                  │
│             │ Determined by: replica.lag.time.max.ms (default 30s) │
├─────────────┼───────────────────────────────────────────────────────┤
│ Leader      │ ONE replica per partition that handles reads/writes.  │
│             │ Chosen by Controller from ISR members.               │
│             │ Changes when current leader fails (leader election). │
├─────────────┼───────────────────────────────────────────────────────┤
│ Controller  │ The broker running KRaft that manages all metadata,  │
│             │ leader elections, and ISR changes.                    │
│             │ Elected via Raft consensus among controller quorum.  │
└─────────────┴───────────────────────────────────────────────────────┘
```

### How Is the Leader Decided?

The leader is chosen by the **Controller**. Here's the full lifecycle:

```
STEP 1: TOPIC CREATION
─────────────────────
  You create topic "orders" with partitions=3, replication.factor=3
  
  Controller decides placement using rack-awareness + load balancing:
  
  Partition 0:  Leader=Broker0,  Replicas=[Broker0, Broker1, Broker2]
  Partition 1:  Leader=Broker1,  Replicas=[Broker1, Broker2, Broker0]
  Partition 2:  Leader=Broker2,  Replicas=[Broker2, Broker0, Broker1]
                  ▲
                  │
  Leaders are SPREAD across brokers (round-robin by default)
  so no single broker gets all the write load.
  The first broker in the replica list is the "preferred leader."


STEP 2: NORMAL OPERATION
─────────────────────────
  Partition 0 on 3 brokers:

  Broker 0 (LEADER):    [0][1][2][3][4][5][6][7][8]  ← LEO=8
  Broker 1 (FOLLOWER):  [0][1][2][3][4][5][6][7][8]  ← caught up, IN ISR ✓
  Broker 2 (FOLLOWER):  [0][1][2][3][4][5][6][7][8]  ← caught up, IN ISR ✓

  ISR = {Broker0, Broker1, Broker2}   ← all 3 replicas are in sync


STEP 3: A FOLLOWER FALLS BEHIND
─────────────────────────────────
  Broker 2 has a long GC pause or disk I/O spike...

  Broker 0 (LEADER):    [0][1][2][3][4][5][6][7][8][9][10][11]  ← LEO=11
  Broker 1 (FOLLOWER):  [0][1][2][3][4][5][6][7][8][9][10][11]  ← caught up ✓
  Broker 2 (FOLLOWER):  [0][1][2][3][4][5][6][7]                 ← BEHIND ✗

  If Broker 2 hasn't fetched within replica.lag.time.max.ms (default 30s):
    → Leader REMOVES Broker 2 from ISR
    → ISR = {Broker0, Broker1}
    → Broker 2 is still a REPLICA, just not in-sync

  When Broker 2 catches up again:
    → Leader ADDS Broker 2 back to ISR
    → ISR = {Broker0, Broker1, Broker2}

  ┌──────────────────────────────────────────────────────────┐
  │  KEY INSIGHT: ISR is DYNAMIC. It shrinks and grows at    │
  │  runtime. Replicas are STATIC (set at topic creation).   │
  │  The leader tracks ISR. The Controller persists it.      │
  └──────────────────────────────────────────────────────────┘
```

### How Does a Follower Stay in ISR?

```
FOLLOWER's JOB:
  1. Continuously send Fetch requests to leader (same protocol as consumers)
  2. Append fetched records to its own log
  3. That's it. Followers are passive.

LEADER's JOB (ISR tracking):
  1. Track the last fetch time for each follower
  2. Track how far behind each follower's LEO is
  3. If a follower hasn't fetched within replica.lag.time.max.ms:
       → Remove from ISR, report to Controller
  4. If a previously removed follower catches up to leader's LEO:
       → Add back to ISR, report to Controller
```

### Leader Election — What Happens When a Broker Dies

```
SCENARIO: Broker 0 (leader of Partition 0) crashes

  BEFORE:
  ISR = {Broker 0 (leader), Broker 1, Broker 2}

  Step 1: Controller detects Broker 0 heartbeat timeout
  Step 2: Controller selects new leader from ISR
          New leader = Broker 1 (first in ISR, or most caught-up)
  Step 3: Controller updates metadata, notifies all brokers
  Step 4: Producers/Consumers discover new leader via metadata refresh

  AFTER:
  ISR = {Broker 1 (new leader), Broker 2}

  Timeline: ~milliseconds with KRaft (vs seconds with ZooKeeper)

UNCLEAN LEADER ELECTION (unclean.leader.election.enable):
─────────────────────────────────────────────────────────
  What if ALL ISR replicas are dead?

  Option A (default): Wait for an ISR member to recover
    ✓ No data loss    ✗ Partition unavailable until recovery

  Option B (unclean=true): Elect any available replica
    ✓ Partition available    ✗ COMMITTED MESSAGES MAY BE LOST

┌───────────────────────────────────────────────────────────────────┐
│  INTERVIEW QUESTION: "How does Kafka guarantee no data loss?"      │
│                                                                   │
│  Answer: acks=all + min.insync.replicas=2 +                       │
│          unclean.leader.election.enable=false                      │
│          + replication.factor=3                                    │
│                                                                   │
│  This ensures:                                                    │
│  • Writes require ack from ≥2 replicas                            │
│  • Leader election only from caught-up replicas                   │
│  • 3 copies of data across different brokers                      │
│  • Can tolerate 1 broker failure with no data loss                │
└───────────────────────────────────────────────────────────────────┘
```

### Kafka's Replication vs Majority Quorum

```
MAJORITY QUORUM (Raft, Paxos, ZAB):     KAFKA ISR MODEL:
────────────────────────────────────     ───────────────────────────
  N replicas, need (N/2)+1 votes          N replicas, ALL ISR must ack
  Tolerates f failures with 2f+1 nodes    Tolerates f failures with f+1 nodes
  
  To survive 1 failure: 3 replicas        To survive 1 failure: 2 replicas
  To survive 2 failures: 5 replicas       To survive 2 failures: 3 replicas
  
  ✓ Latency = fastest majority            ✓ Less replicas needed (less disk)
  ✗ More replicas needed                  ✗ Latency = slowest ISR member

Why Kafka chose ISR over majority vote:
  • Fewer replicas → less disk, less network, more throughput
  • Data-intensive workloads where storage cost matters
  • ISR shrinks dynamically — slow follower removed, doesn't block writes
  • Producer can choose to not wait for ack (acks=0 or acks=1)
```

### URP (Under-Replicated Partitions) — The #1 Monitoring Metric

```
URP = number of partitions where ISR count < total replica count

In other words: "How many partitions have at least one follower that fell behind?"

─────────────────────────────────────────────────────────────────

HEALTHY (URP = 0):

  Partition 0 (RF=3):
    Broker 0 (Leader):   offset 250
    Broker 1 (Follower): offset 250   ← caught up ✓
    Broker 2 (Follower): offset 250   ← caught up ✓
    ISR = [0, 1, 2]   → All replicas in sync

  URP for this partition = 0

─────────────────────────────────────────────────────────────────

UNHEALTHY (URP > 0):

  Partition 0 (RF=3):
    Broker 0 (Leader):   offset 250
    Broker 1 (Follower): offset 250   ← caught up ✓
    Broker 2 (Follower): offset 180   ← BEHIND! Removed from ISR ✗
    ISR = [0, 1]   → Only 2 of 3 replicas in sync

  URP for this partition = 1
  (Replica count=3, ISR count=2 → 3-2 = 1 under-replicated)

─────────────────────────────────────────────────────────────────

CLUSTER-WIDE URP:
  Sum of all partitions that have at least one under-replicated replica.
  Even if one partition has 2 followers behind, it counts as 1 URP.

  Topic "orders" (4 partitions, RF=3):
    Partition 0: ISR=[0,1,2] → OK
    Partition 1: ISR=[1,2]   → URP! (Broker 0 behind)
    Partition 2: ISR=[0,1,2] → OK
    Partition 3: ISR=[0,2]   → URP! (Broker 1 behind)

  Cluster URP = 2

─────────────────────────────────────────────────────────────────

WHY URP IS DANGEROUS:

  Scenario: Partition 1 has ISR=[1,2] (Broker 0 behind)

  If Broker 1 (leader) dies NOW:
    → New leader = Broker 2 (only remaining ISR member)
    → Broker 0 is behind → data gap possible
    → If Broker 2 ALSO dies → data loss!

  With URP=0 and RF=3: losing 1 broker = zero data loss, zero downtime
  With URP>0 and RF=3: losing 1 broker = MIGHT lose data

─────────────────────────────────────────────────────────────────

┌─────────────────────────────────────────────────────────────────┐
│  URP VALUE  │  SEVERITY  │  ACTION                              │
├─────────────┼────────────┼──────────────────────────────────────┤
│  0          │  Healthy   │  None                                │
│  > 0 brief │  Warning   │  Check: broker restart? GC pause?    │
│             │            │  Usually self-heals in seconds.      │
│  > 0 for   │  Critical  │  Investigate: disk failure? network? │
│  minutes    │            │  broker overloaded? uneven leaders?  │
│  = total    │  Outage    │  All followers down. Emergency.      │
│  partitions │            │  Check if broker fleet is alive.     │
└─────────────┴────────────┴──────────────────────────────────────┘

COMMON CAUSES OF URP > 0:
  • Slow disk on follower (I/O saturation)
  • Network partition between brokers
  • Long GC pause on follower JVM
  • Broker restart (temporarily out of ISR)
  • Uneven partition assignment (one broker overloaded)
  • OS page cache thrashing (too many partitions on one broker)

HOW TO MONITOR:
  • JMX: kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions
  • CLI: kafka-topics.sh --describe --under-replicated-partitions
  • Admin API: describeTopics() → check isr.size() < replicas.size()
  • Kafka UI: Topics → "Out of sync replicas" column (0 = good)
  • Alert if URP > 0 for more than 5 minutes in production

─────────────────────────────────────────────────────────────────

INTERVIEW TIP:
  "What's the single most important metric to monitor in Kafka?"

  Answer: Under-Replicated Partitions (URP).
  URP > 0 means data durability is at risk.
  Combined with acks=all + min.insync.replicas=2,
  URP > 0 can cause producers to FAIL (NotEnoughReplicas)
  if ISR drops below min.insync.replicas threshold.
```

### Reading the Replicas Column in Kafka UI (Real 7-Broker Example)

```
Topic: demo-clicks   (7 partitions, RF=3, 7 brokers)

Kafka UI shows:

┌───────────┬─────────────┬───────────────┬────────────┬───────────────┐
│ Partition │ Replicas    │ First Offset  │ Next Off   │ Message Count │
├───────────┼─────────────┼───────────────┼────────────┼───────────────┤
│    0      │  6, 7, 1    │      0        │     0      │      0        │
│    1      │  7, 1, 2    │      0        │     0      │      0        │
│    2      │  1, 2, 3    │      0        │     0      │      0        │
│    3      │  2, 3, 4    │      0        │     0      │      0        │
│    4      │  3, 4, 5    │    100        │   100      │      0        │
│    5      │  4, 5, 6    │    100        │   200      │    100        │
│    6      │  5, 6, 7    │      0        │   100      │    100        │
└───────────┴─────────────┴───────────────┴────────────┴───────────────┘

WHAT "REPLICAS" MEANS:
──────────────────────
  Replicas = the list of broker IDs that hold a COPY of this partition.
  RF=3 → every partition has 3 copies on 3 different brokers.

  Partition 0:  Replicas = [6, 7, 1]
  → Broker 6 has a copy
  → Broker 7 has a copy
  → Broker 1 has a copy

  That's 3 independent copies of the exact same data.

HOW TO READ THE FIRST NUMBER:
─────────────────────────────
  The FIRST broker in the replica list is the "preferred leader."
  This broker handles ALL reads and writes for that partition.

  P0: [6, 7, 1]  → Broker 6 is the leader    (7 and 1 are followers)
  P1: [7, 1, 2]  → Broker 7 is the leader    (1 and 2 are followers)
  P2: [1, 2, 3]  → Broker 1 is the leader    (2 and 3 are followers)
  P3: [2, 3, 4]  → Broker 2 is the leader    (3 and 4 are followers)
  P4: [3, 4, 5]  → Broker 3 is the leader    (4 and 5 are followers)
  P5: [4, 5, 6]  → Broker 4 is the leader    (5 and 6 are followers)
  P6: [5, 6, 7]  → Broker 5 is the leader    (6 and 7 are followers)

  Notice: leaders are SPREAD across all 7 brokers → no single bottleneck.
  The controller assigns replicas using a round-robin shift pattern.

WHAT HAPPENS INSIDE:
────────────────────
  Partition 0:   Replicas = [6, 7, 1]

  Broker 6 (LEADER)        Broker 7 (FOLLOWER)      Broker 1 (FOLLOWER)
  ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
  │  Partition 0      │     │  Partition 0      │     │  Partition 0      │
  │  [0][1][2][3][4]  │     │  [0][1][2][3][4]  │     │  [0][1][2][3][4]  │
  │  ★ All writes     │     │  Fetches from ★   │     │  Fetches from ★   │
  │  ★ All reads      │     │  Stays in sync    │     │  Stays in sync    │
  └──────────────────┘     └──────────────────┘     └──────────────────┘
         ▲                        │                        │
         │←── fetch request ──────┘                        │
         │←── fetch request ───────────────────────────────┘

  Followers pull data from the leader (same Fetch API as consumers).

WHY REPLICAS SHIFT BY 1 BROKER:
───────────────────────────────
  P0: [6, 7, 1]      ← starts at Broker 6
  P1: [7, 1, 2]      ← shifts by 1
  P2: [1, 2, 3]      ← shifts by 1
  P3: [2, 3, 4]      ← shifts by 1
  ...

  This ensures:
  1. Leaders are evenly distributed (each broker leads ~same number)
  2. Replicas are spread out (no two partitions have identical placement)
  3. If one broker dies, the impact is spread across many brokers
     (not all partitions lose their leader at once)

THE OTHER COLUMNS:
──────────────────
  First Offset:   Lowest available offset (data before this was deleted by retention)
  Next Offset:    Next offset that will be assigned to a new message (= end of log)
  Message Count:  Next Offset - First Offset = messages currently retained

  Example — Partition 5:  First=100, Next=200, Count=100
  → Offsets 0-99 were deleted (1-hour retention)
  → Offsets 100-199 are available (100 messages)
  → Next message will get offset 200

IN SYNC REPLICAS (top of the page):
────────────────────────────────────
  "In Sync Replicas: 21 of 21"
  = 7 partitions × 3 replicas = 21 total replica copies
  = ALL 21 are caught up with their leaders
  = URP = 0 → perfectly healthy

  If a broker goes down:
  "In Sync Replicas: 18 of 21"
  = 3 replicas lost sync (the ones on the dead broker)
  = URP = 3 (those 3 partitions are under-replicated)
```

---

## 7. KRaft — ZooKeeper Removal

**Kafka 4.0 (March 2025) removed ZooKeeper entirely. KRaft is now the only mode.**

```
BEFORE (ZooKeeper era):                    AFTER (KRaft era, Kafka 3.3+):

┌─────────────────────────┐               ┌─────────────────────────────┐
│    ZooKeeper Ensemble    │               │      Kafka Cluster          │
│  ┌────┐ ┌────┐ ┌────┐   │               │                             │
│  │ZK-1│ │ZK-2│ │ZK-3│   │               │  ┌───────────────────────┐  │
│  └──┬─┘ └──┬─┘ └──┬─┘   │               │  │ Controller Quorum     │  │
│     └───┬──┘──┬──┘       │               │  │ (3 KRaft controllers) │  │
│         │     │           │               │  │ Uses Raft consensus   │  │
└─────────┼─────┼───────────┘               │  │ Metadata in internal  │  │
          │     │                           │  │ __cluster_metadata    │  │
  ┌───────▼─────▼──────────┐               │  │ topic                 │  │
  │   Kafka Brokers         │               │  └───────────┬───────────┘  │
  │ ┌──────┐ ┌──────┐      │               │              │              │
  │ │Broker│ │Broker│ ...   │               │  ┌───────────▼───────────┐  │
  │ └──────┘ └──────┘       │               │  │   Kafka Brokers       │  │
  └─────────────────────────┘               │  │ ┌──────┐ ┌──────┐    │  │
                                            │  │ │Broker│ │Broker│ .. │  │
  PROBLEMS:                                 │  │ └──────┘ └──────┘    │  │
  • Two systems to operate & monitor        │  └───────────────────────┘  │
  • ZK bottleneck at ~200K partitions       └─────────────────────────────┘
  • Slow controller failover (seconds)
  • Separate security config                BENEFITS:
  • ZK watches don't scale                  • Single system to deploy
                                            • ~2M partitions per cluster
                                            • Instant controller failover
                                            • Unified security model
                                            • Simpler operations
```

### KRaft Internals

```
Controller Quorum (typically 3 or 5 nodes):

  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │ Controller 0  │   │ Controller 1  │   │ Controller 2  │
  │ (ACTIVE)      │   │ (FOLLOWER)    │   │ (FOLLOWER)    │
  │               │   │               │   │               │
  │ Handles all   │   │ Replicates    │   │ Replicates    │
  │ metadata ops  │   │ metadata log  │   │ metadata log  │
  └───────────────┘   └───────────────┘   └───────────────┘

  Metadata stored in: __cluster_metadata topic (single partition)
  Uses Raft consensus for leader election among controllers.
  Brokers pull metadata from controllers (event-driven, not polling).
  Periodic snapshots prevent unbounded metadata log growth.

  Key config:
    process.roles=broker,controller  (combined mode for small clusters)
    process.roles=controller         (dedicated controllers for large clusters)
    process.roles=broker             (dedicated brokers)
    controller.quorum.voters=0@host0:9093,1@host1:9093,2@host2:9093
```

---

## 8. Exactly-Once Semantics (EOS)

The hardest topic in Kafka interviews. Kafka 0.11+ supports EOS.

### Three Layers of Exactly-Once

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   EXACTLY-ONCE SEMANTICS IN KAFKA                         │
│                                                                          │
│  LAYER 1: IDEMPOTENT PRODUCER (within a single partition)                │
│  ───────────────────────────────────────────────────────                  │
│  enable.idempotence=true (default since Kafka 3.0)                       │
│                                                                          │
│  Producer                          Broker                                │
│  ┌──────────┐                      ┌─────────────────────┐               │
│  │ PID=100  │                      │ Dedup table:         │               │
│  │ seq=0 ───┤──── send ──────────► │ PID=100, P0: seq=0  │ ✓ Accept      │
│  │ seq=1 ───┤──── send ──────────► │ PID=100, P0: seq=1  │ ✓ Accept      │
│  │ seq=1 ───┤──── retry (dup) ──►  │ PID=100, P0: seq=1  │ ✗ Reject dup  │
│  │ seq=2 ───┤──── send ──────────► │ PID=100, P0: seq=2  │ ✓ Accept      │
│  └──────────┘                      └─────────────────────┘               │
│                                                                          │
│  How: Broker assigns Producer ID (PID) + tracks sequence number          │
│  per (PID, partition). Duplicates detected and rejected.                 │
│                                                                          │
│  Limitation: PID doesn't survive producer restart.                       │
│  New instance → new PID → broker can't detect cross-session dupes.       │
│                                                                          │
│                                                                          │
│  LAYER 2: TRANSACTIONAL PRODUCER (across partitions + topics)            │
│  ───────────────────────────────────────────────────────────              │
│  transactional.id="my-app-instance-1"                                    │
│                                                                          │
│  Producer                     Transaction Coordinator (broker)           │
│  ┌──────────────────┐         ┌────────────────────────────────┐         │
│  │ initTransactions()│────────►│ Register transactional.id      │         │
│  │                    │        │ Fence old producers (zombie!)  │         │
│  │ beginTransaction() │        │                                │         │
│  │                    │        │ __transaction_state topic:     │         │
│  │ send(topicA, msg1) │───────►│   tx_id=my-app → ONGOING     │         │
│  │ send(topicB, msg2) │───────►│                                │         │
│  │ sendOffsetsToTxn() │───────►│   (consumer offsets bundled)  │         │
│  │                    │        │                                │         │
│  │ commitTransaction()│───────►│   tx_id=my-app → COMMITTED   │         │
│  │  OR                │        │   Write markers to all        │         │
│  │ abortTransaction() │───────►│   involved partitions         │         │
│  └──────────────────┘         └────────────────────────────────┘         │
│                                                                          │
│  Zombie Fencing: If old producer with same transactional.id              │
│  tries to write → fenced with ProducerFencedException.                   │
│  Ensures only ONE active producer per transactional.id.                  │
│                                                                          │
│                                                                          │
│  LAYER 3: READ_COMMITTED CONSUMERS                                       │
│  ─────────────────────────────────                                       │
│  isolation.level=read_committed                                          │
│                                                                          │
│  Consumer only sees messages from COMMITTED transactions.                │
│  Aborted transaction messages are filtered out.                          │
│                                                                          │
│  read_uncommitted (default): sees everything including aborted.          │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### The Consume-Transform-Produce Pattern (Canonical EOS)

```
  Input Topic              Kafka Streams App              Output Topic
  ┌───────────┐            ┌──────────────────┐           ┌────────────┐
  │ [A][B][C] │──read──►   │ 1. Read from input│──write──►│ [A'][B'][C']│
  └───────────┘            │ 2. Transform       │          └────────────┘
                           │ 3. Write to output │
                           │ 4. Commit input    │          __consumer_offsets
                           │    offset          │──write──►┌────────────┐
                           │                    │          │ offset=3   │
                           │ ALL IN ONE TX      │          └────────────┘
                           └──────────────────┘

  If ANYTHING fails → entire transaction aborted.
  Input offset not committed, output not visible.
  Next attempt reprocesses from last committed offset.

┌──────────────────────────────────────────────────────────────────────────┐
│  CRITICAL BOUNDARY:                                                       │
│  EOS works ONLY within the Kafka cluster (Kafka → Kafka).                │
│  For Kafka → external DB, you must implement idempotent writes           │
│  yourself (upsert with dedup key, or store offset in same DB tx).        │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Log Compaction

### How Compaction Works

```
Topic with cleanup.policy=compact (e.g., user profiles):

  BEFORE COMPACTION:
  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
  │K:A  │K:B  │K:A  │K:C  │K:B  │K:A  │K:D  │K:C  │K:A  │K:E  │
  │V:1  │V:1  │V:2  │V:1  │V:2  │V:3  │V:1  │V:2  │V:4  │V:1  │
  │off:0│off:1│off:2│off:3│off:4│off:5│off:6│off:7│off:8│off:9│
  └─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘

  AFTER COMPACTION (keep latest value per key):
  ┌─────┬─────┬─────┬─────┬─────┐
  │K:B  │K:D  │K:C  │K:A  │K:E  │
  │V:2  │V:1  │V:2  │V:4  │V:1  │
  │off:4│off:6│off:7│off:8│off:9│    ← Offsets preserved!
  └─────┴─────┴─────┴─────┴─────┘

  Note: Offsets are NEVER reused. Gaps are allowed.
  Reading from offset 5 → returns offset 6 (next available).

TOMBSTONE RECORDS (deletion):
─────────────────────────────
  Publishing key=B, value=null → tombstone marker.
  After compaction: key B and all its history removed.
  Tombstone itself removed after delete.retention.ms (default 24h).

  Use case: GDPR — user requests deletion → publish tombstone.

LOG STRUCTURE:

  ┌──────────────────────────────┬──────────────────────────────┐
  │         TAIL (Clean)          │         HEAD (Dirty)          │
  │                              │                              │
  │  Already compacted.          │  New writes. Not yet         │
  │  One value per key.          │  compacted. May have         │
  │  Read by new consumers       │  duplicate keys.             │
  │  to bootstrap state.         │  Read by active consumers.   │
  │                              │                              │
  └──────────────────────────────┴──────────────────────────────┘
                                  ▲
                           Cleaner Point
                    (background thread processes)

COMPACTION PROCESS:
  1. Log cleaner thread picks dirtiest partition (highest dirty ratio)
  2. Builds in-memory hash map: key → latest offset (24 bytes/entry)
  3. Recopies segments, skipping records with newer versions
  4. Swaps new clean segments in-place (atomic)
  5. Active segment is NEVER compacted
```

### Log Compaction vs Log Retention

```
┌───────────────────┬─────────────────────┬─────────────────────────┐
│                   │ LOG RETENTION        │ LOG COMPACTION           │
│                   │ (cleanup.policy=     │ (cleanup.policy=         │
│                   │  delete)             │  compact)                │
├───────────────────┼─────────────────────┼─────────────────────────┤
│ What's kept?      │ All messages within  │ Latest value per key    │
│                   │ retention window     │ (forever)               │
├───────────────────┼─────────────────────┼─────────────────────────┤
│ Deletion trigger  │ Time (retention.ms)  │ Newer value for same key│
│                   │ or size              │ or tombstone            │
├───────────────────┼─────────────────────┼─────────────────────────┤
│ Use case          │ Event streams, logs  │ State/changelog, CDC,   │
│                   │ Activity tracking    │ KTable, caches          │
├───────────────────┼─────────────────────┼─────────────────────────┤
│ Null values       │ Regular message      │ Tombstone (triggers     │
│                   │                     │ key deletion)           │
├───────────────────┼─────────────────────┼─────────────────────────┤
│ Both?             │ cleanup.policy=delete,compact → both apply    │
└───────────────────┴─────────────────────┴─────────────────────────┘
```

---

## 10. Kafka Streams & Connect

### Kafka Streams

```
┌──────────────────────────────────────────────────────────────────┐
│                    KAFKA STREAMS                                  │
│                                                                  │
│  A client LIBRARY (not a cluster) for stream processing.         │
│  Runs inside your application JVM. No separate infrastructure.   │
│                                                                  │
│  Input Kafka Topic                          Output Kafka Topic   │
│  ┌────────────┐     ┌──────────────────┐    ┌────────────────┐  │
│  │ raw-events │────►│  Kafka Streams   │───►│ enriched-events│  │
│  └────────────┘     │                  │    └────────────────┘  │
│                     │  • filter()       │                        │
│                     │  • map()          │    State Store         │
│                     │  • groupByKey()   │    ┌──────────────┐   │
│                     │  • aggregate()    │───►│ RocksDB      │   │
│                     │  • join()         │    │ (local disk) │   │
│                     │  • windowedBy()   │    └──────────────┘   │
│                     └──────────────────┘                        │
│                                                                  │
│  KStream: Unbounded stream of events (event stream)              │
│  KTable:  Changelog stream (latest value per key, like a table)  │
│  GlobalKTable: Full copy on every instance (for enrichment joins)│
│                                                                  │
│  Key advantage: exactly-once processing built-in.                │
│  Scaling: Deploy more app instances → partitions rebalanced.     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Kafka Connect

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        KAFKA CONNECT                                      │
│                                                                          │
│  Framework for moving data between Kafka and external systems.           │
│  No code needed — just configuration (JSON/REST API).                    │
│                                                                          │
│  SOURCE CONNECTORS (external → Kafka):                                   │
│  ┌─────────────┐     ┌─────────────────┐     ┌──────────────┐          │
│  │ PostgreSQL   │────►│ Debezium CDC    │────►│ Kafka Topic  │          │
│  │ MySQL        │     │ Source Connector │     │              │          │
│  │ MongoDB      │     └─────────────────┘     └──────────────┘          │
│  │ Files        │                                                        │
│  └─────────────┘                                                         │
│                                                                          │
│  SINK CONNECTORS (Kafka → external):                                     │
│  ┌──────────────┐     ┌─────────────────┐     ┌──────────────┐          │
│  │ Kafka Topic  │────►│ Elasticsearch   │────►│ Elasticsearch │          │
│  │              │     │ Sink Connector   │     │ HDFS          │          │
│  └──────────────┘     └─────────────────┘     │ S3            │          │
│                                               │ JDBC          │          │
│                                               └──────────────┘          │
│                                                                          │
│  100s of community connectors available. Supports exactly-once           │
│  with transactional source connectors.                                   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Real-World Usage at Scale

### Where Kafka Excels

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  COMPANY     │ USE CASE                    │ SCALE                           │
├──────────────┼─────────────────────────────┼─────────────────────────────────┤
│              │                             │                                 │
│  LinkedIn    │ Activity tracking, CDC,     │ 7+ trillion messages/day        │
│  (origin)    │ metrics, change capture     │ 100+ Kafka clusters             │
│              │                             │                                 │
│  Uber        │ Marketplace events, surge   │ Trillions msgs/day              │
│              │ pricing, driver matching,   │ Multiple PB/day                 │
│              │ trip events, ETA, data lake  │ 300+ microservices              │
│              │                             │                                 │
│  Netflix     │ Viewing activity, A/B test  │ Billions of events/day          │
│              │ events, recommendations,    │ Real-time video quality         │
│              │ error tracking              │ adjustment                      │
│              │                             │                                 │
│  Spotify     │ User play events,           │ Billions of events/day          │
│              │ ad impressions, metrics     │ Data pipeline backbone          │
│              │                             │                                 │
│  Twitter/X   │ Timeline events, tweet      │ Hundreds of billions/day        │
│              │ delivery, analytics         │                                 │
│              │                             │                                 │
│  Stripe      │ Payment events, webhook     │ Financial-grade durability      │
│              │ delivery, audit logs        │                                 │
│              │                             │                                 │
│  Shopify     │ Order events, inventory     │ Flash sale traffic spikes       │
│              │ updates, CDC                │                                 │
│              │                             │                                 │
│  Airbnb      │ Search events, booking      │ Real-time search ranking        │
│              │ pipeline, pricing           │                                 │
│              │                             │                                 │
└──────────────┴─────────────────────────────┴─────────────────────────────────┘
```

### Kafka Use Case Categories

```
1. EVENT SOURCING / AUDIT LOG
   ───────────────────────────
   Every state change → Kafka event. Rebuild state by replaying.
   
   [UserCreated] → [EmailChanged] → [AddressUpdated] → [AccountDeleted]
   
   Use: Banking, compliance, order history, GDPR audit

2. CHANGE DATA CAPTURE (CDC)
   ──────────────────────────
   PostgreSQL → Debezium → Kafka → Elasticsearch / Data Warehouse
   
   Keep derived stores in sync without dual-writes.
   The gold standard for microservices data distribution.

3. LOG AGGREGATION
   ────────────────
   Collect logs from 1000s of servers → Kafka → ELK/Splunk
   
   Replaced: Flume, Scribe, syslog-ng
   Advantage: Durable buffer, multiple consumers, replay

4. REAL-TIME ANALYTICS
   ────────────────────
   Clicks → Kafka → Flink/Spark → Dashboard
   
   Use: Trending topics, live metrics, anomaly detection

5. MICROSERVICE DECOUPLING
   ─────────────────────────
   ┌──────────┐     ┌───────────────┐     ┌──────────┐
   │ Order    │────►│ Kafka Topics  │────►│ Payment  │
   │ Service  │     │ (orders,       │────►│ Inventory│
   │          │     │  payments,     │────►│ Shipping │
   │          │     │  notifications)│────►│ Analytics│
   └──────────┘     └───────────────┘     └──────────┘
   
   Services don't know about each other. Add new consumers freely.

6. STREAM PROCESSING BACKBONE
   ───────────────────────────
   Kafka → Kafka Streams/Flink → Kafka
   
   Enrichment, aggregation, windowed computations, joins.

7. ASYNC TASK QUEUE (with caveats)
   ────────────────────────────────
   Uber's uForwarder: Push-based consumer proxy over Kafka.
   Use when you need at-least-once async processing at massive scale.
   For complex routing/priority: consider RabbitMQ instead.
```

---

## 12. When Kafka Failed — Production Incidents

### PagerDuty Kafka Outage (August 2025)

```
ROOT CAUSE: Producer-per-request anti-pattern

  WHAT HAPPENED:
  ┌──────────────────────────────────────────────────────────────────┐
  │ An auditing feature created a NEW KafkaProducer for every       │
  │ single HTTP request instead of reusing a shared instance.       │
  │                                                                  │
  │ Normal load: ~50K producers/hour                                │
  │ After deploy: 4.2 MILLION producers/hour (84x spike)            │
  │                                                                  │
  │ Each KafkaProducer allocates:                                    │
  │   • 32 MB buffer memory                                          │
  │   • New TCP connections to all brokers                            │
  │   • Background sender thread                                     │
  │                                                                  │
  │ Impact:                                                          │
  │   • 95% of events rejected at peak                               │
  │   • Broker heap exhaustion → cascading crashes                   │
  │   • Partition reassignment storms                                 │
  │   • Alerting silenced for 9+ HOURS                               │
  │   • Complete Kafka cluster failure                                │
  └──────────────────────────────────────────────────────────────────┘

  LESSON: KafkaProducer is thread-safe. ALWAYS reuse.
  LESSON: Monitor producer count and connection count as a metric.
  LESSON: Rate-limit producer creation or use connection pooling.
```

### Inngest Kafka Disk Failure (October 2025)

```
ROOT CAUSE: Disk space exhaustion

  ┌──────────────────────────────────────────────────────────────────┐
  │ Kafka cluster's disk filled up completely.                       │
  │                                                                  │
  │ Cascade:                                                         │
  │   1. Disk full → Kafka can't write new segments                  │
  │   2. Broker can't accept new messages                            │
  │   3. File descriptors exhausted on Event API containers          │
  │   4. Workers publishing span traces fail                         │
  │   5. Partial downtime across the platform                        │
  │                                                                  │
  │ Recovery:                                                        │
  │   1. Delete old data to free disk space                          │
  │   2. Restart containers                                          │
  │   3. Waited for cluster to rebalance                             │
  └──────────────────────────────────────────────────────────────────┘

  LESSON: Monitor disk usage PER BROKER with aggressive alerting.
  LESSON: Set retention policies (time + size) conservatively.
  LESSON: Use log.retention.bytes as a safety net even with time-based.
```

### Mews Temporal/Kafka Workflow Gridlock (2025)

```
ROOT CAUSE: Coupling Kafka writes with business logic in Temporal workflows

  ┌──────────────────────────────────────────────────────────────────┐
  │ Temporal workflows processing Stripe webhooks had Kafka          │
  │ publish calls BEFORE core business logic.                        │
  │                                                                  │
  │ When Kafka went down:                                            │
  │   → Kafka publish blocks/fails                                   │
  │   → Temporal workflow stalls                                     │
  │   → Business logic never executes                                │
  │   → Thousands of payment workflows stuck                         │
  │   → Stripe webhooks pile up                                      │
  │                                                                  │
  │ Fix: Decouple side effects from business logic.                  │
  │   → Run Kafka publish as parallel/independent step               │
  │   → Core business logic should never depend on Kafka availability│
  └──────────────────────────────────────────────────────────────────┘

  LESSON: Kafka is infrastructure, not business logic. Don't gate
  critical paths on Kafka writes.
  LESSON: Design for Kafka being temporarily unavailable.
```

### Wikimedia Kafka MirrorMaker Incident (October 2024)

```
ROOT CAUSE: Single-partition bottleneck in cross-DC replication

  ┌──────────────────────────────────────────────────────────────────┐
  │ MirrorMaker replicating between datacenters.                     │
  │ A single partition developed severe backlog: 10+ min lag.        │
  │                                                                  │
  │ Why: Upstream DB saturation caused uneven message production.    │
  │ Hot partition couldn't be consumed fast enough.                   │
  │ Cross-DC replication fell behind.                                │
  │ User reads and writes impacted until traffic rerouted.           │
  └──────────────────────────────────────────────────────────────────┘

  LESSON: Monitor per-partition lag, not just topic-level lag.
  LESSON: Avoid hot partitions — use well-distributed partition keys.
  LESSON: Cross-DC replication multiplies single-partition problems.
```

---

## 13. When NOT to Use Kafka

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                   WHEN KAFKA IS THE WRONG CHOICE                              │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. SIMPLE REQUEST-REPLY / RPC                                               │
│     HTTP, gRPC, or RabbitMQ's RPC pattern are far simpler.                  │
│     Kafka has no built-in request-reply correlation.                         │
│                                                                              │
│  2. LOW-VOLUME, LOW-COMPLEXITY PIPELINES                                     │
│     If you have 100 messages/day, Kafka is massive overkill.                │
│     Use: Redis Streams, SQS, or direct HTTP calls.                          │
│                                                                              │
│  3. COMPLEX ROUTING LOGIC                                                    │
│     RabbitMQ: exchanges, bindings, routing keys, dead-letter, priorities.   │
│     Kafka: topics + partitions. No built-in routing.                        │
│     If you need message-level routing → RabbitMQ, ActiveMQ.                 │
│                                                                              │
│  4. LONG-RUNNING TASKS / JOB QUEUES                                         │
│     Kafka doesn't track per-message acknowledgement.                        │
│     A slow message blocks the entire partition.                              │
│     Use: Celery + Redis/RabbitMQ, AWS SQS, Temporal.                        │
│     (See "Per-Message Ack" explanation below)                                │
│                                                                              │
│  5. REAL-TIME DATABASE REPLACEMENT                                           │
│     Kafka is not a database. No secondary indexes, no ad-hoc queries.       │
│     For key-value lookups: use Kafka + materialized views (KTable).         │
│     For queries: use a real database fed by Kafka.                          │
│                                                                              │
│  6. VERY LOW LATENCY (< 1ms)                                                │
│     Kafka latency: 2-50ms typical.                                          │
│     For sub-millisecond: shared memory, kernel bypass (DPDK/RDMA).          │
│     Stock exchanges use custom protocols, not Kafka.                        │
│                                                                              │
│  7. SMALL TEAM / STARTUP MVP                                                 │
│     Kafka operational burden is significant.                                │
│     Start with: SQS, Redis Streams, Google Pub/Sub (managed).              │
│     Migrate to Kafka when scale demands it.                                 │
│                                                                              │
│  8. GUARANTEED DELIVERY TO EXTERNAL SYSTEMS                                  │
│     Kafka guarantees within the cluster only.                               │
│     For exactly-once to external DB: you still need idempotent writes.      │
│                                                                              │
│  9. MESSAGE PRIORITY QUEUES                                                  │
│     Kafka has no built-in priority mechanism.                               │
│     Workaround: separate topics per priority level.                         │
│     Better: RabbitMQ with priority queues.                                  │
│                                                                              │
│ 10. MQTT / IOT PROTOCOL GATEWAY                                             │
│     Kafka doesn't speak MQTT natively.                                      │
│     For IoT: EMQX/Mosquitto → bridge to Kafka.                             │
│     Or: HiveMQ with Kafka extension.                                        │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Why "Kafka Doesn't Track Per-Message Acknowledgement" Matters

This is a fundamental design difference between Kafka and traditional message queues.

```
TRADITIONAL QUEUE (RabbitMQ, SQS): Per-Message Ack
────────────────────────────────────────────────────

  Queue: [msg-A] [msg-B] [msg-C] [msg-D] [msg-E]

  Consumer picks msg-A → starts processing (msg-A locked/invisible to others)
  Consumer picks msg-B → starts processing (msg-B locked)
  
  msg-B finishes first → Consumer sends ACK for msg-B → REMOVED from queue ✓
  msg-A still processing... (takes 30 seconds)
  msg-C picked by another consumer → processes → ACK → REMOVED ✓
  msg-D picked by another consumer → processes → ACK → REMOVED ✓
  msg-A finally finishes → ACK → REMOVED ✓

  KEY: Each message is tracked INDEPENDENTLY.
  • Slow msg-A does NOT block msg-B, C, D, E
  • If consumer crashes on msg-A → broker re-delivers msg-A to another consumer
  • Each message has its own lifecycle: delivered → processing → acked → deleted
  • Broker knows the STATUS of every single message


KAFKA: Offset-Based Tracking (NOT per-message)
────────────────────────────────────────────────

  Partition: [off:0] [off:1] [off:2] [off:3] [off:4]
              msg-A   msg-B   msg-C   msg-D   msg-E

  Consumer reads batch [0,1,2,3,4]
  Consumer processes msg-A (offset 0)... slow (30 seconds)
  
  PROBLEM: Consumer cannot commit offset 1,2,3,4 and skip 0.
  Offset commit means "I've processed everything UP TO this offset."
  
  If consumer commits offset=5 → means "I'm done with 0,1,2,3,4"
  If consumer commits offset=2 → means "I'm done with 0,1" only
  
  There is NO way to say: "I finished 1,2,3,4 but NOT 0."

  ┌──────────────────────────────────────────────────────────────────┐
  │  Committed offset is a SINGLE NUMBER per (consumer group,       │
  │  partition). It's a WATERMARK, not a per-message bitmap.        │
  │                                                                  │
  │  Partition:  [0] [1] [2] [3] [4] [5] [6] [7]                   │
  │                       ▲                                          │
  │              committed offset = 2                                │
  │              means: 0 and 1 are "done"                           │
  │              2,3,4,5,6,7 are "not yet consumed"                  │
  │                                                                  │
  │  You CANNOT mark message 5 as done while 3 is still processing. │
  │  You can only move the watermark FORWARD sequentially.           │
  └──────────────────────────────────────────────────────────────────┘
```

```
WHY THIS IS A PROBLEM FOR JOB QUEUES:
──────────────────────────────────────

  Imagine processing tasks with varying durations:

  RabbitMQ (per-message ack):
  ┌──────────────────────────────────────────────────────────────────┐
  │ Worker 1: [msg-A: 30s] ─────────────────────── ack ✓            │
  │ Worker 2: [msg-B: 1s] ack ✓  [msg-D: 2s] ack ✓  [msg-F] ack ✓ │
  │ Worker 3: [msg-C: 2s] ack ✓  [msg-E: 1s] ack ✓  [msg-G] ack ✓ │
  │                                                                  │
  │ Workers 2 and 3 keep churning through messages while Worker 1   │
  │ handles the slow one. No blocking. Maximum throughput.           │
  └──────────────────────────────────────────────────────────────────┘

  Kafka (offset-based):
  ┌──────────────────────────────────────────────────────────────────┐
  │ Consumer reads from partition 0:                                  │
  │   [msg-A: 30s] [msg-B: 1s] [msg-C: 2s] [msg-D: 1s]            │
  │       ▲                                                          │
  │       │                                                          │
  │   msg-A takes 30 seconds to process.                             │
  │   Can't commit offset past msg-A until it's done.               │
  │   If consumer crashes at this point → ALL four messages          │
  │   get reprocessed (offset wasn't moved).                         │
  │                                                                  │
  │   ONE slow message = entire partition stalls.                     │
  │                                                                  │
  │   Head-of-line blocking!                                         │
  └──────────────────────────────────────────────────────────────────┘
```

```
WORKAROUNDS IN KAFKA:
─────────────────────

1. PROCESS IN A THREAD POOL (most common)
   Consumer polls fast → dispatches to thread pool → commits offset
   only when ALL messages in the batch are done.
   
   while (true) {
     records = consumer.poll(100ms);
     List<Future> futures = new ArrayList<>();
     for (record : records) {
       futures.add(threadPool.submit(() -> process(record)));
     }
     // Wait for ALL to complete before committing
     for (future : futures) { future.get(); }
     consumer.commitSync();
   }
   
   ✓ Parallelism within consumer
   ✗ Still can't commit per-message
   ✗ Slowest message in batch determines commit time

2. UBER'S APPROACH: uForwarder (Consumer Proxy)
   A push-based proxy that reads from Kafka and sends individual
   messages to consumers via gRPC. Tracks per-message acks itself.
   
   Kafka → uForwarder → gRPC push to workers → per-message ack back
   
   ✓ Per-message semantics on top of Kafka
   ✗ Extra infrastructure to operate

3. USE THE RIGHT TOOL
   If per-message acknowledgement is your primary need:
   • RabbitMQ — built for this
   • AWS SQS — visibility timeout per message
   • Temporal — workflow-level task tracking
   • Celery — distributed task queue
```

```
WHY KAFKA DESIGNED IT THIS WAY:
────────────────────────────────

  Per-message ack tracking is EXPENSIVE at scale:

  RabbitMQ per-message tracking:
    • Broker must track state per message: pending, delivered, acked
    • Lock mechanism so same message isn't delivered twice
    • Timeout mechanism for unacked messages
    • At 1M msgs/sec → 1M state entries to manage PER SECOND
    • This is why RabbitMQ tops out at ~50K msgs/sec

  Kafka offset tracking:
    • ONE integer per (consumer group, partition)
    • 100 partitions × 10 consumer groups = 1000 integers total
    • O(1) storage regardless of message count
    • This is why Kafka handles 1M+ msgs/sec

  Trade-off:
  ┌──────────────────────────────────────────────────────────────────┐
  │ RabbitMQ: Fine-grained control per message → lower throughput    │
  │ Kafka:    Coarse-grained offset tracking   → massive throughput  │
  └──────────────────────────────────────────────────────────────────┘
```

---

## 14. Anti-Patterns That Kill Kafka

```
┌───┬──────────────────────────────┬───────────────────────────────────────────┐
│ # │ ANTI-PATTERN                  │ WHY IT'S BAD / FIX                         │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 1 │ Producer-per-request         │ 32MB buffer + TCP connections per instance│
│   │ (new KafkaProducer each time)│ Fix: Singleton/shared producer (thread-   │
│   │                              │ safe). PagerDuty outage was this.         │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 2 │ Topic explosion              │ 10K+ topics → metadata overhead, slow     │
│   │ (topic per customer/entity)  │ rebalances, ZK/KRaft pressure.           │
│   │                              │ Fix: Topic per event type, not per entity.│
│   │                              │ Use key for per-entity partitioning.      │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 3 │ Hot partitions               │ 90% traffic to 1 partition, others idle.  │
│   │ (skewed key distribution)    │ Fix: Salted keys, compound keys, or       │
│   │                              │ random partitioning for null keys.        │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 4 │ Synchronous sends            │ Calling .get() on every send blocks the   │
│   │ (producer.send().get())      │ producer thread. Destroys throughput.     │
│   │                              │ Fix: Async sends with callbacks.          │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 5 │ Ignoring delivery callbacks  │ Errors silently swallowed → data loss.    │
│   │                              │ Fix: Always check callback errors.        │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 6 │ acks=0 for critical data     │ Fire-and-forget → data loss on broker     │
│   │                              │ failure. Fix: acks=all + min.insync=2.    │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 7 │ Huge messages (>1MB)         │ Memory pressure, consumer timeouts,       │
│   │                              │ uneven partitions. Fix: Store payload     │
│   │                              │ in S3/blob store, send reference in Kafka.│
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 8 │ Too many partitions          │ Each partition = open files, memory, ISR  │
│   │ (10K+ per broker)            │ tracking. Fix: Start small (6-12 per      │
│   │                              │ topic), scale partitions based on         │
│   │                              │ throughput needs.                          │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│ 9 │ Auto offset commit with      │ Commit before processing → message lost   │
│   │ slow processing              │ on crash. Fix: Manual offset commit after │
│   │                              │ processing completes.                     │
├───┼──────────────────────────────┼───────────────────────────────────────────┤
│10 │ Consumer group churn         │ Frequent joins/leaves → constant          │
│   │ (scaling up/down rapidly)    │ rebalancing → no progress.               │
│   │                              │ Fix: Static membership with              │
│   │                              │ group.instance.id.                        │
└───┴──────────────────────────────┴───────────────────────────────────────────┘
```

---

## 15. Performance Tuning Cheat Sheet

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    KAFKA PERFORMANCE TUNING                                   │
├──────────────────┬──────────────────────────────────────────────────────────┤
│                  │                                                          │
│  PRODUCER        │ batch.size=64KB-256KB (default 16KB)                     │
│  THROUGHPUT      │ linger.ms=5-50 (default 0 — send immediately)           │
│                  │ compression.type=lz4 or zstd                            │
│                  │ buffer.memory=64MB+ for high throughput                  │
│                  │ max.in.flight.requests.per.connection=5                  │
│                  │                                                          │
│  PRODUCER        │ acks=all + min.insync.replicas=2                        │
│  DURABILITY      │ retries=Integer.MAX_VALUE                               │
│                  │ enable.idempotence=true                                  │
│                  │ max.in.flight.requests.per.connection=5 (safe with       │
│                  │   idempotence, ensures ordering)                         │
│                  │                                                          │
│  CONSUMER        │ fetch.min.bytes=1MB (default 1 byte)                    │
│  THROUGHPUT      │ fetch.max.wait.ms=500 (wait for more data)              │
│                  │ max.poll.records=500-1000                                │
│                  │ max.partition.fetch.bytes=1MB+                           │
│                  │                                                          │
│  CONSUMER        │ max.poll.interval.ms=300000+ for slow processing        │
│  STABILITY       │ session.timeout.ms=45000                                │
│                  │ heartbeat.interval.ms=15000                              │
│                  │ group.instance.id=<static> (static membership)          │
│                  │ partition.assignment.strategy=CooperativeStickyAssignor  │
│                  │                                                          │
│  BROKER          │ num.partitions=6-12 per topic (start conservative)      │
│                  │ default.replication.factor=3                             │
│                  │ num.io.threads=8 (= num disk drives)                    │
│                  │ num.network.threads=3 (= num CPU cores / 2)             │
│                  │ socket.send.buffer.bytes=102400                          │
│                  │ socket.receive.buffer.bytes=102400                       │
│                  │ log.retention.hours=168 (7 days)                        │
│                  │ log.segment.bytes=1GB                                    │
│                  │                                                          │
│  END-TO-END      │ compression.type=lz4 (best speed/ratio for most)       │
│  LATENCY         │ acks=1 (if some loss acceptable)                        │
│                  │ linger.ms=0                                              │
│                  │ fetch.min.bytes=1                                        │
│                  │                                                          │
└──────────────────┴──────────────────────────────────────────────────────────┘

NUMBERS TO KNOW:
  • Single broker throughput: 200-800 MB/s (depends on hardware)
  • Single partition throughput: 10-100 MB/s
  • End-to-end latency (acks=all): 5-20ms (same DC)
  • p99 latency: 50-200ms under load
  • Max recommended partitions/broker: ~4000 (KRaft mode)
  • Max recommended partitions/cluster: ~200K (ZK), ~2M (KRaft)
```

---

## 16. Interview Questions — Medium

### Q1: How does Kafka ensure message ordering?

```
ANSWER:

Ordering is guaranteed ONLY within a partition, not across partitions.

  Partition 0: [A₁] → [A₂] → [A₃]   ← Consumer sees A₁ before A₂ before A₃
  Partition 1: [B₁] → [B₂] → [B₃]   ← Consumer sees B₁ before B₂ before B₃

  But NO guarantee that A₁ is consumed before B₁ across partitions.

  To ensure ordering for related events:
  1. Use the same key → routes to same partition
  2. hash(order_id) % partitions → all events for one order are ordered
  
  Gotcha with retries:
  If max.in.flight.requests.per.connection > 1 AND retries > 0
  AND enable.idempotence=false:
    → Batch 1 fails, Batch 2 succeeds, Batch 1 retry succeeds
    → Out of order within partition!
  
  Fix: enable.idempotence=true (handles reordering on retry,
       allows up to 5 in-flight requests safely)
```

### Q2: What happens when a consumer crashes mid-processing?

```
ANSWER:

Depends on offset commit strategy:

SCENARIO: Consumer reads messages [5,6,7,8,9], processes 5,6,7, then crashes.

  Case 1 — Auto-commit (committed offset=10 before crash):
    New consumer starts at offset 10.
    Messages 8,9 LOST (never processed but offset committed).

  Case 2 — Manual commit after each (committed offset=7):
    New consumer starts at offset 8.
    Messages 8,9 reprocessed. No data loss, but possible duplicates.

  Case 3 — Manual commit before processing (committed offset=10):
    Same as auto-commit. 8,9 lost.

  BEST PRACTICE:
    Manual commit AFTER processing + idempotent consumers.
    
    while (true) {
      records = consumer.poll(Duration.ofMillis(100));
      for (record : records) {
        processIdempotently(record);  // safe to replay
      }
      consumer.commitSync();  // commit only after successful processing
    }
```

### Q3: Explain consumer lag and how to handle it

```
ANSWER:

Consumer Lag = Latest Offset (LEO) - Consumer's Committed Offset

  Partition 0: [0][1][2][3][4][5][6][7][8][9]
                                         ▲   ▲
                          Consumer offset=7   LEO=9
                          
                          Lag = 9 - 7 = 2 messages

  CAUSES:
  • Slow processing (DB queries, external API calls)
  • Consumer GC pauses
  • Network issues
  • Too many partitions per consumer
  • Sudden traffic spike

  SOLUTIONS:
  1. Scale out: Add more consumers (up to partition count)
  2. Increase max.poll.records + process in batches
  3. Parallelize processing within consumer (thread pool)
  4. Optimize processing logic (async I/O, caching)
  5. Increase partitions (requires careful planning)

  MONITORING:
  • kafka.consumer:type=consumer-fetch-manager-metrics,
    name=records-lag-max
  • Burrow (LinkedIn's consumer lag monitoring tool)
  • Kafka Exporter + Prometheus + Grafana
```

### Q4: How does Kafka handle back-pressure?

```
ANSWER:

Kafka uses a PULL model, so back-pressure is naturally handled:

  PRODUCER SIDE:
  ┌──────────┐     buffer.memory=32MB     ┌─────────┐
  │ Producer │ ──► [RecordAccumulator] ──► │ Broker  │
  └──────────┘                             └─────────┘
  
  If brokers are slow:
  1. Accumulator fills up (buffer.memory exhausted)
  2. producer.send() BLOCKS for max.block.ms (default 60s)
  3. If still blocked → TimeoutException thrown to application
  4. Application decides: retry, drop, circuit break

  CONSUMER SIDE:
  ┌─────────┐      consumer.poll()      ┌──────────┐
  │ Broker  │ ◄───── pull request ──── │ Consumer │
  └─────────┘                          └──────────┘

  Consumer controls its own pace:
  1. Slow consumer? → Kafka doesn't push faster.
  2. Consumer falls behind → messages stay in log (retention period)
  3. Consumer catches up when ready
  4. Lag increases → monitoring alert → scale consumers

  This is a KEY advantage of pull over push:
  No "thundering herd" problem.
  No need for complex back-pressure protocols.
```

### Q5: Design a retry mechanism for failed Kafka message processing

```
ANSWER:

Pattern: Retry Topic + Dead Letter Queue (DLQ)

  Main Topic              Retry Topic            DLQ
  ┌──────────┐           ┌───────────┐          ┌──────┐
  │ events   │──fail──►  │ events.   │──fail──► │events│
  │          │           │ retry.1   │  (3x)    │.dlq  │
  └────┬─────┘           └────┬──────┘          └──────┘
       │                      │                     │
   Consumer A             Consumer B           Human/Alert
   (normal processing)   (retry with delay)    (investigation)

  IMPLEMENTATION:
  1. Consumer reads from "events" topic
  2. Processing fails → publish to "events.retry.1" with retry_count header
  3. Retry consumer reads "events.retry.1" with delay (Thread.sleep or 
     delayed consumer start)
  4. If retry succeeds → done
  5. If retry_count > MAX_RETRIES → publish to "events.dlq"
  6. DLQ monitored by ops team / alerting

  ADVANCED (exponential backoff):
  events.retry.1  → 1 minute delay
  events.retry.2  → 5 minute delay  
  events.retry.3  → 30 minute delay
  events.dlq      → manual investigation

  Delay implementation options:
  a. Pause consumer, resume after delay
  b. Use Kafka headers with retry timestamp + filter in consumer
  c. External scheduler (e.g., Temporal) for sophisticated retries
```

### Q6: What is the difference between Kafka and RabbitMQ? When to use which?

```
┌────────────────────┬─────────────────────────┬─────────────────────────────┐
│ Dimension          │ Kafka                   │ RabbitMQ                    │
├────────────────────┼─────────────────────────┼─────────────────────────────┤
│ Model              │ Distributed log (pull)  │ Message broker (push)       │
│ Message lifecycle  │ Retained after consume  │ Deleted after ack           │
│ Replay             │ Yes (any offset)        │ No                          │
│ Ordering           │ Per partition            │ Per queue                   │
│ Throughput         │ ~1M+ msg/s              │ ~50K msg/s                  │
│ Latency            │ ~5-50ms                 │ ~1-10ms                     │
│ Routing            │ Topic + partition key    │ Exchanges, bindings, keys   │
│ Consumer model     │ Consumer groups          │ Queue with competing        │
│                    │                         │ consumers                   │
│ Message priority   │ No (workaround: topics) │ Yes (built-in)              │
│ Dead letter queue  │ Manual (retry topics)   │ Built-in DLX                │
│ Exactly-once       │ Yes (Kafka-to-Kafka)    │ No (at-least-once)          │
│ Operational cost   │ High (cluster ops)      │ Medium                      │
│ Protocol           │ Custom binary over TCP  │ AMQP, MQTT, STOMP          │
├────────────────────┼─────────────────────────┼─────────────────────────────┤
│ CHOOSE KAFKA       │ Event streaming, CDC, log aggregation, analytics,    │
│                    │ event sourcing, high throughput, multiple consumers   │
├────────────────────┼─────────────────────────┼─────────────────────────────┤
│ CHOOSE RABBITMQ    │ Task queues, RPC, complex routing, priority queues,  │
│                    │ small-medium scale, low latency, simple ops          │
└────────────────────┴─────────────────────────┴─────────────────────────────┘
```

### Q7: How would you migrate a Kafka topic with zero downtime?

```
ANSWER:

APPROACH 1 — Dual-Write + Dual-Read:
  1. Create new topic with desired config (more partitions, new schema)
  2. Producers write to BOTH old and new topic
  3. Consumers read from new topic (with fresh consumer group)
  4. Verify data consistency between old and new
  5. Stop writing to old topic
  6. Decommission old topic after retention expires

APPROACH 2 — MirrorMaker 2 / Kafka Connect:
  1. Set up MirrorMaker 2 from old topic to new topic
  2. MM2 replicates all messages, translates offsets
  3. Switch consumers to new topic
  4. Switch producers to new topic
  5. Stop MM2

APPROACH 3 — Consumer-side migration:
  1. Create new topic
  2. Deploy new consumers reading from new topic (idle initially)
  3. Deploy updated producers to write to new topic
  4. Old consumers drain remaining messages from old topic
  5. Old topic naturally empties
  
  CRITICAL: Never decrease partition count (Kafka doesn't support it).
  If you need fewer partitions → create new topic with fewer partitions.
```

---

## 17. Interview Questions — Hard

### Q8: How does Kafka achieve exactly-once delivery end-to-end?

```
ANSWER: (See Section 8 for full diagram)

Three mechanisms combined:

1. IDEMPOTENT PRODUCER:
   enable.idempotence=true → PID + sequence number per (producer, partition)
   Broker deduplicates retries. Protects against network-level duplicates.
   Limitation: PID changes on producer restart.

2. TRANSACTIONAL PRODUCER:
   transactional.id → persistent across restarts.
   Atomic writes across multiple partitions + consumer offset commits.
   Zombie fencing: old producer with same transactional.id gets killed.
   
   API: beginTransaction() → send() → sendOffsetsToTransaction() → 
        commitTransaction()

3. READ_COMMITTED CONSUMER:
   isolation.level=read_committed → only sees committed transaction messages.

BOUNDARY:
   EOS works Kafka-to-Kafka ONLY.
   For Kafka → external DB: implement idempotent upserts yourself.
   Pattern: Store Kafka offset in same DB transaction as business write.
   On recovery: read last offset from DB → seek consumer to that offset.
```

### Q9: You're seeing increasing end-to-end latency in a Kafka pipeline. Walk through your debugging process.

```
ANSWER:

Step 1: IDENTIFY WHERE LATENCY IS ACCUMULATING
  
  End-to-end = Produce latency + Broker latency + Consume latency + Process latency
  
  ┌──────────┐    T1     ┌───────┐    T2     ┌──────────┐    T3
  │ Producer │ ───────►  │ Broker│ ───────►  │ Consumer │ ───────► Output
  └──────────┘           └───────┘           └──────────┘

Step 2: CHECK PRODUCER SIDE
  • Is linger.ms too high? (batching delay)
  • Is buffer.memory full? (send() blocking)
  • Is acks=all with slow followers? (replication lag)
  • Network latency between producer and broker?
  Metrics: request-latency-avg, record-send-rate, bufferpool-wait-time

Step 3: CHECK BROKER SIDE
  • Is disk I/O saturated? (sequential writes degraded)
  • Are under-replicated partitions present? (ISR shrinkage)
  • Are request queues backing up? (num.io.threads too low)
  • Is OS page cache thrashing? (too much data, not enough RAM)
  • Is GC pausing the JVM? (check GC logs)
  Metrics: UnderReplicatedPartitions, RequestHandlerAvgIdlePercent,
           LogFlushRateAndTimeMs, request-latency-avg

Step 4: CHECK CONSUMER SIDE
  • Consumer lag increasing? (consumer can't keep up)
  • max.poll.interval.ms being exceeded? (rebalance storms)
  • Processing time per record too high? (slow downstream)
  • Too few consumers for partition count?
  • Consumer doing synchronous I/O in poll loop?
  Metrics: records-lag-max, poll-idle-ratio, commit-latency

Step 5: CHECK SYSTEMIC ISSUES
  • Cross-DC replication lag (MirrorMaker)
  • Hot partitions (check per-partition metrics)
  • Schema Registry latency (if using Avro/Protobuf)
  • Network congestion between broker racks
```

### Q10: Design a multi-region Kafka deployment for a global payment system

```
ANSWER:

Requirements:
  • Payments must be durable (zero data loss)
  • Low latency for local users
  • Disaster recovery (entire region can fail)
  • Regulatory: some data must stay in region

Architecture:

  US-EAST Region                         EU-WEST Region
  ┌────────────────────┐                 ┌────────────────────┐
  │ Kafka Cluster A    │                 │ Kafka Cluster B    │
  │ (3 brokers, 3 AZ)  │                 │ (3 brokers, 3 AZ)  │
  │                    │   MirrorMaker2  │                    │
  │ payments.us ◄──────┼────── sync ────►│ payments.eu        │
  │ (primary writes)   │                 │ (primary writes)   │
  │                    │                 │                    │
  │ payments.eu.mirror │                 │ payments.us.mirror │
  │ (read-only replica)│                 │ (read-only replica)│
  └────────────────────┘                 └────────────────────┘

  WRITE STRATEGY:
  • US users write to Kafka Cluster A (payments.us topic)
  • EU users write to Kafka Cluster B (payments.eu topic)
  • MirrorMaker 2 replicates between clusters (async, ~100-500ms lag)
  
  READ STRATEGY:
  • Analytics in US reads: payments.us + payments.eu.mirror
  • Analytics in EU reads: payments.eu + payments.us.mirror
  
  DURABILITY CONFIG:
    acks=all
    min.insync.replicas=2
    replication.factor=3
    unclean.leader.election.enable=false

  DISASTER RECOVERY:
  • If US-EAST goes down entirely:
    1. DNS failover routes US traffic to EU
    2. EU cluster has payments.us.mirror (slightly behind)
    3. Accept ~100-500ms of potential data loss (RPO)
    4. Or: synchronous replication (high latency penalty)

  TRADE-OFF:
  ┌───────────────────────┬──────────────────────────┐
  │ Async replication     │ Sync replication          │
  │ Low latency (~5ms)    │ High latency (~100-500ms) │
  │ RPO > 0 (some loss)   │ RPO = 0 (no loss)         │
  │ Standard for most     │ For financial compliance  │
  └───────────────────────┴──────────────────────────┘
```

### Q11: How would you handle schema evolution in a Kafka-based system?

```
ANSWER:

Problem: Producer and consumer must agree on message format.
         Schemas change over time (add fields, rename, deprecate).

Solution: Schema Registry (Confluent or Apicurio)

  ┌──────────┐   register     ┌─────────────────┐
  │ Producer │ ──schema────►  │ Schema Registry  │
  │          │                │                  │
  │ Writes:  │ ◄──schema id──│ Stores versions: │
  │ [id|data]│                │  v1, v2, v3...   │
  └──────────┘                └────────┬─────────┘
                                       │
  ┌──────────┐   fetch schema          │
  │ Consumer │ ◄──by id───────────────┘
  │          │
  │ Deserializes with correct schema version
  └──────────┘

COMPATIBILITY MODES:
  ┌────────────────┬────────────────────────────────────────┐
  │ BACKWARD       │ New schema can read old data.           │
  │ (recommended)  │ Consumer upgrade first, then producer.  │
  │                │ Can: add optional fields, remove fields │
  ├────────────────┼────────────────────────────────────────┤
  │ FORWARD        │ Old schema can read new data.           │
  │                │ Producer upgrade first, then consumer.  │
  │                │ Can: add fields, remove optional fields │
  ├────────────────┼────────────────────────────────────────┤
  │ FULL           │ Both backward and forward compatible.   │
  │                │ Can: add/remove optional fields only.   │
  ├────────────────┼────────────────────────────────────────┤
  │ NONE           │ No checks. DANGEROUS. Don't use in prod.│
  └────────────────┴────────────────────────────────────────┘

FORMAT COMPARISON:
  ┌──────────┬──────────┬──────────────┬──────────────┐
  │          │ Avro     │ Protobuf     │ JSON Schema  │
  ├──────────┼──────────┼──────────────┼──────────────┤
  │ Size     │ Compact  │ Compact      │ Verbose      │
  │ Schema   │ Required │ Required     │ Optional     │
  │ Evolution│ Excellent│ Good         │ Limited      │
  │ Speed    │ Fast     │ Very Fast    │ Slow         │
  │ Use case │ Kafka    │ gRPC/Kafka   │ REST APIs    │
  │          │ standard │              │              │
  └──────────┴──────────┴──────────────┴──────────────┘
```

### Q12: A Kafka cluster is running out of disk. What do you do?

```
ANSWER:

IMMEDIATE ACTIONS (stop the bleeding):
  1. Check which topics are largest: kafka-log-dirs.sh --describe
  2. Reduce retention for non-critical topics:
     kafka-configs.sh --alter --topic <topic> --add-config retention.ms=86400000
  3. Delete old consumer groups: kafka-consumer-groups.sh --delete
  4. Force log segment roll: log.segment.ms override
  5. If using compaction: trigger manual compaction or reduce
     min.cleanable.dirty.ratio

MEDIUM-TERM:
  1. Add brokers and rebalance partitions (kafka-reassign-partitions)
  2. Set both time-based AND size-based retention:
     log.retention.hours=168 + log.retention.bytes=100GB per partition
  3. Enable compression: compression.type=lz4 (30-50% space savings)
  4. Tiered storage (KIP-405): move cold data to S3/HDFS automatically
     (available in Kafka 3.6+ as early access)

LONG-TERM:
  1. Capacity planning: monitor growth rate, project disk needs
  2. Alerts at 70%, 80%, 90% disk usage per broker
  3. Topic governance: enforce retention policies via admin controls
  4. Separate hot (SSD) and cold (HDD) storage tiers
  5. Archive old data to data lake before Kafka retention expires

FORMULA:
  Disk needed per broker = 
    (daily_ingestion_GB × retention_days × replication_factor) / num_brokers
    + 20% headroom
```

### Q13: How do you guarantee exactly-once when writing from Kafka to an external database?

```
ANSWER:

Kafka's built-in EOS only works Kafka → Kafka.
For Kafka → external DB, you need application-level exactly-once.

PATTERN 1: IDEMPOTENT WRITES (Recommended)
  Consumer reads message → Upsert into DB using natural key.
  Replay-safe: same message written twice = same result.
  
  // Pseudo-code
  for (record : records) {
    db.upsert(record.key(), record.value());  // INSERT ... ON CONFLICT UPDATE
  }
  consumer.commitSync();

PATTERN 2: OFFSET IN DATABASE (Strongest guarantee)
  Store Kafka offset in SAME database transaction as business data.
  
  BEGIN TRANSACTION;
    INSERT INTO orders (id, data) VALUES (...);
    UPDATE kafka_offsets SET offset = 42 
      WHERE topic='orders' AND partition=0;
  COMMIT;
  
  On startup:
    SELECT offset FROM kafka_offsets WHERE topic='orders' AND partition=0;
    consumer.seek(partition, offset);
  
  // Don't use consumer.commitSync() — offset is in your DB.

PATTERN 3: OUTBOX PATTERN (for Kafka-first architectures)
  Write to DB → outbox table → CDC (Debezium) → Kafka
  
  DB Transaction:
    INSERT INTO orders (...);
    INSERT INTO outbox (event_type, payload) VALUES ('OrderCreated', ...);
  COMMIT;
  
  Debezium reads outbox → publishes to Kafka → deletes from outbox.
  Guaranteed: DB write and Kafka publish are atomic.
```

### Q14: Explain Kafka's ISR shrinkage problem and its impact

```
ANSWER:

ISR SHRINKAGE: Followers removed from ISR because they can't keep up.

  Normal: ISR = {Broker 0 (L), Broker 1, Broker 2}   (3 in ISR)
  Degraded: ISR = {Broker 0 (L), Broker 1}            (2 in ISR)
  Critical: ISR = {Broker 0 (L)}                        (1 in ISR)

  WHY IT HAPPENS:
  • Follower GC pause > replica.lag.time.max.ms
  • Follower disk I/O saturated (can't write fast enough)
  • Network partition between leader and follower
  • Follower undergoing log recovery after restart
  • Uneven partition distribution (overloaded broker)

  WHY IT'S DANGEROUS:
  
  With min.insync.replicas=2 and ISR={Broker 0}:
  → ALL writes rejected with NotEnoughReplicasException
  → Producer is effectively blocked
  → If acks=1: writes succeed but only 1 copy exists
     → If that broker dies: DATA LOST
  
  CASCADING FAILURE:
  ISR shrinks → remaining ISR members handle more load → they become
  slow → MORE ISR shrinkage → eventually ISR = {leader only} → one
  broker failure = data loss or unavailability.

  MONITORING:
  kafka.server:name=UnderReplicatedPartitions (should be 0)
  kafka.server:name=UnderMinIsrPartitionCount (should be 0)
  kafka.server:name=IsrShrinksPerSec
  kafka.server:name=IsrExpandsPerSec

  FIXES:
  1. Right-size brokers (CPU, disk, memory, network)
  2. Even partition distribution (leader balance)
  3. Tune replica.lag.time.max.ms (don't make too short)
  4. Monitor and alert on ISR shrinkage IMMEDIATELY
  5. Dedicated network for inter-broker replication
```

### Q15: How would you implement exactly-once deduplication at scale?

```
ANSWER:

Context: Even with at-least-once delivery, consumers may see duplicates
(network retries, rebalances, restart). Need application-level dedup.

APPROACH 1: IDEMPOTENT PROCESSING (stateless)
  Each message has a natural key (order_id, payment_id).
  Processing is an upsert → running it twice = same result.
  
  Simplest and most common. Works when operation is naturally idempotent.

APPROACH 2: DEDUP TABLE (stateful)
  ┌─────────────────────────────────────────────────────────────┐
  │  Redis/DB: dedup_keys = { "msg-uuid-123": true, ... }      │
  │                                                             │
  │  Consumer:                                                  │
  │    if redis.setnx(message.id, 1, TTL=24h):                 │
  │      process(message)       // first time: process          │
  │    else:                                                    │
  │      skip(message)          // duplicate: ignore            │
  └─────────────────────────────────────────────────────────────┘
  
  TTL should be > max(retry_delay + consumer_lag).
  Storage cost: ~100 bytes per message ID.

APPROACH 3: BLOOM FILTER (probabilistic dedup)
  For extremely high volume where Redis cost is prohibitive.
  
  False positives (skip a new message) are acceptable.
  False negatives (process a dup) → use with idempotent processing.
  
  Space: ~1GB Bloom filter for 1 billion messages at 1% FP rate.

APPROACH 4: KAFKA STREAMS (exactly-once built-in)
  processing.guarantee=exactly_once_v2
  Handles dedup internally using transactional producer + consumer offsets.
  Only works for Kafka→Kafka stream processing.
```

---

## 18. Quick Reference Card

```
┌──────────────────────────────────────────────────────────────────────────┐
│                KAFKA INTERVIEW QUICK REFERENCE                            │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  "What is Kafka?"                                                        │
│  → Distributed event streaming platform / commit log.                    │
│    Publish, store, and process streams of events.                        │
│                                                                          │
│  "Why not a regular queue?"                                              │
│  → Retention (replay), multiple consumer groups, high throughput,        │
│    ordering per partition, durable storage.                               │
│                                                                          │
│  "How does it achieve high throughput?"                                   │
│  → Sequential I/O, OS page cache, zero-copy (sendfile), batching,       │
│    compression, partitioned parallelism.                                  │
│                                                                          │
│  "How does it ensure durability?"                                        │
│  → Replication (ISR), acks=all, min.insync.replicas, disk persistence.  │
│                                                                          │
│  "How does it handle failures?"                                          │
│  → ISR-based leader election, controller manages metadata,              │
│    producers retry with idempotence, consumers replay from offset.       │
│                                                                          │
│  "Exactly-once?"                                                         │
│  → Idempotent producer (dedup within partition) +                        │
│    Transactional API (atomic cross-partition) +                          │
│    read_committed consumers. Only Kafka→Kafka.                           │
│                                                                          │
│  "When NOT Kafka?"                                                       │
│  → RPC, small scale, complex routing, priority queues,                   │
│    sub-ms latency, simple job queues.                                    │
│                                                                          │
│  "Kafka 4.0?"                                                            │
│  → ZooKeeper removed. KRaft only. ~2M partitions/cluster.               │
│    Simpler ops, faster failover.                                         │
│                                                                          │
│  NUMBERS TO QUOTE:                                                       │
│  • Throughput: 1M+ msgs/sec per cluster                                  │
│  • Latency: 2-50ms (acks=all, same DC)                                  │
│  • LinkedIn: 7T+ messages/day                                            │
│  • Uber: Trillions messages/day, PB-scale                                │
│  • Max partitions (KRaft): ~2M per cluster                               │
│  • Replication: typically RF=3, min.insync=2                             │
│  • Retention: configurable (default 7 days)                              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Sources & Further Reading

| Source | URL | Topic |
|--------|-----|-------|
| Apache Kafka Design Docs | kafka.apache.org/documentation/#design | Architecture, persistence, replication |
| KIP-500 (KRaft) | cwiki.apache.org/confluence/display/KAFKA/KIP-500 | ZooKeeper removal |
| KIP-98 (EOS) | cwiki.apache.org/confluence/display/KAFKA/KIP-98 | Exactly-once semantics |
| Confluent Blog | confluent.io/blog | Deep dives, best practices |
| Uber Engineering | uber.com/blog | uForwarder, Kafka at scale |
| PagerDuty Post-Mortem | pagerduty.com/eng | Producer-per-request outage |
| LinkedIn Engineering | engineering.linkedin.com | Kafka origins, Burrow |
| Jay Kreps — "The Log" | engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying | Kafka's theoretical foundation |
| Martin Kleppmann — DDIA | dataintensive.net | Distributed systems context |
| Conduktor Blog | conduktor.io/blog | EOS analysis, outage analysis |
