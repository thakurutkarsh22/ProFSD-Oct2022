# Idempotency, Duplicate Requests & Practical Deduplication (vs Exactly-Once)

> **TL;DR.** In a distributed system, **retries are mandatory and duplicates are inevitable**. "Exactly-once delivery" is a myth at the network layer; what you get is "effectively once" = **at-least-once delivery + idempotent processing**. The canonical tools are an **idempotency key**, a **dedup store with TTL**, and **business-layer uniqueness constraints**.

---

## 1. The fundamental problem

Between "client sent a request" and "client got a response" there are three failure windows:

```
┌─ request in flight ─┐ ┌─ server processing ─┐ ┌─ response in flight ─┐
│                     │ │                     │ │                      │
│   retry = safe      │ │   retry = DANGEROUS │ │   retry = DANGEROUS  │
│   (never processed) │ │   (server did half) │ │   (processed, ack   │
│                     │ │                     │ │     was lost)       │
└─────────────────────┘ └─────────────────────┘ └──────────────────────┘
```

The client *cannot* distinguish these cases. From the client's point of view, "no response in 5 s" could mean any of:
- The request never arrived → **safe to retry**.
- The request arrived and succeeded; the ack was lost → **retrying charges the card twice**.
- The request arrived and is still processing → **retrying may double-process**.

Any request-response API that allows retries *must* be idempotent, or you will get duplicate side effects. That's not a "bug in extreme cases"; it's a guaranteed outcome at scale.

---

## 2. Idempotency vs deduplication (close, but not the same)

| Concept | Layer | Meaning |
|---------|-------|---------|
| **Idempotency** | API / business logic | Multiple identical calls produce the same system state and the same observable response. `f(f(x)) = f(x)` |
| **Deduplication** | Transport / processing pipeline | Drop messages we've seen before (usually via an ID and a dedup store). |
| **Exactly-once** | Aspirational | "Each message is processed exactly once." In practice, this is achieved as **at-least-once delivery + idempotent handler**. |

A payment API that guarantees "charging twice with the same idempotency key produces one charge" is idempotent, even if the transport sent the request twice.

---

## 3. HTTP methods and idempotency

| Method | Idempotent by spec? | Notes |
|--------|---------------------|-------|
| GET    | Yes (and safe)      | No side effects |
| PUT    | Yes                 | Replaces the resource; repeating = same state |
| DELETE | Yes                 | Repeating returns 404 / 204 — state converges |
| POST   | **No**              | Creates new resource each call → needs idempotency key |
| PATCH  | Usually no          | `increment by 1` is not idempotent |

The POST row is where the interview drama happens. Most mutating endpoints are POSTs. They need extra engineering.

---

## 4. The idempotency key pattern (the canonical solution)

### 4.1 API contract

```
POST /v1/charges
Idempotency-Key: 2f0c3fa2-7b32-4a1c-8b23-24d47c5e9b55
Content-Type: application/json

{
  "amount": 1000,
  "currency": "usd",
  "card": "tok_visa"
}
```

The client generates a UUID once *before* making the call. If it has to retry, it uses the *same key*. The server guarantees that repeated requests with the same key return the same response (same status, same body), without reprocessing.

### 4.2 Server-side state machine

```
                  request with key K arrives
                             │
                             ▼
               ┌─────────────────────────────┐
               │   Store lookup on key K     │
               └────────────┬────────────────┘
                            │
            ┌───────────────┼────────────────┐
            │               │                │
      not found         in-flight        completed
            │               │                │
            ▼               ▼                ▼
      insert "in-       return 409      return stored
      flight" row       Conflict or     response
      + process         wait-and-poll   (same body,
      + on finish,                       same status code)
      update row
      with response
```

### 4.3 Storage considerations

- Store `(key, request_fingerprint, status, response_body, created_at)`.
- **TTL** = time the client could still reasonably retry (24h for most APIs).
- Partition by `key` (hash-partitioning); the row is written once at request start and updated at finish.
- Unique index on `key` prevents concurrent duplicates.
- `request_fingerprint` = hash of the body. If a subsequent request arrives with the same key but a different body, reject with 422 — the client is confused.

### 4.4 Race between two retries

If the client fires the retry *before* the first request finishes:

```
  t=0    R1 arrives, writes "in-flight" with key K
  t=1    R2 arrives, key K exists and is in-flight
         → return 409 Conflict (safe) or block until R1 finishes
```

Blocking is cleaner for the client but ties up a thread. Most real implementations use `409 Conflict` + `Retry-After` header — the client retries once the server is done.

### 4.5 Stripe's guarantee (the industry reference)

Stripe's docs state that their idempotency layer has a **24-hour window** and a **sticky response** for that window. After the window, the key is reusable. This is the template to copy.

---

## 5. Other idempotency patterns

### 5.1 Natural idempotency via conditional writes

- **Compare-and-set:** `UPDATE … WHERE version = expected_version`. If the update was already applied by an earlier retry, `rows_affected = 0` and the caller knows.
- **INSERT … ON CONFLICT DO NOTHING** (Postgres) / `INSERT IGNORE` (MySQL). Works when a natural unique key exists.
- **PUT semantics.** "Set order status to `PAID`" — repeat freely.

### 5.2 Idempotent consumers in a message queue

The queue delivers at-least-once. The consumer deduplicates by message ID.

```
consumer:
    if dedup_store.seen(message.id):        # Redis SET with TTL
        ack(message); return
    process(message)                         # in a transaction with…
    dedup_store.mark_seen(message.id)        # …the dedup write (outbox/2PC)
    ack(message)
```

