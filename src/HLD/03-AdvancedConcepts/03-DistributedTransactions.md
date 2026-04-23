# Distributed Transactions (2PC, Saga, TCC, Outbox)

> **Difficulty:** Medium-Hard | **Time:** 4 hours | **Priority:** Must Know

A **distributed transaction** is a single logical unit of work that touches
state in **more than one** independent system (different databases, different
microservices, different regions) and must still look **atomic** to the outside
world — either *all* effects happen or *none* do. Because the participants
can crash, the network can drop/reorder/duplicate messages, and clocks can
skew, achieving this is famously one of the hardest problems in backend
engineering. This document covers every pattern worth knowing in interviews
and real production systems.

---

## Table of Contents

1. [The Core Problem](#1-the-core-problem)
2. [Why Local ACID Doesn't Just "Extend"](#2-why-local-acid-doesnt-just-extend)
3. [Two-Phase Commit (2PC)](#3-two-phase-commit-2pc)
4. [Three-Phase Commit (3PC) and Paxos Commit](#4-three-phase-commit-3pc-and-paxos-commit)
5. [XA Transactions (the "classic" 2PC API)](#5-xa-transactions)
6. [The Saga Pattern](#6-the-saga-pattern)
7. [Choreography vs Orchestration](#7-choreography-vs-orchestration)
8. [TCC — Try / Confirm / Cancel](#8-tcc--try--confirm--cancel)
9. [The Outbox Pattern (reliable events)](#9-the-outbox-pattern)
10. [Idempotency — the glue that makes it all work](#10-idempotency)
11. [Isolation in Sagas (the "no isolation" problem)](#11-isolation-in-sagas)
12. [Real-World End-to-End Examples](#12-real-world-end-to-end-examples)
13. [Comparison Matrix](#13-comparison-matrix)
14. [Picking the Right Pattern](#14-picking-the-right-pattern)
15. [Interview Q&A](#15-interview-qa)

---

## 1. The Core Problem

In a microservices world, a single business operation commonly spans
multiple services, each with its **own** database. How do you ensure
all-or-nothing when any step can fail?

```
┌─────────── "Place Order" business action ───────────┐
│                                                     │
│   Order Svc      Payment Svc     Inventory Svc      │
│   (orders DB)    (ledger DB)     (stock DB)         │
│                                                     │
│   ✅ insert      ✅ charge card   ❌ out of stock    │
│                                                     │
│   → Order row exists                                │
│   → Money left the customer's card                  │
│   → But no item can ship!                           │
│                                                     │
│   We need to UNDO the first two effects —           │
│   atomically, durably, and without double-charging. │
└─────────────────────────────────────────────────────┘
```

This one picture is the motivation for **every** pattern in this document.

### 1.1 What "atomic" even means across services

```
 Across a SINGLE db:        Across MULTIPLE services:
 ─────────────────          ───────────────────────
 BEGIN;                     - each service has its own commit point
   UPDATE a;                - no shared WAL, no shared clock
   UPDATE b;                - network partitions are normal
 COMMIT;   ←─ atomic        - "atomic" must be SIMULATED
 (one WAL, one process)       via a protocol + compensations
```

---

## 2. Why Local ACID Doesn't Just "Extend"

A single-node DB gets atomicity almost for free: one Write-Ahead Log, one
fsync, one process. Across services/DBs none of that exists.

```
 Single node                         Distributed
 ─────────────                       ────────────
 1 WAL                               N independent WALs
 1 lock manager                      N independent lock managers
 1 clock                             N skewed clocks
 crash = recover from WAL            crash = "who else committed? who didn't?"
 rollback = truncate WAL             rollback = SEND A NEW TRANSACTION
                                     (compensation)
```

Two hard truths that flow from this:

- **No free rollback.** Once Payment Svc commits locally, you can't
  "un-commit". You must issue a *new* transaction (a refund) that reverses
  the effect semantically.
- **No free atomic visibility.** Different observers may see the commit
  happen at different times — welcome to eventual consistency.

---

## 3. Two-Phase Commit (2PC)

2PC is the canonical atomic commit protocol. A **coordinator** drives all
**participants** (resource managers) through exactly two phases: *vote*, then
*decide*.

### 3.1 The happy path

```
PHASE 1 — PREPARE (voting)

  Coordinator            A              B              C
      │                   │              │              │
      │── PREPARE ───────►│              │              │
      │── PREPARE ───────────────────────►              │
      │── PREPARE ──────────────────────────────────────►│
      │                   │              │              │
      │                   │  (write "PREPARED" to WAL,  │
      │                   │   hold locks, but DON'T     │
      │                   │   commit yet)               │
      │                   │              │              │
      │◄── VOTE-YES ──────│              │              │
      │◄── VOTE-YES ─────────────────────│              │
      │◄── VOTE-YES ────────────────────────────────────│

  Coordinator writes "COMMIT" to ITS OWN log → the decision is now durable.

PHASE 2 — COMMIT (decision)

      │── COMMIT ────────►│  applies, releases locks, ACKs
      │── COMMIT ────────────────────────►  ditto
      │── COMMIT ───────────────────────────────────────►  ditto

  Coordinator writes "END" to its log when all ACKs arrive.
```

### 3.2 The abort path

If **any** participant votes NO, or the coordinator times out, it broadcasts
ABORT and everyone rolls back their local prepared txn.

```
      │── PREPARE ───────►│── PREPARE ──►│── PREPARE ──►│
      │◄── VOTE-YES ──────│              │              │
      │◄── VOTE-NO  ──────────────────── │   (duplicate key)
      │
      │ COORDINATOR LOG:  "ABORT"  (durable)
      │
      │── ABORT ─────────►│── ABORT ────►│── ABORT ────►│
                           rollback prepared txn on each
```

### 3.3 Failure scenarios — this is where 2PC hurts

```
 ┌───────────────────────────────────────────────────────────────┐
 │  SCENARIO                    OUTCOME                          │
 ├───────────────────────────────────────────────────────────────┤
 │  Participant crashes BEFORE  Coordinator times out → ABORT    │
 │  voting                       (safe, no locks held)            │
 │                                                                │
 │  Participant crashes AFTER   On recovery it reads its WAL,    │
 │  voting YES, before COMMIT    sees "PREPARED", asks            │
 │                               coordinator: "commit or abort?"  │
 │                                                                │
 │  Coordinator crashes AFTER   Participants are STUCK in        │
 │  Phase 1 (before logging     PREPARED state — holding locks   │
 │  decision)                    until coordinator comes back.    │
 │                               This is THE 2PC blocking problem.│
 │                                                                │
 │  Coordinator crashes AFTER   On recovery it re-reads its log, │
 │  logging COMMIT               re-sends COMMIT to everyone.     │
 │                               Safe because decision is durable.│
 │                                                                │
 │  Network partition during    Participants on the wrong side   │
 │  Phase 2                      keep the locks until healed.    │
 └───────────────────────────────────────────────────────────────┘
```

The "stuck in PREPARED" window is exactly why 2PC is called a **blocking**
protocol — it can't make progress while the coordinator is unreachable.

### 3.4 2PC drawbacks at a glance

```
┌───────────────────────────────────────────────────────────────┐
│                     2PC DRAWBACKS                             │
├───────────────────────────────────────────────────────────────┤
│ 1. Blocking: participants hold locks while waiting for the    │
│    coordinator's decision. Long tail latencies under failure. │
│                                                               │
│ 2. SPOF: the coordinator's log is the source of truth.        │
│    Lose it and you may have to resolve in-doubt txns by hand. │
│                                                               │
│ 3. Latency: 2 RTTs + every participant has to fsync twice     │
│    (once for PREPARE, once for COMMIT). Bad under WAN.        │
│                                                               │
│ 4. Availability: if any participant is down, the whole        │
│    transaction is blocked. Availability = product of          │
│    availabilities — 4 × 99.9% = 99.6%.                        │
│                                                               │
│ 5. Hetero-datastores: works best when every participant       │
│    supports XA (most NoSQL DBs don't).                        │
└───────────────────────────────────────────────────────────────┘
```

**Use 2PC when:** all participants are databases in the same cluster /
datacenter, operations are short, and you need true ACID.
**Avoid 2PC when:** you're crossing service boundaries, WAN, or polyglot
persistence — use Saga/TCC/Outbox instead.

---

## 4. Three-Phase Commit (3PC) and Paxos Commit

**3PC** tries to fix the blocking problem by inserting a `PRE-COMMIT`
step so participants can unilaterally decide on a timeout. It works under
the assumption of **no network partitions** — which is unrealistic —
so it's a textbook curiosity more than a production protocol.

```
  PHASE 1: CAN-COMMIT?   (like 2PC PREPARE)
  PHASE 2: PRE-COMMIT     ("we're definitely going to commit unless
                           you hear otherwise")
  PHASE 3: DO-COMMIT      (actual commit)

  If coordinator dies after PRE-COMMIT: participants can elect a new
  coordinator and decide COMMIT on their own (they know everyone voted
  YES). No more indefinite blocking — but only if the network behaves.
```

**Paxos Commit** (Gray & Lamport, 2004) is the modern answer: run Paxos on
each participant's vote. It tolerates F failures out of 2F+1 coordinators
and never blocks. Spanner's internal commit protocol is a refined version
of this idea combined with TrueTime.

---

## 5. XA Transactions

XA is the industry-standard **API** for 2PC across heterogeneous resources
(RDBMS, JMS queues, etc.). It defines the "resource manager ↔ transaction
manager" contract.

```
  ┌──────────────────┐
  │ Transaction Mgr  │  (e.g. Atomikos, Narayana, JTA in Java EE)
  └────────┬─────────┘
           │  xa_start / xa_end / xa_prepare / xa_commit / xa_rollback
  ┌────────┴─────────────────────────────────────────┐
  │                                                   │
  ▼                       ▼                          ▼
 MySQL RM              PostgreSQL RM              ActiveMQ RM
```

Typical Java EE snippet:

```java
UserTransaction tx = ...; // JTA
tx.begin();
try {
    orderDAO.insert(order);         // MySQL XA resource
    jmsTemplate.send("orders", ev); // ActiveMQ XA resource
    tx.commit();                    // runs 2PC under the hood
} catch (Exception e) {
    tx.rollback();
}
```

**Reality check:** XA is rarely used in modern microservices because
(a) many datastores don't implement it well, (b) it inherits every 2PC
drawback, (c) Kubernetes rescheduling + XA recovery logs don't mix.

---

## 6. The Saga Pattern

Instead of one big distributed transaction, a **saga** is a *sequence of
local transactions* where each step publishes an event / calls the next,
and each step has a **compensating transaction** that semantically undoes
its effect.

```
SAGA: LOCAL TXNs + COMPENSATIONS

  T1: Create Order          ←→  C1: Cancel Order
  T2: Reserve Inventory     ←→  C2: Release Inventory
  T3: Charge Payment        ←→  C3: Refund Payment
  T4: Schedule Shipping     ←→  C4: Cancel Shipment
  T5: Send Confirmation     ←→  C5: Send Cancellation Email

SUCCESS PATH (forward recovery):
   T1 ──► T2 ──► T3 ──► T4 ──► T5 ──► DONE ✓

FAILURE PATH (backward recovery) — T3 fails:
   T1 ──► T2 ──► T3 ✗
                  │
   C1 ◄── C2 ◄────┘      (compensations run in REVERSE order)
```

### 6.1 Timeline diagram of a failing saga

```
 time ───────────────────────────────────────────────────────►
 T1 ██████▌
           T2 █████▌
                    T3 ████▌ ✗ fails
                           C2 ████▌    (release inventory)
                                  C1 ███▌  (cancel order)
                                         DONE (saga ended in ABORTED state)
```

### 6.2 Compensation design — semantic, not syntactic

You can't "rewind" time. A compensation is a **new** business transaction
with the opposite business meaning:

```
 T: "Charge $100"        →  C: "Refund $100"
 T: "Reserve seat 14A"   →  C: "Release seat 14A"
 T: "Send SMS"           →  C: "Send apology SMS" (can't un-send!)
 T: "Ship package"       →  C: "Request return" (if already shipped)
```

Some effects are **not compensable** (an email sent, a missile launched).
Such steps are called **pivot transactions** and must be placed as the
*last* step of the saga so that earlier failures never leave them
orphaned.

```
  Saga structure with pivot:

  [ compensable T1 ][ compensable T2 ][ PIVOT T3 ][ retryable T4 ][ retryable T5 ]
                                       ▲                            ▲
                                       │                            │
                       point of no return (commit-ish)               must eventually succeed
```

---

## 7. Choreography vs Orchestration

Two ways to implement a saga.

### 7.1 Choreography — event-driven, no central brain

Each service publishes events; other services subscribe and react.

```
 ┌────────┐  OrderCreated   ┌────────┐  ReservationOK  ┌────────┐  PaymentOK
 │ Order  │ ───────────────►│Inventry│────────────────►│Payment │────────────┐
 │ Svc    │                 │ Svc    │                 │ Svc    │            ▼
 └────────┘                 └────────┘                 └────────┘    ┌──────────────┐
     ▲   ▲                      ▲                          ▲        │ Shipping Svc │
     │   │                      │                          │        └──────┬───────┘
     │   │                      │  ReservationFailed       │               │
     │   └──────────────────────┴──────────────────────────┘  Shipped      │
     │                                                                     │
     └── orchestration emerges from the event topology ──────────────────  ┘
```

```
 Pros
  + Loose coupling, no single point of control
  + Services evolve independently
  + Scales well (just add a consumer)

 Cons
  - The flow lives only in the broker; hard to see end-to-end
  - Risk of event cycles and accidental re-processing
  - Compensation logic is scattered across services
  - Adding a new step = updating many services
```

### 7.2 Orchestration — a central saga coordinator

One service (the orchestrator) explicitly tells each participant what to
do. It's *not* the same as 2PC's coordinator — it doesn't hold locks.

```
                ┌─────────────────────────┐
                │   Saga Orchestrator     │
                │  (state machine + log)  │
                └────┬────────────────────┘
                     │
     Step 1: "Create order"    ──► Order Svc       ──► OK
     Step 2: "Reserve items"   ──► Inventory Svc   ──► OK
     Step 3: "Charge $100"     ──► Payment Svc     ──► FAILED!
     Step 4: "Release items"   ──► Inventory Svc   ──► OK   (compensation)
     Step 5: "Cancel order"    ──► Order Svc       ──► OK   (compensation)

     Final state: SAGA_ABORTED (persisted to DB)
```

State-machine view of the orchestrator:

```
       ┌────────────┐   order.create    ┌──────────────┐
 START │  PENDING   │──────────────────►│ ORDER_CREATED│
       └────────────┘                    └──────┬───────┘
                                                │ inventory.reserve
                                                ▼
                                        ┌────────────────┐
                                        │ INVENTORY_OK    │
                                        └──────┬──────────┘
                                               │ payment.charge
                                ┌──────────────┴──────────────┐
                                │                             │
                          ok   ▼                             ▼  fail
                     ┌──────────────┐             ┌───────────────────┐
                     │ PAID (DONE)  │             │ COMPENSATING      │
                     └──────────────┘             └─────────┬─────────┘
                                                            │ release + cancel
                                                            ▼
                                                  ┌──────────────────┐
                                                  │ ABORTED (final)  │
                                                  └──────────────────┘
```

```
 Pros
  + Clear, centralized business flow — easy to explain in a room
  + All retries/timeouts/compensations in one place
  + Easy to audit, visualize, replay
  + Adding a new step = edit one file

 Cons
  - Orchestrator becomes a critical service (scale it, make it HA)
  - Risk of "smart pipes, dumb endpoints" if overused
  - More boilerplate if the workflow is simple
```

**Tools**: Temporal, AWS Step Functions, Netflix Conductor, Camunda, Cadence.

**Rule of thumb for interviews**: pick orchestration. It's easier to reason
about, easier to diagram on a whiteboard, and how most real systems do it.

---

## 8. TCC — Try / Confirm / Cancel

TCC is a business-level 2PC: every participant exposes **three** operations
instead of relying on DB-level prepare.

```
  Phase 1 — TRY     : reserve resources (reversibly)
  Phase 2 — CONFIRM : finalize; must be idempotent and never fail
  Phase 2 — CANCEL  : release the reservation
```

### 8.1 Hotel booking example

```
 Try:     lock room 402 for user=42,  hold for 2 minutes
           (DB row: status=RESERVED, expires_at=now+2m)

 Confirm: status=BOOKED  (payment succeeded elsewhere)
 Cancel:  status=AVAILABLE (payment failed or timeout)
```

### 8.2 Sequence across services

```
  Orchestrator       Hotel Svc         Payment Svc        Flight Svc
      │                 │                  │                 │
      │── try ─────────►│                  │                 │
      │                 │  reserve room    │                 │
      │◄── ok ──────────│                  │                 │
      │── try ─────────────────────────────►│                │
      │                                     │ freeze funds    │
      │◄── ok ──────────────────────────────│                 │
      │── try ──────────────────────────────────────────────►│
      │                                                       │ reserve seat
      │◄── ok ───────────────────────────────────────────────│
      │
      │ all TRYs succeeded → CONFIRM everyone
      │── confirm ─────►│  book
      │── confirm ─────────────────►  capture
      │── confirm ─────────────────────────────────►  ticket
```

If any TRY fails, send CANCEL to everyone who succeeded so far.

### 8.3 TCC vs Saga

```
                     SAGA                  TCC
 Phase count         1 per step            2 per step (Try + Confirm/Cancel)
 Isolation           none (dirty reads)    "soft" — TRY reserves the resource
 Failure effect      visible, then undone  never visible to readers
 Implementation      business logic only   each step must expose 3 endpoints
 Best for            long-running flows    short booking-style flows
```

TCC gives you better isolation (readers never see the "money charged but
no item" state) at the cost of each service having to model a reservation
lifecycle.

---

## 9. The Outbox Pattern

**Problem:** how do you atomically (a) commit a row in your DB **and**
(b) publish a Kafka/RabbitMQ event? These are two different systems — you
can't wrap them in one transaction (without XA, which you don't want).

```
 NAIVE — DUAL WRITE (BROKEN):

   BEGIN;                                  ┌─── PUBLISH ────► Kafka ✗ FAILS
     INSERT INTO orders ...                │                 (lost event)
   COMMIT;   ✅                            │
                            ┌──────────────┘
                            │
     if we publish AFTER commit, the broker can be down
     if we publish BEFORE commit, the DB txn may rollback
     → inconsistency either way
```

**Outbox**: write the event to an `outbox` table inside the **same** local
transaction as the business write. A separate relay process reads the
outbox and publishes to the broker.

```
   SERVICE                          BROKER
   ┌───────────────────────────┐
   │ BEGIN;                    │
   │   INSERT orders (...)     │          ┌──────────┐
   │   INSERT outbox (event)   │          │  Kafka   │
   │ COMMIT;                   │          └────▲─────┘
   └───────────────────────────┘               │
              │                                │  publish
              ▼                                │  (at-least-once)
   ┌──────────────────┐   poll / CDC   ┌───────┴────────┐
   │  outbox table    │ ─────────────► │ Outbox Relay   │
   └──────────────────┘                └────────────────┘
```

Two popular relay implementations:

- **Polling publisher**: `SELECT ... FROM outbox WHERE published=false LIMIT 100`.
  Simple but adds DB load.
- **Log tailing (CDC)**: Debezium reads the DB's write-ahead log and streams
  INSERTs on `outbox` directly into Kafka. No polling, near-zero lag.

Schema sketch:

```sql
CREATE TABLE outbox (
  id           UUID PRIMARY KEY,
  aggregate    VARCHAR(64),      -- e.g. 'orders'
  aggregate_id VARCHAR(64),      -- e.g. order_id
  event_type   VARCHAR(64),      -- e.g. 'OrderCreated'
  payload      JSONB,
  created_at   TIMESTAMPTZ DEFAULT now(),
  published_at TIMESTAMPTZ        -- NULL until relay publishes
);
CREATE INDEX ON outbox (published_at) WHERE published_at IS NULL;
```

**Guarantees**:
- Exactly one DB commit point → no lost events, no phantom events.
- At-least-once delivery to the broker → consumers **must be idempotent**
  (see §10).
- The **Inbox pattern** is the mirror image on the consumer side: store
  the `(producer_id, event_id)` in an `inbox` table inside the same local
  txn as the side-effect, so replays are safe.

---

## 10. Idempotency

Every pattern above relies on messages being delivered *at least once*
(retries on timeout are fundamental). Therefore every consumer **must**
be idempotent — processing the same message twice must produce the same
end state as processing it once.

### 10.1 Why a naive retry is dangerous

```
 Client ──► Payment Svc : "Charge $100"   → ok (but response lost)
 Client ──► Payment Svc : "Charge $100"   → ok again
                                              ↓
                                         Customer charged $200 ✗
```

### 10.2 The idempotency-key pattern

```
 Client ──► Payment Svc:
     POST /charges
     Idempotency-Key: uuid-abc-123
     { "amount": 100 }

 First call:  process, store (key, result) in an idempotency table
 Second call: same key found → return the FIRST result, don't re-charge
```

Table sketch:

```
 ┌────────────────────────────────────────────────────────────┐
 │ idempotency_keys                                           │
 ├──────────────┬─────────┬───────────┬────────────┬──────────┤
 │ key          │ status  │ response  │ created_at │ expires  │
 ├──────────────┼─────────┼───────────┼────────────┼──────────┤
 │ uuid-abc-123 │ SUCCESS │ {txn:456} │ 2025-01-01 │ +24h     │
 │ uuid-xyz-999 │ RUNNING │ NULL      │ 2025-01-01 │ +24h     │
 └──────────────┴─────────┴───────────┴────────────┴──────────┘
```

Races to watch for:

```
 Concurrent requests with the SAME key:

   req A → INSERT key(status=RUNNING) → OK, do work
   req B → INSERT key(status=RUNNING) → conflict (PK violation)
                                        → read row
                                        → if RUNNING: wait / 409
                                        → if SUCCESS: return stored response
```

Must be backed by a **unique constraint** (primary key on `key`) so two
concurrent "first calls" can't both proceed.

### 10.3 Choosing an idempotency key

```
 Operation                    Good key
 ────────────                 ────────
 User clicks "Pay"            UUID generated by client at click time
 Retrying a saga step         (saga_id, step_number)
 Webhook replay               provider's event id (e.g. Stripe event.id)
 Batch job                    (job_id, row_id)
```

### 10.4 Idempotency ≠ dedup ≠ exactly-once

```
 At-least-once + idempotent consumer  =  "effectively" exactly-once
 (which is the best you can get across asynchronous boundaries)
```

---

## 11. Isolation in Sagas

Sagas commit each step locally, so other transactions can **see**
intermediate state — the ACID "I" is gone. Patterns to get some back:

### 11.1 Semantic lock

Add a `status = PENDING` flag to the business entity. Readers/other sagas
treat `PENDING` rows as "don't touch".

```
 orders table
 ┌──────┬───────────┬──────────┐
 │  id  │ amount    │ status   │
 ├──────┼───────────┼──────────┤
 │ 123  │ $100      │ PENDING  │   ← saga in flight
 │ 124  │ $42       │ BOOKED   │
 └──────┴───────────┴──────────┘
```

### 11.2 Commutative updates

Design operations so the order they arrive doesn't matter: `+$10` and
`-$3` commute, whereas `SET balance = x` does not.

### 11.3 Pessimistic view / reservation

TCC-style: during TRY, put the resource in a state where nobody else can
grab it.

### 11.4 Re-read / version check

Before the pivot step, re-read the entities and verify versions haven't
changed (optimistic concurrency). If they did, compensate and abort.

### 11.5 By-value / snapshot

Carry a snapshot of the entities through the saga so later steps work on
the *same* view; use version numbers to detect conflicts at commit.

---

## 12. Real-World End-to-End Examples

### 12.1 E-commerce "place order" (Saga + Outbox + Idempotency)

```
                       ┌────────────────────────────┐
                       │ Order Orchestrator (Temporal)│
                       └──────┬──────────────────────┘
                              │
     ┌────────────────────────┼──────────────────────────┐
     │                        │                          │
     ▼                        ▼                          ▼
 Order Svc                Payment Svc               Inventory Svc
  orders DB                 ledger DB                 stock DB
  outbox   ──► Kafka        outbox ──► Kafka          outbox ──► Kafka
                                                       
 Steps the orchestrator drives:
   1. orderSvc.createOrder(cart, idemKey)           → ORDER_PENDING
   2. inventorySvc.reserve(items, idemKey)          → reserved, TTL=10m
   3. paymentSvc.charge(userId, total, idemKey)     → captured
   4. orderSvc.markPaid(orderId)                    → ORDER_PAID
   5. shippingSvc.schedule(orderId)                 → label printed
 
 If step 3 fails → compensate 2 (release), 1 (cancel order).
 If step 5 fails → retry forever (shipping is a "retryable" post-pivot step).
```

### 12.2 Uber — accept ride (TCC-ish)

```
  Rider taps "Request"
    → Dispatch Svc TRY: lock driver D for rider R (10s)
    → Pricing Svc  TRY: quote and reserve surge capacity
    → if both ok → CONFIRM (create trip, start meter)
    → if driver cancels within 10s → CANCEL (unlock driver)
```

### 12.3 Airline + hotel + car bundle (Orchestrated Saga)

```
  T1 reserve flight   ──► seat held (PNR pending)
  T2 reserve hotel    ──► room held (booking pending)
  T3 reserve car      ──► car held
  T4 PIVOT: charge bundle on customer card
  T5 confirm flight   ──► PNR issued
  T6 confirm hotel    ──► booking confirmed
  T7 confirm car      ──► voucher emailed   ← last, non-compensable email

  Failure at T3: C2 release hotel, C1 release flight.
  Failure at T5 (airline API down): RETRY until success (it's post-pivot).
```

### 12.4 Banking transfer (2PC or Saga, depending on topology)

```
  Same bank, same DB cluster:
    2PC across two account rows (or one ACID multi-row txn).

  Cross-bank (ACH, SWIFT):
    Saga with compensation:
       debit_sender → send_to_correspondent → credit_receiver
       if credit fails 3x → reverse_debit (refund) + notify user
```

### 12.5 Message-driven order fulfillment (Choreography)

```
  OrderCreated   ──► Inventory subscribes → publishes InventoryReserved
  InventoryReserved ──► Payment subscribes → publishes PaymentCaptured
  PaymentCaptured   ──► Shipping subscribes → publishes ShipmentLabeled
  (failure at any step publishes a *Failed event; producers upstream
   listen for matching *Failed events and run their compensations)
```

---

## 13. Comparison Matrix

```
┌──────────────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│ Property         │   2PC    │   Saga   │   TCC    │  Outbox  │  3PC/    │
│                  │          │          │          │          │  Paxos   │
├──────────────────┼──────────┼──────────┼──────────┼──────────┼──────────┤
│ Consistency      │ Strong   │ Eventual │ Strong*  │ Eventual │ Strong   │
│ Isolation        │ Full     │ None     │ Soft     │ None     │ Full     │
│ Blocking         │ Yes      │ No       │ No       │ No       │ No       │
│ Latency          │ High     │ Low      │ Medium   │ Low      │ High     │
│ Ops complexity   │ High     │ Medium   │ Medium   │ Low      │ Very high│
│ Cross-service    │ Hard     │ Great    │ Great    │ Great    │ Hard     │
│ Message bus      │ No       │ Yes      │ Optional │ Yes      │ No       │
│ Retries needed   │ Few      │ Many     │ Many     │ Many     │ Few      │
│ Failure recovery │ Log-based│ Compensa-│ Compensa-│ Idempo-  │ Quorum   │
│                  │          │ tion     │ tion     │ tent     │          │
│ Classic use case │ XA/DB    │ E-commerce│ Booking  │ Events   │ Research │
│                  │ cluster  │           │ systems  │          │          │
└──────────────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
  * "Strong-ish" — TCC gives isolation over the TRY period but not full linearizability.
```

---

## 14. Picking the Right Pattern

```
 START
   │
   │ Are all resources in ONE database/cluster?
   ├─► YES ──► Use a single local transaction.  Done.
   │
   │ No. Do they all speak XA and are they co-located?
   ├─► YES ──► Consider 2PC / XA. Accept the blocking risk.
   │
   │ No. Is the flow SHORT (seconds) and do you need "no dirty reads"?
   ├─► YES ──► TCC.
   │
   │ No. Is the flow LONG-RUNNING (seconds → days)?
   ├─► YES ──► Saga + Orchestrator (Temporal / Step Functions).
   │
   │ Do you just need to commit a row AND publish an event atomically?
   └─► YES ──► Outbox Pattern.  (Often combined with Saga.)
```

And remember: **idempotency is non-optional** in every branch.

---

## 15. Interview Q&A

**Q1. Why does 2PC "block"?**
If the coordinator crashes between logging the decision and broadcasting
it, participants in the PREPARED state can neither commit nor abort
unilaterally — they still hold locks. They must wait for the coordinator
to recover and replay its log.

**Q2. Your boss says "just use 2PC across our 6 microservices." What do
you say?**
Push back. Microservices usually use heterogeneous stores (many without
XA), cross the network, and run on Kubernetes where nodes churn. 2PC
will give you long lock hold times, cascading failures, and unavailability.
Suggest an orchestrated Saga with the Outbox pattern and idempotency keys.

**Q3. A saga step is non-compensable (e.g., sends an email). How do you
model it?**
Make it a **pivot** or post-pivot step — place it *after* the point at
which the saga has effectively committed, and make it retryable. Never
put it before a potentially-failing compensable step.

**Q4. How do you make a payment API idempotent?**
Require an `Idempotency-Key` header. On the server, wrap the charge in a
unique-constraint insert into an `idempotency_keys` table; on conflict,
return the stored response. Expire keys after 24h. For webhooks, use the
provider's event id as the key.

**Q5. Why not just publish the event right after committing to the DB?**
Dual write. If the broker is down the event is lost; if the DB rolls back
after you've already published you have a phantom event. Use the **Outbox
pattern** to write the event in the same local transaction.

**Q6. Orchestration or choreography?**
Choose orchestration when the business flow is non-trivial or you want
one place to understand/debug it. Choose choreography when the flow is
simple fan-out and you want maximum service autonomy. Most production
systems end up with orchestration for multi-step financial flows.

**Q7. What's the difference between a saga "pivot" and 2PC's "commit point"?**
In 2PC, the commit point is when the coordinator writes COMMIT to its log —
atomic and invisible to outsiders until Phase 2 finishes. In a saga, the
pivot is a *business* boundary: before it, failures cause backward recovery
(compensations); after it, failures cause forward recovery (retries).

**Q8. Does Kafka's "exactly-once semantics" save me from needing idempotency?**
Kafka EOS is exactly-once **within Kafka** (producer → topic → consumer's
read offset). The moment your consumer makes an external call or a DB
write, EOS no longer applies. You still need idempotent side effects.

**Q9. Where does "eventual consistency" actually hurt in a saga?**
Between the first local commit and the last one, the world can see partial
state — a debited account but not yet credited one. Mitigate with semantic
locks (`PENDING` flags), short saga durations, or TCC-style reservations.

**Q10. Compare Saga, TCC, and 2PC in one sentence each.**
- **2PC**: lock everything, commit together — strong but blocking.
- **Saga**: commit locally, undo with business compensations — eventual but non-blocking.
- **TCC**: reserve locally, then confirm or cancel — a middle ground with soft isolation.

---

## 16. Key Takeaways

1. **2PC for one DB cluster, Saga for microservices.** This single rule
   answers 80% of interview questions.
2. **Orchestrated saga** is the default in modern systems — Temporal, Step
   Functions, Conductor all implement it.
3. **Idempotency keys are not optional.** Every network call across a
   distributed transaction must be safe to retry.
4. **Compensating transactions are business operations**, not DB rollbacks —
   design them *at the same time* as the forward operation.
5. **Outbox pattern** solves the dual-write problem cleanly; combine with
   CDC (Debezium) for low-latency event publishing.
6. **Pivot transactions** anchor the saga — place non-compensable steps
   last, post-pivot, and make them retryable.
7. **Eventual consistency is the price of availability** — state the
   windows clearly to your product team so UX can compensate (e.g.
   "confirming..." states).
8. **TCC** is a strong pick when the UX demands no visible intermediate
   state (hotel/flight booking) and you control all participants.
