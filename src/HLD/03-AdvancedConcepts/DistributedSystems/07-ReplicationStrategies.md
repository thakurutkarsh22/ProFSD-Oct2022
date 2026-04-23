# Replication Strategies

> **Difficulty:** Medium-Hard | **Time:** 3 hours | **Priority:** Must Know

**Replication** = keeping the same data on more than one machine.
Everything downstream — fault tolerance, read scaling, geo-distribution,
fast failover — depends on which **replication topology** you pick and
how **synchronously** replicas stay in step. This document walks through
every real-world pattern: single-leader, multi-leader, leaderless, chain,
and the sync-vs-async spectrum.

---

## Table of Contents

1. [Why Replicate at All](#1-why-replicate-at-all)
2. [Single-Leader Replication](#2-single-leader-replication)
3. [Multi-Leader Replication](#3-multi-leader-replication)
4. [Leaderless Replication (Dynamo-style)](#4-leaderless-replication-dynamo-style)
5. [Chain Replication](#5-chain-replication)
6. [Sync vs Async vs Semi-Sync](#6-sync-vs-async-vs-semi-sync)
7. [Logical vs Physical Replication](#7-logical-vs-physical-replication)
8. [Replication Lag & Its Anomalies](#8-replication-lag--its-anomalies)
9. [Failover: Promoting a New Leader](#9-failover-promoting-a-new-leader)
10. [Geo-Replication Patterns](#10-geo-replication-patterns)
11. [Picking the Right Strategy](#11-picking-the-right-strategy)
12. [Interview Q&A](#12-interview-qa)

---

## 1. Why Replicate at All

```
 GOAL                        HOW REPLICATION HELPS
 ────                        ─────────────────────
 Fault tolerance             N-1 replicas can fail; data survives
 Read scaling                reads can fan out to followers
 Geo-proximity               users read from their local region
 Zero-downtime upgrade       rolling restart, one replica at a time
 Analytics isolation         OLAP replica doesn't hurt OLTP leader
 DR (disaster recovery)      async replica in a distant region
```

Replication is ALWAYS a trade-off between **latency, durability,
availability, and consistency**. Every strategy below picks
a different Pareto point.

---

## 2. Single-Leader Replication

One node (the "leader", "primary", or "master") accepts all writes.
Others ("followers", "replicas", "secondaries") passively apply them.

### 2.1 Architecture

```
              ┌────────┐
              │ Client │
              └──┬─────┘
                 │ write
                 ▼
           ┌──────────┐        ┌──────────┐
           │  LEADER  │ ─────► │ Follower │
           │(writes)  │ log    └──────────┘
           └────┬─────┘
                │
                │          ┌──────────┐
                └────────► │ Follower │
                   log     └──────────┘

  Reads:  from leader (strong)  OR  from any follower (may be stale)
  Writes: ONLY to leader
```

### 2.2 Flow of a single write

```
  1. Client → Leader:         PUT(x=5)
  2. Leader appends to WAL    (sequence_number = 1234)
  3. Leader fsyncs WAL        (durable on disk)
  4. Leader streams entry to followers
  5. Each follower appends & applies
  6. Leader acks client (immediate for async, post-ack for sync)
```

### 2.3 Strengths

```
  ✓ Simple mental model — one place decides
  ✓ Strong consistency trivially (read from leader)
  ✓ No conflict resolution needed (only one writer)
  ✓ Easy to reason about ordering (single log)
```

### 2.4 Weaknesses

```
  ✗ Leader is a hot spot (write throughput bottleneck)
  ✗ Geographically distant clients suffer cross-region write latency
  ✗ Leader failure requires failover (temporary unavailability)
  ✗ Reads from followers may be stale (replication lag)
```

### 2.5 Examples

```
  MySQL                   asynchronous binlog to replicas (default)
  PostgreSQL              streaming WAL replication
  MongoDB (replica set)   primary + secondaries
  Redis                   master + replicas (async by default)
  Kafka                   leader-per-partition model
  etcd / Consul           Raft leader — similar idea, but with consensus
```

---

## 3. Multi-Leader Replication

Two or more leaders each accept writes. They asynchronously replicate
to each other.

### 3.1 Architecture

```
   Region US-EAST                      Region EU-WEST
  ┌──────────────────┐                ┌──────────────────┐
  │                  │                │                  │
  │  Clients write ─►│                │  Clients write ─►│
  │                  │                │                  │
  │    Leader_US ◄───┼────── async ───┼──► Leader_EU     │
  │       │          │    cross-region│       │          │
  │       ▼          │                │       ▼          │
  │  Followers_US    │                │   Followers_EU   │
  └──────────────────┘                └──────────────────┘
```

### 3.2 Where it shines

```
  - Each region has LOCAL low-latency writes.
  - Survives single-region outage (other continues).
  - Load is distributed across leaders.
```

### 3.3 The hard problem: write conflicts

```
  At 12:00:00.000:
    Leader_US accepts:   SET name = "Alice"
    Leader_EU accepts:   SET name = "Alicia"      ← same key!

  These are CONCURRENT (neither saw the other).
  When they sync:
    - Pick a winner via LWW (timestamp tiebreak)?
    - Show both (siblings)?
    - Merge (CRDT)?

  See 09-ConflictResolution.md for techniques.
```

### 3.4 Topology choices

```
  CIRCULAR / RING
      A ──► B ──► C ──► A
      Simple, but any broken link splits the ring.

  STAR (one hub)
      A ──┐     ┌── C
          ├─ H ─┤
      B ──┘     └── D
      Hub is a SPOF.

  ALL-TO-ALL (full mesh)
      Every leader sends to every other leader.
      Tolerates link failures; every leader sees every write directly.
      Most production systems do this.
```

### 3.5 Real systems

```
  CouchDB / PouchDB         multi-master for offline-first apps
  CockroachDB               multi-region, consensus per shard
  BDR (Postgres-BDR)        active-active Postgres
  Active-Active MySQL       with Percona XtraDB Cluster (Galera)
  Google Docs               client-app level multi-leader (CRDT-based)
```

### 3.6 When to avoid it

```
  ✗ If conflicts cost money (overselling inventory) — use single-leader.
  ✗ If your data has unique constraints — merging usernames is a nightmare.
  ✗ If ops team hasn't encountered "phantom rows appearing after reconciliation"
```

---

## 4. Leaderless Replication (Dynamo-style)

**No leader.** Any replica can accept reads and writes. Clients or a
coordinator send the op to multiple replicas directly.

### 4.1 Architecture

```
                      ┌───────────────┐
                      │    CLIENT     │
                      └───────┬───────┘
                              │ write to ANY W replicas
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
        ┌────────┐      ┌────────┐      ┌────────┐
        │  R1    │      │  R2    │      │  R3    │
        └────────┘      └────────┘      └────────┘
              ▲               ▲               ▲
              │               │               │
              └───────────────┼───────────────┘
                              │ read from ANY R replicas
                      ┌───────┴───────┐
                      │    CLIENT     │
                      └───────────────┘

  Quorum math:  W + R > N  →  strong consistency
```

### 4.2 Flow

```
  WRITE(key=K, value=V):
    1. Coordinator selects N replicas (e.g., via consistent hashing).
    2. Sends write to all N.
    3. Waits for W acks.
    4. Reports success.

  READ(key=K):
    1. Coordinator asks N replicas (or at least R).
    2. Waits for R responses.
    3. Picks freshest (via HLC timestamp or vector clock).
    4. Optionally triggers READ-REPAIR on stale replicas.
```

### 4.3 Strengths / weaknesses

```
  ✓ No leader → no failover downtime
  ✓ High write availability; any W reachable is enough
  ✓ Tunable per operation (CL=ONE for fast, ALL for safe)
  ✗ Conflicts possible (concurrent writes to same key on different nodes)
  ✗ More complex reconciliation (siblings, vector clocks)
  ✗ Read latency tied to slowest of R replicas
```

### 4.4 Examples

```
  Apache Cassandra          tunable consistency, gossip membership
  Amazon DynamoDB           managed, AP default, CP option
  Riak                      original Dynamo clone, now less popular
  ScyllaDB                  Cassandra-compatible, C++ reimplementation
  Voldemort                 LinkedIn's early key-value store
```

---

## 5. Chain Replication

All replicas are arranged in a **linear chain**. Writes go to the head;
reads go to the tail. Popularized by the **"Chain Replication for
Supporting High Throughput and Availability"** paper (van Renesse &
Schneider, 2004).

### 5.1 Architecture

```
                write                           read
   Client ─────────────►           ◄───────────── Client
                         │
                         ▼
                   ┌────────┐
                   │  HEAD  │
                   │  (R1)  │ ─────► WAL + update
                   └────┬───┘
                        │  forward
                        ▼
                   ┌────────┐
                   │ MIDDLE │
                   │  (R2)  │ ─────► WAL + update
                   └────┬───┘
                        │  forward
                        ▼
                   ┌────────┐
                   │  TAIL  │
                   │  (R3)  │ ─────► WAL + update
                   └────────┘

  Write: only ACK to client from TAIL, after every node has applied.
         → strong consistency.
  Read:  only from TAIL → sees only committed writes.
         → linearizable.
```

### 5.2 Strengths

```
  ✓ Linearizable reads and writes.
  ✓ Simple reasoning: "tail is source of truth."
  ✓ Recovery is just reconstructing the chain.
  ✓ Can have many read-only tails (CRAQ variant).
```

### 5.3 Weaknesses

```
  ✗ Writes pay O(N) latency (full chain traversal).
  ✗ Head + tail are fixed SPoFs until re-chained on failure.
  ✗ Complex re-chaining after any failure.
```

### 5.4 CRAQ (Chain Replication with Apportioned Queries)

Improvement: reads can come from ANY node. Each node knows whether it
has the "clean" latest value (all subsequent nodes have it) or a
"dirty" pending value.

```
  Clean read ← any node returns immediately
  Dirty read ← forward to TAIL to get committed value

  Throughput scales with replicas while keeping linearizability.
```

### 5.5 Where used

```
  Azure Storage        chain replication variant for blob storage
  FAWN-KV              academic
  Apache BookKeeper    per-ledger chain-like quorum writes
  Some in-house systems at Netflix, HFT firms
```

---

## 6. Sync vs Async vs Semi-Sync

### 6.1 Asynchronous replication

```
   Client ── write ──► Leader ── ack ──► Client     (immediate)
                         │
                         │ (later, async)
                         ▼
                     Followers

  Latency:      1 RTT to leader
  Data loss:    up to (leader buffer) on leader crash
  Throughput:   maximum
  Availability: maximum
  Consistency:  weakest (followers lag)
```

### 6.2 Synchronous replication

```
   Client ─ write ─► Leader ─ replicate ─► Followers
                                 │
                                 ▼
                            ALL acks
                                 │
                                 ▼
                     Leader ── ack ──► Client

  Latency:      1 RTT + fsync on all followers
  Data loss:    none if at least one replica survives
  Throughput:   limited by slowest follower
  Availability: write fails if ANY follower is down
  Consistency:  strongest
```

### 6.3 Semi-synchronous replication (the sweet spot)

```
   Client ── write ──► Leader ── replicate ──► Followers
                                  │
                                  ▼
                         ACK from AT LEAST ONE follower
                                  │
                                  ▼
                           Leader acks client

  Latency:      1 RTT + fastest follower's fsync
  Data loss:    none if leader + that one follower didn't both die
  Throughput:   high (only wait for fastest follower)
  Availability: works as long as 1 follower is up
  Consistency:  near-strong (one other copy has the write)
```

### 6.4 Side-by-side

```
  ┌────────────┬────────────┬─────────────┬──────────────┬────────────┐
  │ Mode       │ Write  RTT │ Durability  │ Availability │ Examples   │
  ├────────────┼────────────┼─────────────┼──────────────┼────────────┤
  │ Async      │ 1          │ Low         │ High         │ MySQL def. │
  │ Semi-sync  │ 1-2        │ Medium-High │ High         │ MySQL enh. │
  │ Sync (all) │ 1-N        │ Highest     │ Lowest       │ Rare       │
  │ Sync (quo) │ 1-2        │ High        │ High         │ PG, etcd   │
  └────────────┴────────────┴─────────────┴──────────────┴────────────┘
```

### 6.5 Postgres knobs as a concrete example

```
  synchronous_commit = off             fast, async, may lose last txns
  synchronous_commit = local           wait for local WAL fsync only
  synchronous_commit = remote_write    wait until 1 replica has written
  synchronous_commit = remote_apply    wait until 1 replica has APPLIED
                                       (strong read-after-write on replica)
  synchronous_commit = on              equivalent to remote_write with
                                       synchronous_standby_names set
```

---

## 7. Logical vs Physical Replication

### 7.1 Physical (byte-level / WAL)

```
  Leader ships WAL bytes/pages to followers.
  Followers must be BYTE-IDENTICAL (same PG/MySQL version, same schema).

  + Fast (no re-parsing)
  + Exact state duplicated
  − Followers can't be upgraded independently
  − Can't replicate subset of data or cross-engine
```

**Used by:** Postgres streaming, MySQL binlog ROW format (roughly), MongoDB oplog.

### 7.2 Logical (statement / row-level events)

```
  Leader ships high-level events: "row inserted in table X with values Y"
  Followers parse + apply.

  + Version independence
  + Schema-level filtering, cross-engine CDC
  + Powers heterogeneous replication (Postgres → Kafka → Elasticsearch)
  − Some operations are non-deterministic (NOW(), RAND())
     → statement-based form may diverge; row-based is preferred
  − Slightly slower
```

**Used by:** Debezium (CDC), MySQL row-based binlog, Postgres logical
replication (pglogical, pg_logical), Kafka Connect.

### 7.3 Hybrid: MySQL binlog formats

```
  STATEMENT    ships the SQL statement verbatim (issues with NOW(), etc.)
  ROW          ships before/after row images (safer, larger)
  MIXED        MySQL chooses per-statement (default)
```

---

## 8. Replication Lag & Its Anomalies

Async replication means followers are always **behind** the leader.

### 8.1 Metrics to watch

```
  REPLICATION LAG (seconds)        how far behind a follower is
  APPLY LAG                        bytes in WAL not yet applied
  BYTES-BEHIND                     Kafka lag metric
```

### 8.2 The four session anomalies (see `02-ConsistencyModels.md`)

```
  READ-YOUR-WRITES VIOLATION
  User posts → immediately refreshes → doesn't see own post.

  MONOTONIC-READS VIOLATION
  User sees "20 likes" then "15 likes" on refresh
  (second read hit more-lagged replica).

  CAUSAL VIOLATION
  Alice says "I lost my phone", then "found it".
  Bob sees only the second one → confusion.

  MONOTONIC-WRITES VIOLATION
  Two quick edits → older value ends up final.
```

### 8.3 Mitigation patterns

```
  STICKY SESSIONS
    hash(user_id) → always pick the same replica for that user

  VERSION TOKENS
    Client carries last-seen version/HLC; replica refuses/waits if behind

  READ-FROM-LEADER FOR CRITICAL PATHS
    Profile-edit reads come from leader; feed reads from any replica

  REPLICATION LAG ALERTING
    Auto-remove replicas that exceed threshold from load balancer
```

---

## 9. Failover: Promoting a New Leader

### 9.1 The steps

```
  1. Detect leader failure (heartbeat / gossip / Phi accrual)
  2. Choose new leader (fastest up-to-date replica, or via consensus)
  3. Reconfigure clients / DNS / proxy to point at new leader
  4. Old leader, when back, becomes follower (must sync from new leader)
```

### 9.2 Classic failover bugs

```
  LOST WRITES
     Old leader had committed writes to itself that weren't on
     followers → lost when new leader is picked. Fix: require
     SYNC or quorum replication (W ≥ 2).

  SPLIT-BRAIN
     Detector was wrong; old leader comes back, still thinks
     it's leader. Fix: FENCING TOKENS.

  MONOTONICITY VIOLATIONS
     New leader has less up-to-date data than another replica
     (if pick was arbitrary). Fix: pick the replica with highest
     log index / tie-break via ID.

  GHOST AUTO-INCREMENT IDs
     Old leader handed out ID 10001; followers had up to 10000;
     after failover, new leader re-uses 10001. Fix: always
     persist sequence state before use.
```

### 9.3 Raft failover is (almost) free

Raft bakes failover into the protocol:

```
  - Election timeout on followers
  - Candidate requests vote from majority
  - Must have at least as up-to-date log as any voter
  - Majority vote → new leader, bumped term
  - All followers accept it, old leader is rejected (lower term)

  Recovery time: 150-300 ms typical (the randomized election timeout).
```

See `../02-Consensus.md` for details.

---

## 10. Geo-Replication Patterns

Cross-region replication is the hardest because **latency is
unavoidable** (60-300 ms RTT).

### 10.1 Active-passive (primary + DR)

```
   Region A (primary)                 Region B (DR)
      ┌─── writes + reads             ┌─── dormant
      │        │                      │
      │        │ async replication    │
      └───► Leader ─────────────────► Follower
                                         │
                                         └── promoted on DR event
```

**Pros:** Simple. Strong consistency (all writes to one place).
**Cons:** Cross-region write latency for users near B. Minutes of
data loss possible on primary failure (async lag).

### 10.2 Active-active (multi-leader, per §3)

```
  Each region serves its own users locally. Sync cross-region.
  High availability, but conflict-resolution baked in.
```

### 10.3 Regional shards + consensus (Spanner / CockroachDB)

```
  Partition data by "region affinity":
     Rows with region=US live on US replicas (leader in US).
     Rows with region=EU live on EU replicas (leader in EU).
  Each row's writes are cheap (local quorum).
  Cross-region reads hit the home region of that row.
```

### 10.4 "Follower reads" for stale-OK analytics

```
  Analytics queries read from the local-region follower even if
  it lags a few seconds behind. Massive latency win; acceptable for
  dashboards / OLAP.
```

---

## 11. Picking the Right Strategy

```
┌────────────────────────────────────────────────────────────────┐
│                  REPLICATION DECISION TREE                      │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  SINGLE REGION?                                                │
│     Need strong consistency?  ─► Single-leader (sync/semi-sync)│
│     OK with eventual?         ─► Single-leader (async)         │
│     Need write scale/HA?      ─► Leaderless + quorum           │
│                                                                │
│  MULTI REGION?                                                 │
│     Need local write latency? ─► Multi-leader + conflict res.  │
│     OR region-sharded consensus (Spanner/CockroachDB)          │
│     Can accept cross-region writes? ─► Single-leader + DR      │
│                                                                │
│  WRITE-HEAVY, MANY DCs?                                        │
│     ─► Leaderless (Cassandra) with LOCAL_QUORUM                │
│                                                                │
│  STRICT ORDERING + LINEARIZABLE?                               │
│     ─► Single-leader OR Chain replication                      │
│                                                                │
│  UNBOUNDED READ SCALE ON ONE DATASET?                          │
│     ─► Leader + many learner replicas                          │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 11.1 Summary cheat-sheet

```
┌────────────────┬───────────┬──────────┬─────────────┬─────────────┐
│ Strategy       │ Writes    │ Reads    │ Consistency │ Conflict    │
├────────────────┼───────────┼──────────┼─────────────┼─────────────┤
│ Single-Leader  │ 1 node    │ Any node │ Strong(sync)│ Impossible  │
│ Multi-Leader   │ N leaders │ Any node │ Eventual    │ Possible    │
│ Leaderless     │ Any W     │ Any R    │ Tunable     │ Possible    │
│ Chain          │ Head only │ Tail     │ Linearizable│ Impossible  │
│ Consensus      │ Leader    │ Any      │ Strong+HA   │ Impossible  │
└────────────────┴───────────┴──────────┴─────────────┴─────────────┘
```

---

## 12. Interview Q&A

### Q1. "When do you pick single-leader vs leaderless?"

> Single-leader when **strong consistency** and simple reasoning matter
> more than tolerating leader failures (small downtime is OK). Leaderless
> when availability matters more than immediate consistency and you can
> afford to handle concurrent-write conflicts (LWW, siblings, CRDTs).
> Single-leader is the default for OLTP; leaderless for high-write,
> globally distributed workloads (metrics, sessions, social feeds).

### Q2. "What's semi-synchronous replication and why use it?"

> Leader waits for **at least one** replica to acknowledge before acking
> the client. Gives you near-strong durability (one extra copy) without
> paying for waiting on all replicas. Used by MySQL (default since 5.7),
> Postgres (synchronous_standby_names), and virtually any modern
> single-leader DB.

### Q3. "Replication lag keeps growing. How do you debug?"

> Look at: (1) leader's WAL write rate — overloaded? (2) network
> throughput between leader and replica — saturated? (3) replica's
> apply rate — is there lock contention or a slow query blocking it?
> (4) replica's disk I/O. Mitigations: throttle writes on leader,
> add more replicas so traffic distributes, use logical replication
> to parallelize apply.

### Q4. "How do you implement read-your-writes in an async-replica system?"

> Client tracks its last-write timestamp/HLC. On reads it sends
> "please wait until version ≥ T" (version token). Replica either
> waits for catch-up or rejects, forcing the client to retry on
> another/leader. Alternatively: **sticky replica** per user (hash
> user_id → replica), or **read from leader** for that user's own data.

### Q5. "Why is multi-leader replication often discouraged?"

> Because of **write conflicts**. Two users in different regions can
> concurrently update the same record; resolution is application-level
> and always requires trade-offs (data loss with LWW, complexity with
> siblings, limited data-types with CRDTs). Also: unique constraints
> are effectively impossible without coordination.

### Q6. "Explain chain replication briefly."

> Replicas are linearly chained: writes go to the head, propagate
> to tail, and only ack after everyone applied. Reads come from
> tail, which always has committed data. Gives linearizability
> with simple recovery semantics. Used in Azure Storage and
> BookKeeper; the CRAQ variant allows reads from any replica.

### Q7. "What's the recovery time for a leader failure in Raft vs async single-leader DB?"

> **Raft**: 150-300 ms — randomized election timeout → new leader →
> resumes. No data loss (quorum of followers has the committed log).
> **Async DB** (e.g., MySQL with manual promotion): tens of seconds
> to minutes, and you may lose the last seconds of async-replicated
> writes. Tools like **Orchestrator**, **Patroni**, or **RepMgr**
> automate most of this but still expose a window.

### Q8. "Logical vs physical replication — which do you use for cross-version upgrades?"

> **Logical.** Physical replication requires byte-identical binaries,
> so upgrades need a full re-snapshot. Logical replication (pglogical,
> Debezium, MySQL row-based binlog) lets you run the old and new
> version side-by-side, stream changes, cut over, and decommission the
> old — zero downtime upgrades.

---

## 13. Further Reading

- Kleppmann, "Designing Data-Intensive Applications" Ch. 5 — replication
- van Renesse & Schneider, "Chain Replication" (2004)
- Terrace & Freedman, "Object Storage on CRAQ" (2009)
- Shapiro et al., "Conflict-free Replicated Data Types" (2011) — CRDTs
- DeCandia et al., "Dynamo" (2007) — leaderless patterns
- Oracle MySQL docs — semi-sync replication internals
- Postgres docs — logical replication, synchronous_commit modes

---

> **Previous:** [06-Quorum.md](./06-Quorum.md) ·
> **Next:** [08-AntiEntropy.md](./08-AntiEntropy.md)
