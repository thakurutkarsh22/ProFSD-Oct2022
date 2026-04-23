# Deadlock Detection in Distributed Systems

> **Difficulty:** Hard | **Time:** 2.5 hours | **Priority:** Must Know

Any system that lets multiple actors acquire multiple resources under
mutual exclusion eventually has to answer one question: **"what do we
do when two or more actors wait for each other forever?"** In a single
database, the engine builds a *wait-for graph*, finds a cycle, and
kills a victim. In a **distributed** system there is no single
observer of the graph — every participant only sees its local slice.
This document covers why distributed deadlock is genuinely harder,
the classical detection algorithms (centralized, hierarchical, and
edge-chasing), how real systems resolve it, and the pragmatic
timeout-based approach most production systems actually use.

---

## Table of Contents

1. [Why Deadlock Matters](#1-why-deadlock-matters)
2. [The Coffman Conditions](#2-the-coffman-conditions)
3. [Wait-For Graph (WFG) Basics](#3-wait-for-graph-wfg-basics)
4. [Local vs Distributed Deadlock](#4-local-vs-distributed-deadlock)
5. [Three Strategies: Prevention, Avoidance, Detection](#5-three-strategies-prevention-avoidance-detection)
6. [Centralized Detection](#6-centralized-detection)
7. [Hierarchical Detection (Menasce–Muntz)](#7-hierarchical-detection-menascemuntz)
8. [Distributed Edge-Chasing (Chandy–Misra–Haas)](#8-distributed-edge-chasing-chandymisrahaas)
9. [Path-Pushing (Obermarck)](#9-path-pushing-obermarck)
10. [Phantom Deadlocks & Why Global Snapshots Matter](#10-phantom-deadlocks--why-global-snapshots-matter)
11. [Resolution: Picking and Rolling Back the Victim](#11-resolution-picking-and-rolling-back-the-victim)
12. [The Pragmatic Alternative: Timeouts](#12-the-pragmatic-alternative-timeouts)
13. [Real-World Systems](#13-real-world-systems)
14. [Design Checklist](#14-design-checklist)
15. [Interview Q&A](#15-interview-qa)

---

## 1. Why Deadlock Matters

```
  HEALTHY                            DEADLOCKED
  ───────                            ──────────
   T1 ──► holds A, wants B            T1 ──► holds A, waits for B
   T2 ──► holds B, finishes            │
   T1 gets B, finishes                 ▼
                                      T2 ──► holds B, waits for A
                                       │
                                       └─────► cycle; both wait forever
```

Without detection, deadlocked transactions:

```
  HOLD LOCKS FOREVER       other txns queue behind, pool exhausted
  BURN CONNECTIONS         DB connection pool drained
  BLOCK BUSINESS FLOWS     "Place Order" hangs; customer retries
  AMPLIFY LOAD             retries spawn *more* locked workers
  SILENTLY DEGRADE         p99 latency climbs; no crash, no alert
```

In distributed systems the damage compounds: a deadlock on two
services can block a third via cascading waits, and no single
participant has enough information to even *see* the problem.

---

## 2. The Coffman Conditions

A deadlock requires **all four** Coffman conditions simultaneously
(Coffman, Elphick & Shoshani, 1971). Break any one and deadlock is
impossible.

```
  1. MUTUAL EXCLUSION     resource held by at most one owner at a time
  2. HOLD AND WAIT        holder is allowed to request more resources
  3. NO PREEMPTION        a resource cannot be forcibly taken away
  4. CIRCULAR WAIT        a cycle exists in the wait-for graph
```

### 2.1 How each strategy breaks a condition

```
  Strategy          Breaks which condition?
  ───────────────────────────────────────────
  lock-free / CAS   MUTUAL EXCLUSION
  acquire-all-or-
    none (wait-die) HOLD AND WAIT
  timeout + abort   NO PREEMPTION  (forcibly release)
  lock ordering     CIRCULAR WAIT  (never waits "backwards")
  detection         none — allow it, detect, recover
```

Most real systems **mix** strategies: lock ordering inside one service,
timeouts across services, detection only where both fail.

---

## 3. Wait-For Graph (WFG) Basics

A Wait-For Graph is a directed graph where:

```
  Node = a transaction / process
  Edge T_i ──► T_j  means  "T_i is blocked waiting on a resource held by T_j"

  Deadlock ⇔ there is a directed CYCLE in the WFG.
```

### 3.1 Example: four-transaction cycle

```
         T1 ──► T2
          ▲      │
          │      ▼
         T4 ◄── T3

   All four are blocked; no one can make progress.
```

### 3.2 Distinction from the resource-allocation graph (RAG)

A RAG has two node types (processes *and* resources). A WFG collapses
resources out: "T1 waits on R held by T2" becomes just `T1 → T2`.
Cycles in a WFG always mean deadlock; in a RAG with multi-instance
resources a cycle is only a *necessary* condition.

### 3.3 Algorithmic cost

```
  Cycle detection in directed graph:
    DFS or Tarjan's SCC  — O(V + E)

  In a distributed system V and E are SCATTERED.
  The hard part is not the graph algorithm — it is
  BUILDING the global graph consistently while the
  system is changing under you.
```

---

## 4. Local vs Distributed Deadlock

```
 LOCAL (single DB engine)               DISTRIBUTED (across sites)
 ──────────────────────────             ────────────────────────
 One lock manager observes every        Each site has its own lock
 edge of the WFG.                        manager; sees only LOCAL edges.

 Cycle detection: in-memory DFS          A cycle may be
 over a small graph, every few           T1 → T2  (site A)
 hundred milliseconds.                   T2 → T3  (site B)
                                          T3 → T1 (site C)
                                         No single site sees the cycle.

 Resolve: pick lowest-cost victim,       Resolve: picking the victim
 kill + rollback, restart.               requires cross-site consensus
                                         or a designated coordinator.
```

### 4.1 Types of distributed deadlock

```
  RESOURCE DEADLOCK        classic lock-on-record cycle across sites
  COMMUNICATION DEADLOCK   each process waits for a message the other
                           will only send after receiving one
                           (RPC round-trip trapped in a cycle)
  PHANTOM DEADLOCK         WFG "cycle" that doesn't actually exist —
                           an artifact of snapshotting the graph while
                           edges are being added/removed (§10)
```

---

## 5. Three Strategies: Prevention, Avoidance, Detection

```
┌───────────────┬─────────────────────────────────────────────────────────┐
│  Strategy      │ Idea                                                    │
├───────────────┼─────────────────────────────────────────────────────────┤
│ PREVENTION     │ Design locks/protocol so deadlock is impossible.        │
│                │   - Fixed lock order (break circular wait)              │
│                │   - Lock all resources atomically                       │
│                │   - Wait-die / Wound-wait ordering                      │
├───────────────┼─────────────────────────────────────────────────────────┤
│ AVOIDANCE      │ Grant a request only if the resulting state is safe.   │
│                │   - Banker's algorithm (requires known max claims)      │
│                │   - Usually impractical in open distributed systems     │
├───────────────┼─────────────────────────────────────────────────────────┤
│ DETECTION &    │ Allow deadlocks, find them after the fact, kill a       │
│ RECOVERY       │ victim. Everything below this section is about this.    │
└───────────────┴─────────────────────────────────────────────────────────┘
```

### 5.1 Wait-Die vs Wound-Wait (timestamp-based prevention)

Both give each transaction a unique timestamp T at start and use age
to resolve conflicts without ever building a WFG:

```
  When Ti wants a lock held by Tj:

    WAIT-DIE       if Ti is OLDER than Tj  →  Ti WAITS
                   if Ti is YOUNGER       →  Ti DIES (abort, restart
                                             with same timestamp)

    WOUND-WAIT     if Ti is OLDER than Tj  →  Tj is WOUNDED (aborted)
                   if Ti is YOUNGER       →  Ti WAITS

  Both guarantee no cycle can form because waits only go in one
  direction of the age ordering. Wound-wait has fewer restarts in
  practice because older transactions preempt younger ones.
```

These are **prevention** schemes — no detector is needed. Google
Spanner and many MVCC systems use variants of wound-wait.

---

## 6. Centralized Detection

The simplest distributed-deadlock detector: elect one site as the
**global deadlock detector** (GDD). Every lock manager periodically
sends its local WFG edges to the GDD, which unions them and runs a
cycle-detection pass.

```
     site A            site B            site C
     ─────             ─────             ─────
     edges → ─────────────────────────────► GDD
                 ┌───────────────────┐
                 │ build global WFG  │
                 │ run DFS/SCC       │
                 │ if cycle:         │
                 │   pick victim     │
                 │   send abort msg  │
                 └───────────────────┘
                          │
                          ▼
                    (to victim's site)
```

### 6.1 Variants

```
  HO–RAMAMOORTHY ONE-PHASE:  each site sends its full WFG every T ms.
                             GDD alone decides.
  HO–RAMAMOORTHY TWO-PHASE:  first pass finds candidate cycles; second
                             pass re-validates to filter phantoms (§10).
```

### 6.2 Trade-offs

```
  PROS:     simple; uses well-known sequential algorithms;
            easy to reason about correctness.
  CONS:     single point of failure (GDD crash → no detection);
            hotspot for all WFG traffic;
            stale: edges detected on second-old snapshots;
            doesn't scale past a few dozen sites.
```

Centralized detection is still used in single-region RDBMS clusters
and in small service meshes where a control plane naturally exists
(e.g., a transaction coordinator in XA).

---

## 7. Hierarchical Detection (Menasce–Muntz)

Sites are arranged in a **tree**. Each internal node consolidates WFG
edges from its children and does a local cycle check before passing
the summary upward.

```
                    ┌─── root detector ───┐
                   /                       \
            ┌─ region-A GDD ─┐     ┌─ region-B GDD ─┐
           /        │         \             │
       siteA1    siteA2     siteA3        siteB1
```

### 7.1 Why bother

```
  Most deadlocks are LOCAL (within a rack, AZ, region).
  Detect them at the LOWEST level that sees the whole cycle.
  Only truly cross-region cycles escalate to the root.

  Result: dramatically less global traffic and no single hotspot.
```

### 7.2 Limitations

- Tree topology has to match deployment topology.
- Root is still a (smaller) point of failure.
- Reconfiguration on topology change is non-trivial.

Hierarchical detection is mostly of historical interest; its *idea*
(detect locally first, escalate only when needed) survives in modern
multi-region coordinators.

---

## 8. Distributed Edge-Chasing (Chandy–Misra–Haas)

The classical **truly distributed** algorithm (Chandy, Misra, Haas,
1983). No central detector, no global WFG; instead, the blocked
process sends a small **probe** along the wait-for edge. If the probe
comes back to its initiator, there is a cycle.

### 8.1 The probe

A probe is a triple:

```
  probe(i, j, k)   "initiator i sent this via j, currently at k"
```

### 8.2 Protocol

```
  When Pi becomes blocked on Pj:
      if Pi has not seen a probe for this wait yet:
          send probe(i, i, j)

  When Pk receives probe(i, j, k):
      if Pk is BLOCKED and there is an edge Pk → Pm:
          forward probe(i, k, m)  along each outgoing wait-for edge

      if Pk is NOT blocked:
          discard the probe

      if k == i (the probe returned to its initiator):
          DEADLOCK DETECTED — Pi is on a cycle
          initiate victim selection / abort
```

### 8.3 Example walk-through

```
 Cycle:  T1 ─► T2 ─► T3 ─► T1

 1.  T1 is blocked on T2.  Sends probe(1,1,2).
 2.  T2 is blocked on T3.  Forwards probe(1,2,3).
 3.  T3 is blocked on T1.  Forwards probe(1,3,1).
 4.  T1 receives probe(1,3,1).  Initiator is 1 → cycle → deadlock.
```

### 8.4 Why it's elegant

```
  NO GLOBAL STATE        probes discover cycles by TRAVERSING them.
  CHEAP MESSAGES         O(V) probes in the worst case; tiny payload.
  FULLY DISTRIBUTED      no coordinator, no hotspot.
  INCREMENTAL            only blocked processes send probes — idle
                         system has zero detection overhead.
```

### 8.5 Caveats

- Only detects **one** cycle at a time (the one touching the
  initiator). Multiple concurrent initiators may find overlapping
  cycles and each try to kill a victim — usually OK because killing
  any participant breaks all cycles through it.
- Still susceptible to phantom deadlocks if lock/unlock races with
  probe delivery (see §10).
- Works for resource deadlocks, not directly for communication
  deadlocks where the wait relation is over messages, not locks.

---

## 9. Path-Pushing (Obermarck)

Another distributed family of algorithms. Instead of probes, each
site periodically gathers partial WFG **paths** it knows about and
pushes them to neighbors.

```
  Site A knows:    T1 → T2 (locally)
  Site A's WFG includes a virtual "EX" node for external waits:
       T1 → T2 → EX(B)     meaning T2 is waiting at site B

  Site A sends the path to site B. Site B appends its own edges:
       T1 → T2 → T3 → EX(C)

  When a site receives a path whose endpoint loops back into itself
  (EX points "home"), it runs local cycle detection on the combined
  graph. If a cycle exists and the site owns the LOWEST-numbered
  transaction in the cycle, it declares deadlock and aborts.
```

### 9.1 Why the "lowest-id owner aborts" rule

Without a tiebreaker, multiple sites might detect the same cycle and
each abort a different victim. Agreeing on "the site that hosts the
lowest-id transaction is in charge" ensures exactly one abort per
cycle without extra consensus.

### 9.2 Status

Obermarck's algorithm shipped in IBM's R* distributed database in the
1980s and is a classic subject in graduate databases courses.
Modern systems have largely moved to edge-chasing or
timeout-based approaches.

---

## 10. Phantom Deadlocks & Why Global Snapshots Matter

A **phantom deadlock** is a cycle that appears in the reconstructed
global WFG but does not actually exist at any real moment in time.
It is an artifact of messages arriving out of order.

### 10.1 How phantoms happen

```
 Time ─────►
 t0: T1 waits on T2      (edge e1 added)
 t1: T2 finishes; releases; T1 proceeds  (edge e1 removed)
 t2: T3 waits on T4      (unrelated)
 t3: Detector collects "e1 added" (from an earlier snapshot)
     AND "T1 waits on T3" (from a later snapshot)
     AND "T3 waits on T2" (from yet another snapshot)
     → constructs cycle that never coexisted in real time.
```

### 10.2 Mitigations

```
  LOGICAL TIMESTAMPS      stamp every WFG edge with a Lamport clock
                          (see 01-ClocksAndOrdering.md); discard
                          cycles whose edges are mutually
                          "concurrent-inconsistent".
  TWO-PHASE VERIFICATION  detect cycle; then freeze participants and
                          re-check edges still hold before aborting.
  CONSISTENT SNAPSHOTS    use Chandy–Lamport global snapshot to get a
                          single causally-consistent WFG.
  EDGE-CHASING            probes only travel along currently-blocked
                          edges; a cycle detected by probe return is
                          necessarily real at the instant the probe
                          returned (but even CMH can still see stale
                          edges if unlock races with probe).
```

This is the single biggest reason **pure timeout-based detection is
so popular** — phantoms are indistinguishable from real deadlocks
without expensive verification, so many systems just let the timeout
do the work and accept an occasional false-positive abort.

---

## 11. Resolution: Picking and Rolling Back the Victim

Once a cycle is confirmed, one or more transactions must die so the
rest can proceed. Victim choice matters: the wrong victim wastes
work and may immediately redeadlock on restart.

### 11.1 Victim-selection criteria

```
  LEAST WORK DONE          rollback cost = log bytes written so far
  LOWEST PRIORITY          business-defined (e.g., batch < online)
  FEWEST LOCKS HELD        minimizes ripple on other waiters
  YOUNGEST (or OLDEST)     simple, deterministic; pairs with wait-die
  SMALLEST BLAST RADIUS    abort the txn whose compensation is
                           cheapest / not yet visible to users
  AVOID STARVATION         track restart count; never pick a txn that
                           has already been restarted K times
```

### 11.2 Rollback depth

```
  TOTAL ROLLBACK     abort the whole transaction; restart fresh.
                     Simpler, guaranteed deadlock-free on retry
                     (if using ordered locking or backoff).

  PARTIAL ROLLBACK   use savepoints; roll back only to the point
                     before the conflicting lock. Cheaper but harder
                     to guarantee forward progress.
```

### 11.3 Starvation

A badly chosen victim policy can cause the *same* transaction to be
chosen again and again:

```
  T1 starts small, takes 1 lock, gets deadlocked, is chosen as victim
  (least work done), restarts.
  On retry, still small → chosen again → never finishes.
```

Fix: count restarts per txn; cap at K; after K, prefer another
victim even if it has done more work. Most DBs implement this
with a "deadlock count" per session.

---

## 12. The Pragmatic Alternative: Timeouts

The dirty secret of production systems: **most of them do not run a
distributed deadlock detector at all.** They use lock-acquire
timeouts and treat any wait beyond T as "probably deadlocked, abort."

```
          ┌──────────────────────────────────────────────┐
          │  acquire_lock(resource, timeout=5s)          │
          │     blocks until granted OR timeout          │
          │     on timeout → rollback txn, maybe retry   │
          └──────────────────────────────────────────────┘
```

### 12.1 Why it's good enough

```
  NO COORDINATION           every node decides locally
  NO WFG MAINTENANCE        no edges, no probes, no graphs
  RESILIENT TO FAILURES     crashes look the same as deadlocks
  SIMPLE MENTAL MODEL       "if I wait too long, I fail"
  HANDLES PHANTOMS          phantoms self-resolve as they never
                            actually block anyone
```

### 12.2 Trade-offs

```
  FALSE POSITIVES           slow (but non-deadlocked) waits abort too
                            → tune timeout to p99 + safety margin
  LATE DETECTION            you wait the full timeout before acting;
                            a real detector would find the cycle in ms
  RETRY STORMS              many aborted txns retry simultaneously —
                            pair with exponential backoff + jitter
                            (see 12-BackPressureAndRetries.md)
  STARVATION                same txn may keep timing out; enforce a
                            max-retry + escalation
```

### 12.3 Hybrid in practice

Most real databases combine:

```
  LOCAL DETECTION           fast WFG inside one engine (MySQL InnoDB,
                            Postgres, Oracle) — sub-second precision.
  DISTRIBUTED TIMEOUTS      between services / across regions — 1 to
                            30 second lock-acquire timeouts.
  APPLICATION-LEVEL         idempotent retries with jittered backoff;
                            sagas (03-DistributedTransactions.md)
                            avoid global locks entirely.
```

---

## 13. Real-World Systems

```
┌─────────────────────┬──────────────────────────────────────────────────┐
│ System               │ Deadlock handling                                 │
├─────────────────────┼──────────────────────────────────────────────────┤
│ PostgreSQL           │ Local WFG; periodic cycle check                   │
│                      │ (`deadlock_timeout`, default 1s).                 │
│                      │ Kills cheapest victim; returns SQLSTATE 40P01.    │
│ MySQL InnoDB         │ Local WFG maintained continuously; instant cycle  │
│                      │ detection on lock request (not on timer).         │
│                      │ Kills txn with smaller row-lock count.            │
│ Oracle               │ Local statement-level detection; raises ORA-00060 │
│                      │ Distributed deadlock via timeout between DBs.     │
│ SQL Server           │ Background "lock monitor" thread every ~5s;       │
│                      │ selects victim by DEADLOCK_PRIORITY + cost.       │
│ Spanner (Google)     │ Wound-wait (timestamp prevention) — no detector.  │
│ CockroachDB          │ Wait queues with deadlock-detection heuristics;   │
│                      │ push-timestamps on older txns.                    │
│ DynamoDB             │ No row locks (optimistic + conditional writes);   │
│                      │ deadlock category does not apply at API level.    │
│ ZooKeeper / etcd     │ Leases + session timeouts; "deadlock" shows up    │
│                      │ as a session expiry, not a cycle to detect.       │
│ JVM (java.lang.Thread)│ `ThreadMXBean.findDeadlockedThreads()` walks     │
│                      │ the monitor wait graph for diagnostics.           │
│ Cassandra LWT        │ Paxos with per-row leaders; timeout-based only.   │
│ XA / 2PC coordinators│ Typically pair local-WFG detection + global      │
│                      │ transaction timeouts.                             │
└─────────────────────┴──────────────────────────────────────────────────┘
```

### 13.1 What production engineers should remember

- **Within one DB:** there IS a real detector; tune
  `deadlock_timeout`, log victims, alert on `deadlocks/sec`.
- **Between services / regions:** there is NO detector; use timeouts,
  sagas with compensations, and idempotent retries.
- If your design *needs* a cross-service deadlock detector, you
  probably have a design problem — consider sagas
  ([`../03-DistributedTransactions.md`](../03-DistributedTransactions.md))
  or stronger lock ordering instead.

---

## 14. Design Checklist

```
  [ ] Have you identified every place a thread/txn waits on another?
  [ ] Can you enforce a GLOBAL LOCK ORDER? (usually the best fix)
  [ ] Is every lock acquire bounded by a TIMEOUT?
  [ ] Does timeout-expiry trigger clean rollback + compensations?
  [ ] Are retries idempotent? Do they use jittered exponential backoff?
  [ ] Is there a MAX RETRY per transaction to avoid starvation?
  [ ] Is the local DB's deadlock detector on and logged?
  [ ] Are `deadlocks/sec` and `lock_wait_seconds_p99` on dashboards?
  [ ] Do you alert when a single txn has been aborted > K times?
  [ ] Does your design AVOID cross-service locks where possible?
      (prefer sagas, CRDTs, optimistic concurrency control)
```

---

## 15. Interview Q&A

### Q1. "What are the four Coffman conditions? Can you get deadlock without all four?"

> Mutual exclusion, hold-and-wait, no preemption, circular wait. All
> four are **necessary**; breaking any one prevents deadlock. Lock
> ordering eliminates circular wait; timeouts introduce preemption;
> atomic multi-lock acquire removes hold-and-wait; lock-free data
> structures remove mutual exclusion.

### Q2. "How does a wait-for graph detect deadlock?"

> Each node is a transaction, each directed edge `Ti → Tj` means
> "Ti is blocked waiting on a resource held by Tj." A directed cycle
> in the WFG is necessary and sufficient for deadlock. Detection is
> just DFS or Tarjan's SCC over the graph — O(V+E). The hard part in
> distributed systems isn't the algorithm, it's *building* the graph.

### Q3. "Why is distributed deadlock detection harder than local?"

> (1) No single observer — each site sees only its local WFG slice.
> (2) Messages propagating edges have variable latency, so the
> reconstructed global graph is never a consistent snapshot —
> leading to **phantom deadlocks** that don't actually exist.
> (3) The detector itself can fail, requiring either a leader or a
> fully distributed protocol. (4) Aborting a transaction may require
> cross-service compensations, not just a local rollback.

### Q4. "Explain the Chandy–Misra–Haas edge-chasing algorithm."

> When a process becomes blocked, it sends a small probe `(i, i, j)`
> along its wait-for edge. Each blocked recipient forwards the
> probe along its own wait-for edges, rewriting it to
> `(i, k, m)`. If the probe ever comes back to its initiator (the
> first component), a cycle exists and deadlock is declared.
> It's distributed, needs no global state, and only runs when some
> process is actually blocked.

### Q5. "What is a phantom deadlock?"

> A cycle that appears in the reconstructed global WFG but never
> existed at any real moment — caused by edges being added and
> removed between snapshots. Mitigations: logical timestamps on
> edges, Chandy–Lamport consistent snapshots, or two-phase detection
> that re-verifies edges before aborting. Most practical systems
> side-step the problem by using timeouts instead of reconstructing
> global WFGs at all.

### Q6. "Wait-die vs wound-wait?"

> Both use transaction timestamps to *prevent* deadlock. When Ti
> wants a lock held by Tj:
> - **Wait-die**: if Ti is older → it waits; if younger → it dies
>   (abort, retry with same timestamp).
> - **Wound-wait**: if Ti is older → it wounds Tj (aborts Tj); if
>   younger → it waits.
> Both guarantee waits only go one way along the age ordering, so
> no cycle can form. Wound-wait typically has fewer restarts.
> Spanner uses a variant.

### Q7. "How does a typical RDBMS handle deadlocks?"

> Locally, it maintains a wait-for graph. Either continuously
> (InnoDB) or on a timer (Postgres' `deadlock_timeout`, default 1s;
> SQL Server's lock monitor every ~5s). On detecting a cycle it
> picks the cheapest victim (least rollback cost, lowest priority,
> fewest locks held) and aborts it with a dedicated error code.
> The client is expected to retry.

### Q8. "Why do most microservice systems use timeouts instead of deadlock detectors?"

> Because real distributed deadlock detection requires either a
> central coordinator (single point of failure, scale ceiling) or a
> non-trivial distributed algorithm (edge-chasing, path-pushing).
> Timeouts are: simpler, per-node local decisions; resilient to
> failures (a crashed peer looks like a slow peer); and handle
> phantoms for free. The cost is false positives — healthy but slow
> waits get aborted — which you tune via timeout = p99 + margin and
> mitigate via jittered backoff + idempotent retries.

### Q9. "How would you avoid deadlocks in the first place?"

> In order of preference: (1) **Lock ordering** — always acquire
> locks in the same global order (e.g., by primary key); breaks
> circular wait entirely. (2) **Atomic multi-lock acquire** — take
> all locks up front or none. (3) **Optimistic concurrency control**
> — no locks; detect conflicts at commit. (4) **Sagas** — don't
> hold locks across services at all; use compensations. (5)
> **Timestamp-based prevention** (wound-wait) — no detector needed.

### Q10. "Your service occasionally deadlocks only in production. How do you debug?"

> Start with local data: enable the DB's deadlock log (InnoDB's
> `SHOW ENGINE INNODB STATUS`, Postgres' `log_lock_waits` and the
> server log after a deadlock abort). Record the two statements and
> lock sets involved — almost always reveals a lock-ordering
> violation. For JVM: `jstack` + `ThreadMXBean.findDeadlockedThreads`
> captures monitor deadlocks. Metrics to chart: `deadlocks/sec`,
> `lock_wait_seconds_p99`, `abort_rate_by_error_code`. Fix the
> ordering, then add a regression test that holds the two locks in
> the problematic order.

### Q11. "How does Google Spanner avoid ever needing a deadlock detector?"

> Spanner uses **wound-wait** — a timestamp-based prevention scheme.
> Every transaction gets a timestamp at start; when a conflict
> arises, older transactions preempt ("wound") younger ones, and
> younger ones wait for older. Because waits only ever go in one
> direction of the timestamp ordering, no cycle can form in the
> wait-for graph. No detector, no probes, no phantoms — just clean
> prevention powered by TrueTime.

### Q12. "What's the starvation risk in deadlock recovery, and how is it fixed?"

> If the victim-selection policy always picks the same transaction
> (e.g., always the one with least work done), a small transaction
> may be chosen as victim repeatedly and never complete. Fix: track
> a `deadlock_count` per transaction; after K aborts, bias the
> chooser away from it (pick a different victim even at higher
> rollback cost), or promote its priority so it survives the next
> cycle.

---

## 16. Further Reading

- Coffman, Elphick & Shoshani, "System Deadlocks" (ACM Computing
  Surveys, 1971) — the foundational paper defining the four conditions.
- Chandy, Misra & Haas, "Distributed Deadlock Detection"
  (ACM TOCS, 1983) — the edge-chasing probe algorithm.
- Obermarck, "Distributed Deadlock Detection Algorithm"
  (ACM TODS, 1982) — path-pushing in IBM R*.
- Menasce & Muntz, "Locking and Deadlock Detection in Distributed
  Data Bases" (IEEE TSE, 1979) — hierarchical detection.
- Knapp, "Deadlock Detection in Distributed Databases" (ACM
  Computing Surveys, 1987) — comprehensive survey.
- Bernstein, Hadzilacos & Goodman, "Concurrency Control and
  Recovery in Database Systems" (free online) — chapters 7–8.
- PostgreSQL docs — "Explicit Locking" / deadlock detection.
- Google Spanner paper (Corbett et al., OSDI 2012) — wound-wait in
  production.

---

> **Previous:** [13-ServiceDiscovery.md](./13-ServiceDiscovery.md) ·
> **Next:** back to [README.md](./README.md)
