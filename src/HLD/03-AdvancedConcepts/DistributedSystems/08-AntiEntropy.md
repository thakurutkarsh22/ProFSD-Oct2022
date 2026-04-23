# Anti-Entropy & Read Repair

> **Difficulty:** Medium | **Time:** 1.5 hours | **Priority:** Should Know

**Entropy** = the divergence between replicas that happens when messages
get lost, replicas go offline, or writes race. **Anti-entropy** is the
collection of techniques that *converge* replicas back to the same state
without a leader telling them to. The key mechanisms:
**read repair, hinted handoff, and Merkle-tree-based full reconciliation.**

---

## Table of Contents

1. [Why Replicas Diverge](#1-why-replicas-diverge)
2. [The Three Techniques Together](#2-the-three-techniques-together)
3. [Read Repair](#3-read-repair)
4. [Hinted Handoff](#4-hinted-handoff)
5. [Merkle Trees](#5-merkle-trees-the-core-of-background-repair)
6. [Full Anti-Entropy Repair Protocol](#6-full-anti-entropy-repair-protocol-cassandra-example)
7. [Cost, Scheduling, and Throttling](#7-cost-scheduling-and-throttling)
8. [Real-World Implementations](#8-real-world-implementations)
9. [Interview Q&A](#9-interview-qa)

---

## 1. Why Replicas Diverge

```
 CAUSES OF REPLICA DIVERGENCE
 ────────────────────────────
  DROPPED MESSAGES         Replication packet lost, never retried
  NODE DOWN DURING WRITE   Write went to 2 of 3 replicas; third missed
  PARTITION HEALED         Two sides now disagree about state
  CONCURRENT WRITES        Multi-leader / leaderless → conflicts
  DISK BITROT              Silent data corruption on one replica
  HINT TTL EXPIRED         Hinted handoff gave up
  COMPACTION BUG           SSTable rewrite lost a key
```

Over time, **any** AP system will accumulate small divergences.
Without a correction mechanism, they become permanent.

---

## 2. The Three Techniques Together

```
 ┌──────────────────────────────────────────────────────────────┐
 │               ANTI-ENTROPY LAYERED DEFENSE                    │
 ├──────────────────────────────────────────────────────────────┤
 │                                                               │
 │  READ REPAIR                (foreground, opportunistic)       │
 │    Fix stale replicas WHILE serving reads.                    │
 │    Cost: one extra write per detected divergence.             │
 │    Limitation: only fixes KEYS THAT ARE READ.                 │
 │                                                               │
 │  HINTED HANDOFF             (write-path, during failure)      │
 │    During a brief outage of replica X, stash its writes on    │
 │    a stand-in; deliver when X comes back.                     │
 │    Limitation: hint TTL — can't hoard forever.                │
 │                                                               │
 │  ACTIVE ANTI-ENTROPY        (background, periodic)            │
 │    Replicas compare state via Merkle trees and exchange       │
 │    diverged keys. Catches everything the other two missed.    │
 │    Cost: bandwidth + disk I/O.                                │
 │                                                               │
 │  → All three are combined in production; none is sufficient   │
 │    alone.                                                     │
 └──────────────────────────────────────────────────────────────┘
```

---

## 3. Read Repair

### 3.1 The mechanism

```
  Client issues read with R=3.
  Coordinator queries all 3 replicas.
  Responses:
     N1: value=42, ts=100
     N2: value=42, ts=100
     N3: value=40, ts=95    ← stale

  Coordinator:
    1. Return latest (42, ts=100) to client.
    2. Send WRITE to N3: (42, ts=100)   ← "read repair"
    3. N3 applies the fix (or a newer value if already updated).
```

### 3.2 Two flavors

```
 FOREGROUND READ REPAIR
   - Coordinator waits until the repair write ACKs before returning.
   - Guarantees: post-read, all queried replicas are consistent.
   - Cost: adds latency to every mixed-state read.

 BACKGROUND READ REPAIR
   - Coordinator returns to client immediately.
   - Fires the repair write async.
   - Cost: no added latency, but stale replica could serve an even
          earlier read before the fix.
```

### 3.3 Partial vs. full read repair

```
  "READ REPAIR CHANCE" = probability this read triggers repair
  across ALL replicas, not just the ones queried in this R-quorum.

  In Cassandra:
    dclocal_read_repair_chance   probability of fixing all replicas
                                  in the local DC
    read_repair_chance            same, cross-DC (now deprecated,
                                  favoring active anti-entropy)

  Lower the chance → cheaper reads but slower convergence.
```

### 3.4 Limits

```
  ✗ Fixes ONLY keys that are being read.
  ✗ Cold keys may stay divergent for months.
  ✗ Can't cover full-table corruption.
```

→ That's why you still need **active anti-entropy** (§5).

---

## 4. Hinted Handoff

Already covered in detail in [`06-Quorum.md §6`](./06-Quorum.md); here's
the reconciliation-focused view.

### 4.1 The flow

```
  Coordinator notices N1 is DOWN at write time.
  Writes successful on N2, N3, plus stand-in N4 with HINT.

  N4's hint log:
     (target=N1, key=K, value=V, ts=T)
     (target=N1, key=K2, value=V2, ts=T2)
     ...

  When N1 recovers:
     N4 streams hint log → N1 applies.
     Delete applied hints.
```

### 4.2 Why it's not sufficient alone

```
  - Hints stored on disk, but N4 can crash before delivery.
  - Hint TTL (e.g., 3 hours in Cassandra default) drops old hints.
  - Coordinator may itself restart, losing in-flight hints.
  - Doesn't protect against bitrot on N1 itself.

  → must be backstopped by active anti-entropy.
```

---

## 5. Merkle Trees: the core of background repair

A **Merkle tree** is a tree of hashes that lets you compare two big
datasets and find ONLY the diverged keys in **O(log N)** comparisons
instead of comparing every key.

### 5.1 Structure

```
                       ┌── ROOT ──┐
                       │          │
                H(AB,CD)  =  hash(hash(A,B) ++ hash(C,D))
                     ╱          ╲
                    ╱            ╲
             ┌── h(AB) ──┐   ┌── h(CD) ──┐
             │           │   │           │
           h(A)        h(B)  h(C)       h(D)
            │           │     │           │
          key1        key2   key3       key4
```

### 5.2 How comparison works

```
  Replica 1 tree:             Replica 2 tree:

        ROOT = 0xABC               ROOT = 0xDEF   ← different

  Walk down:
        L = 0x11                   L = 0x11      ← same, skip whole subtree
        R = 0x22                   R = 0x33      ← different, recurse

  Into R:
        RL = 0x44                  RL = 0x44     ← same
        RR = 0x55                  RR = 0x66     ← different, recurse
           (leaf)                     (leaf)

  RR corresponds to a range of keys like [key_10000..key_10100].
  Only those keys are streamed + compared directly.

  Result: TB of data compared in seconds, only diverged ranges sent.
```

### 5.3 Tree construction

```
  Typical: sort keys by hash(key), partition into ~100k-size leaves.
  Each leaf = hash of all values in that partition.
  Parent nodes = hash of children.
  Built on demand during repair, not kept in memory forever.
```

### 5.4 Simple walk-through diagram

```
     Replica A                           Replica B
     ─────────                           ─────────
     ROOT: h1                            ROOT: h2    →   DIFFER

     h(LEFT)=a1     h(RIGHT)=a2          h(LEFT)=a1     h(RIGHT)=b2
        ↑ same                              ↑ same         ↑ differ
        └────── skip entire subtree ────────┘

     Recurse into RIGHT:
     h(RR_LEFT)=r1  h(RR_RIGHT)=r2       h(RR_LEFT)=r1  h(RR_RIGHT)=r3
                         ↑ differ                             ↑ differ

     LEAF for RR_RIGHT = 1000 keys.
     Send them all, diff key-by-key, reconcile.
```

### 5.5 Other uses of Merkle trees

```
  Git                 commit hash = Merkle root of tree of blobs
  Bitcoin / Ethereum  block hash = Merkle root of transactions
  IPFS                content-addressed storage uses Merkle DAG
  Amazon DynamoDB     background anti-entropy
  Riak, Cassandra     repair tool (nodetool repair)
  ZFS / BTRFS         checksums form a Merkle tree for corruption detection
```

---

## 6. Full Anti-Entropy Repair Protocol (Cassandra example)

```
  1. Operator (or scheduler) calls:
        nodetool repair -pr   (primary range only)

  2. Initiator node picks a token range owned by it.

  3. Initiator asks all OTHER replicas for that range to build
     their Merkle tree for that range.

  4. Each replica scans SSTables, computes Merkle tree leaves
     = hash of sorted (key, value-hash) pairs.

  5. Replicas send trees back to initiator.

  6. Initiator compares trees pairwise. Where leaves differ,
     it streams the actual data between replicas.

  7. Each replica merges streamed SSTables, runs compaction,
     now consistent for that range.

  Done periodically (default: every 7 days, must finish before
  GC grace to avoid zombie-tombstone issues).
```

### 6.1 Incremental repair

```
  Full repair rescans all SSTables → very expensive.
  INCREMENTAL repair: mark SSTables as "repaired" after first pass;
    subsequent repairs only cover UNREPAIRED ones.

  Huge I/O savings, but historical issues with edge cases
  (marking wrong SSTables as repaired). Cassandra 4.x improved this.
```

### 6.2 Subrange repair

```
  Break the huge token range into small subranges, repair them one
  at a time. Lets you checkpoint and resume if the process crashes.

  Tools: reaper (https://cassandra-reaper.io), Priam.
```

---

## 7. Cost, Scheduling, and Throttling

Anti-entropy is expensive — it touches ALL data.

### 7.1 Resources consumed

```
  CPU        hashing every key/value
  DISK I/O   reading every SSTable + writing reconciled data
  NETWORK    streaming diverged ranges
  MEMORY     holding Merkle trees
```

### 7.2 Throttling levers (Cassandra illustrative)

```
  stream_throughput_outbound_megabits_per_sec
  compaction_throughput_mb_per_sec
  concurrent_compactors
  incremental_backups: true   (reduce I/O via delta snapshots)
```

### 7.3 Scheduling strategy

```
  OFF-HOURS
    Run full repair during low-traffic windows.

  ROUND-ROBIN
    Each night one node repairs its primary range — over a week,
    full cluster is covered.

  CONTINUOUS + INCREMENTAL
    Small incremental passes constantly, bigger full pass weekly.

  TOPOLOGY-AWARE
    Run local-DC-only repairs more often; cross-DC repairs less often
    (cross-DC bandwidth is precious).
```

### 7.4 The anti-entropy ↔ tombstone trap (Cassandra)

```
  DELETES are represented by TOMBSTONES with a TTL = GC grace period.
  Anti-entropy repair must finish a FULL cycle before GC grace,
  otherwise:

     - Replica A deleted key K at ts=T (tombstone).
     - Replica B was down, never got tombstone.
     - GC grace passes on A → A deletes tombstone.
     - Next repair: B has K at ts=T-1, A has NOTHING.
     - System "revives" K on A → ZOMBIE DATA (data seemingly
       un-deletes itself).

  MITIGATION: always run repair more often than gc_grace_seconds.
```

---

## 8. Real-World Implementations

```
┌──────────────┬─────────────────────────────────────────────────┐
│ System       │ Anti-entropy approach                            │
├──────────────┼─────────────────────────────────────────────────┤
│ Cassandra    │ Merkle trees per token range + nodetool repair  │
│ DynamoDB     │ Background Merkle reconciliation (invisible)    │
│ Riak         │ Active anti-entropy via hash trees (AAE)        │
│ HDFS         │ Periodic block scanner + NameNode block reports │
│ Ceph         │ Scrub / deep-scrub compare replicas             │
│ GlusterFS    │ Self-heal daemon + Merkle-like healing          │
│ ZFS / BTRFS  │ Scrub reads every block + checksum comparison   │
│ S3 (internal)│ Continuous background replication verification  │
│ Git          │ git fsck + Merkle-tree-native repo structure    │
└──────────────┴─────────────────────────────────────────────────┘
```

---

## 9. Interview Q&A

### Q1. "How do Cassandra replicas stay in sync without a leader?"

> Three layers combined:
> (1) **Read repair** fixes stale replicas at read time.
> (2) **Hinted handoff** patches short outages by storing the missed
> writes on a neighbor and delivering them when the down node recovers.
> (3) **Active anti-entropy** (nodetool repair) periodically compares
> Merkle trees of each node's data and streams the differences.
> Read repair alone misses cold keys; hinted handoff alone has a TTL;
> anti-entropy backstops everything.

### Q2. "Why use a Merkle tree instead of comparing key by key?"

> Comparing every key requires transferring every key's hash between
> replicas — O(N) in data size. A Merkle tree structure lets you
> compare O(log N) hashes top-down, only descending into subtrees
> whose hashes differ. For terabytes of data, this turns hours of
> network time into seconds.

### Q3. "Foreground vs background read repair — when do you use each?"

> **Foreground** when you need strong consistency guarantees (equivalent
> to a stronger R quorum): the read blocks until repair confirms.
> **Background** when latency matters more than immediate convergence
> — repair fires asynchronously and the next read may still see
> stale data on the same replica.

### Q4. "What can cause data to 'come back from the dead' in Cassandra?"

> Tombstone resurrection: a delete creates a tombstone, which is
> GC'd after `gc_grace_seconds`. If a replica was offline longer than
> that window and missed the tombstone, when it rejoins and anti-entropy
> runs, the old (undeleted) data is compared against *no tombstone* on
> the other replicas → the old data looks newer → deletion "undone."
> Mitigation: run anti-entropy repair **more frequently** than
> gc_grace_seconds (typically every 7 days).

### Q5. "Why can't you rely only on read repair?"

> Because read repair only touches keys that are read. Cold data
> (rarely accessed) can diverge for weeks or months, and silently
> corrupt or lost data never gets fixed. You also need **background
> anti-entropy** to guarantee convergence across all keys.

### Q6. "How is a Merkle tree built over a sharded dataset?"

> Each shard/node builds its own tree over its local key range.
> Leaves are hashes of (key, value-hash) pairs within a partition
> (say, 100k consecutive keys). Internal nodes are hashes of children.
> Nodes exchange only their trees during comparison; only when hashes
> differ at a leaf do they exchange actual data for that range.

### Q7. "Riak's Active Anti-Entropy (AAE) — how does it differ from Cassandra's repair?"

> Riak maintains **persistent Merkle trees** updated incrementally as
> writes happen — no full rescans needed. Cassandra traditionally
> rebuilt trees from SSTables on demand; newer incremental repair is
> similar in spirit to Riak AAE but different in implementation.

### Q8. "If a replica is silently corrupt (bitrot), does anti-entropy catch it?"

> Yes — the corrupted replica's Merkle tree leaves will differ from
> siblings, so the repair process will stream "correct" data over.
> But: the corrupted node must not be the only source of truth for
> the affected keys — if the original write only went to the bad
> replica (W=1) then every replica is "correct by ancestry," nothing
> to compare against.

---

## 10. Further Reading

- DeCandia et al., "Dynamo: Amazon's Highly Available Key-value Store" (2007)
- Merkle, "A Digital Signature Based on a Conventional Encryption Function" (1987) — original Merkle trees
- Cassandra docs — "Anti-entropy repair"
- Basho / Riak docs — "Active Anti-Entropy"
- Kleppmann, "Designing Data-Intensive Applications" Ch. 5 — background repair
- TLP Cassandra Reaper (github.com/thelastpickle/cassandra-reaper)

---

> **Previous:** [07-ReplicationStrategies.md](./07-ReplicationStrategies.md) ·
> **Next:** [09-ConflictResolution.md](./09-ConflictResolution.md)
