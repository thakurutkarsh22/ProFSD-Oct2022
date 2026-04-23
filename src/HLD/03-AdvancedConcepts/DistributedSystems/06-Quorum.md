# Quorum

> **Difficulty:** Medium | **Time:** 2 hours | **Priority:** Must Know

A **quorum** is the minimum number of nodes that must participate in an
operation for it to be considered valid. Quorums turn "we have N copies"
into **provable** consistency guarantees: by making any two quorums
overlap in at least one node, you ensure every read sees the effect
of every completed write.

---

## Table of Contents

1. [The Core Intuition](#1-the-core-intuition)
2. [The N / W / R Model](#2-the-n--w--r-model)
3. [Majority Quorums](#3-majority-quorums)
4. [Why Odd Cluster Sizes](#4-why-odd-cluster-sizes)
5. [Strict vs Sloppy Quorum](#5-strict-vs-sloppy-quorum)
6. [Hinted Handoff](#6-hinted-handoff)
7. [Witness / Learner / Non-voting Replicas](#7-witness--learner--non-voting-replicas)
8. [Flexible / Grid Quorums](#8-flexible--grid-quorums)
9. [Quorum in Consensus Protocols](#9-quorum-in-consensus-protocols-paxos--raft)
10. [Performance Math](#10-performance-math)
11. [Interview Q&A](#11-interview-qa)

---

## 1. The Core Intuition

```
 QUESTION:  If I replicate data to N nodes, how do I know a READ
            will return the latest WRITE?

 ANSWER:    Make the WRITE quorum and the READ quorum OVERLAP.
            Any overlapping node has the latest value.

            If  W + R > N  →  ∀ write quorum QW, ∀ read quorum QR:
                              QW ∩ QR ≠ ∅
                              (by pigeonhole — must share ≥ 1 node)
```

### 1.1 Visual

```
       N = 5 replicas:   [ R1 ][ R2 ][ R3 ][ R4 ][ R5 ]

  Write quorum W = 3:    [  W ][  W ][  W ][    ][    ]   (R1, R2, R3)
  Read  quorum R = 3:    [    ][    ][  R ][  R ][  R ]   (R3, R4, R5)
                                      ▲
                                      └── R3 participates in both
                                          → read sees the write.

  W + R = 3 + 3 = 6 > 5.  Always overlap → strong consistency.
```

### 1.2 Why quorum matters beyond overlap

Quorums also give you:

```
  • FAULT TOLERANCE      write can succeed with up to (N − W) failures
  • PARTITION SAFETY     only majority-side can form quorum → no split-brain
  • CONSISTENT AGREEMENT any two quorums overlap → no conflicting decisions
```

---

## 2. The N / W / R Model

Coined by **Dynamo** (Amazon, 2007) and the de facto language in
quorum-based databases like Cassandra, Riak, DynamoDB.

```
  N  = Number of REPLICAS for each data item
  W  = number of replicas that must ACKNOWLEDGE a WRITE
       for the write to be "successful" to the client
  R  = number of replicas that must RESPOND to a READ
       for the value to be "valid" to the client

  CONSTRAINT for STRONG CONSISTENCY:   W + R > N
  EXTRA CONSTRAINT against SPLIT-BRAIN: W    > N / 2
```

### 2.1 Cheat-sheet of configurations

```
  N = 3 (typical default)

  ┌─────┬─────┬──────────────────────────────────────────┐
  │  W  │  R  │ Behaviour                                 │
  ├─────┼─────┼──────────────────────────────────────────┤
  │  1  │  1  │ Fast, eventually consistent. No overlap.  │
  │  2  │  2  │ STRONG (quorum). Balanced latency.        │
  │  3  │  1  │ Strong reads, slow writes. Read-heavy.    │
  │  1  │  3  │ Strong reads via full scan. Write-heavy.  │
  │  2  │  3  │ Strong + durable reads. Slow reads.       │
  │  3  │  3  │ Every op hits every node. Max safety.     │
  └─────┴─────┴──────────────────────────────────────────┘

  N = 5 (cross-DC or high-durability):

  ┌─────┬─────┬──────────────────────────────────────────┐
  │  W  │  R  │ Behaviour                                 │
  ├─────┼─────┼──────────────────────────────────────────┤
  │  3  │  3  │ Majority both ways. Tolerates 2 failures. │
  │  4  │  2  │ Higher read availability.                 │
  │  2  │  4  │ Higher write availability, slow reads.    │
  └─────┴─────┴──────────────────────────────────────────┘
```

### 2.2 "Consistency levels" in Cassandra terminology

Same idea, named:

```
  ONE            W or R = 1
  TWO            W or R = 2
  THREE          W or R = 3
  QUORUM         W or R = N/2 + 1
  ALL            W or R = N
  LOCAL_QUORUM   QUORUM within the local DC only
  EACH_QUORUM    QUORUM in EVERY DC (writes only)
  LOCAL_ONE      ONE within the local DC
```

### 2.3 Example walk-through

```
  3 replicas in an AP database, W=QUORUM=2, R=QUORUM=2.

  Client writes "x = 100":
     Coordinator sends to all 3.
     Gets acks from 2 of 3 → SUCCESS returned to client.
     (Third replica may lag; will catch up via read-repair.)

  Another client reads x:
     Coordinator asks all 3, waits for 2 responses.
     At least one of those 2 MUST be from the write-quorum set
       (pigeonhole: |W| + |R| > N → overlap).
     Coordinator picks the freshest version (by HLC or timestamp).
```

---

## 3. Majority Quorums

A **majority quorum** is `⌊N/2⌋ + 1` — the smallest set strictly larger
than half.

```
   N       Majority Q   Max Failures F
   ──      ──────────   ──────────────
    1          1              0
    2          2              0   (!)
    3          2              1
    4          3              1
    5          3              2
    6          4              2
    7          4              3
    9          5              4
   11          6              5

   Formula:   N = 2F + 1     F = ⌊(N − 1) / 2⌋
```

### 3.1 Why majority quorums are special

```
  Any two majority quorums of an N-set necessarily share
  more than N/2 − (N − N/2 − 1) = 1 node, so they always overlap.

  This is why Paxos, Raft, and ZAB all use majorities for both
  election and commit: no two conflicting decisions can ever
  be agreed upon.
```

### 3.2 Visual: two majorities always overlap

```
  N = 5 nodes:   [1][2][3][4][5]

  Quorum A:      ▓  ▓  ▓          (nodes 1,2,3)
  Quorum B:            ▓  ▓  ▓   (nodes 3,4,5)
                        ▲
                        └── node 3 shared by both

   No matter which majorities you pick, at least one node is in both.
```

---

## 4. Why Odd Cluster Sizes

Even cluster sizes waste capacity:

```
  4 nodes:   Quorum = 3   → tolerates 1 failure
  5 nodes:   Quorum = 3   → tolerates 2 failures

  Going from 4 → 5 ADDS one failure tolerated.
  Going from 3 → 4 adds ZERO failure tolerance, but DOUBLES the
  chance of losing 2 nodes simultaneously (more nodes → more MTBF risk).
```

### 4.1 Also: even numbers tie votes

```
  4-node election:
     2 vote for A, 2 vote for B → no majority → stalled election.
     You'd need another ballot → latency penalty.

  3 or 5 nodes:
     Majority is unambiguous.
```

### 4.2 Exception: 2-DC with witness

```
  Put 2 voting nodes in each DC + 1 WITNESS in a third location.
  Total N=5 (odd), survives a full-DC loss.
```

---

## 5. Strict vs Sloppy Quorum

### 5.1 Strict quorum

```
  Home replicas for key K: {N1, N2, N3}.
  If fewer than W of {N1, N2, N3} are reachable → write FAILS.

  Safer, but less available.
```

### 5.2 Sloppy quorum (Dynamo / Cassandra default)

```
  If fewer than W home replicas are up, accept writes on any W
  other nodes, storing a "hint" to hand off later.

  Home:         {N1, N2, N3}   (N1 is down)
  Sloppy:       {N2, N3, N4}   ← N4 is a stand-in, stores a HINT

  Preserves availability.
  Weaker consistency: an R-quorum against home replicas may NOT see
  this write until hand-off completes.
```

### 5.3 Visual

```
      Key K → home { N1, N2, N3 }        N1 DOWN
                                           │
                                           ▼
  Write arrives:   coordinator tries {N1, N2, N3}
       N1: unreachable
       N2: ack ✓
       N3: ack ✓
       Still need W=3 acks? With sloppy enabled:
       coordinator picks N4, writes to it WITH HINT:
           "this is for N1; deliver when it recovers"
       N4: ack ✓    ← W satisfied.

  Client sees SUCCESS.

  When N1 comes back:
       N4 replays hinted writes to N1 → replicas converge.
```

### 5.4 Trade-off

```
  SLOPPY QUORUM
    + Higher availability during partial failures
    - Temporary consistency weakness (R may miss writes)
    - Needs anti-entropy to recover if hints are lost
    - Not truly a "quorum" — just "W nodes total"

  STRICT QUORUM
    + Simpler invariants
    - Writes fail if home replicas aren't reachable
```

Most AP databases (Cassandra, Riak, DynamoDB) use sloppy by default
with anti-entropy + hinted handoff to converge eventually.

---

## 6. Hinted Handoff

The mechanism that makes sloppy quorum eventually consistent.

### 6.1 Protocol

```
  1. Coordinator notices N1 is down.
  2. Writes sent to stand-in N4 tagged with:
        HINT: {target=N1, key=K, value=V, timestamp=T}
  3. N4 stores hint in a local "hint log".
  4. N4 periodically pings N1.
  5. When N1 is back:
        N4 streams hints → N1 applies them.
        After successful delivery, N4 deletes the hint.
```

### 6.2 Limitations

```
  - If N4 ITSELF crashes before handing off → write lost unless
    anti-entropy / read-repair covers it.
  - Hint TTL: if N1 stays down longer than TTL, N4 drops the hint
    (can't hoard forever). Recovery relies on anti-entropy.
```

### 6.3 Cassandra defaults (illustrative)

```
  hinted_handoff_enabled: true
  max_hint_window_in_ms:  10800000   # 3 hours
  hinted_handoff_throttle_in_kb: 1024
```

After 3 hours down, hints are dropped → ONLY **anti-entropy repair**
can bring N1 back to consistency. See `08-AntiEntropy.md`.

---

## 7. Witness / Learner / Non-voting Replicas

Not all replicas need to store data or be able to be leaders.

```
  VOTING REPLICAS (full members)
     store data + vote in quorums + can become leader

  WITNESS / ARBITER
     votes in quorums but does NOT store data
     → cheap insurance against split-brain

  LEARNER / READ REPLICA
     stores data, serves reads, but does NOT vote
     → safely scale reads without enlarging the write quorum
```

### 7.1 Why this matters

```
  If you want 7 replicas for read scaling, and you make them all
  voting, you need W=4 to write → every write waits for 4 of 7.

  Instead: keep 3 voting + 4 learners.
     W=2 among voting (cheap writes), reads served by any of 7.
     Learners get the data asynchronously.
```

**Used by:** Raft "non-voting members" (etcd, CockroachDB),
MongoDB "hidden secondaries," Spanner "read-only participants."

---

## 8. Flexible / Grid Quorums

Majority is one choice; there are others.

### 8.1 Grid quorum

Arrange N nodes into a √N × √N grid:

```
  N = 9:     [A1][A2][A3]
             [B1][B2][B3]
             [C1][C2][C3]

  Write quorum: a full ROW     (e.g. A1, A2, A3)    size = 3
  Read  quorum: a full COLUMN  (e.g. A1, B1, C1)   size = 3

  W + R = 6 < N = 9    (!)   but ANY row and ANY column OVERLAP
                             at exactly one cell → correct.

  Smaller quorums with same guarantee → lower latency.
```

### 8.2 Flexible Paxos (Howard, 2016)

Traditional Paxos: election quorum Q1 and commit quorum Q2 must both
be majorities. Flexible Paxos observes only **Q1 ∩ Q2 ≠ ∅** is required.

```
  In a 5-node cluster:
     Classic:     Q1 = 3,  Q2 = 3     (both majority)
     Flexible:    Q1 = 4,  Q2 = 2     (still overlap ≥ 1)

  Result: faster commits (Q2=2) at the cost of slower/rarer elections.
          Great if elections are much less common than commits.
```

This underlies optimizations in CockroachDB, TiDB, Spanner.

---

## 9. Quorum in Consensus Protocols (Paxos / Raft)

### 9.1 Raft uses majority quorums for:

```
  LEADER ELECTION
     Candidate needs votes from majority of N.
     → At most one leader per term.

  LOG COMMIT
     Leader commits entry when majority of N has replicated it.
     → Any future leader must have entry (majorities overlap).
```

### 9.2 Raft commit visualization

```
     Leader sends entry @ idx=7 to all 5 followers
            │
     ACKs come back:
        F1: ack      F2: ack      F3: (slow)    F4: ack    F5: ack

     Leader has 4/5 acks (incl. itself = 5/5) → COMMITTED.
     Responds to client. F3 catches up async.

     If leader crashes NOW:
       New election; candidate must have entry 7 (can only get
       majority vote if up-to-date). So the entry survives. ✓
```

See `../02-Consensus.md` for the full Raft walkthrough.

---

## 10. Performance Math

### 10.1 Latency

```
  Per op latency = max(k-th slowest node response)   where k = W (or R)

  Lower quorum size  → faster (wait for fewer acks)
  Higher quorum size → slower but safer

  Example: 3 nodes, W=2 → wait for 2nd-fastest (p50 of replies).
           5 nodes, W=3 → wait for 3rd-fastest (p50 of 5).
                           With reasonably-uncorrelated latencies,
                           bigger N + same W can actually be FASTER
                           (more chances to get two fast nodes).
```

### 10.2 Throughput

```
  Writes per second  ≈  1 / RTT_to_W_replicas
  Reads  per second  ≈  N × (1 / per-node read latency)   (if R=1)
                        scales linearly with replicas.
```

### 10.3 Durability

```
  Prob{all W acks but we lose data}  ≈  prob{≥ W replicas fail
                                          before replication to others}

  Higher W → higher durability.
  Higher R → better detection of stale data (read repair).
```

### 10.4 Availability

```
  Probability of a successful write = P(at least W of N are up)
     N=3, W=2, per-node uptime 99.9% → 99.9997% write availability

  Higher W → lower availability during partial failures.
```

---

## 11. Interview Q&A

### Q1. "Why does W + R > N give strong consistency?"

> Any write that succeeds is on at least W nodes. Any read queries at
> least R nodes. By the pigeonhole principle, if W + R > N, those two
> sets must share at least one node — so the read always sees the
> write (picking the freshest via timestamp or HLC).

### Q2. "What's the difference between strict and sloppy quorum?"

> **Strict**: writes go only to the designated "home" replicas; if
> fewer than W are reachable, the write fails. **Sloppy**: writes go
> to any W nodes currently reachable, with a "hint" to hand off back
> to the intended home when it recovers. Strict has stronger immediate
> consistency; sloppy has higher availability during failures.

### Q3. "Why odd cluster sizes?"

> `N = 2F + 1` nodes tolerate `F` failures with a majority quorum.
> Going from 3 → 4 doesn't raise F (still 1) but increases cost and
> raises P(losing 2 nodes simultaneously). Even cluster sizes also
> make voting ties possible. The only reason to use an even count
> is when you're adding a **witness/arbiter** for the odd tiebreaker.

### Q4. "How do you pick N, W, R for a system that needs fast reads and strong consistency?"

> Start with N=3, W=3, R=1 — every write touches all replicas, so any
> single read is fresh. Downside: writes are slow and fail on any
> replica outage. If writes are too slow or availability suffers,
> drop to W=2, R=2 (still W+R>N), paying a slight read-latency cost.

### Q5. "What happens if W=1 and R=1 in a 3-node AP system?"

> Extremely fast, extremely weak. A write hits one replica and
> returns; a read may hit any of the three. You may read data that
> doesn't exist yet, lose writes under replica failure, and can
> easily have concurrent writes that diverge. Useful only for
> low-value, write-heavy workloads (metrics, cache-like data).

### Q6. "Explain hinted handoff."

> When a target replica is down, a coordinator writes to an
> alternative node tagged with a "hint" saying "this belongs to
> node X, please deliver when it recovers." The stand-in stores the
> hint, pings X periodically, and streams the pending writes once
> X is back. Keeps availability high without losing updates — but
> hints have a TTL and can be lost, so you also need **anti-entropy**
> (Merkle tree repair) to eventually converge.

### Q7. "How does Raft use quorums differently from Cassandra?"

> Raft uses **majority quorums** for both election and commit — the
> same set of N voting members. Cassandra's N/W/R is **per-key**:
> N is the replication factor for that key, and W/R are tunable
> per-query. Raft targets strong consistency and has exactly one
> leader per term; Cassandra targets high availability and has no
> leader — any node is a coordinator.

### Q8. "You have 5 nodes. What's the smallest W and R that gives strong consistency and tolerates 1 failure?"

> Need W + R > 5 and W ≤ 4, R ≤ 4 (so one failure still leaves enough
> nodes). Minimum: W=3, R=3. Alternatively W=4, R=2 (or W=2, R=4)
> depending on whether you optimize for reads or writes.

### Q9. "What's a witness replica and when would you use one?"

> A node that participates in voting but stores no (or minimal) data.
> Useful in two-DC deployments where each DC holds two data-bearing
> replicas — the witness lives in a third location and breaks the
> tie during a cross-DC partition. Used by MongoDB, Microsoft SQL
> Server AlwaysOn, and some Raft deployments.

---

## 12. Further Reading

- Gifford, "Weighted Voting for Replicated Data" (1979) — original quorum system
- DeCandia et al., "Dynamo: Amazon's Highly Available Key-value Store" (2007)
- Howard et al., "Flexible Paxos" (2016)
- Kleppmann, "Designing Data-Intensive Applications" Ch. 5 & 9
- Bailis et al., "Probabilistically Bounded Staleness" (2012) — quantifying quorum staleness

---

> **Previous:** [05-NetworkPartitions.md](./05-NetworkPartitions.md) ·
> **Next:** [07-ReplicationStrategies.md](./07-ReplicationStrategies.md)
