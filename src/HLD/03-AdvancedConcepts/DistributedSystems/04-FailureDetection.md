# Failure Detection

> **Difficulty:** Medium | **Time:** 2 hours | **Priority:** Must Know

Failure detection is the mechanism by which **"this node is dead"** is
decided. It's the prerequisite for leader election, failover, sharding,
and membership in every distributed system you'll ever build. Get it
wrong and you either **miss real outages** (false negatives) or
**invent fake ones** (false positives → churn, thrashing, split-brain).

---

## Table of Contents

1. [What a Failure Detector Must Do](#1-what-a-failure-detector-must-do)
2. [The Fundamental Trade-off](#2-the-fundamental-trade-off)
3. [Heartbeats](#3-heartbeats-the-baseline)
4. [Ping / Request-Based Detection](#4-ping--request-based-detection)
5. [Phi Accrual Failure Detector](#5-phi-accrual-failure-detector)
6. [Gossip Membership](#6-gossip-membership)
7. [SWIM — Scalable Weakly-consistent Membership](#7-swim--scalable-weakly-consistent-infection-style-membership)
8. [Failure Detectors in Consensus (Ω)](#8-failure-detectors-in-consensus-ω)
9. [Practical Patterns](#9-practical-patterns)
10. [How Popular Systems Do It](#10-how-popular-systems-do-it)
11. [Anti-Patterns](#11-anti-patterns)
12. [Interview Q&A](#12-interview-qa)

---

## 1. What a Failure Detector Must Do

> **"Given a node X, decide (probably): is X still functioning?"**

### 1.1 The four qualities (Chandra & Toueg, 1996)

```
 STRONG COMPLETENESS    Every faulty node is EVENTUALLY suspected
                        by every correct node.
 WEAK   COMPLETENESS    Every faulty node is eventually suspected
                        by SOME correct node.

 STRONG ACCURACY        No correct node is EVER suspected.      (impossible!)
 WEAK   ACCURACY        At least ONE correct node is never suspected.
 EVENTUAL STRONG ACCURACY  After some time, no correct node is suspected.
 EVENTUAL WEAK   ACCURACY  After some time, at least one correct node is trusted.

 ◆ In an asynchronous network, PERFECT accuracy is IMPOSSIBLE.
   You always have to pick a timeout → sometimes you'll suspect
   a healthy but slow node.
```

### 1.2 Class names you'll see in papers

```
  P     (Perfect)          strong completeness + strong accuracy
  S     (Strong)           strong completeness + weak accuracy
  ◇P    (Eventually P)     strong completeness + eventual strong accuracy
  ◇S    (Eventually Strong) strong completeness + eventual weak accuracy
  Ω     (Leader detector)  eventually elects a unique correct leader
```

Consensus (Paxos, Raft) needs at least **◇S** or equivalently **Ω**
to guarantee progress.

---

## 2. The Fundamental Trade-off

Every failure detector lives on this curve:

```
                         ▲  FALSE POSITIVES
                         │  (trust drops, thrashing, split-brain risk)
                         │
       aggressive ─────► │ ◄───── short timeouts
                         │
 ────────────────────────┼───────────────────────────►
                         │                  DETECTION LATENCY
                         │                  (how fast we notice)
                         │
       conservative ───► │ ◄───── long timeouts
                         │
                         ▼  FALSE NEGATIVES
                            (real deaths go unnoticed, bad availability)
```

You **cannot eliminate** either axis. You can only move along the curve.
Every technique in this document is about bending the curve (lowering
false positives at a given detection latency).

---

## 3. Heartbeats (the baseline)

Simplest possible detector: each node periodically sends "I'm alive."

### 3.1 Mechanics

```
  ┌──────┐  heartbeat every T  ┌──────┐
  │Node A│────────────────────►│Node B│
  └──────┘                     └──────┘

  On Node B:
    on receive(hb, from=A): last_seen[A] = now()

    every check_interval:
      for each peer p:
         if now() - last_seen[p] > threshold:
              mark p as SUSPECT/DEAD
```

### 3.2 Tuning

```
  T (interval)          :  typically 100 ms – 1 s
  threshold             :  typically 3 × T   ("3 missed heartbeats")
  check_interval        :  sub-T so delay bounded
  message size          :  keep tiny (bytes); piggyback state if useful
```

### 3.3 Variants

```
 PUSH (A → B)                 A sends heartbeats, B passively listens.
                              A failed? B notices from silence.

 PULL (B → A)                 B polls A ("are you alive?"). A responds.
                              Lets B drive the cadence.

 PUSH-PULL                    Two-way heartbeats so BOTH sides notice.

 PUSH WITH PIGGYBACKED DATA   Heartbeat carries small state (gossip-like).
```

### 3.4 Why bare heartbeats scale poorly

```
  All-to-all heartbeats in N-node cluster → O(N²) messages.

  N = 100   →     10 000 msgs/interval
  N = 1000  →  1 000 000 msgs/interval  ← unusable

  Solutions: gossip (§6), SWIM (§7), hierarchical detectors.
```

---

## 4. Ping / Request-Based Detection

Instead of "are you alive?" send real work and watch for answers.

### 4.1 Benefit

```
 Tests more than the TCP stack — proves the APPLICATION is working.
 Catches gray failures (thread pool full, DB driver hung) that a
 cheap TCP heartbeat misses.
```

### 4.2 Patterns

```
 ACTIVE HEALTH CHECKS       LB periodically calls GET /health
 PASSIVE HEALTH CHECKS      LB observes real-request errors → eject
                            (Envoy's "outlier detection")
 SYNTHETIC TRANSACTIONS     run a canary "end-to-end" probe every minute
                            (e.g., create a test order, verify delivery)
```

### 4.3 Good /health content

```
  Level 0  LIVENESS     "process is running"
  Level 1  READINESS    "I can accept traffic" (connected to DB, etc.)
  Level 2  DEEP CHECK   "my dependencies respond"
                        (usually sampled, not every probe)

  Bad:  return 200 always from a static handler.
  Good: touch one DB row + check cache + downstream RPC.
```

---

## 5. Phi Accrual Failure Detector

Hayashibara et al. (2004). Instead of binary alive/dead, produce a
**suspicion value Φ** that grows smoothly with time since last heartbeat,
**calibrated to the historical distribution** of inter-arrival times.

### 5.1 Intuition

```
 Traditional timeout: "hb missed for > 3×T → DEAD" (hard cut).
 Phi Accrual:        "probability that the next hb will arrive later
                      than now(), given history" → convert to a score.

  Φ(t) = −log10 P(next_hb_interval > t)

  Φ ≈ 1   → ~10%    chance you're wrong calling it dead
  Φ ≈ 2   → ~1%
  Φ ≈ 4   → ~0.01%
  Φ ≈ 8   → ~10⁻⁸  (very confident dead)
```

### 5.2 How it adapts

```
  Keep a sliding window of the last N inter-arrival times (say N=1000).
  Compute mean μ and stddev σ.
  Model inter-arrivals as ~Normal(μ, σ²) or Exponential.
  For the current gap Δt since last hb:

     Φ = −log10 ( 1 − CDF_normal(Δt ; μ, σ) )

  If the network is consistently fast: σ small → Φ rises fast after
                                       a surprise gap.
  If the network is jittery: σ big → Φ rises slowly, fewer false alarms.
```

### 5.3 Benefits

```
  ✓ Adaptive — works both in fast LANs and slow WANs without retuning
  ✓ Application picks its own threshold (critical svc: Φ=12, UI: Φ=6)
  ✓ Smooth output, nice for alerting
  ✓ Can integrate with HEDGED requests (if Φ > 3, send backup request)

 Used by: Cassandra (default), Akka Cluster.
```

### 5.4 Ascii chart

```
   Φ
   │                                          ╭────── DEAD (Φ=12)
 12│                                         ╱
   │                                        ╱
   │                                       ╱
  8│                              SUSPECT ╱
   │                          ╭──────────╯
  4│                   RISING ╱
   │              ╭──────────╯
  0├──────────────╯──────────────────────────────────────►
   └── last hb  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─   time

   Flat-ish near 0 while heartbeats arrive normally;
   rises sharply past the expected interarrival.
```

---

## 6. Gossip Membership

Instead of every node pinging every other, each node periodically
picks **K random peers** and shares a snapshot of its membership view.
Information spreads like an epidemic — **O(log N)** rounds to reach
all nodes.

### 6.1 Protocol

```
 State on each node:
    member_list = { node_id → (status, incarnation, last_seen) }

 every T milliseconds:
    choose K random peers from member_list
    for each peer p:
       send member_list to p

 on receive(remote_list):
    merge(local_list, remote_list):
       for each entry e:
           if e.incarnation > local.incarnation:
               adopt e                             (newer wins)
           else if same incarnation:
               take MAX of last_seen / max status severity
```

### 6.2 Incarnation numbers (aka versions)

```
 Each node "owns" its own status. When a node A hears "A is DEAD"
 from gossip but A is actually alive, A bumps its INCARNATION and
 re-broadcasts "A: alive, incarnation=42."

 Because 42 > previous 41, the alive-announcement beats the stale
 dead-announcement everywhere.
```

### 6.3 Convergence

```
  Round        1        2        3     ...     log₂(N)
  Known         1       ≤K      ≤K²              ≈ N

  With K=3 random peers, N=1000 → converges in ~10 rounds.
  If each round is 200 ms → full propagation in ~2 seconds.
```

### 6.4 Visual

```
         Round 0                  Round 1                Round 2
                                                                       
         ●                       ●─◊                   ◊─◊─◊           
            only A knows          A told B,C             B,C told more 
                                                                       
          ●●●●●●●●●●         ●=updated  ◊=still stale                  
```

---

## 7. SWIM — Scalable Weakly-consistent Infection-style Membership

Das, Gupta, Motivala (2002). Refines gossip with two ideas that
dramatically cut false positives:

### 7.1 Idea 1: Direct then indirect probe

```
  Step 1: Node M picks target X.
            M ──ping──► X
          If X replies within timeout → X is alive, done.

  Step 2 (if no reply): M asks K random others to probe X.
            M ──"please ping X"──► {A, B, C}
             A ──ping──► X
             B ──ping──► X
             C ──ping──► X
          If ANY of A,B,C get a reply and forward it to M → X alive.
          If NONE do → M marks X SUSPECT.

  Why: M's network path to X might be bad even if X is fine.
       Indirect probes rule out local path problems before blaming X.
```

### 7.2 Diagram

```
                           M
                         / │ \
                        /  │  \
                       /   │   \
                      /    │    \
                     /     │     \
                 (A)    (ping X) (B)
                  │        ×       │       X not responding
                  │                │
                  │  "ping X for me"
                  ▼                ▼
                  X                X
              (replies?)       (replies?)
                  │                │
                  └───► relay to M ◄─┘

   If ANY succeeds → M trusts X (it was M's link).
   If NONE succeed → M marks X SUSPECT, gossips it.
```

### 7.3 Idea 2: Suspect → Confirm state machine

```
        ┌────────┐   miss K probes    ┌──────────┐
        │ ALIVE  │ ─────────────────► │ SUSPECT  │
        └────────┘                    └─────┬────┘
            ▲                               │
            │ suspect refuted              │  timeout, still no refute
            │ (target or witness           │
            │  sends "I'm alive, v+1")     ▼
            │                        ┌──────────┐
            └───────────────────────►│CONFIRMED  │
                                     │  FAILED   │
                                     └──────────┘
                                             │
                                          gossip to everyone
```

Hosting 10 000+ nodes? **SWIM** is the de-facto standard:
**Consul, HashiCorp Serf, Uber Ringpop, Cilium, ScyllaDB, CockroachDB**
all use SWIM or a derivative.

### 7.4 Extensions (Lifeguard)

Uber's "Lifeguard" paper (2018) adds:

- **Self-awareness:** a node temporarily boosts its own incarnation
  rate when it's been falsely suspected → fewer flaps.
- **Dogpile protection:** spread indirect-ping targets across time.
- **Buddy pinging:** prefer healthy recent contacts for indirect.

---

## 8. Failure Detectors in Consensus (Ω)

Paxos and Raft don't detect failures themselves — they **assume** an
underlying detector tells them who the leader is. Formally they need
a **leader detector Ω**:

```
 Ω guarantees: EVENTUALLY all correct processes agree on the same
               correct process as "the leader."

   → this is enough to make consensus terminate (even though
     FLP impossibility forbids guaranteed termination without it).
```

In Raft, the Ω role is played by **randomized election timeouts**:

```
 Each follower has a random election timeout in [150, 300] ms.
 If no heartbeat from leader within that → start election.

 Randomization ensures only one follower usually times out first,
 so one candidate at a time, converging to one leader.
```

Without this, dueling candidates keep stealing each other's votes
forever — the classic "split vote."

---

## 9. Practical Patterns

### 9.1 Graduated suspicion

Don't flip alive→dead with one missed heartbeat. Use multiple states:

```
  ALIVE  ──► SUSPECT  ──► DEAD  ──► REMOVED

     ▲         │           │          │
     │         │           │          │
    refute   gossip      still       no contact for
              widely    no reply     T_forget  →
                         after        forget entirely
                         threshold
```

### 9.2 Leases with fencing tokens

Never rely solely on a detector for safety; pair it with fencing:

```
  1. Lock service grants leader lease + monotonic TOKEN=42.
  2. Leader writes to storage with token=42.
  3. Leader pauses (GC / network issue).
  4. Detector says leader dead. New leader gets token=43.
  5. Old leader wakes up, writes with token=42.
     STORAGE sees token=42 < latest 43 → REJECTS.

  → safety preserved even if detector was wrong.
```

See `05-NetworkPartitions.md` §5.3 for details.

### 9.3 Quarantine / draining

```
 Instead of instantly removing a SUSPECT node:
   - Stop sending new requests to it (drain)
   - Keep existing connections alive briefly
   - After N seconds, promote to DEAD and replicate its shards
   - Stagger the replication to avoid overloading survivors
```

### 9.4 Heartbeat piggyback

Carry small state in every heartbeat — gossip membership, load
metrics, leader hints. Saves round trips and makes the heartbeat
do real work.

---

## 10. How Popular Systems Do It

```
┌──────────────────┬──────────────────────────────────────────┐
│ System           │ Failure detection approach                │
├──────────────────┼──────────────────────────────────────────┤
│ Raft (etcd)      │ Leader heartbeats + randomized timeouts  │
│ ZooKeeper        │ Tick-based session timeouts, leader ping │
│ Cassandra        │ Phi Accrual + gossip                      │
│ DynamoDB         │ Gossip-based membership                   │
│ Consul           │ SWIM (Serf protocol)                      │
│ Kubernetes node  │ Kubelet → API server status reports      │
│ Kubernetes pod   │ Liveness/Readiness HTTP probes           │
│ Akka Cluster     │ Phi Accrual + gossip                      │
│ Hazelcast        │ Phi Accrual + deadline-based             │
│ CockroachDB      │ SWIM-like gossip                          │
│ AWS ALB / ELB    │ Active health checks + outlier detection │
│ Envoy / Istio    │ Passive outlier detection + active probes│
└──────────────────┴──────────────────────────────────────────┘
```

---

## 11. Anti-Patterns

```
 ✗ SHARED TIMEOUT between detector and operation timeout
      → operation timeouts make you mark nodes dead under load.

 ✗ USING TCP KEEPALIVE ALONE
      → default 2 HOURS idle before TCP detects a dead peer.
      → must set TCP_USER_TIMEOUT / app-level heartbeats.

 ✗ HEALTH CHECK THAT ALWAYS RETURNS 200
      → node is "alive" while its DB driver is hung. Gray failure.

 ✗ FAILING OVER THE INSTANT A SUSPECT APPEARS
      → every small network blip causes leader thrashing.
      → add quorum of suspicion, or SUSPECT → wait → DEAD.

 ✗ SAME THRESHOLD FOR WAN AND LAN
      → phi accrual or per-peer tuning handles this.

 ✗ NO FALLBACK IF THE DETECTOR IS WRONG
      → always pair with fencing tokens & idempotency to stay safe.
```

---

## 12. Interview Q&A

### Q1. "How do you detect failures in a 10 000-node cluster?"

> Not with all-to-all heartbeats (O(N²)). Use a gossip-based protocol
> like **SWIM**: each node every ~200ms direct-pings a random peer,
> escalates to indirect pings via K witnesses, and gossips membership
> changes piggybacked on these probes. Convergence is O(log N) rounds.
> Combine with **incarnation numbers** so nodes can refute false
> suspicions about themselves.

### Q2. "What's the trade-off in picking a heartbeat interval?"

> Short interval → fast detection but more network overhead and more
> false positives under jitter. Long interval → accurate but slow to
> react. Use **Phi Accrual** to make the threshold adapt to the
> observed network distribution, and pair detection with **fencing
> tokens** so false positives can't corrupt state.

### Q3. "Why does Raft use randomized election timeouts?"

> To give **eventual leader uniqueness (Ω)** under FLP. If all followers
> used the same timeout, multiple would time out simultaneously,
> become candidates, split the vote, and loop. Randomizing in
> `[150, 300] ms` means one candidate usually wins its term before
> another starts.

### Q4. "What's the difference between Phi Accrual and a fixed timeout?"

> Fixed timeout is a hard threshold — static, brittle under jitter.
> Phi Accrual outputs a continuous suspicion level calibrated to the
> actual history of heartbeat arrivals — it's **adaptive**, so the
> same code works on a fast LAN (tight distribution, low threshold
> works) and a noisy WAN (wide distribution, same Phi level
> corresponds to longer gap).

### Q5. "Your k8s /health endpoint always returns 200. Is that OK?"

> No — it's a classic gray-failure trap. The process can be alive
> while the app is fully deadlocked (DB pool exhausted, all threads
> blocked). A proper health check should **touch a dependency** —
> e.g., run a trivial DB query, check a cache connection — so that
> real partial-failure states are surfaced.

### Q6. "How do you prevent a flapping node from destabilizing the cluster?"

> Use **graduated states** (alive → suspect → dead → removed) with
> minimum dwell times; **self-awareness** (Lifeguard-style) so the
> suspected node can refute with a bumped incarnation; **hysteresis**
> so recent flaps extend the suspicion threshold temporarily; and
> exponentially back off rejoin attempts if the node is unstable.

### Q7. "Active health checks vs passive outlier detection — which do you prefer?"

> Both. Active checks (GET /health every N sec) give baseline monitoring
> and warm-up signals; passive outlier detection (observing real
> request error rates per upstream) catches issues that the synthetic
> probe misses AND reacts within a single bad burst without waiting
> for the next probe. Envoy and Istio use both together.

### Q8. "What's the role of failure detectors in consensus?"

> Paxos/Raft need an **eventual leader detector (Ω)** to make progress.
> FLP impossibility says no pure-async deterministic consensus is
> possible; the detector is what introduces the partial-synchrony
> assumption. In Raft, Ω is implemented via heartbeats plus
> randomized election timeouts.

---

## 13. Further Reading

- Chandra & Toueg, "Unreliable Failure Detectors for Reliable Distributed Systems" (1996)
- Hayashibara et al., "The Φ Accrual Failure Detector" (2004)
- Das, Gupta, Motivala, "SWIM: Scalable Weakly-consistent Infection-style Process Group Membership Protocol" (2002)
- DeCandia et al., "Dynamo: Amazon's Highly Available Key-value Store" (2007) — gossip membership in practice
- Lakshman & Malik, "Cassandra" (2010) — Phi Accrual in production
- "Lifeguard: Local Health Awareness for More Accurate Failure Detection" (2018) — Uber's SWIM extension

---

> **Previous:** [03-FailureModes.md](./03-FailureModes.md) ·
> **Next:** [05-NetworkPartitions.md](./05-NetworkPartitions.md)
