# Stream Processing

> **Difficulty:** Medium-Hard | **Time:** 3 hours | **Priority:** Good to Know

Stream processing is how modern systems compute over **unbounded, continuously arriving data** in near real time. It powers fraud detection, live dashboards, recommendations, IoT analytics, surge pricing, and countless other features where "wait until tomorrow's batch job" is not an option.

This note goes deep on the concepts you will actually be asked about in an HLD interview: batch vs stream, Lambda vs Kappa, event-time vs processing-time, watermarks, windows, state, checkpointing, delivery semantics, and the real architectures Uber, Netflix, and LinkedIn run in production.

---

## 1. Batch vs Stream Processing

```
BATCH PROCESSING                         STREAM PROCESSING
================                         =================

Collect data → Process all at once       Process each event as it arrives

 [data]                                   [data]──►[process]──►[output]
 [data]  ──collect──►  [BIG BATCH]               (continuous pipeline)
 [data]                     │
 [data]                     ▼
               ┌─────────────────────┐
               │ Run job once/hour   │
               │ or once/day         │
               └──────────┬──────────┘
                          ▼
                     [results]

 Latency:   minutes → hours              Latency:   milliseconds → seconds
 Input:     bounded (finite)             Input:     unbounded (infinite)
 State:     not required                 State:     essential (windows, joins)
 Failures:  restart the job              Failures:  must not lose events
 Examples:  nightly ETL, ML training,    Examples:  fraud detection, alerting,
            reporting, backfills                    recommendations, dashboards
```

### When do you actually need streaming?

| Symptom in requirements | Answer |
|---|---|
| "Real-time", "live", "as-it-happens" | Streaming |
| "Notify within seconds of X" | Streaming |
| "Analytics up to the last second" | Streaming (or incremental) |
| "Nightly report", "last 24h" | Batch is fine |
| "Train a model on historical data" | Batch |
| "Fraud decision before payment completes" | Streaming (must be low-latency) |

> **Interview rule of thumb:** If the business requirement uses the word "real-time", or if a human or an automated decision depends on data within seconds of the event, reach for stream processing.

---

## 2. Lambda vs Kappa Architecture

Two canonical blueprints for combining historical accuracy with real-time insights.

### 2.1 Lambda Architecture (the classic)

```
                      ┌────────────────────┐
  Events ────────────►│   Kafka (Log)      │
                      └─────────┬──────────┘
                                │ (same events)
                   ┌────────────┴────────────┐
                   ▼                         ▼
          ┌────────────────┐        ┌────────────────┐
          │  BATCH LAYER   │        │  SPEED LAYER   │
          │                │        │                │
          │ Spark, Hadoop, │        │ Flink, Kafka   │
          │ Hive           │        │ Streams, Storm │
          │                │        │                │
          │ High accuracy, │        │ Low latency,   │
          │ high latency   │        │ approximate    │
          │ (hours)        │        │ (seconds)      │
          └───────┬────────┘        └────────┬───────┘
                  │  batch views             │  real-time views
                  ▼                          ▼
                ┌──────────────────────────────┐
                │      SERVING LAYER           │
                │  (Cassandra / Druid / ES)    │
                │  merge: batch + real-time    │
                └──────────────────────────────┘
                              │
                              ▼
                         Query clients
```

**Pros:** accurate historical results + fast approximate real-time.
**Cons:**
- **Logic drift** — the batch and streaming implementations inevitably diverge (different APIs, different late-data handling, different null semantics). You end up with two different "truths."
- Two codebases, two systems to operate, two bugs to reproduce.

### 2.2 Kappa Architecture (streaming-first)

