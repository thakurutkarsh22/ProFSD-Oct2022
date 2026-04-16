# Amazon SNS — The Complete Deep Dive

> **Difficulty:** Medium-Hard | **Time:** 5-7 hours | **Priority:** Must Know  
> **Sources:** AWS SNS Developer Guide, AWS Whitepapers, AWS Compute Blog, Arpit Bhayani, Alex Xu (ByteByteGo), Netflix Tech Blog, Twitter/X Engineering, cloudonaut.io Post-Mortems  
> **For:** Senior Engineers (7+ years) preparing for System Design interviews

---

## Table of Contents

1. [What Is SNS](#1-what-is-sns)
2. [Core Architecture](#2-core-architecture)
3. [Message Lifecycle — Publish to Delivery](#3-message-lifecycle--publish-to-delivery)
4. [Fan-Out Pattern Deep Dive](#4-fan-out-pattern-deep-dive)
5. [Message Filtering](#5-message-filtering)
6. [FIFO Topics — Ordering & Exactly-Once](#6-fifo-topics--ordering--exactly-once)
7. [Retry Policies & Dead-Letter Queues](#7-retry-policies--dead-letter-queues)
8. [Security Model](#8-security-model)
9. [SNS + SQS — The Canonical Combo](#9-sns--sqs--the-canonical-combo)
10. [SNS vs SQS vs EventBridge vs Kafka](#10-sns-vs-sqs-vs-eventbridge-vs-kafka)
11. [Real-World Usage at Scale](#11-real-world-usage-at-scale)
12. [When SNS Failed — Production Incidents](#12-when-sns-failed--production-incidents)
13. [When NOT to Use SNS](#13-when-not-to-use-sns)
14. [Anti-Patterns That Kill SNS](#14-anti-patterns-that-kill-sns)
15. [Limits, Quotas & Numbers to Know](#15-limits-quotas--numbers-to-know)
16. [Interview Questions — Medium](#16-interview-questions--medium)
17. [Interview Questions — Hard](#17-interview-questions--hard)
18. [Quick Reference Card](#18-quick-reference-card)

---

## 1. What Is SNS

Amazon Simple Notification Service (SNS) is a **fully managed publish-subscribe (pub/sub) messaging service** launched by AWS in 2010. It enables decoupled microservices, distributed systems, and serverless applications to communicate through **topics**.

It is NOT a message queue. It is a **message router / broadcast system**.

```
Traditional Message Queue (SQS):             SNS (Pub/Sub Broadcast):

  Producer → [Queue] → 1 Consumer             Publisher → [Topic] → N Subscribers

  ✗ Point-to-point only                        ✓ One-to-many fan-out
  ✗ Consumer pulls messages                    ✓ SNS pushes to subscribers
  ✗ One consumer per message                   ✓ All subscribers get every message
  ✗ Messages retained (up to 14 days)          ✗ No persistence — fire & forget
  ✗ No native filtering                        ✓ Attribute & payload-based filtering
  ✗ Single protocol (SQS API)                  ✓ Multi-protocol (HTTP, SQS, Lambda, SMS, Email)
```

### Two Messaging Modes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SNS = TWO MESSAGING WORLDS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. APPLICATION-TO-APPLICATION (A2A)     2. APPLICATION-TO-PERSON (A2P)     │
│  ┌────────────────────────────┐          ┌─────────────────────────────┐    │
│  │ Machine-to-machine comms    │          │ Machine-to-human comms       │    │
│  │                              │          │                               │    │
│  │ • SQS queues                 │          │ • SMS text messages           │    │
│  │ • Lambda functions           │          │ • Email / Email-JSON          │    │
│  │ • HTTP/HTTPS endpoints       │          │ • Mobile push (APNs, FCM)    │    │
│  │ • Kinesis Data Firehose      │          │ • Platform endpoints          │    │
│  │ • EventBridge                │          │                               │    │
│  └────────────────────────────┘          └─────────────────────────────┘    │
│                                                                             │
│  → System event fan-out, decoupling        → Alerts, OTP, marketing,       │
│    microservices, async workflows            user notifications             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why SNS Matters in System Design Interviews

```
Every notification system question, every event-driven architecture discussion,
every microservice decoupling conversation — SNS (or its equivalent) shows up.

"Design a Notification System"  → SNS is the backbone
"Design an E-commerce Platform" → SNS fans out order events
"Design a Monitoring System"    → SNS routes alerts
"Design a Chat Application"     → SNS delivers push notifications
```

---

## 2. Core Architecture

### The Master Diagram — How Everything Fits Together

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│                            SNS ECOSYSTEM — FULL PICTURE                                   │
│                                                                                          │
│  PUBLISHERS (produce events)                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                 │
│  │ Order Service │  │ CloudWatch   │  │ S3 Events    │  │ API Gateway  │                 │
│  │              │  │ Alarm        │  │ (PutObject)  │  │ (user action)│                 │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                 │
│         │                 │                 │                 │                           │
│         │  Publish API    │  Alarm Action   │  Event Notif.   │  Publish API              │
│         │                 │                 │                 │                           │
│         ▼                 ▼                 ▼                 ▼                           │
│  ═══════════════════════════════════════════════════════════════════════                   │
│                                                                                          │
│   SNS TOPIC (logical channel)                                                            │
│  ┌────────────────────────────────────────────────────────────────────────────┐           │
│  │                                                                            │           │
│  │   Topic ARN: arn:aws:sns:us-east-1:123456789:order-events                  │           │
│  │                                                                            │           │
│  │   ┌─────────────────────────────────────────────────────────────────┐      │           │
│  │   │                    MESSAGE ROUTER ENGINE                        │      │           │
│  │   │                                                                 │      │           │
│  │   │  1. Receive message from publisher                              │      │           │
│  │   │  2. Validate message (size, format, permissions)                │      │           │
│  │   │  3. Evaluate filter policies for each subscription              │      │           │
│  │   │  4. Fan-out: push to ALL matching subscribers in parallel       │      │           │
│  │   │  5. Apply per-endpoint delivery policy (retries, backoff)       │      │           │
│  │   │  6. Route failures to DLQ if configured                         │      │           │
│  │   │                                                                 │      │           │
│  │   └─────────────────────────────────────────────────────────────────┘      │           │
│  │                                                                            │           │
│  │   Subscriptions:                                                           │           │
│  │   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐            │           │
│  │   │ SQS Queue  │ │ Lambda     │ │ HTTP/S     │ │ Email      │            │           │
│  │   │ (inventory)│ │ (analytics)│ │ (webhook)  │ │ (ops team) │            │           │
│  │   │            │ │            │ │            │ │            │            │           │
│  │   │ Filter:    │ │ Filter:    │ │ Filter:    │ │ Filter:    │            │           │
│  │   │ type=stock │ │ type=*     │ │ type=high$ │ │ type=error │            │           │
│  │   └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘            │           │
│  │         │              │              │              │                     │           │
│  └─────────┼──────────────┼──────────────┼──────────────┼─────────────────────┘           │
│            │              │              │              │                                 │
│            ▼              ▼              ▼              ▼                                 │
│  SUBSCRIBERS (consume events)                                                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                 │
│  │ Inventory Svc│  │ Analytics    │  │ Partner API  │  │ ops@co.com   │                 │
│  │ (via SQS)    │  │ Pipeline     │  │ (webhook)    │  │ (email)      │                 │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘                 │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### Three Core Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  CONCEPT 1: TOPIC                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ • Logical access point & communication channel                      │    │
│  │ • Identified by an ARN (Amazon Resource Name)                       │    │
│  │ • Supports up to 12.5 million subscriptions per topic               │    │
│  │ • Two types: Standard (best-effort ordering) & FIFO (strict order)  │    │
│  │ • Has access policies (who can publish, who can subscribe)          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  CONCEPT 2: SUBSCRIPTION                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ • A binding between a topic and an endpoint                         │    │
│  │ • Requires confirmation (except SQS, Lambda, Firehose)              │    │
│  │ • Can have a filter policy (receive subset of messages)             │    │
│  │ • Can have a delivery policy (retry behavior)                       │    │
│  │ • Can have a DLQ (capture failed deliveries)                        │    │
│  │ • Can enable raw message delivery (skip SNS JSON wrapping)          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  CONCEPT 3: MESSAGE                                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ • Max 256 KB (use claim-check pattern for larger payloads)          │    │
│  │ • Has a body (string payload) and optional message attributes       │    │
│  │ • Attributes: up to 10, used for filtering (key-value metadata)     │    │
│  │ • Can be protocol-specific (different body per endpoint type)       │    │
│  │ • Gets a unique MessageId upon publish                              │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### SNS Internal Components (What AWS Runs Under the Hood)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     SNS INTERNAL ARCHITECTURE (INFERRED)                   │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │  FRONTEND FLEET (API Layer)                                    │      │
│  │  • Handles Publish, Subscribe, CreateTopic APIs                │      │
│  │  • Authentication via IAM / SigV4                              │      │
│  │  • Request validation, rate limiting                           │      │
│  │  • Distributed across multiple AZs                             │      │
│  └───────────────────────┬────────────────────────────────────────┘      │
│                          │                                               │
│                          ▼                                               │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │  METADATA STORE                                                │      │
│  │  • Topic configurations, subscription lists                    │      │
│  │  • Filter policies, delivery policies                          │      │
│  │  • Backed by DynamoDB (AWS internal, highly available)          │      │
│  │  • Replicated across AZs for durability                        │      │
│  └───────────────────────┬────────────────────────────────────────┘      │
│                          │                                               │
│                          ▼                                               │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │  DELIVERY ENGINE (Fan-Out Workers)                             │      │
│  │  • Parallel delivery to all matching subscribers               │      │
│  │  • Endpoint-specific adapters (SQS, Lambda, HTTP, SMS, etc.)   │      │
│  │  • Retry scheduling with backoff                               │      │
│  │  • DLQ routing for exhausted retries                           │      │
│  │  • Per-AZ redundancy                                           │      │
│  └───────────────────────┬────────────────────────────────────────┘      │
│                          │                                               │
│                          ▼                                               │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │  MONITORING & OBSERVABILITY                                    │      │
│  │  • CloudWatch Metrics (NumberOfMessagesPublished,              │      │
│  │    NumberOfNotificationsDelivered, NumberOfNotificationsFailed) │      │
│  │  • CloudWatch Logs (delivery status logging)                   │      │
│  │  • CloudTrail (API audit trail)                                │      │
│  └────────────────────────────────────────────────────────────────┘      │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Message Lifecycle — Publish to Delivery

### End-to-End Message Flow

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        MESSAGE LIFECYCLE — STEP BY STEP                                 │
│                                                                                        │
│  STEP 1: PUBLISH                                                                       │
│  ┌─────────────────────────────────────────────────┐                                   │
│  │  Publisher calls SNS Publish API                 │                                   │
│  │  • TopicArn: arn:aws:sns:us-east-1:123:orders    │                                   │
│  │  • Message: {"orderId": "A-123", "amount": 500}  │                                   │
│  │  • MessageAttributes:                             │                                   │
│  │      event_type: "order_placed"                   │                                   │
│  │      priority: "high"                             │                                   │
│  └──────────────────────┬──────────────────────────┘                                   │
│                         │                                                               │
│                         ▼                                                               │
│  STEP 2: VALIDATE & ACCEPT                                                             │
│  ┌─────────────────────────────────────────────────┐                                   │
│  │  SNS Frontend:                                   │                                   │
│  │  • Authenticate caller (IAM/SigV4)               │                                   │
│  │  • Check topic access policy                     │                                   │
│  │  • Validate message size (≤ 256 KB)              │                                   │
│  │  • Assign MessageId (globally unique UUID)       │                                   │
│  │  • Return MessageId to publisher (synchronous)   │                                   │
│  │  • Message stored in ≥ 3 AZs before ACK          │                                   │
│  └──────────────────────┬──────────────────────────┘                                   │
│                         │                                                               │
│                         ▼                                                               │
│  STEP 3: EVALUATE SUBSCRIPTIONS                                                        │
│  ┌─────────────────────────────────────────────────┐                                   │
│  │  For EACH subscription on the topic:             │                                   │
│  │  • Load filter policy (if any)                   │                                   │
│  │  • Match message attributes against filter       │                                   │
│  │  • If NO filter → message matches (deliver)      │                                   │
│  │  • If filter EXISTS but doesn't match → SKIP     │                                   │
│  └──────────────────────┬──────────────────────────┘                                   │
│                         │                                                               │
│                         ▼                                                               │
│  STEP 4: PARALLEL FAN-OUT                                                              │
│  ┌─────────────────────────────────────────────────┐                                   │
│  │  For ALL matching subscriptions SIMULTANEOUSLY:  │                                   │
│  │                                                   │                                   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐          │                                   │
│  │  │  SQS    │  │ Lambda  │  │  HTTP   │          │                                   │
│  │  │ Adapter │  │ Adapter │  │ Adapter │          │                                   │
│  │  └────┬────┘  └────┬────┘  └────┬────┘          │                                   │
│  │       │            │            │                 │                                   │
│  │       ▼            ▼            ▼                 │                                   │
│  │  SendMessage   Invoke      POST request          │                                   │
│  │  to SQS        Lambda      to endpoint           │                                   │
│  └──────────────────────┬──────────────────────────┘                                   │
│                         │                                                               │
│                         ▼                                                               │
│  STEP 5: DELIVERY RESULT                                                               │
│  ┌─────────────────────────────────────────────────┐                                   │
│  │  Per subscriber:                                  │                                   │
│  │                                                   │                                   │
│  │  SUCCESS (2xx) ──→ Done, log delivery             │                                   │
│  │                                                   │                                   │
│  │  FAILURE ──→ Enter retry policy                   │                                   │
│  │    │                                              │                                   │
│  │    ├── Immediate retries (no delay)               │                                   │
│  │    ├── Pre-backoff retries (fixed delay)          │                                   │
│  │    ├── Backoff retries (exponential)              │                                   │
│  │    ├── Post-backoff retries (fixed delay)         │                                   │
│  │    │                                              │                                   │
│  │    └── All retries exhausted?                     │                                   │
│  │         ├── DLQ configured → Send to DLQ          │                                   │
│  │         └── No DLQ → MESSAGE LOST FOREVER         │                                   │
│  └─────────────────────────────────────────────────┘                                   │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Message Format — What Subscribers Actually Receive

```
┌──────────────────────────────────────────────────────────────────┐
│  STANDARD SNS MESSAGE ENVELOPE (JSON wrapper)                    │
│                                                                  │
│  {                                                               │
│    "Type": "Notification",                                       │
│    "MessageId": "da41e39f-ea4d-435a-b922-c6aae3915eba",         │
│    "TopicArn": "arn:aws:sns:us-east-1:123:order-events",        │
│    "Subject": "Order Placed",                                    │
│    "Message": "{\"orderId\":\"A-123\",\"amount\":500}",          │
│    "Timestamp": "2026-04-15T10:30:00.000Z",                     │
│    "SignatureVersion": "1",                                      │
│    "Signature": "EXAMPLEpH+...",                                 │
│    "SigningCertURL": "https://sns.us-east-1.amazonaws.com/...",  │
│    "UnsubscribeURL": "https://sns.us-east-1.amazonaws.com/...", │
│    "MessageAttributes": {                                        │
│      "event_type": {                                             │
│        "Type": "String",                                         │
│        "Value": "order_placed"                                   │
│      }                                                           │
│    }                                                             │
│  }                                                               │
│                                                                  │
│  NOTE: Enable "Raw Message Delivery" on SQS/HTTP subscriptions   │
│  to skip this wrapper and receive only the Message body.         │
│  Raw delivery reduces payload size and avoids double-parsing.    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. Fan-Out Pattern Deep Dive

The fan-out pattern is **the** reason SNS exists. One event, many reactions, zero coupling.

### Basic Fan-Out

```
                         ┌─────────────────────┐
                         │   ORDER SERVICE      │
                         │   publishes:         │
                         │   "order_placed"     │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   SNS TOPIC          │
                         │   order-events       │
                         └──┬────┬────┬────┬───┘
                            │    │    │    │
              ┌─────────────┘    │    │    └──────────────┐
              │                  │    │                    │
              ▼                  ▼    ▼                    ▼
     ┌────────────────┐ ┌──────────┐ ┌──────────────┐ ┌────────────┐
     │ SQS: Inventory │ │ Lambda:  │ │ SQS: Billing │ │ HTTP:      │
     │ Reservation    │ │ Analytics│ │ Processing   │ │ Partner    │
     │ Queue          │ │ Function │ │ Queue        │ │ Webhook    │
     └────────────────┘ └──────────┘ └──────────────┘ └────────────┘

  Each subscriber processes INDEPENDENTLY and in PARALLEL.
  If billing fails → inventory still works.
  If analytics is slow → order flow is unaffected.
```

### Advanced Fan-Out: SNS + SQS (The "Durable Fan-Out")

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│  WHY SNS → SQS (instead of SNS → Lambda directly)?                              │
│                                                                                  │
│  Problem with SNS → Lambda:                                                      │
│  ┌────────────────────────────────────────────────────────────────────┐          │
│  │  • Lambda has concurrency limits (1000 default)                    │          │
│  │  • If Lambda throttled → SNS retries → but retries are TIME-BOUND  │          │
│  │  • After retry window exhausts → MESSAGE LOST                      │          │
│  │  • No backpressure mechanism — Lambda can't say "slow down"        │          │
│  └────────────────────────────────────────────────────────────────────┘          │
│                                                                                  │
│  Solution: SNS → SQS → Lambda (the safe pattern):                                │
│  ┌────────────────────────────────────────────────────────────────────┐          │
│  │  • SQS acts as a BUFFER between SNS and Lambda                     │          │
│  │  • Messages persist in SQS (up to 14 days)                         │          │
│  │  • Lambda polls SQS at its own pace (backpressure!)                │          │
│  │  • If Lambda fails → message returns to queue (visibility timeout) │          │
│  │  • SQS has its OWN DLQ for repeated failures                       │          │
│  │  • Result: ZERO message loss                                       │          │
│  └────────────────────────────────────────────────────────────────────┘          │
│                                                                                  │
│                                                                                  │
│  Publisher                                                                       │
│     │                                                                            │
│     ▼                                                                            │
│  ┌─────────┐      ┌──────────┐      ┌──────────┐      ┌──────────────┐          │
│  │  SNS    │─────→│  SQS     │─────→│  Lambda  │─────→│  DynamoDB    │          │
│  │  Topic  │      │  Queue   │      │  Worker  │      │  (result)    │          │
│  └─────────┘      │          │      └──────────┘      └──────────────┘          │
│       │           │ Buffer:  │           │                                       │
│       │           │ 14 days  │     Fails? Message                               │
│       │           │ retention│     goes back to queue                            │
│       │           └──────────┘                                                   │
│       │                                                                          │
│       ├──────────→ SQS Queue 2 ──→ Another Lambda                               │
│       ├──────────→ SQS Queue 3 ──→ Another Service                              │
│       └──────────→ SQS Queue N ──→ ...                                          │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Fan-Out Topologies

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        FAN-OUT TOPOLOGY COMPARISON                        │
│                                                                          │
│  TOPOLOGY 1: SIMPLE FAN-OUT (one topic, many subscribers)                │
│                                                                          │
│     Publisher ──→ [Topic] ──→ Sub A                                      │
│                          ──→ Sub B                                       │
│                          ──→ Sub C                                       │
│                                                                          │
│     Use: All subscribers want SAME or FILTERED subset of events          │
│                                                                          │
│  ─────────────────────────────────────────────────────────────────────   │
│                                                                          │
│  TOPOLOGY 2: CHAINED FAN-OUT (topic → processing → topic)               │
│                                                                          │
│     Publisher ──→ [Topic A] ──→ Lambda (enrich) ──→ [Topic B] ──→ Sub X │
│                                                                ──→ Sub Y │
│                                                                          │
│     Use: Need to TRANSFORM/ENRICH events before further distribution     │
│                                                                          │
│  ─────────────────────────────────────────────────────────────────────   │
│                                                                          │
│  TOPOLOGY 3: HIERARCHICAL FAN-OUT (topic per domain)                     │
│                                                                          │
│     [Raw Events Topic] ──→ Lambda Router ──→ [Orders Topic]  ──→ ...    │
│                                           ──→ [Payment Topic] ──→ ...    │
│                                           ──→ [User Topic]    ──→ ...    │
│                                                                          │
│     Use: Segregate event domains, different teams own different topics    │
│                                                                          │
│  ─────────────────────────────────────────────────────────────────────   │
│                                                                          │
│  TOPOLOGY 4: SNS + EVENTBRIDGE HYBRID                                    │
│                                                                          │
│     Publisher ──→ [EventBridge] ──→ Rule: match order.* ──→ [SNS Topic]  │
│                                 ──→ Rule: match payment.* ──→ Lambda     │
│                                 ──→ Rule: match audit.* ──→ S3           │
│                                                                          │
│     Use: Need CONTENT-BASED routing before fan-out                       │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Message Filtering

Message filtering is one of the most underrated and most-asked-about features of SNS.

### Without vs. With Filtering

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  WITHOUT FILTERING (naive approach):                                     │
│                                                                          │
│  ┌───────┐     ┌──────────────┐     ┌─────────────────────────────┐     │
│  │ Topic │────→│ Inventory SQS│────→│ Lambda: receives ALL msgs   │     │
│  │       │     └──────────────┘     │ IF event_type == "stock"    │     │
│  │  ALL  │                          │   process()                  │     │
│  │ MSGS  │     ┌──────────────┐     │ ELSE                        │     │
│  │       │────→│ Billing SQS  │     │   discard()  ← WASTE!       │     │
│  │       │     └──────────────┘     └─────────────────────────────┘     │
│  └───────┘                                                               │
│                                                                          │
│  Problems: ✗ Every subscriber gets every message                         │
│            ✗ Wasted compute filtering in consumer code                   │
│            ✗ Wasted SQS polling cost                                     │
│            ✗ Higher Lambda invocations = higher cost                     │
│                                                                          │
│  ─────────────────────────────────────────────────────────────────────   │
│                                                                          │
│  WITH FILTERING (the right approach):                                    │
│                                                                          │
│  ┌───────┐     ┌──────────────────────────────────┐                     │
│  │ Topic │────→│ Inventory SQS                     │                     │
│  │       │     │ Filter: {"event_type": ["stock"]} │                     │
│  │  ALL  │     │ → Only receives stock events       │                     │
│  │ MSGS  │     └──────────────────────────────────┘                     │
│  │       │                                                               │
│  │       │     ┌──────────────────────────────────────────┐             │
│  │       │────→│ Billing SQS                               │             │
│  │       │     │ Filter: {"event_type": ["payment_due"]}   │             │
│  │       │     │ → Only receives billing events             │             │
│  └───────┘     └──────────────────────────────────────────┘             │
│                                                                          │
│  Benefits: ✓ Subscribers get ONLY relevant messages                      │
│            ✓ No wasted compute                                           │
│            ✓ Lower cost (fewer SQS messages, fewer Lambda invocations)   │
│            ✓ Single topic serves multiple use cases                      │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Two Scopes of Filtering

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        FILTER POLICY SCOPES                              │
│                                                                          │
│  SCOPE 1: MessageAttributes (DEFAULT)                                    │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │                                                                │      │
│  │  Publisher sets attributes alongside message body:             │      │
│  │                                                                │      │
│  │  sns.publish(                                                  │      │
│  │    TopicArn = "arn:...",                                       │      │
│  │    Message = '{"orderId": "A-123"}',                           │      │
│  │    MessageAttributes = {                                       │      │
│  │      "event_type": {"DataType": "String", "Value": "order"},  │      │
│  │      "amount":     {"DataType": "Number", "Value": "500"}     │      │
│  │    }                                                           │      │
│  │  )                                                             │      │
│  │                                                                │      │
│  │  Filter policy on subscription:                                │      │
│  │  {"event_type": ["order"], "amount": [{"numeric": [">=",100]}]}│      │
│  │                                                                │      │
│  │  ✓ Fast: attributes are in message metadata (not parsed body)  │      │
│  │  ✗ Requires publisher to set attributes explicitly             │      │
│  └────────────────────────────────────────────────────────────────┘      │
│                                                                          │
│  SCOPE 2: MessageBody (PAYLOAD-BASED)                                    │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │                                                                │      │
│  │  Filters directly on the JSON message body:                    │      │
│  │                                                                │      │
│  │  Message: {"orderId": "A-123", "status": "shipped", "amt": 5} │      │
│  │                                                                │      │
│  │  Filter policy (scope = MessageBody):                          │      │
│  │  {"status": ["shipped", "delivered"]}                          │      │
│  │                                                                │      │
│  │  ✓ No need for publisher to set attributes                     │      │
│  │  ✓ Works with AWS service events (S3, CloudWatch) that don't   │      │
│  │    set MessageAttributes                                       │      │
│  │  ✓ Supports NESTED JSON matching                               │      │
│  │  ✗ Slightly more compute (SNS parses the message body)         │      │
│  └────────────────────────────────────────────────────────────────┘      │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Filter Policy Operators (Interview Favorites)

```
┌────────────────────────────────────────────────────────────────────────────┐
│  OPERATOR             EXAMPLE POLICY                   MATCHES            │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Exact match          {"color": ["red"]}               color = "red"      │
│                                                                            │
│  Multiple values      {"color": ["red","blue"]}        color = red OR blue│
│  (OR logic)                                                                │
│                                                                            │
│  anything-but         {"color": [{"anything-but":      color != "red"     │
│                         ["red"]}]}                      AND color != null  │
│                                                                            │
│  Numeric range        {"price": [{"numeric":           100 ≤ price ≤ 500 │
│                         [">=",100,"<=",500]}]}                             │
│                                                                            │
│  Prefix               {"region": [{"prefix": "us-"}]} region starts w/   │
│                                                         "us-"             │
│                                                                            │
│  Suffix               {"file": [{"suffix": ".png"}]}   file ends w/      │
│                                                         ".png"            │
│                                                                            │
│  exists               {"color": [{"exists": true}]}    attribute exists   │
│  does-not-exist       {"color": [{"exists": false}]}   attribute absent   │
│                                                                            │
│  CROSS-KEY LOGIC: All keys use AND. Values within a key use OR.           │
│                                                                            │
│  Example: {"color": ["red","blue"], "size": [{"numeric": [">",10]}]}     │
│  Means: (color=red OR color=blue) AND (size > 10)                         │
│                                                                            │
├────────────────────────────────────────────────────────────────────────────┤
│  LIMITS:                                                                   │
│  • Max 5 attribute keys per filter policy                                  │
│  • Max 150 combinations across all key-value pairs                         │
│  • Max filter policy size: 256 KB                                          │
│  • String matching is CASE-SENSITIVE                                       │
│  • Filter policy changes take up to 15 minutes to propagate                │
│  • Numeric range: -10^9 to 10^9, up to 5 decimal places                   │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. FIFO Topics — Ordering & Exactly-Once

### Standard vs. FIFO Topics

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     STANDARD vs FIFO TOPICS                                   │
│                                                                              │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐        │
│  │     STANDARD TOPIC           │    │       FIFO TOPIC             │        │
│  ├──────────────────────────────┤    ├──────────────────────────────┤        │
│  │ Ordering:   Best-effort      │    │ Ordering:  Strict per        │        │
│  │             (no guarantee)   │    │            message group     │        │
│  │                              │    │                              │        │
│  │ Delivery:   At-least-once    │    │ Delivery:  Exactly-once     │        │
│  │             (duplicates OK)  │    │            (with conditions) │        │
│  │                              │    │                              │        │
│  │ Throughput: ~30,000 msg/s    │    │ Throughput: 300 msg/s/group  │        │
│  │             per region       │    │            3,000 msg/s/topic │        │
│  │                              │    │            30,000 w/ HT mode │        │
│  │                              │    │                              │        │
│  │ Subscribers: SQS, Lambda,    │    │ Subscribers: SQS FIFO       │        │
│  │   HTTP, Email, SMS, etc.     │    │              queues ONLY     │        │
│  │                              │    │                              │        │
│  │ Dedup:    None (your app)    │    │ Dedup:    5-minute window    │        │
│  │                              │    │           (content or ID)    │        │
│  │                              │    │                              │        │
│  │ Naming:   any-name           │    │ Naming:   must end in .fifo  │        │
│  │                              │    │                              │        │
│  │ Use for: Event broadcasts,   │    │ Use for: Financial txns,     │        │
│  │   notifications, fan-out     │    │   order processing, state    │        │
│  │                              │    │   machines requiring order   │        │
│  └──────────────────────────────┘    └──────────────────────────────┘        │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### FIFO Ordering & Message Groups

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    FIFO MESSAGE GROUPS — HOW ORDERING WORKS                    │
│                                                                              │
│  Each message has a MessageGroupId. Messages within the SAME group           │
│  are ordered. Messages across DIFFERENT groups are processed in PARALLEL.    │
│                                                                              │
│  Publisher sends:                                                            │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────┐       │
│  │  Msg1 (GroupId="user-A", Seq=1) ──┐                               │       │
│  │  Msg2 (GroupId="user-A", Seq=2) ──┼──→ Delivered in order: 1,2,3 │       │
│  │  Msg3 (GroupId="user-A", Seq=3) ──┘                               │       │
│  │                                                                    │       │
│  │  Msg4 (GroupId="user-B", Seq=1) ──┐                               │       │
│  │  Msg5 (GroupId="user-B", Seq=2) ──┼──→ Delivered in order: 4,5   │       │
│  │                                    │                               │       │
│  │  ← These two groups are INDEPENDENT. Processed in PARALLEL. →     │       │
│  └───────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│  KEY INSIGHT (interview gold):                                               │
│  • More MessageGroupIds = more parallelism = higher throughput               │
│  • Single MessageGroupId = everything serialized = bottleneck                │
│  • Use entity IDs (user_id, order_id) as group IDs for natural sharding     │
│                                                                              │
│  Throughput math:                                                            │
│  ┌─────────────────────────────────────────────────────────────┐             │
│  │  1 group   ×  300 msg/s  =     300 msg/s total             │             │
│  │  10 groups ×  300 msg/s  =   3,000 msg/s total             │             │
│  │  100 groups × 300 msg/s  =  30,000 msg/s total (HT mode)  │             │
│  └─────────────────────────────────────────────────────────────┘             │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Exactly-Once Delivery — The Fine Print

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                EXACTLY-ONCE IN SNS FIFO — CONDITIONS THAT MUST HOLD          │
│                                                                              │
│  SNS FIFO → SQS FIFO can achieve exactly-once, BUT ONLY IF:                │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✓ Subscribed SQS queue exists and has correct permissions     │          │
│  │  ✓ Consumer deletes message before visibility timeout expires  │          │
│  │  ✓ NO message filtering enabled on the subscription            │          │
│  │    (filtering downgrades to at-most-once!)                     │          │
│  │  ✓ No network disruptions during delivery acknowledgment      │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  Deduplication:                                                              │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  Method 1: Explicit dedup ID                                   │          │
│  │    Publisher sets MessageDeduplicationId = "order-A-123-v1"    │          │
│  │    Any duplicate with same ID within 5 minutes → silently      │          │
│  │    accepted but NOT delivered                                   │          │
│  │                                                                │          │
│  │  Method 2: Content-based dedup                                 │          │
│  │    Enable ContentBasedDeduplication on topic                   │          │
│  │    SNS hashes message body → uses as dedup ID                  │          │
│  │    ⚠ Message attributes are NOT included in the hash!          │          │
│  │    → Same body + different attributes = DEDUPLICATED (gotcha!) │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ⚠  INTERVIEW TRAP:                                                         │
│  "Does SNS guarantee exactly-once delivery?"                                 │
│  ANSWER: Only FIFO topics, only to SQS FIFO queues, only without            │
│  filtering, and the consumer must handle its side correctly.                 │
│  Standard topics are ALWAYS at-least-once.                                   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Retry Policies & Dead-Letter Queues

### Retry Policy Phases

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    SNS RETRY POLICY — FOUR PHASES                             │
│                                                                              │
│  MESSAGE DELIVERY FAILS                                                      │
│         │                                                                    │
│         ▼                                                                    │
│  PHASE 1: IMMEDIATE RETRY (no delay)                                        │
│  ┌──────────────────────────────────────────┐                               │
│  │  numNoDelayRetries: 3 (default for HTTP)  │                               │
│  │  Retry instantly, no backoff              │                               │
│  └────────────────────┬─────────────────────┘                               │
│                       │ still failing?                                       │
│                       ▼                                                      │
│  PHASE 2: PRE-BACKOFF (fixed min delay)                                     │
│  ┌──────────────────────────────────────────┐                               │
│  │  numMinDelayRetries: 2                    │                               │
│  │  Delay = minDelayTarget (e.g., 20s)       │                               │
│  └────────────────────┬─────────────────────┘                               │
│                       │ still failing?                                       │
│                       ▼                                                      │
│  PHASE 3: BACKOFF (exponential/linear/geometric/arithmetic)                 │
│  ┌──────────────────────────────────────────────────────────────────┐       │
│  │  numRetries: 10                                                   │       │
│  │  backoffFunction: "exponential"                                   │       │
│  │  minDelayTarget: 20s, maxDelayTarget: 300s                        │       │
│  │                                                                    │       │
│  │  Delay progression (exponential):                                  │       │
│  │  20s → 40s → 80s → 160s → 300s → 300s → 300s → ...               │       │
│  │                                (capped at maxDelay)                │       │
│  └────────────────────┬─────────────────────────────────────────────┘       │
│                       │ still failing?                                       │
│                       ▼                                                      │
│  PHASE 4: POST-BACKOFF (fixed max delay)                                    │
│  ┌──────────────────────────────────────────┐                               │
│  │  numMaxDelayRetries: 2                    │                               │
│  │  Delay = maxDelayTarget (e.g., 300s)      │                               │
│  └────────────────────┬─────────────────────┘                               │
│                       │ ALL retries exhausted                                │
│                       ▼                                                      │
│  ┌──────────────────────────────────────────┐                               │
│  │  DLQ configured? ─── Yes ──→ Send to DLQ │                               │
│  │       │                                   │                               │
│  │       No                                  │                               │
│  │       │                                   │                               │
│  │       ▼                                   │                               │
│  │  ⚠ MESSAGE LOST PERMANENTLY              │                               │
│  └──────────────────────────────────────────┘                               │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Retry Defaults by Endpoint Type

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  ENDPOINT TYPE      TOTAL RETRIES     WINDOW         CUSTOMIZABLE?            │
├────────────────────────────────────────────────────────────────────────────────┤
│  SQS                100,015           23 days        No (AWS-managed)         │
│  Lambda             100,015           23 days        No (AWS-managed)         │
│  Firehose           100,015           23 days        No (AWS-managed)         │
│  HTTP/HTTPS         50                6 hours        YES (fully customizable) │
│  Email              (varies)          (varies)       No                       │
│  SMS                0-3               immediate      Limited                  │
│  Mobile push        (varies)          (varies)       Limited                  │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  KEY INSIGHT: AWS-managed endpoints (SQS, Lambda) get VASTLY more retries     │
│  than customer-managed (HTTP). This is because AWS controls both sides        │
│  of the connection for managed endpoints.                                      │
│                                                                                │
│  ⚠ For HTTP endpoints: 50 retries over 6 hours is the MAXIMUM.               │
│  After that → DLQ or lost.                                                     │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Dead-Letter Queue Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     DEAD-LETTER QUEUE PATTERN                                 │
│                                                                              │
│                                                                              │
│  Publisher ──→ [SNS Topic] ──→ [Subscriber Endpoint]                        │
│                    │                   │                                      │
│                    │              FAILS after                                 │
│                    │              all retries                                 │
│                    │                   │                                      │
│                    │                   ▼                                      │
│                    │           ┌───────────────┐                             │
│                    │           │ DEAD LETTER    │                             │
│                    │           │ QUEUE (SQS)    │                             │
│                    │           │                │                             │
│                    │           │ Contains:      │                             │
│                    │           │ • Original msg │                             │
│                    │           │ • Error reason │                             │
│                    │           │ • Retry count  │                             │
│                    │           │ • Timestamps   │                             │
│                    │           └───────┬───────┘                             │
│                    │                   │                                      │
│                    │                   ▼                                      │
│                    │           ┌───────────────┐                             │
│                    │           │ DLQ PROCESSOR  │                             │
│                    │           │ (Lambda/ECS)   │                             │
│                    │           │                │                             │
│                    │           │ • Alert ops    │                             │
│                    │           │ • Log failure  │                             │
│                    │           │ • Fix & replay │                             │
│                    │           │ • Analyze root │                             │
│                    │           │   cause        │                             │
│                    │           └───────────────┘                             │
│                    │                                                          │
│  RULES:                                                                      │
│  • DLQ must be in SAME account and region as the subscription                │
│  • Standard topic subscription → Standard SQS queue as DLQ                   │
│  • FIFO topic subscription → FIFO SQS queue as DLQ                           │
│  • DLQ needs sqs:SendMessage permission for sns.amazonaws.com                │
│  • Each subscription has its OWN DLQ (not shared per topic)                  │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Security Model

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        SNS SECURITY — LAYERED MODEL                          │
│                                                                              │
│  LAYER 1: AUTHENTICATION                                                     │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  • All API calls authenticated via IAM (Signature Version 4)   │          │
│  │  • No anonymous access unless topic policy explicitly allows   │          │
│  │  • Supports temporary credentials via STS (roles, federation)  │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  LAYER 2: AUTHORIZATION (Two Policy Systems)                                 │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │                                                                │          │
│  │  IAM Policies (identity-based):                                │          │
│  │  • Attached to IAM users, groups, roles                        │          │
│  │  • Control: who can Publish, Subscribe, CreateTopic, etc.      │          │
│  │  • Scoped to YOUR account only                                 │          │
│  │                                                                │          │
│  │  SNS Topic Policies (resource-based):                          │          │
│  │  • Attached directly to the SNS topic                          │          │
│  │  • Control: who can access THIS specific topic                 │          │
│  │  • CAN grant cross-account access                              │          │
│  │  • Required for: S3 events, CloudWatch alarms, other           │          │
│  │    AWS services publishing to your topic                       │          │
│  │                                                                │          │
│  │  ⚠ Both policies are evaluated together (union of permissions) │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  LAYER 3: ENCRYPTION                                                         │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  In Transit: TLS 1.2+ (enforced by default on all API calls)   │          │
│  │  At Rest:    Server-Side Encryption (SSE) with AWS KMS         │          │
│  │              • Uses symmetric KMS keys only                    │          │
│  │              • Encrypts message body (not metadata)            │          │
│  │              • Must grant kms:GenerateDataKey + kms:Decrypt    │          │
│  │                to SNS service principal                        │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  LAYER 4: NETWORK                                                            │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  • VPC Endpoints (PrivateLink): keep traffic off public        │          │
│  │    internet, access SNS from within VPC                        │          │
│  │  • Condition keys in policies: aws:SourceVpc, aws:SourceVpce   │          │
│  │  • Restrict publish to specific VPCs/VPC endpoints             │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  LAYER 5: MESSAGE VERIFICATION                                               │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  • Every SNS message is SIGNED (X.509 certificate)             │          │
│  │  • HTTP/S subscribers should VERIFY the signature              │          │
│  │  • Prevents spoofed messages from non-SNS sources              │          │
│  │  • SignatureVersion, Signature, SigningCertURL in every msg     │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Cross-Account Pattern

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│   ACCOUNT A (Publisher)              ACCOUNT B (Subscriber)                  │
│  ┌──────────────────────┐          ┌──────────────────────┐                 │
│  │                      │          │                      │                 │
│  │  Order Service       │          │  Analytics Service   │                 │
│  │       │              │          │       ▲              │                 │
│  │       ▼              │          │       │              │                 │
│  │  ┌───────────┐       │          │  ┌───────────┐      │                 │
│  │  │ SNS Topic │───────┼──────────┼─→│ SQS Queue │      │                 │
│  │  │           │       │          │  │           │      │                 │
│  │  │ Policy:   │       │          │  │ Policy:   │      │                 │
│  │  │ Allow     │       │          │  │ Allow SNS │      │                 │
│  │  │ Acct B to │       │          │  │ from      │      │                 │
│  │  │ Subscribe │       │          │  │ Acct A to │      │                 │
│  │  └───────────┘       │          │  │ SendMsg   │      │                 │
│  │                      │          │  └───────────┘      │                 │
│  └──────────────────────┘          └──────────────────────┘                 │
│                                                                              │
│  Requires:                                                                   │
│  1. SNS topic policy in Account A: Allow Account B to sns:Subscribe          │
│  2. SQS queue policy in Account B: Allow sns.amazonaws.com + Topic ARN       │
│     to sqs:SendMessage                                                       │
│  3. Subscription created from Account B (or Account A if permitted)          │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. SNS + SQS — The Canonical Combo

This is the most important pattern in AWS messaging. If you remember ONE thing, remember this.

### Why They're Better Together

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                      SNS + SQS = BEST OF BOTH WORLDS                         │
│                                                                              │
│  SNS alone:                          SQS alone:                              │
│  ┌────────────────────────┐          ┌────────────────────────┐             │
│  │ ✓ Fan-out (1-to-many)  │          │ ✓ Durable (14 days)    │             │
│  │ ✓ Push-based           │          │ ✓ Backpressure         │             │
│  │ ✓ Filtering            │          │ ✓ Rate control         │             │
│  │ ✗ No persistence       │          │ ✗ Point-to-point only  │             │
│  │ ✗ No backpressure      │          │ ✗ Single consumer      │             │
│  │ ✗ No replay            │          │ ✗ No broadcast         │             │
│  └────────────────────────┘          └────────────────────────┘             │
│                                                                              │
│  SNS + SQS combined:                                                         │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │ ✓ Fan-out + durability                                       │           │
│  │ ✓ Push-based broadcast + pull-based consumption              │           │
│  │ ✓ Topic-level filtering + queue-level retention              │           │
│  │ ✓ Publisher decoupled from consumer speed                    │           │
│  │ ✓ Consumer can crash → message waits in queue                │           │
│  │ ✓ Each consumer team owns their queue (independent scaling)  │           │
│  │ ✓ DLQ at queue level for poison messages                     │           │
│  └──────────────────────────────────────────────────────────────┘           │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Full Production Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                     PRODUCTION: SNS + SQS FAN-OUT WITH DLQ                            │
│                                                                                      │
│                                                                                      │
│  Order Service ──→ [SNS: order-events]                                               │
│                           │                                                          │
│           ┌───────────────┼───────────────┬──────────────────┐                       │
│           │               │               │                  │                       │
│           ▼               ▼               ▼                  ▼                       │
│    ┌──────────┐    ┌──────────┐    ┌──────────┐     ┌────────────┐                   │
│    │ SQS:     │    │ SQS:     │    │ SQS:     │     │ SQS:       │                   │
│    │ inventory│    │ billing  │    │ shipping │     │ analytics  │                   │
│    │ -queue   │    │ -queue   │    │ -queue   │     │ -queue     │                   │
│    │          │    │          │    │          │     │            │                   │
│    │ Filter:  │    │ Filter:  │    │ Filter:  │     │ No filter  │                   │
│    │ stock    │    │ payment  │    │ shipped  │     │ (gets all) │                   │
│    └────┬─────┘    └────┬─────┘    └────┬─────┘     └─────┬──────┘                   │
│         │               │               │                 │                          │
│         ▼               ▼               ▼                 ▼                          │
│    ┌──────────┐    ┌──────────┐    ┌──────────┐     ┌──────────┐                    │
│    │ Lambda:  │    │ ECS:     │    │ Lambda:  │     │ Firehose: │                    │
│    │ update   │    │ charge   │    │ notify   │     │ to S3/    │                    │
│    │ stock    │    │ card     │    │ carrier  │     │ Redshift  │                    │
│    └────┬─────┘    └────┬─────┘    └────┬─────┘     └──────────┘                    │
│         │               │               │                                            │
│    Fails 5x?       Fails 5x?       Fails 5x?                                       │
│         │               │               │                                            │
│         ▼               ▼               ▼                                            │
│    ┌──────────┐    ┌──────────┐    ┌──────────┐                                     │
│    │ DLQ:     │    │ DLQ:     │    │ DLQ:     │                                     │
│    │ inv-dlq  │    │ bill-dlq │    │ ship-dlq │                                     │
│    └────┬─────┘    └────┬─────┘    └────┬─────┘                                     │
│         │               │               │                                            │
│         └───────────────┼───────────────┘                                            │
│                         ▼                                                            │
│                 ┌───────────────┐                                                    │
│                 │ DLQ Monitor   │                                                    │
│                 │ (CloudWatch   │                                                    │
│                 │ Alarm → PD)   │                                                    │
│                 └───────────────┘                                                    │
│                                                                                      │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. SNS vs SQS vs EventBridge vs Kafka

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                  COMPARISON MATRIX                                         │
│                                                                                           │
│  Feature            │  SNS              │  SQS             │ EventBridge    │ Kafka        │
│  ───────────────────┼───────────────────┼──────────────────┼────────────────┼──────────────│
│  Pattern            │  Pub/Sub          │  Queue           │ Event Bus      │ Event Log    │
│  Push/Pull          │  Push             │  Pull            │ Push           │ Pull         │
│  Delivery           │  At-least-once    │  At-least-once   │ At-least-once  │ Configurable │
│  Ordering           │  No (Std) /       │  No (Std) /      │ No guarantee   │ Per partition│
│                     │  Yes (FIFO)       │  Yes (FIFO)      │                │              │
│  Persistence        │  None             │  14 days max     │ 24hr replay    │ Configurable │
│                     │  (fire & forget)  │                  │                │ (days/forever│
│  Message Size       │  256 KB           │  256 KB          │ 256 KB         │ 1 MB default │
│  Throughput         │  30K msg/s        │  Unlimited (Std) │ ~10K events/s  │ 1M+ msg/s   │
│  Filtering          │  Attribute +      │  Client-side     │ Content-based  │ Client-side  │
│                     │  payload-based    │  only            │ rules          │ (topics)     │
│  Consumers          │  1-to-many        │  1-to-1          │ 1-to-many      │ N groups     │
│  Replay             │  No               │  No              │ 24hr archive   │ Yes (offset) │
│  Managed?           │  Fully            │  Fully           │ Fully          │ Self/MSK     │
│  Cost model         │  Per publish +    │  Per request     │ Per event      │ Per broker   │
│                     │  per delivery     │                  │                │ hour         │
│                     │                   │                  │                │              │
│  ───────────────────┼───────────────────┼──────────────────┼────────────────┼──────────────│
│  BEST FOR           │  Broadcasting     │  Work queues,    │ Complex event  │ High-volume  │
│                     │  events to many   │  decoupling      │ routing, SaaS  │ streaming,   │
│                     │  consumers,       │  services,       │ integrations,  │ replay,      │
│                     │  notifications    │  buffering       │ schema-based   │ event        │
│                     │                   │                  │ matching       │ sourcing     │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

### Decision Tree

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    WHICH AWS MESSAGING SERVICE?                               │
│                                                                              │
│  Need to broadcast ONE event to MANY consumers?                              │
│  ├── Yes → Do you need message replay?                                       │
│  │         ├── Yes → Use KAFKA (MSK) or EventBridge (24hr archive)           │
│  │         └── No  → Use SNS (+ SQS per consumer for durability)             │
│  │                                                                           │
│  └── No  → Point-to-point, ONE producer to ONE consumer?                     │
│            ├── Yes → Use SQS                                                 │
│            └── No  → Need content-based routing with complex rules?           │
│                     ├── Yes → Use EventBridge                                │
│                     └── No  → Need >100K msg/s with replay?                  │
│                              ├── Yes → Use Kafka (MSK)                       │
│                              └── No  → Use SNS + SQS combo                   │
│                                                                              │
│  SHORTCUT:                                                                   │
│  • "Notify many"           → SNS                                             │
│  • "Process one by one"    → SQS                                             │
│  • "Route by content"      → EventBridge                                     │
│  • "Stream at massive scale" → Kafka                                         │
│  • "Notify many + durable" → SNS → SQS (fan-out)                            │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Real-World Usage at Scale

### Scenario 1: E-Commerce Order Processing (Amazon/Shopify pattern)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                 E-COMMERCE: ORDER EVENT FAN-OUT                               │
│                                                                              │
│  User places order                                                           │
│       │                                                                      │
│       ▼                                                                      │
│  Order Service ──→ Publish to SNS topic: "order-events"                      │
│                    Attributes: {type: "placed", amount: 150, region: "US"}   │
│                                                                              │
│  Fan-out to:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐         │
│  │  1. Inventory Service (SQS)                                     │         │
│  │     Filter: {type: ["placed"]}                                  │         │
│  │     → Reserve items, update stock count                         │         │
│  │                                                                 │         │
│  │  2. Payment Service (SQS)                                       │         │
│  │     Filter: {type: ["placed"]}                                  │         │
│  │     → Charge credit card, create invoice                        │         │
│  │                                                                 │         │
│  │  3. Notification Service (Lambda)                               │         │
│  │     Filter: {type: ["placed", "shipped", "delivered"]}          │         │
│  │     → Send email/SMS to customer                                │         │
│  │                                                                 │         │
│  │  4. Fraud Detection (SQS)                                       │         │
│  │     Filter: {amount: [{"numeric": [">=", 500]}]}                │         │
│  │     → High-value order review (only orders ≥$500)               │         │
│  │                                                                 │         │
│  │  5. Analytics Pipeline (Kinesis Firehose)                       │         │
│  │     No filter (receives everything)                             │         │
│  │     → Stream to S3 → Redshift for reporting                     │         │
│  │                                                                 │         │
│  │  6. Partner Webhook (HTTPS)                                     │         │
│  │     Filter: {region: ["US"]}                                    │         │
│  │     → Notify US-based fulfillment partner                       │         │
│  └─────────────────────────────────────────────────────────────────┘         │
│                                                                              │
│  Why SNS here (not direct service calls):                                    │
│  • Order service doesn't know (or care) about downstream consumers           │
│  • Adding a new consumer = add a subscription (no code change in publisher)  │
│  • Each consumer processes at its own speed                                  │
│  • One consumer failing doesn't block others                                 │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Scenario 2: Infrastructure Alerting (Netflix/Uber pattern)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                 ALERTING: MULTI-CHANNEL FAN-OUT                               │
│                                                                              │
│  CloudWatch Alarm fires (CPU > 90%)                                          │
│       │                                                                      │
│       ▼                                                                      │
│  SNS Topic: "infra-alerts"                                                   │
│       │                                                                      │
│       ├──→ Email: ops-team@company.com   (all alerts)                        │
│       │                                                                      │
│       ├──→ SMS: +1-555-0100              (severity: "critical" only)         │
│       │    Filter: {severity: ["critical"]}                                  │
│       │                                                                      │
│       ├──→ Lambda: create-pagerduty-incident                                 │
│       │    Filter: {severity: ["critical", "high"]}                          │
│       │                                                                      │
│       ├──→ SQS → Lambda: auto-scale-handler                                 │
│       │    Filter: {alert_type: ["cpu_high", "memory_high"]}                 │
│       │    → Automatically scale up ECS/EC2 instances                        │
│       │                                                                      │
│       └──→ Slack webhook (HTTPS)                                             │
│            → Post to #ops-alerts channel                                     │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Scenario 3: Multi-Region Event Distribution

```
┌──────────────────────────────────────────────────────────────────────────────┐
│               MULTI-REGION: CROSS-REGION EVENT REPLICATION                    │
│                                                                              │
│  Region: us-east-1                    Region: eu-west-1                      │
│  ┌─────────────────────────┐          ┌─────────────────────────┐           │
│  │                         │          │                         │           │
│  │  User Service           │          │  EU Compliance Service  │           │
│  │       │                 │          │       ▲                 │           │
│  │       ▼                 │          │       │                 │           │
│  │  [SNS: user-events]     │          │  [SQS: eu-user-queue]  │           │
│  │       │                 │          │                         │           │
│  │       ├──→ Local SQS    │          └─────────────────────────┘           │
│  │       │                 │                    ▲                           │
│  │       └──→ SQS in ──────┼────────────────────┘                           │
│  │            eu-west-1    │     Cross-region subscription                  │
│  │  (cross-region sub)     │     (SNS pushes to SQS in other region)       │
│  │                         │                                                │
│  └─────────────────────────┘                                                │
│                                                                              │
│  ⚠ Caveat: Cross-region adds latency (~50-150ms) and data transfer cost    │
│  ⚠ Alternative: Use EventBridge Global Endpoints for active-active          │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Scenario 4: Notification System Design (Alex Xu / ByteByteGo Pattern)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│        NOTIFICATION SYSTEM DESIGN (THE INTERVIEW CLASSIC)                    │
│                                                                              │
│  Trigger Event (new follower, new comment, price drop, etc.)                 │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────┐                                                        │
│  │ Notification     │──→ Store in DB (notification_id, user_id,             │
│  │ Service          │    channel, status=PENDING, created_at)               │
│  │                  │                                                        │
│  │  Publish to SNS  │                                                        │
│  └────────┬─────────┘                                                        │
│           │                                                                  │
│           ▼                                                                  │
│  [SNS: notification-dispatch]                                                │
│           │                                                                  │
│  ┌────────┼────────┬────────┬────────────┐                                  │
│  │        │        │        │            │                                   │
│  ▼        ▼        ▼        ▼            ▼                                   │
│ SQS:    SQS:    SQS:    SQS:        SQS:                                   │
│ email   sms     push    in-app      webhook                                 │
│ -queue  -queue  -queue  -queue      -queue                                  │
│  │        │        │        │            │                                   │
│  ▼        ▼        ▼        ▼            ▼                                   │
│ SES    Twilio   FCM/    WebSocket   HTTP POST                               │
│        API     APNs    Server      to partner                               │
│                                                                              │
│  Each channel:                                                               │
│  • Has its own SQS queue (independent scaling)                               │
│  • Has its own DLQ (channel-specific failure handling)                        │
│  • Has its own retry logic (SMS retries differ from email)                   │
│  • Updates DB status: PENDING → SENT → DELIVERED / FAILED                    │
│                                                                              │
│  Scheduler (Arpit Bhayani / Razorpay pattern):                               │
│  • Periodic job scans DB for PENDING notifications older than X minutes      │
│  • Re-publishes to SNS (catch anything that fell through)                    │
│  • Guarantees eventual delivery even if initial publish fails                │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 12. When SNS Failed — Production Incidents

### Incident 1: The 65-Day Silent Delay (cloudonaut.io, 2020)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: SNS HTTPS Delivery Delayed 30+ Minutes for 65 DAYS               │
│                                                                              │
│  Company: cloudonaut.io (AWS consulting firm)                                │
│  Impact:  Monitoring alerts delayed by 30+ minutes                           │
│  Duration: 65 days before detection                                          │
│  Root Cause: SNS throttlePolicy bug                                          │
│                                                                              │
│  TIMELINE:                                                                   │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  Day 0:   Set throttlePolicy: maxReceivesPerSecond = 1        │          │
│  │           (conservative: topic handles few msgs/hour)          │          │
│  │                                                                │          │
│  │  Day 1-65: Messages to HTTPS subscriber delayed 30+ minutes   │          │
│  │           No errors in CloudWatch.                             │          │
│  │           No failures reported.                                │          │
│  │           Messages eventually delivered — but LATE.            │          │
│  │                                                                │          │
│  │  Day 65:  Team notices alerts arriving late.                   │          │
│  │           Opens AWS Support ticket.                            │          │
│  │                                                                │          │
│  │  AWS FIX: "Remove the throttlePolicy entirely."               │          │
│  │           AWS confirmed the throttle was CAUSING the delay,    │          │
│  │           even though rate was well under the limit.           │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  LESSONS:                                                                    │
│  • SNS throttlePolicy is poorly documented and buggy                         │
│  • Low-volume topics + rate limits = unexpected latency                      │
│  • CloudWatch metrics showed SUCCESS — no indication of delay                │
│  • Enable CloudWatch DELIVERY LOGS (not just metrics) to see timing          │
│  • Monitor end-to-end latency, not just delivery success/failure             │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Incident 2: Twitter's Notification Meltdown

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Twitter/X Notification Delivery Failure (2024)                    │
│                                                                              │
│  Problem: Notifications lagging ~10 minutes, 20% messages failing            │
│                                                                              │
│  Root Cause: "One-topic-to-rule-them-all" anti-pattern                       │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │                                                                │          │
│  │  ALL notification types → [Single SNS Topic] → Subscribers    │          │
│  │                                                                │          │
│  │  Likes, retweets, DMs, follows, mentions, trending alerts     │          │
│  │  ALL going through ONE topic.                                  │          │
│  │                                                                │          │
│  │  At scale: hit throughput ceiling                              │          │
│  │  Result: throttling, delayed delivery, message drops           │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  Fix: Type-scoped topics + filtering + smarter scaling                       │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  [SNS: engagement-events] → Like, Retweet subscribers         │          │
│  │  [SNS: social-events]     → Follow, Mention subscribers       │          │
│  │  [SNS: dm-events]         → DM notification subscribers       │          │
│  │  [SNS: system-events]     → Trending, algorithmic alerts      │          │
│  │                                                                │          │
│  │  Each topic scaled independently                               │          │
│  │  Filtering within each topic for further granularity           │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  Result: Latency dropped to ~500ms, delivery improved to ~98%                │
│                                                                              │
│  LESSON: One mega-topic is an anti-pattern at scale. Shard topics            │
│  by domain/event-type. Same principle as database sharding.                  │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Incident 3: Silent Failures from Misconfigured Policies

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Production Fan-Out Silently Dropping Messages                     │
│                                                                              │
│  Scenario: Payroll system using SNS → SQS fan-out                            │
│                                                                              │
│  What happened:                                                              │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  1. Developer created new SQS queue for payroll processing    │          │
│  │  2. Subscribed queue to SNS topic                              │          │
│  │  3. FORGOT to add SQS resource policy allowing SNS to send    │          │
│  │  4. SNS attempted delivery → SQS rejected → SNS counted as    │          │
│  │     "client-side error" → NO RETRIES for client errors         │          │
│  │  5. Messages silently dropped for 3 weeks                     │          │
│  │  6. Discovered only when employees reported missing paychecks  │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  Why it was silent:                                                          │
│  • NumberOfNotificationsFailed metric was incrementing                        │
│  • But NO CloudWatch alarm was set on this metric                            │
│  • SNS delivery logs were not enabled                                        │
│  • No DLQ was configured on the subscription                                 │
│                                                                              │
│  FIX CHECKLIST (mandatory for production):                                   │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  □ SQS queue policy grants sns:SendMessage for the topic ARN  │          │
│  │  □ CloudWatch alarm on NumberOfNotificationsFailed > 0         │          │
│  │  □ DLQ configured on every subscription                       │          │
│  │  □ CloudWatch delivery logging enabled on the topic            │          │
│  │  □ End-to-end health check: publish test message, verify       │          │
│  │    delivery to all subscribers                                 │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Incident 4: Retry Storm Bankrupting the Account

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Retry Storm Causing Cost Explosion                                │
│                                                                              │
│  Setup: SNS → Lambda (direct, no SQS buffer)                                │
│                                                                              │
│  What happened:                                                              │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  1. Downstream API the Lambda called went down (503 errors)   │          │
│  │  2. Lambda threw error → SNS retried (100,015 times!)         │          │
│  │  3. Each retry = new Lambda invocation = new cost              │          │
│  │  4. 10,000 messages × 100K retries = 1 BILLION Lambda calls   │          │
│  │  5. Monthly bill spiked from $200 to $15,000                  │          │
│  │  6. Lambda concurrency exhausted → OTHER functions affected    │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  Root cause: No circuit breaker between SNS and Lambda                       │
│                                                                              │
│  Fix:                                                                        │
│  • Use SNS → SQS → Lambda (SQS acts as buffer and circuit breaker)          │
│  • Set maxReceiveCount on SQS (e.g., 5 retries then DLQ)                    │
│  • Set Lambda reserved concurrency to prevent blast radius                   │
│  • Set Lambda max retry attempts to 2 (for async invocation)                 │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 13. When NOT to Use SNS

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        WHEN SNS IS THE WRONG CHOICE                          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────┐                │
│  │  NEED                           │  USE INSTEAD           │                │
│  ├──────────────────────────────────┼────────────────────────┤                │
│  │  Message replay / reprocessing  │  Kafka (MSK) or        │                │
│  │  from arbitrary point in time   │  Kinesis Data Streams   │                │
│  │                                  │                        │                │
│  │  Messages >256 KB               │  SQS + S3 claim-check, │                │
│  │  (video, images, large docs)    │  or Kafka               │                │
│  │                                  │                        │                │
│  │  Strict message ordering        │  SQS FIFO (simpler) or │                │
│  │  without fan-out                │  Kafka (higher scale)   │                │
│  │                                  │                        │                │
│  │  Long-running work queues       │  SQS (built for this)  │                │
│  │  with visibility timeout        │                        │                │
│  │                                  │                        │                │
│  │  Complex content-based routing  │  EventBridge (50+       │                │
│  │  with transforms                │  target types, rules)   │                │
│  │                                  │                        │                │
│  │  Real-time bidirectional        │  WebSockets via         │                │
│  │  communication                  │  API Gateway / AppSync  │                │
│  │                                  │                        │                │
│  │  >30K publishes/second in       │  Kafka or Kinesis       │                │
│  │  a single region                │                        │                │
│  │                                  │                        │                │
│  │  Event sourcing (immutable      │  Kafka (designed for    │                │
│  │  log of all events)             │  this exact pattern)    │                │
│  │                                  │                        │                │
│  │  Multiple consumer groups       │  Kafka (consumer groups │                │
│  │  reading at different speeds    │  with independent       │                │
│  │                                  │  offsets)               │                │
│  └──────────────────────────────────┴────────────────────────┘                │
│                                                                              │
│  RULE OF THUMB:                                                              │
│  SNS = "I want to BROADCAST and FORGET"                                      │
│  SQS = "I want to BUFFER and PROCESS"                                        │
│  Kafka = "I want to STORE and REPLAY"                                        │
│  EventBridge = "I want to ROUTE by CONTENT"                                  │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 14. Anti-Patterns That Kill SNS

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     SNS ANTI-PATTERNS — LEARN FROM OTHERS' PAIN              │
│                                                                              │
│  ANTI-PATTERN 1: THE MEGA TOPIC                                              │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ One topic for ALL events in the system                      │          │
│  │  ✗ 50 subscribers, each filtering for 1-2 event types          │          │
│  │  ✗ Throughput ceiling, noisy-neighbor between event types       │          │
│  │                                                                │          │
│  │  ✓ FIX: Domain-scoped topics (order-events, user-events,      │          │
│  │    payment-events). 5-10 topics better than 1 overloaded one.  │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ANTI-PATTERN 2: SNS → LAMBDA WITHOUT SQS BUFFER                            │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ SNS directly invokes Lambda                                 │          │
│  │  ✗ Lambda fails → SNS retries 100K+ times → cost explosion     │          │
│  │  ✗ No backpressure, no rate control                            │          │
│  │                                                                │          │
│  │  ✓ FIX: SNS → SQS → Lambda. Always. No exceptions in prod.    │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ANTI-PATTERN 3: NO DLQ                                                      │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ Subscription has no dead-letter queue                       │          │
│  │  ✗ After retries exhaust → message gone forever                │          │
│  │  ✗ No way to investigate or replay failed messages             │          │
│  │                                                                │          │
│  │  ✓ FIX: Every subscription gets a DLQ. Every DLQ gets a       │          │
│  │    CloudWatch alarm. Non-negotiable.                           │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ANTI-PATTERN 4: NO IDEMPOTENCY IN CONSUMERS                                │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ Standard SNS = at-least-once delivery = DUPLICATES HAPPEN   │          │
│  │  ✗ Consumer processes duplicate → double charge, double email   │          │
│  │                                                                │          │
│  │  ✓ FIX: Use idempotency keys (MessageId or business key).     │          │
│  │    Store processed IDs in DynamoDB/Redis with TTL.             │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ANTI-PATTERN 5: OVERSIZED PAYLOADS                                          │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ Stuffing entire object (image metadata, full document)      │          │
│  │    into SNS message. Hits 256 KB limit → publish fails.        │          │
│  │                                                                │          │
│  │  ✓ FIX: Claim-check pattern. Store payload in S3, publish     │          │
│  │    only { "s3_bucket": "...", "s3_key": "..." } in SNS.       │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ANTI-PATTERN 6: IGNORING FILTER POLICY PROPAGATION DELAY                    │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ Update filter policy → immediately publish → old filter     │          │
│  │    still active → messages routed incorrectly                  │          │
│  │                                                                │          │
│  │  ✓ FIX: Filter policies take up to 15 minutes to propagate.   │          │
│  │    Build in a buffer period after filter updates.              │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ANTI-PATTERN 7: NOT VERIFYING SNS MESSAGE SIGNATURES                        │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ HTTP subscriber accepts any POST that looks like SNS        │          │
│  │  ✗ Attacker spoofs SNS messages to trigger actions             │          │
│  │                                                                │          │
│  │  ✓ FIX: Always verify SignatureVersion + Signature +           │          │
│  │    SigningCertURL. AWS SDKs provide helper methods.            │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ANTI-PATTERN 8: USING SNS FOR WORK QUEUES                                   │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  ✗ Using SNS to distribute work items to a single consumer     │          │
│  │  ✗ SNS pushes to all subscribers — can't do competing consumers│          │
│  │                                                                │          │
│  │  ✓ FIX: Use SQS for work queues. Multiple consumers poll      │          │
│  │    from the same queue = competing consumer pattern.           │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 15. Limits, Quotas & Numbers to Know

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                    SNS QUOTAS — NUMBERS FOR THE INTERVIEW                       │
│                                                                                │
│  QUOTA                                 VALUE                 ADJUSTABLE?       │
│  ──────────────────────────────────────────────────────────────────────────    │
│  Message size (body + attributes)      256 KB                No (hard limit)  │
│  Topics per account per region         100,000               Yes              │
│  Subscriptions per topic               12,500,000            Yes              │
│  Message attributes per message        10                    No               │
│  Filter policy keys                    5 per policy          No               │
│  Filter policy combinations            150 max               No               │
│  Filter policy size                    256 KB                No               │
│                                                                                │
│  THROUGHPUT — STANDARD TOPICS                                                  │
│  ──────────────────────────────────────────────────────────────────────────    │
│  Publishes (us-east-1)                 30,000 msg/s          Yes              │
│  Publishes (other regions)             9,000 msg/s           Yes              │
│  PublishBatch: up to 10 msgs/call      Same quota applies    —               │
│                                                                                │
│  THROUGHPUT — FIFO TOPICS                                                      │
│  ──────────────────────────────────────────────────────────────────────────    │
│  Per message group                     300 msg/s             No               │
│  Per topic (standard mode)             3,000 msg/s           No               │
│  Per account (high-throughput mode)    30,000 msg/s          Yes              │
│                                                                                │
│  DEDUPLICATION                                                                 │
│  ──────────────────────────────────────────────────────────────────────────    │
│  Dedup window (FIFO)                   5 minutes             No               │
│                                                                                │
│  COSTS (us-east-1, approximate)                                                │
│  ──────────────────────────────────────────────────────────────────────────    │
│  First 1M requests/month               Free                  —               │
│  Publishes (Standard)                  $0.50 per 1M          —               │
│  Deliveries to SQS/Lambda/Firehose     Free                  —               │
│  Deliveries to HTTP/S                  $0.06 per 100K        —               │
│  Deliveries to Email                   $2.00 per 100K        —               │
│  Deliveries to SMS                     $0.00645+ per msg     Varies by country│
│  FIFO publishes                        $0.30 per 1M          —               │
│                                                                                │
│  ⚠ KEY COST INSIGHT: Delivering to SQS, Lambda, Firehose is FREE.            │
│  This makes SNS → SQS fan-out incredibly cost-effective.                       │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Interview Questions — Medium

### Q1: Explain the SNS fan-out pattern and why you'd use SNS + SQS together

```
ANSWER FRAMEWORK:

1. Fan-out: One message published to SNS, delivered to ALL subscribers in parallel.
   Publisher is fully decoupled from consumers.

2. Why not SNS alone:
   • No persistence — if subscriber is down, message is lost (after retries)
   • No backpressure — SNS pushes, can't slow down
   • No rate control for consumers

3. Why SNS + SQS:
   • SQS provides DURABILITY (14-day retention)
   • SQS provides BACKPRESSURE (consumer polls at own speed)
   • Each consumer gets own queue (independent scaling)
   • Queue-level DLQ catches poison messages

4. Draw the diagram:
   Publisher → [SNS Topic] → SQS Queue A → Consumer A
                           → SQS Queue B → Consumer B
                           → SQS Queue C → Consumer C

5. Real example: Order placed → inventory, billing, shipping, analytics
   all process independently. One failing doesn't block others.
```

### Q2: How does SNS message filtering work? When would you use attribute-based vs. payload-based filtering?

```
ANSWER FRAMEWORK:

1. Without filtering: all subscribers get all messages → waste.
   With filtering: each subscription has a filter policy (JSON).
   SNS evaluates message against filter before delivery.

2. Two scopes:
   Attribute-based (default): matches on message attributes (metadata)
   Payload-based: matches on message body (JSON content)

3. When to use which:
   Attribute-based: publisher controls attributes, fast evaluation
   Payload-based: AWS service events (S3, CloudWatch) that don't set
   attributes, or when you can't modify publisher code

4. Operators: exact match, anything-but, numeric range, prefix, suffix, exists

5. Gotcha: cross-key logic is AND, within-key is OR
   {"color": ["red","blue"], "size": [{"numeric":[">",10]}]}
   = (red OR blue) AND (size > 10)

6. Limits: 5 keys max, 150 combinations, 15-minute propagation delay
```

### Q3: What happens when SNS can't deliver a message? Walk through the retry flow.

```
ANSWER FRAMEWORK:

1. Retry phases:
   Immediate → Pre-backoff → Exponential backoff → Post-backoff

2. Endpoint-specific behavior:
   AWS-managed (SQS, Lambda): 100,015 retries over 23 days
   Customer-managed (HTTP):   50 retries over 6 hours

3. After all retries exhausted:
   DLQ configured → message goes to DLQ (SQS queue)
   No DLQ → MESSAGE LOST PERMANENTLY

4. Client-side errors (wrong permissions, deleted endpoint):
   NO retries. Immediate DLQ or loss.

5. Best practice: ALWAYS configure DLQ + CloudWatch alarm on DLQ depth
```

### Q4: Standard vs. FIFO SNS topics — when would you use each?

```
ANSWER FRAMEWORK:

1. Standard:
   • Best-effort ordering (no guarantee)
   • At-least-once delivery (duplicates possible)
   • 30K msg/s throughput
   • All subscriber types (SQS, Lambda, HTTP, SMS, Email, etc.)
   • Use for: notifications, alerts, general event broadcast

2. FIFO:
   • Strict ordering within message groups
   • Exactly-once delivery (with conditions)
   • 300 msg/s per group, 3K per topic (30K with high-throughput mode)
   • ONLY SQS FIFO subscribers
   • Use for: financial transactions, order processing, state machines

3. FIFO gotchas:
   • Must end in .fifo
   • Content-based dedup excludes attributes from hash
   • Filtering downgrades exactly-once to at-most-once
   • Single message group = serialization bottleneck
```

### Q5: Design a notification system that sends email, SMS, and push notifications

```
ANSWER FRAMEWORK:

1. Requirements: multi-channel, at-least-once, handle failures per channel

2. Architecture:
   Event trigger → Notification Service → SNS topic
   SNS → SQS per channel (email-queue, sms-queue, push-queue)
   Each SQS → dedicated Lambda/worker
   Workers call SES (email), Twilio/SNS-SMS (SMS), FCM/APNs (push)

3. Why SNS + SQS per channel (not SNS direct):
   • Each channel has different failure modes and retry needs
   • SMS has rate limits, email has bounce handling
   • Independent scaling per channel

4. DLQ per channel for forensics

5. State tracking: DB table with notification_id, channel, status
   Scheduler re-processes PENDING records (Razorpay pattern)

6. Advanced: priority queues (OTP > marketing), rate limiting per user
```

### Q6: How would you handle messages larger than 256 KB in SNS?

```
ANSWER FRAMEWORK:

1. SNS hard limit: 256 KB per message. Cannot be increased.

2. Solution: Claim-check pattern
   Publisher → Store large payload in S3 → Publish S3 reference to SNS
   SNS message: {"s3_bucket": "data-bucket", "s3_key": "orders/large-123.json"}
   Consumer → Read SNS message → Fetch from S3 → Process

3. AWS provides Extended Client Library for SQS (Java).
   Similar pattern can be implemented for SNS.

4. Alternatives:
   • If you need large messages natively → Kafka (1MB default, configurable)
   • If always large → consider bypassing SNS, use direct S3 events
```

### Q7: Explain cross-account SNS subscriptions. How do you set them up securely?

```
ANSWER FRAMEWORK:

1. Use case: Team A publishes events, Team B (different AWS account) subscribes

2. Setup:
   Account A (publisher): Add SNS topic policy allowing Account B to subscribe
   Account B (subscriber): Create SQS queue with policy allowing SNS to send
   Either account creates the subscription (depends on policy)

3. Security:
   • Topic policy: restrict to specific account IDs (not Principal: *)
   • Use aws:SourceArn condition to lock down to specific topic
   • Enable SSE with KMS (cross-account key policy needed)
   • VPC endpoints if traffic must stay off public internet

4. Monitoring: Account A monitors publish metrics,
   Account B monitors delivery + DLQ
```

---

## 17. Interview Questions — Hard

### Q8: Design an event-driven architecture for a ride-sharing app using SNS

```
ANSWER FRAMEWORK:

1. Events: ride_requested, driver_assigned, ride_started, ride_completed,
   payment_processed, rating_submitted

2. Topic design (domain-scoped):
   ride-lifecycle-events: ride_requested, driver_assigned, ride_started, completed
   payment-events: payment_processed, payment_failed, refund_issued
   user-events: rating_submitted, driver_status_changed

3. Subscribers per topic:
   ride-lifecycle-events:
   → SQS: driver-matching-queue (Filter: ride_requested)
   → SQS: eta-calculation-queue (Filter: driver_assigned)
   → Lambda: push-notification (Filter: driver_assigned, ride_started)
   → SQS: analytics-queue (no filter, gets everything)

   payment-events:
   → SQS: receipt-generation-queue
   → Lambda: fraud-detection (Filter: amount >= 200)
   → SQS: driver-payout-queue (Filter: ride_completed)

4. Why not one topic:
   Different SLAs (payment must not be delayed by analytics load)
   Different teams own different topics
   Independent throughput scaling

5. FIFO consideration:
   Payment events → FIFO topic (ordered by ride_id as MessageGroupId)
   Ride events → Standard (eventual consistency acceptable)

6. Failure handling:
   DLQ per subscription
   Payment DLQ → high-priority alarm → manual review
   Analytics DLQ → low-priority, replay later

7. Scale math:
   1M rides/day = ~12 rides/sec
   6 events per ride = ~72 events/sec
   Well within SNS standard limits (30K msg/s)
   At Uber scale (25M rides/day): 1,800 events/sec → still fine for SNS
```

### Q9: You're seeing duplicate messages in your SNS consumers. Root-cause analysis and fix?

```
ANSWER FRAMEWORK:

1. Why duplicates happen in Standard SNS:
   • Standard topics guarantee at-least-once (not exactly-once)
   • SNS stores messages across multiple AZs
   • If ACK is lost due to network issue → SNS re-delivers
   • Publisher may retry failed Publish call → SNS sees as new message

2. Root-cause investigation:
   • Check MessageId in consumer logs — same ID = SNS retry,
     different ID = publisher retry
   • Check CloudWatch NumberOfMessagesPublished vs consumer count
   • Enable delivery logs to see delivery attempts per MessageId

3. Fixes:

   For SNS-level dedup (FIFO only):
   • Switch to FIFO topic + FIFO SQS queues
   • Use MessageDeduplicationId or content-based dedup
   • 5-minute dedup window

   For consumer-level dedup (Standard topics):
   • Idempotency key: use MessageId or business key (e.g., order_id)
   • Store processed keys in:
     - DynamoDB (with TTL for cleanup)
     - Redis (SETNX with expiration)
     - Database unique constraint
   • Check before processing: IF key exists → skip, ELSE process + store key

   Architecture:
   Consumer receives message
     → Extract idempotency key
     → DynamoDB ConditionalPut (PutItem with ConditionExpression)
     → If ConditionalCheckFailed → already processed, skip
     → Else → process message

4. Tradeoff:
   FIFO dedup: 300 msg/s per group (lower throughput)
   Consumer dedup: more code, needs external store, but higher throughput
```

### Q10: How would you migrate from a single SNS topic handling 50M events/day to a sharded architecture without downtime?

```
ANSWER FRAMEWORK:

1. Current state: One "mega-topic" with 30+ subscribers
   50M events/day = ~580 events/sec (within limits but risky)

2. Target: Multiple domain-scoped topics

3. Migration strategy (zero downtime):

   Phase 1: Dual-write
   ┌─────────────────────────────────────────────────────────────┐
   │  Publisher writes to BOTH old topic AND new topic(s)        │
   │  Consumers still subscribed to old topic only               │
   │  New topics accumulate messages but nobody reads yet        │
   └─────────────────────────────────────────────────────────────┘

   Phase 2: Double-subscribe
   ┌─────────────────────────────────────────────────────────────┐
   │  Add consumer subscriptions to new topics (via SQS queues)  │
   │  Consumers now get messages from BOTH old and new           │
   │  Consumer idempotency prevents double processing            │
   │  Monitor: verify new topic delivery matches old topic       │
   └─────────────────────────────────────────────────────────────┘

   Phase 3: Cut-over consumers
   ┌─────────────────────────────────────────────────────────────┐
   │  Remove subscriptions from old topic (one consumer at time) │
   │  Verify each consumer works correctly from new topic alone  │
   │  Rollback: re-subscribe to old topic if issues              │
   └─────────────────────────────────────────────────────────────┘

   Phase 4: Stop dual-write
   ┌─────────────────────────────────────────────────────────────┐
   │  Publisher stops publishing to old topic                    │
   │  Delete old topic after grace period                        │
   └─────────────────────────────────────────────────────────────┘

4. Key requirement: Consumer IDEMPOTENCY (will see duplicates during migration)
5. Monitoring: compare message counts, latencies, error rates between old and new
6. Rollback plan at every phase
```

### Q11: Design a system where SNS delivery order matters but you can't use FIFO topics (because you need HTTP subscribers)

```
ANSWER FRAMEWORK:

1. Problem: FIFO topics ONLY support SQS FIFO queues as subscribers.
   You need HTTP webhook delivery with ordering guarantees.

2. Solution: Application-level ordering

   Architecture:
   Publisher → SNS FIFO topic → SQS FIFO queue → Ordering Lambda → HTTP endpoint

   OR:
   Publisher → SNS Standard → SQS Standard → Consumer with ordering logic

3. Option A: SNS FIFO → SQS FIFO → Lambda → HTTP
   • FIFO guarantees order up to Lambda
   • Lambda calls HTTP endpoint synchronously
   • If HTTP fails → message stays in FIFO queue (ordered retry)
   • Lambda concurrency = 1 per message group (preserves order)

4. Option B: Application-level sequencing
   • Publisher adds sequence_number to message attributes
   • Consumer stores received messages in buffer
   • Consumer only processes message N after N-1 is confirmed
   • Out-of-order messages held in buffer until gap fills
   • Timeout for missing messages (request retransmit or alert)

5. Option C: Step Functions with ordered states
   • Publish to SNS → trigger Step Function
   • Step Function guarantees execution order within workflow
   • Each state makes the HTTP call and waits for response

6. Tradeoff analysis:
   Option A: Simple, limited by Lambda concurrency per group
   Option B: Complex, but works with any subscriber type
   Option C: Cost per execution, limited throughput
```

### Q12: Your SNS topic has 5,000 SQS subscribers. A critical message must reach all 5,000 within 2 seconds. Is this feasible? How would you verify?

```
ANSWER FRAMEWORK:

1. Can SNS deliver to 5,000 subscribers within 2 seconds?
   • SNS fans out in PARALLEL, not sequentially
   • AWS delivers to SQS subscribers as internal API calls
   • SQS is an AWS-managed endpoint → highly reliable, fast delivery
   • Typical SNS → SQS latency: 10-100ms

2. Feasibility analysis:
   • 5,000 parallel SQS deliveries = ~5K API calls
   • SNS is designed for millions of subscribers per topic (12.5M limit)
   • Internal AWS network, no public internet hop
   • 2-second window: YES, this is feasible for SQS subscribers

3. Verification approach:
   • Publish test message with precise timestamp in body
   • Each SQS queue has a Lambda that records:
     {message_id, publish_time, receive_time, queue_name}
   • Calculate delivery latency per queue
   • Plot P50, P95, P99 latency distribution
   • Check for outliers (queues with consistently high latency)

4. What could go wrong:
   • Account-level SQS throughput limits (unlikely with standard queues)
   • Cross-region subscribers add 50-150ms latency
   • SNS publishing throttle if account is near 30K msg/s limit
   • KMS decryption latency if SSE enabled (adds ~5-20ms per delivery)

5. If 2 seconds is not met:
   • Check if encryption is adding latency → use AWS-managed key
   • Ensure all queues are in same region as topic
   • Request throughput quota increase
   • Consider batched verification (not all 5K simultaneously)
```

### Q13: How would you implement exactly-once processing in a Standard SNS topic setup?

```
ANSWER FRAMEWORK:

1. Standard SNS CANNOT guarantee exactly-once at the messaging layer.
   At-least-once = duplicates WILL happen.

2. Solution: Exactly-once at the APPLICATION layer.

3. Implementation:

   Consumer-side idempotency:

   ┌─────────────────────────────────────────────────────────────────┐
   │  SNS → SQS → Lambda Consumer                                   │
   │                                                                 │
   │  function handler(event):                                       │
   │    for record in event.Records:                                 │
   │      message_id = record.messageId                              │
   │      # or use business key: order_id from message body          │
   │                                                                 │
   │      # Atomic check-and-set in DynamoDB                         │
   │      try:                                                       │
   │        dynamodb.put_item(                                       │
   │          Table = "processed_messages",                          │
   │          Item = {id: message_id, ttl: now + 24hrs},             │
   │          ConditionExpression = "attribute_not_exists(id)"       │
   │        )                                                        │
   │      except ConditionalCheckFailedException:                    │
   │        log("Duplicate, skipping")                               │
   │        continue  # skip duplicate                               │
   │                                                                 │
   │      # Not a duplicate — process                                │
   │      process_message(record)                                    │
   └─────────────────────────────────────────────────────────────────┘

4. Edge cases:
   • What if process succeeds but DynamoDB write fails?
     → Message reprocessed on next attempt (safe if process is idempotent)
   • What if DynamoDB write succeeds but process fails?
     → Message marked as processed but wasn't → need compensation
     → Solution: process FIRST, then mark (reversed order)
     → Or: use DynamoDB transaction that writes BOTH result + dedup key

5. Alternative: Use SQS message dedup ID if using FIFO
   But FIFO only deduplicates within 5-minute window

6. At massive scale: Redis SETNX (faster than DynamoDB for hot-path dedup)
   Trade-off: Redis is not durable by default (acceptable for dedup cache)
```

### Q14: Design a multi-tenant notification system where tenants have different delivery SLAs

```
ANSWER FRAMEWORK:

1. Requirements:
   Enterprise tenants: <500ms delivery, 99.99% SLA
   Standard tenants: <5s delivery, 99.9% SLA
   Free tenants: best-effort, rate-limited

2. Architecture:

   API Gateway → Notification Service (determines tenant tier)
                        │
            ┌───────────┼──────────────┐
            ▼           ▼              ▼
   [SNS: enterprise] [SNS: standard] [SNS: free]
        │                  │              │
        ▼                  ▼              ▼
   SQS (0 delay,     SQS (normal)    SQS (with delay
   high concurrency)                  queue, throttled)
        │                  │              │
        ▼                  ▼              ▼
   Lambda (reserved    Lambda           Lambda (low
   concurrency: 500)  (default)        concurrency: 10)

3. Why separate topics per tier:
   • Prevents free-tier traffic from impacting enterprise SLA
   • Independent throughput quotas
   • Different DLQ monitoring thresholds
   • Different retry policies (enterprise gets more aggressive retries)

4. Advanced: Priority within enterprise tier
   • Use SQS message priority (not native — use two queues:
     high-priority-queue, normal-priority-queue)
   • Lambda processes high-priority first

5. Monitoring per tier:
   • Enterprise: alarm if P99 > 500ms
   • Standard: alarm if P99 > 5s
   • Free: alarm only on complete failure

6. Cost allocation: tag topics per tenant, use AWS Cost Explorer
```

### Q15: Your SNS + SQS architecture is experiencing "message ordering anomalies" in a financial system. Debug and fix.

```
ANSWER FRAMEWORK:

1. Symptoms: Payment events processed out of order
   Example: "payment_confirmed" processed before "payment_initiated"

2. Root causes (check in order):

   a. Standard topic + Standard SQS (most likely):
      Neither guarantees ordering → messages delivered out of order
      Fix: Switch to FIFO topic → FIFO queue with MessageGroupId = payment_id

   b. Multiple Lambda workers processing same queue:
      SQS delivers to multiple Lambdas in parallel
      Even with FIFO, if maxBatchWindow = 0 and concurrency > 1,
      different message groups process in parallel (correct behavior)
      But SAME message group goes to same Lambda instance
      Fix: verify MessageGroupId is set correctly

   c. Publisher sending events out of order:
      If publisher is distributed (multiple instances), they may publish
      events in different order than business logic
      Fix: Add event_sequence_number to message, consumer reorders

   d. Retry-induced reordering:
      Message A fails → goes to retry
      Message B succeeds → processed first
      Message A succeeds on retry → processed second (out of order)
      Fix: FIFO queues block delivery of next message in group until
      current message is acknowledged

3. Architecture fix:

   Publisher → SNS FIFO topic → SQS FIFO queue → Lambda
   MessageGroupId = payment_id (ensures per-payment ordering)
   MessageDeduplicationId = payment_id + event_type + timestamp

4. Verification:
   Publish sequence: A(1), A(2), A(3) with same MessageGroupId
   Consumer logs must show: received A(1) before A(2) before A(3)
   Use SequenceNumber (assigned by SNS) to verify
```

---

## 18. Quick Reference Card

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                      SNS QUICK REFERENCE CARD                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  WHAT:    Fully managed pub/sub messaging service                            │
│  MODEL:   Push-based, one-to-many (fan-out)                                 │
│  TYPES:   Standard (best-effort) | FIFO (ordered, exactly-once)             │
│                                                                              │
│  MESSAGE: 256 KB max | 10 attributes | UUID MessageId                       │
│  TOPIC:   100K per region | 12.5M subscriptions/topic                       │
│                                                                              │
│  THROUGHPUT:                                                                 │
│    Standard: 30K msg/s (us-east-1), 9K (other regions)                      │
│    FIFO: 300/group, 3K/topic, 30K w/ high-throughput                        │
│                                                                              │
│  DELIVERY:                                                                   │
│    Standard: at-least-once (design for duplicates!)                          │
│    FIFO: exactly-once (to SQS FIFO, no filtering, no network issues)        │
│                                                                              │
│  RETRIES:                                                                    │
│    AWS-managed (SQS/Lambda): 100,015 over 23 days                           │
│    HTTP: 50 over 6 hours (customizable)                                     │
│                                                                              │
│  FILTERING:                                                                  │
│    Attribute-based (default): metadata matching                              │
│    Payload-based: JSON body matching                                         │
│    Operators: exact, anything-but, numeric, prefix, suffix, exists           │
│    Limits: 5 keys, 150 combinations, 15-min propagation                     │
│                                                                              │
│  SECURITY:                                                                   │
│    Auth: IAM SigV4                                                           │
│    Policies: IAM (identity) + Topic (resource)                               │
│    Encryption: TLS in transit, KMS SSE at rest                               │
│    Network: VPC endpoints (PrivateLink)                                      │
│    Verification: X.509 signed messages                                       │
│                                                                              │
│  GOLDEN RULES:                                                               │
│    1. ALWAYS use SNS → SQS → Lambda (not SNS → Lambda direct)              │
│    2. ALWAYS configure DLQ on every subscription                             │
│    3. ALWAYS design consumers to be idempotent                               │
│    4. Use domain-scoped topics (not one mega-topic)                          │
│    5. Verify message signatures on HTTP subscribers                          │
│    6. Use claim-check pattern for payloads >256 KB                           │
│    7. Monitor: NumberOfNotificationsFailed + DLQ depth                       │
│                                                                              │
│  COST TRICK: SNS → SQS/Lambda/Firehose delivery is FREE.                   │
│  Only pay for Publish requests.                                              │
│                                                                              │
│  MNEMONIC:                                                                   │
│    SNS = "Shout to Nobody Specific" (broadcast, don't care who listens)     │
│    SQS = "Stand in Queue, Single-file" (point-to-point, ordered work)       │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

> **Last Updated:** April 2026  
> **Next Review:** When AWS releases SNS features at re:Invent 2026  
> **Sources:** AWS SNS Developer Guide, AWS Compute Blog, AWS Whitepapers, cloudonaut.io, Alex Xu (ByteByteGo), Arpit Bhayani, Netflix Tech Blog, Twitter/X Engineering, DZone, SystemsArchitect.io
