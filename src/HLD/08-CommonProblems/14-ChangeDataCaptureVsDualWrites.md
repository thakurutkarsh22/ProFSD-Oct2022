# Change Data Capture (CDC) vs Dual Writes

> **TL;DR.** When a write needs to land in *two* systems (DB + search index, DB + cache, DB + analytics warehouse, service A's DB + service B's DB), the naïve "write to both from application code" approach — **dual writes** — has no atomicity guarantee and silently loses data on partial failure. The safe alternatives are the **Transactional Outbox pattern** and **log-based Change Data Capture** (Debezium, DynamoDB Streams, Postgres logical decoding). The unifying principle: **single source of truth, propagated via a durable log.**

---

## 1. The dual-write problem

```java
// Naïve dual-write
db.save(order);                      // step A
kafka.publish("orders", order);      // step B
```

What happens if:
- Step A succeeds, step B fails → event never published; downstream consumers never see the order; search index missing the order; analytics data hole.
- Step A fails, step B succeeds → event published for an order that doesn't exist in the DB; consumers see a phantom.
- The process crashes **between** A and B → same as above.

```
   A OK → B OK                    (all good)
   A OK → B failed                (DB has it, Kafka doesn't → ghost in DB)
   A failed → B OK                (Kafka has it, DB doesn't → phantom event)
   A OK → process crash before B  (same as second case)
```

You cannot wrap these in a cross-system transaction unless you use 2PC (complex, expensive, rarely available). So **every dual-write is an incident waiting to happen** — the rate might be 1 in 10 000 writes, but at 10 000 writes/sec that's one incident per second.

---

## 2. Why people still do it