The *co-transactionality* of `process` and `mark_seen` is the delicate part:
- Easy case: both are in the same relational DB — one transaction.
- Hard case: processing writes to multiple systems. Use the **outbox pattern** (see [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md)) — write the side effects and the dedup record to one DB, then publish events from the outbox.

### 5.3 Natural dedup via business uniqueness

Sometimes the business already has uniqueness:

- Order number.
- Email address (signup idempotency).
- External transaction ID (from the payment processor).
- `(user_id, resource, day)` — e.g. "one vote per user per day".

Use a `UNIQUE` constraint. Duplicate inserts fail loudly; catch and return the existing row.

### 5.4 Idempotent tokens / nonces

Pre-allocate an idempotency token to the client. Client presents it once; server marks it used. Can't be replayed.

---

## 6. Exactly-once *processing* semantics (streams)

In stream processing, "exactly-once" is real — but only within a closed processing loop under specific preconditions.

```
Kafka                Stream Processor             Sink
─────                ───────────────             ────
offsets committed ◄──── atomically ────► output written
                  same transaction
```

- **Kafka + Kafka Streams / Flink** give you exactly-once by tying **offset commits** and **output writes** into the *same* transaction (or two-phase commit-like protocol).
- The output sink must support transactions (Kafka topic, PostgreSQL).
- A plain HTTP sink with no idempotency support = back to at-least-once + need idempotent API on the far side.

More depth: [../03-AdvancedConcepts/DistributedSystems/11-DeliverySemantics.md](../03-AdvancedConcepts/DistributedSystems/11-DeliverySemantics.md) and [../03-AdvancedConcepts/05-StreamProcessing.md](../03-AdvancedConcepts/05-StreamProcessing.md).

---

## 7. Layered view (how all pieces fit)

```
┌──────────────────────────────────────────────────────────────────┐
│                  IDEMPOTENCY END-TO-END                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Client   ─── Idempotency-Key: K ─────────────────────────►       │
│                                                                  │
│  LB / Gateway  (passthrough; may add request-id)                 │
│                                                                  │
│  API Service                                                     │
│     ├── dedup store lookup on K                                  │
│     ├── transaction:                                             │
│     │     INSERT idempotency_keys(K, status='in-flight')         │
│     │     do business logic                                      │
│     │     INSERT outbox(event)                                   │
│     │     UPDATE idempotency_keys SET response=…, status='done'  │
│     │                                                            │
│     └── return response                                          │
│                                                                  │
│  Outbox relay → Kafka topic (at-least-once)                      │
│                                                                  │
│  Downstream consumer                                             │
│     ├── dedup by event.id (Redis or Flink state)                 │
│     ├── process (idempotent against its DB)                      │
│     └── commit offset + dedup mark atomically                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

Every hop has its own dedup/idempotency. That is how you get "effectively-once".

---

## 8. Common pitfalls

| Pitfall | Failure mode |
|---------|--------------|
| Client generates a *different* key on each retry | Each retry charges the card again |
| Server stores key only after success | Crash between processing and storing → duplicate processing on retry |
| No `request_fingerprint` check | Client sends key K with different bodies → inconsistent stored response |
| Unbounded TTL on dedup store | Grows to infinity; store becomes the bottleneck |
| Too-short TTL (e.g. 5 min) | Client retries after network flap at T=6 min → duplicate |
| Single node Redis for dedup | SPOF → if Redis dies, system either stops or fails open to duplicates |
| Dedup only on ID, not on content | Two logically different events with the same ID are conflated |
| Retries at multiple layers (LB + client + framework) | Same request tries 27 times with *different* keys (if the gateway doesn't pass the key through) |

---

## 9. Interview talking points

- **Say "at-least-once delivery + idempotent processing = effectively-once".** Avoid the phrase "exactly-once delivery"; interviewers will push back.
- **Prescribe an idempotency key for every mutating API.** "POST /charges takes `Idempotency-Key` header; server stores key + response for 24 h; re-requests in that window return the stored response."
- **Call out the in-flight race.** "Concurrent duplicates → 409 with `Retry-After`."
- **Outbox pattern for cross-system consistency.** It's the senior-signal answer when the interviewer asks "what if you publish to Kafka *and* write to the DB?"
- **Different layers, same idea.** HTTP idempotency key, Kafka message ID dedup, stream processor transactional offsets — all instances of the same pattern.
- **Quantify the dedup store.** "If we get 1000 TPS with a 24 h window and ~1 KB per entry, that's 86 GB — fits easily in a sharded Redis cluster."

---

## 10. Related reading

- [../06-DesignHard/03-PaymentSystem.md](../06-DesignHard/03-PaymentSystem.md) — the canonical interview question for idempotency.
- [../03-AdvancedConcepts/DistributedSystems/11-DeliverySemantics.md](../03-AdvancedConcepts/DistributedSystems/11-DeliverySemantics.md) — at-most-once / at-least-once / exactly-once.
- [14-ChangeDataCaptureVsDualWrites.md](14-ChangeDataCaptureVsDualWrites.md) — outbox pattern.
- [05-RetryStormsAndCircuitBreakers.md](05-RetryStormsAndCircuitBreakers.md) — the reason duplicates exist in the first place.
- [10-DeadLetterQueuesAndPoisonMessages.md](10-DeadLetterQueuesAndPoisonMessages.md) — dedup collides with poison messages; pick your failure mode.
