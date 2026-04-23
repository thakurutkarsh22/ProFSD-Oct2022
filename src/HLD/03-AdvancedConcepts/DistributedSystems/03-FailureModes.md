# Failure Modes in Distributed Systems

> **Difficulty:** Medium | **Time:** 2 hours | **Priority:** Must Know

"Everything fails, all the time." — Werner Vogels, CTO of Amazon.
The foundational skill in distributed systems is **categorizing**
the ways things break, because the *same* mitigation (retry, replicate,
timeout) works for some failures and makes others *worse*.

---

## Table of Contents

1. [Why Classify Failures at All](#1-why-classify-failures-at-all)
2. [The Failure Hierarchy](#2-the-failure-hierarchy)
3. [Crash Failures](#3-crash-failures)
4. [Omission Failures](#4-omission-failures)
5. [Timing Failures](#5-timing-failures)
6. [Response Failures](#6-response-failures)
7. [Byzantine Failures](#7-byzantine-failures)
8. [Gray Failures (the real prod killer)](#8-gray-failures-the-real-production-killer)
9. [Failure Domains & Blast Radius](#9-failure-domains--blast-radius)
10. [Cascading Failures](#10-cascading-failures)
11. [Metastable Failures](#11-metastable-failures)
12. [Detection, Response, and Mitigation Matrix](#12-detection-response-and-mitigation-matrix)
13. [Interview Q&A](#13-interview-qa)

---

## 1. Why Classify Failures at All

If you treat every failure as "something is broken," your mitigations
collide:

```
 Symptom: a node stops responding.

 If CRASH     : re-elect leader, promote replica, done.
 If OMISSION  : node is alive — a network blip. Retry; don't re-elect.
 If TIMING    : node is alive but slow. Back off; don't re-elect (worse).
 If BYZANTINE : node is responding but lying. Majority-vote; ignore node.

 Wrong mitigation → SPLIT BRAIN, DATA LOSS, or RETRY STORMS.
```

Understanding the category tells you which mechanism to trust.

---

## 2. The Failure Hierarchy

Each category is a **superset** of the ones below it — handling a
stronger category automatically handles the weaker ones.

```
                 ┌─ BYZANTINE         (arbitrary / malicious)
                 │     ▲
                 │     │ strictly superset
                 │     │
                 ├─ RESPONSE          (wrong answer, not malicious)
                 │     ▲
                 │     │
  HARDER ───►    ├─ TIMING            (answer is too slow)
                 │     ▲
                 │     │
                 ├─ OMISSION          (drops some messages)
                 │     ▲
                 │     │
  EASIER ───►    └─ CRASH / FAIL-STOP (cleanly stops)

 Handling Byzantine → handles all.
 Handling crash only → can't survive response/timing bugs.
```

### 2.1 Terminology you'll see

```
 Fail-stop     node crashes AND the crash is detectable.
 Fail-silent   node crashes silently (no "I'm dying" announcement).
 Fail-fast     code halts immediately on invariant violation.
 Fail-safe     system stays in a safe state despite failure.
```

Most interview answers assume **fail-stop** unless you say otherwise.

---

## 3. Crash Failures

### 3.1 What it looks like

```
  time ─────────────────────────────────────────────►

  NODE B    ───running───running───running───┤ DEAD
                                              ▲
                                              └── permanently stops
                                                  (until restart)

  Detected by:  heartbeat timeout, TCP RST, connection refused
  Recovery    : failover to replica, re-run leader election
```

### 3.2 Causes

- Power loss
- Kernel panic (NULL-pointer dereference in a driver)
- OOM killer
- Hardware failure (disk, NIC, RAM)
- Process killed (kill -9, systemd, k8s oom-kill)
- Uncaught exception in an unrecoverable thread

### 3.3 Handling

```
 REPLICAS        Keep N synchronized copies of the state machine.
 HEARTBEATS      Detect the crash within T seconds.
 FAILOVER        Promote a replica to leader.
 RE-ELECTION     If leader crashes, pick a new one via consensus.
 RESTART         Kubernetes restarts the pod, systemd respawns the daemon.

 KEY INVARIANT:  N = 2F + 1 replicas to tolerate F crashes.
```

### 3.4 Subtle bugs even with crash-only design

```
 FSYNC-AFTER-WRITE CRASH
 ───────────────────────
 Write WAL → return success to client → crash BEFORE fsync.
 → Durability lost. Must fsync BEFORE ack.

 CRASH DURING LEADER ELECTION
 ────────────────────────────
 Old leader crashes with in-flight writes → follower becomes leader
 → some writes only exist on the dead leader → lost on restart.
 Fix: Raft's "log matching" + majority quorum + commit index rules.

 STARTUP vs. SHUTDOWN CRASH RECOVERY
 ───────────────────────────────────
 If the crash happened mid-snapshot, don't load the partial snapshot.
 Store snapshots atomically (write-then-rename).
```

---

## 4. Omission Failures

A node is *alive* but some messages are lost (send or receive).

### 4.1 Diagram

```
  Client           Network             Server
    │                                    │
    │── req A ─────► (dropped)           │    send omission
    │                                    │
    │── req B ──────────────────────────►│
    │◄── resp B ──────────────────────── │
    │                                    │
    │── req C ──────────────────────────►│
    │                     (dropped)◄──── │    reply omission
    │   ? timeout ?                       │
```

### 4.2 Root causes

- Packet loss (congested switch, bad cable, wifi)
- NIC hardware/firmware bug
- Full TCP buffers → retransmit storms → drops
- Firewall / iptables rule drops
- Half-open TCP (one side crashed, other holds a connection)
- Application message queue full → drops or rejects

### 4.3 Difficulty: you can't distinguish it from crash

```
 Node A sends heartbeat to Node B.
 3 heartbeats missed.
 Did B crash? Or is the network dropping packets?
   → you don't know.
   → this is the fundamental hard problem in distributed systems.
```

### 4.4 Handling

```
 TIMEOUTS            Bound how long you wait.
 RETRIES             Idempotent retries with backoff.
 DUPLICATES          Assume your retries mean "at least once"; dedupe.
 CIRCUIT BREAKER     Stop hammering a broken path.
 END-TO-END ACKS     Don't trust "message delivered to queue"
                     = "processed by service."
```

---

## 5. Timing Failures

A message or response arrives, but **too late** to be useful.

### 5.1 Why it's its own category

```
  SLA: "respond within 500 ms"
        │
        ▼
  Client sends request.
  Request sits in a GC pause for 2 seconds.
  Server replies at t=2000ms.
  Client has already timed out → considers request failed → retries.
  Server does the work TWICE (if not idempotent).

  → Timing failure + omission assumption = duplicated side effects.
```

### 5.2 Causes

```
 STOP-THE-WORLD GC PAUSE     Java, Go, JVMs → multi-second pauses.
 VIRTUALIZATION STALLS       VM migrates, steal time ≈ 100%.
 CPU THROTTLING              AWS burst credits exhausted.
 DISK LATENCY SPIKE          Fsync on a degraded SSD takes seconds.
 NETWORK LATENCY SPIKE       Cross-AZ traffic across overloaded link.
 LOCK CONTENTION             One slow query blocks thousands.
```

### 5.3 The GC-pause trap (real example: Cassandra)

```
  Node A holds a distributed lock (lease expires in 10 s).
  Node A enters a 30-second GC pause.
  Lock service gives lock to Node B (after 10 s).
  Node B makes writes.
  Node A wakes up, still "thinks" it has the lock, makes writes.
  → Data corruption.

  MITIGATION: fencing tokens (see 05-NetworkPartitions.md §5.3).
```

### 5.4 Handling timing

```
 TIGHT TIMEOUTS + RETRIES   short per-attempt; idempotent retry.
 HEDGED REQUESTS            after p95, send to a second replica.
 BACK-PRESSURE              servers tell clients to slow down.
 LOAD SHEDDING              drop low-priority requests early.
 ADMISSION CONTROL          refuse new work above capacity.
 GC TUNING                  shorter pauses, off-heap storage.
```

---

## 6. Response Failures

The server responds, but the **answer is wrong** — not malicious, just
buggy/corrupt.

### 6.1 Examples

```
 Bit flip in RAM          value returned differs from stored value.
 Bad SSD firmware         returns prior version of the block.
 Buggy code path          off-by-one, wrong cache entry.
 Network corruption       packet payload mutated, CRC missed it.
 Silent disk corruption   filesystem returns wrong bytes.
```

Amazon, Facebook, Google have all published papers about **silent
data corruption** (SDC) at fleet scale. At millions of cores, bit
flips happen routinely.

### 6.2 Handling

```
 CHECKSUMS / HASHES            at every layer: disk block, RPC
                                payload, file, row, message.
 END-TO-END INTEGRITY          checksum at application level,
                                not just TCP (TCP checksum is 16-bit, weak).
 ECC MEMORY                    mandatory in servers; still not enough
                                for very rare multi-bit errors.
 REPLICATE + COMPARE           read from multiple replicas, compare.
 MERKLE TREES                  anti-entropy to detect divergence.
 FAIL-FAST ASSERTIONS          crash when invariant violated vs
                                silently continuing.
```

### 6.3 Fail-fast vs fail-safe

```
 FAIL-FAST: as soon as something looks wrong, crash the node.
             Safer — bugs propagate less.
             Example: Postgres aborts when it sees a corrupted WAL.

 FAIL-SAFE: continue with a safe default.
             Example: ABS brakes — lose computer → fall back to
             pure hydraulic.
             More complex, but lets the system keep running.

 DISTRIBUTED CODE often combines: fail-fast for that process,
 fail-safe at the system level (replica takes over).
```

---

## 7. Byzantine Failures

Named after the **Byzantine Generals Problem** (Lamport, Shostak, Pease,
1982). Nodes can behave **arbitrarily** — including sending different
data to different peers. This is the hardest category.

### 7.1 Where it matters

```
 IN A TRUSTED DATACENTER           →  rarely worth defending against
                                      (assume hardware + ops is honest)
 BLOCKCHAIN / PUBLIC P2P           →  Byzantine is the whole point
 GOVERNMENT / DEFENSE              →  consider it
 MULTI-PARTY FINANCIAL SETTLEMENT  →  often BFT-based
 SPACECRAFT / AVIONICS             →  triple-modular redundancy
```

### 7.2 Why it's harder

```
  N = 2F + 1 replicas tolerate F CRASH failures.
  N = 3F + 1 replicas tolerate F BYZANTINE failures.

  Why the jump?
  Because a Byzantine node can pretend to be multiple voices, send
  different answers to different observers, and collude. You need
  enough honest quorum to OUT-VOTE both the Byzantine nodes AND
  the temporary confusion they cause.
```

### 7.3 BFT algorithms

```
 PBFT (Castro & Liskov, 1999)       Datacenter, 3F+1 nodes, O(N²) msgs
 Tendermint / HotStuff              Modern PBFT variants, O(N) per view
 Bitcoin PoW (Nakamoto)             Probabilistic via proof-of-work
 Ethereum PoS / Gasper              Slashing-based economic safety
```

### 7.4 When to mention in an interview

```
 ✓ "Inside a trusted DC we assume non-Byzantine; consensus uses Raft."
 ✓ "For our payment rails between banks, we'd consider a BFT design."
 ✗ Don't propose PBFT when the interviewer just asked about replication
   in a single DC — you'll look like you're overcomplicating.
```

---

## 8. Gray Failures (the real production killer)

Clean crashes are easy. The nightmare failures are **partial** — the
node *half works*.

### 8.1 Definition

> A **gray failure** is one where a component is impaired, but the
> impairment isn't visible to standard health checks.
>
> (Term popularized by Microsoft's 2017 HotOS paper on Azure.)

### 8.2 Classic symptoms

```
  SYMPTOM                                  UNDERLYING REALITY
  ────────                                 ──────────────────
  TCP pings answer; RPCs don't             App thread pool full / deadlock
  5% of writes silently disappear          Buggy disk controller firmware
  p99 latency 50× baseline                 NIC dropping packets, TCP retx
  Node logs are fine, clients seeing 5xx   DNS cache returning bad IP
  Intermittent corruption                  Bad RAM stick, ECC warnings
  Auto-scaler "succeeds" but nodes half-up Missing init container in k8s
```

### 8.3 Why health checks miss them

```
  HEALTH CHECK                         WHAT IT ACTUALLY TESTS
  ────────────                         ──────────────────────
  ping 8.8.8.8                         ICMP works; says NOTHING about app
  GET /health → 200 OK                 HTTP server alive; not the DB pool
  TCP SYN accepted                     Kernel is up; not the app
  systemctl status foo                 Process is running; not serving traffic

  → Always DO WORK in a health check: touch a DB row, call a dependency.
```

### 8.4 Detection strategies

```
 1. END-TO-END PROBES
    Synthetic transaction: "place a $0.01 test order every 30s,
     verify it ends up in the DB and gets emailed."

 2. p99 AND p99.9 ALERTING
    Not just mean latency — tail percentiles catch gray failures.

 3. PEER PERSPECTIVE
    What do OTHER nodes think of this node? (Gossip membership.)

 4. ERROR BUDGET / OUTLIER DETECTION
    If this node has 10× the error rate of siblings, drain it.

 5. HEDGED REQUESTS
    For reads, send to two replicas after p95 timeout.
    Doesn't fix gray failure but tolerates it.
```

### 8.5 Why gray failures cascade

```
  Slow node still accepts requests → queues build up on it.
  Clients retry → more requests → queue longer.
  Upstream timeouts fire → connection count explodes.
  Other services now also slow.
   ... and you have a metastable failure (see §11).
```

---

## 9. Failure Domains & Blast Radius

Engineering question: *if this thing fails, how many users does it hurt?*

### 9.1 Hierarchy

```
  ┌──────────────────── REGION (us-east-1) ──────────────────────┐
  │   ┌──── AVAILABILITY ZONE (us-east-1a) ────┐                  │
  │   │   ┌── Rack ──┐  ┌── Rack ──┐            │                  │
  │   │   │  Host    │  │  Host    │            │                  │
  │   │   │  VM/Pod  │  │  VM/Pod  │            │                  │
  │   │   └──────────┘  └──────────┘            │                  │
  │   └────────────────────────────────────────┘                  │
  │   ┌──── AZ (us-east-1b) ───┐ ... ┌─ AZ (us-east-1c) ──┐       │
  │   └────────────────────────┘     └────────────────────┘       │
  └──────────────────────────────────────────────────────────────┘

  Rule: place REPLICAS across different FAILURE DOMAINS.
        Never all 3 Raft replicas in one rack.
        For multi-region DR: at least one replica in another region.
```

### 9.2 Shared dependencies (hidden blast radius)

```
  "We're multi-AZ!" …but:

  - All AZs share the same config service (single-point)
  - All pods use the same bad container image (deploy bomb)
  - All workers dial the same DNS name (DNS outage = total outage)
  - Both replicas query the same single RDS master

  These are CORRELATED FAILURES. Design for them.
```

### 9.3 Cell-based architecture

```
  Instead of one big cluster: many small "cells," each serving
  a subset of users. A failed cell can only break its users.

  [Cell A: 10% of users] [Cell B: 10%] ... [Cell J: 10%]
          ▲                        ▲
          └── independent DB       └── independent deploy pipeline

  Used by: AWS (Availability Zones ARE cells), Slack, Shopify.
```

---

## 10. Cascading Failures

Small failures that get amplified by the system's own retry/failover logic.

### 10.1 Classic cascade

```
  1. Node A crashes.
  2. Clients retry → hit Node B, doubling its load.
  3. Node B saturates, starts rejecting.
  4. Retries go to Node C → triples its load.
  5. Node C crashes.
  6. All surviving nodes thrash → total outage.

  → The FAILURE of A didn't bring down the system;
     the RESPONSE to A's failure did.
```

### 10.2 Amplifiers (add retries to this list with caution)

```
  UNBOUNDED RETRIES          (1 failure → 1,000,000 attempts)
  NO BACKOFF / JITTER        synchronized retry waves
  LONG TIMEOUTS              pile up connections before failing
  FAILOVER CONCENTRATION     all clients redirect to single survivor
  WORKLOAD MIGRATION STORM   k8s evicts all pods from a node → floods
```

### 10.3 Mitigations

```
  EXPONENTIAL BACKOFF + JITTER       see 12-BackPressureAndRetries.md
  CIRCUIT BREAKERS                   fail fast when dependency is down
  LOAD SHEDDING                      drop work you can't serve
  TOKEN BUCKETS / RATE LIMITS        cap fan-out to dependencies
  BULKHEADS                          isolate thread pools per dependency
  GRACEFUL DEGRADATION               serve cached / partial data
  AUTO-SCALING COOLDOWNS             don't spawn 100 nodes in a minute
```

---

## 11. Metastable Failures

First formally described in "Metastable Failures in Distributed Systems"
(Bronson et al., 2021). The system has **two stable states**:

```
  STATE 1: healthy      low latency, low queue depth
  STATE 2: overloaded   high latency, queues full, retries amplify load

  Small trigger event pushes system from 1 → 2.
  Even after the TRIGGER goes away, feedback loops hold it in state 2.
  Only way out: drain load entirely (drop traffic, restart fleet).
```

### 11.1 Diagram

```
     ┌─────────────┐       trigger        ┌──────────────────┐
     │  STATE 1    │ ──────────────────►  │    STATE 2       │
     │  HEALTHY    │                       │  OVERLOADED      │
     │  low load   │ ◄─── must drain ────  │  retries         │
     │             │       traffic         │  amplify load    │
     └─────────────┘                       └──────────────────┘

       self-sustaining in BOTH states.
```

### 11.2 Real-world examples

- **Facebook 2010 outage**: DB servers overloaded, clients retried harder.
- **AWS DynamoDB 2015**: metadata fetcher thundering herd; took hours to drain.
- **Gmail "retry amplification" outages**.

### 11.3 Prevention

```
  - Admission control (reject load before accepting it).
  - Cap the fan-out and retry budgets globally.
  - Long-term circuit breakers that don't auto-reset under load.
  - Load shedding at the ingress (CDN / LB).
  - Hedged requests only at low load (don't at p99 overload!).
```

---

## 12. Detection, Response, and Mitigation Matrix

```
┌────────────┬──────────────────┬───────────────────────┬─────────────────────┐
│ Failure    │ How to detect    │ How to respond        │ Prevent / mitigate  │
├────────────┼──────────────────┼───────────────────────┼─────────────────────┤
│ Crash      │ Heartbeat miss   │ Failover, re-elect    │ Replicas, k8s/systemd│
│ Omission   │ Timeouts, retrans│ Retry (idempotent)    │ Idempotency keys    │
│ Timing     │ p99 spikes       │ Hedged req, backoff   │ GC tune, admission  │
│ Response   │ Checksum mismatch│ Quorum read repair    │ ECC, hashes, Merkle │
│ Byzantine  │ BFT vote compare │ Remove node, re-vote  │ 3F+1 replicas, PBFT │
│ Gray       │ Synthetic probes │ Drain / quarantine    │ End-to-end health   │
│ Cascading  │ Error rate slope │ Circuit-break deps    │ Budgets, bulkheads  │
│ Metastable │ Stuck bad state  │ Drain traffic, reboot │ Admission control   │
└────────────┴──────────────────┴───────────────────────┴─────────────────────┘
```

---

## 13. Interview Q&A

### Q1. "Can you distinguish a crashed node from a slow one?"

> Not from outside, no — that's one of the fundamental impossibilities of
> distributed systems. You pick a timeout and call any slower "dead,"
> knowing you'll sometimes be wrong (false positive). Mitigations:
> **fencing tokens** so a "dead" node can't corrupt state if it comes back,
> **Phi accrual** detectors for probabilistic confidence, and
> **indirect pings** (SWIM) to reduce false positives.

### Q2. "What's the difference between fail-stop and fail-silent?"

> **Fail-stop** means the node crashes AND other nodes can detect the
> crash (e.g., the OS sends RSTs, hardware signals failure). **Fail-silent**
> means it crashes silently — from outside, looks like omission. Most
> practical systems assume fail-stop because fail-silent needs heartbeat
> mechanisms to handle.

### Q3. "Why is N = 2F + 1 not enough for Byzantine?"

> A crashed node stops talking; a Byzantine node can **send conflicting
> values to different peers**. With 2F+1 you have F+1 honest, F Byzantine
> — but Byzantine nodes can "split" the honest F+1 into two equal groups
> by telling each half different things, so no honest majority emerges.
> You need 3F+1 so that F+1 honest can agree even if F Byzantine split
> the remaining F honest evenly.

### Q4. "Your service gets a retry storm during an incident. What do you do?"

> 1. **Circuit-break** the dependency immediately — stop hammering it.
> 2. **Shed load** at the ingress; return 503 with Retry-After.
> 3. **Backoff + jitter** on all retry paths (if it wasn't already there).
> 4. **Drain + restart** the stuck fleet if it's metastable.
> 5. Post-mortem: add a **global retry budget** + **admission control**
>    at the LB so a single bad deploy can't cascade.

### Q5. "How do you detect 'gray' failures?"

> Standard health checks (TCP ping, process alive) miss them. You need:
> (a) **synthetic end-to-end probes** that actually exercise the
> dependency chain; (b) **peer-perspective** checks — what does the
> rest of the cluster think of this node?; (c) **tail-latency alerts**
> (p99/p99.9), not averages; (d) **outlier detection** against sibling
> nodes with the same workload.

### Q6. "A disk returns corrupted data. How should the DB behave?"

> Detect via per-block checksums. On mismatch: **fail-fast** — reject
> the read and alert. Then either (a) serve the request from a replica
> (in replicated systems), or (b) refuse and let the caller retry
> elsewhere. **Never silently return possibly-corrupted data** — the
> value of error-surface is the whole point of checksums.

### Q7. "What's a metastable failure and why is it hard to recover from?"

> A system state that, once entered, has feedback loops that keep it
> there even after the original trigger is gone. E.g., retry amplification
> — overloaded → retries multiply load → stays overloaded. Recovery
> often requires **dropping traffic** (drain), because healthy operation
> needs headroom the overloaded state is preventing from existing.

### Q8. "Are Byzantine failures ever worth defending against in-DC?"

> Usually no — intra-DC networks are trusted, and the cost (3F+1 nodes,
> O(N²) messages) is large. Exceptions: compliance-sensitive systems
> (military, critical infra), inter-org settlement, cross-cloud-provider
> systems, and of course **blockchains**.

---

## 14. Further Reading

- Lamport, Shostak, Pease, "The Byzantine Generals Problem" (1982)
- Cristian, "Understanding Fault-Tolerant Distributed Systems" (1991)
- Huang et al., "Gray Failure: The Achilles' Heel of Cloud-Scale Systems" (2017)
- Bronson et al., "Metastable Failures in Distributed Systems" (2021)
- Beyer et al., "Site Reliability Engineering" (Google) — chapters on
  cascading failure, addressing overload
- "Shit Happens" (James Mickens) — real examples at Google scale

---

> **Previous:** [02-ConsistencyModels.md](./02-ConsistencyModels.md) ·
> **Next:** [04-FailureDetection.md](./04-FailureDetection.md)
