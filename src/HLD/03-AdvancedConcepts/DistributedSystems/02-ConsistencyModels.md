# Consistency Models

> **Difficulty:** Medium-Hard | **Time:** 3 hours | **Priority:** Must Know

A **consistency model** is a contract between a storage system and the
applications using it — "if you do X, you'll see Y." This document walks
the full spectrum from the strongest (linearizability) down to the weakest
(eventual), with diagrams of every important anomaly and how real systems
guarantee or violate each model.

---

## Table of Contents

1. [Why Consistency Is a Trade-off](#1-why-consistency-is-a-trade-off)
2. [The Hierarchy at a Glance](#2-the-hierarchy-at-a-glance)
3. [Linearizability (Strong / Strict)](#3-linearizability)
4. [Sequential Consistency](#4-sequential-consistency)
5. [Causal Consistency](#5-causal-consistency)
6. [Session (Client-centric) Guarantees](#6-session-client-centric-guarantees)
7. [Eventual Consistency](#7-eventual-consistency)
8. [PRAM / FIFO Consistency](#8-pram--fifo-consistency)
9. [Transaction Isolation vs Consistency](#9-transaction-isolation-vs-consistency)
10. [Cost / Latency / Availability Trade-offs](#10-cost--latency--availability-trade-offs)
11. [Real Systems and What They Actually Give You](#11-real-systems-and-what-they-actually-give-you)
12. [Anomaly Gallery](#12-anomaly-gallery)
13. [Interview Q&A](#13-interview-qa)

---

## 1. Why Consistency Is a Trade-off

Every stronger guarantee **costs latency** or **availability** (think PACELC):

```
 STRONGER                                                        WEAKER
    │                                                              │
    │   pays: coordination, quorum RTTs, cross-region round trips  │
    │   gains: simpler application logic, no surprises             │
    ▼                                                              ▼
 Linearizable → Sequential → Causal → Session → Monotonic → Eventual
```

> Rule of thumb: **pick the weakest model your feature can tolerate.**
> Strong consistency is expensive — only pay for it where correctness
> actually depends on it (balances, unique constraints, leader election).

---

## 2. The Hierarchy at a Glance

```
┌──────────────────────────────────────────────────────────────────┐
│                    CONSISTENCY MODEL HIERARCHY                    │
└──────────────────────────────────────────────────────────────────┘

   STRICT / LINEARIZABLE
      │   (real-time order respected, appears as single machine)
      ▼
   SEQUENTIAL                                  ┐
      │   (all nodes see same global order,    │ "Strong"
      │    not necessarily real-time)          │ models
      ▼                                        ┘
   CAUSAL
      │   (causally related ops seen in order; │
      │    concurrent ops may vary)            │ "Middle"
      ▼                                        ┘
   SESSION (RYW, MR, MW, WFR)                  ┐
      │   (per-client guarantees, cheap)       │ "Client-centric"
      ▼                                        ┘
   PRAM / FIFO
      │   (same-source writes preserve order)  │
      ▼                                        │ "Weak"
   EVENTUAL                                    │
      │   (replicas converge given quiescence) │
      ▼                                        ┘
   "No guarantees"   (e.g., cache with no coherence)

 Each level IMPLIES all weaker levels below it.
```

---

## 3. Linearizability

Often called **strong consistency**, **strict consistency**, or **atomic
consistency**. The most intuitive model: *the system behaves as if there
were only one copy of the data and all operations were atomic.*

### 3.1 Formal definition

```
 An execution is LINEARIZABLE if there is a single total order of all
 operations such that:

   (a) Every operation appears to take effect INSTANTANEOUSLY at some
       point between its invocation and its response.
   (b) That total order is consistent with REAL-TIME ORDER:
          if op A completes before op B starts, then A comes before B.
   (c) Each operation observes the effects of all earlier ops in the order.
```

### 3.2 Visual: linearizable vs not

```
 LINEARIZABLE
 ─────────────
 Client 1:  |----write(x=1)----|
 Client 2:                        |----read(x)----|  returns 1 ✓
                                                     (write completed
                                                      before read started)

 NOT LINEARIZABLE (sequential consistency OK but not linearizable)
 ─────────────────────────────────────────────────────────────────
 Client 1:  |----write(x=1)----|
 Client 2:                        |----read(x)----|  returns 0 ✗
                                                     (real-time says 1
                                                      but we got 0)
```

### 3.3 Who provides linearizability?

```
 SYSTEM                          MECHANISM
 ──────                          ─────────
 etcd, Consul, ZooKeeper         Raft / ZAB consensus on every read/write
 Spanner                         Paxos + TrueTime commit-wait
 DynamoDB strongly-consistent    Quorum (W+R > N) against leader replica
 Postgres single-leader          Reads from leader only
 Redis + Redlock                 (approximately — don't rely on it)
 S3 (since 2020)                 Strong read-after-write consistency
```

### 3.4 Cost

```
 Minimum latency per write = 1 round trip to a majority of replicas.

   3-replica Raft cluster:
      client → leader:      1 RTT
      leader → followers:   1 RTT  (fsync + ack)
   Total: 2 RTTs per write (1 RTT if client is the leader)

 Cross-region linearizability → 100+ ms per write.
 That's why AP databases exist.
```

---

## 4. Sequential Consistency

Lamport's original 1979 definition. Weaker than linearizable because it
doesn't require real-time order.

### 4.1 Definition

```
 Every client sees operations in SOME total order, and all clients
 agree on THAT order — but the order may not match real-time.
```

### 4.2 Why it's weaker

```
 Client 1:  write(x=1)   -- finishes at t=10
 Client 2:                              read(x)  -- at t=20, returns 0

 In LINEARIZABLE systems, that read MUST return 1.
 In SEQUENTIAL systems, it could return 0 as long as ALL CLIENTS
 agree on some order like: [read, write] or [write, read] — they
 just have to agree.
```

### 4.3 Canonical example

Single-leader replication with **asynchronous** reads from followers:

```
                    LEADER                FOLLOWER-1      FOLLOWER-2
  Write x=1 ───►    apply ────────►        apply           apply
                                           (immediately)   (10 s lag)

  Client reads from Follower-2 while it still says x=0.
  All replicas APPLY writes in the same order → sequentially consistent.
  But real-time violated → NOT linearizable.
```

### 4.4 When is this enough?

```
 Good for: Display counters, dashboards, user-agnostic feeds —
           where "same picture for everyone, eventually" is fine.
 Bad for:  Anything that compares real time across clients
           (e.g., "your transfer came AFTER his withdrawal").
```

---

## 5. Causal Consistency

*Cause-and-effect* relationships (happens-before) are preserved;
everything else is unordered.

### 5.1 The rule

```
 If operation A causally precedes operation B (A → B),
 then ALL replicas apply A before B.

 If A ∥ B (concurrent), replicas may apply them in any order,
 possibly different orders at different replicas.
```

### 5.2 Visual

```
 Required causality chain:
    write(likes=1) ──► read(likes)=1 ──► write(total_likes=5)
                                              │
                                              ▼
   Any client reading total_likes=5 MUST also see likes=1.

 Concurrent writes:
    Alice:  write(post_A)
    Bob:    write(post_B)     (independent, no causal chain)
    Different clients may see them in different orders → fine.
```

### 5.3 Session examples where causal matters

```
 EMAIL CLIENT
    1. User sends email to boss
    2. Then sends sarcastic email to friend saying "sent it, done"
    3. Friend (on different replica) sees "done" but not the boss email.
        → Causal violation. Mortifying.

 SOCIAL MEDIA
    1. Alice: "I lost my phone!"
    2. Alice: "(Found it — everyone ignore)"
    3. Bob sees step 2 but not step 1 → "found what?"
```

### 5.4 How it's implemented

- **Version vectors** per write; a replica delays applying a write until
  all causally-earlier writes are visible.
- **Causal tokens** the client attaches to every request (MongoDB's
  `afterClusterTime`, Cassandra's per-session token).
- **COPS / Eiger** — academic systems that provide causal+ consistency
  at datacenter scale.

---

## 6. Session (Client-centric) Guarantees

The **Bayou papers (1994)** introduced four per-session guarantees that
dramatically improve UX without the cost of full causal consistency.

```
 ┌───────────────────────────────────────────────────────────────┐
 │                FOUR SESSION GUARANTEES                         │
 ├───────────────────────────────────────────────────────────────┤
 │                                                               │
 │  1. READ-YOUR-WRITES   (RYW)                                  │
 │     Once you write v, YOU read v or newer.                    │
 │     Prevents: "I posted but don't see my post."               │
 │                                                               │
 │  2. MONOTONIC READS    (MR)                                   │
 │     Once you read v, you never read older than v.             │
 │     Prevents: "The counter went backwards."                   │
 │                                                               │
 │  3. MONOTONIC WRITES   (MW)                                   │
 │     Your writes apply in the order you issued them.           │
 │     Prevents: "My correction was overwritten by old value."   │
 │                                                               │
 │  4. WRITES FOLLOW READS  (WFR)                                │
 │     If you read v1 then write v2, replicas that see v2        │
 │     must have already seen v1.                                │
 │     Prevents: "Reply appears before the message it replies to."│
 │                                                               │
 └───────────────────────────────────────────────────────────────┘

 Together these four ≈ causal consistency for one client.
```

### 6.1 Read-Your-Writes anomaly

```
 NO RYW:
    User updates profile photo → reads from replica that lags → old photo.
    User panics and updates again → two writes, still sees old.

 WITH RYW:
    Client stores last-write token.
    Reads include "at-least token T"; replica waits or redirects.
```

### 6.2 Monotonic Reads anomaly

```
       Leader:    v1 → v2 → v3 → v4
                    \       \      \
       Rep-A:     v1 → v2 → v3
       Rep-B:     v1 → v2
       Rep-C:     v1

  Client round-robins:
     Read1 → A: v3
     Read2 → C: v1     ← TIME TRAVELLED BACKWARDS
     Read3 → B: v2

  MR fix: stick to one replica per session, OR include a version
          token and reject if replica is behind it.
```

### 6.3 Monotonic Writes anomaly

```
  Client issues:
     W1: set name="Alice"
     W2: set name="Alice B." (correction)

  If routed to different leaders (multi-leader) without MW:
     Replica X applies W2 then W1 → final "Alice"
     Replica Y applies W1 then W2 → final "Alice B."

  MW fix: serialize writes from a session through one coordinator,
          or tag with per-session seq numbers.
```

### 6.4 Writes-Follow-Reads anomaly

```
 Thread on social media:
    1. Alice reads "Bob: where's the meeting?"
    2. Alice writes a reply "Conference room 3"

 Replica that accepts Alice's reply must ALREADY have Bob's message.
 Otherwise users on that replica see Alice's orphan reply first.

 WFR fix: client attaches the version it read;
          replicas accept writes only after they've applied that version.
```

---

## 7. Eventual Consistency

The weakest "useful" model: if no new writes are made to an object,
eventually all replicas will return the same value.

```
 WRITE(x=5) happens on Replica A
   │
   ▼
 Replica B still reads x=3  (or nothing)
 Replica C still reads x=2
   │
   │  ... background replication ...
   ▼
 Eventually (seconds to minutes later):
   A, B, C all read x=5
```

### 7.1 What "eventual" really means

```
 Formally:  if no new writes, after some FINITE but UNSPECIFIED time,
            every replica converges to the same value.

 In practice:
   - DNS propagation: minutes to hours
   - S3 (pre-2020): up to several seconds
   - Cassandra: usually < 1 s within a DC, 10-100 s across DCs
```

### 7.2 The anomalies you accept

```
 - Stale reads (any duration)
 - Reading your own writes back as missing
 - Different clients seeing completely different values simultaneously
 - Reordered updates — need explicit conflict resolution (LWW, CRDTs)
```

### 7.3 When it's fine

```
 ✓ DNS, CDN invalidation, S3 metadata
 ✓ Like/view counters (approximate OK)
 ✓ Social feeds — users accept "eventual"
 ✓ Shopping cart — ADD operations merge well

 ✗ Account balances that must ≥ 0
 ✗ Inventory where overselling is disaster
 ✗ Leader election / distributed locks
 ✗ Anything with a unique-constraint requirement
```

---

## 8. PRAM / FIFO Consistency

**P**ipelined **RAM**: writes from **one** process appear in program
order to every other process, but writes from DIFFERENT processes may
be seen in different orders by different observers.

```
 Process P1 writes:  a, b, c   (in that order)
 Process P2 writes:  x, y, z   (in that order)

 Observer 1 might see:   a, x, b, y, c, z
 Observer 2 might see:   x, a, y, b, z, c       ← allowed
 Observer 3 might see:   a, b, c, x, y, z       ← also allowed

 What's NOT allowed:    b, a, ...     (out-of-order within a process)
```

Weaker than causal (no cross-process causal chain) but still preserves
single-process order. Mostly of theoretical interest; nothing modern
advertises "PRAM" but many in-memory replicated systems accidentally
give you this when they get lazy.

---

## 9. Transaction Isolation vs Consistency

These are **orthogonal** axes, but interviews often mix them up.

```
 CONSISTENCY            about: WHICH version of a single key
 (model)                       replicas agree on and when
                                
 ISOLATION              about: how CONCURRENT TRANSACTIONS interact,
 (level)                       can one see uncommitted / partial state
                               of another?
```

### 9.1 Isolation ladder (weakest → strongest)

```
 Read Uncommitted       dirty reads allowed
       │
       ▼
 Read Committed         no dirty reads
       │                (but non-repeatable reads possible)
       ▼
 Repeatable Read        same row returns same value within txn
       │                (phantom rows still possible)
       ▼
 Snapshot Isolation     txn sees a consistent snapshot;
       │                write-skew anomaly still possible
       ▼
 Serializable           as if txns ran one at a time
       │
       ▼
 Strict Serializable    serializable + linearizable commits
 (= linearizable + serializable)
```

### 9.2 Classic anomalies

```
 DIRTY READ            T1 sees T2's uncommitted change.
 NON-REPEATABLE READ   T1 reads key, T2 commits, T1 reads again → diff.
 PHANTOM READ          T1 queries range, T2 inserts row, T1 re-queries.
 LOST UPDATE           T1 and T2 both read-modify-write, one gets lost.
 WRITE SKEW            T1/T2 read overlapping set, each updates a
                       disjoint part based on invariant → invariant broken.
```

### 9.3 Write-skew (snapshot isolation doesn't save you)

```
 Invariant: at least ONE doctor must be on call.

 Snapshot:  Alice=oncall, Bob=oncall

 Txn T1 (Alice): if any other on call, set Alice=off    → commits
 Txn T2 (Bob):   if any other on call, set Bob=off      → commits

 Both txns saw the same snapshot (both oncall) → both committed.
 Final state: nobody on call.   INVARIANT VIOLATED.

 Fix: Serializable isolation (SELECT ... FOR UPDATE, SSI, or 2PL).
```

---

## 10. Cost / Latency / Availability Trade-offs

PACELC (If **P**artitioned, choose between **A** and **C**;
**E**lse, choose between **L**atency and **C**onsistency):

```
┌──────────────┬──────────────┬──────────────┬──────────────┐
│ System       │ If partition │ No partition │ Notes        │
├──────────────┼──────────────┼──────────────┼──────────────┤
│ DynamoDB     │ PA           │ EL           │ AP, fast     │
│ Cassandra    │ PA           │ EL           │ Tunable      │
│ MongoDB      │ PA or PC     │ EC           │ Depends on W │
│ PostgreSQL   │ PC (stops)   │ EC           │ Sync replica │
│ Spanner      │ PC           │ EC           │ Chubby/Paxos │
│ Cockroach    │ PC           │ EC           │ Raft + HLC   │
│ Redis (core) │ PA           │ EL           │ Async repl   │
│ etcd / ZK    │ PC           │ EC           │ Raft / ZAB   │
└──────────────┴──────────────┴──────────────┴──────────────┘

 Note: "PC" systems give up AVAILABILITY during partitions.
 There is no free lunch — know what your system does.
```

---

## 11. Real Systems and What They Actually Give You

```
┌─────────────────┬─────────────────────────────────────────────────┐
│ System          │ Default consistency                              │
├─────────────────┼─────────────────────────────────────────────────┤
│ etcd            │ Linearizable (Raft)                              │
│ ZooKeeper       │ Sequential (reads from follower) +               │
│                 │   linearizable writes + FIFO client order        │
│ Spanner         │ Strict serializable (TrueTime)                   │
│ CockroachDB     │ Serializable (HLC + Raft)                        │
│ Postgres        │ Read Committed (default), Serializable opt-in    │
│ MySQL (InnoDB)  │ Repeatable Read (default)                        │
│ MongoDB         │ Causal session available; strong with majority   │
│ Cassandra       │ Tunable: ONE, QUORUM, ALL (per query)            │
│ DynamoDB        │ Eventual or Strongly consistent (per API call)   │
│ Riak            │ Eventual + vector-clock siblings                 │
│ Redis           │ Single-node linearizable; cluster is weaker      │
│ Kafka           │ Per-partition total order; cross-partition none  │
│ S3              │ Strong read-after-write (since Dec 2020)         │
│ DNS             │ Eventual (TTL-bounded)                           │
└─────────────────┴─────────────────────────────────────────────────┘
```

---

## 12. Anomaly Gallery

One-liner diagrams to recognize in interviews:

```
 STALE READ                             NON-MONOTONIC READ
 ──────────                             ──────────────────
 W(x=5) ─────► L   R(x)→3 (follower)    R₁(x)=5  R₂(x)=3

 DIRTY READ                             PHANTOM READ
 ──────────                             ────────────
 T1 writes x=5 (not commit)             T1: SELECT count(*)
 T2 reads x=5, then T1 rollbacks        T2: INSERT row
 → T2 saw non-existent state            T1: SELECT count(*) → different

 LOST UPDATE                            WRITE SKEW
 ───────────                            ──────────
 T1 reads x=1, sets x=x+1               Both read shared predicate,
 T2 reads x=1, sets x=x+1               both act independently,
 → final x=2 instead of 3               invariant broken (on-call example)

 CAUSAL VIOLATION                       REAL-TIME VIOLATION
 ─────────────────                      ────────────────────
 Alice: "lost phone"                    Write W1 completes at t=10.
 Alice: "found it"                      Read at t=20 returns pre-W1.
 Bob sees only #2                       (OK for sequential, not linearizable)
```

---

## 13. Interview Q&A

### Q1. "Linearizability vs Serializability — what's the difference?"

> **Linearizability** is about **single objects** (or single operations on
> potentially multiple objects presented as one). It adds *real-time order*
> — if op A completed before B started, A appears before B.
>
> **Serializability** is about **transactions** (multiple operations
> grouped). It guarantees equivalent to *some* serial schedule, but not
> necessarily real-time.
>
> **Strict serializability** = both. That's what Spanner/CockroachDB target.

### Q2. "Can you have linearizability without consensus?"

> For a **single writer** on a single register, yes. For **multi-writer**
> or any replicated state, essentially no — you need either a single-point
> coordination (read/write to one node) or a consensus protocol like Raft.
> Herlihy (1991) showed consensus is **universal** — anything you can
> build with linearizability reduces to consensus.

### Q3. "Why is eventual consistency usually good enough for social media?"

> Users tolerate stale reads for posts by others (~seconds), but will
> notice "my own post disappeared." So platforms add **read-your-writes**
> on top (sticky replicas or version tokens) and leave the rest eventual.
> This gives most of the availability benefit at minimal UX cost.

### Q4. "What anomalies does snapshot isolation NOT prevent?"

> **Write skew.** Two txns each reading the same snapshot and each
> modifying different rows based on that snapshot can together violate
> a multi-row invariant (e.g., the doctors-on-call example).
> **Fix:** serializable isolation, SELECT ... FOR UPDATE, or SSI
> (PostgreSQL's Serializable Snapshot Isolation detects write-skew at commit).

### Q5. "How would you give causal consistency across regions?"

> Each write gets a **vector clock** or **HLC timestamp** that captures
> its causal dependencies. Clients carry a "causal token" in every
> request. Replicas delay applying a write or serving a read until they
> have all causally earlier writes. Implemented by COPS, Eiger, and
> commercial systems like MongoDB causal sessions, Cosmos DB "Consistent
> Prefix" / "Session" levels.

### Q6. "A read from a follower returned older data than a previous read. Why?"

> Non-monotonic read — the second read hit a more-lagged replica.
> Fixes:
>
> 1. **Sticky sessions**: hash client_id → single replica per session.
> 2. **Read tokens**: client remembers max version seen; includes it;
>    replica refuses to answer if behind.
> 3. **Read from leader** for that client.

### Q7. "What's the cheapest consistency model that feels correct to users?"

> The **session guarantees** set (RYW + MR + MW + WFR) with **eventual**
> for cross-client visibility. This is the sweet spot MongoDB, Azure
> Cosmos (Session level), and most modern web stacks aim for.

### Q8. "Your cache returns old values sometimes. Is it still 'consistent'?"

> If you consider it part of the storage system: yes, it's eventually
> consistent. If you want **monotonic reads**, invalidate on write and
> route writes through the cache (or use a cache-aside pattern with
> short TTLs). Always document explicitly what model your cache gives.

---

## 14. Further Reading

- Herlihy & Wing, "Linearizability: a correctness condition for concurrent objects" (1990)
- Lamport, "How to Make a Multiprocessor Computer That Correctly Executes Multiprocess Programs" (1979) — sequential consistency
- Terry et al. "Session Guarantees for Weakly Consistent Replicated Data" (1994) — Bayou
- Lloyd et al. "Don't Settle for Eventual" (2011) — COPS
- Viotti & Vukolić, "Consistency in Non-Transactional Distributed Storage Systems" (2016) — survey of 50+ models
- Adya et al., "Generalized Isolation Level Definitions" (2000) — isolation in multi-object transactions
- Jepsen.io — empirical analysis of real systems' claimed vs. actual consistency

---

> **Previous:** [01-ClocksAndOrdering.md](./01-ClocksAndOrdering.md) ·
> **Next:** [03-FailureModes.md](./03-FailureModes.md)
