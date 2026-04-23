# State Machine Replication & the Write-Ahead Log

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Must Know

**State Machine Replication (SMR)** is the single most foundational
pattern in distributed systems: *apply the same deterministic operations
in the same order on every replica; they end up in the same state.*
Every consensus-backed DB, queue, and coordination service is built on
this idea. The **Write-Ahead Log (WAL)** is the canonical
implementation: every change is recorded as a log entry before it's
applied.

---

## Table of Contents

1. [The Core Insight](#1-the-core-insight)
2. [The Replicated Log](#2-the-replicated-log)
3. [Write-Ahead Log (WAL)](#3-write-ahead-log-wal)
4. [Determinism: the Non-Negotiable Requirement](#4-determinism-the-non-negotiable-requirement)
5. [Snapshots & Log Compaction](#5-snapshots--log-compaction)
6. [Recovery from a Crash](#6-recovery-from-a-crash)
7. [Raft's Log vs Kafka's Log — Same Idea, Different Goals](#7-rafts-log-vs-kafkas-log--same-idea-different-goals)
8. [Group Commit & Batching](#8-group-commit--batching)
9. [Checkpointing & Truncation](#9-checkpointing--truncation)
10. [Real-World Systems](#10-real-world-systems)
11. [Interview Q&A](#11-interview-qa)

---

## 1. The Core Insight

```
  IF every replica is a DETERMINISTIC state machine
  AND every replica applies the SAME operations
  IN the SAME order
  THEN every replica ends up in the SAME STATE.
```

This reduces "replicate state" → "replicate the ORDERED LIST of operations."
Consensus protocols (Paxos, Raft) solve **one** problem:
*agree on the log.*

### 1.1 Picture

```
           ┌─────── REPLICATED LOG (ordered, append-only) ───────┐
           │ [0] set x=1                                          │
           │ [1] set y=2                                          │
           │ [2] incr x                                           │
           │ [3] set y=5                                          │
           │ [4] del x                                            │
           └─────────────────────────────────────────────────────┘
                │                │                │
                ▼                ▼                ▼
           ┌────────┐       ┌────────┐       ┌────────┐
           │ SM_A   │       │ SM_B   │       │ SM_C   │
           │ (empty │       │ (empty │       │ (empty │
           │  init  │       │  init  │       │  init  │
           │  state)│       │  state)│       │  state)│
           └────────┘       └────────┘       └────────┘
               │                │                │
            apply [0..4]    apply [0..4]      apply [0..4]
               │                │                │
               ▼                ▼                ▼
           x=_, y=5        x=_, y=5          x=_, y=5
```

All three converge **provably**. That's SMR.

### 1.2 Why this is so powerful

```
  "Replicate a database" is an intimidating problem.
  "Order a list of commands" is a solved problem (Raft / Paxos).

  Pick any deterministic program — key-value store, SQL engine,
  counter, message queue — wrap it behind a replicated log, and
  you have a fault-tolerant distributed version of it.
```

---

## 2. The Replicated Log

At its core, every SMR system has a **log** that is:

```
  ORDERED         each entry has a monotonically increasing index
  APPEND-ONLY     never updated in place
  DURABLE         fsynced to disk before ACK
  REPLICATED      copies on ≥ majority of nodes
  CONSISTENT      once committed, no replica will disagree
```

### 2.1 Life of a log entry (Raft)

```
  1. LEADER receives client command.
  2. LEADER appends entry @ idx=N to its local log.
  3. LEADER fsyncs WAL.
  4. LEADER replicates to followers (AppendEntries RPC).
  5. FOLLOWERS append + fsync + respond.
  6. Once MAJORITY has entry @ idx=N → leader marks "committed".
  7. LEADER applies to state machine, replies to client.
  8. LEADER tells followers the commit index; they apply too.
```

### 2.2 Commit vs Apply

```
  COMMITTED    the entry is SAFE — any future leader will have it.
  APPLIED      the entry has actually been run against the state machine.

  commit_idx is set once majority has it.
  last_applied catches up to commit_idx over time.
```

---

## 3. Write-Ahead Log (WAL)

A **Write-Ahead Log** is the disk-level realization of SMR:

```
  Every mutation is FIRST appended to a durable log,
  THEN applied to the in-memory state.
```

### 3.1 Flow

```
  client ──put(x,5)──► Server

                            ┌──── 1. append to WAL ────┐
                            │       "put x=5 @ seq=42" │
                            │                          │
                            │    2. fsync (durable)    │
                            │                          │
                            │    3. ack client         │
                            │                          │
                            │    4. apply to memory    │
                            │       state.x = 5        │
                            │                          │
                            └──────────────────────────┘

  If server crashes between 3 and 4:
    On restart: REPLAY WAL from last checkpoint.
    State is rebuilt. No data loss.
```

### 3.2 Why WAL over "flush state directly"?

```
  Alternative: update in-memory state, then checkpoint to disk.
     Problem 1: if crash before checkpoint, all recent changes lost.
     Problem 2: random-write I/O is slow (seeks, page rewrites).
     Problem 3: can't recover if checkpoint is corrupted.

  WAL wins because:
     - Sequential writes (fastest disk pattern)
     - Durability before ack → no data loss
     - Recovery is just "replay the log"
     - Log can double as REPLICATION STREAM (binlog, CDC)
     - Checkpoint is now just an optimization, not a durability mechanism
```

### 3.3 What fsync actually does

```
  write(fd, data)        puts bytes in kernel page cache
  fsync(fd)              forces kernel + disk controller to flush
                         data to physical media, then waits for
                         media-side confirmation

  Without fsync, a power loss can lose bytes even after write().
  Every serious DB calls fsync on its WAL before acking. That
  fsync is usually the dominant cost of a write.
```

### 3.4 Group commit

```
  fsync is expensive (ms per call on spinning disks, µs on NVMe).
  To amortize: GROUP-COMMIT multiple log appends with one fsync.

    t=0      100 writes arrive, go into in-memory WAL buffer
    t=1ms    single fsync of the whole buffer
    t=2ms    100 ACKs to 100 clients

  Throughput: 100× over per-write fsync.
  Latency   : +1 ms average (worst-case individual writer).
```

---

## 4. Determinism: the Non-Negotiable Requirement

SMR requires replicas to produce identical states from identical inputs.
**Non-determinism breaks everything.**

### 4.1 Common sources of non-determinism

```
  CLOCK          now()  differs per node
  RANDOMNESS     rand() produces different sequences
  HASH ORDER     Python dict / Go map iteration is non-deterministic
  FLOATING POINT slightly different across CPUs in some edge cases
  UNORDERED SET  iteration order not guaranteed
  SYSTEM STATE   reading /proc, env vars, disk size
  NETWORK CALLS  external side effects during apply
```

### 4.2 Mitigations

```
  SERIALIZE THE INPUT, NOT THE OUTPUT
    Include "timestamp = T" and "nonce = R" in the log entry.
    Every replica applies the same T and R → same result.

  NO NETWORK CALLS IN APPLY
    If you need an external result, call ONCE on the leader,
    append the RESULT to the log as an extra entry.

  DETERMINISTIC DATA STRUCTURES
    Sorted maps, explicit iteration order.

  SANDBOX USER CODE
    If users run stored procedures, restrict to a deterministic subset
    (e.g., Lua + no clock).
```

### 4.3 Example: Raft's handling of timestamp

```
  Client request:  "INSERT record with current timestamp"
  WRONG: each replica writes its own current timestamp → divergence

  Raft-style FIX:  leader stamps the request (ts = now()) before
                   appending to log, so the TIMESTAMP is in the log.
                   Followers apply with THAT ts. Deterministic.
```

---

## 5. Snapshots & Log Compaction

Left unchecked, the log grows forever. Snapshots + compaction keep it bounded.

### 5.1 Snapshotting

```
  Every N entries (or every T seconds):
     1. Capture the current state machine state (serialize to disk).
     2. Record that it corresponds to log index N.
     3. (Optionally) delete log entries < N.

  On recovery:
     Load snapshot @ index S.
     Replay log entries (S, last_applied].
```

### 5.2 Visual

```
     Time ─────────────────────────────────────────────►

     Log: ┃──────────── old ────────────┃━━━━ live ━━━━┃
          0         500      ...      1000          current

     Snapshot at idx=1000 → state saved on disk.
     Log entries 0..999 can be deleted (compacted away).

     If a new replica joins:
        1. Ship snapshot.
        2. Ship log from idx 1000 onwards.
        3. Replica is caught up.
```

### 5.3 Raft's InstallSnapshot RPC

```
  If a follower is too far behind (leader has already compacted past
  the follower's next index), the leader instead ships a full
  SNAPSHOT via InstallSnapshot RPC.

     Follower replaces state with the snapshot.
     Then catches up on the tail of the log.
```

### 5.4 Copy-on-write snapshots

```
  Problem: dumping the whole in-memory state while accepting writes
  → either blocks writes, or snapshot is inconsistent.

  Solution: fork() the process (Linux COW) → child writes the snapshot
             out to disk while parent keeps serving. Redis does this.
             Alternatively: LSM-tree design (SSTables are snapshots).
```

---

## 6. Recovery from a Crash

The whole reason we have a WAL is to recover from crashes without
data loss.

### 6.1 Typical recovery

```
  On startup:
    1. Find latest durable snapshot.
    2. Load snapshot into memory.
    3. Open WAL at snapshot's log index.
    4. Replay entries > snapshot_idx and ≤ last_committed_idx.
    5. Resume serving.

  Idempotent application is KEY: replaying a committed entry
  twice must produce the same state (not "double-apply").
  In SMR this is free — the state machine is deterministic.
```

### 6.2 Handling torn writes

```
  Crash mid-fsync → WAL may end with a partial record.
  Recovery must DETECT and TRUNCATE the corrupt tail.

  Techniques:
    - Checksum every record (CRC32/XXHash).
    - Magic sentinel bytes at record boundaries.
    - Fixed-size record headers with length + checksum.
    - Stop replay at first invalid record.
```

### 6.3 The "did it commit?" recovery problem

```
  Leader fsynced entry, died before replicating.
  On restart (or new leader election):
     - If another follower has the entry → committed.
     - If no replica has it → NOT committed, discard on old leader
       (when it rejoins as follower with lower term).

  Raft handles this via the "majority has it" commit rule +
  term numbers: a leader can only commit entries from its current term.
```

---

## 7. Raft's Log vs Kafka's Log — Same Idea, Different Goals

Both are replicated, ordered, append-only logs. But the emphasis differs:

```
┌──────────────────┬───────────────────────┬─────────────────────┐
│ Property         │ Raft / etcd            │ Kafka                │
├──────────────────┼───────────────────────┼─────────────────────┤
│ Purpose          │ metadata / small state│ huge event stream    │
│ Size             │ KB-MB                 │ TB-PB                │
│ Read model       │ replay + state machine│ consumer offsets     │
│ Retention        │ compact after snapshot│ time or size policy  │
│ Entry size       │ small                 │ up to 1MB typical    │
│ Ordering unit    │ whole log             │ per-partition log    │
│ Leader per unit  │ one per cluster        │ one per partition    │
│ Fsync frequency  │ every entry            │ batched, configurable│
└──────────────────┴───────────────────────┴─────────────────────┘
```

Under the hood they're cousins. Kafka "compacted topics" even
function as a Raft-like state store (where each key's latest
value survives compaction).

---

## 8. Group Commit & Batching

Even with sequential writes, fsync is costly. Real DBs batch
aggressively:

```
 THREAD 1:  write(entry1) ──┐
 THREAD 2:  write(entry2) ──┼── into WAL buffer
 THREAD 3:  write(entry3) ──┤
 THREAD 4:  write(entry4) ──┘
                             │
                             ▼
                       single fsync
                             │
                             ▼
                 all four threads ACK'd
                 (4 writes, 1 fsync)
```

Techniques seen in Postgres, MySQL, LevelDB, RocksDB:

```
  - Dedicated "log-writer" thread that drains the queue
  - Adaptive batch size based on load
  - Pipelined fsyncs (start next batch while this one syncs)
  - Commit-pipelining: pre-sync the next batch
```

---

## 9. Checkpointing & Truncation

```
  SNAPSHOT     capture state at log index S, save as "snapshot_S"
  CHECKPOINT   like a snapshot, but lighter (index of last applied)
  TRUNCATION   drop log entries below S (can't replay older than snapshot)

  Tuning knobs:
    - CHECKPOINT_INTERVAL       every N entries or T seconds
    - WAL_SEGMENT_SIZE          one file per N MB, rotate
    - WAL_RETENTION             keep extra segments for replication
    - PRUNE_OLDEST              drop segments older than some threshold

  Trade-off: frequent checkpoints → small recovery work,
             but high steady-state I/O.
```

---

## 10. Real-World Systems

```
┌──────────────────┬─────────────────────────────────────────────┐
│ System           │ WAL / SMR details                            │
├──────────────────┼─────────────────────────────────────────────┤
│ Postgres         │ WAL (pg_wal), replicated via streaming       │
│ MySQL (InnoDB)   │ Redo log + binlog; binlog for replication    │
│ MongoDB          │ Oplog (capped collection), replica sets      │
│ etcd             │ WAL + Raft log; snapshots every 10k entries  │
│ ZooKeeper        │ Transaction log + in-memory DataTree, ZAB    │
│ Kafka            │ Segment files per partition; log compaction  │
│ RocksDB/LevelDB  │ WAL + memtable + SSTables (LSM)              │
│ Redis            │ AOF (append-only file) + optional RDB snaps  │
│ HBase            │ HLog (HDFS WAL) + MemStore + HFiles          │
│ Cassandra        │ CommitLog + Memtable + SSTables              │
│ SQL Server       │ Transaction log, always-on availability grps │
│ Chubby/GFS/BFT DBs│ Paxos-replicated log + state machines       │
└──────────────────┴─────────────────────────────────────────────┘
```

---

## 11. Interview Q&A

### Q1. "Explain state machine replication."

> Every replica is a deterministic state machine that starts in the
> same initial state. If they all apply the **same ordered sequence
> of operations**, they end up in the same final state. Consensus
> (Raft/Paxos) agrees on the order; the rest — replication, recovery,
> consistency — falls out of this property.

### Q2. "Why is the WAL written before the state is updated?"

> So the system can recover after a crash. Durability is guaranteed by
> an **fsynced WAL entry**; applying to in-memory state can happen
> later because, if we crash, we can replay the log. If we updated
> state first and crashed before logging, we'd have no way to know
> we'd acknowledged that write.

### Q3. "How does group commit improve throughput?"

> It combines many concurrent writes into a single fsync. Since fsync
> is the dominant cost (often ms per call), batching amortizes that
> cost across all the writes, increasing throughput by 10-100×.
> Average latency rises a bit (wait for the batch), but per-op CPU
> and disk I/O drop dramatically.

### Q4. "What's the difference between committed and applied in Raft?"

> **Committed**: an entry is present on a majority of replicas, so
> no future leader can discard it. **Applied**: the entry has been
> actually run against the state machine. A node can (and usually does)
> commit faster than it applies. Client responses happen after apply,
> not just after commit.

### Q5. "How do you avoid the log growing forever?"

> **Snapshots + log truncation**. Periodically serialize the state
> machine's full state to disk at a known log index S. After the
> snapshot is durable, delete log entries below S. New replicas
> bootstrap by loading the snapshot first, then replaying only recent
> log entries.

### Q6. "A replica has fallen far behind — the leader already compacted past it. What now?"

> The leader sends an **InstallSnapshot** RPC: the follower receives
> the latest snapshot, loads it, and then catches up on the tail
> of the log from the snapshot's index onward. This is why SMR
> systems need both log replication AND snapshot shipping.

### Q7. "Why must the state machine be deterministic?"

> Two replicas applying the same input but arriving at different
> state defeats the whole point of SMR. Common non-determinism
> sources — clocks, randomness, hash iteration order, external
> API calls — must be eliminated or folded into the log itself.
> For example, "current timestamp" is stamped by the leader and
> written into the log, so followers use the exact same value.

### Q8. "Kafka and Raft both have logs — are they the same thing?"

> Architecturally similar: both are ordered, append-only, replicated
> logs with a leader per "unit." But Kafka is tuned for **huge event
> streams with consumer offsets and time-based retention**, while
> Raft targets **small metadata coordination with full state
> recovery via snapshots**. Kafka trades some consistency properties
> (a follower being a bit behind is fine) for throughput; Raft is
> stricter.

---

## 12. Further Reading

- Schneider, "Implementing Fault-Tolerant Services Using the State Machine Approach" (1990) — seminal
- Ongaro & Ousterhout, "In Search of an Understandable Consensus Algorithm" (2014) — Raft
- Postgres docs — "Reliability and the Write-Ahead Log"
- RocksDB & LevelDB source code — classic WAL + LSM implementations
- Kafka "The Log" blog post (Jay Kreps, 2013) — why logs are foundational

---

> **Previous:** [09-ConflictResolution.md](./09-ConflictResolution.md) ·
> **Next:** [11-DeliverySemantics.md](./11-DeliverySemantics.md)
