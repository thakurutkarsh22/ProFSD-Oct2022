# Conflict Resolution

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Should Know

In any system where replicas can accept writes independently
(multi-leader, leaderless, offline clients, mobile apps), **concurrent
writes WILL collide on the same key**. Conflict resolution is how the
system decides what the "truth" is after a collision. Pick wrong and
you silently lose data.

---

## Table of Contents

1. [When Conflicts Happen](#1-when-conflicts-happen)
2. [Detecting That a Conflict Happened](#2-detecting-that-a-conflict-happened)
3. [Last-Writer-Wins (LWW)](#3-last-writer-wins-lww)
4. [Returning Siblings (Dynamo-style)](#4-returning-siblings-dynamo-style)
5. [Conflict-free Replicated Data Types (CRDTs)](#5-conflict-free-replicated-data-types-crdts)
6. [Operational Transformation (OT)](#6-operational-transformation-ot)
7. [Application-Specific Custom Merge](#7-application-specific-custom-merge)
8. [Comparison & When to Use Which](#8-comparison--when-to-use-which)
9. [Handling Externalities](#9-handling-externalities-side-effects)
10. [Real-World Examples](#10-real-world-examples)
11. [Interview Q&A](#11-interview-qa)

---

## 1. When Conflicts Happen

```
 MULTI-LEADER REPLICATION
    Alice writes "name=Alice" in US region.
    Bob   writes "name=Bobby" in EU region.
    Both leaders accept; async sync → conflict.

 LEADERLESS (Dynamo)
    Two coordinators hash-mod to different replicas:
       R1 gets write v1 at t=100
       R2 gets write v2 at t=100 (concurrent per vector clock)

 OFFLINE CLIENTS
    Mobile app edits note offline; server also has edits.
    On sync → conflict to resolve.

 NET SPLIT → HEAL
    During partition, both sides accept writes on the same key.
    On heal → all concurrent writes need resolution.
```

The common thread: **two writes neither observed, so neither can
"win" by being later causally.**

---

## 2. Detecting That a Conflict Happened

You can't resolve a conflict you didn't detect.

### 2.1 Causality tagging

Every write carries a **version context** (vector clock, DVV, or HLC).
On merge:

```
  V1 = [A:2, B:0]   (writer on node A)
  V2 = [A:0, B:1]   (writer on node B)

  V1 ≤ V2 ?   NO   (V1[A]=2 > V2[A]=0)
  V2 ≤ V1 ?   NO   (V2[B]=1 > V1[B]=0)

  → CONCURRENT → CONFLICT.
```

Without this, **the system can't tell** if one write is newer than the
other or if they happened in parallel.

### 2.2 Why wall-clock timestamps alone are NOT enough

```
  Alice writes at 10:00:00.500  ts=100500
  Bob   writes at 10:00:00.300  ts=100300    (but clock is skewed!)

  Bob's write actually happened LATER in real time.
  LWW on wall clock → picks Alice → Bob's edit SILENTLY LOST.
```

You need **causality** (happens-before), not just time.

---

## 3. Last-Writer-Wins (LWW)

The simplest strategy: pick whichever write has the **largest timestamp**.

### 3.1 Mechanics

```
  Each write tagged with a timestamp (HLC preferred; wall clock risky).
  On conflict, keep only the write with max ts.
  Tiebreak with node_id if timestamps equal.

  replica receives:
    put(k, v1, ts=100)
  later receives:
    put(k, v2, ts=99)   ← older, IGNORE.
```

### 3.2 Visual

```
  Replica R1 value: v1, ts=100500
  Replica R2 value: v2, ts=100300

  After LWW merge → everyone has (v1, 100500).
                    v2 DISCARDED. No evidence it ever existed.
```

### 3.3 Upsides

```
  ✓ O(1) resolution; no schema support needed.
  ✓ Deterministic — all replicas converge.
  ✓ Available in every K/V store (Cassandra, DynamoDB, Redis).
```

### 3.4 The brutal downside: SILENT DATA LOSS

```
  Two clients update the same order's shipping address nearly at
  the same time. LWW keeps one, drops the other. The user whose
  update was dropped never knows.

  "LWW is great as long as you don't mind losing data."
                                         — Peter Bailis
```

### 3.5 When LWW is acceptable

```
  ✓ Ephemeral data (session caches, rate-limit counters)
  ✓ Single-writer-per-key workloads (user updates only their row)
  ✓ Where overwriting is the intent (a heartbeat "I'm alive at T")
  ✗ NEVER for a shopping cart, comments, documents, financial data
```

---

## 4. Returning Siblings (Dynamo-style)

Instead of the database picking a winner, **return all concurrent
versions** to the application and let it decide.

### 4.1 Mechanics

```
  Write:
     put(k, v, context)     context = vector clock "seen before write"

  Detection:
     If v1.context and v2.context are concurrent (neither dominates),
     keep BOTH values as SIBLINGS under key k.

  Read:
     GET k → [ {value: v1, ctx: VC1},
                {value: v2, ctx: VC2} ]
     Application merges → newV
     put(k, newV, ctx = merged VC)     ← resolves the conflict
```

### 4.2 Example: shopping cart

```
  Customer state:
    {items: [a, b]}   ctx=[R1:1, R2:0]    on R1
    {items: [a, c]}   ctx=[R1:0, R2:1]    on R2

  Both ctxes are concurrent → both preserved as siblings.

  Next read of cart:
     returns both siblings.
     Application logic:  "cart is the UNION of item-sets"
       result = {items: [a, b, c]}
     writes back with merged ctx=[R1:1, R2:1].

  Note: this is why Amazon's cart "never forgot an add" —
  siblings + union merge rule.
```

### 4.3 Delete dilemma

```
  Customer REMOVES item "b" on R1.
  Concurrently ADDS item "c" on R2.

  Sibling sets:
    R1: {a, c}      (removed b)
    R2: {a, b, c}   (added c, still has b)

  Union = {a, b, c}    → removed item MAGICALLY REAPPEARS.

  Fix: use ADD/REMOVE logs (op-based) or OR-Set CRDT instead of
       naive set union.
```

### 4.4 Upsides / downsides

```
  ✓ No silent data loss.
  ✓ Gives app control over domain semantics.
  ✗ App must handle siblings on every read (more code).
  ✗ Unbounded sibling growth if not regularly reconciled
    (sibling explosion is a known operational issue).
```

**Used by:** Riak, early Dynamo, CouchDB (in conflict mode).

---

## 5. Conflict-free Replicated Data Types (CRDTs)

Data types whose **merge function is mathematically guaranteed** to
produce the same result regardless of:

1. **Order** of operations
2. **Duplicates** of operations
3. **Number of replicas**

Formally they satisfy: merge is **associative, commutative, idempotent**.
This "ACI" property → replicas reach the same state eventually, no
matter when or how often merges happen.

### 5.1 Two flavors

```
  STATE-BASED (CvRDT)
     Each replica sends its FULL STATE.
     Receiver applies: state = merge(my_state, received_state).
     Easy to reason about; larger messages.

  OPERATION-BASED (CmRDT)
     Replicas broadcast OPERATIONS (not state).
     Receivers apply ops on receipt.
     Requires reliable delivery + causal ordering.
     Smaller messages, more plumbing.
```

### 5.2 Common CRDTs and how they work

#### G-Counter (Grow-Only Counter)

```
  State:    vector  C[i] per replica i
  inc(i):   C[i] += 1
  value():  sum of all C[i]
  merge():  element-wise max

  Example:
    R1: [3, 0]  after 3 increments
    R2: [0, 5]  after 5 increments
    merged: [3, 5]   total = 8

  Property: every increment is recorded exactly once, regardless of
  order or duplication of gossip messages.
```

#### PN-Counter (Increment + Decrement)

```
  Two G-Counters: P (for increments), N (for decrements).
  value = sum(P) - sum(N)

  Riak uses PN-Counters for view counts, score trackers, etc.
```

#### LWW-Register

```
  Every value tagged with timestamp.
  merge: pick max-ts value.

  This is LWW wrapped in CRDT semantics — deterministic, but STILL
  loses data on concurrent writes. Use only where LWW fits.
```

#### OR-Set (Observed-Remove Set)

```
  Every add attaches a unique tag: add(x) → adds (x, tag_t1)
  Remove: remove(x) → removes every (x, tag) observed so far
  merge: union of tag-sets; x present iff any tag for x present.

  Solves the "remove undone" problem of naive set union.
```

#### Sequence CRDTs (for collaborative text)

```
  RGA (Replicated Growable Array)
  LSEQ
  WOOT
  Yjs, Automerge      used by Google Docs internals, Figma, Zed

  Each character assigned a UNIQUE position identifier that
  sorts deterministically across replicas. Concurrent inserts
  merge to a unique, consistent order.
```

### 5.3 Visual: CRDT merge is associative, commutative, idempotent

```
            ┌───────────────────────────────────────┐
   ┌─ R1 ──►│                                       │
   │        │     deterministic merge function       │◄── same state on all
   └─ R2 ──►│     m(m(a, b), c) = m(a, m(b, c))      │   replicas, always
            │     m(a, b)       = m(b, a)            │
            │     m(a, a)       = a                  │
            └───────────────────────────────────────┘
```

### 5.4 Limitations

```
  - Not every operation can be a CRDT (e.g., "set first unused username"
    requires coordination).
  - State size can grow (tags in OR-Set, tombstones) without compaction.
  - Network overhead for state-based CRDTs (whole state gossiped).
  - Adding a new CRDT type retroactively to production is hard.
```

**Used by:** Redis (Enterprise Active-Active), Riak, Akka Distributed
Data, Automerge, Yjs, Figma, Roblox game state, SoundCloud
counters, Apple's CloudKit.

---

## 6. Operational Transformation (OT)

Predecessor to CRDTs; still widely used in collaborative editors
(Google Docs being the most famous).

### 6.1 Idea

```
  Two users insert text concurrently.
  Each edit is an OPERATION (op).
  On receive, TRANSFORM the incoming op so it applies correctly
  after any LOCAL ops that happened in between.

  User1: insert("A", pos=0)  → "A"
  User2: insert("B", pos=0)  → "B"

  User1 receives User2's op:
     User1 has already inserted at pos=0, so transform:
        insert("B", pos=0) → insert("B", pos=1)
     Result: "AB"

  User2 receives User1's op → insert("A", pos=0)
     Result: "AB"

  Both converge. Non-trivial math to get right in all cases.
```

### 6.2 OT vs CRDT

```
  OT needs a CENTRAL SERVER to linearize operations.
  CRDTs work PEER-TO-PEER.

  OT has smaller state (ops, not tombstones).
  CRDTs are easier to verify correct.

  Modern collaborative tools (Figma, Linear, Notion) have shifted
  from OT to CRDTs for peer-to-peer sync and offline-first support.
```

---

## 7. Application-Specific Custom Merge

Sometimes you know your domain better than any library. Write a
domain-specific merge function.

### 7.1 Examples

```
  ACCOUNT BALANCE
     Merge = sum of all absolute deltas (like PN-Counter)
     Never treat balance as a "value to overwrite."

  USER PROFILE
     Per-field LWW: each field has its own timestamp.
     Concurrent edits to first name vs last name don't fight.

  CALENDAR
     Per-event merge: {add, remove, update} with unique event IDs.
     Conflicts within one event field → show to user for resolution.

  MODERATION STATUS
     "banned" overrides "active" regardless of timestamp.
     (Monotonic state — safety > recency.)
```

### 7.2 Principle

```
  For each field or entity, ask:
     What does CONCURRENT update mean semantically?
     Pick a merge function that preserves THAT semantic.
```

---

## 8. Comparison & When to Use Which

```
┌────────────────┬──────────────┬──────────────┬──────────────────┐
│ Strategy       │ Data loss?   │ Complexity   │ Use when…         │
├────────────────┼──────────────┼──────────────┼──────────────────┤
│ LWW            │ Yes (silent) │ Lowest       │ Single-writer,    │
│                │              │              │ ephemeral data    │
│ Siblings       │ No           │ Medium       │ User-visible      │
│ (vector clock) │              │ (app merge)  │ merging OK        │
│ CRDTs          │ No           │ High upfront │ Counters, sets,   │
│                │              │              │ collab editing    │
│ OT             │ No           │ Very high    │ Real-time text    │
│ Custom merge   │ Depends      │ Case-by-case │ Domain-specific   │
└────────────────┴──────────────┴──────────────┴──────────────────┘
```

### 8.1 Decision tree

```
  Can concurrent writes even happen?
     NO  → single-leader, no resolution needed.
     YES ▼

  Is the merged result mathematically nice?
     YES → CRDT (set, counter, register).
     NO  ▼

  Can you show siblings to the user?
     YES → siblings (Dynamo-style).
     NO  ▼

  Do you have a custom domain function?
     YES → custom merge.
     NO  ▼

  Is losing one write tolerable?
     YES → LWW.
     NO  → go back, you've painted yourself into a corner.
```

---

## 9. Handling Externalities (side effects)

Conflicts in data are bad; conflicts in **external actions** are
catastrophic.

```
  Alice clicks "Buy now" on US region: charges card, emails receipt.
  Bob (also Alice, different device) clicks "Buy now" on EU region:
       charges card AGAIN, emails receipt.

  Partition heals. Data is reconciled. But:
     - Two charges on the card.
     - Two receipt emails.
     - Two warehouse shipments.
  These can't be "merged." They're EXTERNAL SIDE EFFECTS.
```

### 9.1 Defenses

```
  1. ONLY ONE SIDE should perform side effects (the majority /
     designated primary region).

  2. IDEMPOTENCY KEYS on all external API calls — the payment
     processor dedupes by key.

  3. BEFORE side effect, WRITE a "pending op" row to storage.
     On replication conflict, pick one winner, cancel others.
     (Saga pattern territory — see ../03-DistributedTransactions.md.)

  4. USER-VISIBLE RESOLUTION for ambiguous cases:
     "We detected two orders at the same time — confirm which?"
```

---

## 10. Real-World Examples

```
┌─────────────────────┬────────────────────────────────────────┐
│ System              │ Conflict strategy                       │
├─────────────────────┼────────────────────────────────────────┤
│ Amazon shopping cart│ Siblings + application-level set union │
│ DynamoDB            │ LWW by default; conditional writes opt │
│ Cassandra           │ LWW per cell (timestamp)               │
│ Riak                │ Siblings + optional per-type CRDTs     │
│ Redis Enterprise    │ CRDTs (counters, sets, HLL)            │
│ Google Docs         │ OT (moved to CRDT-ish for offline)      │
│ Figma               │ CRDT (live collaborative editing)       │
│ Yjs / Automerge     │ CRDT libraries for JS/Rust             │
│ iCloud Notes        │ CRDT-based sync                        │
│ CouchDB / PouchDB   │ Sibling winner by algorithm + revisions │
│ Bitcoin             │ Longest-chain rule (a consensus "merge")│
│ Git                 │ 3-way merge with user resolution        │
└─────────────────────┴────────────────────────────────────────┘
```

---

## 11. Interview Q&A

### Q1. "Two clients write to the same key on different replicas at the same time. What happens?"

> Depends on strategy:
> (a) **LWW**: later timestamp wins, other silently lost.
> (b) **Siblings**: both preserved, application merges on next read.
> (c) **CRDT**: merge function combines them deterministically (e.g.,
> set union for OR-Set, sum for PN-Counter).
>
> For user-visible data, **never choose LWW** for critical writes.

### Q2. "What's wrong with last-writer-wins?"

> It silently loses data when clocks are skewed or writes are truly
> concurrent. It's fine for ephemeral values but dangerous for user
> content. Even with perfect clocks, true simultaneity means one
> write disappears without the user ever knowing.

### Q3. "Explain CRDTs in one sentence."

> Data types whose merge function is **associative, commutative, and
> idempotent**, guaranteeing replicas converge to the same state
> regardless of the order, timing, or duplication of updates.

### Q4. "How do you prevent the 'remove undone' bug in replicated sets?"

> Don't use a naive set of elements. Use an **OR-Set**: every add
> attaches a unique tag; remove records the tags it observed; on
> merge, an element is present iff any of its tags is still live.
> A concurrent add with a new tag won't be undone by a remove that
> didn't see it.

### Q5. "When is LWW acceptable?"

> When a field has a single logical writer (user updates their own
> profile), when data is ephemeral (rate-limit counters, session
> caches), or when the last-write IS the intent (heartbeat "I'm
> alive at T"). Never for shopping carts, collaborative documents,
> financial records.

### Q6. "How did Amazon's shopping cart never lose 'add to cart'?"

> Writes were tagged with **vector clocks**. Concurrent writes were
> preserved as **siblings**. On the next read the app would **union**
> the item sets — you never lost an add, at worst a delete got undone
> (and re-delete worked). Paper: DeCandia et al. 2007.

### Q7. "What if my data type can't be a CRDT?"

> Either (a) fall back to **coordination** (single-leader / Paxos),
> (b) use **siblings + domain-specific merge**, or (c) redesign the
> data model to split non-mergeable and mergeable parts apart.
> Some problems — unique usernames, "transfer $X from A to B" —
> fundamentally require coordination.

### Q8. "How does Google Docs handle concurrent edits?"

> Historically **Operational Transformation** with a central server
> linearizing all operations and transforming incoming ones against
> local history. Modern systems (Figma, Linear, Notion, Automerge)
> have mostly moved to **CRDT-based sequence types** (RGA, LSEQ,
> Yjs) because they work peer-to-peer and handle offline edits
> cleanly.

---

## 12. Further Reading

- DeCandia et al., "Dynamo" (2007)
- Shapiro et al., "Conflict-free Replicated Data Types" (2011)
- Kleppmann, "A Critique of the CAP Theorem" + CRDT posts on martin.kleppmann.com
- Automerge & Yjs docs — production CRDT libraries
- Preguiça, "Conflict-free Replicated Data Types: An Overview" (2018)

---

> **Previous:** [08-AntiEntropy.md](./08-AntiEntropy.md) ·
> **Next:** [10-StateMachineReplication.md](./10-StateMachineReplication.md)