1. "Our code is reliable, failures are rare." (They aren't.)
2. "We'll retry on failure." (Retry of B doesn't compose well with idempotency; and if the process dies, nobody retries.)
3. "We added a try/catch and rollback the DB if Kafka fails." (Now you've built a broken 2PC: DB commit can succeed while your rollback code runs — the DB write has been acknowledged to the client.)
4. "It's just the cache; we'll eventually invalidate." (See [07-CacheConsistency.md](07-CacheConsistency.md) for why "eventual" means "often inconsistent".)

---

## 3. The Transactional Outbox pattern

**Idea:** put both writes in the *same* local transaction by writing to an "outbox" table, and publish to Kafka from that table via a relay.

```
┌────────────────────────────────────────────────────────────────┐
│                    TRANSACTIONAL OUTBOX                         │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│     Application                                                │
│        │                                                       │
│        │ BEGIN TX                                              │
│        │   INSERT into orders (...)                            │
│        │   INSERT into outbox (event_id, topic, payload, ts)   │
│        │ COMMIT                                                │
│        │                                                       │
│        ▼                                                       │
│     Database                                                   │
│     ┌──────────────────────────┐                               │
│     │   orders    │   outbox   │                               │
│     └──────────────────────────┘                               │
│                        │                                       │
│                        ▼                                       │
│            ┌────────────────────────┐                          │
│            │  Outbox Relay          │ polls or tails the log   │
│            │                        │                          │
│            │  reads outbox rows →   │                          │
│            │  publishes to Kafka →  │                          │
│            │  marks row published   │                          │
│            └────────────────────────┘                          │
│                        │                                       │
│                        ▼                                       │
│                     Kafka                                      │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

Guarantees:
- Atomicity: if `orders` is committed, the `outbox` row exists. If the TX rolls back, neither exists.
- Durability: outbox rows survive crash.
- At-least-once: the relay may publish a row twice (crash before marking it published) → **consumers must be idempotent** (see [06-IdempotencyAndDeduplication.md](06-IdempotencyAndDeduplication.md)).

### 3.1 The relay's two flavours

**Polling-based**:
```
loop:
    SELECT * FROM outbox WHERE published = false ORDER BY id LIMIT 1000
    for each row: publish(row); mark published
    sleep 100 ms
```
Simple; latency ≈ polling interval.

**Log-tailing (CDC on the outbox)**:
```
Debezium tails the MySQL binlog / Postgres WAL for INSERTs to `outbox`
                                    → publishes to Kafka in real time
```
Lower latency (ms), more robust under load. Same technique as full CDC, but with the outbox table as the sole source.

### 3.2 Outbox vs dual-write in one table

A clever variant: **no separate outbox**. Use the *primary* table and log-tail it directly (skip the outbox). This is just CDC — see next section.

---

## 4. Change Data Capture (CDC)

CDC = "tail the database's write-ahead log and stream every change as events".

```
┌────────────────────────────────────────────────────────────────┐
│                         CDC PIPELINE                            │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│    Application                                                 │
│        │                                                       │
│        │ INSERT / UPDATE / DELETE                              │
│        ▼                                                       │
│    Database                                                    │
│    ┌────────────────────┐                                      │
│    │ Tables             │                                      │
│    │ ──────────────     │  WAL / binlog (persistent redo log)  │
│    │ ───────────        │ ─────────────────────────────────►   │
│    └────────────────────┘                                      │
│                                                                │
│                     ┌─────────────────────┐                    │
│                     │ CDC tool            │                    │
│                     │ (Debezium, Maxwell, │                    │
│                     │  pg_replication_    │                    │
│                     │  slots)             │                    │
│                     └──────────┬──────────┘                    │
│                                │                               │
│               Kafka topics ────┤                               │
│                                ▼                               │
│           ┌──────────┬──────────┬──────────┐                  │
│           │  Search  │ Cache    │ Analytics│                  │
│           │  Index   │ invalid  │ Warehouse│                  │
│           └──────────┴──────────┴──────────┘                  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 4.1 Why CDC is the strongest option

- **Single source of truth.** The DB is the truth; everything downstream is derived.
- **No application changes.** App just writes normally.
- **Ordering.** The log is per-partition ordered → downstream sees writes in commit order.
- **Replayable.** Bug? Rewind the log and reprocess.
- **Supports heterogeneous sinks.** Same stream feeds search, cache, warehouse, audit.

### 4.2 Underlying mechanisms

| DB | CDC mechanism |
|----|---------------|
| PostgreSQL | Logical decoding + replication slots |
| MySQL / MariaDB | Binary log (`ROW` format) |
| MongoDB | Change streams (oplog) |
| DynamoDB | DynamoDB Streams |
| Cassandra | CDC commitlog (weak; less-adopted) |
| SQL Server | Transactional replication / CDC feature |

### 4.3 Operational concerns

- **Replication slot bloat (Postgres).** If the CDC consumer stops consuming, the WAL can't be truncated → disk fills up. Monitor slot lag; page on it.
- **Schema changes.** If a column is added/dropped, Debezium needs to handle the schema transition (Avro schema registry is your friend).
- **At-least-once delivery.** Downstream must dedup by primary key + LSN / offset.
- **Large transactions.** A single TX with 1M updates becomes 1M events in a burst; size your downstream.
- **Bootstrapping.** When you first start, you need a snapshot of the table *plus* the log tail. Debezium does both; plan for it to hit the DB hard during bootstrap.

### 4.4 Event shape

Debezium-style event:
```json
{
  "before": { "id": 17, "status": "PENDING" },
  "after":  { "id": 17, "status": "PAID"    },
  "source": { "db": "orders", "table": "orders", "lsn": 12345 },
  "op":     "u",
  "ts_ms":  1715187600000
}
```

Downstream consumers:
- Cache invalidator: `cache.delete(key=after.id)`.
- Search indexer: `index.upsert(after)` or `index.delete(before.id)` if op = 'd'.
- Warehouse loader: append to a dimensional table, dedup by `(table, id, lsn)`.

---

## 5. Outbox vs CDC — when to use which

| Need | Pick |
|------|------|
| You own the app, can add a table | Outbox (clean, explicit event schema) |
| You cannot change the app | CDC (invisible to app) |
| Legacy system with no event emission | CDC |
| Events are business-level semantics ("OrderPlaced") | Outbox (curated shape) |
| Events are raw row changes | CDC |
| Multiple services need the same stream | CDC (one pipeline, many consumers) |
| You want explicit schema ownership | Outbox (app defines the event shape) |

Many mature systems do **both**:
- Outbox for "business events" that need curated shapes.
- CDC for replication / warehouse sync.

---

## 6. What CDC does *not* solve

- **Two-phase commit between your DB and another system.** It gives you propagation, not cross-DB atomicity. If a downstream write must happen *or* the local write must be rolled back, you're back to Saga patterns (see [../03-AdvancedConcepts/03-DistributedTransactions.md](../03-AdvancedConcepts/03-DistributedTransactions.md)).
- **Schema coupling.** Consumers still need to know the schema; bad schema changes still break them. Use Avro + Schema Registry + compatibility rules.
- **Ordering across unrelated keys.** Per-key (or per-partition) order is preserved; global total order across all keys is not.
- **Read-your-own-writes for downstream.** A user who just wrote to DB may query the downstream before CDC has propagated (typically 100 ms - 1 s).

---

## 7. The unifying principle

```
┌────────────────────────────────────────────────────────────┐
│     "Truth lives in ONE log. Everything else is           │
│      a projection built by tailing that log."             │
└────────────────────────────────────────────────────────────┘
```

This is the **log-centric architecture** Jay Kreps popularised ("The Log" article, LinkedIn). It changes how you think about "where is the truth":

- DB? Search index? Cache? Warehouse? **Only one** is the source of truth. The others are materialised views.
- Need a new view of the data? Subscribe a new consumer to the log, process from the beginning.
- Found a bug? Fix the consumer, reset its offset to the start, let it rebuild.

Kafka as the central log, services as producers/consumers — this is *Kappa architecture* at its cleanest.

---

## 8. Common failure modes

| Failure | Cause | Fix |
|---------|-------|-----|
| Downstream sees a write the DB doesn't have | Dual-write: Kafka succeeded, DB rolled back | Outbox or CDC |
| Downstream misses a write | Dual-write: DB succeeded, Kafka failed | Outbox or CDC |
| Lost update after replaying the log | Non-idempotent consumer | Dedup by PK + LSN |
| Out-of-order updates in downstream | Consumer reorders or parallelizes | Key-partitioned parallelism (same key → same partition) |
| Consumer falls far behind | Slow consumer; burst of writes | Scale out; more partitions; tune batching |
| WAL fills disk | Unattended replication slot | Monitor slot lag; disable slots you're not using |
| Schema drift breaks consumers | DB column dropped, consumers crash | Avro + compatibility mode BACKWARD; test in staging |
| Events lost on bootstrap window | Start tailing the log *before* taking the snapshot, then replay events since snapshot LSN | Debezium does this; if hand-rolling, copy carefully |

---

## 9. Anti-patterns

| Anti-pattern | Why |
|--------------|-----|
| Dual-writes with try/catch + rollback | Broken 2PC. Failure modes still exist; just rarer and weirder |
| Event produced from a DB trigger writing to a queue table read by cron | A home-rolled outbox + relay with poor monitoring; usually invented because the team didn't know about outbox |
| "Eventual consistency" as a blanket excuse for lost updates | Consistency should be *bounded*; know the lag; alert on it |
| CDC directly into operational DB (without staging topic) | Failure propagation; coupling. Always land into Kafka first |
| Skipping dedup in consumers | CDC is at-least-once; duplicates *will* arrive |

---

## 10. Interview talking points

- **Call out the dual-write bug.** "Writing to the DB and publishing to Kafka from app code has no atomicity — partial failures cause data drift." Say this out loud.
- **Prescribe outbox for new systems, CDC for legacy.** Demonstrates judgement.
- **Name the tool.** Debezium for RDBMS, DynamoDB Streams for DDB, native change streams for MongoDB. Concrete > generic.
- **Mention at-least-once delivery + idempotency on consumers.**
- **Cite the "log at the centre" principle.** "The log is the truth; DBs / caches / indices are materialised views."
- **Schema evolution.** "Avro + schema registry + BACKWARD-compat rules."
- **Monitor the lag.** Replication slot lag, CDC consumer offset lag — failures here are silent by default.

---

## 11. Related reading

- [../03-AdvancedConcepts/03-DistributedTransactions.md](../03-AdvancedConcepts/03-DistributedTransactions.md) — Saga orchestration, outbox in context.
- [../Components/01-Kafka.md](../Components/01-Kafka.md) — Kafka as the central log.
- [07-CacheConsistency.md](07-CacheConsistency.md) — CDC-driven cache invalidation.
- [11-ZeroDowntimeSchemaMigration.md](11-ZeroDowntimeSchemaMigration.md) — use CDC as the backfill mechanism.
- [13-MultiRegionFailover.md](13-MultiRegionFailover.md) — CDC across regions.
- [06-IdempotencyAndDeduplication.md](06-IdempotencyAndDeduplication.md) — at-least-once delivery from CDC must be deduped.