```
                     ┌────────────────────────┐
  Events ───────────►│  Kafka (long-retention │
                     │  event log — source    │
                     │  of truth)             │
                     └─────────┬──────────────┘
                               │
                               ▼
                     ┌───────────────────────┐
                     │   STREAM PROCESSOR    │
                     │   (Flink / Spark      │
                     │    Structured / KS)   │
                     └──────────┬────────────┘
                                │
                                ▼
                     ┌───────────────────────┐
                     │   Serving store       │
                     │   (materialized views)│
                     └───────────────────────┘

  Reprocessing = replay the Kafka log from offset 0 through the same job.
```

**Pros:** one codebase, one system, one truth. Reprocessing history = rewind the log.
**Cons:** replaying terabytes of log is expensive; requires long-retention Kafka or tiered storage.

### 2.3 Modern "lakehouse + streaming" pattern (2025+)

Most big-tech teams no longer pick "pure Lambda" or "pure Kappa." Uber, Netflix, and others use:

```
  Events ─► Kafka ─► Flink ─┬─► Real-time materialized views (Pinot / Druid / RW)
                            └─► Iceberg / Hudi / Delta lake on S3
                                        │
                                        ▼
                               Spark / Trino batch reads
                               (ad-hoc analytics, ML training)
```

Single write path (streaming), but the same data is usable for both real-time and batch analytics. Uber's **IngestionNext** followed exactly this pattern — Flink → Hudi tables on S3 with transactional commits, reducing data-lake freshness from hours to minutes.

---

## 3. The Two Times: Event Time vs Processing Time

This is the single most important concept in stream processing. Almost all watermark / windowing confusion comes from mixing these up.

```
  REAL WORLD                           YOUR PIPELINE
  ----------                           -------------
  08:00:00  User clicks "Buy"         08:00:03  Event arrives at Kafka
  08:00:01  Phone loses network       08:00:04  Flink receives it
  08:00:15  Phone reconnects          08:00:15  Flink processes
            (buffered event sent)

  event_time    = 08:00:00  (when it happened)
  ingest_time   = 08:00:03  (when it entered the system)
  processing_time = 08:00:15 (when your operator handled it)
```

```
  Event time is WHAT                Processing time is WHEN
  happened in the real world.       the system got around to it.

  Deterministic on replay:          Non-deterministic on replay:
  same input → same output          replay gives different windows
```

**Why it matters:**
- Event time lets you compute **correct** windowed aggregates even when events arrive out of order or late.
- Reprocessing historical data with event-time logic is **reproducible** — critical for audit, debugging, and backfills.
- Processing time is simpler and lower latency but **wrong** for any "X per minute at source" metric.

### 3.1 Out-of-order events are the norm

```
  Events produced at:  1s   2s   3s   4s   5s   6s
                       │    │    │    │    │    │
                       ▼    ▼    ▼    ▼    ▼    ▼
  Events arrive at:    1s   2s   5s   3s   4s   6s
                                 ▲    ▲    ▲
                                 └─ arrived late because of mobile network,
                                    GC pauses, partition rebalance, etc.
```

---

## 4. Watermarks — "I believe time has progressed to T"

A **watermark** is a timestamped marker injected into the stream that tells operators: *"I don't expect any more events with event_time ≤ T to arrive."*

It's the system's best guess — not a guarantee — and it's what lets a window ever close and emit a result.

```
  Stream of events (event_time in parentheses):

  ...─[e(8)]─[e(7)]─[e(9)]─[W:7]─[e(8 late!)]─[e(10)]─[W:9]─[e(11)]─►
                              ▲                          ▲
                              │                          │
                    "No more events with           "No more events with
                     event_time ≤ 7 expected"       event_time ≤ 9 expected"
```

### 4.1 Most common strategy: bounded out-of-orderness

```
  watermark = max_observed_event_time − allowed_lateness
```

Trade-off in a single line:

```
  allowed_lateness  ↑   →  results more complete, but slower to emit
  allowed_lateness  ↓   →  results faster, but may drop late events
```

### 4.2 Handling late events

When an event arrives after the watermark has passed its window:

