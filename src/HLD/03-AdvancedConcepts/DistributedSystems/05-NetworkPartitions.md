# Network Partitions & Split-Brain

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Must Know

A **network partition** is when a subset of nodes can still talk to
each other but **can't reach the rest of the cluster**. Partitions are
unavoidable — they *will* happen — and the single most dangerous
consequence is **split-brain**: two "leaders" making independent,
divergent decisions. This document covers detection, prevention, and
recovery.

---

## Table of Contents

1. [What a Partition Actually Is](#1-what-a-partition-actually-is)
2. [Partition Taxonomy](#2-partition-taxonomy)
3. [Split-Brain](#3-split-brain)
4. [The CAP Choice During a Partition](#4-the-cap-choice-during-a-partition)
5. [Fencing Tokens](#5-fencing-tokens)
6. [STONITH / Shoot-the-Other-Node](#6-stonith--shoot-the-other-node)
7. [Quorum, Witnesses, and Arbiters](#7-quorum-witnesses-and-arbiters)
8. [Healing a Partition (Merge & Reconcile)](#8-healing-a-partition-merge--reconcile)
9. [Design Checklist for "Partition-Proof" Services](#9-design-checklist-for-partition-proof-services)
10. [Real-World Partition Incidents](#10-real-world-partition-incidents)
11. [Interview Q&A](#11-interview-qa)

---

## 1. What a Partition Actually Is

```
 BEFORE PARTITION                    DURING PARTITION
 ────────────────                    ─────────────────
  ┌──┐  ┌──┐  ┌──┐                   ┌──┐  ┌──┐    ▌     ┌──┐
  │A │──│B │──│C │                   │A │──│B │    ▌     │C │
  └──┘  └──┘  └──┘                   └──┘  └──┘    ▌     └──┘
                                                    ▌
                                          network split (wall)

  A+B can talk.  C is isolated.
  From A's/B's view: "C is DEAD."
  From C's   view: "A and B are DEAD."

  Both views are WRONG. Both sides still have state; both still have
  clients hitting them.
```

### 1.1 Partitions are not rare

Bailis & Kingsbury (2014) surveyed production operators: partitions
happen **routinely** even in "reliable" networks — cloud outages,
bad router firmware, fiber cuts, overloaded switches, misconfigured
firewalls. You must design for them.

---

## 2. Partition Taxonomy

```
┌──────────────────────────────────────────────────────────────┐
│                 TYPES OF PARTITIONS                           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  CLEAN PARTITION (symmetric)                                  │
│  Two sides cannot reach each other at all.                    │
│       A─B | C                                                  │
│                                                               │
│  ASYMMETRIC PARTITION                                         │
│  A can hear B but B can't hear A (one-way).                   │
│  Extremely confusing — detectors disagree with each other.    │
│                                                               │
│  PARTIAL PARTITION                                            │
│  Some links work, others don't, intermittently.               │
│  Causes "split-brain-lite" — two groups, each sometimes       │
│  able to reach a third node.                                  │
│                                                               │
│  "FLAPPING" PARTITION                                         │
│  Intermittent packet loss making membership churn constantly. │
│                                                               │
│  SLOW PARTITION (indistinguishable from timing failure)       │
│  Network delivers packets but at 10× normal latency →         │
│  detectors start flagging nodes as dead.                      │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 2.1 Asymmetric partition — why it's nasty

```
  A ──► B    packets flow
  A ◄─╳─ B   B's replies drop

  A thinks: B is silent → DEAD
  B thinks: A keeps sending → ALIVE

  Two independent "realities" inside the cluster.
```

Many real network partitions are asymmetric. Failure detectors that
use one-way heartbeats (A sends, B listens) will disagree with each
other and produce inconsistent cluster membership.

---

## 3. Split-Brain

When both sides of a partition *act* as if they own the data:

```
   ┌─── partition ╳ partition ───┐
   │                              │
   ▼                              ▼
 LEADER A ── writes "x=5" ── clients in zone 1
 LEADER B ── writes "x=7" ── clients in zone 2

 Partition heals. Now:
   - Replica A says x=5
   - Replica B says x=7
   - Both sides have their own history since the split.
   - Merging is hard: which wins? What about secondary effects?
     (Emails sent? Charges captured? Rows created?)
```

### 3.1 Split-brain in the wild

```
  RabbitMQ        mirrored queues on both sides → duplicate messages
  Elasticsearch   two "masters" → two shards think they're primary
  Redis Sentinel  two "masters" accepting writes
  MongoDB         both primaries in separate regions
  ZooKeeper       correctly PREVENTS it via majority quorum
```

### 3.2 Why "leader election with no quorum" is always wrong

```
   2-node "HA pair" with heartbeats but no quorum:

   Node A ──heartbeat─► Node B
       Link breaks.
   A says  "B dead, I'm the leader."
   B says  "A dead, I'm the leader."
   → Split-brain.

   Need ≥3 nodes (odd) OR a WITNESS (§7) to break ties.
```

---

## 4. The CAP Choice During a Partition

CAP says: during a partition, you must choose between **C** and **A**.

```
       CP            AP
       ──            ──
  Stop serving       Keep serving
  the minority       both sides
  side entirely.     (accept potential
                     conflict).
  No split-brain.    Eventually
                     reconcile.

  e.g. etcd,         e.g. DynamoDB,
       ZooKeeper,    Cassandra,
       Spanner       Riak
```

### 4.1 CP behaviour during partition

```
 Cluster of 5. Partition into {3} + {2}.

   Majority side {3}:      continues to commit (3 ≥ quorum)
   Minority side {2}:      refuses writes, may refuse reads too

 Result: the 2-node side is essentially OFFLINE until partition heals.
 Safety kept; availability lost for those nodes.
```

### 4.2 AP behaviour during partition

```
 Both sides accept writes independently:
    {3}  accepts  write("x=5")
    {2}  accepts  write("x=7")

 After healing: conflict resolution runs (LWW, siblings, CRDTs).
 Safety relaxed; availability kept.
```

### 4.3 Middle ground: CRDT-based AP is "safe AP"

```
 Use CRDTs so that ANY merge of concurrent writes yields the
 same, meaningful result (no data loss, no user-visible conflict).
 See 09-ConflictResolution.md.
```

---

## 5. Fencing Tokens

The single most important defense against split-brain after a lease expiry.

### 5.1 The problem WITHOUT fencing

```
  1. Client A acquires distributed lock. Lease=30s.
  2. A enters a 60-second GC pause.
  3. Lease expires, lock service hands lock to B.
  4. B does work, writes to storage.
  5. A wakes up, still "holds" the lock (its local view).
  6. A writes to storage with stale information.

  → Lost updates, data corruption.

  TIME LINE
  ─────────
  t=0       A acquires lease
  t=1       A starts long GC
  t=30      A's lease expires
  t=31      B acquires lease, writes
  t=60      A wakes up, writes using the LEASE IT NO LONGER HAS
```

### 5.2 The fix: monotonically increasing token

```
  Lock service returns a TOKEN with every acquisition:
      A got lock → token = 33
      ... A pauses ...
      B got lock → token = 34

  Storage MUST enforce:
      "Reject any write whose token < latest seen token."

  ┌──────────┐  write(data, token=33)      ┌──────────┐
  │ Client A │ ─────────────────────────►  │ Storage  │
  └──────────┘                             │   latest │
  (zombie)                                 │ token=34 │
                                           │  REJECT  │
                                           └──────────┘
```

### 5.3 Requirements for fencing to work

```
  1. Tokens must be MONOTONIC across grants (1 < 2 < 3...).
  2. STORAGE (not just clients) must check the token on every write.
  3. Tokens must be issued by a SINGLE source of truth (the lock
     service, which is typically a consensus system like etcd/ZooKeeper).

  Without storage-side checking: fencing is just a label, no safety.
```

### 5.4 Real implementations

```
  HBase        RegionServer sequence numbers
  Kafka        producer "epoch" per transactional.id
  Chubby/GFS   master election sequence numbers
  etcd         lease revision IDs
  ZooKeeper    zxid (transaction ID) as the token
```

---

## 6. STONITH / Shoot-the-Other-Node

"**S**hoot **T**he **O**ther **N**ode **I**n **T**he **H**ead"
(yes, really). When you can't trust the suspected-dead node to stay
dead quietly, **physically force it offline**.

### 6.1 Mechanics

```
  Cluster manager decides Node A is dead.
  Before promoting B:
     - Send IPMI "power off" command to A's BMC.
     - Or disable A's SAN port on the switch.
     - Or disable A's network interface via BGP withdrawal.
     - Or revoke A's Kerberos tickets / service account.
  THEN promote B.

  Guarantees A cannot come back as a zombie.
```

### 6.2 Where you'll see it

```
  - HA databases on shared storage (Pacemaker / DRBD)
  - Oracle RAC
  - VMware HA
  - Some high-end financial / telecom systems
```

### 6.3 Trade-offs

```
  + Hard safety guarantee
  - Complex plumbing (IPMI, smart PDUs, specialized switches)
  - Mistaken STONITH bricks a healthy node unnecessarily
  - Cloud-native systems usually prefer fencing tokens instead
```

---

## 7. Quorum, Witnesses, and Arbiters

### 7.1 Why even-sized clusters are bad

```
  2-node:   quorum=2.  Lose 1 → no quorum → no progress.
  4-node:   quorum=3.  Lose 1 → still OK.  Lose 2 → split 2/2, no quorum.
           Adds a node but STILL tolerates just 1 failure.
           → waste of a node.

  3-node:   quorum=2.  Tolerates 1 failure.
  5-node:   quorum=3.  Tolerates 2 failures.

  Rule:  N = 2F + 1  (odd numbers optimal)
```

### 7.2 Two-datacenter dilemma

```
  DC1: 2 nodes.  DC2: 2 nodes.
  Partition between DCs → each side has 2, neither is majority.
  → entire cluster stalls.

  Fix: add a 5th node (WITNESS) in a third location.
```

### 7.3 Witness / arbiter node

A lightweight node that **votes** but doesn't store data. Used purely
to break ties across two symmetric regions.

```
     DC-EAST (2)           DC-WEST (2)
        A   B                 C   D
         \ /                   \ /
          └─────── quorum ─────┘
                     │
                     │
                   ┌─▼─┐
                   │ W │   Witness (single node in DC-Neutral)
                   └───┘

  Normal operation: 5 nodes, quorum=3.
  Partition DC-EAST | DC-WEST:
     East side has {A,B,W}=3 → majority, keeps running.
     West side has {C,D}=2  → minority, refuses writes.
  W (witness) picks the winning side and breaks the tie.
```

**Used by:** MongoDB (arbiter), SQL Server AlwaysOn (file-share witness),
Raft-based systems in cross-DC deployments.

### 7.4 Dynamic quorum & flexible Paxos

Newer consensus variants (Flexible Paxos, Spanner's "participant nodes")
allow differently-sized quorums for election vs. committing, letting
you tune latency vs. fault tolerance. See `../02-Consensus.md`.

---

## 8. Healing a Partition (Merge & Reconcile)

Partitions always end eventually — then you have to reconcile divergent
history. Strategy depends on what your system did during the split:

### 8.1 CP system — trivial merge

```
 Minority side didn't accept writes. When link restores:
   - Minority side catches up from majority via log replication.
   - No conflict possible.
```

### 8.2 AP system — real work

```
 Both sides have independent histories.
 Reconciliation options:

 (a) LAST-WRITER-WINS
     Pick the write with the largest timestamp. Simple, lossy.

 (b) SIBLINGS (Dynamo)
     Return all concurrent versions to the app; let it merge.

 (c) CRDTS
     Merge mathematically; guaranteed same result regardless of order.

 (d) OPERATION LOG REPLAY
     Re-execute both sides' operations on a merged base (needs
     commutative operations or a merge function per op-type).
```

### 8.3 Externalities are the hard part

```
  During the split, both sides may have:
    - Sent emails
    - Charged credit cards
    - Updated external systems
    - Triggered workflows

  These CAN'T be un-done automatically.
  Rule: external side effects should be idempotency-keyed and
        at most one side should produce them (e.g., only the
        majority-quorum side triggers payment).
```

---

## 9. Design Checklist for "Partition-Proof" Services

```
 ☐ Cluster size is ODD (3, 5, or 7)
 ☐ At least one node in a different failure domain (AZ/Region)
 ☐ Leader election requires a MAJORITY QUORUM (never 2-node HA)
 ☐ Minority side REFUSES to serve writes (or only AP-safe ones via CRDTs)
 ☐ Every write carries a FENCING TOKEN; storage enforces it
 ☐ External side effects happen ONLY on the majority side
 ☐ Client retries are IDEMPOTENT (idempotency keys, dedup)
 ☐ Monitoring alerts on partition detection, not just latency
 ☐ Chaos testing: regularly inject partitions in staging
    (tools: Jepsen, Chaos Mesh, AWS Fault Injection Simulator)
 ☐ Runbook exists for "what happens when we partition" scenarios
```

---

## 10. Real-World Partition Incidents

```
 GITHUB (2018-10-21)
 ────────────────────
 43-sec partition between East/West coast.
 MySQL replication topology got into a state where both sides
 accepted writes. Recovery: 24 hours of manual reconciliation.

 KAFKA 0.8.2 (classic)
 ────────────────────
 Controller split-brain: two nodes each thought they were
 controller after a ZK session expiry. Led to concurrent
 partition-leader elections and lost messages. Fixed via
 monotonic controller epoch (fencing token).

 ELASTICSEARCH "SPLIT-BRAIN"
 ────────────────────────────
 Before discovery.zen.minimum_master_nodes was set correctly,
 2-node ES clusters routinely split into two masters,
 causing silent data divergence.

 MONGODB PRE-3.6
 ────────────────
 Writes to a "stepped-down" primary during rollback → data loss.
 Fixed in 3.6 by adding majority write concern as a defense.

 AWS DYNAMODB (2015)
 ────────────────────
 Metadata service partition + retry storm → hours-long outage in
 us-east-1. Recovery required drain-and-restart of storage fleet.
```

---

## 11. Interview Q&A

### Q1. "What is split-brain and how do you prevent it?"

> Split-brain is when two (or more) partitions of a cluster each act
> as if they're in charge — each accepts writes, each runs workflows.
> Prevent via:
> (a) **Majority quorum** — only the side with more than N/2 nodes
> can commit.
> (b) **Leases with fencing tokens** — storage rejects writes from
> old leaders.
> (c) **STONITH / physical fencing** — power off suspected nodes
> before promoting replacements.
> (d) **Witness / arbiter** nodes to break 2-DC ties.

### Q2. "Why are two-node HA clusters dangerous?"

> Because neither node alone can form a majority. When the link
> between them breaks, both nodes independently conclude the other
> is dead and both try to become active. That's split-brain by
> construction. Always go ≥ 3 nodes (odd), or add a witness.

### Q3. "Explain fencing tokens and why a lease alone isn't enough."

> A distributed lock with a time-based lease can be held by a
> process that pauses (GC, VM freeze) past the expiry. By the time
> it wakes up, the lock is held by someone else, but the paused
> process doesn't know. Without fencing, it writes with stale
> "authority" and corrupts data.
>
> A **fencing token** is a monotonically increasing number issued
> with each lock grant. Storage rejects any write whose token is
> less than the latest seen. So even a zombie leader's write
> is harmlessly rejected.

### Q4. "During a partition in a CP system, what happens to requests on the minority side?"

> Writes fail immediately (no quorum). Reads may also fail or be
> served stale depending on configuration; linearizable reads require
> a quorum round-trip, so they also fail. The minority side is
> effectively offline until the partition heals and it can re-sync.

### Q5. "Your two-region DB splits. Both regions accept writes. What's the recovery plan?"

> That means AP behaviour. Recovery:
> 1. Detect the split is healed (gossip / admin intervention).
> 2. For each diverged key, resolve via LWW / sibling merge / CRDT.
> 3. Manually review any externally-visible side effects
>    (payments, emails) — these cannot be auto-reversed.
> 4. Post-mortem: ensure only the majority side should have
>    triggered external effects in the first place.

### Q6. "What's the purpose of a witness / arbiter node?"

> In a two-DC deployment with N nodes per DC, a partition between
> DCs splits the cluster evenly — no majority. A witness is a
> lightweight node in a third location that only *votes*. It
> brings the total to an odd number so one side always has a
> majority during a DC partition.

### Q7. "How do you test that your system handles partitions correctly?"

> **Jepsen** or similar tools: inject partitions while running
> workloads, then check for linearizability / invariant violations.
> Also use cloud-native fault injectors (Chaos Mesh, AWS Fault
> Injection Simulator, GKE ChaosLib) to drop traffic between pods
> or AZs. Do it *regularly* and treat failures as bugs.

### Q8. "Can a system be both 'strong consistency' and 'always available'?"

> No — that's exactly what CAP says is impossible during partitions.
> You can approximate it within a single failure domain (no partitions
> possible), but any real multi-region system must pick one.

---

## 12. Further Reading

- Bailis & Kingsbury, "The Network is Reliable" (2014)
- Kleppmann, "Designing Data-Intensive Applications" Ch. 8 — partitioning & fencing
- Kingsbury, Jepsen analyses (jepsen.io) — empirical partition testing on real DBs
- "Flexible Paxos" (Howard et al., 2016) — quorum systems beyond simple majority
- "Practical Aspects of Split-Brain Resolution" — MS SQL Server team

---

> **Previous:** [04-FailureDetection.md](./04-FailureDetection.md) ·
> **Next:** [06-Quorum.md](./06-Quorum.md)
