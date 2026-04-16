# Amazon SQS — The Complete Deep Dive

> **Difficulty:** Medium-Hard | **Time:** 5-7 hours | **Priority:** Must Know  
> **Sources:** AWS SQS Developer Guide, AWS Whitepaper, AWS re:Invent Talks, AWS News Blog, Uber Engineering, Capital One Tech, Segment Engineering  
> **For:** Senior Engineers (8+ years) preparing for System Design interviews

---

## Table of Contents

1. [What Is SQS](#1-what-is-sqs)
2. [Core Architecture](#2-core-architecture)
3. [Standard vs FIFO Queues](#3-standard-vs-fifo-queues)
4. [Message Lifecycle Deep Dive](#4-message-lifecycle-deep-dive)
5. [Visibility Timeout — The Hidden Complexity](#5-visibility-timeout--the-hidden-complexity)
6. [Dead Letter Queues & Redrive](#6-dead-letter-queues--redrive)
7. [Deduplication & Exactly-Once Processing](#7-deduplication--exactly-once-processing)
8. [SQS Internals — How AWS Built It](#8-sqs-internals--how-aws-built-it)
9. [Integration Patterns](#9-integration-patterns)
10. [Consumer Scaling & Backpressure](#10-consumer-scaling--backpressure)
11. [SQS vs Kafka vs RabbitMQ](#11-sqs-vs-kafka-vs-rabbitmq)
12. [Real-World Usage at Scale](#12-real-world-usage-at-scale)
13. [When SQS Failed — Production Incidents](#13-when-sqs-failed--production-incidents)
14. [When SQS Cannot Cope — Limitations](#14-when-sqs-cannot-cope--limitations)
15. [Anti-Patterns That Kill SQS Systems](#15-anti-patterns-that-kill-sqs-systems)
16. [Performance Tuning Cheat Sheet](#16-performance-tuning-cheat-sheet)
17. [Interview Questions — Medium](#17-interview-questions--medium)
18. [Interview Questions — Hard](#18-interview-questions--hard)
19. [Quick Reference Card](#19-quick-reference-card)

---

## 1. What Is SQS

Amazon Simple Queue Service (SQS) is a **fully managed distributed message queue service** launched in 2004 — it was AWS's **very first service** (before S3, before EC2). It decouples producers from consumers in distributed systems.

It is NOT an event streaming platform. It is a **transient message queue** — messages are consumed and deleted.

```
Event Streaming (Kafka):                 Message Queue (SQS):

  Producer → [Append-Only Log] → Consumer   Producer → [Queue] → Consumer
                                           
  ✓ Messages retained (time/size)           ✗ Message deleted after processing
  ✓ Multiple consumer groups replay         ✗ Single consumer per message
  ✓ Replay from any offset                  ✗ No replay after deletion
  ✓ Ordering per partition                  ✓ Ordering per message group (FIFO)
  ✓ 1M+ msg/s per cluster                  ✓ ~100M+ msg/s (managed, auto-scaled)
  ✗ You manage infrastructure               ✓ Fully managed, zero ops
  ✗ Complex consumer group mgmt             ✓ Simple poll-based consumption
```

### The Three Guarantees SQS Provides

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   SQS = THREE GUARANTEES                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. DURABILITY                2. AVAILABILITY           3. SCALABILITY      │
│  ┌────────────────────┐   ┌─────────────────────┐   ┌──────────────────┐   │
│  │ Messages stored     │   │ Distributed across   │   │ Auto-scales      │   │
│  │ redundantly across  │   │ multiple AZs within  │   │ transparently    │   │
│  │ multiple AZs        │   │ a region. No single  │   │ from 1 msg/day   │   │
│  │                     │   │ point of failure     │   │ to 100M msg/sec  │   │
│  └────────────────────┘   └─────────────────────┘   └──────────────────┘   │
│                                                                             │
│  → Data survives AZ failure   → 99.999999999%        → No provisioning     │
│    without message loss         (11 9s) durability      required at all     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why SQS Exists — The Fundamental Problem

```
WITHOUT A QUEUE (Tight Coupling):

  ┌──────────┐     ┌──────────┐     ┌──────────┐
  │ Order    │────▶│ Payment  │────▶│ Shipping │
  │ Service  │     │ Service  │     │ Service  │
  └──────────┘     └──────────┘     └──────────┘
       │                │                │
       └── If Payment is down, Orders fail ──┘
       └── If Shipping is slow, everything backs up ──┘
       └── One failure cascades through the entire chain ──┘

WITH SQS (Decoupled):

  ┌──────────┐     ┌─────┐     ┌──────────┐     ┌─────┐     ┌──────────┐
  │ Order    │────▶│ SQS │────▶│ Payment  │────▶│ SQS │────▶│ Shipping │
  │ Service  │     │  Q1 │     │ Service  │     │  Q2 │     │ Service  │
  └──────────┘     └─────┘     └──────────┘     └─────┘     └──────────┘
       │                            │                            │
       └── Orders accepted even if Payment is down ──┘           │
       └── Payment processes at its own pace ──────────────────┘
       └── Each service scales independently ──────────────────┘
```

---

## 2. Core Architecture

### The Master Diagram — How Everything Fits Together

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│                             SQS ECOSYSTEM — FULL PICTURE                                 │
│                                                                                          │
│  PRODUCERS (write messages)                                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                    │
│  │ Order Service │ │ API Gateway  │ │ SNS Topic    │ │ EventBridge  │                    │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘                    │
│         │                │                │                │                              │
│         │ SendMessage    │ SendMessage    │ Subscription   │ Target                       │
│         │                │  Batch         │  push          │  delivery                    │
│         ▼                ▼                ▼                ▼                              │
│  ═══════════════════════════════════════════════════════════════════════                   │
│                                                                                          │
│   SQS SERVICE (Fully Managed by AWS)                                                     │
│  ┌────────────────────────────────────────────────────────────────────────────────┐       │
│  │                                                                                │       │
│  │  CUSTOMER FRONT-END LAYER                                                      │       │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐                      │       │
│  │  │ Auth &        │  │ Request       │  │ Rate          │                      │       │
│  │  │ Authorize     │  │ Routing       │  │ Limiting      │                      │       │
│  │  └───────────────┘  └───────────────┘  └───────────────┘                      │       │
│  │         │                   │                   │                               │       │
│  │         ▼                   ▼                   ▼                               │       │
│  │  ┌─────────────────────────────────────────────────────────────────────┐       │       │
│  │  │               BINARY FRAMING PROTOCOL (since 2024)                  │       │       │
│  │  │  ┌──────────────────────────────────────────────────────────┐       │       │       │
│  │  │  │ Multiplexed connections │ 128-bit IDs │ Checksumming    │       │       │       │
│  │  │  └──────────────────────────────────────────────────────────┘       │       │       │
│  │  └─────────────────────────────────────────────────────────────────────┘       │       │
│  │         │                   │                   │                               │       │
│  │         ▼                   ▼                   ▼                               │       │
│  │  STORAGE BACK-END (Cell-Based Architecture)                                    │       │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐               │       │
│  │  │   Cell A         │  │   Cell B         │  │   Cell C         │               │       │
│  │  │  ┌─────────────┐ │  │  ┌─────────────┐ │  │  ┌─────────────┐ │               │       │
│  │  │  │ Cluster 1   │ │  │  │ Cluster 3   │ │  │  │ Cluster 5   │ │               │       │
│  │  │  │ ┌─────────┐ │ │  │  │ ┌─────────┐ │ │  │  │ ┌─────────┐ │ │               │       │
│  │  │  │ │ Host A  │ │ │  │  │ │ Host E  │ │ │  │  │ │ Host I  │ │ │               │       │
│  │  │  │ │ Host B  │ │ │  │  │ │ Host F  │ │ │  │  │ │ Host J  │ │ │               │       │
│  │  │  │ └─────────┘ │ │  │  │ └─────────┘ │ │  │  │ └─────────┘ │ │               │       │
│  │  │  ├─────────────┤ │  │  ├─────────────┤ │  │  ├─────────────┤ │               │       │
│  │  │  │ Cluster 2   │ │  │  │ Cluster 4   │ │  │  │ Cluster 6   │ │               │       │
│  │  │  │ ┌─────────┐ │ │  │  │ ┌─────────┐ │ │  │  │ ┌─────────┐ │ │               │       │
│  │  │  │ │ Host C  │ │ │  │  │ │ Host G  │ │ │  │  │ │ Host K  │ │ │               │       │
│  │  │  │ │ Host D  │ │ │  │  │ │ Host H  │ │ │  │  │ │ Host L  │ │ │               │       │
│  │  │  │ └─────────┘ │ │  │  │ └─────────┘ │ │  │  │ └─────────┘ │ │               │       │
│  │  │  └─────────────┘ │  │  └─────────────┘ │  │  └─────────────┘ │               │       │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘               │       │
│  │                                                                                │       │
│  │  Messages replicated across multiple AZs within each cell                      │       │
│  └────────────────────────────────────────────────────────────────────────────────┘       │
│                                                                                          │
│         │                   │                   │                                         │
│         ▼                   ▼                   ▼                                         │
│  ═══════════════════════════════════════════════════════════════════════                   │
│                                                                                          │
│  CONSUMERS (read & delete messages)                                                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                    │
│  │ Lambda       │ │ ECS Task     │ │ EC2 Worker   │ │ K8s Pod      │                    │
│  │ (event src)  │ │ (long poll)  │ │ (long poll)  │ │ (long poll)  │                    │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘                    │
│                                                                                          │
│  DEAD LETTER QUEUE (DLQ)                                                                 │
│  ┌──────────────────────────────────────────────────────────────────┐                    │
│  │ Messages that failed maxReceiveCount times land here             │                    │
│  │ → Monitor with CloudWatch alarms                                 │                    │
│  │ → Redrive back to source queue after fixing the bug              │                    │
│  └──────────────────────────────────────────────────────────────────┘                    │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### API Surface — Only 7 Core Operations

```
┌────────────────────────────────────────────────────────────────────┐
│                    SQS API — DECEPTIVELY SIMPLE                    │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  QUEUE MANAGEMENT                                                  │
│  ├── CreateQueue         → Create Standard or FIFO queue           │
│  ├── DeleteQueue         → Delete queue and all messages           │
│  ├── GetQueueAttributes  → Read queue config (depth, ARN, etc.)    │
│  └── SetQueueAttributes  → Update config (visibility, policy)      │
│                                                                    │
│  MESSAGE OPERATIONS                                                │
│  ├── SendMessage         → Publish 1 message (up to 256 KB)       │
│  ├── SendMessageBatch    → Publish up to 10 messages at once       │
│  ├── ReceiveMessage      → Poll for 1-10 messages                  │
│  ├── DeleteMessage       → Acknowledge processing complete         │
│  ├── DeleteMessageBatch  → Acknowledge up to 10 at once            │
│  └── ChangeMessageVisibility → Extend/shorten processing time      │
│                                                                    │
│  KEY INSIGHT: There is no "subscribe" or "push" API.               │
│  SQS is PULL-BASED. Consumers must poll.                           │
│  (Exception: Lambda event source mapping — AWS polls for you)      │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 3. Standard vs FIFO Queues

This is one of the most asked interview topics. Know the trade-offs cold.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    STANDARD QUEUE vs FIFO QUEUE                                  │
├────────────────────────┬─────────────────────────┬───────────────────────────────┤
│  Property              │  Standard Queue          │  FIFO Queue                  │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Throughput            │  Nearly unlimited         │  300 TPS default             │
│                        │  (100M+ msg/sec)          │  3,000 with batching         │
│                        │                           │  70,000 with high throughput │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Delivery              │  At-least-once            │  Exactly-once                │
│                        │  (duplicates possible)    │  (within 5-min window)       │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Ordering              │  Best-effort              │  Strict FIFO per             │
│                        │  (mostly ordered)         │  MessageGroupId              │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Deduplication         │  None                     │  Content-based or            │
│                        │  (handle in app)          │  explicit dedup ID           │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Queue Name            │  Any valid name           │  Must end in .fifo           │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  In-Flight Messages    │  120,000 max              │  120,000 max                 │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Batching              │  10 msgs / 256 KB         │  10 msgs / 256 KB            │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Use When              │  High throughput needed    │  Ordering matters            │
│                        │  Occasional duplicates OK  │  No duplicates allowed       │
│                        │  Log processing, metrics   │  Financial txns, commands    │
├────────────────────────┼─────────────────────────┼───────────────────────────────┤
│  Cost (per M requests) │  $0.40                    │  $0.50                       │
└────────────────────────┴─────────────────────────┴───────────────────────────────┘
```

### FIFO Message Groups — The Key to Parallel FIFO

```
WRONG: Single MessageGroupId (serialized, slow)
┌──────────────────────────────────────────────────┐
│  FIFO Queue with GroupId = "all"                  │
│                                                    │
│  msg1 → msg2 → msg3 → msg4 → msg5 → msg6         │
│  ▲                                                 │
│  Only ONE consumer can process at a time!          │
│  Throughput = 1 consumer's speed                   │
└──────────────────────────────────────────────────┘

RIGHT: Multiple MessageGroupIds (parallel, fast)
┌──────────────────────────────────────────────────┐
│  FIFO Queue with multiple GroupIds                │
│                                                    │
│  GroupId="user-A":  msg1 → msg3 → msg5            │
│                       ▲                            │
│                     Consumer 1                     │
│                                                    │
│  GroupId="user-B":  msg2 → msg6                    │
│                       ▲                            │
│                     Consumer 2                     │
│                                                    │
│  GroupId="user-C":  msg4                           │
│                       ▲                            │
│                     Consumer 3                     │
│                                                    │
│  Throughput = N consumers × speed per consumer     │
│  Ordering guaranteed WITHIN each group only        │
└──────────────────────────────────────────────────┘
```

### Interview Trap: "Is FIFO truly FIFO across all messages?"

**No.** FIFO ordering is ONLY within a single `MessageGroupId`. Messages across different groups can interleave. This trips up 80% of candidates. The correct answer: "FIFO guarantees per-group ordering, not global ordering. To get global ordering, you'd use a single MessageGroupId, but that serializes everything to ~300 TPS."

---

## 4. Message Lifecycle Deep Dive

### The Complete Journey of a Message

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         MESSAGE LIFECYCLE IN SQS                                 │
│                                                                                  │
│  PHASE 1: SEND                                                                   │
│  ┌──────────┐    SendMessage()     ┌──────────────────────────────────┐          │
│  │ Producer │───────────────────▶  │ SQS Front-End                    │          │
│  └──────────┘                      │  1. Authenticate (IAM/SigV4)    │          │
│                                    │  2. Validate (size ≤ 256 KB)    │          │
│       Optional:                    │  3. Route to storage cluster    │          │
│       - DelaySeconds (0-900)       │  4. Replicate across AZs       │          │
│       - MessageAttributes          │  5. Return MessageId + MD5     │          │
│       - MessageGroupId (FIFO)      └──────────────────────────────────┘          │
│       - DeduplicationId (FIFO)                    │                              │
│                                                   ▼                              │
│  PHASE 2: WAIT (message sits in queue)                                           │
│  ┌────────────────────────────────────────────────────────────────────┐          │
│  │  Message is VISIBLE (available for consumption)                    │          │
│  │                                                                    │          │
│  │  If DelaySeconds > 0:  message is DELAYED (invisible) first       │          │
│  │  ┌──────────────┐     ┌──────────────┐                            │          │
│  │  │  DELAYED      │────▶│  VISIBLE      │                            │          │
│  │  │  (invisible)  │     │  (available)  │                            │          │
│  │  └──────────────┘     └──────────────┘                            │          │
│  │  0-900 seconds                                                     │          │
│  └────────────────────────────────────────────────────────────────────┘          │
│                                                   │                              │
│                                                   ▼                              │
│  PHASE 3: RECEIVE                                                                │
│  ┌──────────┐    ReceiveMessage()   ┌──────────────────────────────────┐         │
│  │ Consumer │◀──────────────────── │ SQS returns:                     │         │
│  └──────────┘                      │  - MessageBody                   │         │
│                                    │  - ReceiptHandle (delete token)  │         │
│       Receives up to 10 msgs       │  - MessageAttributes             │         │
│       Long poll: wait up to 20s    │  - ApproximateReceiveCount       │         │
│                                    └──────────────────────────────────┘         │
│                                                   │                              │
│                                                   ▼                              │
│  PHASE 4: PROCESSING (message is IN-FLIGHT / invisible)                          │
│  ┌────────────────────────────────────────────────────────────────────┐          │
│  │                                                                    │          │
│  │  ┌─ Visibility Timeout starts (default 30s, max 12h) ──────────┐ │          │
│  │  │                                                               │ │          │
│  │  │  Consumer processes message...                                │ │          │
│  │  │                                                               │ │          │
│  │  │  PATH A: Success → DeleteMessage(ReceiptHandle) → DONE ✓     │ │          │
│  │  │                                                               │ │          │
│  │  │  PATH B: Need more time → ChangeMessageVisibility() → Reset  │ │          │
│  │  │                                                               │ │          │
│  │  │  PATH C: Consumer crashes → Timeout expires → msg VISIBLE    │ │          │
│  │  │          again → another consumer picks it up (RETRY)        │ │          │
│  │  │                                                               │ │          │
│  │  │  PATH D: maxReceiveCount exceeded → msg → DLQ                │ │          │
│  │  │                                                               │ │          │
│  │  └───────────────────────────────────────────────────────────────┘ │          │
│  │                                                                    │          │
│  └────────────────────────────────────────────────────────────────────┘          │
│                                                                                  │
│  PHASE 5: DELETION or EXPIRY                                                     │
│  ┌────────────────────────────────────────────────────────────────────┐          │
│  │  DeleteMessage() → Message permanently removed from queue          │          │
│  │  OR                                                                │          │
│  │  Retention period expires (default 4 days, max 14 days) → Purged  │          │
│  └────────────────────────────────────────────────────────────────────┘          │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Short Polling vs Long Polling

```
SHORT POLLING (default, wasteful):
┌──────────┐                           ┌─────┐
│ Consumer │──── ReceiveMessage() ────▶│ SQS │  Checks SUBSET of servers
│          │◀─── Empty response ──────│     │  Returns immediately if empty
│          │                           │     │
│          │──── ReceiveMessage() ────▶│     │  Checks different SUBSET
│          │◀─── Empty response ──────│     │  Most calls return empty!
│          │                           │     │
│          │──── ReceiveMessage() ────▶│     │  $$$ you pay per request
│          │◀─── 1 message ───────────│     │
└──────────┘                           └─────┘
Problem: ~70% of calls return empty. You pay $0.40/million for nothing.

LONG POLLING (WaitTimeSeconds=20, recommended):
┌──────────┐                           ┌─────┐
│ Consumer │──── ReceiveMessage() ────▶│ SQS │  Checks ALL servers
│          │     WaitTimeSeconds=20    │     │  Waits up to 20s for msg
│          │                           │     │
│          │       ... waiting ...     │     │  Connection held open
│          │                           │     │
│          │◀─── Messages! ───────────│     │  Returns as soon as
│          │                           │     │  messages arrive
└──────────┘                           └─────┘
Benefit: Fewer empty responses, lower cost, lower latency to receive.
```

---

## 5. Visibility Timeout — The Hidden Complexity

This is where most production bugs live. Understand it deeply.

### What Is Visibility Timeout?

Visibility timeout is the **lock period** after a consumer receives a message from SQS. During this window, the message becomes **invisible to all other consumers** — preventing two workers from processing the same message simultaneously.

**Think of it like a library book checkout:** you check out a book (receive a message), and you have 30 minutes to read and return it (delete it). If you don't return it in time, the library puts it back on the shelf for someone else to grab.

**The lifecycle in plain English:**

1. Consumer A calls `ReceiveMessage()` — SQS hands over the message **and starts a countdown timer** (default: 30 seconds)
2. The message is now **invisible** — no other consumer can see it, even if they poll the queue
3. Consumer A processes the message and calls `DeleteMessage()` — message permanently removed. Done.
4. **But if Consumer A crashes, hangs, or is too slow** and the timer expires — the message becomes **visible again** and a different consumer picks it up. This is SQS's built-in retry mechanism.

**Key parameters:**

| Parameter | Default | Min | Max |
|-----------|---------|-----|-----|
| Visibility Timeout | 30 seconds | 0 seconds | 12 hours |

- Set at **queue level** (`SetQueueAttributes`) or **per-message** (`ChangeMessageVisibility`)
- Each `ReceiveMessage` call resets the timeout for that message
- The `ReceiptHandle` returned with each receive is tied to the current visibility window — stale handles from a previous receive won't work for deletion

**Why it exists:** SQS is a distributed system with at-least-once delivery. Without visibility timeout, two consumers polling simultaneously could receive the same message. The timeout creates a **temporary exclusive lock** so only one consumer processes each message at a time.

**Real-world incident (from Slack `#help-govcloud`):** The `seciamsvc` service went down, meaning messages were received but never deleted. After the visibility timeout expired, they kept re-appearing and eventually all landed in the DLQ after exceeding `maxReceiveCount` — escalated to a **Sev-1 incident**.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                        VISIBILITY TIMEOUT EXPLAINED                              │
│                                                                                  │
│  Timeline for a single message:                                                  │
│                                                                                  │
│  ──────────────────────────────────────────────────────────────────▶ time         │
│  │                                                                               │
│  │ t=0        t=0         t=30s (default)        t=30s+                          │
│  │  │          │               │                    │                             │
│  │  ▼          ▼               ▼                    ▼                             │
│  │ Sent     Received     Visibility            Message becomes                   │
│  │ to SQS   by Consumer  timeout expires       VISIBLE again                     │
│  │                                                                               │
│  │  ├────────INVISIBLE──────────┤─────VISIBLE (re-deliverable)───▶              │
│  │                                                                               │
│  │                                                                               │
│  │  SCENARIO 1: Consumer finishes in time                                        │
│  │  ┌────────────────────────────┐                                               │
│  │  │ t=0: Receive  t=10s: Delete│  ← Message deleted. Done. ✓                  │
│  │  └────────────────────────────┘                                               │
│  │                                                                               │
│  │  SCENARIO 2: Consumer too slow                                                │
│  │  ┌────────────────────────────┬─────────────────────────┐                     │
│  │  │ t=0: Receive               │ t=30s: Timeout!         │                     │
│  │  │      processing...         │ Another consumer gets it│                     │
│  │  │      still processing...   │ → DUPLICATE PROCESSING! │                     │
│  │  └────────────────────────────┴─────────────────────────┘                     │
│  │                                                                               │
│  │  SCENARIO 3: Consumer extends timeout                                         │
│  │  ┌───────────────────┬───────────────────────────────┐                        │
│  │  │ t=0: Receive      │ t=25s: ChangeMessageVisibility│                        │
│  │  │                   │        (extend by 30s more)   │                        │
│  │  │                   │ t=50s: Delete ✓               │                        │
│  │  └───────────────────┴───────────────────────────────┘                        │
│  │                                                                               │
│  │  SCENARIO 4: Consumer crashes                                                 │
│  │  ┌───────────────────┬───────────────────────────────┐                        │
│  │  │ t=0: Receive      │ t=5s: Consumer CRASHES        │                        │
│  │  │                   │ t=30s: Timeout expires         │                        │
│  │  │                   │ Message re-appears in queue    │                        │
│  │  │                   │ Another consumer picks it up   │                        │
│  │  └───────────────────┴───────────────────────────────┘                        │
│  │                                                                               │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### The Goldilocks Problem

```
Visibility Timeout TOO SHORT (e.g., 5s for a 30s job):
  → Message reappears while original consumer still processing
  → Two consumers process same message = DUPLICATES
  → maxReceiveCount increments falsely → premature DLQ

Visibility Timeout TOO LONG (e.g., 1 hour for a 30s job):
  → If consumer crashes at t=1s, message stuck invisible for 59 min 59 sec
  → Massive delay before retry
  → Queue appears empty but messages are stuck in-flight

BEST PRACTICE:
  visibility_timeout = 6 × average_processing_time
  Use ChangeMessageVisibility() for heartbeat pattern (extend while processing)
```

### The Heartbeat Pattern (Production Must-Have)

For long-running or unpredictable processing times, don't just set a huge visibility timeout. Instead, use a **heartbeat** — a background thread that periodically extends the visibility timeout while the main thread processes the message.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         HEARTBEAT PATTERN                                        │
│                                                                                  │
│  Consumer receives message (visibility timeout = 30s)                            │
│                                                                                  │
│  Main thread:     [─── processing ──────────────────────────── delete ─]         │
│                                                                                  │
│  Heartbeat thread: t=20s: extend +30s                                            │
│                    t=50s: extend +30s                                             │
│                    t=80s: extend +30s                                             │
│                    ...continues until main thread signals completion              │
│                                                                                  │
│  If consumer crashes → heartbeat stops → timeout expires → message retried ✓     │
│  If consumer is slow → heartbeat keeps extending → no premature re-delivery ✓    │
│                                                                                  │
│  PSEUDOCODE:                                                                     │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  msg = sqs.receive_message()                               │                  │
│  │                                                             │                  │
│  │  # Start heartbeat in background thread                     │                  │
│  │  heartbeat = every 20 seconds:                              │                  │
│  │      sqs.change_message_visibility(                         │                  │
│  │          receipt_handle=msg.receipt_handle,                  │                  │
│  │          visibility_timeout=30                               │                  │
│  │      )                                                      │                  │
│  │                                                             │                  │
│  │  try:                                                       │                  │
│  │      process(msg)              # could take 5s or 5 min     │                  │
│  │      sqs.delete_message(msg)   # success → remove from queue│                  │
│  │  finally:                                                   │                  │
│  │      heartbeat.stop()          # always stop heartbeat      │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Dead Letter Queues & Redrive

### DLQ Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                           DEAD LETTER QUEUE FLOW                                 │
│                                                                                  │
│                    maxReceiveCount = 3                                            │
│                                                                                  │
│  ┌──────────┐     ┌─────────────────────────────────────────────┐               │
│  │ Producer │────▶│              SOURCE QUEUE                    │               │
│  └──────────┘     │                                              │               │
│                   │  Attempt 1: Consumer receives, fails,       │               │
│                   │             doesn't delete                   │               │
│                   │             ApproximateReceiveCount = 1      │               │
│                   │                                              │               │
│                   │  Attempt 2: Another consumer receives,      │               │
│                   │             fails again                      │               │
│                   │             ApproximateReceiveCount = 2      │               │
│                   │                                              │               │
│                   │  Attempt 3: Third attempt, fails again      │               │
│                   │             ApproximateReceiveCount = 3      │               │
│                   │             ⚠ maxReceiveCount reached!       │               │
│                   │                                              │               │
│                   └──────────────────┬───────────────────────────┘               │
│                                      │                                           │
│                                      ▼                                           │
│                   ┌─────────────────────────────────────────────┐               │
│                   │           DEAD LETTER QUEUE                  │               │
│                   │                                              │               │
│                   │  Message lands here with ALL original        │               │
│                   │  attributes + metadata intact                │               │
│                   │                                              │               │
│                   │  ⚠ CRITICAL: Retention clock does NOT reset │               │
│                   │    If source queue retention = 4 days and    │               │
│                   │    message spent 3 days failing, you only    │               │
│                   │    have 1 day to investigate in DLQ!         │               │
│                   │                                              │               │
│                   │  Best Practice: Set DLQ retention to         │               │
│                   │  maximum 14 days                             │               │
│                   │                                              │               │
│                   └──────────────────┬───────────────────────────┘               │
│                                      │                                           │
│                          After fixing the bug...                                 │
│                                      │                                           │
│                                      ▼                                           │
│                   ┌─────────────────────────────────────────────┐               │
│                   │           DLQ REDRIVE                        │               │
│                   │                                              │               │
│                   │  Redrive moves messages back to source queue │               │
│                   │  - Start with small velocity (e.g., 10/sec) │               │
│                   │  - Max velocity: 500 messages/sec            │               │
│                   │  - New MessageId assigned                    │               │
│                   │  - New enqueue timestamp                     │               │
│                   │  - Retention period reset                    │               │
│                   └─────────────────────────────────────────────┘               │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### DLQ Anti-Patterns

```
ANTI-PATTERN 1: maxReceiveCount = 1
  Problem: Network glitch → message goes to DLQ on first failure
  Fix: Set maxReceiveCount ≥ 3 (AWS recommends 3-5)

ANTI-PATTERN 2: No CloudWatch alarm on DLQ
  Problem: Messages silently pile up in DLQ, nobody notices for days
  Fix: Alarm on ApproximateNumberOfMessagesVisible > 0

ANTI-PATTERN 3: Same retention period for source and DLQ
  Problem: Messages expire in DLQ before you can investigate
  Fix: DLQ retention = 14 days (maximum)

ANTI-PATTERN 4: DLQ on FIFO queue when order matters
  Problem: Messages out of order after redrive (msg5 arrives before msg3)
  Fix: Don't use DLQ for strictly ordered FIFO. Handle failures in-app.
```

### Real-World: maxReceiveCount Tuning in Production (From Internal Slack)

A production incident in the `#uds-livesite` channel (Jan 2026) perfectly illustrates why `maxReceiveCount` matters:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: FIFO Queue Blocked for 3.3 Hours by One Bad Message                   │
│  Source: Internal Slack #uds-livesite (Jan 17-20, 2026)                          │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  BEFORE (bad config):                                                            │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS FIFO queue settings:                                   │                  │
│  │    maxReceiveCount    = 10                                  │                  │
│  │    visibility_timeout = 20 minutes                          │                  │
│  │                                                             │                  │
│  │  One "corrupted" message (deserialization error) hits queue: │                  │
│  │    10 retries × 20 min timeout = 200 minutes (3.3 HOURS)   │                  │
│  │                                                             │                  │
│  │  ⚠ In FIFO, this blocks the ENTIRE message group!          │                  │
│  │    No other messages in that group can be processed         │                  │
│  │    until the poison message reaches DLQ.                    │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  AFTER (tuned config):                                                           │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  maxReceiveCount = 3  (reduced from 10)                     │                  │
│  │                                                             │                  │
│  │  Now: 3 retries × 20 min = 60 minutes to DLQ               │                  │
│  │  3.3x faster unblocking of the message group                │                  │
│  │                                                             │                  │
│  │  Next step: Reduce visibility_timeout to 5 min              │                  │
│  │  Then: 3 retries × 5 min = 15 minutes to DLQ (13x better)  │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  KEY FORMULA FOR FIFO QUEUES:                                                    │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │                                                             │                  │
│  │  max_block_time = maxReceiveCount × visibility_timeout      │                  │
│  │                                                             │                  │
│  │  This is how long ONE bad message can block an entire       │                  │
│  │  message group in a FIFO queue. Keep this number LOW.       │                  │
│  │                                                             │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  LESSONS FROM THE TEAM:                                                          │
│  1. "10 is too many retries, 3 should be good enough" — Sr. Engineer             │
│  2. "Change only ONE parameter at a time and observe PROD behavior"              │
│  3. "Get P90, P95 processing times before setting visibility timeout,            │
│      otherwise 5 min can be too aggressive and cause duplicate processing"       │
│  4. This was a GLOBAL property affecting ALL queues — shared SQS config          │
│     across multiple queues amplifies a bad setting                               │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Deduplication & Exactly-Once Processing

### How FIFO Deduplication Works

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   FIFO DEDUPLICATION — 5-MINUTE WINDOW                            │
│                                                                                  │
│  TWO DEDUPLICATION METHODS:                                                      │
│                                                                                  │
│  METHOD 1: Content-Based (hash of message body)                                  │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  ContentBasedDeduplication = true (queue-level setting)     │                  │
│  │                                                             │                  │
│  │  t=0s:  Send("process order 123")  → SHA-256 hash → ACCEPT │                  │
│  │  t=30s: Send("process order 123")  → Same hash   → REJECT  │ (within 5 min)  │
│  │  t=6m:  Send("process order 123")  → Same hash   → ACCEPT  │ (window passed) │
│  │                                                             │                  │
│  │  ⚠ Problem: Different body = different hash = no dedup!    │                  │
│  │    Send("{"orderId":123,"ts":1000}") → hash A              │                  │
│  │    Send("{"orderId":123,"ts":1001}") → hash B (different!) │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  METHOD 2: Explicit MessageDeduplicationId (recommended)                         │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Send(body="...", MessageDeduplicationId="order-123")       │                  │
│  │                                                             │                  │
│  │  t=0s:  DeduplicationId="order-123"  → ACCEPT               │                  │
│  │  t=30s: DeduplicationId="order-123"  → REJECT (duplicate)   │                  │
│  │  t=6m:  DeduplicationId="order-123"  → ACCEPT (new window)  │                  │
│  │                                                             │                  │
│  │  ✓ Dedup works regardless of body content                   │                  │
│  │  ✓ You control the dedup key (e.g., orderId, txnId)         │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ⚠ THE 5-MINUTE TRAP:                                                           │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Deduplication only works within a 5-minute window.         │                  │
│  │  If your producer retries after 5 minutes (e.g., during a   │                  │
│  │  deployment or outage), the duplicate WILL be accepted.     │                  │
│  │                                                             │                  │
│  │  → For true exactly-once, you STILL need application-level  │                  │
│  │    idempotency (idempotency key in DB, conditional writes). │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Standard Queue: DIY Deduplication

```
Standard queues have NO built-in dedup. You must handle it:

APPROACH 1: Idempotency Key in Database
  ┌──────────┐     ┌─────┐     ┌──────────┐     ┌─────────────┐
  │ Producer │────▶│ SQS │────▶│ Consumer │────▶│ Database     │
  └──────────┘     └─────┘     └──────────┘     │             │
                                    │            │ IF NOT      │
                                    │            │ EXISTS      │
                                    │            │ (idempotency│
                                    │            │  _key)      │
                                    │            │ THEN INSERT │
                                    │            │ ELSE SKIP   │
                                    │            └─────────────┘
                                    │
                                    ▼
                                 DeleteMessage()

APPROACH 2: Redis Dedup Cache
  Consumer checks: SETNX("processed:{messageId}", 1, EX=3600)
  If key exists → skip (already processed)
  If key set    → process message

APPROACH 3: Conditional Writes (DynamoDB)
  PutItem with ConditionExpression: "attribute_not_exists(messageId)"
  ConditionalCheckFailedException → already processed, skip
```

---

## 8. SQS Internals — How AWS Built It

Based on the AWS News Blog (2024) and re:Invent talks.

### Cell-Based Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                     SQS INTERNAL ARCHITECTURE                                    │
│                                                                                  │
│  CUSTOMER FRONT-END (Stateless fleet)                                            │
│  ┌──────────────────────────────────────────────────────────────┐               │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐       │               │
│  │  │ FE Host │  │ FE Host │  │ FE Host │  │ FE Host │  ...  │               │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘       │               │
│  │       │            │            │            │              │               │
│  │  Functions:                                                 │               │
│  │  1. Accept HTTPS requests (SendMessage, ReceiveMessage)    │               │
│  │  2. Authenticate via IAM / STS                              │               │
│  │  3. Authorize against resource policy                       │               │
│  │  4. Route to correct storage cell                           │               │
│  │  5. Rate limit per-account                                  │               │
│  └──────────────────────────┬───────────────────────────────────┘               │
│                              │                                                   │
│                     Binary Framing Protocol                                      │
│                     (multiplexed, checksummed)                                   │
│                              │                                                   │
│                              ▼                                                   │
│  STORAGE BACK-END (Cell-based, stateful)                                         │
│  ┌──────────────────────────────────────────────────────────────┐               │
│  │                                                               │               │
│  │  Cell = isolation boundary (blast radius containment)         │               │
│  │                                                               │               │
│  │  ┌─── Cell A ──────────────┐   ┌─── Cell B ────────────────┐ │               │
│  │  │                          │   │                            │ │               │
│  │  │  Queue "orders-prod"     │   │  Queue "payments-prod"    │ │               │
│  │  │  Queue "notifications"   │   │  Queue "analytics"        │ │               │
│  │  │  Queue "user-events"     │   │  Queue "audit-log"        │ │               │
│  │  │                          │   │                            │ │               │
│  │  │  Replicated across:      │   │  Replicated across:       │ │               │
│  │  │  AZ-a, AZ-b, AZ-c       │   │  AZ-a, AZ-b, AZ-c        │ │               │
│  │  │                          │   │                            │ │               │
│  │  └──────────────────────────┘   └────────────────────────────┘ │               │
│  │                                                               │               │
│  │  Each queue → assigned to a cell's cluster                    │               │
│  │  Each cluster → multiple hosts across AZs                     │               │
│  │  No queue spans multiple cells (isolation)                    │               │
│  │                                                               │               │
│  └──────────────────────────────────────────────────────────────┘               │
│                                                                                  │
│  WHY CELLS?                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐               │
│  │  Problem: A bug in SQS backend could take down ALL queues    │               │
│  │  Solution: Cells isolate blast radius                         │               │
│  │                                                               │               │
│  │  If Cell A has a bug:                                         │               │
│  │    - Only queues in Cell A affected                           │               │
│  │    - Cell B, C, D continue operating normally                 │               │
│  │    - Affected queues = small % of total fleet                 │               │
│  │    - AWS can canary deploy to one cell first                  │               │
│  └──────────────────────────────────────────────────────────────┘               │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### The 2024 Binary Framing Protocol Upgrade

```
BEFORE (Connection-per-request):
  ┌─────┐    conn 1    ┌─────────┐
  │ FE  │─────────────▶│ Storage │  1 TCP connection = 1 request
  │     │    conn 2    │         │  Overhead: TCP handshake per request
  │     │─────────────▶│         │  Problem: connection exhaustion at scale
  │     │    conn 3    │         │  Problem: head-of-line blocking
  │     │─────────────▶│         │
  └─────┘              └─────────┘

AFTER (Multiplexed binary framing):
  ┌─────┐   1 connection   ┌─────────┐
  │ FE  │═══════════════▶  │ Storage │  Multiple requests on 1 connection
  │     │  req1, req2,     │         │  128-bit IDs prevent crosstalk
  │     │  req3, req4...   │         │  Checksums detect corruption
  └─────┘                  └─────────┘

RESULTS (AWS published numbers):
  ┌─────────────────────────────────────────┐
  │  Metric                │  Improvement   │
  ├────────────────────────┼────────────────┤
  │  Average latency       │  -11%          │
  │  P90 latency           │  -17.4%        │
  │  Fleet capacity        │  +17.8%        │
  │  SNS→SQS delivery     │  -10%          │
  │  Requests processed    │  744.9 trillion│
  └─────────────────────────────────────────┘
```

---

## 9. Integration Patterns

### Pattern 1: SNS + SQS Fan-Out (Most Common)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                     SNS + SQS FAN-OUT PATTERN                                    │
│                                                                                  │
│                        ┌──────────────┐                                          │
│                        │  Order        │                                          │
│                        │  Service      │                                          │
│                        └──────┬───────┘                                          │
│                               │ Publish("OrderCreated")                          │
│                               ▼                                                  │
│                     ┌─────────────────┐                                          │
│                     │   SNS Topic:     │                                          │
│                     │   order-events   │                                          │
│                     └─────────────────┘                                          │
│                       │       │       │                                           │
│              ┌────────┘       │       └────────┐                                 │
│              ▼                ▼                ▼                                  │
│       ┌──────────┐    ┌──────────┐    ┌──────────┐                              │
│       │ SQS:     │    │ SQS:     │    │ SQS:     │                              │
│       │ inventory│    │ shipping │    │ notif    │                              │
│       └────┬─────┘    └────┬─────┘    └────┬─────┘                              │
│            ▼               ▼               ▼                                     │
│       ┌──────────┐    ┌──────────┐    ┌──────────┐                              │
│       │ Lambda:  │    │ ECS:     │    │ Lambda:  │                              │
│       │ update   │    │ create   │    │ send     │                              │
│       │ stock    │    │ shipment │    │ email    │                              │
│       └──────────┘    └──────────┘    └──────────┘                              │
│                                                                                  │
│  WHY THIS PATTERN:                                                               │
│  ✓ Publisher doesn't know about consumers (loose coupling)                       │
│  ✓ Adding a new consumer = just subscribe another SQS queue                      │
│  ✓ Failure in one consumer doesn't affect others (fault isolation)               │
│  ✓ Each consumer scales independently based on its queue depth                   │
│  ✓ SQS buffers messages if consumer is slow/down (resilience)                    │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Pattern 2: SQS + Lambda (Event Source Mapping)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                     SQS + LAMBDA EVENT SOURCE MAPPING                            │
│                                                                                  │
│  ┌──────────┐     ┌─────────────────┐     ┌──────────────────────┐              │
│  │ Producer │────▶│   SQS Queue     │────▶│   Lambda Function    │              │
│  └──────────┘     └─────────────────┘     └──────────────────────┘              │
│                          │                         │                             │
│                   AWS polls for you         Batch of 1-10 messages               │
│                   (long polling)            passed as event                      │
│                                                                                  │
│  HOW IT WORKS INTERNALLY:                                                        │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │  1. Lambda service runs a POLLER fleet (managed by AWS)        │              │
│  │  2. Pollers long-poll your SQS queue                           │              │
│  │  3. When messages arrive, poller invokes your Lambda           │              │
│  │  4. If Lambda succeeds → poller deletes messages from SQS     │              │
│  │  5. If Lambda fails → messages return to queue after timeout   │              │
│  │  6. Poller scales: 5 concurrent batches → up to 1000          │              │
│  └────────────────────────────────────────────────────────────────┘              │
│                                                                                  │
│  SCALING BEHAVIOR:                                                               │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │  Initial:  5 long-polling connections                          │              │
│  │  Scale up: +60 connections/min if queue has backlog            │              │
│  │  Max:      1000 concurrent Lambda invocations (default)        │              │
│  │                                                                │              │
│  │  For FIFO: Lambda respects MessageGroupId                      │              │
│  │            Only 1 Lambda per group at a time                   │              │
│  └────────────────────────────────────────────────────────────────┘              │
│                                                                                  │
│  PARTIAL BATCH FAILURE (critical to understand):                                 │
│  ┌────────────────────────────────────────────────────────────────┐              │
│  │  Batch of 10 messages → Lambda processes them:                 │              │
│  │    msg1 ✓  msg2 ✓  msg3 ✗  msg4 ✓  msg5 ✗  ...               │              │
│  │                                                                │              │
│  │  WITHOUT ReportBatchItemFailures:                              │              │
│  │    Entire batch retried (even msg1, msg4 that succeeded!)     │              │
│  │    → Requires idempotency for ALL messages                     │              │
│  │                                                                │              │
│  │  WITH ReportBatchItemFailures:                                 │              │
│  │    Return failed messageIds → only msg3, msg5 retried          │              │
│  │    → Much more efficient, fewer duplicate processes            │              │
│  └────────────────────────────────────────────────────────────────┘              │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Pattern 3: Request Buffering / Load Leveling

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                     LOAD LEVELING WITH SQS                                       │
│                                                                                  │
│  WITHOUT SQS (spiky traffic kills your DB):                                      │
│                                                                                  │
│  Traffic ▲                            DB Connections ▲                            │
│          │   ████                                    │  ████                      │
│          │  ██████                                   │ ██████ ← DB maxed out     │
│          │ ████████                                  │████████                    │
│          │██████████                                 │██████████                  │
│          └──────────▶ time                           └──────────▶ time            │
│                                                                                  │
│  WITH SQS (absorbs spikes, smooth processing):                                   │
│                                                                                  │
│  Traffic ▲           Queue Depth ▲           Processing ▲                        │
│          │   ████                │   ████                │                        │
│          │  ██████               │  ██████████           │ ═══════════════        │
│          │ ████████              │ █████████████         │ (constant rate)        │
│          │██████████             │██████████████████     │                        │
│          └──────────▶            └──────────────────▶    └───────────────▶        │
│                                  Queue absorbs spike    Workers drain at         │
│                                                         safe, steady rate         │
│                                                                                  │
│  REAL EXAMPLE: E-commerce checkout during flash sale                              │
│  - 50,000 orders/sec hit API                                                     │
│  - DB can handle 5,000 writes/sec                                                │
│  - SQS absorbs 45,000 msg/sec surplus                                            │
│  - Workers process at DB-safe rate                                                │
│  - No orders lost, no DB crashes                                                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Pattern 4: Delayed Processing / Scheduling

### What Is SQS Max Delay?

Max delay is the **maximum time you can postpone a message's delivery** to consumers after it's sent to the queue. When you send a message with `DelaySeconds`, it enters the queue but stays **invisible** — no consumer can see or receive it until the delay expires.

**Max delay = 900 seconds (15 minutes)** — this is a hard AWS limit.

```
Producer sends message at t=0 with DelaySeconds=900

  t=0          t=15 min
  │             │
  ▼             ▼
  Sent        Message becomes VISIBLE
  to SQS      (consumers can now receive it)

  ├── DELAYED (invisible) ──┤── VISIBLE (available) ──▶
```

**Two ways to set it:**

| Method | Scope | Example |
|--------|-------|---------|
| Queue-level | Every message in the queue | `CreateQueue(DelaySeconds=300)` — all messages delayed 5 min |
| Per-message | Only that specific message | `SendMessage(DelaySeconds=60)` — overrides queue-level setting |

**If you need delays longer than 15 minutes:**
- **EventBridge Scheduler** — supports hours, days, months (recommended by AWS)
- **Step Functions Wait state** — for workflow-based delays
- **Re-enqueue pattern** — consumer receives, checks timestamp, re-sends with another 15-min delay (hacky, not recommended)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                     DELAY QUEUE PATTERNS                                         │
│                                                                                  │
│  PATTERN A: Queue-Level Delay (all messages delayed)                             │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  CreateQueue(DelaySeconds=300)   → 5-minute delay for all  │                  │
│  │  Max: 900 seconds (15 minutes)                              │                  │
│  │  Use case: "Send confirmation email 5 min after signup"     │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  PATTERN B: Per-Message Delay (selective)                                        │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SendMessage(DelaySeconds=60, body="reminder")              │                  │
│  │  SendMessage(DelaySeconds=0, body="urgent alert")           │                  │
│  │  Each message has its own delay                             │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  PATTERN C: Multi-Stage Retry with Increasing Delays                             │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │                                                             │                  │
│  │  attempt=1  →  delay-queue-30s   →  process                │                  │
│  │  attempt=2  →  delay-queue-120s  →  process                │                  │
│  │  attempt=3  →  delay-queue-600s  →  process                │                  │
│  │  attempt=4  →  DLQ (give up)                               │                  │
│  │                                                             │                  │
│  │  ⚠ SQS max delay = 15 min. For longer delays:             │                  │
│  │    Use EventBridge Scheduler (supports hours/days/months)  │                  │
│  │    Use Step Functions Wait state                            │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Pattern 5: Priority Queue

```
SQS does NOT have native priority support. Implement with multiple queues:

┌──────────────────────────────────────────────────────────────────────────────────┐
│                     PRIORITY QUEUE PATTERN                                        │
│                                                                                  │
│  ┌──────────┐                                                                    │
│  │ Producer │─── priority: HIGH ──▶ ┌──────────────┐                            │
│  │          │                       │ SQS: high    │───┐                        │
│  │          │─── priority: MED  ──▶ ├──────────────┤   │                        │
│  │          │                       │ SQS: medium  │───┤    ┌──────────┐        │
│  │          │─── priority: LOW  ──▶ ├──────────────┤   ├──▶│ Consumer │        │
│  │          │                       │ SQS: low     │───┘    │ (polls   │        │
│  └──────────┘                       └──────────────┘        │  high    │        │
│                                                              │  first)  │        │
│  Consumer logic:                                             └──────────┘        │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  while true:                                                │                  │
│  │    msgs = poll(high_queue, WaitTimeSeconds=1)               │                  │
│  │    if msgs: process(msgs); continue                         │                  │
│  │                                                             │                  │
│  │    msgs = poll(medium_queue, WaitTimeSeconds=1)             │                  │
│  │    if msgs: process(msgs); continue                         │                  │
│  │                                                             │                  │
│  │    msgs = poll(low_queue, WaitTimeSeconds=20)               │                  │
│  │    if msgs: process(msgs)                                   │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  Alternative: Weighted polling (poll high 70%, medium 20%, low 10%)              │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Consumer Scaling & Backpressure

### Autoscaling Based on Queue Depth

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                CONSUMER AUTOSCALING WITH SQS                                     │
│                                                                                  │
│  KEY METRIC: Backlog Per Instance (NOT raw queue depth)                           │
│                                                                                  │
│  Formula:                                                                        │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │                                                             │                  │
│  │  backlog_per_instance = ApproximateNumberOfMessages          │                  │
│  │                         ────────────────────────────         │                  │
│  │                         number_of_running_instances          │                  │
│  │                                                             │                  │
│  │  acceptable_backlog = acceptable_latency                    │                  │
│  │                       ────────────────────                  │                  │
│  │                       avg_processing_time_per_msg           │                  │
│  │                                                             │                  │
│  │  target_value = acceptable_backlog                          │                  │
│  │                                                             │                  │
│  │  EXAMPLE:                                                   │                  │
│  │  - Acceptable latency: 30 seconds                           │                  │
│  │  - Avg processing time: 0.1 seconds/msg                     │                  │
│  │  - Acceptable backlog per instance = 30 / 0.1 = 300 msgs   │                  │
│  │  - If queue has 3000 msgs and 5 instances:                  │                  │
│  │    backlog_per_instance = 3000/5 = 600                      │                  │
│  │    600 > 300 → SCALE UP                                     │                  │
│  │    Need at least 3000/300 = 10 instances                    │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  SCALING DIAGRAM:                                                                │
│                                                                                  │
│  Queue Depth  ▲     5000                                                         │
│               │    ██                                                            │
│               │   ████                                                           │
│               │  ██████                                                          │
│               │ ████████                                                         │
│               └────────────▶ time                                                │
│                                                                                  │
│  Instances    ▲                                                                  │
│               │          ████████                                                │
│               │       ████████████                                               │
│               │    ████████████████                                              │
│               │ ██  (scale out)  ██ (scale in)                                   │
│               └────────────────────▶ time                                        │
│                                                                                  │
│  Backlog/Inst ▲     600 (above target)                                           │
│               │    ██                                                            │
│  target=300 ──│────────────────────── (target line)                              │
│               │                ████                                              │
│               │             ████████                                             │
│               └────────────────────▶ time                                        │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Backpressure Signals

```
┌────────────────────────────────────────────────────────────────────┐
│               SQS BACKPRESSURE SIGNALS — WHAT TO MONITOR           │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  METRIC                              │  ALARM WHEN                 │
│  ────────────────────────────────────┼───────────────────────────  │
│  ApproximateNumberOfMessages         │  > threshold (backlog)      │
│  ApproximateAgeOfOldestMessage       │  > acceptable_latency       │
│  ApproximateNumberOfMessagesNotVis   │  > in_flight_limit × 0.8   │
│  NumberOfMessagesSent                │  Sudden spike (abuse?)      │
│  NumberOfMessagesDeleted             │  Drop to 0 (consumer dead?) │
│  NumberOfEmptyReceives               │  High (switch to long poll) │
│                                                                    │
│  ⚠ THE SILENT KILLER:                                             │
│  ApproximateAgeOfOldestMessage is the BEST indicator of problems. │
│  It tells you: "the oldest unprocessed message has been waiting    │
│  for X seconds." If this grows, consumers can't keep up.          │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 11. SQS vs Kafka vs RabbitMQ

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                 SQS vs KAFKA vs RABBITMQ — DECISION MATRIX                       │
├───────────────────────┬───────────────┬───────────────┬──────────────────────────┤
│  Dimension            │  SQS          │  Kafka        │  RabbitMQ               │
├───────────────────────┼───────────────┼───────────────┼──────────────────────────┤
│  Model                │  Queue        │  Log/Stream   │  Broker (Queue+Pub/Sub) │
│  Message Retention    │  Consumed &   │  Retained for │  Consumed & deleted     │
│                       │  deleted      │  days/forever │                          │
│  Replay               │  ✗ No         │  ✓ Yes        │  ✗ No                   │
│  Throughput           │  100M msg/s   │  1M+ msg/s    │  ~100K msg/s            │
│  Latency              │  10-100ms     │  2-5ms        │  1-20ms                 │
│  Consumer Model       │  Pull (poll)  │  Pull (poll)  │  Push + Pull            │
│  Ordering             │  Per group    │  Per partition │  Per queue              │
│                       │  (FIFO only)  │               │                          │
│  Multi-Consumer       │  ✗ No (1:1)   │  ✓ Yes (CGs) │  ✗ No (use exchange)    │
│  Ops Overhead         │  Zero         │  High         │  Medium                 │
│  Scaling              │  Automatic    │  Manual       │  Manual                 │
│  Cost Model           │  Pay per msg  │  Pay per node │  Pay per node           │
│  Vendor Lock-in       │  Yes (AWS)    │  No (OSS)     │  No (OSS)              │
│  Max Message Size     │  256 KB       │  1 MB default │  128 MB (configurable) │
├───────────────────────┴───────────────┴───────────────┴──────────────────────────┤
│                                                                                  │
│  WHEN TO USE WHAT:                                                               │
│                                                                                  │
│  USE SQS WHEN:                     USE KAFKA WHEN:                               │
│  ✓ AWS-native serverless stack     ✓ Event replay/sourcing needed                │
│  ✓ Zero-ops is priority            ✓ Multiple consumers same events              │
│  ✓ Simple task queue / job queue   ✓ High-throughput streaming                   │
│  ✓ Decoupling microservices        ✓ Real-time analytics pipeline                │
│  ✓ Request buffering               ✓ Log aggregation at scale                    │
│  ✓ Team doesn't want infra mgmt   ✓ Team can manage Kafka cluster               │
│                                                                                  │
│  USE RABBITMQ WHEN:                                                              │
│  ✓ Complex routing (topic, headers, fanout exchanges)                            │
│  ✓ Need push-based delivery                                                      │
│  ✓ Request-reply pattern (RPC over queue)                                        │
│  ✓ Priority queues natively                                                      │
│  ✓ Plugin ecosystem (delayed messages, shovel, federation)                       │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### The Interview Answer for "SQS vs Kafka"

```
"Kafka and SQS solve fundamentally different problems:

Kafka is a DISTRIBUTED LOG — messages persist, multiple consumer groups 
can independently read the same stream, and you can replay from any offset.
It's for event streaming, event sourcing, and real-time analytics.

SQS is a TRANSIENT QUEUE — messages are consumed once and deleted. It's 
for decoupling services, job queues, and request buffering. Zero ops.

If you need replay → Kafka.
If you need multiple consumers on same event → Kafka.
If you need zero-ops + just decoupling → SQS.
If you're in AWS serverless → SQS (Lambda integration is excellent).
If you need both → SNS fan-out to SQS queues for different consumers,
                   or Kafka if you need replay semantics."
```

---

## 12. Real-World Usage at Scale

### Where SQS Runs in Production

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    SQS IN THE REAL WORLD                                         │
│                                                                                  │
│  AMAZON (internal):                                                              │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  - Order processing pipeline (decouples order → payment    │                  │
│  │    → inventory → shipping)                                  │                  │
│  │  - SQS was the FIRST AWS service (2004), built for Amazon's│                  │
│  │    own internal decoupling needs                            │                  │
│  │  - Processes 100M+ messages/sec at peak                     │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  CAPITAL ONE:                                                                    │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  - Real-time fraud detection pipeline                       │                  │
│  │  - Transaction events → SQS → ML scoring → alert/block     │                  │
│  │  - FIFO queues for ordered transaction processing           │                  │
│  │  - DLQ for transactions that fail fraud check               │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  NETFLIX:                                                                        │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  - Video encoding pipeline (upload → SQS → encode workers) │                  │
│  │  - Prioritized queues: premium content encoded first        │                  │
│  │  - Load leveling: absorbs 4K upload spikes                  │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  AIRBNB:                                                                         │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  - Search indexing pipeline                                 │                  │
│  │  - Listing updates → SQS → Elasticsearch indexer            │                  │
│  │  - Decouples write path from search index updates           │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  STRIPE:                                                                         │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  - Webhook delivery system                                  │                  │
│  │  - Payment events → SQS → webhook dispatcher                │                  │
│  │  - Retry with exponential backoff for failed deliveries     │                  │
│  │  - DLQ for webhooks that exhaust retries                    │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  COMMON PATTERNS ACROSS ALL:                                                     │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  1. Decouple synchronous → asynchronous (resilience)       │                  │
│  │  2. Buffer writes to protect databases (load leveling)     │                  │
│  │  3. Distribute work across worker fleets (fan-out)         │                  │
│  │  4. Retry failed operations with backoff (reliability)     │                  │
│  │  5. DLQ for operations that exhaust retries (observability)│                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 13. When SQS Failed — Production Incidents

### Incident 1: The 2023 Silent Backpressure Outage

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: SQS Backpressure Without Errors (2023)                                │
│  Source: AWS Post-Mortem, Medium Engineering Blogs                                │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  WHAT HAPPENED:                                                                  │
│  - E-commerce checkout flow using SQS between API and payment processor          │
│  - Queue depth grew to 47,000 messages                                           │
│  - Average message age hit 4 minutes                                             │
│  - SLA target: 500ms end-to-end                                                  │
│                                                                                  │
│  THE SILENT PART:                                                                │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  ✓ No errors in CloudWatch                                  │                  │
│  │  ✓ No timeouts                                              │                  │
│  │  ✓ No 5xx responses                                         │                  │
│  │  ✓ All health checks passing                                │                  │
│  │  ✗ Messages just... slow                                    │                  │
│  │  ✗ Customers waiting 4+ minutes for payment confirmation   │                  │
│  │  ✗ Alarms only triggered on errors, not latency            │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ROOT CAUSE:                                                                     │
│  - Traffic spike from marketing campaign                                         │
│  - Consumer fleet didn't scale fast enough                                       │
│  - Autoscaling based on CPU, not queue depth                                     │
│  - Queue absorbed the spike silently (by design!)                                │
│                                                                                  │
│  LESSON:                                                                         │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  1. ALWAYS alarm on ApproximateAgeOfOldestMessage           │                  │
│  │  2. Scale consumers based on queue depth, not CPU           │                  │
│  │  3. SQS "working correctly" can still mean "system broken" │                  │
│  │  4. Backpressure without errors is the hardest failure mode│                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Incident 2: Visibility Timeout Cascade

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Visibility Timeout → Duplicate Processing → Data Corruption           │
│  Source: Multiple engineering blog post-mortems                                   │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  TIMELINE:                                                                       │
│                                                                                  │
│  t=0s:    Consumer A receives "transfer $500 from Alice to Bob"                 │
│  t=0-25s: Consumer A processing (calling bank API, which is slow)               │
│  t=30s:   Visibility timeout expires! Message becomes visible again             │
│  t=31s:   Consumer B receives same message                                       │
│  t=32s:   Consumer B processes: transfer $500 Alice → Bob (DUPLICATE!)          │
│  t=45s:   Consumer A finishes: transfer $500 Alice → Bob (first one)            │
│  t=46s:   Consumer A calls DeleteMessage... but Consumer B's ReceiptHandle      │
│           is now active. Consumer A's delete may fail or succeed (race).         │
│                                                                                  │
│  RESULT: Alice lost $1000 instead of $500                                        │
│                                                                                  │
│  FIXES:                                                                          │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  1. Set visibility timeout = 6× processing time            │                  │
│  │  2. Use heartbeat pattern: extend visibility while working │                  │
│  │  3. ALWAYS implement idempotency for financial operations  │                  │
│  │     (idempotency key = transaction_id in DB)               │                  │
│  │  4. Use FIFO queue with dedup for financial transactions   │                  │
│  │  5. Check-and-set: UPDATE ... WHERE status='pending'       │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Incident 3: The October 2025 AWS us-east-1 Outage

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: 14-Hour us-east-1 Regional Outage (October 2025)                      │
│  Source: AWS Post-Mortem, Offbeat Engineer                                        │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  WHAT HAPPENED:                                                                  │
│  - Race condition in DynamoDB's DNS management (TOCTOU bug)                      │
│  - Three DNS Enactor processes applied stale plans concurrently                  │
│  - All IP addresses removed from DNS records                                     │
│  - 100+ AWS services affected including SQS                                      │
│  - Slack, Snapchat, Fortnite, Coinbase, Disney+ down                            │
│  - 14 hours to full recovery                                                     │
│                                                                                  │
│  IMPACT ON SQS:                                                                  │
│  - SQS in us-east-1 completely unavailable                                       │
│  - All SendMessage / ReceiveMessage calls failed                                 │
│  - Messages already in queue were safe (persisted across AZs)                    │
│  - But no NEW messages could enter or leave                                      │
│                                                                                  │
│  LESSON FOR YOUR ARCHITECTURE:                                                   │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS is regional, NOT global.                               │                  │
│  │                                                             │                  │
│  │  If you're in ONE region, you're one outage away from zero. │                  │
│  │                                                             │                  │
│  │  Mitigation strategies:                                     │                  │
│  │  1. Multi-region active-active (complex, expensive)         │                  │
│  │  2. Circuit breaker: fall back to sync processing           │                  │
│  │  3. Client-side retry with local persistence                │                  │
│  │  4. Cross-region queue replication (EventBridge Global EP)  │                  │
│  │  5. Accept the risk: us-east-1 outage = ~once/year          │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Incident 4: FIFO Throttling Under Load

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: FIFO Queue Throttling During Peak Traffic                              │
│  Source: AWS Developer Blog, Stack Overflow reports                               │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  SCENARIO:                                                                       │
│  - Payment processing on FIFO queue (for exactly-once guarantee)                 │
│  - Normal traffic: 200 TPS → well within 300 TPS limit                          │
│  - Black Friday: traffic spikes to 2,000 TPS                                     │
│  - ThrottlingException on 85% of SendMessage calls!                              │
│                                                                                  │
│  WHY:                                                                            │
│  - FIFO default: 300 TPS per API action                                          │
│  - Single MessageGroupId = serialized to 300 TPS hard limit                      │
│  - Even with batching: 3,000 messages/sec max (10 per batch × 300 TPS)          │
│                                                                                  │
│  FIX:                                                                            │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  1. Enable high-throughput mode (up to 70,000 msg/sec)     │                  │
│  │  2. Use MANY MessageGroupIds (e.g., per-merchant, per-user)│                  │
│  │  3. Use batching (SendMessageBatch of 10)                  │                  │
│  │  4. If ordering not critical → switch to Standard queue    │                  │
│  │  5. If ordering only per-entity → use entity ID as GroupId │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 14. When SQS Cannot Cope — Limitations

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    WHEN NOT TO USE SQS                                            │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ✗ EVENT REPLAY / EVENT SOURCING                                                 │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS deletes messages after consumption. No replay.         │                  │
│  │  If you need to re-read events from 3 hours ago → Kafka.    │                  │
│  │  If you need event sourcing (rebuild state from events)     │                  │
│  │  → Kafka or EventStore, not SQS.                            │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ MULTIPLE CONSUMERS ON SAME MESSAGE                                            │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS = 1 message → 1 consumer. Once processed, it's gone. │                  │
│  │  For fan-out (same event to N services), use SNS → SQS.    │                  │
│  │  For true multi-consumer replay → Kafka consumer groups.    │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ SUB-MILLISECOND LATENCY                                                       │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS latency = 10-100ms (network round trip + polling).    │                  │
│  │  For <5ms latency → Kafka, Redis Streams, or in-memory     │                  │
│  │  queues.                                                    │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ GLOBAL STRICT ORDERING AT HIGH THROUGHPUT                                     │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  FIFO = strict order BUT maxes at 300-70K TPS.              │                  │
│  │  Standard = high throughput BUT no ordering guarantee.       │                  │
│  │  You can't have both. Kafka partitions are a better fit     │                  │
│  │  for ordered high-throughput scenarios.                      │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ LONG-TERM MESSAGE STORAGE                                                     │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Max retention = 14 days. Messages auto-expire.             │                  │
│  │  For long-term event storage → Kafka (infinite retention)   │                  │
│  │  or write to S3/DynamoDB.                                   │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ LARGE MESSAGES (>256 KB)                                                      │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Max message size = 256 KB (up to 1 MB with new limits).   │                  │
│  │  For large payloads: store in S3, put S3 pointer in SQS.   │                  │
│  │  AWS provides Extended Client Library for this pattern.     │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ COMPLEX ROUTING                                                               │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS has no routing logic. 1 queue = 1 destination.         │                  │
│  │  For topic-based, header-based, or wildcard routing         │                  │
│  │  → RabbitMQ exchanges or SNS message filtering.             │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ REQUEST-REPLY PATTERN                                                         │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS is fire-and-forget. No built-in correlation/reply.     │                  │
│  │  Implementing RPC over SQS is painful (temp reply queues).  │                  │
│  │  → Use RabbitMQ's reply-to pattern or gRPC for RPC.         │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ✗ MULTI-REGION BY DEFAULT                                                       │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  SQS is regional. No built-in cross-region replication.     │                  │
│  │  For global event distribution → EventBridge Global         │                  │
│  │  Endpoints, or Kafka MirrorMaker.                           │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 15. Anti-Patterns That Kill SQS Systems

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                 ANTI-PATTERNS — DON'T DO THESE                                   │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ❌ 1. NOT IMPLEMENTING IDEMPOTENCY                                              │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Standard queues deliver at-least-once. FIFO dedup window  │                  │
│  │  is only 5 minutes. Your consumer WILL see duplicates.     │                  │
│  │                                                             │                  │
│  │  Fix: Idempotency key in DB for every consumer.             │                  │
│  │       INSERT ... ON CONFLICT DO NOTHING                     │                  │
│  │       or DynamoDB conditional PutItem                       │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 2. USING SHORT POLLING                                                       │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  ~70% of short poll calls return empty. You pay for each.  │                  │
│  │  At 10 polls/sec × $0.40/million = wasted money + latency. │                  │
│  │                                                             │                  │
│  │  Fix: WaitTimeSeconds=20 (long polling). Always.            │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 3. SINGLE MessageGroupId IN FIFO                                             │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  One group ID = serialized processing = 300 TPS max.       │                  │
│  │  All parallelism gone. Might as well be single-threaded.   │                  │
│  │                                                             │                  │
│  │  Fix: Use entity ID as MessageGroupId (userId, orderId).   │                  │
│  │       Ordering within entity, parallelism across entities.  │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 4. IGNORING THE ReceiptHandle                                                │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  ReceiptHandle changes on each receive. Storing old ones   │                  │
│  │  and trying to delete later = message never deleted =       │                  │
│  │  infinite reprocessing loop.                                │                  │
│  │                                                             │                  │
│  │  Fix: Delete immediately after successful processing.       │                  │
│  │       Never cache ReceiptHandles across poll cycles.        │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 5. PROCESSING MESSAGE BEFORE DELETING (WRONG ORDER)                          │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  WRONG:  receive → delete → process                        │                  │
│  │          (if process fails, message is already gone!)       │                  │
│  │                                                             │                  │
│  │  RIGHT:  receive → process → delete                        │                  │
│  │          (if process fails, message retries automatically) │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 6. NOT MONITORING DLQ                                                        │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Messages silently accumulate in DLQ for days.              │                  │
│  │  Nobody notices. Data loss.                                 │                  │
│  │                                                             │                  │
│  │  Fix: CloudWatch alarm on DLQ message count > 0.            │                  │
│  │       PagerDuty integration for critical queues.            │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 7. PUTTING TOO MUCH LOGIC IN THE MESSAGE                                     │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Stuffing 200 KB JSON blobs into 256 KB messages.           │                  │
│  │  Encoding business logic into message format.               │                  │
│  │                                                             │                  │
│  │  Fix: Message = pointer + minimal metadata.                 │                  │
│  │       Store payload in S3/DynamoDB. Message carries the ID. │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 8. NOT HANDLING POISON MESSAGES                                               │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  A malformed message that always fails processing will      │                  │
│  │  loop forever: receive → fail → reappear → receive → fail. │                  │
│  │  Without DLQ, it blocks other messages.                     │                  │
│  │                                                             │                  │
│  │  Fix: DLQ with maxReceiveCount=3-5.                         │                  │
│  │       Application-level try/catch that logs and deletes     │                  │
│  │       permanently broken messages.                          │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 9. AUTOSCALING ON CPU INSTEAD OF QUEUE DEPTH                                 │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Workers waiting on I/O (HTTP calls, DB queries) have low  │                  │
│  │  CPU even when overwhelmed. Autoscaling never triggers.     │                  │
│  │                                                             │                  │
│  │  Fix: Custom metric = backlog_per_instance.                 │                  │
│  │       Target tracking policy on queue depth.                │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ❌ 10. USING SQS AS A DATABASE                                                  │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  Storing messages in SQS and reading them repeatedly.       │                  │
│  │  SQS is not a database. Messages expire. No queries.        │                  │
│  │                                                             │                  │
│  │  Fix: SQS for transit. DB for storage. Don't mix them.     │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Performance Tuning Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    SQS PERFORMANCE TUNING CHEAT SHEET                            │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  THROUGHPUT                                                                      │
│  ├── Use SendMessageBatch (10 msgs/batch)              → 10x fewer API calls    │
│  ├── Use ReceiveMessage MaxNumberOfMessages=10          → 10x fewer polls        │
│  ├── Use DeleteMessageBatch                             → 10x fewer deletes      │
│  ├── Multiple concurrent consumers (horizontal scaling) → Linear throughput gain │
│  └── FIFO: maximize unique MessageGroupIds              → Parallel processing    │
│                                                                                  │
│  LATENCY                                                                         │
│  ├── Long polling (WaitTimeSeconds=20)                  → Fewer empty responses  │
│  ├── Reduce visibility timeout for fast operations      → Faster retries         │
│  ├── Place consumers in same region as queue             → No cross-region RTT   │
│  └── Use Lambda event source (AWS polls optimally)       → AWS-optimized polling │
│                                                                                  │
│  COST                                                                            │
│  ├── Long polling                     → ~70% fewer empty requests                │
│  ├── Batching (10 msgs = 1 request)   → ~90% cost reduction                     │
│  ├── DLQ instead of infinite retries  → Stop paying for broken messages          │
│  ├── Standard over FIFO when possible → $0.40 vs $0.50 per million              │
│  └── Request = any API call ≤ 64 KB. 256 KB msg = 4 requests billed!            │
│                                                                                  │
│  RELIABILITY                                                                     │
│  ├── Visibility timeout = 6× avg processing time                                │
│  ├── DLQ with maxReceiveCount = 3-5                                              │
│  ├── DLQ retention = 14 days (max)                                               │
│  ├── CloudWatch alarm on ApproximateAgeOfOldestMessage                           │
│  ├── Idempotency at consumer level (always!)                                     │
│  └── Heartbeat pattern for long-running processing                               │
│                                                                                  │
│  SECURITY                                                                        │
│  ├── Server-side encryption (SSE-SQS or SSE-KMS)                                │
│  ├── Queue policies (resource-based IAM)                                         │
│  ├── VPC endpoints (PrivateLink) — no traffic over public internet               │
│  ├── Encrypt message body at application level for sensitive data                │
│  └── Least-privilege IAM roles per producer/consumer                             │
│                                                                                  │
│  BILLING GOTCHA:                                                                 │
│  ┌────────────────────────────────────────────────────────────┐                  │
│  │  1 request = first 64 KB of a message.                     │                  │
│  │  A 256 KB message = 4 requests billed.                     │                  │
│  │  A batch of 10 × 256 KB messages = 40 requests billed!     │                  │
│  │                                                             │                  │
│  │  Keep messages small. Use S3 for large payloads.            │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 17. Interview Questions — Medium

### Q1: Explain the difference between Standard and FIFO queues. When would you choose each?

**Expected Answer:**
- Standard: unlimited throughput, at-least-once delivery, best-effort ordering. Choose for high-throughput workloads where occasional duplicates are acceptable (log processing, metrics, non-critical notifications).
- FIFO: 300-70K TPS, exactly-once within 5-min dedup window, strict per-group ordering. Choose when ordering matters (financial transactions, command sequences) or when duplicates are unacceptable.
- Trap: FIFO ordering is per-MessageGroupId, not global. Many candidates miss this.

### Q2: How would you handle a scenario where your SQS consumer is processing messages slower than producers are sending them?

**Expected Answer:**
```
1. Monitor: Alarm on ApproximateAgeOfOldestMessage and queue depth
2. Scale consumers: Autoscale on backlog_per_instance, NOT CPU
   - backlog_per_instance = total_messages / num_consumers
   - target = acceptable_latency / avg_processing_time
3. Optimize consumer:
   - Batch processing (receive 10 messages at once)
   - Parallel processing within consumer
   - Reduce visibility timeout if overestimated
4. Rate limit producer if queue depth exceeds threshold (backpressure)
5. Shed load: route low-priority messages to separate queue
```

### Q3: What happens when a consumer receives a message but crashes before deleting it?

**Expected Answer:**
- Message becomes invisible for the duration of the visibility timeout
- After timeout expires, message becomes visible again in the queue
- Another consumer picks it up (retry semantics)
- ApproximateReceiveCount increments
- If maxReceiveCount is set and exceeded → message moves to DLQ
- Key insight: this is why consumers MUST be idempotent — same message will be processed by a different consumer

### Q4: Design a retry mechanism with exponential backoff using SQS.

**Expected Answer:**
```
Architecture:
  ┌──────────┐     ┌─────────────┐     ┌──────────┐
  │ Producer │────▶│ Main Queue  │────▶│ Consumer │
  └──────────┘     └─────────────┘     └──────┬───┘
                                              │ fail
                                              ▼
                        ┌─────────────────────────────────────┐
                        │ Check attempt count from attribute   │
                        │                                      │
                        │ attempt=1 → delay-30s-queue          │
                        │ attempt=2 → delay-120s-queue         │
                        │ attempt=3 → delay-600s-queue         │
                        │ attempt=4 → DLQ (give up)            │
                        └─────────────────────────────────────┘

Each delay queue has DelaySeconds set appropriately.
After delay, message goes back to main queue for reprocessing.
Attempt count stored as MessageAttribute, incremented each time.

Alternative: Single queue + ChangeMessageVisibility to increase
timeout on each retry (simpler but less flexible).
```

### Q5: How does SQS achieve high availability? What happens during an AZ failure?

**Expected Answer:**
- Messages stored redundantly across multiple AZs within a region
- If one AZ fails, messages are still available from other AZs
- SQS front-end fleet is distributed across AZs
- Cell-based architecture limits blast radius
- Caveat: SQS is regional. A full region outage (like us-east-1 Oct 2025) takes SQS down entirely
- Mitigation: multi-region active-active with EventBridge Global Endpoints, or accept regional risk

### Q6: Explain the claim-check pattern with SQS.

**Expected Answer:**
```
Problem: SQS max message size = 256 KB. You have 5 MB payloads.

Solution: Claim-Check Pattern
  ┌──────────┐   1. Store payload in S3    ┌──────┐
  │ Producer │─────────────────────────▶  │  S3  │
  └────┬─────┘                             └──────┘
       │                                      │
       │ 2. Send S3 pointer to SQS            │
       ▼                                      │
  ┌─────────┐   3. Consumer reads pointer  ┌──────────┐
  │   SQS   │──────────────────────────▶  │ Consumer │
  └─────────┘                              └────┬─────┘
                                                │
                    4. Fetch payload from S3     │
                    ┌──────┐◀───────────────────┘
                    │  S3  │
                    └──────┘

AWS provides: Amazon SQS Extended Client Library (Java, Python)
- Automatically stores large payloads in S3
- Sends S3 reference as SQS message
- Consumer auto-fetches from S3 transparently
```

### Q7: How would you implement a priority queue using SQS?

**Expected Answer:**
- SQS has no native priority support
- Use multiple queues: high-priority, medium-priority, low-priority
- Consumer polls high queue first, then medium, then low
- Alternative: weighted random polling (70% high, 20% medium, 10% low)
- For Lambda: use separate Lambda triggers per queue with different reserved concurrency
- Trade-off: more queues = more complexity, but true priority semantics

### Q8: What is the significance of the ReceiptHandle in SQS?

**Expected Answer:**
- ReceiptHandle is a temporary token returned with each ReceiveMessage call
- Required for DeleteMessage and ChangeMessageVisibility
- Changes every time a message is received (even if same message re-delivered)
- Acts as a proof-of-receipt: only the consumer who received the message can delete it
- Has a limited lifetime tied to the visibility timeout
- Anti-pattern: caching ReceiptHandles across polling cycles — they become stale

### Q9: How does SQS integrate with Lambda? What are the scaling semantics?

**Expected Answer:**
- Lambda creates an event source mapping that polls SQS on your behalf
- Starts with 5 concurrent polling connections
- Scales up by 60 connections/minute while backlog exists
- Max 1000 concurrent Lambda invocations (default, can be raised)
- For FIFO: respects MessageGroupId — only 1 Lambda per group at a time
- Partial batch failure: use ReportBatchItemFailures to retry only failed messages
- Without ReportBatchItemFailures: entire batch retries on any single failure

### Q10: How would you estimate the cost of SQS for 1 billion messages per month?

**Expected Answer:**
```
Assumptions:
- Average message size: 4 KB (1 request each)
- Using long polling (WaitTimeSeconds=20)
- Batching: 10 messages per SendMessageBatch and DeleteMessageBatch

Requests:
  Send:     1B / 10 per batch = 100M send requests
  Receive:  ~120M receive requests (some empty, long polling reduces)
  Delete:   1B / 10 per batch = 100M delete requests
  Total:    ~320M requests

Cost (Standard queue):
  320M × $0.40 / 1M = $128/month

  First 1M requests/month are free → negligible savings at this scale

Compare to Kafka:
  3 brokers × m5.xlarge = ~$450/month + EBS storage + ops time
  SQS wins on cost until ~10B+ messages/month
```

---

## 18. Interview Questions — Hard

### Q1: Design an exactly-once processing pipeline using SQS, where FIFO queue's 5-minute deduplication window is insufficient.

**Expected Answer:**
```
Problem: FIFO dedup window = 5 minutes. If producer retries after 5+ min
(e.g., during a deployment), SQS accepts the duplicate.

Solution: Application-level idempotency + SQS FIFO

  ┌──────────┐     ┌────────────┐     ┌──────────┐     ┌─────────────┐
  │ Producer │────▶│ FIFO Queue │────▶│ Consumer │────▶│  Database    │
  └──────────┘     └────────────┘     └──────────┘     └─────────────┘
                                           │
                                           ▼
                                    ┌─────────────────────────┐
                                    │ BEGIN TRANSACTION        │
                                    │                          │
                                    │ SELECT id FROM processed │
                                    │ WHERE idempotency_key =  │
                                    │       :msg_dedup_id      │
                                    │                          │
                                    │ IF EXISTS → skip         │
                                    │                          │
                                    │ ELSE:                    │
                                    │   INSERT INTO processed  │
                                    │   DO business logic      │
                                    │                          │
                                    │ COMMIT                   │
                                    └─────────────────────────┘

Layer 1: FIFO dedup handles retries within 5 minutes (SQS-level)
Layer 2: DB idempotency key handles retries beyond 5 minutes (app-level)
Layer 3: Transactional outbox if you need to publish downstream events

Trade-off: Extra DB lookup per message, but guarantees exactly-once
even across deployments, outages, and extended retries.
```

### Q2: Your SQS-based system processes 50,000 msg/sec normally. During Black Friday, it spikes to 500,000 msg/sec for 2 hours. Design the system to handle this without message loss.

**Expected Answer:**
```
Architecture:
                    Normal: 50K/sec
                    Spike:  500K/sec (10x for 2 hours)

  ┌──────────┐     ┌──────────────────────────────┐     ┌──────────────┐
  │ API      │────▶│ SQS Standard Queue            │────▶│ Worker Fleet │
  │ Gateway  │     │ (unlimited throughput)         │     │ (Auto Scaled)│
  └──────────┘     │                                │     └──────────────┘
                   │ Absorbs 500K/sec instantly     │            │
                   │ No provisioning needed          │            │
                   └──────────────────────────────┘            │
                                                                │
  Autoscaling:                                                  │
  ┌─────────────────────────────────────────────────────────┐  │
  │ Metric: backlog_per_instance                             │  │
  │ Target: 300 msgs/instance (= 30s latency at 10ms/msg)  │  │
  │                                                          │  │
  │ Normal:  50K / 300 = ~167 instances                      │  │
  │ Spike:   500K / 300 = ~1,667 instances → scale to 1,700 │  │
  │                                                          │  │
  │ Scaling speed: EC2 takes 3-5 min to launch               │  │
  │ During scale-up lag: queue absorbs backlog (SQS strength)│  │
  │                                                          │  │
  │ Pre-warming strategy:                                     │  │
  │ - Schedule ASG scale-up 30 min before Black Friday       │  │
  │ - Warm pool of 500 pre-initialized instances             │  │
  │ - Step scaling: aggressive scale-up, gentle scale-down   │  │
  └─────────────────────────────────────────────────────────┘  │

  Database protection:
  ┌─────────────────────────────────────────────────────────┐
  │ Workers write to DB at controlled rate (connection pool) │
  │ If DB saturated → worker slows down → queue depth grows  │
  │ → autoscaling adds workers → new workers get connections │
  │ → eventually consistent drain at safe DB rate            │
  │                                                          │
  │ DB write rate capped at: max_connections × writes/sec    │
  │ Overflow absorbed by SQS (load leveling pattern)         │
  └─────────────────────────────────────────────────────────┘
```

### Q3: You discover that your SQS consumers are processing the same message 3-4 times. Root cause and fix?

**Expected Answer:**
```
Diagnosis tree:

  Message processed multiple times
  ├── Is it Standard queue? → At-least-once delivery (expected)
  │   └── Fix: Implement idempotency (DB dedup key)
  │
  ├── Is visibility timeout too short?
  │   ├── Processing time > visibility timeout?
  │   │   └── Fix: Increase timeout to 6× avg processing time
  │   └── Consumer doing long I/O (HTTP calls, DB writes)?
  │       └── Fix: Heartbeat pattern (ChangeMessageVisibility)
  │
  ├── Is consumer failing to delete after processing?
  │   ├── Delete call after process fails silently?
  │   │   └── Fix: Retry delete with exponential backoff
  │   └── Using stale ReceiptHandle?
  │       └── Fix: Delete with handle from most recent receive
  │
  ├── Multiple consumers competing without idempotency?
  │   └── Fix: Consumer-level idempotency + FIFO if ordering needed
  │
  └── Lambda partial batch failure without ReportBatchItemFailures?
      └── Fix: Enable FunctionResponseTypes: [ReportBatchItemFailures]
          Return only failed messageIds

Investigation commands:
  1. Check ApproximateReceiveCount on messages in DLQ
     (If >>3, visibility timeout is too short)
  2. Check ApproximateAgeOfOldestMessage 
     (If growing, consumers too slow)
  3. Check NumberOfMessagesReceived vs NumberOfMessagesDeleted
     (Delta = messages being reprocessed)
```

### Q4: Design a multi-region active-active system using SQS where both regions process messages and handle failover.

**Expected Answer:**
```
Architecture:

  Region: us-east-1                    Region: us-west-2
  ┌────────────────────┐              ┌────────────────────┐
  │ API Gateway        │              │ API Gateway        │
  │ (Route 53 latency) │              │ (Route 53 latency) │
  └────────┬───────────┘              └────────┬───────────┘
           │                                    │
           ▼                                    ▼
  ┌────────────────────┐              ┌────────────────────┐
  │ SQS Queue (east)   │              │ SQS Queue (west)   │
  └────────┬───────────┘              └────────┬───────────┘
           │                                    │
           ▼                                    ▼
  ┌────────────────────┐              ┌────────────────────┐
  │ Workers (east)     │              │ Workers (west)     │
  └────────┬───────────┘              └────────┬───────────┘
           │                                    │
           ▼                                    ▼
  ┌────────────────────┐              ┌────────────────────┐
  │ DynamoDB Global    │◀────────────▶│ DynamoDB Global    │
  │ Table (east)       │  replication │ Table (west)       │
  └────────────────────┘              └────────────────────┘

  Cross-region sync options:
  ┌─────────────────────────────────────────────────────────┐
  │ Option A: EventBridge Global Endpoints                   │
  │   - Route events to secondary region automatically       │
  │   - Built-in health checks and failover                  │
  │   - Recommended by AWS for cross-region event routing    │
  │                                                          │
  │ Option B: Lambda replicator                              │
  │   - Lambda in east reads SQS, writes to west SQS         │
  │   - Simple but adds latency and cost                     │
  │   - Need dedup in both regions (DynamoDB conditional)    │
  │                                                          │
  │ Option C: Produce to both regions from client            │
  │   - Client sends to both east and west SQS               │
  │   - Idempotency key ensures no double-processing         │
  │   - Simplest but requires client change                  │
  └─────────────────────────────────────────────────────────┘

  Failover:
  - Route 53 health check on regional API endpoint
  - If us-east-1 down → all traffic to us-west-2
  - Messages already in us-east-1 SQS are safe (persisted)
  - They'll be processed when region recovers
  - No message loss, possible delay for in-flight messages

  Idempotency (critical for multi-region):
  - DynamoDB Global Table with conditional writes
  - PutItem WHERE attribute_not_exists(idempotency_key)
  - Handles duplicate processing across regions
```

### Q5: Compare the internal architecture of SQS with Kafka. Why can SQS auto-scale but Kafka requires partition management?

**Expected Answer:**
```
SQS Architecture:
  ┌──────────────────────────────────────────────────┐
  │ Fully managed, cell-based storage backend         │
  │                                                    │
  │ Queue = logical abstraction over distributed cells │
  │ No concept of "partitions" exposed to user         │
  │ AWS internally shards and rebalances               │
  │                                                    │
  │ Scaling is transparent:                            │
  │ - More messages → AWS adds hosts to cell           │
  │ - No user action needed                            │
  │ - No rebalancing visible to consumers              │
  └──────────────────────────────────────────────────┘

Kafka Architecture:
  ┌──────────────────────────────────────────────────┐
  │ User-managed, partition-based storage              │
  │                                                    │
  │ Topic = N partitions, each on specific broker     │
  │ Partition count set at creation time               │
  │ Consumers = 1 per partition per consumer group     │
  │                                                    │
  │ Scaling requires manual action:                    │
  │ - More throughput → add partitions (can't shrink!) │
  │ - More consumers → need more partitions first      │
  │ - Rebalancing causes consumer lag                  │
  │ - Adding brokers requires partition reassignment    │
  └──────────────────────────────────────────────────┘

Why the difference:
  SQS made a DESIGN TRADE-OFF:
    ✓ Auto-scales (no partitions to manage)
    ✓ Unlimited consumers
    ✗ No replay (messages deleted after processing)
    ✗ No ordering guarantee in Standard (no partitions = no order)
    ✗ Limited throughput in FIFO (ordering constraints)

  Kafka made the OPPOSITE trade-off:
    ✓ Replay (log retention)
    ✓ Strict partition ordering
    ✓ Multiple consumer groups
    ✗ Manual partition management
    ✗ Consumer count ≤ partition count per group
    ✗ Rebalancing disruptions
```

### Q6: You're building an order processing system. An order goes through states: CREATED → PAID → SHIPPED → DELIVERED. How do you use SQS to ensure state transitions happen in order and exactly once?

**Expected Answer:**
```
Architecture: State Machine + FIFO Queue + Idempotent Consumer

  ┌──────────┐     ┌────────────────────────┐     ┌──────────┐
  │ Order    │────▶│ FIFO Queue             │────▶│ State    │
  │ Events   │     │ GroupId = orderId       │     │ Machine  │
  └──────────┘     │ DeduplicationId = eventId│     │ Consumer │
                   └────────────────────────┘     └────┬─────┘
                                                       │
                                                       ▼
                                                ┌─────────────┐
                                                │  Orders DB   │
                                                │              │
                                                │ UPDATE orders │
                                                │ SET status =  │
                                                │   :new_status │
                                                │ WHERE id =    │
                                                │   :order_id   │
                                                │ AND status =   │
                                                │   :expected    │
                                                │   _status     │
                                                └─────────────┘

  Key decisions:
  1. FIFO queue with MessageGroupId = orderId
     → All events for same order processed in sequence
     → Different orders processed in parallel

  2. MessageDeduplicationId = unique event ID
     → Retries within 5 min deduplicated by SQS
     → Beyond 5 min: DB conditional update handles it

  3. Conditional DB update (optimistic locking):
     UPDATE orders SET status='PAID' 
     WHERE id=:orderId AND status='CREATED'
     
     If rows_affected = 0 → stale event, skip
     If rows_affected = 1 → state transition applied

  4. Dead letter queue for events that fail 3 times
     → Alert ops team
     → Manual investigation

  Edge case: What if PAID event arrives before CREATED?
  → This CAN'T happen with FIFO + same GroupId (ordered)
  → But if using Standard queue → need a state buffer:
    Store event, process when prerequisite state exists
```

### Q7: Your SQS consumers are hitting the 120,000 in-flight message limit. How do you diagnose and fix this?

**Expected Answer:**
```
Diagnosis:
  OverLimit error from ReceiveMessage = 120,000 in-flight messages
  
  In-flight = received but not yet deleted
  
  Root causes:
  ┌─────────────────────────────────────────────────────────────┐
  │ 1. Visibility timeout way too high                          │
  │    → Messages stay in-flight for hours even after processing│
  │    Fix: Reduce to 6× actual processing time                 │
  │                                                             │
  │ 2. Consumers not deleting messages after processing         │
  │    → Bug in consumer code (no DeleteMessage call)           │
  │    Fix: Verify delete is called on success path             │
  │                                                             │
  │ 3. Consumer processing is too slow                          │
  │    → Each consumer holds messages for too long              │
  │    Fix: Optimize processing, add more consumers             │
  │                                                             │
  │ 4. Too many consumers receiving but few deleting            │
  │    → Consumers crash during processing                      │
  │    Fix: Fix crashes, reduce batch size, add monitoring       │
  └─────────────────────────────────────────────────────────────┘

  Mitigation strategies:
  1. Split into multiple queues (each gets 120K limit)
  2. Reduce visibility timeout to free up in-flight slots faster
  3. Process and delete faster (optimize consumer)
  4. Scale consumers to drain queue faster
  5. Use CloudWatch: ApproximateNumberOfMessagesNotVisible
     Alert when approaching 100,000 (83% of limit)
```

### Q8: Design an SQS-based system where message processing must complete within a strict SLA of 5 seconds end-to-end (producer sends → consumer finishes). What are the challenges?

**Expected Answer:**
```
Challenges with a 5-second SLA:

  Total budget = 5000ms

  Breakdown:
  ┌────────────────────────────────────────────────────┐
  │ SendMessage API call          ~10-50ms             │
  │ SQS internal routing          ~5-20ms              │
  │ Consumer polling latency      0ms (long poll)      │
  │   OR  up to 20s (worst case long poll wait)        │
  │ Consumer processing           ~varies              │
  │ DeleteMessage API call        ~10-50ms             │
  │                                                    │
  │ PROBLEM: Long polling wait can be up to 20 seconds!│
  │ Even with WaitTimeSeconds=1, you burn 1 second.    │
  └────────────────────────────────────────────────────┘

  Solution architecture:
  ┌──────────────────────────────────────────────────────────┐
  │ 1. Use Lambda event source mapping (AWS polls optimally) │
  │    - AWS maintains persistent polling connections         │
  │    - Near-instant message delivery to Lambda             │
  │    - No 20-second wait                                   │
  │                                                          │
  │ 2. If not Lambda: aggressive polling                     │
  │    - Multiple concurrent pollers (10-20)                 │
  │    - WaitTimeSeconds=1 (short enough for SLA)            │
  │    - Accept higher cost for lower latency                │
  │                                                          │
  │ 3. Over-provision consumers                              │
  │    - Ensure backlog_per_instance is always near 0        │
  │    - Messages processed instantly when they arrive       │
  │                                                          │
  │ 4. Monitor P99 latency, not average                      │
  │    - ApproximateAgeOfOldestMessage must stay < 3 seconds │
  │    - Alert at 2 seconds (buffer for processing)          │
  │                                                          │
  │ 5. Consider: Is SQS the right choice for 5s SLA?        │
  │    - If synchronous response needed → don't use SQS      │
  │    - If async with 5s completion → SQS works with above  │
  │    - For sub-second → consider direct invocation or      │
  │      in-memory queue (Redis Streams)                     │
  └──────────────────────────────────────────────────────────┘
```

### Q9: Explain the "thundering herd" problem with SQS and how to mitigate it.

**Expected Answer:**
```
The Problem:
  ┌────────────────────────────────────────────────────────────┐
  │                                                             │
  │  100 consumers long-polling an SQS queue                    │
  │  Queue is mostly empty (low-traffic period)                 │
  │                                                             │
  │  A burst of 10 messages arrives:                            │
  │                                                             │
  │  Consumer 1  ─── polling ──▶ ┌─────┐ ◀── polling ─── Consumer 50 │
  │  Consumer 2  ─── polling ──▶ │ SQS │ ◀── polling ─── Consumer 51 │
  │  Consumer 3  ─── polling ──▶ │     │ ◀── polling ─── Consumer 52 │
  │  ...                         │ 10  │                  ...         │
  │  Consumer 49 ─── polling ──▶ │ msgs│ ◀── polling ─── Consumer 100│
  │                              └─────┘                             │
  │                                                                  │
  │  All 100 consumers wake up simultaneously!                       │
  │  Only 10 get messages. 90 get empty responses.                   │
  │  All 90 immediately re-poll → spike in API calls.                │
  │  $$ wasted, and SQS rate limiting may kick in.                   │
  │                                                                  │
  └──────────────────────────────────────────────────────────────────┘

  Mitigations:
  ┌────────────────────────────────────────────────────────────┐
  │  1. Scale consumers DOWN during low traffic                 │
  │     - Autoscale on queue depth → fewer idle consumers      │
  │     - Min instances = expected_throughput / per_consumer    │
  │                                                             │
  │  2. Jittered backoff on empty receives                      │
  │     - If ReceiveMessage returns empty → sleep(random(1-5s))│
  │     - Prevents synchronized re-polling                      │
  │                                                             │
  │  3. Use Lambda event source mapping                         │
  │     - AWS manages polling fleet optimally                   │
  │     - Scales pollers based on queue depth automatically     │
  │     - No thundering herd — AWS solves this for you         │
  │                                                             │
  │  4. Reduce MaxNumberOfMessages per consumer                 │
  │     - Instead of 10 consumers × 10 msgs = 100 capacity     │
  │     - Use 20 consumers × 5 msgs for better distribution    │
  └────────────────────────────────────────────────────────────┘
```

### Q10: Your company is migrating from RabbitMQ to SQS. What are the architectural differences you must account for, and what will break?

**Expected Answer:**
```
Critical Differences:
┌────────────────────────────┬─────────────────────┬──────────────────┐
│  Feature                   │  RabbitMQ            │  SQS              │
├────────────────────────────┼─────────────────────┼──────────────────┤
│  Delivery model            │  Push (broker pushes)│  Pull (consumer   │
│                            │                      │  polls)            │
│  Routing                   │  Exchange + bindings │  No routing       │
│                            │  (topic, fanout,     │  (1 queue = 1     │
│                            │   headers, direct)   │  destination)     │
│  Priority queues           │  Native (x-max-pri) │  Not supported    │
│  Request-reply (RPC)       │  reply-to + corr_id │  Not supported    │
│  TTL per message           │  Supported           │  Only at queue    │
│                            │                      │  level (retention)│
│  Acknowledgment            │  ACK/NACK/REJECT     │  Delete = ACK     │
│  Consumer cancel notify    │  Supported           │  Not supported    │
│  Dead-lettering trigger    │  Rejection or TTL    │  maxReceiveCount  │
└────────────────────────────┴─────────────────────┴──────────────────┘

What WILL break:
┌─────────────────────────────────────────────────────────────┐
│ 1. Push-based consumers → must rewrite as polling loops     │
│ 2. Exchange routing → must use SNS + SQS fan-out            │
│ 3. RPC pattern (reply-to) → must redesign (API Gateway +    │
│    async callback or Step Functions)                        │
│ 4. Priority queues → must implement with multiple SQS queues│
│ 5. Message TTL per message → use DelaySeconds or app logic  │
│ 6. NACK (negative ack) → just don't delete (timeout = retry)│
│ 7. Exclusive consumers → use FIFO with single MessageGroupId│
│ 8. Consumer prefetch → ReceiveMessage MaxNumberOfMessages   │
│ 9. Plugins (delayed exchange) → SQS delay queues (max 15m)  │
│10. Management UI → CloudWatch + AWS Console                  │
└─────────────────────────────────────────────────────────────┘

Migration strategy:
  Phase 1: Run both in parallel (dual-write)
  Phase 2: Migrate consumers one by one to SQS
  Phase 3: Cutover producers
  Phase 4: Decommission RabbitMQ
```

---

## 19. Quick Reference Card

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          SQS QUICK REFERENCE CARD                                │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  QUEUE LIMITS                                                                    │
│  ├── Max message size:           256 KB (up to 1 MB with newer limits)           │
│  ├── Max retention:              14 days (default 4 days)                        │
│  ├── Max delay:                  900 seconds (15 minutes)                        │
│  ├── Max visibility timeout:     12 hours                                        │
│  ├── Max in-flight messages:     120,000 per queue                               │
│  ├── Max batch size:             10 messages or 256 KB per batch                 │
│  ├── Max long poll wait:         20 seconds                                      │
│  ├── Max queues per account:     unlimited                                       │
│  └── Max message attributes:     10 per message                                  │
│                                                                                  │
│  FIFO LIMITS                                                                     │
│  ├── Default throughput:         300 TPS per API action                           │
│  ├── With batching:              3,000 messages/sec                               │
│  ├── High throughput mode:       Up to 70,000 messages/sec                       │
│  ├── Deduplication window:       5 minutes                                        │
│  ├── Queue name:                 Must end in .fifo                                │
│  └── MessageGroupId:             Required (ordering key)                          │
│                                                                                  │
│  PRICING (us-east-1, 2025)                                                       │
│  ├── Standard:  $0.40 per 1M requests (first 1M free/month)                     │
│  ├── FIFO:      $0.50 per 1M requests (first 1M free/month)                     │
│  ├── 1 request = first 64 KB of payload                                          │
│  ├── 256 KB message = 4 requests billed                                          │
│  └── Data transfer: standard AWS rates apply                                     │
│                                                                                  │
│  KEY CLOUDWATCH METRICS                                                          │
│  ├── ApproximateNumberOfMessagesVisible        (queue depth)                     │
│  ├── ApproximateNumberOfMessagesNotVisible     (in-flight)                       │
│  ├── ApproximateNumberOfMessagesDelayed        (delayed)                         │
│  ├── ApproximateAgeOfOldestMessage             (staleness) ← MOST IMPORTANT     │
│  ├── NumberOfMessagesSent                      (producer throughput)             │
│  ├── NumberOfMessagesReceived                  (consumer throughput)             │
│  ├── NumberOfMessagesDeleted                   (completion rate)                 │
│  ├── NumberOfEmptyReceives                     (wasted polls)                    │
│  └── SentMessageSize                           (avg message size)               │
│                                                                                  │
│  REMEMBER FOR INTERVIEWS                                                         │
│  ├── SQS is PULL-based (not push). Consumers poll.                               │
│  ├── Standard = at-least-once. FIFO = exactly-once (5 min window).               │
│  ├── FIFO ordering is per MessageGroupId, NOT global.                            │
│  ├── Visibility timeout is where 80% of bugs hide.                               │
│  ├── Always implement idempotency. Always.                                       │
│  ├── Long polling saves money. Batching saves more.                              │
│  ├── DLQ is not optional — it's a necessity.                                     │
│  ├── Autoscale on queue depth, not CPU.                                          │
│  ├── SQS is regional. Region outage = SQS outage.                               │
│  └── Message deleted after processing. No replay. That's the design.            │
│                                                                                  │
│  ONE-LINER FOR INTERVIEWS:                                                       │
│  "SQS is a fully managed, serverless message queue that decouples producers     │
│   from consumers with at-least-once delivery, auto-scaling, and zero ops.        │
│   It's the right choice when you need simple async decoupling without            │
│   event replay semantics."                                                       │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

> **Last Updated:** April 2026  
> **Sources:** AWS SQS Developer Guide, AWS News Blog (Optimizing SQS for Speed and Scale, 2024), AWS re:Invent talks, Capital One Tech Blog, Segment Engineering Blog, Medium Engineering Post-Mortems, AWS Well-Architected Framework, Stack Overflow community discussions