| Strategy | What happens | Used when |
|---|---|---|
| **Drop** | Event discarded | Rough metrics, cheap to lose a few |
| **Allow lateness** | Keep window open extra N minutes | Most common default |
| **Side output** | Route to a "late events" stream | Audit/reconciliation |
| **Update & re-emit** | Recompute window, emit updated result | Billing, exact counts |

### 4.3 The idle-source trap

If *any* upstream partition stops producing events, its watermark stops advancing → downstream watermarks are pinned to the minimum → **nothing ever closes**. Flink/Kafka Streams handle this with **idle source detection**; always configure it in production.

---

## 5. Windowing in Depth

Windows slice an infinite stream into finite chunks you can aggregate over.

### 5.1 Tumbling window — fixed size, non-overlapping

```
  Time →
  |──Window 1──|──Window 2──|──Window 3──|──Window 4──|
  0           5s           10s          15s          20s

  Each event belongs to exactly one window.
  "Count orders per 1-minute window"
```

### 5.2 Sliding window — fixed size, overlapping

```
  Time →
  |──Window A (size=6s, slide=2s)──|
         |─────Window B─────|
                |─────Window C─────|
                       |─────Window D─────|
  0   2s   4s   6s   8s  10s  12s

  Each event can belong to multiple windows.
  "Moving average of CPU over 5 minutes, updated every 30 seconds"
```

### 5.3 Session window — dynamic, gap-based

```
  User events (with 5-min inactivity gap → new session):

  [e][e][e] ......10 min gap...... [e][e] ...6 min gap... [e][e][e]
   ────Session 1────                ─Session 2─            ───Session 3───

  Used for: user sessions, shopping carts, IoT device activity bursts
```

### 5.4 Global window + custom trigger — the "advanced" one

A single window containing every event. You attach a custom trigger that decides when to emit:

```
  trigger: "after every 1000 events OR after 10 seconds of inactivity"
```

Use this for complex triggering rules that tumbling / sliding can't express.

### 5.5 Picking the right window

| Requirement | Window |
|---|---|
| "Clicks per minute" | Tumbling |
| "Rolling average over last 5 min, updated each minute" | Sliding |
| "Group activity into user sessions" | Session |
| "Emit as soon as 100 events or 10s" | Global + trigger |

---

## 6. Stream Processing Frameworks (2026 reality)

These tools are **not equivalent** — they serve different roles. A very common interview trap is treating them as interchangeable.

```
┌───────────────────┬──────────────────┬──────────────────┬───────────────────┐
│ Dimension         │ Apache Flink     │ Kafka Streams    │ Spark Structured  │
│                   │                  │                  │  Streaming        │
├───────────────────┼──────────────────┼──────────────────┼───────────────────┤
│ Shape             │ Cluster / job    │ Java library     │ Cluster / job     │
│                   │ (true stream)    │ embedded in app  │ (micro-batch)     │
│ Processing model  │ Record-at-a-time │ Record-at-a-time │ Micro-batch       │
│ Latency (P99)     │ sub-100 ms       │ ~10s-100s ms     │ 100 ms–seconds*   │
│ State backend     │ RocksDB + incr.  │ RocksDB          │ RocksDB (3.2+)    │
│                   │ checkpoints      │                  │                   │
│ State scale       │ TBs per node     │ GBs per instance │ GBs per executor  │
│ Exactly-once      │ Yes (2PC sinks)  │ Yes (Kafka tx)   │ Yes               │
│ Event time+WMs    │ First-class      │ Yes              │ Yes               │
│ SQL               │ Flink SQL        │ ksqlDB           │ Spark SQL         │
│ Connectors        │ Huge ecosystem   │ Kafka only       │ Huge (unified     │
│                   │                  │                  │ with batch)       │
│ Ops complexity    │ Medium-High      │ Low (just a jar) │ Medium            │
│ Sweet spot        │ Large, stateful, │ Kafka-centric    │ Unified batch +   │
│                   │ low-latency      │ microservices    │ stream on Spark   │
└───────────────────┴──────────────────┴──────────────────┴───────────────────┘

  * Databricks "Real-Time Mode" (2025+) reaches 15–300ms P99 but only for
    stateless Scala queries, Databricks-only.
```

