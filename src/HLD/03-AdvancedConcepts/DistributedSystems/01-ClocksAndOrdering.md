# Clocks & Ordering in Distributed Systems

> **Difficulty:** Medium | **Time:** 2-3 hours | **Priority:** Must Know

Without a shared clock, two machines cannot reliably agree on *"what happened
first."* Yet every distributed feature — replication, leader election,
transactions, causal messaging, conflict resolution — depends on ordering.
This document walks through every practical clock used today, from the
naïve **wall-clock** all the way to Google's **TrueTime**.

---

## Table of Contents

1. [Why This Matters](#1-why-this-matters)
2. [Physical Clocks & Why They Lie](#2-physical-clocks--why-they-lie)
3. [NTP in Practice](#3-ntp-in-practice)
4. [The Happens-Before Relation](#4-the-happens-before-relation-lamport-1978)
5. [Lamport Clocks (Scalar Logical Clocks)](#5-lamport-clocks)
6. [Vector Clocks](#6-vector-clocks)
7. [Version Vectors & Dotted Version Vectors](#7-version-vectors--dotted-version-vectors)
8. [Hybrid Logical Clocks (HLC)](#8-hybrid-logical-clocks-hlc)
9. [TrueTime (Spanner) — Intervals Instead of Points](#9-truetime-spanner--intervals-instead-of-points)
10. [Picking a Clock for Your Problem](#10-picking-a-clock-for-your-problem)
11. [Common Bugs & Pitfalls](#11-common-bugs--pitfalls)
12. [Interview Q&A](#12-interview-qa)

---

## 1. Why This Matters

```
 What can go wrong without ordering?
 ───────────────────────────────────

  USER'S CALENDAR                    BANK ACCOUNT
  ───────────────                    ────────────
  09:00: Create event "lunch"         09:00: Deposit  $100
  09:01: Delete  event "lunch"        09:01: Transfer $80 out
                                      
  Replicated out-of-order →           Replicated out-of-order →
  event exists forever.               $80 transferred BEFORE deposit →
                                      overdraft alarm, frozen card.

  SOCIAL MEDIA                        GIT / FILE SYSTEMS
  ────────────                        ──────────────────
  Alice: "I lost my phone"            Commit A parents Commit B
  Alice: "Found it!"                  Commit B parents Commit A
  Bob sees reversed → confused.       Impossible without ordering.
```

Every one of these bugs is an **ordering bug**. The rest of this document
is about the tools we use to prevent them.

---

## 2. Physical Clocks & Why They Lie

Every computer has one or more hardware clocks:

```
HARDWARE CLOCKS IN A TYPICAL SERVER
───────────────────────────────────

  ┌────────────────────┐
  │ Crystal Oscillator │   ~32.768 kHz or ~14.318 MHz
  │    (TCXO / XO)     │   Drifts ~10-100 ppm   → ~1 s/day
  └─────────┬──────────┘
            │ ticks
            ▼
  ┌────────────────────┐
  │  CPU TSC (counter) │   Fast, monotonic per core,
  │                    │   but may skew across cores/sockets
  └─────────┬──────────┘
            │
            ▼
  ┌────────────────────┐
  │  OS clocks         │   CLOCK_REALTIME   (wall clock, NTP-adjusted)
  │                    │   CLOCK_MONOTONIC  (only goes forward)
  │                    │   CLOCK_BOOTTIME   (counts during sleep)
  └────────────────────┘
```

### 2.1 Two things go wrong

```
A. SKEW    (two clocks disagree on "now")

   Node A:  10:00:00.000        Node B:  10:00:00.300
           ┊                            ┊
           └───── 300 ms apart ─────────┘

   Caused by: independent NTP adjustments, boot times, VM
              migrations, cosmic rays, leap seconds.

B. DRIFT   (same clock ticks at slightly the wrong rate)

   Nominal: 1.000000 s per real second
   Actual : 1.000037 s   →  +37 ppm drift
                             ~3.2 seconds gained per day.
```

### 2.2 Why you must never mix clocks across machines

```
 BAD (will lose data some day):

     if (event.timestamp > last_seen_timestamp) apply(event);

     Two nodes, clocks differ by 200ms → late event discarded
     on one replica, kept on another → replicas diverge.

 GOOD:

     Use a LOGICAL or HYBRID clock.
     Use wall clock only for human-readable UIs, audit logs,
     and "how old is this thing" style heuristics.
```

### 2.3 Monotonic vs. wall clock

```
                 CLOCK_REALTIME           CLOCK_MONOTONIC
                 ───────────────           ───────────────
 "What time now"   YES                      NO (arbitrary epoch)
 Can jump back     YES (NTP corrections)    NO (guaranteed forward)
 Good for          logging, audit, UI       durations, timeouts,
                                            retry backoff, latency

 RULE: measure DURATIONS with monotonic; display TIMES with realtime.

 Languages:
   Java:     System.currentTimeMillis()  (real)
             System.nanoTime()           (monotonic)
   Go:       time.Now()                  (both combined)
   Python:   time.time()                 (real)
             time.monotonic()            (monotonic)
```

---

## 3. NTP in Practice

NTP (Network Time Protocol, RFC 5905) syncs machine clocks to
"true" time via a hierarchy:

```
                     ┌─────────────────────────┐
                     │  STRATUM 0              │
                     │  GPS / Atomic clocks    │   (not on the network)
                     └────────────┬────────────┘
                                  │
                     ┌────────────▼────────────┐
                     │  STRATUM 1              │   Directly-attached
                     │  time.nist.gov, etc     │   reference servers
                     └────────────┬────────────┘
                                  │
                     ┌────────────▼────────────┐
                     │  STRATUM 2              │   pool.ntp.org,
                     │                         │   AWS Time Sync
                     └────────────┬────────────┘
                                  │
                     ┌────────────▼────────────┐
                     │  STRATUM 3+             │   your app servers
                     └─────────────────────────┘

 Up to stratum 15;  stratum 16 = unsynced/unusable.
```

### 3.1 How a single NTP exchange works

```
     Client                                Server
       │   T1 = client-send time            │
       │ ─────── request ─────────────────► │
       │                                    │ T2 = server-receive
       │                                    │ T3 = server-send
       │ ◄────── response ───────────────── │
       │   T4 = client-receive              │

  Round-trip delay δ  = (T4 - T1) - (T3 - T2)
  Clock offset     θ  = ((T2 - T1) + (T3 - T4)) / 2

  Client then SLEWS or STEPS its clock by θ.
```

### 3.2 What can go wrong

```
 STEP vs SLEW
 ────────────
   |offset| < 128 ms  → SLEW (gently speed up or slow down)
   |offset| ≥ 128 ms  → STEP (jump to correct time)

   STEP can make clock GO BACKWARDS → breaks "timestamp > last"
   logic, TTLs, distributed locks that compare wall times.

 LEAP SECOND (has caused real outages)
 ────────────
   June 30 2012 leap second → Reddit, LinkedIn, Cloudflare outages.
   Modern fix: "LEAP SMEAR" — spread the extra second over 24h.
                Google Public NTP, AWS Time Sync, Facebook all do this.

 TYPICAL ACCURACY
 ────────────────
   Public internet             : 10-100 ms
   LAN / cloud AZ              :  1-10 ms
   PTP (IEEE 1588, HW assist)  :  sub-microsecond
   GPS-synced stratum 1        :  ~1 µs
```

### 3.3 The "clock is close but not equal" trap

Even with NTP running happily, two nodes typically differ by
**5-50 ms**. That is an eternity compared to an in-memory operation.
Any algorithm whose correctness depends on "clocks are within ε"
must either **use a logical clock** or **explicitly bound ε** the
way TrueTime does (§9).

---

## 4. The Happens-Before Relation (Lamport, 1978)

The canonical partial order of events "→" in a distributed system:

```
 a → b  ("a happens before b")  iff one of:

   (1) a and b are on the same node and a came first, OR
   (2) a is a send and b is the matching receive, OR
   (3) there exists c with  a → c → b   (transitivity)

 If NEITHER a → b NOR b → a, then a and b are CONCURRENT (a ∥ b).
```

### 4.1 Visual example

```
             time ───────────────────────►
 Node A:   e1 ─────── e2(send m) ───────── e5 ─────────
                            │
                            ▼
 Node B:   e3 ─────── e4 ──(recv m)────── e6 ──────────

 Happens-before relationships:
   e1 → e2    (same node, order)
   e2 → e4    (send → receive)
   e1 → e4    (transitivity)
   e4 → e6    (same node)
   e2 → e6    (transitivity)

 Concurrent (∥):
   e3 ∥ e1    (neither reaches the other)
   e5 ∥ e6    (no message between them)
```

Happens-before is a **partial order**: some pairs of events are
simply unordered. Understanding this is the single most important
insight in distributed systems.

---

## 5. Lamport Clocks

A **scalar** logical clock — one counter per node.

### 5.1 Rules

```
 Each node keeps integer L (starts at 0).

   On ANY local event:       L ← L + 1
   On send(msg):             L ← L + 1;        attach L to msg
   On receive(msg, L_m):     L ← max(L, L_m) + 1
```

### 5.2 Walk-through

```
  A │ [1]─────[2]────────────────[6]───────────────
    │  local   send ─────┐        recv (max(5,3)+1=6)
    │                    │         ▲
    │                    ▼         │
  B │ [1]─────[2]────[3]─(4)──────[5]───────────────
    │  local   local   recv   local  send ──┘
    │                 (max(1,2)+1=3)

 Guarantee:      a → b  ⇒  L(a) < L(b)      (sound)
 NOT guaranteed: L(a) < L(b)  ⇒  a → b
                 L(a) < L(b) could be concurrent — two nodes
                 may simply both have L=5 for unrelated events.
```

### 5.3 Total order from Lamport

If you need a **total order**, break ties using node id:

```
  event (L, node_id) < (L', node_id')   iff
     L < L'   OR   (L == L' AND node_id < node_id')

  Useful for:
    - Replicated log sequence numbers
    - Deterministic tie-breaking for LWW conflict resolution
```

### 5.4 What Lamport clocks CAN'T do

```
 Cannot detect CONCURRENCY.

   A: L=5  "add 'red' to cart"
   B: L=5  "add 'blue' to cart"

   Lamport alone can't tell these are concurrent vs one causing
   the other.  You need VECTOR CLOCKS for that.
```

---

## 6. Vector Clocks

One counter **per node**, stored as a vector `V[i]`. Captures full
causal history — and uniquely detects concurrency.

### 6.1 Rules

```
 Each node i keeps V[i] = [0, 0, ..., 0] of length N.

   On ANY local event at node i:   V[i][i] ← V[i][i] + 1
   On send from i:                 V[i][i] ← V[i][i] + 1; attach V[i]
   On receive at j of (V_m):       V[j][k] ← max(V[j][k], V_m[k])  ∀k
                                   V[j][j] ← V[j][j] + 1
```

### 6.2 Three-node walk-through

```
 Start:   A=[0,0,0]   B=[0,0,0]   C=[0,0,0]

 A does local event           → A=[1,0,0]
 A sends m1 to B              → A=[2,0,0]     msg carries [2,0,0]
 B receives m1                → B=[2,1,0]
 B does local event           → B=[2,2,0]
 C does local event (unrelated)→ C=[0,0,1]
 B sends m2 to C              → B=[2,3,0]     msg carries [2,3,0]
 C receives m2                → C=[2,3,2]
```

### 6.3 Comparing two vector clocks

```
 Given V1 and V2 (same length):

   V1 ≤  V2   iff  V1[i] ≤ V2[i]  for ALL i
   V1 <  V2   iff  V1 ≤ V2  AND  V1 ≠ V2      (V1 happens-before V2)
   V1 =  V2   iff  identical
   V1 ∥  V2   iff  neither V1 ≤ V2 nor V2 ≤ V1  (CONCURRENT — conflict!)
```

### 6.4 Detecting concurrent writes (Dynamo-style)

```
 Two shopping-cart replicas, same user:

   Replica R1 receives:  put(cart, {a,b})   → ctx=[R1:1, R2:0]
   Replica R2 receives:  put(cart, {a,c})   → ctx=[R1:0, R2:1]

   When R1 and R2 gossip:
     [R1:1, R2:0]  ∥  [R1:0, R2:1]   → CONCURRENT
     Return BOTH (siblings) to the application.

   Application merges → {a, b, c}  (set union rule)
   Writes back with ctx = [R1:1, R2:1].
```

This is exactly how Amazon's original Dynamo shopping cart
never lost an "add to cart."

### 6.5 Downsides

```
 1. Vector size grows with cluster size (N machines → N-int vector).
 2. What counts as a "node"?
      - Every server?   Too many.
      - Every client?   Vector explodes.
 3. Garbage: once a node leaves, its slot lingers.
```

---

## 7. Version Vectors & Dotted Version Vectors

**Version vectors** are vector clocks applied **per-key** in a DB.
Each stored object carries a small vector describing which writers
have observed which versions.

### 7.1 The "anonymous writes" problem

Classic vector clocks assume every write is tagged with a stable
"actor id". In reality many writes come through load-balanced
coordinators, so **clients can't be the actor**:

```
 BAD:
   Client writes through coordinator C1  → V[C1]++
   Next write through coordinator C2     → V[C2]++  (different actor!)
   Even the SAME client's sequential writes appear concurrent.
```

### 7.2 Dotted Version Vectors (DVV)

DVVs fix this by pairing **(actor, counter) dots** with the vector.
A write gets a single dot `(node_id, counter)` plus the vector it
"saw" when it happened.

```
 Each value stored:
    value  +  [dot=(node, counter), context=V]

 Two values conflict iff neither's dot is dominated by the other's V.

 Pros:   Bounded size (O(N) with N server nodes, not N clients).
         Correctly handles anonymous writes.
 Used by: Riak 2.0+.
```

---

## 8. Hybrid Logical Clocks (HLC)

HLC combines wall-clock time (so timestamps look like real time)
with a logical counter (so ordering works even under skew).

### 8.1 Structure

```
   HLC = (pt, l, node_id)

     pt        = "physical time" component (ms since epoch, roughly)
     l         = logical counter, bumped to preserve causality
     node_id   = tiebreaker for total order
```

### 8.2 Update rules

```
 On LOCAL event at node i (wall = current wall clock ms):
   pt_new = max(pt_old, wall)
   if pt_new == pt_old:  l ← l + 1
   else:                 l ← 0
   HLC ← (pt_new, l)

 On RECEIVE of message with HLC_m = (pt_m, l_m):
   pt_new = max(pt_old, pt_m, wall)
   if    pt_new == pt_old == pt_m:  l = max(l_old, l_m) + 1
   elif  pt_new == pt_old:          l = l_old + 1
   elif  pt_new == pt_m:            l = l_m + 1
   else:                             l = 0
   HLC ← (pt_new, l)
```

### 8.3 Example

```
 Wall clocks:  A=100ms   B=95ms    (5ms of skew)

 A local event:       HLC_A = (100, 0)
 A sends to B:        msg carries (100, 0)
 B receives (wall=95):
     pt_new = max(95, 100, 95) = 100
     pt_new ≠ pt_old (95) and pt_new == pt_m (100) → l = l_m + 1 = 1
     HLC_B = (100, 1)

 Guarantee:  HLC always MONOTONICALLY INCREASES across causally
             related events, even if B's wall clock lags.
```

### 8.4 Why HLC is so widely used

```
 + 64-bit representation → cheap to store per row/version
 + Close to wall-clock time → great for debugging, logs, TTLs
 + Monotonic across causal chains → correct ordering
 + No external hardware required (unlike TrueTime)

 Used by:  CockroachDB, YugabyteDB, MongoDB (cluster time),
           TiDB, ScyllaDB-internal, FaunaDB
```

---

## 9. TrueTime (Spanner) — Intervals Instead of Points

Google Spanner takes the opposite approach: use **real time** but
report it as a **confidence interval**.

### 9.1 The API

```
    TT.now()  →  [earliest, latest]        (an interval)
    TT.after(t)  →  true iff now().earliest > t
    TT.before(t) →  true iff now().latest  < t
```

The interval width `ε = (latest - earliest) / 2` is typically **~3-7 ms**
in Google's fleet, thanks to GPS receivers and atomic clocks in
every datacenter, continuously disciplining the servers' clocks.

### 9.2 Architecture

```
 Every Google datacenter runs several TIME MASTERS.

  ┌──────────┐        ┌──────────┐        ┌──────────┐
  │GPS master│        │GPS master│        │ATOMIC mstr│   (Armageddon-proof)
  └────┬─────┘        └────┬─────┘        └────┬─────┘
       │                   │                   │
       └───────────────┬───┴──────────┬────────┘
                       │              │
                       ▼              ▼
                  every Spanner server every 30s
                  runs Marzullo's algorithm on master answers
                  → discards outliers → computes [earliest, latest]
```

### 9.3 The "commit wait" trick

To commit a transaction at time `T`:

```
 1. Pick T = TT.now().latest   (the latest possible "now")
 2. SLEEP until TT.after(T)    (until no future TT.now() could
                                report < T)
 3. Reply commit to client.

 Result: ANY READ that starts after the commit is guaranteed
 to see a TrueTime later than T → will see this commit.

 → External consistency (linearizability) across the whole planet.
 → Cost: ~2ε ≈ 14 ms of wait time per transaction.
```

### 9.4 Why most systems can't do this

```
 Requires:
   - GPS antennas (roof access)
   - Atomic clocks (expensive)
   - Datacenter-wide time infrastructure
   - OS-level hooks

 For everyone else: HLC gets you 95% of the benefit with pure software.
```

---

## 10. Picking a Clock for Your Problem

```
┌────────────────────────────────────────────────────────────────┐
│                    CLOCK DECISION TREE                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Do you only need DURATION/TIMEOUT?                            │
│    → CLOCK_MONOTONIC  (System.nanoTime)                        │
│                                                                │
│  Do you only display it to humans / use in logs?               │
│    → Wall clock + NTP                                          │
│                                                                │
│  Do you need to ORDER events within one datacenter?            │
│    → Lamport clock (or HLC if you also want wall-like values)  │
│                                                                │
│  Do you need to DETECT CONCURRENT updates?                     │
│    → Vector clocks (or DVV if anonymous writes)                │
│                                                                │
│  Do you need STRICT serializable transactions across regions?  │
│    → TrueTime (if you're Google), HLC + per-shard consensus    │
│      (if you're CockroachDB), or Paxos/Raft ordering.          │
│                                                                │
│  Do you want "causal session" guarantees for a user?           │
│    → Client carries a vector/HLC token across requests.        │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 10.1 Side-by-side comparison

```
┌──────────────┬────────┬────────────┬───────────────┬───────────┐
│ Clock        │ Size   │ Detects    │ Close to      │ Used by   │
│              │        │ concurrency│ real time?    │           │
├──────────────┼────────┼────────────┼───────────────┼───────────┤
│ Wall + NTP   │ 8 B    │ No         │ Yes (~ms)     │ Everyone  │
│ Lamport      │ 8 B    │ No         │ No            │ Simple DBs│
│ Vector       │ 8·N B  │ Yes        │ No            │ Dynamo    │
│ DVV          │ O(N) B │ Yes        │ No            │ Riak      │
│ HLC          │ 8-16 B │ Via causal │ Yes (~ms)     │ Cockroach │
│ TrueTime     │ 16 B   │ Via wait   │ Yes (bounded ε)│ Spanner  │
└──────────────┴────────┴────────────┴───────────────┴───────────┘
```

---

## 11. Common Bugs & Pitfalls

### 11.1 Using wall time for leader leases

```
 BAD:
    if (now() < lease.expires) I_am_leader = true

 Problem: NTP step backwards, or clock skew, can extend a lease
 accidentally → TWO leaders → split-brain → data loss.

 GOOD:
    Use monotonic clock for lease expiration math.
    Additionally use FENCING TOKENS so storage rejects stale leaders.
```

### 11.2 "timestamp > last_seen" for event ordering

```
 BAD:
    if (event.ts > last_seen.ts) apply(event)

 Clock skew between producers → later events look older → dropped.

 GOOD:
    Use monotonic sequence number per source, or HLC.
```

### 11.3 Log timestamps in microseconds

```
 Two events in same ms get the same timestamp →
 debugging ordering becomes impossible.

 Fix: use HLC, or append a nanosecond part, or include a
 per-process sequence number in the log.
```

### 11.4 Vector clock explosion

```
 Assigning vector entries to every CLIENT → vectors grow forever.

 Fix: assign to SERVER nodes (bounded), or use DVVs.
```

### 11.5 Leap seconds

```
 If you ever write code that assumes:
    "there are always 86,400 seconds in a day"
 prepare for outages. Use leap-smearing NTP, or never treat
 the difference of two wall times as authoritative.
```

---

## 12. Interview Q&A

### Q1. "Why can't we just use NTP-synced wall clocks to order events?"

> NTP accuracy is ~1-50 ms between machines, clocks can jump backwards
> during corrections, and some algorithms execute in microseconds.
> So wall-clock ordering is *probabilistic at best* and *outright wrong*
> frequently enough to corrupt data. Use Lamport, vector, or HLC for
> real ordering guarantees.

### Q2. "When would you use Lamport vs. vector clocks?"

> Lamport when you need a **total order** but don't care about detecting
> concurrency (e.g., replicated log sequence numbers with a single leader).
> Vector clocks when you need to **detect concurrent writes** for
> leaderless replication (Dynamo/Cassandra) or CRDT merges.

### Q3. "How does CockroachDB order transactions across regions without GPS?"

> It uses **Hybrid Logical Clocks (HLC)**. Each node's HLC is bounded
> close to wall-clock by NTP, but logical counters preserve causality
> across messages. When a transaction is read, it's assigned an HLC
> timestamp; the commit must wait for any possibly-concurrent txn to
> resolve. This gives serializable isolation without Spanner-level
> hardware.

### Q4. "What's 'commit wait' in Spanner?"

> After a transaction chooses commit timestamp `T` (= `TT.now().latest`),
> it sleeps until `TT.now().earliest > T`. This guarantees that NO
> subsequent transaction on any server could ever see a `now()` less
> than `T`, so future reads are guaranteed to observe the commit.
> Cost: ~2ε ≈ 14 ms per commit.

### Q5. "A user posts a comment, refreshes, and doesn't see it. Why?"

> Read hit a replica that hadn't yet received the write. To fix:
> the client sends its last observed HLC/vector clock on reads;
> the replica serves the read only if it has advanced past that
> clock, otherwise it waits or forwards. This is how MongoDB's
> "causal consistency sessions" work.

### Q6. "Can two events have the same Lamport timestamp?"

> Yes — events on different nodes with no causal relation can share
> the same integer. Break ties with `(L, node_id)` for a total order,
> but know that the total order is **arbitrary** for concurrent events.

### Q7. "How big is a vector clock in a 10k-node cluster?"

> Naively 10k × 8 bytes = 80 KB per object — unusable. That's why
> production systems either (a) use **per-key DVVs** limited to the
> few replicas that actually touch each key, or (b) fall back to HLC
> which is constant-sized.

### Q8. "What happens at a leap second?"

> The kernel either inserts second 60 (POSIX undefined) or sleeps for
> an extra second. Poorly-written code that computes `end - start` or
> treats `time_t` as monotonic can crash, loop, or deadlock.
> Modern clouds use **leap smearing**: spread the leap second over 24h.

---

## 13. Further Reading

- Lamport, "Time, Clocks, and the Ordering of Events in a Distributed System" (1978)
- Fidge & Mattern, "Time and Causal Dependency in Distributed Systems" (1988) — vector clocks
- Kulkarni et al., "Logical Physical Clocks and Consistent Snapshots" (2014) — HLC paper
- Corbett et al., "Spanner: Google's Globally-Distributed Database" (2012) — TrueTime
- Preguiça et al., "Dotted Version Vectors: Logical Clocks for Optimistic Replication" (2010)

---

> **Next up:** [02-ConsistencyModels.md](./02-ConsistencyModels.md)
