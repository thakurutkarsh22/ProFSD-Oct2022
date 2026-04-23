# Distributed Systems Fundamentals

> **Difficulty:** Medium-Hard | **Time:** 4-5 hours | **Priority:** Must Know

This is the mental-model chapter for everything else in the HLD track.
Once you understand *why* distributed systems are hard, every "design X" problem
becomes a variation on the same handful of trade-offs: consistency vs. availability,
latency vs. durability, coordination vs. throughput.

---

## Table of Contents

1. [What Makes Distributed Systems Hard](#what-makes-distributed-systems-hard)
2. [Clocks & Ordering](#1-clocks--ordering-in-distributed-systems)
3. [Consistency Models](#2-consistency-models-the-spectrum)
4. [Failure Modes](#3-failure-modes)
5. [Failure Detection](#4-failure-detection)
6. [Network Partitions & Split-Brain](#5-network-partitions--split-brain)
7. [Quorum](#6-quorum)
8. [Replication Strategies](#7-replication-strategies)
9. [Consistent Reads & Anti-Entropy](#8-consistent-reads--anti-entropy)
10. [Conflict Resolution](#9-conflict-resolution)
11. [State Machine Replication & WAL](#10-state-machine-replication--the-replicated-log)
12. [Delivery Semantics & Idempotency](#11-delivery-semantics--idempotency)
13. [Back-Pressure, Retries & Thundering Herd](#12-back-pressure-retries--thundering-herd)
14. [Service Discovery & Membership](#13-service-discovery--membership)
15. [Key Takeaways for Interviews](#14-key-takeaways-for-interviews)
16. [Common Interview Follow-Ups](#15-common-interview-follow-ups)

> Related deep-dives in this repo:
> [CAP / PACELC](../02-BuildingBlocks/01-CAPTheorem.md) ·
> [Consensus (Paxos/Raft)](./02-Consensus.md) ·
> [Distributed Transactions (2PC / Saga)](./03-DistributedTransactions.md) ·
> [Consistent Hashing](../02-BuildingBlocks/03-ConsistentHashing.md)

---

## Dedicated Deep Dives

Every major topic below has its own standalone document, with more
diagrams, anomalies, interview Q&A, and real-world system references.

| # | Deep Dive | Covers |
|---|---|---|
| 01 | [Clocks & Ordering](./DistributedSystems/01-ClocksAndOrdering.md) | NTP, Lamport, vector clocks, HLC, TrueTime |
| 02 | [Consistency Models](./DistributedSystems/02-ConsistencyModels.md) | Linearizable → eventual, session guarantees, isolation vs consistency |
| 03 | [Failure Modes](./DistributedSystems/03-FailureModes.md) | Crash, omission, timing, Byzantine, gray, cascading, metastable |
| 04 | [Failure Detection](./DistributedSystems/04-FailureDetection.md) | Heartbeats, Phi Accrual, Gossip, SWIM |
| 05 | [Network Partitions & Split-Brain](./DistributedSystems/05-NetworkPartitions.md) | Fencing tokens, STONITH, witness nodes |
| 06 | [Quorum](./DistributedSystems/06-Quorum.md) | N/W/R math, sloppy quorum, hinted handoff, flexible quorums |
| 07 | [Replication Strategies](./DistributedSystems/07-ReplicationStrategies.md) | Single/multi/leaderless, chain, sync modes, geo-replication |
| 08 | [Anti-Entropy & Read Repair](./DistributedSystems/08-AntiEntropy.md) | Merkle trees, hinted handoff, background repair |
| 09 | [Conflict Resolution](./DistributedSystems/09-ConflictResolution.md) | LWW, siblings, CRDTs, OT |
| 10 | [State Machine Replication & WAL](./DistributedSystems/10-StateMachineReplication.md) | Replicated log, WAL, snapshots, determinism |
| 11 | [Delivery Semantics & Idempotency](./DistributedSystems/11-DeliverySemantics.md) | At-most/least/exactly-once, idempotency keys, outbox, Kafka EOS |
| 12 | [Back-Pressure, Retries, Circuit Breakers](./DistributedSystems/12-BackPressureAndRetries.md) | Jitter, retry budgets, circuit breakers, hedged requests, bulkheads |
| 13 | [Service Discovery & Membership](./DistributedSystems/13-ServiceDiscovery.md) | Client/server-side discovery, service mesh, DNS, registration |

> Full index with a reading-order diagram: [`DistributedSystems/README.md`](./DistributedSystems/README.md)

The rest of this file is the **overview / map of the territory**.
Start here, then dive into whichever sub-document is most relevant.

---

## What Makes Distributed Systems Hard

```
┌──────────────────────────────────────────────────────────────┐
│           THE 8 FALLACIES OF DISTRIBUTED COMPUTING            │
│                   (Peter Deutsch, 1994)                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  1. The network is reliable          ← Packets get lost      │
│  2. Latency is zero                  ← Network hops add ms   │
│  3. Bandwidth is infinite            ← There are limits      │
│  4. The network is secure            ← Always verify         │
│  5. Topology doesn't change          ← Nodes join/leave      │
│  6. There is one administrator       ← Multiple teams        │
│  7. Transport cost is zero           ← Serialization costs   │
│  8. The network is homogeneous       ← Different hardware    │
│                                                              │
│  Every design must account for these realities!              │
└──────────────────────────────────────────────────────────────┘
```

### Why these fallacies bite you in practice

```
FALLACY              REAL-WORLD SYMPTOM                    MITIGATION
────────             ──────────────────                    ──────────
Reliable network  →  TCP RST, half-open sockets            Retries + idempotency keys
Zero latency      →  p99 spikes from cross-AZ hops         Read-local, write-leader
Infinite BW       →  Replication lag, backfill stalls      Batching, compression
Secure network    →  MITM, spoofed replicas                mTLS, service mesh
Stable topology   →  Auto-scaling, rolling deploys         Gossip + service discovery
One admin         →  Config drift between regions          GitOps, IaC
Zero transport    →  Serialization CPU burn                Protobuf/FlatBuffers
Homogeneous net   →  Slow nodes drag p99                   Hedged requests, tail-tolerance
```

### The three hard problems

Almost every interview question collapses into one of these:

```
        ┌─────────────────────────────────────────────┐
        │  1. ORDER    Which event happened first?     │
        │  2. AGREE    What is the single truth?       │
        │  3. DETECT   Is that node actually dead?     │
        └─────────────────────────────────────────────┘
```

---

## 1. Clocks & Ordering in Distributed Systems

> **Deep dive:** [`DistributedSystems/01-ClocksAndOrdering.md`](./DistributedSystems/01-ClocksAndOrdering.md)

### 1.1 Why physical clocks lie

```
THE PROBLEM: No global clock in distributed systems

  Server A                     Server B
  Wall:   10:00:01.000         Wall:   10:00:01.005
      │                             │
      │── Event X ─────────────────►│
      │                             │── Event Y
      │                             │

  Did X happen before Y? Server A says yes, Server B isn't sure.
  NTP keeps machines within ~10ms; bad NTP can mean full seconds of skew.
```

Physical-clock issues you'll see in production:

- **Clock skew** — two nodes disagree on "now".
- **Clock drift** — crystal oscillators tick at slightly different rates (~10-100 ppm).
- **NTP jumps** — clocks can *go backwards* after a correction.
- **Leap seconds** — caused real outages at Reddit, Cloudflare, LinkedIn.

> Rule of thumb: **never** use `System.currentTimeMillis()` to order events between machines.

### 1.2 Happens-Before (Lamport, 1978)

The canonical partial order "→" of events in a distributed system:

```
Event a → Event b  iff
  (1) a and b are on the same node and a came first, OR
  (2) a is a send and b is the matching receive, OR
  (3) there is a chain:  a → c → b  (transitivity)

If neither a → b nor b → a, the events are CONCURRENT (a ∥ b).
```

### 1.3 Lamport Clocks (scalar logical clock)

```
RULES:
  On any local event:     L := L + 1
  On send(m):             L := L + 1;  attach L to m
  On receive(m, L_m):     L := max(L, L_m) + 1

                                          Sends arrow shows message direction
  A │ [1]──────[2]──────────────[6]─────────────────────────────
    │   evt     send ──────┐     recv (max(5,3)+1=6)
    │                      │      ▲
    │                      ▼      │
  B │ [1]──────[2]─────[3]─(4)────[5]─────────────────────
    │   evt     evt     recv   evt    send ────────────┘
    │                  (max(1,2)+1=3)

  PROPERTY:  a → b  ⇒  L(a) < L(b)           (sound)
  BUT:       L(a) < L(b)  ⇏  a → b           (could be concurrent!)
```

Use Lamport clocks when you need a **total order of events** but only
one-way causality — e.g. printing a consistent event log.

### 1.4 Vector Clocks

Each node keeps a vector `V[i]` — one counter per node. They can detect
*concurrency* (something Lamport clocks cannot).

```
3-node vector clock walk-through (nodes A, B, C):

  A: [0,0,0]   B: [0,0,0]   C: [0,0,0]

  A does local event:
  A: [1,0,0]

  A → B  (send with [1,0,0])
  B merges: max([0,0,0],[1,0,0]) + bump B = [1,1,0]

  B does local event:
  B: [1,2,0]

  Meanwhile, C does event (independent):
  C: [0,0,1]

  B → C  (send with [1,2,0])
  C merges: max([0,0,1],[1,2,0]) + bump C = [1,2,2]

COMPARING VECTORS:
  V1 ≤ V2   iff  V1[i] ≤ V2[i]  for all i
  V1 <  V2  iff  V1 ≤ V2 and V1 ≠ V2        (V1 happens-before V2)
  V1 ∥ V2   iff  neither V1 < V2 nor V2 < V1 (concurrent — conflict!)

Used by: Amazon Dynamo, Riak, Voldemort.
Downside: vector grows with cluster size (mitigated with "dotted version vectors").
```

### 1.5 Hybrid Logical Clocks (HLC)

Combines wall time + logical counter so timestamps are (a) close to real time
and (b) monotonic across nodes.

```
HLC = (pt, l, node_id)  where pt = physical time (ms), l = logical counter

  Local event:
    pt_new = max(pt_local, wall_clock)
    if pt_new == pt_local:  l := l + 1
    else:                    l := 0
    HLC := (pt_new, l)

  On receive(msg_HLC):
    pt_new = max(pt_local, msg.pt, wall_clock)
    l      = logic to keep monotonic (see paper)

GUARANTEES:
  - HLC(a) < HLC(b) if a → b (happens-before respected)
  - HLC ≈ wall-clock time (bounded drift)
  - Fits in 64 bits → cheap to store per row/version

Used by: CockroachDB, YugabyteDB, MongoDB (cluster time).
```

### 1.6 TrueTime (Google Spanner) — clocks as intervals

Instead of pretending `now()` is a point, return an **interval** `[earliest, latest]`
with a bounded uncertainty `ε` (typically ~7 ms, achieved via GPS + atomic clocks).

```
  now() = [t - ε, t + ε]

  To commit a transaction at time T:
     sleep until now().earliest > T       ← "commit wait"
     now it is IMPOSSIBLE for any future
     TrueTime interval to report < T.

Result: external consistency (linearizability) across the globe,
with transactions paying ~2ε (~14 ms) of commit-wait latency.
```

---

## 2. Consistency Models — The Spectrum

> **Deep dive:** [`DistributedSystems/02-ConsistencyModels.md`](./DistributedSystems/02-ConsistencyModels.md)

Interviewers love when you can name the *specific* model your design needs.

```
   STRONGEST  ←──────────────────────────────────────────────── WEAKEST
                                                                             
   Linearizable ── Sequential ── Causal ── Read-your-writes ── Eventual     
      │               │            │             │                 │        
      │               │            │             │                 └─ DNS,  
      │               │            │             │                    S3    
      │               │            │             └─ user sees own posts     
      │               │            │                immediately             
      │               │            │                                        
      │               │            └─ cause-effect preserved                
      │               │               (Facebook COPS)                       
      │               │                                                     
      │               └─ all nodes see same order (not necessarily          
      │                  real time) — a single leader gives this            
      │                                                                     
      └─ appears as a single machine; real-time order preserved             
         (Spanner, etcd, ZooKeeper)                                         
```

### 2.1 Quick reference

| Model | Rule | Example |
|---|---|---|
| **Linearizable** | Reads see the *most recent* committed write, in real time | etcd, Spanner, ZooKeeper |
| **Sequential** | All clients see operations in *some* total order, same across nodes | Single-leader DB with sync replicas |
| **Causal** | If A causally precedes B, all nodes see A before B | COPS, MongoDB causal sessions |
| **Read-your-writes** | A client always sees its own prior writes | Sticky sessions, session tokens |
| **Monotonic reads** | Once you read value v, later reads won't return older values | Pin client to one replica |
| **Monotonic writes** | Writes from same client apply in issue order | Per-session queue on server |
| **Eventual** | Given no new writes, all replicas converge | DNS, S3, Cassandra default |

### 2.2 Anomalies you must recognize

```
READ-YOUR-WRITES VIOLATION               MONOTONIC READ VIOLATION
──────────────────────────               ────────────────────────
  User updates profile name.               User sees "20 likes".
  Refreshes page → sees old name.          Refreshes → sees "15 likes".
  (Cause: read hit lagging replica)        (Cause: hit different replicas)

CAUSAL VIOLATION                         WRITE-WRITE CONFLICT
─────────────────                         ────────────────────
  Alice: "I lost my phone"                 Two replicas each accept a write
  Alice: "Someone found it!"               to the same key concurrently.
  Bob sees order reversed → confused.      Both are "latest" — who wins?
```

---

## 3. Failure Modes

> **Deep dive:** [`DistributedSystems/03-FailureModes.md`](./DistributedSystems/03-FailureModes.md)

```
┌──────────────────────────────────────────────────────────────┐
│                   TYPES OF FAILURES                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CRASH FAILURE (fail-stop)                                   │
│  ─────────────                                               │
│  Node stops responding permanently. Detectable via           │
│  heartbeat timeout.                                          │
│  Solution: Replicas, failover, leader re-election.           │
│                                                              │
│  OMISSION FAILURE                                            │
│  ────────────────                                            │
│  Node fails to send or receive some messages (dropped        │
│  packets, half-open TCP). Node may be alive but unreachable. │
│  Solution: Retries (idempotent!), timeouts, circuit breakers.│
│                                                              │
│  TIMING FAILURE                                              │
│  ──────────────                                              │
│  Node responds too late (beyond timeout). Common in GC       │
│  pauses, CPU throttling, disk stalls.                        │
│  Solution: Timeouts, SLAs, back-pressure, hedged requests.   │
│                                                              │
│  RESPONSE FAILURE                                            │
│  ────────────────                                            │
│  Node responds but with wrong (non-malicious) value due to   │
│  bit flips, bad disk, corrupted memory.                      │
│  Solution: Checksums, ECC memory, end-to-end hashing.        │
│                                                              │
│  BYZANTINE FAILURE (Hardest)                                 │
│  ─────────────────────────                                   │
│  Node sends ARBITRARY / MALICIOUS data, possibly different   │
│  values to different peers.                                  │
│  Solution: BFT consensus (PBFT, Tendermint, HotStuff).       │
│  Most intra-datacenter designs ignore this (trusted network).│
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.1 The failure hierarchy

```
            ┌─ Byzantine  (can lie)
            │      │
            │      ├─ Response      (wrong answer)
            │      │      │
   HARDER   │      │      ├─ Timing (too slow)
     ▲      │      │      │    │
     │      │      │      │    ├─ Omission   (drops messages)
     │      │      │      │    │    │
     │      │      │      │    │    └─ Crash (stops cleanly)
   EASIER   │      │      │    │
            │      │      │    │   Each one is a strict subset of the next.
            │      │      │    │   If you handle Byzantine, you handle all.
```

### 3.2 The "gray failure" trap (real-world nastiness)

Most prod outages aren't clean crashes — they're **slow or partial** failures:

```
  Symptoms                               What it actually is
  ────────                               ───────────────────
  p99 latency 50× baseline               → NIC packet drops, silent
  Node answers pings but not RPCs        → App thread pool exhausted
  5% of writes silently lost             → Disk controller firmware bug
  Node leaves, rejoins, leaves, rejoins  → Flapping; gossip never converges

  Defense: use application-level health checks, not just TCP pings.
```

---

## 4. Failure Detection

> **Deep dive:** [`DistributedSystems/04-FailureDetection.md`](./DistributedSystems/04-FailureDetection.md)

### 4.1 Heartbeat (simplest)

```
  ┌──────┐  heartbeat   ┌──────┐  heartbeat   ┌──────┐
  │Node A│──every 1s───►│Node B│──every 1s───►│Node C│
  └──────┘              └──────┘              └──────┘
       ▲                                          │
       └──────────── every 1s ────────────────────┘

  Missed 3 consecutive → SUSPECT
  Missed N seconds     → DEAD

TRADE-OFF:
  Short timeout → fast detection, but more false positives
  Long timeout  → accurate, but slow to react
```

### 4.2 Phi Accrual Failure Detector

Instead of a yes/no answer, produce a **suspicion level `Φ`** that rises with
time since the last heartbeat, based on the *distribution* of past inter-arrival times.

```
  Φ(t) = -log10(P(heartbeat later than t | history))

  Φ ≈ 1   → ~10% chance of mistake
  Φ ≈ 2   → ~1% chance
  Φ ≈ 8   → ~10⁻⁸ chance  (very confident it's dead)

  App sets its own threshold per criticality.
  Used by: Cassandra, Akka Cluster.
```

### 4.3 Gossip (SWIM) — scalable membership

Point-to-point heartbeats are O(N²). **Gossip** reduces it to O(log N) convergence.

```
BASIC GOSSIP:
  Each node, every T ms, picks K random peers and shares its
  membership view (list of {node, status, incarnation#}).
  Conflicting entries resolved by higher incarnation number.

  Node A ──"B=alive(v5), C=alive(v3)"───► Node D
  Node E ──"B=alive(v5), C=DEAD(v10)"───► Node D
                                          ▲
                                          └─ v10 > v3, so D updates C=DEAD.

  Information reaches the whole cluster in O(log N) rounds.

SWIM (Scalable Weakly-consistent Infection-style Membership):
  Instead of direct probes only, also use INDIRECT probes:

  A ──ping──► B  (no reply)
  A ──"please ping B"──► {C, D, E}
       C, D, E ──ping──► B
                          └─ if ANY succeeds, B is alive
                          └─ if NONE do, A marks B SUSPECT, gossips it

  Reduces false positives from A's bad network path.
  Used by: Consul, HashiCorp Serf, Uber Ringpop.
```

---

## 5. Network Partitions & Split-Brain

> **Deep dive:** [`DistributedSystems/05-NetworkPartitions.md`](./DistributedSystems/05-NetworkPartitions.md)

### 5.1 Anatomy of a partition

```
BEFORE PARTITION                    DURING PARTITION
────────────────                    ─────────────────
  ┌──┐  ┌──┐  ┌──┐                  ┌──┐  ┌──┐ │ ┌──┐
  │A │──│B │──│C │                  │A │──│B │ │ │C │
  └──┘  └──┘  └──┘                  └──┘  └──┘ │ └──┘
                                                │
                                         network split

  If A+B can still form majority → they keep serving (safe).
  C is isolated → must REFUSE writes (or risk split-brain).
```

### 5.2 Split-Brain

When *both* sides of a partition think they're in charge:

```
                    ┌─── LEADER A ───► clients in zone 1 write "x=5"
    ╳ partition ╳
                    └─── LEADER B ───► clients in zone 2 write "x=7"

    Partition heals → two conflicting histories. Data divergence.
```

**Prevention techniques:**

```
1. QUORUM WRITES           majority rule — only one side can have > N/2
2. LEASE + FENCING TOKEN   see below
3. STONITH                 "Shoot The Other Node In The Head"
                           (IPMI power off, disk fencing)
4. WITNESS / ARBITER NODE  tiebreaker for 2-DC deployments
```

### 5.3 Fencing Tokens

A lease alone isn't enough — GC pauses can cause zombie leaders.

```
WITHOUT FENCING:
  Client A acquires lock, GC pause 30s, lease expires.
  Lock service gives lock to Client B.
  Client A wakes up, still thinks it has the lock, writes to storage.
  ─ Lost updates, corrupted data.

WITH FENCING TOKENS:
  Each grant returns a monotonically increasing token (33, 34, 35...).

     Client A: got lock with token 33, then pauses
     Client B: got lock with token 34, writes with token=34 ───► Storage
     Client A: wakes up, writes with token=33 ───► Storage
                                                   ▲
                                                   └─ REJECTS (33 < 34 latest)
```

Storage must enforce: *reject any write whose token is less than the latest seen.*

---

## 6. Quorum

> **Deep dive:** [`DistributedSystems/06-Quorum.md`](./DistributedSystems/06-Quorum.md)

A quorum ensures consistency in a replicated system.

```
N = Total replicas
W = Write quorum (nodes that must acknowledge write)
R = Read quorum (nodes that must respond to read)

RULE: W + R > N  → Every read overlaps every write → Strong consistency
ALSO: W > N/2    → No split-brain writes

Example with N=3:

  STRONG CONSISTENCY: W=2, R=2 (2+2 > 3 ✓)
  ┌──────┐ ┌──────┐ ┌──────┐
  │Node 1│ │Node 2│ │Node 3│
  │ W ✓  │ │ W ✓  │ │      │   Write: 2 out of 3 ACK
  │ R ✓  │ │      │ │ R ✓  │   Read : 2 out of 3 respond
  └──────┘ └──────┘ └──────┘   → overlap on Node 1 → always fresh

  EVENTUAL CONSISTENCY: W=1, R=1 (1+1 ≤ 3 ✗)
  Fast, highly available, but reads may be stale.

  READ-HEAVY: W=3, R=1 (3+1 > 3 ✓)
  Slow writes, cheap reads. Any single node is always fresh.

  WRITE-HEAVY: W=1, R=3 (1+3 > 3 ✓)
  Fast writes, every read must contact all replicas.
```

### 6.1 Sloppy Quorum + Hinted Handoff

Classic quorum refuses writes when fewer than `W` replicas are up. Dynamo/Cassandra
relax this for availability:

```
Home replicas for key "k": {N1, N2, N3}
N1 is down.

CLIENT ──write k=v──► N2 ✓  (home)
                   ──write k=v──► N3 ✓  (home)
                   ──write k=v──► N4 ✓  (sloppy — stand-in for N1)
                                  │
                                  └─ N4 stores with HINT: "deliver to N1 on recovery"

When N1 comes back → N4 replays hinted writes to N1.

Trade-off: preserves availability during failures but temporarily
weakens "strong quorum" guarantees.
```

---

## 7. Replication Strategies

> **Deep dive:** [`DistributedSystems/07-ReplicationStrategies.md`](./DistributedSystems/07-ReplicationStrategies.md)

```
SINGLE-LEADER REPLICATION:
  All writes → Leader → Replicates to followers
  Reads from any node (may be stale)
  Used by: PostgreSQL, MySQL, MongoDB (default), Redis

MULTI-LEADER REPLICATION:
  Multiple leaders accept writes → sync between leaders
  Conflict resolution needed (LWW, merge, CRDT)
  Used by: Multi-region MySQL, CockroachDB, CouchDB

LEADERLESS REPLICATION:
  Any node accepts reads/writes → quorum-based
  Conflict resolution via vector clocks + read repair
  Used by: Cassandra, DynamoDB, Riak
```

### 7.1 Single-Leader in detail

```
    ┌────────┐      write        ┌──────────┐   async/sync   ┌──────────┐
    │ Client │────────────────►  │  LEADER  │ ─────────────► │ Follower │
    └────────┘                   └──────────┘                 └──────────┘
        │                              │                          ▲
        │       read (stale ok)        │          replicate       │
        └──────────────────────────────┼──────────────────────────┘
                                       ▼
                                  ┌──────────┐
                                  │ Follower │
                                  └──────────┘

  Sync vs Async replication:
    SYNC   → leader waits for follower ACK before returning success.
             Safer (no data loss on leader crash) but slower, and
             a slow follower blocks writes.
    ASYNC  → leader returns immediately, replicates in background.
             Faster, but data loss possible if leader crashes
             before replication catches up.
    SEMI-SYNC → wait for AT LEAST ONE follower ACK (MySQL default).
```

### 7.2 Multi-leader (active-active)

```
           ┌────── Region: US-East ───────┐    ┌──── Region: EU-West ────┐
           │                               │    │                          │
   client ─┼──► Leader_US ─────────────────┼────┼──► Leader_EU ◄── client │
           │       │                       │    │       │                  │
           │       ▼                       │    │       ▼                  │
           │   Follower_US                 │    │   Follower_EU            │
           └───────────────────────────────┘    └──────────────────────────┘
                          ▲                               │
                          └──── async cross-region sync ──┘

  Pros: local writes in every region, survives region outage.
  Cons: write conflicts are possible → need deterministic resolution.
```

### 7.3 Comparison cheat-sheet

```
┌─────────────────┬────────────┬──────────┬──────────────┬──────────────┐
│ Strategy        │ Writes     │ Reads    │ Consistency  │ Complexity   │
├─────────────────┼────────────┼──────────┼──────────────┼──────────────┤
│ Single-Leader   │ 1 node     │ Any node │ Strong (sync)│ Low          │
│ Multi-Leader    │ N leaders  │ Any node │ Eventual     │ High         │
│ Leaderless      │ Any node   │ Quorum   │ Tunable      │ Medium       │
└─────────────────┴────────────┴──────────┴──────────────┴──────────────┘
```

---

## 8. Consistent Reads & Anti-Entropy

> **Deep dive:** [`DistributedSystems/08-AntiEntropy.md`](./DistributedSystems/08-AntiEntropy.md)

### 8.1 Read Repair

```
Client reads from 3 replicas → gets different values.

  Client ──read──► Node A: v2, ts=100 (latest)
  Client ──read──► Node B: v1, ts= 90 (stale)
  Client ──read──► Node C: v2, ts=100 (latest)

  Client (or coordinator) ──repair──► Node B: "update to v2, ts=100"

  Foreground (blocking):  must finish before returning to caller
  Background (async):     return to caller first, repair in the background
```

### 8.2 Anti-Entropy with Merkle Trees

Reconciling replicas by exchanging every key is O(N). Merkle trees make it
O(log N) by comparing hashes top-down.

```
                          ROOT h(AB,CD)
                         ╱               ╲
                  h(A,B)                 h(C,D)
                 ╱      ╲                ╱      ╲
             h(A)       h(B)          h(C)       h(D)
             key1       key2          key3       key4

  Replica 1 and Replica 2 each build such a tree.
  1. Compare roots. Same? → all data matches, done.
  2. Different? → recurse into the children whose hashes differ.
  3. Keep walking down → only diverged keys get exchanged.

  Used by: Cassandra, DynamoDB, Riak, Git (blobs/trees).
```

### 8.3 Write-path techniques

```
HINTED HANDOFF        Temporarily store writes for an unreachable replica.
READ REPAIR           Fix stale replicas *during* reads.
ACTIVE ANTI-ENTROPY   Periodic background Merkle-tree compare & fix.
```

All three are usually combined — none is sufficient alone.

---

## 9. Conflict Resolution

> **Deep dive:** [`DistributedSystems/09-ConflictResolution.md`](./DistributedSystems/09-ConflictResolution.md)

When multiple replicas accept concurrent writes (multi-leader, leaderless,
offline clients), you *will* get conflicts. Strategies:

### 9.1 Last-Writer-Wins (LWW)

```
  Replica A: set(key, "red",  ts=100)
  Replica B: set(key, "blue", ts=102)

  After sync: both converge to "blue" (higher ts wins).

  Problem: clock skew → "wrong" winner. And silent data loss.
  Use only when the lost update is acceptable (e.g., ephemeral session data).
```

### 9.2 Application-level merge (siblings)

Dynamo/Riak return **all concurrent versions** to the client and let it merge.

```
  GET cart_123
  ← [ {"items":["a","b"]}, {"items":["a","c"]} ]   two siblings

  Application merges → {"items":["a","b","c"]}  (union)
  PUT cart_123 with new value + context (vector clock) → conflict resolved.

  This is how Amazon's original shopping cart never lost a "add to cart".
```

### 9.3 CRDTs (Conflict-free Replicated Data Types)

Mathematical structures where concurrent operations *always* commute and merge
deterministically with no coordination.

```
  G-Counter (grow-only counter)
  ────────────────────────────
    Each replica has its own slot. Total = sum of all slots.
    Merge = element-wise max.

    A: [A:3, B:0]     +1 local → [A:4, B:0]
    B: [A:0, B:2]     +1 local → [A:0, B:3]
    merge → [A:4, B:3]  total = 7

  Other CRDTs:
    PN-Counter  (inc + dec)         used in Riak
    LWW-Register                    ClickHouse, DynamoDB
    OR-Set       (add/remove sets)  Redis CRDT, Akka
    RGA / LSEQ   (text collab)      Figma, Google Docs, Automerge
```

Rule: use CRDTs when *high availability and automatic merging* matter more
than expressive power.

---

## 10. State Machine Replication & the Replicated Log

> **Deep dive:** [`DistributedSystems/10-StateMachineReplication.md`](./DistributedSystems/10-StateMachineReplication.md)

The key idea behind Raft, Paxos, Kafka, databases, and pretty much every
system that survives node failure.

```
INSIGHT:  If every replica applies the SAME deterministic operations
          in the SAME order, they end up in the SAME state.

      ┌─────────────── REPLICATED LOG (ordered, append-only) ─────────────┐
      │  [idx 0] set x=1                                                    │
      │  [idx 1] set y=2                                                    │
      │  [idx 2] incr x                                                     │
      │  [idx 3] set y=5                                                    │
      └──────────────────────────────────────────────────────────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
      ┌───────┐           ┌───────┐           ┌───────┐
      │ SM_A  │           │ SM_B  │           │ SM_C  │   all apply in order,
      │ x=2   │           │ x=2   │           │ x=2   │   all converge to
      │ y=5   │           │ y=5   │           │ y=5   │   identical state.
      └───────┘           └───────┘           └───────┘

  The hard part is *agreeing on the order*.  That's consensus (Raft/Paxos).
```

### 10.1 Write-Ahead Log (WAL)

Every mutation is first appended to a durable log, then applied to state.
This gives **crash recovery** and **replication** in one mechanism.

```
  client ──put(x,5)──► server
                          │
                          ▼
                     ┌─────────┐     1. append "put x=5" to WAL + fsync
                     │  WAL    │     2. return success to client
                     └─────────┘     3. apply to in-memory state later
                          │          4. on crash: replay WAL
                          ▼
                     ┌─────────┐
                     │ State   │
                     └─────────┘

  Properties you get "for free":
    - Durability       (fsync before ack)
    - Replication      (ship the log to followers)
    - Snapshots        (periodically checkpoint + truncate log)
    - Point-in-time recovery
```

Used by: every serious DB (Postgres WAL, MySQL binlog, Kafka, etcd Raft log, HBase HLog).

---

## 11. Delivery Semantics & Idempotency

> **Deep dive:** [`DistributedSystems/11-DeliverySemantics.md`](./DistributedSystems/11-DeliverySemantics.md)

Any time you retry across the network, you're choosing a semantics:

```
AT-MOST-ONCE                 AT-LEAST-ONCE              EXACTLY-ONCE
────────────                 ─────────────              ────────────
fire & forget                retry until ACK            retry + dedupe
may lose messages            may duplicate              appears once
e.g. UDP metrics             e.g. most queues           e.g. Kafka EOS,
                                                            Stripe idempotency
     ▲                             ▲                          ▲
     │ simplest, weakest           │ most common              │ strongest,
     │                             │                          │ requires
     │                             │                          │ dedup store
     └─ lowest overhead            └─ needs idempotent work   └─ highest cost
```

### 11.1 Idempotency patterns

```
1. IDEMPOTENCY KEY (request-scoped UUID)

     client ──POST /charge  (key=abc123, amount=$10)──► server
            ──POST /charge  (key=abc123, amount=$10)──► server   ← retry

     server:
       if seen(key) → return cached response
       else        → do work, store (key → response) for 24h

   Used by: Stripe, Square, PayPal, AWS SDKs.

2. CONDITIONAL WRITES / CAS

     PUT /item?version=7    ← succeeds only if current version is 7
                            ← returns 409 Conflict otherwise

3. DEDUP WINDOW

     Track recent message IDs in Redis/Bloom filter for N minutes.
```

### 11.2 "Exactly-once" honestly

True end-to-end exactly-once doesn't exist in general (Two Generals Problem).
What systems like Kafka EOS actually provide:

```
Producer-side:  idempotent producer (seq# per partition) + transactions
Consumer-side:  commit offsets atomically with output writes

 → effectively-exactly-once INSIDE the Kafka boundary.
 → if your side-effect escapes (send email, call Stripe),
   you still need an idempotency key on the *external* side.
```

---

## 12. Back-Pressure, Retries & Thundering Herd

> **Deep dive:** [`DistributedSystems/12-BackPressureAndRetries.md`](./DistributedSystems/12-BackPressureAndRetries.md)

### 12.1 Retry storms

```
   100 clients ──► service (fails briefly)
                        │
                        ▼
   Every client retries 3x with no backoff
                        │
                        ▼
   400 requests/sec slam the now-recovering service → crashes it AGAIN.
```

### 12.2 Mitigations

```
EXPONENTIAL BACKOFF + JITTER
  Wait 100ms · 2^n · random(0.5, 1.5)  before retry
  Prevents synchronized retry waves.

CIRCUIT BREAKER
   CLOSED  ── errors >= threshold ──► OPEN  (fail fast, no calls)
      ▲                                  │
      │                         after cool-down
      │                                  ▼
   HALF-OPEN  ◄── allow trickle of probes; if OK → CLOSED

LOAD SHEDDING
  Under overload, drop non-critical requests early (before they queue).
  Return 503 with Retry-After.

HEDGED REQUESTS
  If request hasn't returned in p95 time, send SECOND request to a replica.
  Use whichever replies first. Kills tail latency at ~5% extra load.
  Classic "The Tail at Scale" technique (Jeff Dean).
```

### 12.3 Back-pressure

The consumer signals the producer to slow down instead of silently dropping.

```
  fast producer                 slow consumer
  ┌─────────────┐   queue     ┌─────────────┐
  │             │ ─────────►  │             │
  │             │              │             │
  └─────────────┘              └─────────────┘
         ▲                           │
         └──────── "slow down" ──────┘
                   (or block sends)

  Mechanisms: TCP windows, Reactive Streams `request(n)`,
              bounded channels, credits in RSocket/gRPC flow control.
```

---

## 13. Service Discovery & Membership

> **Deep dive:** [`DistributedSystems/13-ServiceDiscovery.md`](./DistributedSystems/13-ServiceDiscovery.md)

Who is alive and how do I find them?

```
CLIENT-SIDE DISCOVERY                     SERVER-SIDE DISCOVERY
─────────────────────                     ─────────────────────
      Registry (Consul/etcd/ZK)            Registry
          ▲                                   ▲
          │ heartbeat                         │ heartbeat
          │                                   │
  Services register, clients query      Services register,
  registry directly; clients do         clients hit a Load Balancer
  load balancing.                       which queries the registry.

  Examples: Netflix Eureka,              Examples: AWS ALB + target
            Ribbon, older Finagle.                  groups, k8s Service.
```

### 13.1 Typical registration flow

```
  1. Service starts → POST /register (name, ip, port, health URL)
  2. Every 10s    → PUT  /heartbeat  (extend TTL)
  3. On shutdown   → DELETE /deregister
  4. If heartbeat misses 3× TTL → registry marks it DOWN
  5. Clients/LB refresh list every few seconds (or via watch/long-poll)
```

---

## 14. Key Takeaways for Interviews

1. **No global clock** — use logical clocks (Lamport, vector, HLC) or TrueTime for ordering.
2. **Quorum (W + R > N)** gives you tunable consistency; `W > N/2` prevents split-brain.
3. **Gossip + SWIM** for failure detection at scale; Phi Accrual for soft detection.
4. **Single-leader** is simplest; **leaderless** for highest availability; **multi-leader** only if you really need multi-region active-active.
5. **Read repair + hinted handoff + anti-entropy** together keep replicas in sync.
6. **Fencing tokens** prevent zombie-leader write after GC pause — always mention them when discussing locks.
7. **Byzantine failures** are ignored in most intra-DC system designs (trusted network).
8. Always mention **network partitions** as a first-class failure case and how you'd respond (AP vs CP choice).
9. **Idempotency keys** turn at-least-once into effectively-exactly-once for external side effects.
10. **Exponential backoff with jitter + circuit breakers** prevent retry storms. State this *explicitly* in interviews.

---

## 15. Common Interview Follow-Ups

```
Q: "Two clients write to the same key at the same millisecond. Who wins?"
   → Mention LWW with tiebreaker (node id), vector clocks for
     concurrency detection, or CRDTs to avoid the question entirely.

Q: "Your leader GC-pauses for 30 seconds. What happens?"
   → Followers time out, re-elect. Old leader may come back thinking
     it's still leader → split-brain. Defense: fencing tokens.

Q: "How do you detect a dead node in a 1000-node cluster?"
   → Not all-to-all heartbeat (O(N²)). Use gossip/SWIM:
     random peer probes + indirect probes + Phi Accrual.

Q: "A client retries a payment 3 times due to timeouts. How many charges?"
   → Without idempotency key: up to 3. With idempotency key: exactly 1.
     Server dedupes by key for a window (e.g., 24h).

Q: "Strong vs eventual consistency — when do you pick which?"
   → Strong when invariants MUST hold (balance ≥ 0, unique usernames).
     Eventual when user experience > freshness (likes, feed counts, DNS).

Q: "Why do you always see odd cluster sizes (3, 5, 7)?"
   → N = 2F + 1 tolerates F failures with majority quorum.
     Even sizes add cost without raising F. Plus avoid tie votes.

Q: "Leader election — what keeps two leaders from emerging?"
   → Majority quorum on the vote + increasing term numbers +
     followers only accept writes from the current-term leader.
     (See Raft deep-dive in 02-Consensus.md.)
```

---

> **Next read:** [02-Consensus.md](./02-Consensus.md) — how Paxos, Raft, and ZAB
> actually implement the "agree on a single value" part sketched here.
