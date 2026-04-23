# Delivery Semantics & Idempotency

> **Difficulty:** Medium | **Time:** 2 hours | **Priority:** Must Know

Every time you send a message, make an RPC, or enqueue a job, you face
a three-way trade-off: **at-most-once, at-least-once, exactly-once.**
This document breaks down what each actually means, why *true*
exactly-once is mostly a myth, and the concrete patterns
(**idempotency keys, dedup stores, transactional outbox, Kafka EOS**)
used to approximate it in production.

---

## Table of Contents

1. [The Problem: Network Uncertainty](#1-the-problem-network-uncertainty)
2. [At-Most-Once](#2-at-most-once)
3. [At-Least-Once](#3-at-least-once)
4. [Exactly-Once: The Honest Story](#4-exactly-once-the-honest-story)
5. [Idempotency](#5-idempotency-the-fundamental-tool)
6. [Idempotency Keys](#6-idempotency-keys)
7. [Dedup Stores & Windows](#7-dedup-stores--windows)
8. [Transactional Outbox](#8-transactional-outbox)
9. [Kafka's "Exactly-Once Semantics" (EOS)](#9-kafkas-exactly-once-semantics-eos)
10. [Two Generals & End-to-End Argument](#10-two-generals--end-to-end-argument)
11. [Quick Reference / When to Use What](#11-quick-reference--when-to-use-what)
12. [Interview Q&A](#12-interview-qa)

---

## 1. The Problem: Network Uncertainty

```
 Client sends request.  Three things can happen:

   Case A   request lost                → no side effect, client retries
   Case B   request delivered, response lost   → SIDE EFFECT,
                                                  client doesn't know!
   Case C   request + response delivered → happy path

 FROM THE CLIENT'S VIEW, A and B are INDISTINGUISHABLE.
 Both look like "no response, timeout."
```

The client's only defense is to **retry** or **accept possible loss**.
Those two choices give us two basic delivery semantics:

```
  RETRY on failure        → AT-LEAST-ONCE
  DON'T RETRY on failure  → AT-MOST-ONCE
```

And combining retries with dedupe gives the third:

```
  RETRY + DEDUPE          → EFFECTIVELY EXACTLY-ONCE
```

---

## 2. At-Most-Once

**"Send it and forget it. If it's lost, tough."**

```
  Client ──req──► Server            Client ──req──► Server
             │                                   ✗    │
             │ (maybe delivered,                       │ (lost, never delivered)
             │  maybe not)                              │
             │                                           │
  Client does NOT retry → message may be silently missed.
```

### 2.1 Where it fits

```
  ✓ UDP metrics / telemetry
       Dropping 0.1% of samples doesn't move the average meaningfully.

  ✓ Ephemeral "live view" updates
       The next update will overwrite anyway; missing one is fine.

  ✓ Audit logs that don't need full fidelity
```

### 2.2 Where it breaks things

```
  ✗ Orders, payments, charges
  ✗ Anything you expect "to always arrive"
  ✗ Anything where loss is silent — users don't know what they missed
```

### 2.3 Cost

```
  Lowest. No retries, no dedupe state. One-way fire.
```

---

## 3. At-Least-Once

**"Keep trying until you succeed. Accept that duplicates may happen."**

```
  Client ──req 1──► Server          (handled, response lost)
  Client ──req 2──► Server          (handled AGAIN — duplicate)
  Client ──req 3──► Server          (response arrives, client stops)

  Server has processed the request MORE THAN ONCE.
```

### 3.1 Where it fits

Basically **every practical queueing system** uses this by default:

```
  SQS        polling / visibility timeout
  RabbitMQ   unacked messages redelivered
  Kafka      offset commit retries
  AWS Lambda async invocation retries
  Celery     default at-least-once
  HTTP 5xx   client retries
```

### 3.2 Implications

```
  ✗ Consumers MUST be IDEMPOTENT.
       Otherwise: double charges, double emails, duplicate rows.

  ✗ Side effects MUST be guarded by idempotency keys.
```

### 3.3 Cost

```
  Medium. Retry plumbing on sender, dedupe/idempotency on receiver.
```

---

## 4. Exactly-Once: The Honest Story

### 4.1 Why it's essentially impossible (pure form)

Two Generals problem: in an unreliable network you can never know
for certain whether a message was received. Any "exactly-once"
protocol reduces to **"at-least-once + dedupe"**, i.e., you deduplicate
somewhere. That somewhere has state — and therefore its own failure modes.

### 4.2 What "exactly-once" systems ACTUALLY give you

```
  EFFECTIVELY EXACTLY-ONCE
  ────────────────────────
  = at-least-once + dedup → appears as exactly-once TO THE APPLICATION.

  This works IF the dedup boundary encloses the side effect.
  It FAILS if the side effect escapes the boundary.
```

### 4.3 The boundary matters

```
  KAFKA EXACTLY-ONCE (producer → Kafka → consumer → Kafka)
  ✓ Within Kafka, producer retries are deduped by (producer_id, seq).
  ✓ Transactions commit offsets + output atomically.
  ✓ Exactly-once appears to app that stays inside Kafka.

  But:
  ✗ If your consumer sends an email via SendGrid, Kafka cannot make
    that idempotent — the EMAIL API can be called multiple times
    unless YOU pass an idempotency key to SendGrid.
```

### 4.4 Golden rule

> **"Exactly-once" is a property of a closed system. As soon as you
> have a side effect that escapes, you are back to at-least-once
> and MUST layer idempotency on every escape point.**

---

## 5. Idempotency: The Fundamental Tool

An operation is **idempotent** iff applying it **more than once**
gives the same result as applying it once.

```
  SET x = 5         idempotent   (same outcome no matter how many times)
  x = x + 1         NOT idempotent (each call changes x)
  DELETE order 42   idempotent   (second call is a no-op)
  CREATE order      NOT idempotent without dedup (creates 2 rows)
```

### 5.1 Idempotent by design

```
  ✓ UPSERT semantics:  "put x=5" instead of "increment x"
  ✓ CONDITIONAL UPDATE: "set x=5 IF current version == 7"
  ✓ TERMINAL STATE:    mark-as-shipped vs ship-order
  ✓ SET INSERTION:     add to a dedup set — repeat is a no-op
```

### 5.2 Made idempotent via a key

```
  Inherently NOT idempotent: create charge for $100.
  Retry → two charges.

  Fixed with idempotency key:
     charge(key=K1, amount=100)
     charge(key=K1, amount=100)  ← server sees K1 already processed
                                     returns CACHED response.
```

### 5.3 Idempotency patterns at a glance

```
  NATURAL              operation maps state-to-state deterministically
  CONDITIONAL          use CAS / if-version-equals to block duplicates
  KEY-BASED            client supplies unique key; server dedupes
  TOMBSTONE            record "done" markers; skip if already marked
  CHECKPOINT           persist progress; restart resumes where it left off
```

---

## 6. Idempotency Keys

The industry-standard way to make remote APIs safe to retry.

### 6.1 Protocol

```
  Client generates a unique key per logical request.
     - UUIDv4, or
     - Hash of (user_id + intent + nonce)

  Every retry of the same logical request uses the SAME key.

  Server:
    1. On request arrival with key K:
         look up K in dedup store.
         IF FOUND: return stored response. DONE.
    2. IF NOT FOUND:
         do the work atomically with storing (K → response).
         return the response.
    3. Keep K for TTL (e.g., 24h-7d).
```

### 6.2 Stripe-style API

```
  POST /v1/charges
  Idempotency-Key: abc-def-1234

  { amount: 500, currency: "usd", source: tok_xxx }

  - Any retry with same key returns the ORIGINAL response.
  - No duplicate charge, even if network eats the response 5 times.
```

### 6.3 Keying at the RIGHT granularity

```
  BAD:  hash of request body alone
         (two legitimately-identical requests collide)

  GOOD: key = UUID the CLIENT created, stored locally,
         retried verbatim.

  If the user clicks "Buy" twice rapidly → two UUIDs → two charges.
  If one click retries due to network failure → same UUID → one charge.
  The UUID captures CLIENT INTENT.
```

### 6.4 Where to put the dedup store

```
  Option                       Pros            Cons
  ───────                       ────            ────
  Row in main DB (same txn)     Simplest         +1 row per op
  Redis with TTL                Fast             Eviction risk under memory pressure
  DynamoDB / Cosmos DB          Durable + TTL    $$
  Bloom filter                  Tiny             False positives (OK if paired with DB check)
```

---

## 7. Dedup Stores & Windows

Instead of per-request keys, another pattern: track recent message IDs
in a finite window.

### 7.1 Bloom-filter-backed dedup

```
  Every processed msg_id → added to Bloom filter.

  New msg arrives:
    IF bloom.might_contain(msg_id):
        consult authoritative DB / store to confirm
    ELSE:
        definitely new → process + add to bloom
```

### 7.2 Bounded window

```
  For a high-throughput queue (billions/day), you can't keep
  dedup state forever. Fix a window:

    "Dedup exactly-once within the last 24 hours."
    After 24h, dedup set forgets → re-processing possible
    but extremely unlikely in practice.
```

### 7.3 Strategies by scale

```
  LOW VOLUME    store every idempotency key in main DB
  MID VOLUME    Redis + TTL
  HIGH VOLUME   distributed hash set + bloom filter
  STREAM        sliding-window dedup by key + high watermark
```

---

## 8. Transactional Outbox

**The pattern that bridges database writes to external side effects.**

### 8.1 The problem

```
  Service does:
    db.insert(order)     ← commits
    queue.publish(msg)   ← succeeds?

  Between the two:
    - If service crashes: order exists, no msg sent.
    - If queue.publish duplicates: order exists, duplicate msg.
    - No atomic "do both or neither."
```

### 8.2 The fix

```
  Inside ONE DB transaction:

    INSERT order ...;
    INSERT outbox (id, payload, status=PENDING) ...;
    COMMIT;

  Separate async RELAY process reads outbox:
    For each pending row:
       publish to queue (with the row's id as idempotency key).
       mark row as SENT (or delete it).

  Crash scenarios:
    - Crash after commit, before publish → relay retries → dedupe on consumer side
    - Crash during publish → retry on next tick, dedupe by row id
    - Duplicate publish → consumer dedupes by row id
```

### 8.3 Visual

```
  APP SERVICE
  ┌───────────────────────────┐
  │  BEGIN TX                 │       ┌───────────────┐
  │    INSERT INTO orders     │       │     DB        │
  │    INSERT INTO outbox     │       │               │
  │  COMMIT                   │──►────┤  orders       │
  └──────────┬────────────────┘       │  outbox       │
             │                        └───────────────┘
             ▼                                │
       (application returns)                  │
                                              │
                                  RELAY (worker polling outbox)
                                              │
                                              ▼
                                    ┌───────────────────┐
                                    │   MESSAGE QUEUE   │
                                    │    (Kafka/SQS)    │
                                    └───────────────────┘
```

### 8.4 Variants

```
  TRANSACTIONAL INBOX
     Consumer writes incoming msg_id into a dedup table in same txn
     as the side effect. Skips if msg_id already present.

  CDC-BASED OUTBOX
     Skip the relay: use Debezium/CDC to stream outbox inserts
     directly to Kafka. Atomicity comes from DB commit + CDC ordering.

  SAGA PATTERN
     For long-running workflows; each step publishes compensation
     events. See ../03-DistributedTransactions.md.
```

---

## 9. Kafka's "Exactly-Once Semantics" (EOS)

Released 2017; widely used in stream processing. What it actually means:

### 9.1 Producer-side idempotence

```
  Each producer gets a Producer ID (PID) + per-partition SEQUENCE
  NUMBER. Kafka broker dedupes by (PID, partition, seq).

  Producer: msg #42 for partition P, seq=1000
  Kafka:    PID=xyz seq=1000 received → store.
  Producer: timeout, retry → msg #42 again.
  Kafka:    duplicate (xyz, 1000) → ignored.

  → AT-LEAST-ONCE send becomes EXACTLY-ONCE within the broker.
```

### 9.2 Atomic transactions

```
  Producer can begin/commit/abort transactions spanning multiple
  partitions / topics:

     beginTransaction()
     send(topic=orders, key=..., value=...)
     send(topic=metrics, key=..., value=...)
     sendOffsetsToTransaction(consumer_offsets)
     commitTransaction()

  All messages become visible ATOMICALLY. Consumers configured
  with isolation.level=read_committed see all-or-nothing.
```

### 9.3 Stream processing "read-process-write" atomic loop

```
  Kafka Streams / Flink EOS:

     consume(msg) → process → write(output) + commit(input_offset)
                                              atomically

  If any step fails → nothing visible → retry reads the same msg.
  On success → exactly-once delivery within Kafka.
```

### 9.4 What EOS does NOT fix

```
  ✗ Side effects to EXTERNAL systems during processing
      (email APIs, REST calls, non-Kafka DBs not in the transaction)
  ✗ Consumer side effects outside the read_committed window
  ✗ Bugs in your processing logic

  Whenever you cross a boundary OUT of Kafka, idempotency is
  YOUR PROBLEM AGAIN.
```

---

## 10. Two Generals & End-to-End Argument

### 10.1 Two Generals problem

```
  Two armies must attack at the same time to win.
  Generals communicate via MESSENGERS that may be captured.

  General A: "Attack at dawn." (message may be lost)
  General B: "Confirmed — attack at dawn." (reply may be lost)
  General A: "Got your confirm." (reply to reply may be lost)
  ...

  You can never end the protocol with BOTH sides certain the
  other is ready. Provably impossible.
```

→ Implication: any distributed agreement protocol must tolerate
ambiguity at the boundaries. At some point, the system must pick
a semantics (at-most or at-least) and live with it.

### 10.2 End-to-end argument (Saltzer, Reed, Clark, 1984)

> **"A function should be implemented at the endpoints, not in the
> communication layer, if correctness depends on it."**

Applied to delivery:

```
  You cannot trust the NETWORK / QUEUE / RPC layer to deliver
  exactly-once. Build idempotency and dedup at the APPLICATION
  ENDPOINTS — the only place that actually knows the semantics
  of the operation.
```

---

## 11. Quick Reference / When to Use What

```
┌────────────────────┬──────────────┬───────────────┬────────────────┐
│ Use case           │ Delivery     │ Idempotency   │ Notes           │
├────────────────────┼──────────────┼───────────────┼────────────────┤
│ Metrics / telemetry│ at-most-once │ n/a           │ UDP is fine    │
│ Email notifications│ at-least-once│ dedup by key  │ Idemp. in SMTP │
│ Payment / charge   │ at-least-once│ idempotency key│ Required!      │
│ Order placement    │ at-least-once│ outbox + key  │ Critical       │
│ Stream transforms  │ exactly-once │ Kafka EOS     │ In-Kafka only  │
│ Live push updates  │ at-most-once │ n/a           │ Next overwrites│
│ Critical workflow  │ at-least-once│ saga / outbox │ + compensations│
│ User events/clicks │ at-least-once│ dedup on id   │ Analytics      │
└────────────────────┴──────────────┴───────────────┴────────────────┘
```

---

## 12. Interview Q&A

### Q1. "What's the difference between at-least-once and exactly-once?"

> At-least-once means the message will be delivered *at least one time*,
> but possibly more (duplicates happen on retry). Exactly-once means
> the side effect of the message happens exactly once from the
> application's perspective. True exactly-once at the transport layer
> is impossible (Two Generals); what you get in practice is
> at-least-once + **idempotency** at the receiver, which *appears*
> as exactly-once.

### Q2. "How does Stripe prevent double-charging on retries?"

> Clients pass an **Idempotency-Key** header — a unique UUID for each
> logical payment. Stripe caches the response keyed by that UUID for
> 24h. Any retry with the same key returns the cached response,
> doing no new work. This turns at-least-once HTTP into effective
> exactly-once payment.

### Q3. "Explain the transactional outbox pattern."

> To atomically "insert a row AND publish an event," insert both the
> business row and an outbox row in the **same DB transaction**.
> A separate relay (or CDC consumer) reads the outbox and publishes
> to the message queue, marking rows as sent. This gives at-least-once
> publishing with crash-safety, and consumers dedupe by outbox row id
> for effectively-exactly-once.

### Q4. "Why is Kafka's 'exactly-once semantics' not truly exactly-once?"

> It's exactly-once **within Kafka**: idempotent producer + atomic
> transactions across offsets and topics. But as soon as your consumer
> makes an external call (email, non-Kafka DB, HTTP API), the delivery
> semantics reset — the external system may see the call 0 or 1+ times.
> You must add idempotency on every escape point.

### Q5. "When is at-most-once acceptable?"

> When missing a message is tolerable and duplicates aren't.
> Classic cases: UDP metrics pipelines (dropping a sample doesn't
> change aggregates), live-view websocket updates (next tick
> overwrites anyway), and "last known value" heartbeats.

### Q6. "How do you make a non-idempotent operation safe to retry?"

> Wrap it with an idempotency key: the client generates a unique key,
> sends it on every retry; the server stores `key → response` atomically
> with the operation. Any duplicate returns the cached response without
> repeating the work. Keep the key for a TTL longer than the expected
> retry window (usually hours to days).

### Q7. "What's wrong with hashing the request body as the idempotency key?"

> Two legitimately identical requests (user clicks "Buy" twice for two
> intentional orders) would collide. The key must represent **client
> intent**, not just data — typically a UUID the client generates and
> retries with verbatim.

### Q8. "How does the outbox pattern differ from 'just publish after commit'?"

> "Publish after commit" has a crash window: if the service crashes
> between DB commit and queue publish, the event is lost forever.
> The outbox writes the event into the DB **inside** the transaction
> — so it survives crashes — and a relay publishes asynchronously,
> retrying until success.

### Q9. "What would you do to make a webhook consumer idempotent?"

> Receiver keeps a `processed_events` table keyed by `event_id`. On
> webhook arrival:
> 1. Begin DB transaction.
> 2. INSERT event_id; if duplicate key → exit early.
> 3. Perform business logic.
> 4. Commit.
>
> Sender retries the webhook until 2xx; receiver dedupes. Both sides
> sleep well.

### Q10. "Can you always achieve exactly-once?"

> In a closed system (e.g., all processing inside Kafka with
> transactions), yes — effectively. Across systems (DB → queue →
> external API), no — you must design for at-least-once delivery and
> **push idempotency all the way to the point where the side effect
> actually happens**.

---

## 13. Further Reading

- Stripe API docs — idempotency keys
- Confluent blog — "Exactly-Once Semantics Are Possible: Here's How Kafka Does It"
- Saltzer, Reed, Clark, "End-to-End Arguments in System Design" (1984)
- Chris Richardson, "Microservices Patterns" — transactional outbox, saga
- Kleppmann, "Designing Data-Intensive Applications" Ch. 8-9 — delivery semantics
- "Two Generals Problem" (Wikipedia) — the formal impossibility

---

> **Previous:** [10-StateMachineReplication.md](./10-StateMachineReplication.md) ·
> **Next:** [12-BackPressureAndRetries.md](./12-BackPressureAndRetries.md)
