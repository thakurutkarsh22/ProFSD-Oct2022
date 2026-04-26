# Dead-Letter Queues & Poison Messages

> **TL;DR.** A **poison message** is any message a consumer cannot process — bad schema, business rule violation, missing dependency, or permanent bug. Without a plan, one poison message **blocks the entire partition** (Kafka) or gets re-delivered forever (SQS), eventually DoSing your own pipeline. The solution is a **Dead-Letter Queue (DLQ)** with a **retry-then-divert policy**, a **replay process**, and **alerting on DLQ depth**.

---

## 1. What a poison message looks like

You are consuming a stream of `OrderCreated` events. One event lands with a `customer_id` that your customer service returns 404 for. Your handler throws; the consumer does not commit the offset. The broker redelivers. The handler throws again. Forever.

```
Kafka partition 3:
  offset 1000  OrderCreated{id:1, customer:17} ✅  processed, offset committed
  offset 1001  OrderCreated{id:2, customer:33} ✅
  offset 1002  OrderCreated{id:3, customer:NaN} ❌  NullPointerException
  offset 1003  OrderCreated{id:4, customer:44}     never seen, blocked behind offset 1002
  offset 1004  OrderCreated{id:5, customer:45}     never seen
  ...
```

Consumer lag rockets. Everything downstream of partition 3 is stuck. This is the *classic* production outage that a DLQ prevents.

---

## 2. The pattern in one diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     POISON MESSAGE FLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                    ┌───────────────┐                            │
│     main queue ───►│   Consumer    │                            │
│                    │               │                            │
│                    │ try process() │                            │
│                    └──────┬────────┘                            │
│                           │                                     │
│             success ◄─────┼─────► failure                       │
│                           │                                     │
│              commit offset / ack     N retries with backoff     │
│                                      (maybe to a retry topic)   │
│                                           │                     │
│                                           │ still failing?      │
│                                           ▼                     │
│                                  ┌────────────────┐             │
│                                  │      DLQ       │             │
│                                  │  (bad message  │             │
│                                  │   + headers:   │             │
│                                  │    error, try, │             │
│                                  │    origin)     │             │
│                                  └────────┬───────┘             │
│                                           │                     │
│                                           │ human / automated   │
│                                           │ triage              │
│                                           ▼                     │
│                                    ┌──────────────┐             │
│                                    │   Replay     │             │
│                                    │  after fix   │             │
│                                    └──────────────┘             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Retry topology variants

### 3.1 SQS / RabbitMQ — DLQ is built-in

- SQS: set `RedrivePolicy { maxReceiveCount: 5, deadLetterTargetArn: ... }`. After 5 `ReceiveMessage`+failure cycles, SQS moves the message to the DLQ automatically.
- RabbitMQ: set `x-dead-letter-exchange` on the queue; rejected (`basic.nack`) messages are routed to that exchange.

### 3.2 Kafka — no built-in DLQ; you build it

