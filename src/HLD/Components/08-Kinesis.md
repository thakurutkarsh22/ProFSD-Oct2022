# Amazon Kinesis — The Complete Deep Dive

> **Difficulty:** Hard | **Time:** 6-8 hours | **Priority:** Must Know  
> **Sources:** AWS Kinesis Data Streams Developer Guide, AWS Streaming Data Solutions Whitepaper, AWS re:Invent (ANT325, ANT402, ANT418), AWS Post-Event Summary (Nov 25, 2020 Kinesis Outage), Netflix Tech Blog, Lyft Engineering, Nextdoor Engineering, Arpit Bhyani (sharding & streaming), Alex Xu (System Design Interview Vol 1 & 2)  
> **For:** Senior Engineers (8+ years) preparing for System Design interviews  
> **Hands-on lab:** [`examples/kinesis-java-demo/`](examples/kinesis-java-demo/) — a runnable Java CLI that creates real streams, sends records, reads them back, simulates hot shards, replays from a timestamp, and splits/merges shards. Every menu option maps to a section of this document.

---

## Table of Contents

1. [What Is Kinesis](#1-what-is-kinesis)
2. [The Kinesis Family — Four Services](#2-the-kinesis-family--four-services)
3. [Core Architecture of Data Streams](#3-core-architecture-of-data-streams)
4. [Shards, Partition Keys & The Hash Ring](#4-shards-partition-keys--the-hash-ring)
5. [Records, Sequence Numbers & Ordering](#5-records-sequence-numbers--ordering)
6. [Producers — PutRecord, PutRecords & KPL](#6-producers--putrecord-putrecords--kpl)
7. [Consumers — KCL, Shared Throughput, Enhanced Fan-Out](#7-consumers--kcl-shared-throughput-enhanced-fan-out)
8. [Checkpointing with DynamoDB](#8-checkpointing-with-dynamodb)
9. [Resharding — Split, Merge & Hot Shards](#9-resharding--split-merge--hot-shards)
10. [On-Demand vs Provisioned Mode](#10-on-demand-vs-provisioned-mode)
11. [Kinesis Data Firehose](#11-kinesis-data-firehose)
12. [Kinesis Data Analytics (Managed Apache Flink)](#12-kinesis-data-analytics-managed-apache-flink)
13. [Kinesis Video Streams](#13-kinesis-video-streams)
14. [Kinesis vs Kafka vs SQS vs MSK](#14-kinesis-vs-kafka-vs-sqs-vs-msk)
15. [Real-World Usage at Scale](#15-real-world-usage-at-scale)
16. [When Kinesis Failed — Production Incidents](#16-when-kinesis-failed--production-incidents)
17. [When Kinesis Cannot Cope — Limitations](#17-when-kinesis-cannot-cope--limitations)
18. [Anti-Patterns That Kill Kinesis Systems](#18-anti-patterns-that-kill-kinesis-systems)
19. [Performance Tuning Cheat Sheet](#19-performance-tuning-cheat-sheet)
20. [Interview Questions — Medium](#20-interview-questions--medium)
21. [Interview Questions — Hard](#21-interview-questions--hard)
22. [Quick Reference Card](#22-quick-reference-card)

---

## 1. What Is Kinesis

**Amazon Kinesis** is AWS's **managed real-time data streaming platform**, launched in **November 2013** as AWS's answer to Apache Kafka. It ingests, buffers, and processes **gigabytes of streaming data per second** from hundreds of thousands of sources, with **sub-second end-to-end latency** and **24h–365d retention**.

Kinesis is NOT a message queue. It is a **distributed, append-only, partitioned log** — the same mental model as Kafka — with one killer difference: **zero servers to run**.

```
Message Queue (SQS):                         Event Stream (Kinesis):

  Producer → [Queue] → Consumer               Producer → [Append-Only Log] → Many Consumers
                                                              │
  ✗ Consumed → deleted                                        ├─ Consumer Group A (replay)
  ✗ One consumer per message                                  ├─ Consumer Group B (analytics)
  ✗ No replay                                                 └─ Consumer Group C (archive)
  ✓ Simple poll                                ✓ Multiple independent readers
  ✓ Unlimited throughput                       ✓ Replay from any sequence # within retention
                                               ✓ Strict ordering per shard (partition key)
                                               ✓ Stateful stream processing possible
```

### The Fundamental Promise of Kinesis

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              KINESIS DATA STREAMS = FIVE GUARANTEES                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. DURABLE     2. ORDERED     3. REPLAYABLE    4. REAL-TIME   5. MANAGED   │
│  ────────────   ────────────   ─────────────    ───────────    ──────────   │
│  Synchronous    Strict FIFO    Data retained    <1 sec end-    No brokers   │
│  replication    per partition  24 h (default)   to-end latency No ZooKeeper │
│  across 3 AZs   key (shard)    up to 365 d      (EFO: ~70 ms) No capacity   │
│                                                                  planning   │
│                                                                  (On-Demand)│
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why Kinesis Exists — The Problem It Solves

```
WITHOUT A STREAMING LOG (Direct Coupling):

   ┌──────────┐                       ┌────────────┐
   │ Clickstream│──────────────HTTP────▶│ Analytics  │
   │ (1M events/s)                    │ (1 instance)│
   └──────────┘                       └────────────┘
         │                                   │
         │     ┌────────────┐                │
         └─────▶ Fraud Svc   │◀───────────── (same data)
         │     └────────────┘
         │     ┌────────────┐
         └─────▶ DW ETL      │
               └────────────┘

   Problems:
   - Producer must know every consumer (tight coupling)
   - If Analytics is slow, producer blocks
   - Adding a consumer = re-deploy producer
   - A spike overwhelms slow consumers
   - No replay → bug in consumer = data loss

WITH KINESIS DATA STREAMS (Decoupled, Buffered, Replayable):

   ┌────────────┐     ┌─────────────────────────────────┐      ┌────────┐
   │ Clickstream │────▶│ Kinesis Stream (N shards)        │─────▶│Analytics│
   │  (producer) │     │ ┌─────┐ ┌─────┐ ... ┌─────┐      │      └────────┘
   └────────────┘     │ │shard│ │shard│     │shard│      │─────▶┌────────┐
                       │ │  0  │ │  1  │     │ N-1 │      │      │Fraud Svc│
                       │ └─────┘ └─────┘     └─────┘      │      └────────┘
                       └─────────────────────────────────┘─────▶┌────────┐
                         (append-only, replicated 3 AZ)        │ DW ETL  │
                         (retention: 24h–365d)                  └────────┘
                                                                 │
   - Producer writes once; any number of consumers read independently
   - Consumers at their own pace (stream retains data)
   - Replay last 7 days if consumer had a bug
   - Spikes absorbed by retention window
```

### Kinesis Data Streams — The Headline Numbers

```
Per shard (provisioned mode):
  Writes:          1 MiB / sec    or  1,000 records / sec (whichever first)
  Reads (shared):  2 MiB / sec    (across ALL shared consumers)
  Reads (EFO):     2 MiB / sec    PER consumer (dedicated pipe)
  Record size:     up to 1 MiB

Per stream (provisioned): up to 100,000 shards per stream (soft limit)
                          → 100 GiB/sec write, 200 GiB/sec read theoretical

On-Demand mode:
  Default:         200 MiB/sec write (200 MiB/sec × 1 byte = 200 MB)
                   400 MiB/sec read
  Auto-scales:     Doubles capacity when sustained traffic > 50% of peak in last 30 days
                   (up to the service quotas)

Retention:
  Default:         24 hours (free)
  Extended:        up to 7 days (small per-GB-hour charge)
  Long-term:       up to 365 days (higher per-GB-hour; backed by different storage tier)

Latency:
  Producer → stream:    ~50 ms (PutRecords with KPL aggregation)
  Stream → consumer (shared polling):  ~200 ms–2 s
  Stream → consumer (EFO push):         ~70 ms p50, ~200 ms p99
```

---

## 2. The Kinesis Family — Four Services

People say "Kinesis" and mean four very different things. A senior engineer must distinguish them instantly.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                        THE KINESIS FAMILY (as of 2024)                               │
├────────────────────┬─────────────────────────────────────────────────────────────────┤
│ Service             │ What it is                                                       │
├────────────────────┼─────────────────────────────────────────────────────────────────┤
│ Kinesis Data        │ Real-time, durable, partitioned event log.                       │
│ Streams (KDS)       │ You own producers and consumers. You retain data 24h–365d.      │
│                     │ "Managed Kafka-lite."                                            │
│                     │                                                                   │
│ Kinesis Data        │ Fully managed, zero-code pipe: source → transform → sink         │
│ Firehose (KDF)      │ (S3 / Redshift / OpenSearch / Splunk / HTTP endpoint).           │
│                     │ Buffers up to 900 s or 128 MiB, no retention, no replay.         │
│                     │ Renamed to "Amazon Data Firehose" in Feb 2024.                   │
│                     │                                                                   │
│ Kinesis Data        │ Managed Apache Flink. Continuous SQL / DataStream API over       │
│ Analytics (KDA)     │ KDS / Kafka / MSK. Renamed to "Managed Service for Apache Flink"│
│                     │ in Aug 2023.                                                     │
│                     │                                                                   │
│ Kinesis Video       │ WebRTC + HLS + DASH streaming for video/audio/radar/depth       │
│ Streams (KVS)       │ from millions of devices (doorbell cameras, factory floor, cars) │
│                     │ to the cloud. Time-indexed fragments in MKV container.            │
└────────────────────┴─────────────────────────────────────────────────────────────────┘
```

```
How they compose in a typical architecture:

                                         ┌─────────────────┐
                                         │ Firehose        │──▶ S3 (raw, compressed, partitioned)
                                         │ (S3 sink)       │──▶ Redshift / OpenSearch
                                         └─────────────────┘
                                                ▲
                                                │
   ┌──────────────┐      ┌─────────────┐        │
   │ IoT / App    │─────▶│ Data Streams│────────┤
   │ / KPL        │      │ (KDS)       │        │
   └──────────────┘      └─────────────┘        │
                                ▲                │
                                │                ▼
                                │       ┌──────────────────┐
                                │       │ KDA / Managed    │──▶ back to KDS
                                │       │ Flink (SQL/DS)   │──▶ DynamoDB / RDS
                                │       └──────────────────┘

   ┌──────────────┐      ┌─────────────┐
   │ Cameras      │─────▶│ Video Streams│──▶ Rekognition Video
   └──────────────┘      └─────────────┘
```

From here on, unless I say otherwise, **"Kinesis" means Kinesis Data Streams (KDS)** — this is what senior interviews drill on.

---

## 3. Core Architecture of Data Streams

Every KDS abstraction collapses to this picture:

```
                                 STREAM  (my-clickstream)
┌───────────────────────────────────────────────────────────────────────────────┐
│                                                                                │
│   Shard 0                  Shard 1                  Shard 2       ...  Shard N-1│
│  ┌─────────────┐          ┌─────────────┐          ┌─────────────┐             │
│  │ seq=100 ... │          │ seq=501 ... │          │ seq=920 ... │             │
│  │ seq=101 ... │          │ seq=502 ... │          │ seq=921 ... │             │
│  │ seq=102 ... │ (append) │ seq=503 ... │ (append) │ seq=922 ... │   (append)  │
│  │   ...       │  only    │   ...       │  only    │   ...       │    only     │
│  │ seq=4500 ◄──┼─ tail    │ seq=4800 ◄──┼─ tail    │ seq=5001 ◄──┼── tail      │
│  └─────────────┘          └─────────────┘          └─────────────┘             │
│                                                                                │
│  each shard: 1 MiB/s IN, 2 MiB/s OUT, synchronously replicated to 3 AZs         │
│              retention 24h (default) up to 365 days                            │
└───────────────────────────────────────────────────────────────────────────────┘
           ▲                         ▲                        ▲
           │                         │                        │
      ┌────┴────┐              ┌─────┴─────┐            ┌─────┴─────┐
      │Producer │              │ Producer  │            │ Producer  │
      │PK="u_1" │              │ PK="u_42" │            │ PK="u_98" │
      └─────────┘              └───────────┘            └───────────┘
```

### Key Invariants (Memorize These)

1. **A stream is a named collection of shards.** That's it.
2. **A shard is an ordered, append-only log.** Sequence numbers are monotonically increasing **within a shard**. Across shards, there is NO global order.
3. **A record lives in exactly one shard**, chosen by hashing the producer-supplied `PartitionKey` onto the shard's hash-range.
4. **Reads are offset-based** (sequence number) and **pull-based** (shared) or **push-based** (EFO over HTTP/2).
5. **Shards are the unit of throughput, cost, parallelism, and ordering.** Every capacity conversation ends with "how many shards?"

### Anatomy of the Data Plane

```
                     ┌──────────────────────────────────────────────────┐
                     │            KINESIS DATA PLANE (per shard)         │
                     │                                                   │
   PutRecord(s) ────▶│   Frontend ── Leader replica ── Replicas (2 AZ)  │──▶ GetRecords
                     │      │             │              │                │     (shared)
                     │      └─ rate limit │              │                │
                     │                    ▼              ▼                │──▶ SubscribeToShard
                     │            (synchronous replication, quorum-ACK)   │     (Enhanced FO)
                     └──────────────────────────────────────────────────┘

   AWS does NOT publicly document the internals, but from re:Invent ANT402 / public talks:
   - Each shard is backed by a storage cell (paxos-like quorum, 3 replicas across 3 AZs)
   - Front-end fleet terminates customer TLS, enforces throttles, signs records with seq#
   - Long-term retention (>24h) is tiered storage — pulled on GetRecords if older
```

---

## 4. Shards, Partition Keys & The Hash Ring

This is the single most important section in this document. **Hot shards are the #1 cause of Kinesis pain in production.**

### 4.1 The MD5 Hash Ring

When you call `PutRecord(StreamName, Data, PartitionKey)`, Kinesis does:

```
explicit_hash = MD5(PartitionKey)        // 128-bit integer
shard         = find shard whose [StartingHashKey, EndingHashKey] contains explicit_hash
append(shard, Data)                      // returns sequence number
```

Every shard owns a contiguous slice of the 128-bit hash space:

```
Stream with 4 shards (uniform split):

  0 ─────────────────── 2^128 ─ 1
  │                                            │
  ├────────┬────────┬────────┬────────┤
  │ shard 0│ shard 1│ shard 2│ shard 3│
  │   25%  │   25%  │   25%  │   25%  │
  └────────┴────────┴────────┴────────┘

  PK="user_42"  → MD5 = 0x3f...   → falls in shard 1's range
  PK="user_99"  → MD5 = 0x8a...   → falls in shard 2's range
  PK="orderId=5"→ MD5 = 0xe1...   → falls in shard 3's range
```

Rule of thumb:
- **Same `PartitionKey` → same shard → ordered** (this is how you get FIFO per entity)
- **Different `PartitionKey` → likely different shards → parallel**

### 4.2 Hot Shards — The Classic Failure

A hot shard is a shard receiving **more than its 1 MiB/s or 1,000 records/s** share while other shards sit idle. Symptoms:

```
  ProvisionedThroughputExceededException   ← producer retries with exponential backoff
  IteratorAgeMilliseconds spikes            ← consumer falls behind ONE shard
  WriteProvisionedThroughputExceeded        ← CloudWatch metric spikes per shard
```

```
  Stream with 4 shards, but PK = "tenant_id" and tenant T1 = 80 % of traffic:

  ┌────────┬────────┬────────┬────────┐
  │shard 0 │shard 1 │shard 2 │shard 3 │
  │ T1-80% │ T2,T3  │ T4,T5  │ T6,T7  │
  │ 🔥🔥🔥 │  10%   │  5%    │  5%    │
  └────────┴────────┴────────┴────────┘
      ▲
      └── 1 MiB/s cap hit, throttling, all T1 events queuing at producer side
```

### 4.3 Fixing a Hot Shard — Four Strategies

```
STRATEGY 1 — Better PartitionKey
  Bad:  PartitionKey = tenant_id        (80% traffic on one tenant = hot)
  Good: PartitionKey = tenant_id + "#" + random(0..31)   ← salted key
        but then you LOSE ordering per tenant. OK only if order doesn't matter.

STRATEGY 2 — Hierarchical PartitionKey
  PartitionKey = tenant_id + "#" + user_id
  → T1 traffic now spreads across all of T1's users
  → Still preserves per-user order (most apps need per-user, not per-tenant, order)

STRATEGY 3 — Split the hot shard
  aws kinesis split-shard \
      --stream-name my-stream \
      --shard-to-split shardId-000000000001 \
      --new-starting-hash-key <midpoint>
  → Doubles capacity in the hot range
  → Old shard goes CLOSED (still readable until retention expires)

STRATEGY 4 — Switch to On-Demand mode
  Kinesis auto-splits hot shards every 15 minutes when sustained p99 > 50%
  → Simpler, no PartitionKey engineering required
  → Costs ~40% more per GB ingested, but saves engineering time
```

### 4.4 The ExplicitHashKey Escape Hatch

If you need precise control over which shard a record lands on (e.g., to co-locate related records or to bypass a bad PartitionKey), use `ExplicitHashKey`:

```python
# Force this record into shard whose range contains 0x4000...
kinesis.put_record(
    StreamName="s",
    Data=b"...",
    PartitionKey="anything",           # ignored for routing
    ExplicitHashKey="85070591730234615865843651857942052864"  # = 2^125
)
```

Real-world use: **Ordered multi-producer fan-in**. Two producers both want events for `order_42` on the same shard. Instead of hoping their PKs MD5-collide, they pre-compute the hash.

---

## 5. Records, Sequence Numbers & Ordering

### 5.1 The Record Structure

```json
{
  "SequenceNumber":  "49607163008663746234...",   // 128-bit, lexicographically increasing
  "ApproximateArrivalTimestamp": "2026-04-18T10:00:00.123Z",
  "Data":            "base64-encoded up to 1 MiB",
  "PartitionKey":    "user_42"
}
```

The `SequenceNumber` is **not** monotonic across shards. It is a **structured string** that contains:
- A shard-iterator-scoped counter
- The timestamp of ingestion (roughly)
- Some opaque AWS internal bits

This is why you sort records **within a shard** by sequence number, but across shards you either:
1. Use `ApproximateArrivalTimestamp` (not strictly accurate — can differ by ~10 ms across shards), or
2. Embed an application-level sequence/Lamport clock in the payload.

### 5.2 Ordering Guarantees — The Fine Print

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ORDERING GUARANTEE LADDER                                                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. Per-shard:           STRICT total order on SequenceNumber             │
│  2. Per-PartitionKey:    Strict order IF you never reshard the stream    │
│  3. Per-PartitionKey across resharding:                                   │
│     Order preserved BUT you must drain the parent shard BEFORE           │
│     consuming from the child shards (KCL does this for you).             │
│  4. Across shards:       NO ordering guarantee. Ever.                    │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.3 At-Least-Once, Not Exactly-Once

Kinesis **guarantees at-least-once delivery** out of the box:
- Producers retry on network errors → may create duplicates.
- Consumers process a batch, checkpoint, then crash before the checkpoint flushes → replay on restart.

Exactly-once requires:
- **Idempotent consumer** (dedup on record hash or business key), or
- **KCL + transactional sink** (e.g., `DynamoDB ConditionExpression`), or
- **Flink (KDA) with exactly-once checkpoint barriers** to a transactional sink (S3 commit, Kafka txn).

---

## 6. Producers — PutRecord, PutRecords & KPL

### 6.1 The Three Producer APIs

```
┌──────────────────┬──────────────────────┬────────────────────────────────────┐
│ API               │ Records per call      │ When to use                        │
├──────────────────┼──────────────────────┼────────────────────────────────────┤
│ PutRecord         │ 1                     │ Rare. Latency-sensitive + low rate. │
│ PutRecords        │ 1–500 (max 5 MiB)     │ Default for batched producers.      │
│ KPL (KCL client)  │ aggregates internally │ High-throughput (>1 K rec/s/prod).  │
│                   │ → 1 PutRecords call    │ Can bundle ~100 user records into   │
│                   │                        │ 1 Kinesis record (<1 MiB each).      │
└──────────────────┴──────────────────────┴────────────────────────────────────┘
```

### 6.2 Why `PutRecords` Is NOT Atomic

```
PutRecords response:
{
  "FailedRecordCount": 3,
  "Records": [
    { "SequenceNumber": "4960...", "ShardId": "shardId-1" },    // OK
    { "SequenceNumber": "4961...", "ShardId": "shardId-2" },    // OK
    { "ErrorCode": "ProvisionedThroughputExceededException",    // FAILED
      "ErrorMessage": "Rate exceeded for shard shardId-3" },
    ...
  ]
}
```

The naïve bug: ignore `FailedRecordCount`. The fix:

```python
def send_with_retry(records, max_retries=5):
    pending = records
    for attempt in range(max_retries):
        resp = kinesis.put_records(StreamName=S, Records=pending)
        if resp["FailedRecordCount"] == 0:
            return
        # only retry records that failed, preserve order
        pending = [pending[i] for i, r in enumerate(resp["Records"]) if "ErrorCode" in r]
        time.sleep((2 ** attempt) * 0.1 + random.random() * 0.1)  # exp backoff + jitter
    raise RuntimeError(f"{len(pending)} records still failing")
```

### 6.3 The Kinesis Producer Library (KPL)

KPL is a C++ daemon that your JVM/Python process talks to over IPC. Killer features:

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ KPL KILLER FEATURES                                                           │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│ 1. AGGREGATION                                                                │
│    Packs many user records into ONE Kinesis record (up to 1 MiB).              │
│    → Defeats the 1,000 rec/s limit per shard                                  │
│    → Billing is per record, so 100× cheaper                                    │
│    → Records are de-aggregated automatically by KCL v2                         │
│                                                                               │
│ 2. COLLECTION (BATCHING)                                                       │
│    Waits up to RecordMaxBufferedTime (default 100 ms)                          │
│    then flushes via one PutRecords.                                            │
│                                                                               │
│ 3. ASYNC + BACK-PRESSURE                                                       │
│    Producer thread returns a ListenableFuture.                                 │
│    When buffer > threshold, KPL applies back-pressure (rejects or blocks).     │
│                                                                               │
│ 4. RETRY / RATE-LIMITING                                                       │
│    Exponential back-off, per-shard rate limiter, shard map cache.              │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

Diagram:

```
                 ┌─────────────────────────────────────────────┐
                 │                KPL PROCESS                   │
                 │                                              │
 user.addRecord  │  ┌──────────┐   ┌──────────┐   ┌──────────┐  │   PutRecords
  ─────────────▶│  │buffer    │──▶│aggregator│──▶│collector │──┼─────────────▶ Kinesis
                 │  │(per shd) │   │(1 MiB)   │   │(500 rec) │  │
                 │  └──────────┘   └──────────┘   └──────────┘  │
                 │       ▲              ▲              ▲        │
                 │       └─── 100 ms ───┴──  5 MiB  ────┘        │
                 └─────────────────────────────────────────────┘
```

**Trade-off:** KPL adds 100–200 ms p50 latency. Do NOT use for trading, fraud-detection with sub-50 ms SLAs. Use `PutRecord` with a dedicated HTTP/2 connection.

---

## 7. Consumers — KCL, Shared Throughput, Enhanced Fan-Out

### 7.1 Two Consumption Models

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      SHARED-THROUGHPUT (CLASSIC)                            │
│                                                                             │
│           ┌────────────────────────┐                                        │
│           │   shard (2 MiB/s out)   │                                        │
│           └─────────┬──────────────┘                                        │
│                     │   shared across ALL consumers                         │
│           ┌─────────┼───────────────┐                                        │
│           ▼         ▼               ▼                                        │
│      consumer A  consumer B     consumer C       ← each gets <2 MiB/s combined│
│      GetRecords   GetRecords     GetRecords                                  │
│      (5 /s/shd)   (5 /s/shd)     (5 /s/shd)       ← 5 GetRecords/s/shd TOTAL │
│                                                                             │
│  Consequence: add a 3rd consumer → all three slow down                      │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                      ENHANCED FAN-OUT (EFO)                                 │
│                                                                             │
│           ┌────────────────────────┐                                        │
│           │   shard                 │  each consumer registers → gets       │
│           └─┬────────────┬─────────┘  its OWN 2 MiB/s pipe                  │
│             │            │            (dedicated, push-based over HTTP/2)   │
│             │  2 MiB/s   │  2 MiB/s                                         │
│             ▼            ▼                                                   │
│        consumer A    consumer B         (up to 20 per stream)               │
│        SubscribeToShard                 ~70 ms p50 latency                   │
│                                                                             │
│  Consequence: 20 independent consumers at full throughput each              │
│  Cost: $0.015 per shard-hour per consumer (≈ $11/mo per consumer per shard) │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 The Kinesis Client Library (KCL)

KCL is an open-source Java/Python library that solves the three hardest problems of building a Kinesis consumer:

```
  1. Shard discovery                    → periodic ListShards; reacts to resharding
  2. Coordination across workers        → DynamoDB lease table (one row per shard)
  3. Checkpointing                      → DynamoDB (same table; atomic conditional update)
```

#### KCL Lease Table in DynamoDB

```
Partition key: leaseKey (= shardId)
┌─────────────────────┬──────────┬───────────────┬──────────────┬────────┐
│ leaseKey             │ owner    │ leaseCounter   │ checkpoint    │ status │
├─────────────────────┼──────────┼───────────────┼──────────────┼────────┤
│ shardId-0000000000   │ worker-7 │ 142            │ 4960718...    │ ACTIVE │
│ shardId-0000000001   │ worker-3 │ 89             │ 4960816...    │ ACTIVE │
│ shardId-0000000002   │ worker-7 │ 142            │ TRIM_HORIZON   │ LEASED │
│ shardId-0000000003   │          │ 0              │ —              │ AVAILABLE│
└─────────────────────┴──────────┴───────────────┴──────────────┴────────┘

When worker-7 loses its heartbeat (no leaseCounter++ within failover timeout),
worker-3 steals shards 0000 and 0002 on the next lease-taker run (~every 10 s).
```

Principles:
- **Goal:** each worker owns roughly `shard_count / worker_count` shards.
- **Stealing:** if workers have different counts, a worker with fewer leases steals from a worker with more.
- **Heartbeat:** `leaseCounter` is incremented every `failoverTimeMillis / 3` (default 10 s, i.e., 3.3 s).
- **Starvation avoidance:** the lease-stealing algorithm is probabilistic to prevent ping-pong.

#### The Processor Interface (KCL v2)

```java
public class ClickstreamProcessor implements ShardRecordProcessor {
    private String shardId;

    public void initialize(InitializationInput input) {
        this.shardId = input.shardId();
    }

    public void processRecords(ProcessRecordsInput input) {
        for (KinesisClientRecord r : input.records()) {
            handle(r.partitionKey(), r.data());
        }
        // Checkpoint after successful processing
        try {
            input.checkpointer().checkpoint(input.records().get(input.records().size() - 1));
        } catch (InvalidStateException | ShutdownException e) {
            // abort — another worker owns this lease now
        }
    }

    public void leaseLost(LeaseLostInput input)            { /* stop work */ }
    public void shardEnded(ShardEndedInput input)          throws Exception {
        // Parent shard drained after a split/merge.
        // KCL requires us to call checkpoint() to unblock children.
        input.checkpointer().checkpoint();
    }
    public void shutdownRequested(ShutdownRequestedInput i) throws Exception {
        i.checkpointer().checkpoint();
    }
}
```

---

## 8. Checkpointing with DynamoDB

### 8.1 Why Checkpoint?

A consumer reads a batch, processes it, and must remember "I processed up to sequence number X so I don't replay it on restart." That bookmark is the **checkpoint**.

Kinesis itself does **not** store per-consumer offsets (unlike Kafka's `__consumer_offsets` topic). You store them yourself. KCL uses DynamoDB; you could use Redis or your own table.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                 CHECKPOINT LIFECYCLE                                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│   1. Worker reads batch B = [seq 100, 101, ..., 199] via GetRecords        │
│   2. Worker processes each record (may take seconds)                       │
│   3. Worker writes checkpoint=199 to DynamoDB                               │
│      UPDATE lease SET checkpoint='199'                                      │
│      WHERE leaseKey='shard-0' AND leaseCounter=142  (conditional)          │
│   4. Worker issues next GetRecords with ShardIterator starting after 199    │
│                                                                            │
│   Failure scenarios:                                                        │
│                                                                            │
│   (a) Crash after step 2, before step 3:                                   │
│       → another worker takes lease (step 1 from checkpoint=99)             │
│       → re-processes batch B                                                │
│       → AT-LEAST-ONCE; consumer MUST be idempotent                         │
│                                                                            │
│   (b) Checkpoint every record vs every batch:                               │
│       - Every record  → 10× DynamoDB cost; tiny duplicate window            │
│       - Every batch   → cheapest; replay 1 batch on crash                  │
│       - Every N sec   → balance; default KCL pattern                       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Checkpoint Too Often → DynamoDB Throttle

Classic production bug: producer sends 50 K records/s, KCL processes them one at a time and checkpoints every record → DynamoDB lease table needs 50 K WCU per shard. With 200 shards that's 10 M WCU, which exceeds your table's provisioned capacity and DynamoDB throttles, which slows KCL, which falls behind, which spikes `IteratorAge`, which wakes up the on-call.

**Rules of thumb:**
- Checkpoint every 60 s OR every 10,000 records — whichever comes first.
- Size lease-table WCU ≥ `(workers × 0.3)` (heartbeats) + `(shards × 1)` (checkpoints every 60 s).
- Use on-demand billing for the lease table — the traffic is spiky and low-volume.

### 8.3 Special Checkpoint Values

```
TRIM_HORIZON  — oldest record still in the stream (~retention seconds old)
LATEST         — only records after SubscribeToShard (skip backlog)
AT_TIMESTAMP  — records at or after a specific time (replay starting T-1h)
AT_SEQUENCE_NUMBER | AFTER_SEQUENCE_NUMBER — resume from an exact point
```

This is the superpower over SQS: **you can replay last 7 days in minutes** by starting from TRIM_HORIZON or AT_TIMESTAMP.

---

## 9. Resharding — Split, Merge & Hot Shards

### 9.1 Why Reshard?

```
Scale up: add capacity (split hot shard into two)
Scale down: reduce cost (merge two cold adjacent shards)
Rebalance: fix skew (split-merge pair can move a hash boundary)
```

### 9.2 Split Operation

```
Before (shard 1 owns hash range [A, B], hot):

  [A────────1────────B]

After aws kinesis split-shard (mid = (A+B)/2):

  [A────1a────M]    [M────1b────B]   ← two new OPEN shards
  [A────────1────────B] CLOSED        ← parent, still readable until retention ends

  Both parent and children exist simultaneously.
  New writes with PK in [A, M] → shard 1a
  New writes with PK in [M, B] → shard 1b
  Old unread records in parent → consumers drain them from parent,
    THEN move to children (KCL handles this).
```

### 9.3 The "ShardEnded" Handshake

Consumers must NOT start reading a child shard until they have fully drained the parent — otherwise you'd break per-PartitionKey ordering:

```
  Time →

  Parent shard 1 (CLOSED):  [... old records ...]  ← must be drained first
                                                  │
                                                  ▼ KCL calls shardEnded()
                                                  ▼ checkpoint()
  Child shard 1a (OPEN):    (starts being read)
  Child shard 1b (OPEN):    (starts being read)
```

If you write a consumer without KCL, you must implement this handshake yourself (`DescribeStreamSummary` → build shard tree → track parent-child edges). KCL lease table has a `parentShardIds` column for exactly this.

### 9.4 Merge Operation

```
Before: two cold adjacent shards
  [────shard 4────][────shard 5────]

After:
  [────shard 4────][────shard 5────] CLOSED
                                     │
                                     ▼
  [──────────shard 6──────────────] OPEN (union of hash range)
```

### 9.5 Common Resharding Mistake

Every split doubles the bill for that shard. Don't split blindly. **First check if you have skew** (CloudWatch `IncomingBytes` per shard). If traffic is uniform and you're just globally over capacity, add shards evenly instead — or switch to On-Demand.

---

## 10. On-Demand vs Provisioned Mode

Launched Nov 2021, On-Demand removes capacity planning at a ~25-40 % cost premium.

```
┌─────────────────────┬──────────────────────────┬──────────────────────────┐
│                      │ PROVISIONED              │ ON-DEMAND                 │
├─────────────────────┼──────────────────────────┼──────────────────────────┤
│ Pricing unit         │ $0.015 / shard-hour      │ $0.04 / GB ingested       │
│                      │ + PUT payload units      │ + $0.04 / GB retrieved    │
│ Capacity planning    │ You specify shard count  │ AWS does it                │
│ Scaling speed        │ Manual split-shard (~2m) │ Auto every 15 min          │
│                      │ OR auto via custom Lambda│                            │
│ Max throughput       │ No hard cap (soft 100K  │ 200 MiB/s write default,   │
│                      │ shards/stream)           │ request quota increase     │
│ Hot shards           │ You must fix (split)      │ Auto-split                 │
│ Cost at steady state │ Cheaper if utilization>  │ Cheaper if utilization<40% │
│                      │  ~40 %                   │  (very spiky traffic)      │
│ Conversion           │ One-way switches twice/d │ Free                       │
└─────────────────────┴──────────────────────────┴──────────────────────────┘
```

Senior engineer heuristic:
- **Startup / unpredictable load** → On-Demand. Don't engineer what you can rent.
- **Steady-state pipeline at >1 GB/s** → Provisioned. The math saves 30%.
- **Spiky (2 × 4-hour peaks/day)** → Provisioned + scheduled scaling scripts.

---

## 11. Kinesis Data Firehose

### 11.1 The One-Liner

> "Firehose is a managed ETL pipe: source → buffer → optional Lambda transform → sink. No shards, no consumers, no checkpoints. Zero code."

```
 ┌──────────┐     ┌──────────────────────────────────────────┐     ┌───────────┐
 │ Producer  │────▶│ Firehose                                  │────▶│ S3         │
 │           │     │  ┌─────────────┐   ┌────────────────┐    │     │ Redshift   │
 │ PutRecord │     │  │ 1–900 s OR  │──▶│ Lambda transform│──▶│    │ OpenSearch │
 │ PutBatch  │     │  │ 1–128 MiB   │   └────────────────┘    │     │ Splunk     │
 │ KDS→FH    │     │  │ (buffer)     │           │              │     │ HTTP EP    │
 │ MSK→FH    │     │  └─────────────┘           ▼              │     │ Iceberg    │
 └──────────┘     │  compression (GZIP/Snappy/ZSTD) + format   │     │ (new 2024) │
                   │  conversion (JSON → Parquet/ORC via Glue)  │     └───────────┘
                   └──────────────────────────────────────────┘
```

### 11.2 The Buffer Hint — The ONLY Tunable That Matters

```
BufferingHints = (SizeInMBs, IntervalInSeconds)

  Small buffer (1 MiB, 60 s)  →  many tiny S3 files, high latency to sink
                                  BAD for Athena/Spark queries (small file problem)

  Large buffer (128 MiB, 900s)→  fewer big files, higher ingest latency
                                  GOOD for analytics, BAD for real-time search
```

Rule:
- If sink is **S3 for analytics** → buffer **128 MiB / 900 s** (optimize file size).
- If sink is **OpenSearch for dashboards** → buffer **5 MiB / 60 s** (dashboard freshness).
- If sink is **Splunk / HTTP** → follow the endpoint's rate-limit docs.

### 11.3 KDS → Firehose Is a First-Class Pattern

Do not build Firehose-to-S3 yourself with a KCL consumer. Chain them:

```
 App → KDS (24 h retention, replay, multiple consumers)
           │
           ├──▶ Firehose #1 → S3 (raw, Parquet, partitioned by hour)
           ├──▶ Firehose #2 → OpenSearch (hot 7-day index)
           └──▶ Flink (KDA)  → DynamoDB (real-time aggregates)
```

Firehose removes an entire class of on-call incidents (rolling over S3 files, compaction, retries).

---

## 12. Kinesis Data Analytics (Managed Apache Flink)

Renamed to **Amazon Managed Service for Apache Flink** in August 2023, but you'll still see "KDA" in older docs.

### 12.1 What It Is

A managed Flink cluster (KPU-based pricing where 1 KPU = 1 vCPU + 4 GiB RAM) that reads from Kinesis / MSK / Kafka / Firehose, runs continuous SQL or DataStream code, and writes to Kinesis / Firehose / S3 / DynamoDB / JDBC.

Crucially, it maintains **stateful stream processing** with exactly-once semantics via Flink's checkpoint barriers and S3-backed state backend.

### 12.2 Canonical Example — 5-Minute Tumbling Window

```sql
CREATE TABLE clicks (
    user_id   BIGINT,
    url       VARCHAR,
    ts        TIMESTAMP(3),
    WATERMARK FOR ts AS ts - INTERVAL '5' SECOND
) WITH ('connector' = 'kinesis', 'stream' = 'clickstream', ...);

CREATE TABLE agg_clicks (
    window_start TIMESTAMP(3),
    url          VARCHAR,
    cnt          BIGINT
) WITH ('connector' = 'kinesis', 'stream' = 'aggregated', ...);

INSERT INTO agg_clicks
SELECT TUMBLE_START(ts, INTERVAL '5' MINUTE), url, COUNT(*)
FROM clicks
GROUP BY TUMBLE(ts, INTERVAL '5' MINUTE), url;
```

Six lines of SQL replace a 1,500-line KCL consumer + Redis aggregator + cron compactor.

### 12.3 Watermarks and Late Data

```
  Event-time:  12:00:00       12:00:10       12:00:05    ← out-of-order
  Wall-clock:  12:00:01       12:00:11       12:00:16

  Watermark strategy: "ts - 5 seconds"
  At 12:00:11 (event-time), watermark = 12:00:06
  Event with ts=12:00:05 arrives at 12:00:16 → LATE (watermark already 12:00:06)

  Flink options: drop, send to side-output, or allowLateness(10 s)
```

This is not Kinesis-specific but it's the #1 source of "our dashboard is wrong" incidents when engineers put Kinesis + Flink together without understanding watermarks.

---

## 13. Kinesis Video Streams

Quick tour because senior interviews occasionally ask "have you used it?".

```
Concept                           Equivalent in data-streams world
────────────────────────          ─────────────────────────────────
Stream                             Stream
Fragment (MKV container, ~2–10 s)  Record
Producer SDK (C/Android/iOS/RPi)   KPL
HLS/DASH playback URL              GetRecords (sort of)
GetMedia                           GetRecords (binary/chunked)
ContentType                        application-defined
WebRTC signaling channel          n/a — new in KVS
```

Latency: ~2–5 s with HLS playback; ~200–500 ms end-to-end with WebRTC. Main customers are smart-home cameras (Ring is the reference use case — Amazon owns Ring), industrial CCTV, and autonomous-vehicle telemetry.

---

## 14. Kinesis vs Kafka vs SQS vs MSK

```
┌──────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│                    │ Kinesis KDS  │ Apache Kafka │ Amazon MSK    │ SQS          │
├──────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Model             │ Partitioned  │ Partitioned  │ Managed       │ Queue        │
│                    │ log          │ log          │ Kafka         │              │
│ Unit of parallel  │ Shard        │ Partition    │ Partition     │ (none)       │
│ Max throughput    │ Soft limit   │ Hardware-    │ Hardware-     │ Effectively  │
│                    │ 100 K shards  │ bound         │ bound         │ unlimited    │
│                    │ → 100 GiB/s   │                │                │               │
│ Retention          │ 24 h–365 d    │ Config (∞)    │ Config (∞)    │ ≤14 d         │
│ Ordering           │ Per shard     │ Per partition │ Per partition │ Per FIFO grp  │
│ Delivery           │ At-least-once │ At-least-once │ At-least-once │ At-least-once │
│ Exactly-once       │ Via Flink     │ Idempotent    │ Idempotent    │ FIFO dedup    │
│                    │                │ producer +    │ producer +    │ 5-min window  │
│                    │                │ transactions  │ transactions  │               │
│ Fan-out            │ EFO (push, 20│ Consumer      │ Consumer      │ Each msg read │
│                    │ max/stream)   │ groups (∞)    │ groups (∞)    │ by 1 consumer │
│ Latency (p50)      │ 70–200 ms     │ 2–20 ms       │ 2–20 ms       │ 10–100 ms     │
│ Ops burden         │ None          │ High (ZK,     │ Medium        │ None          │
│                    │                │ broker, etc)  │                │               │
│ Pricing            │ Per shard-hr  │ Per EC2 node  │ Per broker-hr │ Per request   │
│                    │ + PUT PU      │ + EBS         │ + EBS         │               │
│ Replication model  │ 3 AZ sync     │ Replica.factor│ 3 AZ sync     │ 3 AZ sync     │
│ Max record size    │ 1 MiB         │ Config (1 MB  │ Config        │ 256 KB        │
│                    │                │ default)      │                │               │
│ Replay             │ Yes (retent.) │ Yes (retent.) │ Yes           │ No            │
│ Ecosystem          │ AWS-native     │ Huge (Connect,│ Kafka +       │ AWS-native    │
│                    │                │ Streams, etc) │ AWS            │               │
└──────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

**Interview sound-bite:** "Kinesis is Kafka-minus-ops. Choose Kinesis when the AWS integration tax (Firehose, Lambda triggers, Flink) saves more than the throughput tax (shard math, EFO cost). Choose Kafka/MSK when you need sub-10ms latency, >100 MB/s per partition, tight Kafka Streams/Connect ecosystem, or multi-cloud."

---

## 15. Real-World Usage at Scale

Documented from public talks, blogs, and re:Invent.

### Netflix — Keystone Pipeline
Netflix built **Keystone** on Kinesis: hundreds of billions of events/day flow through Kinesis → Flink (internally) → S3/Elasticsearch. Keystone has since been partially migrated off Kinesis to Kafka/MSK for cost reasons at their scale (>1 PB/day), but the original design is the canonical "streaming platform on AWS" reference. (Netflix Tech Blog, *Evolution of the Netflix Data Pipeline*.)

### Lyft — Real-Time Pricing
Driver/rider location pings flow to KDS, Flink (on KDA) computes surge multipliers in 1-minute windows, writes back to DynamoDB for mobile-client reads. Latency target: < 2 s from ping to surge update.

### Nextdoor — Activity Feeds
User posts → KDS → Lambda trigger → DynamoDB (per-user feed) + ElasticSearch. Using On-Demand mode to absorb event-driven traffic spikes (earthquake, big-news-day).

### Expedia — Clickstream
A single stream at 500+ shards during holiday peak. Uses KPL aggregation (100:1 packing ratio) to fit within 1,000 rec/s per shard without over-provisioning.

### ADP — Payroll Processing
For tax-filing deadlines, ADP runs Kinesis streams with 365-day retention for audit reconstruction (regulatory requirement: replay 12 months).

### Generic pattern — "Central Event Bus"
Most Fortune-500s using AWS have, at some point, built a "central event bus" where every service publishes via KDS:
```
                 ┌─── KDS "events.<env>" ─────────────┐
  N services ───▶│                                    │──▶ Firehose → S3 (audit, 365 d)
  publish here    │  12-month retention                │──▶ Flink → dashboards
                 │  On-Demand mode                    │──▶ Lambda → alerts/automation
                 └────────────────────────────────────┘──▶ Firehose → OpenSearch (search)
```

---

## 16. When Kinesis Failed — Production Incidents

### 16.1 The November 25, 2020 Outage (us-east-1)

The biggest Kinesis incident of all time. A senior-engineer favorite in interviews.

**Timeline (from AWS's official post-event summary):**

```
 05:15 PST  us-east-1 Kinesis front-end fleet capacity added (routine)
            ↓
            Each front-end server maintains a thread and an OS-level file
            descriptor per back-end server. Adding front-end capacity
            increased the total thread count past the OS max-threads limit
            on the OLD front-end servers (not the new ones).
            ↓
 05:15–05:45  Old front-ends start failing health-checks (can't spawn threads)
            ↓
            Cascading cache-invalidation storm: front-ends refresh their
            view of back-end fleet, fail, retry, fail…
            ↓
 05:45  "elevated error rates" on Kinesis APIs
            ↓
            CloudWatch uses Kinesis internally for metrics delivery
            → CloudWatch metrics go blind
            → Auto-scalers blind → more outages
            → Cognito uses Kinesis for stream records → login failures
            → ACM uses Kinesis → some certificate operations fail
            ↓
            Spread to > 30 AWS services
            ↓
 13:25  AWS engineers identified root cause (thread limit)
            ↓
            Remediation required REBOOT of entire front-end fleet, slowly,
            one at a time (cold-start back-end map rebuild takes minutes)
            ↓
 22:23  Full recovery, ~17 hours later.
```

**Lessons every senior engineer should cite:**

1. **Cross-service coupling is hidden.** CloudWatch, Cognito, EventBridge, Lambda (event source mapping), and many others transparently depended on KDS. An "only Kinesis" outage was actually a partial us-east-1 outage.
2. **OS-level limits bite at scale.** Thread count, file-descriptor count, ephemeral port count — these all have hard caps that surface only past some threshold.
3. **The blast radius of a regional data-plane outage is far larger than its on-paper SLA.** Multi-region active-active is the only real mitigation for critical systems.
4. **Kinesis is NOT multi-region by default.** Each region has its own stream namespace. Cross-region replication = you build it (or use MSK Serverless with MirrorMaker 2, or Kinesis MultiStream).

**Official document:** *Summary of the Amazon Kinesis Event in the Northern Virginia (us-east-1) Region* — aws.amazon.com/message/11201/

### 16.2 Pinterest — Duplicate Events from Client Retries

Pinterest published that their home-feed ranking pipeline had a **15% duplicate rate** traced to KPL retrying records after the Kinesis stream had ACKed them (but the TCP ACK was lost). Their fix:
- Client-side dedup key in the payload (eventId = UUIDv4)
- Downstream Flink job deduplicates within a 10-minute window keyed on eventId

### 16.3 A Common Team Failure — Hot Shard + Slow Autoscale

A typical team story, paraphrased from multiple re:Invent talks:

```
  07:45  Super-Bowl ad airs. Clickstream goes from 50 K rec/s to 500 K rec/s.
  07:46  Kinesis stream in Provisioned mode with 100 shards (ample "on paper").
         But PartitionKey = "country_code". 70% of clicks have PK="US".
         US shard (~3 shards out of 100) is hot.
  07:47  ProvisionedThroughputExceeded errors. KPL buffers fill up.
  07:48  KPL starts rejecting new records (back-pressure) → app OOMs.
  07:55  On-call runs split-shard on the 3 hot shards → takes 3 min each.
         But KPL shard-map cache has 30-s TTL → still sending to old shards for 30 s.
  08:05  Finally stable.
  Postmortem: 20 minutes of 0% ingest during peak marketing event.
  Fix: PartitionKey = country + "#" + hash(userId) % 32 → spreads hot keys
       Or: Switch to On-Demand (which would have auto-split in 15 min anyway,
            so this only helps for SUSTAINED hot shards, not 20-min spikes)
       Or: KPL with aggregation + higher shard count to leave headroom
```

### 16.4 Consumer Fell Behind 24 Hours → Data Loss

Team ran a bulk Lambda consumer with 50 K concurrency ceiling. Traffic grew; consumer couldn't keep up. `IteratorAgeMilliseconds` climbed until it exceeded 24 h (the stream's retention). At that point, the **oldest records are dropped** — Kinesis does not buffer forever.

Fix: alert on `IteratorAgeMilliseconds > 4 h` (allows time to respond), extend retention to 7 days preventively.

---

## 17. When Kinesis Cannot Cope — Limitations

### 17.1 The Hard Walls

```
┌────────────────────────────────────────────────────────────────────────┐
│ Limit                                   Value                           │
├────────────────────────────────────────────────────────────────────────┤
│ Record size                               1 MiB (hard)                   │
│ Write throughput per shard                1 MiB/s OR 1,000 rec/s         │
│ Read throughput per shard (shared)       2 MiB/s TOTAL (all consumers)  │
│ Read throughput per shard (EFO)          2 MiB/s PER consumer           │
│ EFO consumers per stream                  20                             │
│ PutRecords batch                          500 records, 5 MiB total       │
│ GetRecords result                         10 MiB or 10,000 records       │
│ GetRecords calls per shard                5 / sec                        │
│ Shards per stream (default / max)         500 / 100,000 (soft)           │
│ Retention                                 24 h default, 7 d ext, 365 d LT │
│ On-demand throughput (default)            200 MiB/s write, 400 MiB/s rd  │
│ Streams per account per region            unlimited (soft 50)            │
│ SubscribeToShard invocation               5 min, auto-renew               │
└────────────────────────────────────────────────────────────────────────┘
```

### 17.2 Kinesis Cannot Do

1. **Priority messages.** No priority queues. Emulate with multiple streams.
2. **Delayed delivery.** No `DelaySeconds` like SQS. Emulate with Step Functions or per-record scheduling logic.
3. **Dead-letter queue.** No native DLQ for failed consumer processing (SQS has this). You must build it: "on 3rd retry, publish to a DLQ stream/queue".
4. **Server-side filtering.** Consumers read everything; they filter in code. (Kafka doesn't do this either; Lambda event-source mapping does filter patterns but those skip records, they don't rewind.)
5. **Record-level ACK.** ACKing a batch is the unit; no selective per-record ACK like SQS.
6. **Cross-region replication.** You must build it (Flink job copies stream A in us-east-1 → stream B in us-west-2).
7. **Record-size > 1 MiB.** For 2 MiB payloads, put them in S3 and stream the S3 key.
8. **>20 real-time fan-out consumers at full throughput.** Hard limit. For 50 consumers, use MSK.

### 17.3 Where the Math Breaks

- **Low-latency trading.** 70 ms EFO p50 is too slow. Use MSK or direct TCP fan-out.
- **Hundreds of tenants, each needing own shard-equivalent ordering.** A shard minimum of $0.015/hr × N tenants = accounts that can't justify it. Alternative: FIFO SQS with MessageGroupId per tenant.
- **Mostly-cold streams.** A stream with 1 record/day still costs $0.015 × 1 shard × 720 h ≈ $11/mo minimum in Provisioned. Use On-Demand or SQS.
- **Single-region HA.** For a true active-active app, you need cross-region.

---

## 18. Anti-Patterns That Kill Kinesis Systems

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ #1   PartitionKey = constant ("main")                                        │
│       → All records go to shard 0. Stream capacity = 1 shard. Fatal.         │
│       Fix: PartitionKey = entityId (user_id, order_id)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│ #2   Using PutRecord in a for-loop (no batching)                             │
│       → 1 network round-trip per record. 10× slower, 10× cost.               │
│       Fix: PutRecords(≤500 records) or KPL aggregation                        │
├─────────────────────────────────────────────────────────────────────────────┤
│ #3   Ignoring FailedRecordCount in PutRecords response                       │
│       → Silent data loss when shard is throttled.                            │
│       Fix: Explicit retry loop on per-record ErrorCode                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ #4   Checkpointing after every record                                        │
│       → DynamoDB lease table becomes the bottleneck. Throttles cascade.      │
│       Fix: Checkpoint every N records OR every T seconds                     │
├─────────────────────────────────────────────────────────────────────────────┤
│ #5   Using Kinesis as a message queue (one consumer, delete-after-process)   │
│       → Paying for retention you never use; scaling model is wrong.          │
│       Fix: SQS is the correct primitive                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│ #6   Running a KCL worker on a fraction of shards                            │
│       → Uneven load, worker failover = thundering herd on DynamoDB table.    │
│       Fix: Let KCL lease-balancer run. Don't hard-assign shards.              │
├─────────────────────────────────────────────────────────────────────────────┤
│ #7   Skipping monitoring of IteratorAgeMilliseconds                          │
│       → You only find out consumers fell behind when retention expires.      │
│       Fix: Alarm at IteratorAge > 5 min (or 10% of retention)                │
├─────────────────────────────────────────────────────────────────────────────┤
│ #8   Using KDS when you should use Firehose                                  │
│       → Writing a KCL consumer that does batch-write-to-S3 yourself.         │
│       Fix: Plug Firehose onto the stream, delete your consumer               │
├─────────────────────────────────────────────────────────────────────────────┤
│ #9   Relying on ApproximateArrivalTimestamp for ordering across shards       │
│       → Clock skew between shards can be 10 ms. Subtle bugs in analytics.    │
│       Fix: Embed Lamport clock / causal sequence in payload                  │
├─────────────────────────────────────────────────────────────────────────────┤
│ #10  Enabling EFO on all consumers "just in case"                            │
│       → EFO costs $0.015/shard-hour PER consumer. 100 shards × 5 consumers   │
│         = $5,400/month extra.                                                 │
│       Fix: EFO only for latency-critical consumers                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 19. Performance Tuning Cheat Sheet

```
GOAL                                     KNOB
───────────────────────────────────────  ─────────────────────────────────
Increase write throughput                 Add shards   OR   switch to On-Demand
Reduce write latency (producer)           Disable KPL aggregation (smaller batch)
Reduce write cost                         KPL aggregation (100:1 is common)
Reduce hot shard pain                     Better PartitionKey (salt / hierarchical)
Reduce consumer lag (IteratorAge)         More consumer instances OR EFO
Reduce per-consumer latency               EFO (push, 70 ms vs shared 200 ms–2 s)
Reduce cost of many consumers             Shared throughput (but max 2 MiB/s total)
Enable replay                              Extend retention (7 d or 365 d)
Zero-code S3 archive                       Attach Firehose to stream
Real-time aggregates                       KDA (Managed Flink) with tumbling windows
Survive us-east-1 outage                   Active-active cross-region with Flink MirrorMaker
                                          or an application-level dual-write producer
Meet sub-100 ms E2E                        EFO consumer + PutRecord (no KPL) + same-AZ compute
Handle 2 MiB records                       S3 + stream the key (small header-only record)
Dedup / exactly-once                       Idempotent consumer keyed on business-ID
                                          OR Flink with transactional sinks
```

### A Sensible Default Starting Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ DEFAULT STARTING POINT FOR A NEW KINESIS-BASED SYSTEM                   │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│ • On-Demand mode (no capacity planning)                                 │
│ • Retention = 7 days (enough for most replay-debugging)                 │
│ • PartitionKey = the most natural entity ID (userId, orderId)           │
│ • Producer = AWS SDK PutRecords (switch to KPL only if throughput > 5 K/s)│
│ • Primary consumer = Lambda event-source mapping (shared throughput)     │
│ • Archive consumer = Firehose → S3 Parquet (partitioned by hour)         │
│ • Analytics consumer = KDA Flink SQL for real-time aggregates            │
│ • Alarms:                                                                │
│    - IteratorAgeMilliseconds > 300,000 (5 min)                          │
│    - WriteProvisionedThroughputExceeded > 0                             │
│    - ReadProvisionedThroughputExceeded > 0                               │
│    - Lambda EventSourceMapping errors > 0                                │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 20. Interview Questions — Medium

### Q1: Why does a Kinesis stream have shards? Why not a single ordered log per stream like a database WAL?

**Expected Answer:**
```
Shards are Kinesis's unit of horizontal scale, parallelism, ordering, and billing.

A single ordered log cannot exceed:
  - single-threaded write throughput (contention on append)
  - single-node network / disk I/O
  - single-node failover latency

By partitioning the stream, Kinesis:
  - scales writes horizontally (each shard is independent)
  - scales consumers horizontally (each consumer processes one shard)
  - provides per-shard ordering without cross-shard coordination
  - prices capacity in small, elastic units (you pay per shard-hour)

The trade-off: NO global ordering. Application code that needs cross-entity
causal ordering must embed Lamport timestamps or use a single-shard design
(PartitionKey = constant), which caps throughput at ~1 MiB/s.

This is the same fundamental design as Kafka partitions, Kinesis shards,
DynamoDB partitions, and Cassandra token ranges — "hash and spread".
```

### Q2: You see `ProvisionedThroughputExceededException` on the producer side. Walk me through how you'd diagnose it.

**Expected Answer:**
```
Step 1 — Identify which shard is throttled
  CloudWatch Metrics > Kinesis > PerShard > WriteProvisionedThroughputExceeded
  Sum by ShardId. One or a few? → hot shard. All of them? → under-provisioned.

Step 2 — For a hot shard: inspect PartitionKey distribution
  Log PartitionKey samples for 1 min from producer. Count by key.
  Top-1 key > ~30 % of volume? → hot-key problem.

Step 3 — Fix:
  - (Short term) split-shard on the hot shard  → doubles its capacity
  - (Short term) enable KPL aggregation on producer (100:1 → bypasses
    rec/s limit since aggregated record counts as 1)
  - (Medium term) change PartitionKey (hierarchical or salted)
  - (Long term) switch to On-Demand mode — auto-splits hot shards

Step 4 — For under-provisioning: scale up
  - Provisioned:  aws kinesis update-shard-count --target-shard-count=N
     (doubles or halves — max 2× per 24 h per stream)
  - Or switch to On-Demand.

Step 5 — Verify
  - Producer's RecordsPut count matches sent count
  - IteratorAgeMilliseconds stable (consumer not falling behind)
```

### Q3: What is the difference between shared-throughput consumers and Enhanced Fan-Out? When would you NOT use EFO?

**Expected Answer:**
```
Shared throughput:
  - Pull model: consumer calls GetRecords
  - 2 MiB/s total per shard SHARED across all consumers
  - Max 5 GetRecords/s per shard → higher latency (p50 ~200 ms, p99 > 1 s)
  - Cheap: no per-consumer cost

EFO:
  - Push model: SubscribeToShard over HTTP/2
  - 2 MiB/s dedicated PER consumer per shard
  - ~70 ms p50 latency
  - Up to 20 registered consumers per stream
  - Cost: $0.015 per shard-hour per consumer + $0.013 per GB delivered

When NOT to use EFO:
  1. Only 1 consumer — shared is already fine and cheaper.
  2. Very many shards (≥1000) with cost-sensitive pipeline:
     1000 shards × 3 consumers × $0.015 × 720 h = $32,400/month, often the
     bulk of the Kinesis bill.
  3. Latency-tolerant batch consumers (Firehose, nightly ETL).
  4. When you have >20 consumers required — EFO caps at 20; fall back to
     shared, or split into multiple streams.
```

### Q4: Your consumer's `IteratorAgeMilliseconds` is steadily growing. What causes this and how do you remediate?

**Expected Answer:**
```
IteratorAge = (now - arrival time of oldest record not yet consumed).

Growing monotonically → consumer throughput < producer throughput.

Root causes:
  1. Slow processing code (sync calls to a slow downstream DB/API).
  2. Too few consumer workers (KCL: workers < shards).
  3. Hot shard on read side (one shard has 10× more data than others).
  4. Consumer stuck in a retry loop on a poison-pill record.
  5. DynamoDB lease table throttled → checkpointing blocked → re-reads same data.

Remediation:
  1. Add consumer workers (KCL balances shards across workers).
  2. Parallelize per-record processing inside each worker (thread pool, async).
  3. Increase batch size on GetRecords (up to 10 MiB / 10 K records per call).
  4. Switch that consumer to EFO (2 MiB/s dedicated pipe per shard).
  5. Handle poison pills: send to a DLQ stream after N retries.
  6. Scale up DynamoDB lease table (or move to on-demand billing).
  7. If hot shard on read: split shard — reading capacity doubles.

Urgency — retention is the hard deadline:
  If IteratorAge approaches retention window (default 24 h), you LOSE data.
  Alert threshold = 10 % of retention (e.g., 2.4 h for 24 h retention).
  If you're inside the window: extend retention IMMEDIATELY
   (aws kinesis increase-stream-retention-period).
```

### Q5: Explain the ordering guarantees of Kinesis in detail. Where can ordering be broken?

**Expected Answer:**
```
Strict FIFO is guaranteed ONLY within a single shard, by SequenceNumber.

Same PartitionKey → same shard (because MD5(PK) falls in same hash range)
 → same order.

BUT ordering CAN be broken by:

  1. Resharding (split / merge):
     - PK falls in parent shard P pre-reshard → goes to P
     - Post-reshard: same PK now hashes into child shard C1 or C2
     - If consumer reads C1 BEFORE parent P is fully drained → out-of-order
     - KCL solves this via parentShardIds in the lease table — it won't lease
       a child shard until parent checkpoint = SHARD_END.
     - If you write a custom consumer, you MUST implement this handshake.

  2. Producer retries:
     - PutRecord #5 fails (network blip), retried after #6 already succeeded.
     - Record 5 now has a later SequenceNumber than record 6.
     - Fix: KPL's "retry in order" mode OR include monotonic seq in payload.

  3. PutRecords partial failure:
     - Batch of 10 records, 3 fail. Your retry logic puts them back onto the
       stream AFTER records 11, 12, 13 have been sent.
     - Fix: Use the KPL (which preserves order on retry) OR pause the stream
       for that PartitionKey until the failure is resolved.

  4. Multiple producers for the same PK, not coordinated:
     - Producer A writes event.v1 at t=100, Producer B writes event.v2 at t=101.
     - They race on the wire; Kinesis might ingest v2 before v1.
     - Fix: single-writer principle, or embed monotonic version in payload.

  5. Consumer-side multi-threading:
     - Consumer receives batch, dispatches records to a thread pool.
     - Thread pool reorders records for the same PK.
     - Fix: hash-partition the thread pool by PK so same PK → same thread.
```

### Q6: Compare Kinesis Data Streams and Kinesis Data Firehose. When do you use which?

**Expected Answer:**
```
KDS:                                        KDF:
- You write the consumer                     - AWS is the consumer
- Retention 24 h–365 d (replay possible)     - No retention (flight-only)
- Per-shard capacity planning                - Zero capacity planning
- Ordering per shard                         - No ordering guarantees
- Multiple independent consumers             - One destination (S3/Redshift/...)
- Sub-second latency (with EFO)              - 60 s–900 s buffer latency
- Billing: per shard-hour + per PUT PU       - Billing: per GB ingested + transform

Choose KDF when ALL these are true:
  - destination is S3, Redshift, OpenSearch, Splunk, HTTP endpoint, or Iceberg
  - you don't need to replay / re-process
  - >60 s latency is acceptable
  - you don't need multiple consumers

Choose KDS when ANY of these:
  - multiple consumers fan out the same data
  - you need replay
  - you need <1 s end-to-end latency
  - you need ordering
  - you need a stream processor (Flink) with state

Common pattern: KDS → KDF → S3.
  KDS is the "bus" (durable, replayable, multi-consumer).
  KDF is one of several consumers (the S3 archiver).
```

### Q7: What exactly does the KCL DynamoDB lease table store, and why?

**Expected Answer:**
```
One row per shard. Columns (KCL v2):

  leaseKey            (PK)   shardId
  leaseOwner                  workerId that currently processes this shard
  leaseCounter                integer, incremented on every heartbeat (~3 s)
  checkpoint                  SequenceNumber of last processed record
  ownerSwitchesSinceCP        how often this lease bounced between workers
  parentShardIds              for resharding: must drain parent(s) first
  hashKeyRangeForLease        redundant copy of hash range (useful for ops)
  childShardIds               post-resharding: pointers to children
  lastCounterIncrementNanos   monotonic timestamp for race resolution

Why:
  1. Distributed coordination without a coordinator service (uses DynamoDB's
     conditional write as the only sync primitive).
  2. Heartbeat — other workers know if this worker died.
  3. Checkpoint — resume work from where we left off after crash.
  4. Resharding safety — don't start child until parent SHARD_END.
  5. Debugging — ops can query the table to see lease distribution.

Throughput implications:
  write cost = O(heartbeats + checkpoints)
           ~= workers × 20 + shards × (60 s / 60 s) per minute
  A 200-shard stream with 10 workers + checkpoint every 60 s:
           write ≈ 200 + 200 = ~400 writes/min = ~7 WCU sustained.
  ALWAYS use on-demand billing on the lease table — it's spiky.
```

### Q8: Your team is about to build a Kinesis producer in Python at 20,000 records/sec. Walk through your choices.

**Expected Answer:**
```
Throughput math:
  20,000 rec/s @ ~500 B each = 10 MB/s → ~10 shards minimum.
  Add 50 % headroom for hot-shard burst → 15 shards.

Producer options:
  A) boto3 PutRecord in a loop        → 1 network call per record → FATAL
  B) boto3 PutRecords batches of 500   → ~40 calls/s, fine but no aggregation
  C) KPL Python binding (amazon_kclpy) → aggregation + async → BEST if lib fits
  D) Aws-kinesis-agent (daemon)        → for log-file tail scenarios only

Choice: (B) with a simple batcher, UNLESS:
  - records are small (< 5 KB) → (C) KPL aggregation gives 10× cost savings
  - latency SLA < 50 ms       → (B) without batching

Code sketch for (B):
  - ThreadPoolExecutor with 8 workers
  - Each worker consumes from asyncio.Queue
  - Batch up to 500 records OR 5 MiB OR 100 ms linger time
  - On PutRecords response, retry only records with ErrorCode
  - Exponential backoff with full jitter
  - Circuit breaker: if >10% throttles over 30 s → sleep 1 s

Observability:
  - Metric: records_sent, records_failed, shard_throttles (per shard)
  - Log PartitionKey distribution every 1 min (detect hot keys early)

Data design:
  - PartitionKey = most-natural entity ID (user_id, session_id)
  - NEVER PK=constant
  - Compress payload if > 10 KB (brotli or zstd; decompress in consumer)
  - Keep single record < 1 MiB (hard limit)
```

### Q9: How does Lambda event-source-mapping for Kinesis work under the hood? What are its gotchas?

**Expected Answer:**
```
Event-source mapping (ESM) is a Lambda-managed polling fleet that AWS runs
on your behalf:

  1. ESM creates a persistent shared-throughput consumer per shard (unless
     EFO is explicitly enabled).
  2. It GetRecords in batches (you set BatchSize up to 10,000 and
     MaximumBatchingWindow up to 5 min).
  3. Invokes YOUR Lambda with the batch.
  4. On Lambda success, ESM checkpoints on your behalf.
  5. On Lambda error (throw), ESM retries the entire batch from the same
     sequence number. Forever. By default. ← Gotcha.

Gotchas:
  (a) A single bad record blocks the whole shard ("poison pill")
      → Fix: set BisectBatchOnFunctionError = true → ESM halves the batch
              on error until it isolates the bad record.
      → Set MaximumRetryAttempts and DestinationConfig.OnFailure to a DLQ
        (SQS or an SNS topic).

  (b) Parallelism is per-shard by default
      → ParallelizationFactor (1–10) lets you run multiple Lambda invocations
        in parallel from ONE shard (as long as ordering-per-PK is maintained
        internally by Lambda, which it is by hashing PK to one of N workers).

  (c) Cold starts + long batching window = high end-to-end latency
      → Reduce MaximumBatchingWindow (e.g., 5 s for dashboard freshness).

  (d) Lambda concurrency ceiling  
      → If you have 1000 shards × ParallelizationFactor=10 = 10,000 concurrent,
        and the account default Lambda concurrency limit is 1000 → throttles.
      → Reserve concurrency, request limit increase.

  (e) No EFO by default (shared throughput).
      → 2 MiB/s total per shard across ALL consumers (including ESM).
      → If you also have a KCL consumer, they share the 2 MiB/s.
      → Enable EFO on the ESM if you need dedicated bandwidth.
```

### Q10: What happens to records when you decrease a stream's retention period?

**Expected Answer:**
```
aws kinesis decrease-stream-retention-period --retention-period-hours 24

Effect:
  Records older than the new retention (e.g., records from 72 h ago when
  shrinking from 168 h to 24 h) become IMMEDIATELY unreadable — but not
  instantly purged from storage; Kinesis just refuses to return them via
  GetRecords / SubscribeToShard.

Practical implications:
  1. Consumers currently reading older data will get "EXPIRED_ITERATOR"
     on next GetRecords → KCL resets to TRIM_HORIZON (= earliest still-
     readable record) and moves on, SKIPPING the dropped records.
     → SILENT DATA LOSS for that consumer.

  2. You cannot UNDO this by increasing retention back — the data is gone.

  3. Cost: drops immediately (long-term-retention billing stops at next
     hour boundary).

Safe procedure before decreasing:
  1. Confirm NO consumer is currently reading data that would be trimmed
     (IteratorAge < new retention for ALL shards, across ALL registered
     consumers and ESMs).
  2. Communicate with any humans running ad-hoc replays.
  3. Then decrease.

Increasing retention is always safe — it costs more but doesn't lose data.
```

---

## 21. Interview Questions — Hard

### Q1: Design a system that ingests 1 billion events per day, stores them for 365 days, supports real-time dashboards, and allows 2-week replay for bug fixes. Walk through the architecture and explain every capacity calculation.

**Expected Answer:**
```
Numbers:
  1 B events/day = ~11,574 events/sec on average, ~35,000 events/sec at 3× peak
  Avg event size 1 KB → ~11.5 MB/s average, ~35 MB/s peak.

Shard math (Provisioned):
  35 MB/s × 1 shard/MB/s × 1.5 headroom = 53 shards.
  For 1 KB records: 35,000 rec/s / 1,000 rec/s/shard = 35 shards — writes bound
  by rec/s before MB/s (small records). Take max = 53 shards.
  On-Demand (200 MB/s default) is 4× this; no sharding work needed.

Architecture:

  Producers (mobile, web, backend services)
        │
        │  KPL aggregation (100:1) → 350 rec/s to stream (rate bypass)
        ▼
  ┌─────────────────────────────────────────┐
  │ Kinesis Data Streams "events.prod"       │
  │  - On-Demand mode                        │
  │  - Retention: 14 days (for replay)        │
  │  - Extended to 365 days → SEPARATE S3    │
  │    archive (don't pay 365 d on KDS, too  │
  │    expensive at 11.5 MB/s × 365 d = 362 TB)│
  └──┬────────────┬────────────┬────────────┘
     │            │            │
     ▼            ▼            ▼
 Firehose    KDA Flink     Lambda ESM
 → S3 Parquet → real-time   → dedupe/enrich
 partition=   aggregates    → DynamoDB
 yyyy-mm-dd   → KDS "agg"   (online index)
 /hh            → DynamoDB
                tables

  S3 (365 day archive):
    GLACIER_IR class for data > 30 d (10× cheaper than STANDARD)
    Athena for ad-hoc analytics
    EMR for heavy replay jobs

  Real-time dashboard:
    Flink 1-min tumbling window → KDS "agg" → Firehose → OpenSearch
    Kibana dashboard on OpenSearch

  2-week replay:
    KDS retention 14 d — replay via KCL consumer with AT_TIMESTAMP iterator
    or via reading Parquet archive into EMR Spark if > 14 d ago

Capacity / cost estimate:
  Kinesis On-Demand:
    ingress: 1 GB/s × 86,400 s × 365 d = 31.5 PB/year — NO, wait:
    11.5 MB/s × 86,400 = ~1 TB/day = 365 TB/year ingress
    At $0.04/GB = $40/TB = ~$15 K/year on ingest alone.
    (egress ~2-3× if multiple consumers read)

  Provisioned comparison:
    53 shards × $0.015 × 720 h × 12 = $6,800/year in shard hours,
    much cheaper if utilization > ~40 % (which it is here).

Monitoring:
  - IteratorAgeMilliseconds per consumer, alarm > 10 min
  - WriteProvisionedThroughputExceeded > 0
  - ReadProvisionedThroughputExceeded > 0
  - Flink checkpoint duration p99 < 30 s
  - DynamoDB aggregate table throttle events = 0
```

### Q2: Your Kinesis stream runs at p99 write latency of 50 ms typically, but spikes to 3 seconds every Monday at 09:00. You've confirmed no hot shard. What else could cause this, and how would you diagnose?

**Expected Answer:**
```
"No hot shard" means per-shard writes are evenly distributed. Latency spikes
that affect ALL shards uniformly on a cadence point to something outside shard
capacity. Candidates:

  1. Producer-side saturation
     - Monday 09:00 = global start-of-week traffic burst across MANY producers.
     - Producers share a connection pool / DNS cache / TCP connection.
     - Check: producer-side TCP retransmits, DNS resolve time, HTTP
       connection-pool wait (p99 > 0 = bug).
     - Fix: per-process HTTPS keep-alive to kinesis.<region>.amazonaws.com,
       pre-warmed connection pool, increased ephemeral-port range.

  2. Client library KPL buffer behavior
     - RecordMaxBufferedTime = 100 ms plus collection time = 400 ms baseline.
     - Under burst, buffer fills → blocking put calls → spikes to seconds.
     - Check: KPL metrics (UserRecordsPending, RecordsPerSecondLimit).
     - Fix: increase MaxConnections (default 24 → 64+), or KPL → raw SDK.

  3. DNS / NLB at AWS front end
     - Monday morning bursts may trigger front-end fleet scaling inside KDS;
       new front-ends take 20–60 s to warm caches. Saw exactly this during
       the Nov 2020 outage scaled down.
     - Diagnose: AWS Support, CloudTrail for API call latency per endpoint.
     - Mitigation: pre-warm by sending low-volume keep-alives 10 min before.

  4. TLS handshake
     - If connection pool evicts connections during weekend quiet hours,
       Monday morning rebuilds TCP+TLS for every worker simultaneously.
     - Fix: tune connection pool idle-timeout > weekend gap (e.g., 72 h idle).

  5. Producer autoscaling cold-start
     - ASG scales out on Monday, new EC2 instances are cold, first writes
       are slow (JIT warm-up, JVM class-loading).
     - Fix: pre-warm via scheduled scale-out at 08:45.

  6. Cross-AZ network burst
     - If producer is in AZ-a and all front-ends for this stream are in
       AZ-b due to zonal skew, Monday burst = cross-AZ bandwidth contention.
     - AWS doesn't expose front-end AZ directly. Use VPC endpoint to pin
       traffic on-region and reduce hop count.

Diagnosis plan:
  1. Add X-Ray tracing to producer for 1 week → decompose 3 s into
     {connection_acquire, tls_handshake, DNS, request_send, wait_response}.
  2. CloudWatch SDKClientSideLatency vs ServerSideLatency
     (diff = network+client; equal = AWS-side).
  3. Compare producer instances that DO spike vs those that don't
     (same AZ? same AMI? same EC2 type?).
  4. Capture a tcpdump on one producer during a spike — look for Kinesis
     server returning 503 or slow TCP ACKs.
```

### Q3: Design the world's simplest exactly-once analytics pipeline on Kinesis. Strict requirement: no duplicates in the final counts, even if consumers crash mid-batch. Don't use Flink.

**Expected Answer:**
```
Core idea: "idempotent commit keyed on (shardId, sequenceNumber)" lets you
collapse at-least-once into exactly-once at the SINK boundary.

Architecture:

  Producer (at-least-once, embeds eventId UUID in payload)
        │
        ▼
  Kinesis (at-least-once delivery)
        │
        ▼
  KCL consumer with custom checkpoint strategy
        │
        ▼
  DynamoDB aggregation table (idempotent writes)

The Table:
  PK = (partition_key, tumble_window_start)
  SK = eventId                                   ← write sentinel, NOT the counter
  TTL = window_end + 24 h
  ------------------------------------------------
  Plus a summary item:
  PK = (partition_key, tumble_window_start)
  SK = "__COUNT__"
  count = atomic increment via UpdateItem ADD

Consumer logic:
  for each record r in batch:
      sentinel_pk = (r.PartitionKey, floor(r.ts, 5min))
      try:
          dynamo.PutItem(
              PK=sentinel_pk, SK=r.eventId, TTL=…,
              ConditionExpression="attribute_not_exists(SK)"    # IDEMPOTENT
          )
      except ConditionalCheckFailed:
          continue  # duplicate, already processed

      dynamo.UpdateItem(
          PK=sentinel_pk, SK="__COUNT__",
          UpdateExpression="ADD #cnt :one",
          ...
      )

  checkpoint(r.sequenceNumber)    # every 60 s OR 5k records

Why this works:
  - Dupe-on-retry: sentinel PUT fails → count NOT incremented → correct.
  - Crash mid-batch: re-read same records → sentinels already exist → noop.
  - Network retry of PUT: conditional write is idempotent by design.

Limits / caveats:
  - DynamoDB cost: 2 writes per event (sentinel + counter). At 50K rec/s
    that's 100K WCU ≈ $700/mo (on-demand).
  - Sentinel table TTL must exceed max retry window (retention + clock skew).
    Otherwise a very delayed retry could re-count.
  - `eventId` MUST be produced by the ORIGINAL producer, not added by the
    consumer (else each retry gets a new id).
  - Windows close only when you're "sure no late events arrive" — a safety
    delay of 2× watermark slack is needed before exposing counts.

Comparison with Flink:
  Flink does this with cheaper state (local RocksDB + distributed S3 ckpts)
  and end-to-end barriers; our DynamoDB approach is equivalent but 10×
  more expensive at scale. Use it when you can't run Flink.
```

### Q4: Explain the November 25, 2020 Kinesis outage root cause. What would you have changed in AWS's architecture to prevent it? What should customers have done differently?

**Expected Answer:**
```
Root cause (from AWS's post-event summary):
  Kinesis front-end fleet scaled up → existing front-end servers crossed the
  OS thread limit because they maintain 1 thread per back-end server × N
  back-end servers × (growing fleet). When N grew past a threshold, the
  OS could not spawn new threads; front-ends health-failed, cascaded through
  retry storms, amplified across dependent services (CloudWatch, Cognito,
  EventBridge, Lambda, ACM, …).

  Key flaw: the front-end fleet's scaling model had O(N) per-server memory/
  thread overhead as back-end fleet grew. There was no bound that forced
  operators to address this before adding capacity.

What AWS could change (architecturally):
  1. Bound front-end per-server resources regardless of fleet size:
     use connection multiplexing (a few threads handling thousands of
     back-end connections via epoll) instead of thread-per-backend.
  2. Decouple CloudWatch metrics plane from Kinesis data plane.
     (CloudWatch going blind during a Kinesis outage was what made the
     incident so nasty — auto-scalers and humans lost visibility.)
  3. Separate front-end sharding — each front-end owns a subset of streams,
     so scaling back-end doesn't scale every front-end's internal map.
  4. Cell-based isolation: if us-east-1 were divided into independent "cells"
     (like S3 EASYO), a thread-limit bug would hit one cell, not all.
  5. Better canary deploys — roll new capacity with tight latency budgets
     and auto-rollback. The 05:15 change propagated for hours before rollback.

What customers should have done:
  1. Multi-region active-active for any business-critical pipeline:
     run parallel streams in us-east-1 AND us-west-2, with a dual-write
     producer and idempotent consumers. Expensive but the only real defense.
  2. Don't put your health-check / status page on services that transitively
     depend on Kinesis (Cognito, CloudWatch). A surprising fraction of
     customers could not show their users a status page that day.
  3. Tested, documented failover runbooks. Many teams had no idea Lambda
     ESM was silently blocked because it reads Kinesis records internally.
  4. Recognize that "99.9% SLA" means 8 h/year of allowed downtime, and
     outages can cluster (17 h in one day). Model this in your RPO/RTO.
  5. Static stability: make sure your system continues operating (perhaps
     degraded) if the Kinesis data plane is unavailable — e.g., producers
     write to a local on-disk WAL and backfill when KDS recovers.
```

### Q5: You must migrate a 40-billion-records-per-day pipeline from Kafka to Kinesis with zero downtime and zero data loss. Design the migration.

**Expected Answer:**
```
Scale check:
  40 B/day = 463,000 rec/s average. Peak maybe 1M rec/s.
  Record size unknown; assume 1 KB → 463 MB/s avg, 1 GB/s peak.
  → KDS Provisioned: ~1,500 shards; On-Demand: needs quota increase
    (default 200 MB/s per stream).

High-level plan (dual-write pattern):

  Phase 0 — Preparation (weeks)
    - Build Kinesis consumers that implement the SAME interface as Kafka ones
      (abstract "record" API hides which stream you're reading).
    - Verify shard count & On-Demand quotas with AWS.
    - Build a reconciler: read window of records from BOTH Kafka and Kinesis,
      diff counts, alert on divergence.

  Phase 1 — Dual-write (producers write to both)
    - Every producer writes each event to Kafka AND Kinesis.
    - On any failure on EITHER, log and alert, but don't fail the request
      (the OTHER system is assumed source-of-truth for now).
    - Reconciler job runs continuously, diff should be ~0.
    - Cost doubles during this phase. Acceptable for migration window.

  Phase 2 — Dual-read (consumers read from both, deduplicate)
    - Consumers switched to a multi-source adapter that reads from Kafka
      AND Kinesis and deduplicates on eventId.
    - Verify consumer output unchanged (downstream invariants still hold).

  Phase 3 — Cut over consumers to Kinesis-only
    - One consumer at a time, flip to Kinesis-only.
    - If a regression appears, rollback is just a config flip.
    - Kafka still receives from producers (dual-write) so rollback is safe.

  Phase 4 — Cut over producers to Kinesis-only
    - Once ALL consumers are proven on Kinesis, and reconciler has been
      silent for a week, stop writing to Kafka.
    - Monitor for 7 days.

  Phase 5 — Decommission Kafka
    - Retain Kafka in read-only mode for 30 days (for emergency replay).
    - After 30 d with no issues, tear down Kafka cluster.

Zero data loss argument:
  - Phase 1: if Kinesis fails, Kafka still has it (and vice versa).
  - Phase 2: dedup ensures no double-processing.
  - Phase 3/4: dual-write still intact during per-consumer rollout; rollback
    is O(minute) via config flip.
  - eventId propagation across both systems is the lynchpin. If producers
    can't generate stable ids, you can't dedupe safely.

Gotchas:
  - PartitionKey for Kinesis must map to a concept similar to Kafka
    partition key — otherwise ordering semantics differ and downstream
    processors break subtly. Use the same key.
  - Kafka headers carry metadata that Kinesis doesn't have natively →
    serialize into the record payload.
  - Retention difference: Kafka often has 30–90 days, Kinesis 24 h default.
    Extend Kinesis retention to 7 days during migration.
  - Throughput ramp: On-Demand starts at 200 MB/s and doubles based on
    last 30-day peak. At 1 GB/s peak you need to PRE-warm by sending
    production-like traffic for weeks, or start in Provisioned mode with
    pre-computed shard count.
```

### Q6: A consumer is processing records and must write them to S3 in exactly-once fashion (no duplicate objects). Design this with just Kinesis and S3 (no Firehose, no Flink).

**Expected Answer:**
```
The insight: S3 PutObject is idempotent — same key + same body = one object.
Use the shard's sequence-number range as the object key.

Consumer logic (per shard):
  1. Read a "window" of records — e.g., 1 minute or 5,000 records, whichever
     first — keep them in memory (or local disk for larger windows).
  2. Compute object key:
        s3://bucket/yyyy=2026/mm=04/dd=18/stream=clicks/shard=0000000000/
            from=<first_seq>/to=<last_seq>.parquet
  3. Serialize batch → parquet → PutObject (deterministic key, content-MD5
     header for integrity).
  4. On 200 response, checkpoint at <last_seq>.
  5. If step 3 or 4 crashes, restart reads from <first_seq+1> (last
     successful checkpoint). New attempt computes the SAME key → same object
     replaced in S3. No duplicates.

Why no duplicates:
  - Key is deterministic function of sequence-number range.
  - S3 overwrites on same key (object version change but single logical object).
  - Downstream readers (Athena) see each key once.

Edge cases:
  - Reshard: parent shard ends mid-window. Finalize partial window,
    checkpoint at SHARD_END. Child shards start fresh windows.
  - Record larger than chunk: keep windows in memory; if > JVM heap, spill
    to disk first (durable temp). Use incremental MPU to stream to S3.
  - Downstream consumer must handle S3 versioning: enable versioning on the
    bucket so old version is auditable; or disable and accept overwrite.
  - Clock skew does NOT matter here — we key on sequence #, not time.

Cost: ~10× cheaper than sentinel-based exactly-once because S3 PutObject
is $0.005/1000 calls and we do 1 per minute per shard.
```

### Q7: You're told to build a fair multi-tenant Kinesis pipeline: tenant A produces 1M rec/s, tenants B-Z each produce <1K rec/s. Tenant A's traffic must never delay tenant Z's latency. Design it.

**Expected Answer:**
```
Problem: on a shared stream, A hogs a shard → Z's records on same shard
slow down. Classic noisy-neighbor.

Three approaches (pick based on tenant count and isolation strength required):

APPROACH 1 — Stream per tenant (strict isolation, few tenants)
  A → stream "events.tenant_a" (10 shards, on-demand)
  B..Z → stream "events.tenant_b" etc. (1 shard each)
  Pros: absolute isolation. Tenant A bug can't hurt Z.
  Cons: 25+ streams = 25× shard-minimum cost. Hard above ~200 tenants.

APPROACH 2 — Shared stream, tenant-aware PartitionKey (pragmatic, many tenants)
  Single stream with On-Demand mode.
  PartitionKey = tenant_id + "#" + user_id
  Rely on On-Demand auto-splitting to isolate hot tenants.
  Consumer uses tenant-aware thread pool so one tenant can't starve another
  INSIDE the consumer.
  Pros: cheap, operationally simple.
  Cons: A burst can briefly delay Z until On-Demand splits (~15 min).

APPROACH 3 — Tiered streams (best of both)
  Large tenants (>10K rec/s): own dedicated stream.
  Small tenants: shared stream (approach 2).
  Auto-promotion: small tenant exceeding 5K rec/s → migration job moves
  them to a dedicated stream at next rollover.
  Pros: isolation where it matters, cost efficiency everywhere else.

Consumer design (matters regardless of approach):
  - Per-shard worker has a TENANT-AWARE inner queue (one sub-queue per
    tenant) with fair scheduling (round-robin or weighted).
  - NEVER a single blocking downstream call per record without a timeout.
  - Circuit breaker per tenant: if tenant A's downstream sink is slow,
    SHED A's records (to a DLQ) and keep processing others.

Backpressure / rate-limiting at ingest:
  - Each tenant has an API gateway quota (e.g., 10K TPS for B-Z, 1M for A).
  - Enforce at the producer layer before Kinesis, not at Kinesis.

Monitoring:
  - Per-tenant: IteratorAge (synthetic), error rate, rec/s.
  - Dashboard alerts at tenant level, not global.
```

### Q8: Explain how KCL handles resharding correctly. A worker receives records from parent and 2 children simultaneously — describe the race, the fix, and what could go wrong if you write your own consumer.

**Expected Answer:**
```
The race:
  T=0  Parent shard P is open, receiving writes.
  T=1  split-shard P → C1, C2. P is now CLOSED (no new writes, but still
       has unread records r100..r150).
  T=1  KCL workers discover C1 and C2 via ListShards (cached TTL of 30 s).
  T=2  A worker's lease-balancer wants to lease C1 and C2 so they start
       processing. But if it starts reading C1 before draining P, a record
       for PK="user_42" that was written to C1 at T=1.5 might be processed
       BEFORE r100 (which is on P and has the same PK).
       → Per-PartitionKey ordering VIOLATED.

KCL's fix (parent-child handshake):
  - Lease table row for C1 has `parentShardIds = [P]`.
  - Lease-taker refuses to lease C1 unless lease for P has status
    SHARD_END in its checkpoint column.
  - Worker owning P reads until records run out, calls shardEnded() callback.
  - User code MUST call input.checkpointer().checkpoint() inside shardEnded().
  - This atomically sets P's checkpoint to SHARD_END.
  - Now lease-taker leases C1 (and C2), starts processing from TRIM_HORIZON
    of the child.

What goes wrong if you write your own consumer (common mistakes):
  1. You call ListShards, see all of P, C1, C2, and launch 3 workers in
     parallel. → Out-of-order per PK. Subtle, intermittent bugs.
  2. You correctly drain P first, but forget to checkpoint SHARD_END —
     on restart, you re-read P from scratch. Duplicates.
  3. You drain P but use `TRIM_HORIZON` for children instead of the child's
     actual first SequenceNumber. Harmless if you dedup, wasteful otherwise.
  4. You drain P and then read only ONE child (forget to discover C2).
     Silent data loss for half the hash range.
  5. During a 3-way merge (impossible in KDS — only 2-into-1 merges exist,
     but suppose for other streaming systems) the ordering logic nests.

Scaling observation:
  During a split in a high-throughput stream, you momentarily have 3 shards
  readable for the same hash range — until P drains. Account for 1.5×
  temporary read throughput for the duration.
```

### Q9: Design a Kinesis-based "request-response" (RPC) system for async jobs — one service sends a job and eventually receives the result on a reply channel. What breaks and how do you fix it?

**Expected Answer:**
```
Naive design:
  Requester → KDS "jobs" → Worker → KDS "results" → Requester
  Requester reads "results" stream, filters for its own job IDs.

Problems:
  1. EVERY requester reads the ENTIRE "results" stream.
     → 2 MiB/s/shard divided by N requesters — doesn't scale.
  2. Fan-out explosion: 1000 requesters = 1000 consumers.
     → Only 20 EFO consumers per stream.
  3. Latency: results arrive 200 ms–2 s after job completes (polling).
  4. No timeout / cancellation signal.

Better design:

  Requester assigns jobId = UUID + replyShardHint.
    Requester → KDS "jobs" → Worker pool
    Worker completes job, writes result to:
      DynamoDB table (jobId PK, TTL 1 h)
      then optionally: SNS topic "job-complete" → Requester's SQS queue
        (Requester subscribes its queue to the topic with a filter on
         replyShardHint or jobId prefix matching its worker set)

  Requester reads ITS OWN SQS queue → gets the jobId → looks up result
  in DynamoDB → done.

Why this fixes the problems:
  1. No shared scan — each requester reads only jobs addressed to it.
  2. SNS fan-out is unbounded (no 20-consumer limit).
  3. SNS → SQS is push-ish (Lambda ESM), 50–200 ms.
  4. Requester can CancelRequest by writing a tombstone in DynamoDB.

Alternative: long-poll DynamoDB with conditional GetItem+stream subscribe.
  Or use DynamoDB Streams → Lambda → WebSocket push to the caller.

Why Kinesis is the RIGHT choice for "jobs" but WRONG for "results":
  - "jobs": durable, ordered per userId, multiple worker pools can replay
    on crash — perfect for a replayable work queue.
  - "results": each result has exactly one recipient; best modeled as
    a per-recipient mailbox (SQS) or a lookup-by-id table (DynamoDB).

Common mistake:
  Using a FIFO SQS queue for results WITH MessageGroupId = requester_id.
  → Head-of-line blocking inside one requester's messages. Good.
  → But cross-requester isolation is not guaranteed if you share a queue.
  → Better: one SQS queue per requester, subscribed to one SNS topic.

Metric to watch:
  end-to-end response latency p99 = time from PutRecord on "jobs" stream
  to message available in requester's SQS. Breaks down into:
    jobs-stream ingest, worker process time, dynamodb write, sns fanout,
    sqs delivery. Trace each.
```

### Q10: A critical Kinesis consumer in production is "stuck" — IteratorAge is growing, but CPU is idle, no errors in logs, DynamoDB lease table has current heartbeats. You have 30 minutes before retention expires. Walk through diagnosis and mitigation.

**Expected Answer:**
```
The 30-minute runbook:

T+0:00 (minutes elapsed)
  Confirm scope:
  - Which shards? All, or a subset?  → CloudWatch IteratorAgeMilliseconds by shard.
  - Which consumer? Check ConsumerARN / lease-table owner column.
  - Is IteratorAge still growing, or leveled off?

T+0:02 — Mitigate FIRST, diagnose second.
  Extend retention IMMEDIATELY to buy time:
  aws kinesis increase-stream-retention-period --retention-period-hours 168
  → Now you have 7 days to debug, not 30 min. Cost ≈ 2× on long-term tier.

T+0:04 — Quick hypothesis check.
  Heartbeats OK but IteratorAge growing:
  → consumer is alive but not actually calling GetRecords / SubscribeToShard
    (or is receiving records but not processing them).

  Check #1: is the application pulling records?
    - Enable DEBUG logging in KCL for 30 seconds.
    - Watch for ProcessRecordsInput events.
    - Look at AWS SDK client metrics for GetRecords call count and latency.

  Check #2: is the application hanging in user code?
    - Take 3 thread dumps 10 seconds apart.
    - Diff them — threads stuck in same stack = deadlock or slow dependency.
    - Most common: synchronous call to a downstream DB/API that silently hung
      (no timeout on HTTP client = thread stuck for hours).

  Check #3: is there a poison pill?
    - If the same record batch has been re-read N times (check lease-table
      checkpoint — did it advance?), consumer is stuck retrying a bad record.
    - KCL will retry forever by default unless you configure
      MaxRetryAttempts.

T+0:10 — Common root causes, in order of frequency:
  1. Downstream sink (DynamoDB, RDS, external API) is slow / throttled.
     Consumer threads blocked on I/O; CPU idle but no progress.
     → Check downstream dashboards.
  2. KCL worker thread pool saturated.
     → Increase maxLeasesPerWorker, recordBuffer, or scale OUT workers.
  3. Poison pill causing infinite retry.
     → Add DLQ: on N retries, publish record to DLQ and skip.
  4. DynamoDB lease table throttle.
     → Heartbeats work (small writes) but CHECKPOINT writes throttle;
       check DynamoDB WriteThrottleEvents metric for lease table.
  5. GC pressure on consumer JVM.
     → 30-second pause looks like "CPU idle" briefly; repeated = death spiral.
     → Heap dump + profiler.

T+0:15 — Mitigation options (in increasing order of risk):
  A) Scale out consumers 2× — more workers take more leases.
  B) Restart current consumer fleet — one at a time, 30 s apart.
     If hang is due to a stuck thread, restart clears it.
  C) Temporarily skip poison pill: manually advance checkpoint to sequence
     AFTER the stuck record (write directly to DynamoDB lease table).
     Losses single record but unblocks shard.
  D) Drop downstream dependency: reconfigure consumer to write only to
     Firehose → S3 (trivial sink). Come back and replay later once the root
     cause is fixed.

T+0:25 — Verify recovery:
  - IteratorAge decreasing, not increasing.
  - Lease-table checkpoint column advancing for all shards.
  - Downstream sinks receiving records at expected rate.

Postmortem owed:
  - Why did the shared dashboard not alert at IteratorAge > 5 min?
    (Your alarm threshold was set to 1 h. Tighten it.)
  - Why was the downstream call not timed out? (Add per-request timeout.)
  - Why did KCL retry indefinitely? (Set MaxRetryAttempts + DLQ.)
  - Why did we only discover this 30 min from data loss? (SLO / alerting gap.)
```

---

## 22. Quick Reference Card

```
────────────────────────────────────────────────────────────────────────────
 KINESIS DATA STREAMS — CHEAT SHEET                                         
────────────────────────────────────────────────────────────────────────────

 WHEN TO USE KDS
   ✓ multi-consumer fan-out, replay, ordering per key, <1 s latency, AWS-native
   ✗ one consumer + delete semantics → SQS
   ✗ sub-10 ms latency or multi-region active-active → MSK / Kafka
   ✗ one destination, no replay → Firehose

 SHARD LIMITS (per shard)
   write: 1 MiB/s OR 1,000 rec/s    record size: 1 MiB
   read:  2 MiB/s (shared)           GetRecords: 5 /sec
   read:  2 MiB/s per EFO consumer   max 20 EFO consumers/stream

 PARTITION KEY CHOICES
   good:  userId, orderId, sessionId            (hierarchical, naturally uniform)
   bad:   tenantId (skewed), constant (1 shard), date (time-skewed)
   escape: ExplicitHashKey to force a shard    (multi-producer fan-in)

 PRODUCERS
   low rate:      PutRecord
   normal:        PutRecords (batch ≤500 records, ≤5 MiB)
   >5K rec/s:     KPL with aggregation (100:1 packing typical)
   CHECK:         FailedRecordCount; retry only failures

 CONSUMERS
   simplest:      Lambda event-source-mapping (AWS manages polling)
   high-thru:     KCL v2 (Java/Python)
   low-latency:   EFO via SubscribeToShard
   exactly-once:  KDA (Managed Flink) OR idempotent key in sink

 CHECKPOINTING
   store:         DynamoDB lease table (KCL default)
   cadence:       every 60 s OR every 10 K records, whichever first
   table size:    ~7 WCU per 200 shards × 10 workers (on-demand billing)

 RESHARDING
   split → 2 children, parent CLOSED; drain before reading children
   merge → 2 adjacent → 1 child
   KCL handles parent-child handshake via `parentShardIds` column
   custom consumer MUST implement the handshake to preserve PK order

 MODES
   Provisioned:   you choose shard count; cheaper at >40% utilization
   On-Demand:     auto-scales every 15 min; 200 MiB/s default; spiky-friendly

 RETENTION
   24 h default (free) → 7 d extended → 365 d long-term (tiered pricing)
   decrease is IRREVERSIBLE and silently skips now-expired records

 ALARMS (non-negotiable)
   IteratorAgeMilliseconds > 300,000 (5 min)   P1: drop in throughput or crash
   WriteProvisionedThroughputExceeded > 0      P2: hot shard or under-provisioned
   ReadProvisionedThroughputExceeded > 0       P2: too many consumers or slow
   Lambda ESM errors > 0                       P2: poison pill or downstream fail
   DynamoDB lease table ThrottleEvents > 0     P3: checkpoint cadence too high

 COST LEVERS
   KPL aggregation → 10–100× fewer PUT payload units
   Firehose attached to stream → skip building S3 writer
   Provisioned over On-Demand at steady state > 40% utilization
   EFO only for latency-critical consumers (not every consumer)
   On-demand DynamoDB for lease table (spiky traffic)

 MOST COMMON PRODUCTION INCIDENTS
   1. Hot shard (PK = tenant_id)              → better PK + split
   2. IteratorAge grows during marketing blast → scale consumers + EFO
   3. FailedRecordCount ignored                → silent data loss on throttles
   4. Checkpoint every record                   → DynamoDB lease table throttles
   5. Parent-child handshake skipped in custom consumer → order violations
   6. us-east-1 regional outage (Nov 2020)      → multi-region active-active

 GROUND TRUTH TO QUOTE
   AWS Kinesis Data Streams Developer Guide
   AWS Streaming Data Solutions Whitepaper (2022)
   AWS Post-Event Summary: Nov 25, 2020 Kinesis Outage
   re:Invent sessions ANT325, ANT402, ANT418
   Netflix Tech Blog: "Evolution of the Netflix Data Pipeline"
   Alex Xu — System Design Interview Vol 2, Ch. "Real-time Data Pipeline"
   Arpit Bhyani — "System Design for High Throughput Streaming" (YouTube)
────────────────────────────────────────────────────────────────────────────
```

---

*Last reviewed: 2026-04. If a limit cited here seems wrong, check the AWS Service Quotas console — AWS revises Kinesis limits every few months.*

---

## Hands-on Lab — See it all running

Everything in this document has a corresponding operation in the Java playground next to this file:

**[`examples/kinesis-java-demo/`](examples/kinesis-java-demo/)**

```
| This doc section                          | Playground menu item                 |
|-------------------------------------------|--------------------------------------|
| §4 Hash ring, shard routing                | 7 — List shards + hash ranges         |
| §4 Hot shard failure mode                  | 4 — Batch with HOT partition key      |
| §4 ExplicitHashKey escape hatch            | 5 — PutRecord with ExplicitHashKey   |
| §5 Per-shard SequenceNumber ordering       | 1, 2 — see SequenceNumber returned   |
| §6 PutRecord vs PutRecords                 | 1 vs 3                                |
| §6 Partial failures in PutRecords          | 6 — Stress test shows FailedRecordCt |
| §7 Competing consumers (mini-KCL)          | 12 — One worker thread per shard      |
| §8 TRIM_HORIZON / LATEST / AT_TIMESTAMP    | 8, 9, 10                              |
| §9 Resharding (SplitShard, MergeShards)    | 14, 15                                |
| §10 Retention increase                     | 16                                    |
| §17 CloudWatch metrics (alarms)            | 17                                    |
| §17 IteratorAgeMilliseconds P1 metric      | 11                                    |
```

Set-up (one-time, ~3 min): create an IAM user with `AmazonKinesisFullAccess`, `aws configure`, then:

```bash
cd src/HLD/Components/examples/kinesis-java-demo
mvn compile exec:java
```

The lab auto-creates two streams (`kinesis-demo-ondemand`, `kinesis-demo-provisioned` with 2 shards). Everything you do is visible in **AWS Console → Kinesis → Data streams → Data viewer**.