### Key recent evolution

- **Flink 2.0 (March 2025)** introduced **disaggregated state** via ForStDB — state lives in remote storage (S3) instead of being pinned to a TaskManager, enabling elastic scaling without huge state moves.
- **Spark 3.2+** added RocksDB state; earlier versions kept state in-heap which capped scalability.
- **Kafka Streams** remains the simplest to deploy — it's *just a library*. No cluster, no scheduler. Scale by starting more instances of your app; partitions are rebalanced across them.

### Which to pick in an interview

```
  Need sub-100ms latency + huge state + event-time?
    ────►  Flink

  Already on Kafka, microservice-style, small team?
    ────►  Kafka Streams (or ksqlDB for SQL)

  Unified batch + stream, team on Spark, Databricks?
    ────►  Spark Structured Streaming
```

> **Don't forget:** **Kafka itself is not a stream processor.** It's the durable, partitioned log that *all* of the above sit on top of.

---

## 7. State Management and Checkpointing

A streaming pipeline without state is trivial (just `map/filter`). The moment you aggregate, join, deduplicate, or detect patterns, you need state — and state must survive failures.

### 7.1 Stateful operators

```
  Examples of state:
    ─ counter per key    (running total orders per user)
    ─ window buffer      (events in the current 5-min window)
    ─ last-seen value    (stream-table join lookups)
    ─ dedup set          (event IDs seen in last hour)
    ─ pattern matcher    (CEP state machine per session)
```

### 7.2 Checkpointing — how state survives crashes

```
  Flink checkpoint barrier flow:

   ┌──────┐       ┌────────┐       ┌─────────┐       ┌──────┐
   │Source│──┐    │  Map   │       │Aggregate│       │ Sink │
   └──────┘  │    └────────┘       └─────────┘       └──────┘
             │ B       │ B              │ B              │
             ▼         ▼                ▼                ▼
           [state snapshot at each operator, stored to S3/HDFS]

   B = barrier (special marker injected into stream every N seconds)

   On failure:  restart all operators from the last completed checkpoint,
                rewind Kafka offsets to the positions recorded in that
                checkpoint → re-process events after that point.
```

### 7.3 Incremental (RocksDB) checkpoints

For multi-TB state, a full snapshot every 30s is impossible. RocksDB + **incremental checkpointing** writes only SST files that changed since the previous checkpoint → cheap, frequent checkpoints even with enormous state.

### 7.4 Aligned vs unaligned checkpoints

```
  ALIGNED (default):
    Operator waits until barrier arrives on ALL input channels
    before snapshotting. Great for correctness, but slow under
    backpressure (barriers queue behind buffered data).

  UNALIGNED (Flink 1.11+):
    Barriers overtake in-flight buffers; the buffered data is
    included in the checkpoint itself.
    → Checkpoint duration becomes independent of throughput.
    → Costs extra I/O to state storage.
```

Use **unaligned** when your job is often backpressured. Use **buffer debloating** (Flink 1.14+) as a lighter-weight first step.

---

## 8. Delivery Semantics — At-Most / At-Least / Exactly-Once

These describe the guarantee about *how many times* an event affects the final output.

```
  AT-MOST-ONCE                AT-LEAST-ONCE             EXACTLY-ONCE
  ────────────                ─────────────             ────────────
  Might lose events           Never lose events         Never lose, never dup
  Never duplicate             May duplicate             (effectively)

  Fire-and-forget             Retry until ACK           Retry + dedupe /
                              (idempotent required)     transactional sinks

  Metrics that can be         Most real systems         Billing, payments,
  sampled, telemetry                                    one-time emails
```

### 8.1 "Exactly-once" is almost always **effectively once**