Standard pattern (Confluent reference, Spring Cloud Stream's default):

```
main_topic.orders
     │
     └─► consumer
           ├── try → success: commit offset
           └── fail → publish to retry_topic.orders.5s, commit offset
                            (and skip)
                     │
                     └─► delayed consumer
                           │
                           └── fail again → publish to retry_topic.orders.30s
                                                  │
                                                  └─► fail again → DLQ topic
```

- Retry topics have increasing delays (5 s, 30 s, 5 min, 1 h) implemented by **consumer-side sleep until** a `process_after` timestamp in the header.
- Commit offset on the main topic *immediately* after writing to the retry topic — so a poison message doesn't block its partition.

### 3.3 Google Pub/Sub, AWS Kinesis

- Pub/Sub: native DLQ support with `deadLetterPolicy`.
- Kinesis: no native DLQ. Convention: checkpoint past the failed record, write it to an S3 "errors" prefix or SQS DLQ. Write your own.

---

## 4. What to attach to a DLQ message

A DLQ entry with no context is worse than dropping the message. Every DLQ record needs:

```json
{
  "original_payload": { ... },
  "original_headers":  { ... },
  "error_class":       "IllegalStateException",
  "error_message":     "Customer 33 not found",
  "stack_trace":       "...",
  "retry_count":       3,
  "first_failed_at":   "2025-04-23T14:25:00Z",
  "last_failed_at":    "2025-04-23T14:25:12Z",
  "consumer_id":       "order-processor-v1.4.2",
  "origin_topic":      "orders",
  "origin_partition":  3,
  "origin_offset":     1002
}
```

Without this, the replay path can't find the original source, and on-call can't debug.

---

## 5. When NOT to DLQ (common mistakes)

Not all failures are "poison". Misclassifying causes data loss.

| Failure type | DLQ? | What to do |
|--------------|------|------------|
| **Transient** — DB connection dropped, 5xx from downstream | **No** — retry with backoff | Circuit breaker + exponential backoff |
| **Rate-limited** — you hit a 429 | **No** | Honour `Retry-After`, slow down |
| **Data-dependent permanent** — bad enum, referential integrity miss | **Yes** | DLQ after N retries |
| **Code bug that affects all messages** | **No — stop the consumer** | DLQ would drain your entire stream |
| **Schema evolution mismatch** | **Sometimes** | DLQ for versioning debt; deploy a fixed consumer |

Senior engineers classify the error **before** routing to DLQ. A retryable error sent to DLQ is a delivery failure; a permanent error retried forever blocks the queue.

---

## 6. How do we know a code bug vs a data bug?

Rule of thumb: **batch rate of DLQ entries**.

- 1 message/minute going to DLQ → probably real bad data.
- 1000 messages/second going to DLQ → almost certainly your consumer is broken.

A **DLQ rate alert** ("> 10 messages/min to DLQ") is as important as consumer-lag alerts. Most teams forget this.

Implement a **poison-pill circuit breaker**:
```
if dlq_rate(last 1m) > 1% of throughput:
    stop the consumer
    page on-call
    (don't keep draining good data into DLQ because your parser is off-by-one)
```

---

## 7. Replay from DLQ — always design for it

A DLQ full of unprocessable messages is useless unless you can:

1. **Inspect** individual entries (UI / CLI over the DLQ topic).
2. **Fix** the underlying cause (code, data, config).
3. **Replay** selectively back to the main topic *or* bypass it into a repair consumer.

### 7.1 Replay pitfalls

- **Replay sequence matters.** If events have causal order (`OrderCreated` → `OrderPaid`), replaying out of order breaks downstream state.
- **Re-deduplication.** If the failed message was half-processed last time, replaying creates a duplicate — idempotent handlers required (see [06-IdempotencyAndDeduplication.md](06-IdempotencyAndDeduplication.md)).
- **Replay ≠ new event.** Mark replayed events with a header `replayed=true` so downstream can distinguish.
- **Rate-limit the replay.** A replay of 1M messages should not saturate the live pipeline.

---

## 8. Anti-patterns and their failures

| Anti-pattern | What goes wrong |
|--------------|-----------------|
| No DLQ at all | One bad message blocks the partition forever; lag grows; SLO breached |
| Infinite retry with no DLQ | Same outcome — queue never drains |
| DLQ with no alert | Messages pile up silently; team learns 3 months later that they've been dropping events |
| DLQ with no retention | Messages expire in 24 h; bug takes 3 days to fix → data loss |
| DLQ at the wrong scope | Per-consumer DLQ but the bug is in the source topic's schema — you can't tell which consumer suffered |
| Auto-replay on a timer | Poison message goes DLQ → auto-replay → DLQ again → infinite loop |
| No metadata in DLQ | Replay is guesswork; root cause analysis impossible |
| Manually "skip" bad offsets in prod | Irrecoverable. Data gone with no trace. |

---

## 9. Observability & SLOs

- **DLQ depth** — current number of messages. Should trend to zero; static > 0 means unresolved issues.
- **DLQ ingress rate** — alert on spikes.
- **Time-to-triage** — how long from arrival in DLQ to resolution. Track it like an incident SLO.
- **Replay success rate** — replaying should succeed after a fix; if it doesn't, your fix is wrong.
- **Consumer lag vs DLQ rate correlation** — a sudden lag + DLQ spike ⇒ poison message; lag without DLQ ⇒ throughput problem.

Good dashboard tile:

```
┌──────────────────── orders consumer ────────────────────┐
│ Throughput:        2,340 msg/s    (healthy)             │
│ Consumer lag:      145 (partition 3 = 1200 ← spike)     │
│ DLQ ingress rate:  0.7 /s         (healthy baseline)    │
│ DLQ depth:         312            (12 new this hour)    │
│ Retry topic depth: 8              (retries in flight)   │
└─────────────────────────────────────────────────────────┘
```

---

## 10. Full example — order processor with DLQ

```java
@KafkaListener(topics = "orders", groupId = "order-proc")
public void onOrder(ConsumerRecord<String, Order> record,
                    Acknowledgment ack) {
    try {
        processor.process(record.value());
        ack.acknowledge();                        // advance main offset
    } catch (TransientException e) {
        // bounded exponential retry at the SAME offset
        throw e;
    } catch (PoisonException e) {
        dlqPublisher.publish(
            DlqEnvelope.builder()
                .payload(record.value())
                .headers(record.headers())
                .error(e)
                .origin(record.topic(),
                        record.partition(),
                        record.offset())
                .retries(e.getRetries())
                .build()
        );
        ack.acknowledge();                        // advance past poison
        dlqCounter.increment();
    }
}
```

Decisions encoded:
- Two exception types separate transient from permanent.
- DLQ publish is synchronous; only after success do we advance offset (else we might lose it).
- Metric fires so ops can alert on DLQ ingress.

---

## 11. Interview talking points

- **Name the risk concretely.** "In Kafka, a poison message without a DLQ halts the partition; lag explodes until someone skips the offset."
- **Prescribe the retry-then-DLQ with metadata.** Don't just say "we'll DLQ it" — describe the retry ladder, offset semantics, and the metadata schema.
- **Classify transient vs permanent.** This shows you won't DLQ a blip.
- **Design the replay.** "The DLQ is only useful if we can fix the root cause and replay — with idempotent consumers and a replayed=true flag."
- **DLQ rate alert.** Make a point of it; most candidates don't mention this.
- **Cross-link to idempotency.** "Replay creates duplicates, which is only safe if the consumer is idempotent."

---

## 12. Related reading

- [../02-BuildingBlocks/04-MessageQueues.md](../02-BuildingBlocks/04-MessageQueues.md) — queue primitives.
- [../Components/01-Kafka.md](../Components/01-Kafka.md), [../Components/02-SQS.md](../Components/02-SQS.md), [../Components/04-RabbitMQ.md](../Components/04-RabbitMQ.md) — per-broker specifics.
- [../03-AdvancedConcepts/DistributedSystems/11-DeliverySemantics.md](../03-AdvancedConcepts/DistributedSystems/11-DeliverySemantics.md) — at-least-once, context for why poison messages occur.
- [06-IdempotencyAndDeduplication.md](06-IdempotencyAndDeduplication.md) — replay safety.
- [05-RetryStormsAndCircuitBreakers.md](05-RetryStormsAndCircuitBreakers.md) — transient vs permanent error handling.