True physical exactly-once delivery is impossible over a network. What Kafka/Flink give you is:

```
  (1) Kafka transactions (atomic writes across partitions)
  (2) Two-phase commit with sinks (pre-commit → commit on checkpoint)
  (3) Idempotent sinks keyed on (event_id | offset)

  Together → the end-to-end effect is as if each event was processed once.
```

### 8.2 Designing for exactly-once in practice

- Give every event a **stable unique ID** at the source.
- Make sinks **idempotent** (UPSERT by key, conditional writes, dedupe tables).
- Use Kafka's **transactional producer** + `isolation.level=read_committed` consumers.
- For non-transactional sinks (HTTP APIs, third parties), do **at-least-once + idempotency key**.

---

## 9. Core Streaming Patterns

### 9.1 Event Sourcing

Store every state change as an immutable event; current state = fold over events.

```
  Events for order #42:

  [OrderCreated(items=[]) ]
  [ItemAdded(sku=ABC, qty=1)]
  [ItemAdded(sku=XYZ, qty=2)]
  [DiscountApplied(10%) ]
  [OrderPaid(card=****1234)]
  [OrderShipped(tracking=T1)]

  Current state = replay these events in order.
  Time-travel:  stop the replay at any point to see past state.
```

- **Pros:** full audit, perfect history, rebuild any projection, time-travel debugging.
- **Cons:** event store grows forever (→ snapshots every N events to avoid long replays), schema evolution across old events is hard.
- **Used by:** banking ledgers, Git itself, e-commerce order history, accounting systems.

### 9.2 CQRS (Command Query Responsibility Segregation)

```
  ┌──────────┐     ┌──────────┐                       Event log
  │ Commands │────►│  Write   │────► [Events] ───────────┐
  │ (writes) │     │  Model   │      (append-only)       │
  └──────────┘     └──────────┘                          │
                                                         ▼
  ┌──────────┐     ┌──────────┐                ┌───────────────┐
  │ Queries  │◄────│  Read    │◄──────────────►│  Projection   │
  │ (reads)  │     │  Model   │                │ (denormalized │
  └──────────┘     └──────────┘                │  views)       │
                                               └───────────────┘
     (e.g. OrderDetailsView, CustomerLTVView, AnalyticsCube...)
```

- Write model = normalized, event-sourced.
- Read model = denormalized, one projection per query shape.
- You can add new read models (e.g. a new dashboard) by replaying the log — without touching the write side.

### 9.3 Change Data Capture (CDC)

Stream the changes in your OLTP database to everything else that needs to stay in sync.

```
  ┌────────────┐      ┌──────────┐       ┌────────────────────────┐
  │ PostgreSQL │─WAL─►│ Debezium │──────►│ Kafka topic users.cdc  │
  │ (source of │      │ (CDC     │       │  [INSERT user_1]       │
  │  truth)    │      │  conn.)  │       │  [UPDATE user_2]       │
  └────────────┘      └──────────┘       │  [DELETE user_3]       │
                                         └──────────┬─────────────┘
                                                    │
                         ┌──────────────────────────┼──────────────────────┐
                         ▼                          ▼                      ▼
                  ┌────────────┐            ┌────────────┐          ┌────────────┐
                  │Elasticsearch│           │ Snowflake  │          │ Redis cache│
                  │  (search)   │           │  (DWH)     │          │  (hot data)│
                  └────────────┘            └────────────┘          └────────────┘
```

- CDC replaces brittle dual-writes ("update DB, then update ES") that inevitably desynchronize.
- Debezium, Maxwell, AWS DMS are the common implementations.
- Pairs beautifully with Kafka Connect for sinks.

### 9.4 Stream enrichment (stream-table join)

Incoming events are often lean; you need to join them with reference data.

```
      Clicks stream             Users table (changelog)
    ─[uid=1, url=/a]─►         ─[u=1, country=US]─►
    ─[uid=7, url=/b]─►         ─[u=7, country=DE]─►
          │                             │
          └────────── JOIN on uid ──────┘
                        │
                        ▼
              enriched click events:
              {uid=1, url=/a, country=US, plan="pro"}
```

- Keep the "table side" as a compacted Kafka topic / KTable (Kafka Streams) / broadcast state (Flink) / dim table (Spark).
- Common for: session enrichment, ad personalization, fraud features.

### 9.5 Stream-stream join (windowed)

Join two live streams within a time window — e.g. match clicks with impressions within 30 minutes.

```
   impressions ─┐
                ├──[window=30min join on ad_id]──► CTR per ad
   clicks ──────┘
```

### 9.6 CEP (Complex Event Processing)

Detect patterns across multiple events, e.g.:

```
  LOGIN(userA) → FAILED_LOGIN × 5 → LOGIN(userA, different country) within 5 min
    → SECURITY_ALERT
```

Flink CEP, Esper, or SQL with `MATCH_RECOGNIZE` do this.

---

## 10. Backpressure — when the consumer can't keep up

```
  Producer  ───────► Kafka ───────► Consumer (slow!)
   (fast)              │                   │
                       ▼                   │
           lag grows, buffers fill  ◄──────┘
```

### What backpressure *is*

A feedback signal that tells upstream "slow down" — either explicitly (reactive streams) or implicitly (full buffers, blocked writes).

### Symptoms

- Consumer lag on Kafka climbs and never recovers.
- Checkpoint times explode (barriers can't traverse clogged buffers).
- End-to-end latency grows from seconds to minutes.

### Mitigations

1. **Scale out the slow operator** (more partitions, more parallelism).
2. **Optimize the hot path** (batch writes, async I/O, avoid per-record GC).
3. **Buffer debloating** (Flink 1.14+) to shrink in-flight buffers.
4. **Unaligned checkpoints** so checkpoints don't stall under pressure.
5. **Shedding** — drop low-priority work (e.g. downsample metrics) when catastrophic.
6. **Async sinks** — never do synchronous HTTP per record; batch + async.

---

## 11. Real-World Case Studies

### 11.1 Uber — IngestionNext & uForwarder

```
   Mobile app / backend services
                │
                ▼
           ┌───────────┐
           │   Kafka   │  ←── 1,000+ topics, trillions of msgs/day
           └─────┬─────┘
                 │
         ┌───────┴────────┐
         ▼                ▼
   ┌──────────┐    ┌───────────────┐
   │  Flink   │    │  uForwarder   │  (push-based proxy, solves
   │  jobs    │    │  → services   │   head-of-line blocking &
   └─────┬────┘    └───────────────┘   partition-scaling limits)
         ▼
   ┌──────────────────┐
   │  Apache Hudi     │  (transactional commits,
   │  tables on S3    │   minutes-fresh data lake)
   └─────┬────────────┘
         ▼
   Batch analytics (Spark / Presto), ML training, Surge pricing,
   ETA models, fraud detection
```

Key wins:
- **Data-lake freshness** went from hours → minutes (so ML models are trained on fresher data).
- **Cost** fell because streaming scales with traffic instead of paying for worst-case batch scheduling.

### 11.2 Netflix — Keystone + event mesh

- **~2 trillion events/day (~23M events/sec)** — plays, pauses, seeks, search, errors, A/B impressions, recommendations, logs.
- **Keystone Router** sits on top of Kafka to provide schema evolution, consumer-specific filtering, and routing — so consumers don't each re-implement filtering.
- Recommendation engine = **Lambda-style**: nightly Spark jobs train collaborative-filtering models; real-time Flink pipelines adjust what you see within seconds.

### 11.3 LinkedIn — the birthplace of Kafka

- Kafka was built at LinkedIn in 2010 to solve the same N×M problem ("every service needs events from every other service").
- Today powers news-feed, notifications, anti-abuse, tracking, metrics.
- Samza (originally) and now Flink for complex processing; Kafka Streams for light microservice-scoped logic.

### 11.4 Stripe — exactly-once billing

- Every webhook and internal event has a UUID.
- Idempotency keys on every API mutation.
- Event-sourced ledger: a payment is a sequence of events; projections build the customer balance, the accounting GL, the fraud features — all from the same log.

---

## 12. Mini-Design: Real-Time Fraud Detection

A worked example that threads together almost every concept above.

```
   Card swipe ──┐                                             Alert / block
   Login     ──┼──►  Kafka ──► Flink job ──┬─► Feature store (Redis)
   Geo       ──┘                            ├─► Fraud model (inference)
                                            └─► Rule engine
                                                     │
                           ┌─────────────────────────┼─────────────────────┐
                           ▼                         ▼                     ▼
                   Allow tx + log            Step-up (OTP)            Block + ticket
```

- **Event time** used so replays (after model retrain) produce identical decisions.
- **Sliding windows** for velocity features: tx count in last 1, 5, 60 min per card.
- **Session windows** per user+device for login-burst detection.
- **Stream-table join**: enrich each swipe with latest known device/location (KTable).
- **Exactly-once** from card gateway → Kafka → Flink → decision store (via Kafka transactions + idempotent sink).
- **Watermarks**: `max_event_time − 2s` — fraud systems must react fast, so small allowed lateness; very late events go to a side output for post-hoc review.
- **Backpressure handling**: async calls to the model server; unaligned checkpoints so barrier propagation never blocks checkout latency.

---

## 13. Common Interview Pitfalls

| Pitfall | Correct mental model |
|---|---|
| "Kafka is a stream processor" | Kafka is a log. Processors (Flink, KS) sit on top. |
| "Spark Streaming and Flink are the same" | Spark = micro-batch, Flink = true streaming; latency profiles differ by an order of magnitude. |
| "We'll use processing time" | Almost always wrong for business metrics. Event time + watermarks. |
| "Exactly-once is free" | It requires transactional sinks OR idempotency. Never claim it without saying how. |
| "Just add more consumers to scale" | You're capped by the number of Kafka partitions. Plan partitions up front. |
| "We'll checkpoint every second" | Checkpoints have real I/O cost; 10–60s is typical. Use incremental + unaligned if needed. |
| "Windows just work" | They're the hardest part. Know tumbling/sliding/session and late-event policy before you whiteboard. |

---

## 14. Key Takeaways for Interviews

1. **Kafka + Flink (or Kafka Streams)** is the 80% answer for real-time processing in 2026. Spark Structured Streaming if you're already on Spark/Databricks.
2. **Event time + watermarks** — mention these the moment a problem involves "per minute" or "within the last X" metrics.
3. **Exactly-once** requires Kafka transactions **and** idempotent or transactional sinks. Say *how*, not just *that*.
4. **Windows:** tumbling (non-overlapping), sliding (overlapping), session (gap-based). Pick the one that matches the product requirement, not the one that sounds coolest.
5. **Checkpointing** (RocksDB + incremental) is how streaming jobs recover. For huge state, mention **unaligned checkpoints** under backpressure.
6. **Event Sourcing + CQRS** when you need an audit trail, time-travel, or very different read/write shapes.
7. **CDC (Debezium)** is the standard way to propagate DB changes to search indexes, caches, and data lakes without brittle dual-writes.
8. **Kappa > Lambda** for new designs — single codebase, replay the log to reprocess. Use the lakehouse (Iceberg/Hudi/Delta) variant for batch analytics without duplicating logic.
9. **Backpressure** is where real streaming systems actually break. Know the symptoms (growing lag, exploding checkpoint times) and the mitigations (parallelism, async I/O, unaligned checkpoints).
10. Stream-processing lands naturally on these classic HLD problems: **fraud detection, live dashboards, trending now, real-time recommendations, notifications, anomaly/SLO alerting, ETA / surge pricing, CDC to search index.**
