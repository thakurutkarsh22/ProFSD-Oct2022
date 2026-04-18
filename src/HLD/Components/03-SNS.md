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

### SNS Internals — Full Anatomy Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│                  SNS INTERNAL ANATOMY — WHAT LIVES INSIDE THE BLACK BOX                  │
│                                                                                          │
│  This is what AWS DOESN'T show you. Reconstructed from AWS whitepapers,                 │
│  re:Invent talks (SRV302, SRV303), and observed behavior.                                │
│                                                                                          │
│  ════════════════════════════════════════════════════════════════════════════════════════  │
│                                                                                          │
│                          YOUR AWS ACCOUNT (us-east-1)                                    │
│                                                                                          │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐    │
│  │                                                                                  │    │
│  │    TOPIC: ride-lifecycle-events (Standard)                                       │    │
│  │    ARN: arn:aws:sns:us-east-1:123456789:ride-lifecycle-events                    │    │
│  │                                                                                  │    │
│  │    ┌──────────────────────────────────────────────────────────────────────────┐  │    │
│  │    │  TOPIC METADATA (stored in internal DynamoDB)                            │  │    │
│  │    │                                                                          │  │    │
│  │    │  ┌───────────────────┬──────────────────────────────────────────────┐   │  │    │
│  │    │  │ TopicArn           │ arn:aws:sns:us-east-1:123456:ride-lifecycle │   │  │    │
│  │    │  │ TopicType          │ Standard                                    │   │  │    │
│  │    │  │ Owner              │ 123456789012 (AWS Account ID)              │   │  │    │
│  │    │  │ Region             │ us-east-1                                   │   │  │    │
│  │    │  │ DisplayName        │ "Ride Lifecycle Events"                     │   │  │    │
│  │    │  │ KmsMasterKeyId     │ alias/sns-ride-key (SSE encryption)        │   │  │    │
│  │    │  │ AccessPolicy       │ {IAM resource policy JSON}                 │   │  │    │
│  │    │  │ DeliveryPolicy     │ {default retry config}                     │   │  │    │
│  │    │  │ SubscriptionCount  │ 4                                           │   │  │    │
│  │    │  │ CreatedAt          │ 2026-01-15T10:30:00Z                       │   │  │    │
│  │    │  └───────────────────┴──────────────────────────────────────────────┘   │  │    │
│  │    └──────────────────────────────────────────────────────────────────────────┘  │    │
│  │                                                                                  │    │
│  │    ┌──────────────────────────────────────────────────────────────────────────┐  │    │
│  │    │  SUBSCRIPTIONS (each is an independent delivery binding)                 │  │    │
│  │    │                                                                          │  │    │
│  │    │  Sub 1 ────────────────────────────────────────────────────────────────  │  │    │
│  │    │  │ SubscriptionArn  │ arn:aws:sns:...:ride-lifecycle:abc-123            │  │    │
│  │    │  │ Protocol         │ sqs                                               │  │    │
│  │    │  │ Endpoint         │ arn:aws:sqs:...:driver-matching-queue             │  │    │
│  │    │  │ FilterPolicy     │ {"event_type": ["ride_requested"]}               │  │    │
│  │    │  │ FilterScope      │ MessageAttributes (default)                       │  │    │
│  │    │  │ RawMsgDelivery   │ true                                              │  │    │
│  │    │  │ RedrivePolicy    │ {"deadLetterTargetArn": "...matching-dlq"}       │  │    │
│  │    │  │ Status           │ CONFIRMED                                         │  │    │
│  │    │  └─────────────────────────────────────────────────────────────────────  │  │    │
│  │    │                                                                          │  │    │
│  │    │  Sub 2 ────────────────────────────────────────────────────────────────  │  │    │
│  │    │  │ SubscriptionArn  │ arn:aws:sns:...:ride-lifecycle:def-456            │  │    │
│  │    │  │ Protocol         │ sqs                                               │  │    │
│  │    │  │ Endpoint         │ arn:aws:sqs:...:eta-calculation-queue             │  │    │
│  │    │  │ FilterPolicy     │ {"event_type": ["driver_assigned"]}              │  │    │
│  │    │  │ RedrivePolicy    │ {"deadLetterTargetArn": "...eta-dlq"}            │  │    │
│  │    │  │ Status           │ CONFIRMED                                         │  │    │
│  │    │  └─────────────────────────────────────────────────────────────────────  │  │    │
│  │    │                                                                          │  │    │
│  │    │  Sub 3 ────────────────────────────────────────────────────────────────  │  │    │
│  │    │  │ SubscriptionArn  │ arn:aws:sns:...:ride-lifecycle:ghi-789            │  │    │
│  │    │  │ Protocol         │ lambda                                            │  │    │
│  │    │  │ Endpoint         │ arn:aws:lambda:...:push-notification              │  │    │
│  │    │  │ FilterPolicy     │ {"event_type":["driver_assigned","ride_started"]}│  │    │
│  │    │  │ RedrivePolicy    │ {"deadLetterTargetArn": "...push-notif-dlq"}    │  │    │
│  │    │  │ Status           │ CONFIRMED                                         │  │    │
│  │    │  └─────────────────────────────────────────────────────────────────────  │  │    │
│  │    │                                                                          │  │    │
│  │    │  Sub 4 ────────────────────────────────────────────────────────────────  │  │    │
│  │    │  │ SubscriptionArn  │ arn:aws:sns:...:ride-lifecycle:jkl-012            │  │    │
│  │    │  │ Protocol         │ sqs                                               │  │    │
│  │    │  │ Endpoint         │ arn:aws:sqs:...:analytics-queue                   │  │    │
│  │    │  │ FilterPolicy     │ {} (empty = receives ALL messages)               │  │    │
│  │    │  │ RedrivePolicy    │ {"deadLetterTargetArn": "...analytics-dlq"}      │  │    │
│  │    │  │ Status           │ CONFIRMED                                         │  │    │
│  │    │  └─────────────────────────────────────────────────────────────────────  │  │    │
│  │    │                                                                          │  │    │
│  │    └──────────────────────────────────────────────────────────────────────────┘  │    │
│  │                                                                                  │    │
│  └──────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                          │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐    │
│  │                                                                                  │    │
│  │    TOPIC: payment-events.fifo (FIFO)                                             │    │
│  │    ARN: arn:aws:sns:us-east-1:123456789:payment-events.fifo                      │    │
│  │                                                                                  │    │
│  │    ┌──────────────────────────────────────────────────────────────────────────┐  │    │
│  │    │  FIFO-SPECIFIC INTERNALS (what Standard topics DON'T have)              │  │    │
│  │    │                                                                          │  │    │
│  │    │  ┌────────────────────────────────────────────────────────────────┐     │  │    │
│  │    │  │  MESSAGE GROUP REGISTRY                                        │     │  │    │
│  │    │  │                                                                │     │  │    │
│  │    │  │  A FIFO topic internally maintains LANES called                │     │  │    │
│  │    │  │  "Message Groups." Each group is independent.                  │     │  │    │
│  │    │  │                                                                │     │  │    │
│  │    │  │  ┌────────────────────────────────────────────────┐           │     │  │    │
│  │    │  │  │ MessageGroupId │ Status    │ Head Sequence     │           │     │  │    │
│  │    │  │  ├────────────────┼───────────┼───────────────────┤           │     │  │    │
│  │    │  │  │ "ride-A"       │ ACTIVE    │ seq-00000047      │           │     │  │    │
│  │    │  │  │ "ride-B"       │ ACTIVE    │ seq-00000048      │           │     │  │    │
│  │    │  │  │ "ride-C"       │ IDLE      │ seq-00000045      │           │     │  │    │
│  │    │  │  │ "ride-D"       │ ACTIVE    │ seq-00000049      │           │     │  │    │
│  │    │  │  │ ...            │ ...       │ ...               │           │     │  │    │
│  │    │  │  └────────────────┴───────────┴───────────────────┘           │     │  │    │
│  │    │  │                                                                │     │  │    │
│  │    │  │  • Each group gets its own SEQUENCE COUNTER                   │     │  │    │
│  │    │  │  • Messages within a group are delivered in sequence order    │     │  │    │
│  │    │  │  • Groups are INDEPENDENT — ride-A's delivery doesn't        │     │  │    │
│  │    │  │    block ride-B's delivery                                    │     │  │    │
│  │    │  │  • Max 300 messages/sec PER GROUP                            │     │  │    │
│  │    │  │  • Max 3,000 messages/sec PER TOPIC (across all groups)      │     │  │    │
│  │    │  └────────────────────────────────────────────────────────────────┘     │  │    │
│  │    │                                                                          │  │    │
│  │    │  ┌────────────────────────────────────────────────────────────────┐     │  │    │
│  │    │  │  DEDUPLICATION CACHE                                           │     │  │    │
│  │    │  │                                                                │     │  │    │
│  │    │  │  In-memory cache that stores MessageDeduplicationIds          │     │  │    │
│  │    │  │  for the LAST 5 MINUTES.                                      │     │  │    │
│  │    │  │                                                                │     │  │    │
│  │    │  │  ┌──────────────────────────────────┬─────────────────────┐  │     │  │    │
│  │    │  │  │ DeduplicationId                   │ Expires At          │  │     │  │    │
│  │    │  │  ├──────────────────────────────────┼─────────────────────┤  │     │  │    │
│  │    │  │  │ "pay-ride-A-v1"                  │ 2026-04-15T10:05:00 │  │     │  │    │
│  │    │  │  │ "pay-ride-B-v1"                  │ 2026-04-15T10:05:01 │  │     │  │    │
│  │    │  │  │ "pay-ride-C-v2"                  │ 2026-04-15T10:04:30 │  │     │  │    │
│  │    │  │  └──────────────────────────────────┴─────────────────────┘  │     │  │    │
│  │    │  │                                                                │     │  │    │
│  │    │  │  • Publish with existing dedup ID → 200 OK but NOT delivered │     │  │    │
│  │    │  │  • After 5 min → ID evicted → same ID treated as NEW         │     │  │    │
│  │    │  │  • Content-based dedup: SHA-256(body) used as dedup ID       │     │  │    │
│  │    │  └────────────────────────────────────────────────────────────────┘     │  │    │
│  │    │                                                                          │  │    │
│  │    └──────────────────────────────────────────────────────────────────────────┘  │    │
│  │                                                                                  │    │
│  └──────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                          │
│  ════════════════════════════════════════════════════════════════════════════════════════  │
│                                                                                          │
│                     AWS SNS SERVICE INTERNALS (MANAGED BY AWS)                            │
│                                                                                          │
│  ════════════════════════════════════════════════════════════════════════════════════════  │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│               WHAT HAPPENS INSIDE SNS WHEN YOU CALL Publish()                            │
│                                                                                          │
│  ┌────────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                                    │  │
│  │  LAYER 1: API FRONTEND FLEET                                                      │  │
│  │  ─────────────────────────────                                                    │  │
│  │                                                                                    │  │
│  │  Publisher calls: sns.publish(topicArn, message, attributes)                      │  │
│  │       │                                                                            │  │
│  │       ▼                                                                            │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐          │  │
│  │  │  REGIONAL ENDPOINT (sns.us-east-1.amazonaws.com)                    │          │  │
│  │  │                                                                     │          │  │
│  │  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐              │          │  │
│  │  │  │ API Host│  │ API Host│  │ API Host│  │ API Host│              │          │  │
│  │  │  │ (AZ-1a) │  │ (AZ-1b) │  │ (AZ-1c) │  │ (AZ-1d) │              │          │  │
│  │  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘              │          │  │
│  │  │       │            │            │            │                     │          │  │
│  │  │       └────────────┴─────┬──────┴────────────┘                     │          │  │
│  │  │                          │                                         │          │  │
│  │  │  Load balancer routes to any healthy host (stateless)              │          │  │
│  │  └──────────────────────────┼─────────────────────────────────────────┘          │  │
│  │                             │                                                    │  │
│  │       What happens on the API host:                                              │  │
│  │       ┌─────────────────────▼──────────────────────┐                             │  │
│  │       │ 1. AUTHENTICATE                             │                             │  │
│  │       │    Verify IAM SigV4 signature               │                             │  │
│  │       │    Check caller has sns:Publish permission  │                             │  │
│  │       │    Check topic resource policy allows caller│                             │  │
│  │       │                                             │                             │  │
│  │       │ 2. VALIDATE                                 │                             │  │
│  │       │    Message size ≤ 256 KB?                   │                             │  │
│  │       │    Attributes ≤ 10?                         │                             │  │
│  │       │    Attribute names valid?                    │                             │  │
│  │       │    If FIFO: MessageGroupId present?         │                             │  │
│  │       │    If FIFO: MessageDeduplicationId present  │                             │  │
│  │       │            OR content-based dedup enabled?  │                             │  │
│  │       │                                             │                             │  │
│  │       │ 3. ENCRYPT (if SSE enabled)                 │                             │  │
│  │       │    Encrypt message body with KMS key        │                             │  │
│  │       │                                             │                             │  │
│  │       │ 4. ASSIGN                                   │                             │  │
│  │       │    Generate unique MessageId (UUID)         │                             │  │
│  │       │    If FIFO: assign SequenceNumber           │                             │  │
│  │       │    (monotonically increasing per group)     │                             │  │
│  │       └─────────────────────┬───────────────────────┘                             │  │
│  │                             │                                                    │  │
│  │                             ▼                                                    │  │
│  └─────────────────────────────┼────────────────────────────────────────────────────┘  │
│                                │                                                      │
│  ┌─────────────────────────────▼────────────────────────────────────────────────────┐  │
│  │                                                                                    │  │
│  │  LAYER 2: METADATA STORE (Topic & Subscription Registry)                          │  │
│  │  ──────────────────────────────────────────────────────                            │  │
│  │                                                                                    │  │
│  │  Internal DynamoDB tables (managed by AWS, not visible to you):                   │  │
│  │                                                                                    │  │
│  │  ┌──────────────────────────────────────────────────────────────┐                │  │
│  │  │  TABLE: topics                                               │                │  │
│  │  │  PK: topic_arn                                               │                │  │
│  │  │  Stores: config, policies, encryption settings, type         │                │  │
│  │  └──────────────────────────────────────────────────────────────┘                │  │
│  │                                                                                    │  │
│  │  ┌──────────────────────────────────────────────────────────────┐                │  │
│  │  │  TABLE: subscriptions                                        │                │  │
│  │  │  PK: topic_arn    SK: subscription_arn                       │                │  │
│  │  │  Stores: protocol, endpoint, filter_policy, delivery_policy, │                │  │
│  │  │          redrive_policy (DLQ), raw_message_delivery, status  │                │  │
│  │  └──────────────────────────────────────────────────────────────┘                │  │
│  │                                                                                    │  │
│  │  ┌──────────────────────────────────────────────────────────────┐                │  │
│  │  │  TABLE: fifo_dedup_cache  (FIFO topics only)                 │                │  │
│  │  │  PK: topic_arn + dedup_id     TTL: 5 minutes                 │                │  │
│  │  │  Stores: dedup_id, timestamp, expiry                         │                │  │
│  │  └──────────────────────────────────────────────────────────────┘                │  │
│  │                                                                                    │  │
│  │  ┌──────────────────────────────────────────────────────────────┐                │  │
│  │  │  TABLE: fifo_sequence_counters  (FIFO topics only)           │                │  │
│  │  │  PK: topic_arn + message_group_id                            │                │  │
│  │  │  Stores: last_sequence_number (atomic increment)             │                │  │
│  │  └──────────────────────────────────────────────────────────────┘                │  │
│  │                                                                                    │  │
│  │  The API host queries this store to:                                              │  │
│  │  • Fetch subscription list for the target topic                                  │  │
│  │  • Load filter policy for each subscription                                      │  │
│  │  • Check FIFO dedup cache (is this a duplicate?)                                 │  │
│  │  • Get and increment sequence counter (FIFO only)                                │  │
│  │                                                                                    │  │
│  └─────────────────────────────┬────────────────────────────────────────────────────┘  │
│                                │                                                      │
│                                ▼                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                                   │  │
│  │  LAYER 3: FILTER ENGINE                                                          │  │
│  │  ─────────────────────────                                                       │  │
│  │                                                                                   │  │
│  │  For EACH subscription, evaluate its filter policy against the message:          │  │
│  │                                                                                   │  │
│  │  Incoming message attributes:                                                    │  │
│  │  { "event_type": "ride_requested", "city": "bangalore", "surge": 2.1 }          │  │
│  │                                                                                   │  │
│  │       │                                                                           │  │
│  │       ├──→ Sub 1 filter: {"event_type": ["ride_requested"]}                     │  │
│  │       │    event_type = "ride_requested" matches "ride_requested" ✓ PASS         │  │
│  │       │    → Add to delivery list                                                │  │
│  │       │                                                                           │  │
│  │       ├──→ Sub 2 filter: {"event_type": ["driver_assigned"]}                    │  │
│  │       │    event_type = "ride_requested" ≠ "driver_assigned" ✗ REJECT            │  │
│  │       │    → Skip (message never reaches this subscriber)                        │  │
│  │       │    → NOT a failure. Not counted in metrics. Just filtered out.           │  │
│  │       │                                                                           │  │
│  │       ├──→ Sub 3 filter: {"event_type": ["driver_assigned","ride_started"]}     │  │
│  │       │    event_type = "ride_requested" ∉ ["driver_assigned","ride_started"]    │  │
│  │       │    ✗ REJECT → Skip                                                       │  │
│  │       │                                                                           │  │
│  │       └──→ Sub 4 filter: {} (empty)                                              │  │
│  │            Empty filter = MATCH EVERYTHING ✓ PASS                                │  │
│  │            → Add to delivery list                                                │  │
│  │                                                                                   │  │
│  │  Result: delivery_list = [Sub 1 (driver-matching), Sub 4 (analytics)]           │  │
│  │                                                                                   │  │
│  │  ⚠ Filter evaluation is FREE — no extra charge per filtered-out message         │  │
│  │  ⚠ Filter changes take up to 15 minutes to propagate (cached internally)        │  │
│  │                                                                                   │  │
│  └─────────────────────────────┬───────────────────────────────────────────────────┘  │
│                                │                                                      │
│                                ▼                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                                   │  │
│  │  LAYER 4: DELIVERY ENGINE (Fan-Out Workers)                                      │  │
│  │  ─────────────────────────────────────────                                       │  │
│  │                                                                                   │  │
│  │  Takes the delivery_list and dispatches to each endpoint IN PARALLEL:            │  │
│  │                                                                                   │  │
│  │  delivery_list = [Sub 1 (SQS), Sub 4 (SQS)]                                    │  │
│  │                                                                                   │  │
│  │       ┌──────────────────────────────┐                                           │  │
│  │       │   DELIVERY THREAD POOL        │                                           │  │
│  │       │                              │                                           │  │
│  │       │   ┌──────────────────────┐   │     ┌────────────────────┐               │  │
│  │       │   │ Worker 1 (Sub 1)     │───┼────→│ SQS: driver-       │               │  │
│  │       │   │ Protocol: SQS       │   │     │ matching-queue     │               │  │
│  │       │   │ Action: sqs.send()  │   │     │                    │               │  │
│  │       │   │ Raw: true (no wrap) │   │     │ Message arrives    │               │  │
│  │       │   └──────────────────────┘   │     │ as raw body        │               │  │
│  │       │                              │     └────────────────────┘               │  │
│  │       │   ┌──────────────────────┐   │     ┌────────────────────┐               │  │
│  │       │   │ Worker 2 (Sub 4)     │───┼────→│ SQS: analytics-   │               │  │
│  │       │   │ Protocol: SQS       │   │     │ queue              │               │  │
│  │       │   │ Action: sqs.send()  │   │     │                    │               │  │
│  │       │   │ Raw: false (wrapped)│   │     │ Message arrives    │               │  │
│  │       │   └──────────────────────┘   │     │ in SNS JSON envelope│              │  │
│  │       │                              │     └────────────────────┘               │  │
│  │       └──────────────────────────────┘                                           │  │
│  │                                                                                   │  │
│  │  PROTOCOL-SPECIFIC ADAPTERS:                                                     │  │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐   │  │
│  │  │                                                                          │   │  │
│  │  │  ┌─────────────┐  SNS calls sqs:SendMessage on behalf of the topic     │   │  │
│  │  │  │  SQS Adapter │  Uses topic's IAM role. Message persisted in SQS.    │   │  │
│  │  │  │              │  Delivery: INSTANT (within same AWS region)           │   │  │
│  │  │  │              │  Cost: FREE (SNS → SQS delivery)                     │   │  │
│  │  │  └─────────────┘                                                        │   │  │
│  │  │                                                                          │   │  │
│  │  │  ┌─────────────┐  SNS calls lambda:Invoke asynchronously               │   │  │
│  │  │  │  Lambda      │  Lambda processes or fails (SNS retries 3x)          │   │  │
│  │  │  │  Adapter     │  On exhaust → route to subscription DLQ              │   │  │
│  │  │  │              │  Cost: FREE (SNS → Lambda delivery)                  │   │  │
│  │  │  └─────────────┘                                                        │   │  │
│  │  │                                                                          │   │  │
│  │  │  ┌─────────────┐  SNS makes HTTP POST to the endpoint URL              │   │  │
│  │  │  │  HTTP/S      │  Expects 2xx response. Else → retry with backoff.    │   │  │
│  │  │  │  Adapter     │  Retry policy: up to 100,085 attempts over 23 days   │   │  │
│  │  │  │              │  (default) or custom delivery policy.                │   │  │
│  │  │  │              │  Message includes X-Amz-Sns-* headers + signature    │   │  │
│  │  │  └─────────────┘                                                        │   │  │
│  │  │                                                                          │   │  │
│  │  │  ┌─────────────┐  SNS calls ses:SendEmail / ses:SendRawEmail           │   │  │
│  │  │  │  Email       │  Subject = topic display name                        │   │  │
│  │  │  │  Adapter     │  Body = message text                                 │   │  │
│  │  │  │              │  No retries (fire-and-forget for email)              │   │  │
│  │  │  └─────────────┘                                                        │   │  │
│  │  │                                                                          │   │  │
│  │  │  ┌─────────────┐  SNS calls Amazon SNS SMS service                     │   │  │
│  │  │  │  SMS Adapter │  Subject to spend limits and opt-out lists           │   │  │
│  │  │  │              │  Transactional vs promotional routing                │   │  │
│  │  │  └─────────────┘                                                        │   │  │
│  │  │                                                                          │   │  │
│  │  └──────────────────────────────────────────────────────────────────────────┘   │  │
│  │                                                                                   │  │
│  └─────────────────────────────┬───────────────────────────────────────────────────┘  │
│                                │                                                      │
│                          (if delivery fails)                                          │
│                                │                                                      │
│                                ▼                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                                   │  │
│  │  LAYER 5: RETRY & DLQ ENGINE                                                    │  │
│  │  ────────────────────────────                                                    │  │
│  │                                                                                   │  │
│  │  When delivery to a subscriber FAILS:                                            │  │
│  │                                                                                   │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐         │  │
│  │  │  Delivery fails (HTTP 5xx, Lambda error, SQS permission denied)    │         │  │
│  │  │       │                                                             │         │  │
│  │  │       ▼                                                             │         │  │
│  │  │  ┌─────────────────────────────┐                                   │         │  │
│  │  │  │ RETRY SCHEDULER             │                                   │         │  │
│  │  │  │                             │                                   │         │  │
│  │  │  │ Phase 1: Immediate retries  │  3 retries, no delay             │         │  │
│  │  │  │ Phase 2: Pre-backoff        │  2 retries, 1s apart             │         │  │
│  │  │  │ Phase 3: Backoff            │  10 retries, exponential          │         │  │
│  │  │  │          (linear/exponential│  20s → 40s → ... → 20 min        │         │  │
│  │  │  │           /geometric)       │  (customizable per subscription)  │         │  │
│  │  │  │ Phase 4: Post-backoff       │  100,000 retries, 20 min each    │         │  │
│  │  │  │                             │  (total: ~23 days for HTTP)       │         │  │
│  │  │  └──────────────┬──────────────┘                                   │         │  │
│  │  │                 │                                                   │         │  │
│  │  │          (all retries exhausted)                                    │         │  │
│  │  │                 │                                                   │         │  │
│  │  │                 ▼                                                   │         │  │
│  │  │  ┌──────────────────────────────────┐                              │         │  │
│  │  │  │  DLQ configured?                 │                              │         │  │
│  │  │  │                                  │                              │         │  │
│  │  │  │  YES → Send to SQS DLQ          │  Message + failure metadata  │         │  │
│  │  │  │        (RedrivePolicy target)    │  preserved for investigation │         │  │
│  │  │  │                                  │                              │         │  │
│  │  │  │  NO  → MESSAGE DROPPED           │  ← SILENTLY LOST FOREVER    │         │  │
│  │  │  │        (only CloudWatch metric   │    This is why DLQs are     │         │  │
│  │  │  │         records the failure)     │    MANDATORY in production  │         │  │
│  │  │  └──────────────────────────────────┘                              │         │  │
│  │  └─────────────────────────────────────────────────────────────────────┘         │  │
│  │                                                                                   │  │
│  └─────────────────────────────┬───────────────────────────────────────────────────┘  │
│                                │                                                      │
│                                ▼                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                                   │  │
│  │  LAYER 6: METRICS & LOGGING                                                      │  │
│  │  ──────────────────────────                                                      │  │
│  │                                                                                   │  │
│  │  Throughout all layers, SNS emits:                                               │  │
│  │                                                                                   │  │
│  │  CloudWatch Metrics (automatic, always-on):                                      │  │
│  │  ┌───────────────────────────────────┬──────────────────────────────────────┐   │  │
│  │  │ Metric                            │ Meaning                              │   │  │
│  │  ├───────────────────────────────────┼──────────────────────────────────────┤   │  │
│  │  │ NumberOfMessagesPublished         │ Messages received by topic           │   │  │
│  │  │ PublishSize                       │ Size of published messages           │   │  │
│  │  │ NumberOfNotificationsDelivered    │ Successful deliveries to subscribers │   │  │
│  │  │ NumberOfNotificationsFailed       │ Failed deliveries (after all retries)│   │  │
│  │  │ NumberOfNotificationsFilteredOut  │ Messages that didn't match filter    │   │  │
│  │  │ SMSSuccessRate                    │ % of SMS messages delivered          │   │  │
│  │  └───────────────────────────────────┴──────────────────────────────────────┘   │  │
│  │                                                                                   │  │
│  │  CloudWatch Delivery Logs (opt-in, per protocol):                                │  │
│  │  ┌──────────────────────────────────────────────────────────────────────┐       │  │
│  │  │  Logs every delivery attempt with:                                   │       │  │
│  │  │  • providerResponse (what the endpoint returned)                     │       │  │
│  │  │  • dwellTimeMs (how long SNS held the message before delivery)      │       │  │
│  │  │  • statusCode (HTTP status or error code)                           │       │  │
│  │  │  • timestamp, messageId, subscriptionArn                            │       │  │
│  │  │                                                                      │       │  │
│  │  │  ⚠ dwellTimeMs is the ONLY way to detect internal delivery delays  │       │  │
│  │  │  (like the throttlePolicy 65-day incident — metrics showed SUCCESS  │       │  │
│  │  │   but dwellTimeMs would have revealed the 30+ min delay)            │       │  │
│  │  └──────────────────────────────────────────────────────────────────────┘       │  │
│  │                                                                                   │  │
│  │  CloudTrail (API audit):                                                         │  │
│  │  ┌──────────────────────────────────────────────────────────────────────┐       │  │
│  │  │  Logs management events: CreateTopic, Subscribe, SetTopicAttributes │       │  │
│  │  │  Does NOT log Publish calls by default (data event — opt-in)       │       │  │
│  │  │  Enable data events for compliance-heavy workloads                 │       │  │
│  │  └──────────────────────────────────────────────────────────────────────┘       │  │
│  │                                                                                   │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│          STANDARD TOPIC vs FIFO TOPIC — INTERNAL DIFFERENCES                             │
│                                                                                          │
│  ┌───────────────────────────────────┐  ┌───────────────────────────────────┐            │
│  │  STANDARD TOPIC                    │  │  FIFO TOPIC (.fifo suffix)        │            │
│  │                                    │  │                                    │            │
│  │  ┌─────────────────────────────┐  │  │  ┌─────────────────────────────┐  │            │
│  │  │ Message arrives             │  │  │  │ Message arrives             │  │            │
│  │  │         │                   │  │  │  │         │                   │  │            │
│  │  │         ▼                   │  │  │  │         ▼                   │  │            │
│  │  │  Validate + Assign          │  │  │  │  Validate + Assign          │  │            │
│  │  │  MessageId (UUID)           │  │  │  │  MessageId (UUID)           │  │            │
│  │  │         │                   │  │  │  │         │                   │  │            │
│  │  │         │                   │  │  │  │         ▼                   │  │            │
│  │  │         │                   │  │  │  │  ┌───────────────────┐     │  │            │
│  │  │         │   (no dedup)      │  │  │  │  │ DEDUP CHECK       │     │  │            │
│  │  │         │   (no ordering)   │  │  │  │  │ Is dedup_id in    │     │  │            │
│  │  │         │                   │  │  │  │  │ 5-min cache?      │     │  │            │
│  │  │         │                   │  │  │  │  │                   │     │  │            │
│  │  │         │                   │  │  │  │  │ YES → return 200  │     │  │            │
│  │  │         │                   │  │  │  │  │ (don't deliver)   │     │  │            │
│  │  │         │                   │  │  │  │  │                   │     │  │            │
│  │  │         │                   │  │  │  │  │ NO → continue     │     │  │            │
│  │  │         │                   │  │  │  │  └────────┬──────────┘     │  │            │
│  │  │         │                   │  │  │  │           │                │  │            │
│  │  │         │                   │  │  │  │           ▼                │  │            │
│  │  │         │                   │  │  │  │  ┌───────────────────┐     │  │            │
│  │  │         │                   │  │  │  │  │ SEQUENCE ASSIGN    │     │  │            │
│  │  │         │                   │  │  │  │  │ Atomic increment   │     │  │            │
│  │  │         │                   │  │  │  │  │ per MessageGroupId │     │  │            │
│  │  │         │                   │  │  │  │  │ seq-00000047 →     │     │  │            │
│  │  │         │                   │  │  │  │  │ seq-00000048       │     │  │            │
│  │  │         │                   │  │  │  │  └────────┬──────────┘     │  │            │
│  │  │         │                   │  │  │  │           │                │  │            │
│  │  │         ▼                   │  │  │  │           ▼                │  │            │
│  │  │  ┌───────────────────┐     │  │  │  │  ┌───────────────────┐     │  │            │
│  │  │  │ FILTER + DELIVER   │     │  │  │  │  │ FILTER + DELIVER   │     │  │            │
│  │  │  │                   │     │  │  │  │  │                   │     │  │            │
│  │  │  │ Best-effort order │     │  │  │  │  │ Strict order per  │     │  │            │
│  │  │  │ (no guarantee)    │     │  │  │  │  │ MessageGroupId    │     │  │            │
│  │  │  │                   │     │  │  │  │  │                   │     │  │            │
│  │  │  │ At-least-once     │     │  │  │  │  │ Exactly-once      │     │  │            │
│  │  │  │ (may duplicate)   │     │  │  │  │  │ (to FIFO SQS)     │     │  │            │
│  │  │  │                   │     │  │  │  │  │                   │     │  │            │
│  │  │  │ 30,000 msg/s      │     │  │  │  │  │ 300 msg/s/group   │     │  │            │
│  │  │  │                   │     │  │  │  │  │ 3,000 msg/s/topic │     │  │            │
│  │  │  └───────────────────┘     │  │  │  │  └───────────────────┘     │  │            │
│  │  │                            │  │  │  │                            │  │            │
│  │  └─────────────────────────────┘  │  │  └─────────────────────────────┘  │            │
│  │                                    │  │                                    │            │
│  │  INTERNAL COMPONENTS:              │  │  INTERNAL COMPONENTS:              │            │
│  │  • API Frontend     ✓             │  │  • API Frontend          ✓        │            │
│  │  • Metadata Store   ✓             │  │  • Metadata Store        ✓        │            │
│  │  • Filter Engine    ✓             │  │  • Filter Engine         ✓        │            │
│  │  • Delivery Engine  ✓             │  │  • Delivery Engine       ✓        │            │
│  │  • Retry/DLQ Engine ✓             │  │  • Retry/DLQ Engine      ✓        │            │
│  │  • Dedup Cache      ✗ (none)      │  │  • Dedup Cache           ✓ (5 min)│            │
│  │  • Sequence Counter ✗ (none)      │  │  • Sequence Counter      ✓        │            │
│  │  • Group Registry   ✗ (none)      │  │  • Group Registry        ✓        │            │
│  │                                    │  │                                    │            │
│  └───────────────────────────────────┘  └───────────────────────────────────┘            │
│                                                                                          │
│  FIFO has 3 EXTRA internal components that Standard doesn't have.                       │
│  These add overhead → that's why FIFO throughput is 10x lower.                          │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
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
│  CONDITION 1 EXPLAINED: Queue exists + correct permissions                   │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  If the SQS queue is deleted, or its resource policy doesn't   │          │
│  │  allow sqs:SendMessage from the topic ARN — SNS tries to       │          │
│  │  deliver, SQS rejects with 403, and the message is GONE.       │          │
│  │                                                                │          │
│  │  SNS FIFO ──deliver──► SQS FIFO (deleted / wrong policy)      │          │
│  │                            │                                   │          │
│  │                            └─► 403 Forbidden                   │          │
│  │                                → "client-side error"           │          │
│  │                                → ZERO retries for client errors│          │
│  │                                → message LOST permanently      │          │
│  │                                                                │          │
│  │  This is NOT a transient failure. SNS treats permission errors │          │
│  │  as YOUR fault and does not retry.                             │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  CONDITION 2 EXPLAINED: Delete before visibility timeout                     │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  After SQS delivers a message to your consumer, it becomes     │          │
│  │  INVISIBLE for the visibility timeout (default 30s). If your   │          │
│  │  consumer doesn't call DeleteMessage before timeout expires:   │          │
│  │                                                                │          │
│  │  SQS delivers to Consumer A → timeout starts (30s)            │          │
│  │                                   │                            │          │
│  │  Consumer A is slow (takes 45s)   │                            │          │
│  │                                   ▼                            │          │
│  │                             Timeout expires at 30s             │          │
│  │                                   │                            │          │
│  │                                   ▼                            │          │
│  │                        Message becomes VISIBLE again           │          │
│  │                                   │                            │          │
│  │                                   ▼                            │          │
│  │                   Consumer B picks it up → DUPLICATE!          │          │
│  │                                                                │          │
│  │  Now processed TWICE — no longer exactly-once.                 │          │
│  │  Fix: set visibility timeout > max processing time,            │          │
│  │  or call ChangeMessageVisibility to extend it mid-processing.  │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  CONDITION 3 EXPLAINED: No filtering (the sneaky interview gotcha)           │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  WITHOUT filter:                                               │          │
│  │    SNS FIFO → SQS FIFO                                        │          │
│  │    Delivery: exactly-once ✓                                    │          │
│  │                                                                │          │
│  │  WITH filter:                                                  │          │
│  │    SNS FIFO → [Filter: event_type=order] → SQS FIFO           │          │
│  │    Delivery: AT-MOST-ONCE ⚠                                   │          │
│  │                                                                │          │
│  │  "At-most-once" means:                                         │          │
│  │    • Message might be delivered ONCE (good)                    │          │
│  │    • Message might be delivered ZERO times (lost!)             │          │
│  │    • But never duplicated                                      │          │
│  │                                                                │          │
│  │  WHY? Filtering adds an evaluation step. If that evaluation    │          │
│  │  fails transiently (internal SNS error), SNS does NOT retry    │          │
│  │  (to preserve the "no duplicates" guarantee in FIFO).          │          │
│  │  So the message is silently dropped.                           │          │
│  │                                                                │          │
│  │  SNS chose "no duplicates" OVER "guaranteed delivery"          │          │
│  │  when filtering is enabled on FIFO subscriptions.              │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  CONDITION 4 EXPLAINED: No network disruptions (two generals problem)        │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  SNS FIFO ──deliver──► SQS FIFO ──ACK──► SNS                  │          │
│  │                                     │                          │          │
│  │                               Network blip                     │          │
│  │                                     │                          │          │
│  │                                     ▼                          │          │
│  │                          ACK lost in transit                   │          │
│  │                                     │                          │          │
│  │                                     ▼                          │          │
│  │                 SNS thinks delivery failed → retries           │          │
│  │                 SQS already has the message → DUPLICATE!       │          │
│  │                                                                │          │
│  │  This is the classic two generals problem from distributed     │          │
│  │  systems. SNS sent it, SQS got it, but the ACK was lost.      │          │
│  │  SNS retries, now SQS has it twice.                            │          │
│  │                                                                │          │
│  │  In practice this is RARE, but it means exactly-once is a      │          │
│  │  best-effort guarantee, not an absolute mathematical one.      │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  THE SENIOR ENGINEER ANSWER:                                                 │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  "Even with FIFO topics meeting all four conditions, I'd       │          │
│  │   STILL design my consumers to be idempotent — because         │          │
│  │   distributed exactly-once is a theoretical ideal, not a       │          │
│  │   production certainty. Idempotency keys (MessageId or         │          │
│  │   business key in DynamoDB/Redis with TTL) cost almost          │          │
│  │   nothing and make the system bulletproof."                    │          │
│  │                                                                │          │
│  │  This answer separates senior from mid-level in interviews.   │          │
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

### Deep Dive: The 5-Minute Dedup Window

```
┌──────────────────────────────────────────────────────────────────────────────┐
│         SNS FIFO — 5-MINUTE DEDUPLICATION WINDOW EXPLAINED                   │
│                                                                              │
│  WHAT IT IS:                                                                 │
│  When you publish to a FIFO topic, you provide a MessageDeduplicationId     │
│  (or SNS auto-generates one from the body hash if content-based dedup       │
│  is enabled). SNS stores that ID in an internal cache. For the NEXT         │
│  5 MINUTES, any message with the SAME dedup ID is silently discarded —     │
│  SNS returns 200 OK to the publisher, but never delivers it.                │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  TIMELINE EXAMPLE:                                                           │
│                                                                              │
│  T=0:00  Publish(msg, dedup_id="pay-R-12345-attempt-1")                     │
│          → SNS accepts ✓                                                    │
│          → Delivers to all subscribers ✓                                    │
│          → Stores "pay-R-12345-attempt-1" in dedup cache                    │
│                                                                              │
│  T=0:05  Publish(msg, dedup_id="pay-R-12345-attempt-1")  ← network retry   │
│          → SNS finds dedup ID in cache                                      │
│          → Returns 200 OK (looks successful to publisher)                   │
│          → Does NOT deliver. Silently dropped. ✓                            │
│                                                                              │
│  T=2:30  Publish(msg, dedup_id="pay-R-12345-attempt-1")  ← app retry       │
│          → Still within 5-min window → silently dropped ✓                   │
│                                                                              │
│  T=5:01  Publish(msg, dedup_id="pay-R-12345-attempt-1")                     │
│          → 5-min window EXPIRED → dedup ID evicted from cache              │
│          → SNS treats this as a NEW message                                 │
│          → DELIVERS AGAIN ✗  ← THIS IS THE TRAP                           │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────┐                │
│  │                                                         │                │
│  │   0 min          2.5 min          5 min         7 min   │                │
│  │   │───────────────│───────────────│──────────────│       │                │
│  │   │◄── SAFE ZONE (duplicates dropped) ──►│               │                │
│  │   │                                      │               │                │
│  │   │                                      │◄── DANGER ──►││                │
│  │   │                                      │ (same dedup   ││                │
│  │   │                                      │  ID treated   ││                │
│  │   │                                      │  as NEW msg)  ││                │
│  │                                                         │                │
│  └─────────────────────────────────────────────────────────┘                │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  WHY EXACTLY 5 MINUTES? (AWS's trade-off)                                   │
│                                                                              │
│  ┌──────────────┬───────────────────────────────────────────────────┐       │
│  │ Too short    │ 30 seconds: Network retries or slow producers     │       │
│  │ (bad)        │ could re-publish after window closes → duplicates │       │
│  ├──────────────┼───────────────────────────────────────────────────┤       │
│  │ Too long     │ 1 hour: AWS must hold millions of dedup IDs in   │       │
│  │ (bad)        │ memory across all customers → massive memory cost │       │
│  ├──────────────┼───────────────────────────────────────────────────┤       │
│  │ 5 minutes    │ Covers 99.9% of retry scenarios (most retries    │       │
│  │ (sweet spot) │ happen within seconds). Manageable memory cost.  │       │
│  └──────────────┴───────────────────────────────────────────────────┘       │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  WHAT'S SAFE vs WHAT'S NOT:                                                  │
│                                                                              │
│  ✓ SAFE: Publisher crashes, restarts, retries within 5 min                  │
│          → Dedup window catches it. No duplicate delivery.                  │
│                                                                              │
│  ✓ SAFE: SDK auto-retries on timeout (happens in milliseconds)              │
│          → Well within window. No duplicate delivery.                       │
│                                                                              │
│  ✗ UNSAFE: App bug re-publishes the same event 6 min later                  │
│            with the same dedup ID                                           │
│            → Window expired. SNS delivers it again.                         │
│                                                                              │
│  ✗ UNSAFE: Cron job runs every 10 min, replays "unconfirmed"               │
│            events with same dedup IDs                                       │
│            → Anything older than 5 min will duplicate.                      │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  THE PRODUCTION TRAP:                                                        │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────┐         │
│  │  1. Publisher publishes payment_processed for ride R-12345     │         │
│  │     dedup_id = "pay-R-12345-v1"                               │         │
│  │                                                               │         │
│  │  2. Consumer processes it but crashes BEFORE acknowledging    │         │
│  │     (message goes back to SQS queue)                          │         │
│  │                                                               │         │
│  │  3. 8 minutes later, your "retry orchestrator" sees the       │         │
│  │     event was never confirmed                                 │         │
│  │                                                               │         │
│  │  4. Re-publishes with same dedup_id = "pay-R-12345-v1"       │         │
│  │                                                               │         │
│  │  5. SNS: "Never seen this before" (5-min window expired)     │         │
│  │     → Delivers AGAIN → Driver gets paid TWICE                │         │
│  │                                                               │         │
│  │  FIX: Your CONSUMER must be idempotent.                       │         │
│  │  The dedup window protects against publisher-level retries,   │         │
│  │  NOT against application-level replays after 5 minutes.       │         │
│  └────────────────────────────────────────────────────────────────┘         │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  BEST PRACTICES:                                                             │
│                                                                              │
│  1. ALWAYS make consumers idempotent (don't rely solely on dedup window)    │
│     → Store processed event IDs in DynamoDB/Redis/Postgres                  │
│     → Check before processing: "Have I seen pay-R-12345-v1 before?"        │
│                                                                              │
│  2. Use versioned dedup IDs: "pay-R-12345-v1", "pay-R-12345-v2"           │
│     → If you genuinely need to re-publish, increment the version           │
│     → This bypasses the window intentionally                               │
│                                                                              │
│  3. Never re-publish with same dedup ID after 5 minutes                     │
│     → If your retry logic may exceed 5 min, handle retries at              │
│       the CONSUMER level (SQS visibility timeout), not publisher           │
│                                                                              │
│  4. For replay/reprocessing scenarios, use a DIFFERENT dedup ID             │
│     → "pay-R-12345-v1-replay-20260415" makes the intent clear             │
│     → But the consumer must still be idempotent                            │
│                                                                              │
│  INTERVIEW SUMMARY:                                                          │
│  "The 5-minute dedup window handles infrastructure duplicates —             │
│   network retries, SDK retries, publisher failovers. It does NOT            │
│   replace consumer-side idempotency. Think of it as a first line            │
│   of defense, not the only line."                                           │
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

### SNS DLQ vs SQS Redrive DLQ — Two Different Things

```
┌──────────────────────────────────────────────────────────────────────────────┐
│           SNS DLQ vs SQS DLQ — COMMON INTERVIEW CONFUSION                    │
│                                                                              │
│  These are TWO separate DLQ mechanisms at different layers:                   │
│                                                                              │
│  ┌──────────────────────────────┐  ┌──────────────────────────────┐         │
│  │   SNS SUBSCRIPTION DLQ       │  │   SQS REDRIVE DLQ            │         │
│  ├──────────────────────────────┤  ├──────────────────────────────┤         │
│  │ Trigger: SNS can't DELIVER   │  │ Trigger: Consumer fails to   │         │
│  │   to the subscriber endpoint │  │   PROCESS a message N times  │         │
│  │                              │  │                              │         │
│  │ Who sends: SNS service       │  │ Who sends: SQS service       │         │
│  │                              │  │                              │         │
│  │ Attached to: SNS subscription│  │ Attached to: SQS queue       │         │
│  │                              │  │                              │         │
│  │ Catches: endpoint down,      │  │ Catches: poison messages,    │         │
│  │   permission errors,         │  │   Lambda bugs, timeouts,     │         │
│  │   network failures           │  │   unprocessable data         │         │
│  └──────────────────────────────┘  └──────────────────────────────┘         │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### The Two-Layer DLQ Pattern (Production Best Practice)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│         TWO-LAYER DLQ = ZERO MESSAGE LOSS                                    │
│                                                                              │
│  In a full SNS → SQS → Lambda architecture, you want BOTH DLQs:            │
│                                                                              │
│                                                                              │
│  Publisher ──→ [SNS Topic]                                                   │
│                    │                                                         │
│                    ▼                                                         │
│  LAYER 1:   [SNS Subscription]                                               │
│                    │                                                         │
│              Delivery fails                                                  │
│              after all retries?                                              │
│                    │                                                         │
│              YES ──┼──────────────► [SNS DLQ] (SQS queue)                   │
│                    │                  Catches: SQS queue down,               │
│                    │                  permission errors, throttling           │
│              NO (delivered OK)                                               │
│                    │                                                         │
│                    ▼                                                         │
│  LAYER 2:   [SQS Queue]                                                     │
│                    │                                                         │
│              Consumer fails                                                  │
│              to process 5x?                                                  │
│                    │                                                         │
│              YES ──┼──────────────► [SQS Redrive DLQ] (SQS queue)           │
│                    │                  Catches: poison messages,               │
│                    │                  Lambda bugs, bad data                   │
│              NO (processed OK)                                               │
│                    │                                                         │
│                    ▼                                                         │
│             [Lambda Consumer]                                                │
│                    │                                                         │
│                    ▼                                                         │
│               Success!                                                       │
│                                                                              │
│                                                                              │
│  WITHOUT two-layer DLQ:                                                      │
│    • No SNS DLQ → delivery failure = message gone forever                   │
│    • No SQS DLQ → poison message = infinite retry loop                      │
│                    (Lambda invoked forever, cost explodes)                   │
│                                                                              │
│  WITH two-layer DLQ:                                                         │
│    • Every failure is captured somewhere                                     │
│    • Both DLQs have CloudWatch alarms (depth > 0 → alert)                  │
│    • Ops team can inspect, fix, and replay from either DLQ                  │
│    • ZERO messages lost, ZERO infinite loops                                │
│                                                                              │
│  This is the production-grade pattern. Non-negotiable for any               │
│  system handling money, orders, or critical business events.                │
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

### Scenario 5: Ride-Hailing Event Architecture (Uber/Lyft/Ola Pattern)

This is the **most interview-relevant** SNS scenario because it forces you to reason about
topic design, filtering, FIFO vs Standard, failure handling, and scale math — all in one system.

#### 5a. The Event Catalog

Every ride generates a chain of domain events. Each event is a fact about something that happened:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     RIDE LIFECYCLE — EVENT CHAIN                              │
│                                                                              │
│  ┌─────────────┐   ┌────────────────┐   ┌──────────────┐   ┌────────────┐  │
│  │   Rider      │   │   Matching     │   │   Driver     │   │  Billing   │  │
│  │   App        │   │   Engine       │   │   App        │   │  Service   │  │
│  └──────┬───────┘   └───────┬────────┘   └──────┬───────┘   └─────┬──────┘  │
│         │                   │                    │                 │         │
│         ▼                   ▼                    ▼                 ▼         │
│   ride_requested     driver_assigned       ride_started     payment_processed│
│                                            ride_completed   payment_failed  │
│                                                             refund_issued   │
│         │                                        │                          │
│         ▼                                        ▼                          │
│   ┌──────────┐                             ┌───────────┐                    │
│   │  Rider   │                             │  Rating   │                    │
│   │  App     │                             │  Service  │                    │
│   └──────────┘                             └─────┬─────┘                    │
│                                                  │                          │
│                                                  ▼                          │
│                                           rating_submitted                  │
│                                           driver_status_changed             │
│                                                                              │
│  TOTAL: 9 distinct event types across 3 domains                             │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │  DOMAIN         │  EVENTS                                    │           │
│  ├──────────────────┼───────────────────────────────────────────┤           │
│  │  Ride Lifecycle  │  ride_requested, driver_assigned,          │           │
│  │                  │  ride_started, ride_completed              │           │
│  ├──────────────────┼───────────────────────────────────────────┤           │
│  │  Payment         │  payment_processed, payment_failed,        │           │
│  │                  │  refund_issued                              │           │
│  ├──────────────────┼───────────────────────────────────────────┤           │
│  │  User Activity   │  rating_submitted, driver_status_changed   │           │
│  └──────────────────┴───────────────────────────────────────────┘           │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 5b. Topic Design — Domain-Scoped (Not One Mega-Topic)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│              WHY 3 TOPICS, NOT 1?  (THE CRITICAL DESIGN DECISION)            │
│                                                                              │
│  ╔══════════════════════════════════════════════════════════════════════╗     │
│  ║  WRONG: One mega-topic "ride-platform-events"                       ║     │
│  ║                                                                     ║     │
│  ║    ride_requested ──┐                                               ║     │
│  ║    driver_assigned ─┤                                               ║     │
│  ║    ride_started ────┤                                               ║     │
│  ║    ride_completed ──┼──→  [SNS: ride-platform-events] ──→ 10+ subs ║     │
│  ║    payment_processed┤          30K msgs/sec shared                  ║     │
│  ║    payment_failed ──┤          throttle across ALL                  ║     │
│  ║    refund_issued ───┤          event types                          ║     │
│  ║    rating_submitted ┤                                               ║     │
│  ║    driver_status ───┘                                               ║     │
│  ║                                                                     ║     │
│  ║  PROBLEMS:                                                          ║     │
│  ║  1. Analytics spike (ride_requested burst during surge)             ║     │
│  ║     throttles payment delivery → receipts delayed                   ║     │
│  ║  2. One team deploys bad filter → breaks ALL subscribers            ║     │
│  ║  3. Can't make payments FIFO without making everything FIFO         ║     │
│  ║     (FIFO limit: 300 msg/s vs Standard: 30K msg/s)                 ║     │
│  ║  4. IAM permissions are coarse: publish to topic = publish all      ║     │
│  ║  5. Single blast radius: topic-level issue = total outage           ║     │
│  ╚══════════════════════════════════════════════════════════════════════╝     │
│                                                                              │
│  ╔══════════════════════════════════════════════════════════════════════╗     │
│  ║  CORRECT: Domain-scoped topics                                      ║     │
│  ║                                                                     ║     │
│  ║    ┌─────────────────────────────────────────────────────────────┐  ║     │
│  ║    │ ride-lifecycle-events (Standard)                             │  ║     │
│  ║    │   Owns: ride_requested, driver_assigned,                    │  ║     │
│  ║    │         ride_started, ride_completed                        │  ║     │
│  ║    │   Owner team: Ride Platform                                 │  ║     │
│  ║    │   SLA: best-effort, p99 < 500ms                            │  ║     │
│  ║    │   Volume: ~48 msgs/sec (4 events × 12 rides/sec)           │  ║     │
│  ║    └─────────────────────────────────────────────────────────────┘  ║     │
│  ║                                                                     ║     │
│  ║    ┌─────────────────────────────────────────────────────────────┐  ║     │
│  ║    │ payment-events.fifo (FIFO)                                  │  ║     │
│  ║    │   Owns: payment_processed, payment_failed, refund_issued   │  ║     │
│  ║    │   Owner team: Payments                                     │  ║     │
│  ║    │   SLA: exactly-once, strict order per ride                 │  ║     │
│  ║    │   MessageGroupId: ride_id                                  │  ║     │
│  ║    │   Volume: ~36 msgs/sec (3 events × 12 rides/sec)          │  ║     │
│  ║    └─────────────────────────────────────────────────────────────┘  ║     │
│  ║                                                                     ║     │
│  ║    ┌─────────────────────────────────────────────────────────────┐  ║     │
│  ║    │ user-events (Standard)                                      │  ║     │
│  ║    │   Owns: rating_submitted, driver_status_changed            │  ║     │
│  ║    │   Owner team: User Experience                              │  ║     │
│  ║    │   SLA: best-effort, p99 < 2s (non-critical)               │  ║     │
│  ║    │   Volume: ~24 msgs/sec (2 events × 12 rides/sec)          │  ║     │
│  ║    └─────────────────────────────────────────────────────────────┘  ║     │
│  ║                                                                     ║     │
│  ║  BENEFITS:                                                          ║     │
│  ║  ✓ Independent scaling (each topic has its own 30K/s limit)        ║     │
│  ║  ✓ Independent SLAs (payment FIFO, rides Standard)                 ║     │
│  ║  ✓ Team ownership (Payments team → payment-events IAM only)        ║     │
│  ║  ✓ Blast radius isolation (payment issue ≠ ride matching issue)    ║     │
│  ║  ✓ Independent monitoring (CloudWatch alarms per topic)            ║     │
│  ╚══════════════════════════════════════════════════════════════════════╝     │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 5c. Complete Subscription Topology — The Full Fan-Out Map

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│          RIDE-HAILING SNS ARCHITECTURE — FULL SUBSCRIPTION MAP               │
│                                                                              │
│  ════════════════════════════════════════════════════════════════════════════ │
│  TOPIC 1: ride-lifecycle-events (Standard)                                   │
│  ════════════════════════════════════════════════════════════════════════════ │
│                                                                              │
│  Ride Service publishes:                                                     │
│  {                                                                           │
│    "Message": {"ride_id":"R-12345", "rider_id":"U-99", ...},                │
│    "MessageAttributes": {                                                    │
│      "event_type": {"DataType":"String", "StringValue":"ride_requested"},    │
│      "city":       {"DataType":"String", "StringValue":"bangalore"},         │
│      "surge_mult": {"DataType":"Number", "StringValue":"2.1"}               │
│    }                                                                         │
│  }                                                                           │
│                                                                              │
│                  [SNS: ride-lifecycle-events]                                 │
│                           │                                                  │
│         ┌─────────────────┼─────────────────┬──────────────────┐            │
│         │                 │                 │                  │             │
│         ▼                 ▼                 ▼                  ▼             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐  ┌──────────────┐      │
│  │ SQS:         │ │ SQS:         │ │ Lambda:      │  │ SQS:         │      │
│  │ driver-      │ │ eta-         │ │ push-        │  │ analytics-   │      │
│  │ matching-    │ │ calculation- │ │ notification │  │ queue        │      │
│  │ queue        │ │ queue        │ │              │  │              │      │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘  └──────┬───────┘      │
│         │                │                │                  │              │
│   FILTER:          FILTER:          FILTER:           NO FILTER             │
│   event_type =     event_type =     event_type IN     (gets ALL             │
│   "ride_requested" "driver_assigned" ["driver_assigned" events)             │
│                                      "ride_started"]                        │
│         │                │                │                  │              │
│         ▼                ▼                ▼                  ▼              │
│   Match nearest    Calculate ETA     Send push to      Stream to           │
│   available        and update        rider: "Driver     Kinesis →          │
│   driver using     rider app in      Rahul is on       Redshift for        │
│   geo index        real-time         the way!"         BI dashboards       │
│                                                                              │
│   WHY FILTER?                                                                │
│   • driver-matching only cares about NEW ride requests                      │
│   • eta-calculation only needs to trigger when driver IS assigned           │
│   • push-notification fires on assignment + trip start (2 events)           │
│   • analytics needs EVERY event for funnel tracking                         │
│                                                                              │
│  ════════════════════════════════════════════════════════════════════════════ │
│  TOPIC 2: payment-events.fifo (FIFO — ordered by ride_id)                   │
│  ════════════════════════════════════════════════════════════════════════════ │
│                                                                              │
│  Payment Service publishes:                                                  │
│  {                                                                           │
│    "Message": {"ride_id":"R-12345", "amount":450, "currency":"INR", ...},   │
│    "MessageAttributes": {                                                    │
│      "event_type": {"DataType":"String", "StringValue":"payment_processed"},│
│      "amount":     {"DataType":"Number", "StringValue":"450"},               │
│      "method":     {"DataType":"String", "StringValue":"upi"}                │
│    },                                                                        │
│    "MessageGroupId": "R-12345",                                              │
│    "MessageDeduplicationId": "pay-R-12345-attempt-1"                         │
│  }                                                                           │
│                                                                              │
│  WHY FIFO FOR PAYMENTS?                                                      │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │  Ride R-12345 generates these events IN ORDER:               │           │
│  │                                                              │           │
│  │    1. payment_processed (₹450 charged)                       │           │
│  │    2. payment_failed    (bank declined, reversed)            │           │
│  │    3. payment_processed (retry succeeds on UPI)              │           │
│  │                                                              │           │
│  │  If Standard topic delivers #3 before #2:                    │           │
│  │    → Driver gets paid for ₹450 TWICE                        │           │
│  │    → Rider charged ₹450 TWICE                               │           │
│  │    → Accounting goes haywire                                 │           │
│  │                                                              │           │
│  │  FIFO topic with MessageGroupId = ride_id:                   │           │
│  │    → Events for R-12345 always arrive 1 → 2 → 3             │           │
│  │    → Events for R-12346 are independent (different group)    │           │
│  │    → Parallelism across rides, strict order within each ride │           │
│  └──────────────────────────────────────────────────────────────┘           │
│                                                                              │
│                 [SNS: payment-events.fifo]                                   │
│                          │                                                   │
│         ┌────────────────┼────────────────┐                                 │
│         │                │                │                                  │
│         ▼                ▼                ▼                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                        │
│  │ SQS (FIFO):  │ │ Lambda:      │ │ SQS (FIFO):  │                        │
│  │ receipt-     │ │ fraud-       │ │ driver-      │                        │
│  │ generation-  │ │ detection    │ │ payout-      │                        │
│  │ queue.fifo   │ │              │ │ queue.fifo   │                        │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘                        │
│         │                │                │                                  │
│   NO FILTER        FILTER:          FILTER:                                 │
│   (all payment     amount >= 200    event_type =                            │
│    events need                      "payment_processed"                     │
│    receipts)                                                                 │
│         │                │                │                                  │
│         ▼                ▼                ▼                                  │
│   Generate PDF     Flag high-value   Trigger driver                        │
│   receipt, email   transactions,     payout settlement                     │
│   to rider,        check velocity,   to bank account                       │
│   store in S3      block stolen       (once confirmed                      │
│                    cards              payment received)                      │
│                                                                              │
│   ⚠ FIFO CONSTRAINT: All SQS subscribers MUST also be FIFO queues         │
│   ⚠ Lambda subscribers on FIFO topics NOT supported as of 2026             │
│   ⚠ fraud-detection Lambda → must go through SQS FIFO → Lambda trigger    │
│                                                                              │
│  ════════════════════════════════════════════════════════════════════════════ │
│  TOPIC 3: user-events (Standard)                                             │
│  ════════════════════════════════════════════════════════════════════════════ │
│                                                                              │
│                   [SNS: user-events]                                          │
│                          │                                                   │
│              ┌───────────┼───────────┐                                      │
│              │                       │                                       │
│              ▼                       ▼                                       │
│  ┌────────────────────┐  ┌────────────────────┐                             │
│  │ SQS:               │  │ SQS:               │                             │
│  │ driver-rating-     │  │ gamification-      │                             │
│  │ queue              │  │ queue              │                             │
│  └────────┬───────────┘  └────────┬───────────┘                             │
│           │                       │                                          │
│     FILTER:                 FILTER:                                          │
│     event_type =            event_type =                                    │
│     "rating_submitted"      "rating_submitted"                              │
│                             AND stars >= 5                                   │
│           │                       │                                          │
│           ▼                       ▼                                          │
│     Update driver            Award badge,                                   │
│     rating average,          trigger bonus                                  │
│     flag if < 4.0            payout for                                     │
│     for training             5-star streak                                  │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 5d. Failure Handling — DLQ Strategy Per Subscription

```
┌──────────────────────────────────────────────────────────────────────────────┐
│              DLQ STRATEGY — PRIORITY-BASED FAILURE HANDLING                   │
│                                                                              │
│  NOT all failures are equal. Payment failures need humans. Analytics         │
│  failures can wait until Monday.                                             │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────┐       │
│  │                                                                  │       │
│  │  TIER 1: CRITICAL (Payment DLQs) — PagerDuty alarm in 1 min    │       │
│  │  ────────────────────────────────────────────────────────────    │       │
│  │                                                                  │       │
│  │  payment-events.fifo                                             │       │
│  │       │                                                          │       │
│  │       ├──→ receipt-generation-queue.fifo                         │       │
│  │       │         │                                                │       │
│  │       │         └──→ DLQ: receipt-dlq.fifo                      │       │
│  │       │               • Max receives: 3 (fail fast)             │       │
│  │       │               • Alarm: ≥1 message → PagerDuty (P1)     │       │
│  │       │               • Action: On-call reviews within 15 min   │       │
│  │       │               • Retention: 14 days                      │       │
│  │       │                                                          │       │
│  │       ├──→ fraud-detection-queue.fifo                            │       │
│  │       │         │                                                │       │
│  │       │         └──→ DLQ: fraud-dlq.fifo                        │       │
│  │       │               • Max receives: 5 (retries help here)     │       │
│  │       │               • Alarm: ≥1 message → PagerDuty (P1)     │       │
│  │       │               • Missed fraud check = potential loss      │       │
│  │       │                                                          │       │
│  │       └──→ driver-payout-queue.fifo                              │       │
│  │                 │                                                │       │
│  │                 └──→ DLQ: payout-dlq.fifo                       │       │
│  │                       • Max receives: 3                         │       │
│  │                       • Alarm: ≥1 message → PagerDuty (P1)     │       │
│  │                       • Driver not paid = support escalation     │       │
│  │                                                                  │       │
│  ├──────────────────────────────────────────────────────────────────┤       │
│  │                                                                  │       │
│  │  TIER 2: IMPORTANT (Ride DLQs) — Slack alert, fix within 1 hr  │       │
│  │  ────────────────────────────────────────────────────────────    │       │
│  │                                                                  │       │
│  │  ride-lifecycle-events                                           │       │
│  │       │                                                          │       │
│  │       ├──→ driver-matching-queue                                 │       │
│  │       │         │                                                │       │
│  │       │         └──→ DLQ: matching-dlq                          │       │
│  │       │               • Max receives: 3                         │       │
│  │       │               • Alarm: ≥5 msgs → Slack #ride-ops (P2)  │       │
│  │       │               • Impact: rider waits longer              │       │
│  │       │                                                          │       │
│  │       ├──→ eta-calculation-queue                                 │       │
│  │       │         │                                                │       │
│  │       │         └──→ DLQ: eta-dlq                               │       │
│  │       │               • Max receives: 5                         │       │
│  │       │               • Alarm: ≥10 msgs → Slack (P3)           │       │
│  │       │               • Impact: stale ETA, bad UX               │       │
│  │       │                                                          │       │
│  │       └──→ push-notification (Lambda)                            │       │
│  │                 │                                                │       │
│  │                 └──→ SNS Subscription DLQ: push-notif-dlq       │       │
│  │                       • SNS-level DLQ (not SQS redrive)         │       │
│  │                       • Catches Lambda throttling/errors         │       │
│  │                       • Alarm: ≥20 msgs → Slack (P3)           │       │
│  │                       • Impact: rider doesn't get push notif    │       │
│  │                                                                  │       │
│  ├──────────────────────────────────────────────────────────────────┤       │
│  │                                                                  │       │
│  │  TIER 3: LOW PRIORITY (Analytics DLQ) — Replay on Monday       │       │
│  │  ────────────────────────────────────────────────────────────    │       │
│  │                                                                  │       │
│  │  analytics-queue                                                 │       │
│  │       │                                                          │       │
│  │       └──→ DLQ: analytics-dlq                                   │       │
│  │             • Max receives: 10 (many retries, low urgency)      │       │
│  │             • Alarm: ≥1000 msgs → Slack #data-eng (P4)         │       │
│  │             • Action: Batch replay via script, no rush           │       │
│  │             • Retention: 14 days                                │       │
│  │             • Impact: slightly stale dashboards                 │       │
│  │                                                                  │       │
│  └──────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│  KEY INSIGHT: DLQ alarm thresholds reflect business impact,                 │
│  not technical severity. 1 failed payment > 1000 failed analytics events.   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 5e. Scale Math — From Startup to Uber

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                  SCALE MATH — WILL SNS HANDLE THIS?                          │
│                                                                              │
│  STEP 1: Estimate event volume from rides/day                                │
│  ─────────────────────────────────────────────                               │
│                                                                              │
│  ┌────────────────┬────────────┬─────────────┬─────────────────────────────┐│
│  │ Scale          │ Rides/day  │ Events/sec  │ SNS Headroom                ││
│  │                │            │ (9 events   │ (Standard: 30K msg/s)       ││
│  │                │            │  per ride)  │ (FIFO: 300 msg/s/group,    ││
│  │                │            │             │  3K msg/s/topic)            ││
│  ├────────────────┼────────────┼─────────────┼─────────────────────────────┤│
│  │ Early startup  │    1K      │     0.1     │ 300,000x headroom          ││
│  │ Growing        │   100K     │     10      │ 3,000x headroom            ││
│  │ Mid-scale      │    1M      │    104      │ 288x headroom              ││
│  │ Ola/Lyft       │    5M      │    520      │ 57x headroom               ││
│  │ Uber (global)  │   25M      │  2,604      │ 11x headroom               ││
│  │ Uber (peak)    │   25M      │  ~8,000*    │ 3.7x headroom              ││
│  └────────────────┴────────────┴─────────────┴─────────────────────────────┘│
│                                                                              │
│  * Peak = 3x average (New Year's Eve, surge events)                         │
│                                                                              │
│  MATH BREAKDOWN (1M rides/day example):                                      │
│                                                                              │
│    1,000,000 rides/day ÷ 86,400 sec/day = 11.57 rides/sec                   │
│    11.57 rides/sec × 9 events/ride = 104.17 events/sec total                │
│                                                                              │
│    Per topic:                                                                │
│    • ride-lifecycle-events: 11.57 × 4 = 46.3 msg/s  (Standard: fine)       │
│    • payment-events.fifo:  11.57 × 3 = 34.7 msg/s  (FIFO: fine)           │
│    • user-events:          11.57 × 2 = 23.1 msg/s  (Standard: fine)       │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  STEP 2: When does SNS become the bottleneck?                                │
│  ─────────────────────────────────────────────                               │
│                                                                              │
│  Standard topic limit:  30,000 msg/s → reached at ~280M rides/day           │
│  FIFO topic limit:       3,000 msg/s → reached at ~22M rides/day            │
│  FIFO per-group limit:     300 msg/s → reached at ~8.6M rides/day           │
│                             (if ONE ride_id generates 300 events/sec        │
│                              which is impossible — a ride has ~3 payments)  │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │  VERDICT:                                                    │           │
│  │                                                              │           │
│  │  • For 99% of ride-hailing companies: SNS is MORE than      │           │
│  │    enough. You'll hit database limits long before SNS.       │           │
│  │                                                              │           │
│  │  • FIFO topic limit (3K msg/s) is the first real ceiling.   │           │
│  │    At Uber's scale, payment events alone would hit this.    │           │
│  │                                                              │           │
│  │  • At Uber scale (25M rides/day): SNS Standard is fine,     │           │
│  │    but FIFO topics need sharding:                            │           │
│  │    → payment-events-shard-01.fifo through shard-10.fifo     │           │
│  │    → Route by ride_id hash: ride_id % 10 → shard number    │           │
│  │    → Each shard handles ~260 msg/s (well under 3K limit)   │           │
│  │                                                              │           │
│  │  • Beyond Uber scale: Use Kafka (no publish throughput cap)  │           │
│  │    This is exactly why Uber uses Kafka internally, not SNS  │           │
│  └──────────────────────────────────────────────────────────────┘           │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  STEP 3: Cost estimate                                                       │
│  ─────────────────────                                                       │
│                                                                              │
│  SNS pricing: $0.50 per 1M publishes (first 1M free/month)                  │
│  SNS → SQS delivery: FREE                                                   │
│  SNS → Lambda delivery: FREE                                                │
│                                                                              │
│  At 1M rides/day:                                                            │
│    9M events/day × 30 days = 270M publishes/month                           │
│    Cost = 270 × $0.50 = $135/month  (extremely cheap)                       │
│                                                                              │
│  At 25M rides/day (Uber):                                                    │
│    6.75B publishes/month                                                     │
│    Cost = 6,750 × $0.50 = $3,375/month                                     │
│    (still cheap compared to Kafka cluster costs)                             │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 5f. Full System Diagram — Everything Together

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│           RIDE-HAILING EVENT ARCHITECTURE — COMPLETE PICTURE                 │
│                                                                              │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐               │
│  │  Rider    │  │  Matching │  │  Driver   │  │  Payment  │               │
│  │  App      │  │  Engine   │  │  App      │  │  Service  │               │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘               │
│        │              │              │              │                        │
│        │  ride_       │ driver_      │ ride_started │ payment_              │
│        │  requested   │ assigned     │ ride_completed processed             │
│        │              │              │              │ payment_failed         │
│        ▼              ▼              ▼              ▼                        │
│  ┌────────────────────────────┐  ┌────────────────────────────┐            │
│  │  ride-lifecycle-events     │  │  payment-events.fifo       │            │
│  │  (Standard Topic)          │  │  (FIFO Topic)              │            │
│  │                            │  │  GroupId = ride_id          │            │
│  └────┬───┬────┬────┬────────┘  └────┬──────┬──────┬─────────┘            │
│       │   │    │    │                │      │      │                       │
│       │   │    │    │                │      │      │                       │
│       │   │    │    │    ┌───────────┘      │      │                       │
│       │   │    │    │    │    ┌─────────────┘      │                       │
│       │   │    │    │    │    │    ┌───────────────┘                       │
│       ▼   ▼    ▼    ▼    ▼    ▼    ▼                                       │
│      ┌─┐ ┌─┐ ┌──┐ ┌─┐ ┌──┐ ┌──┐ ┌──┐                                    │
│      │D│ │E│ │PN│ │A│ │R │ │F │ │DP│                                     │
│      │M│ │C│ │  │ │N│ │G │ │D │ │  │                                     │
│      │Q│ │Q│ │λ │ │Q│ │Q │ │λ │ │Q │                                     │
│      └┬┘ └┬┘ └┬─┘ └┬┘ └┬─┘ └┬─┘ └┬─┘                                    │
│       │   │   │    │   │    │    │                                         │
│       ▼   ▼   ▼    ▼   ▼    ▼    ▼                                         │
│      DLQ DLQ DLQ  DLQ DLQ DLQ  DLQ                                       │
│      ─── ─── ───  ─── ─── ───  ───                                       │
│      P2  P3  P3   P4  P1  P1   P1      ← alarm priority                  │
│                                                                              │
│                                                                              │
│  ┌─────────────┐                                                            │
│  │  Rating     │       LEGEND:                                              │
│  │  Service    │       DM = driver-matching        P1 = PagerDuty (1 min)  │
│  └─────┬───────┘       EC = eta-calculation        P2 = Slack (15 min)     │
│        │               PN = push-notification (λ)  P3 = Slack (1 hr)       │
│        │ rating_       AN = analytics              P4 = Slack (Monday)     │
│        │ submitted     RG = receipt-generation                              │
│        ▼               FD = fraud-detection (λ)                             │
│  ┌────────────────┐    DP = driver-payout                                   │
│  │  user-events   │                                                         │
│  │  (Standard)    │    → SQS subscribers get filter policies               │
│  └────┬──────┬────┘    → Lambda subs need SNS subscription DLQs            │
│       │      │         → Every queue has its own SQS redrive DLQ           │
│       ▼      ▼         → Two-layer DLQ for Lambda paths                    │
│     ┌──┐  ┌──┐                                                             │
│     │DR│  │GA│                                                              │
│     │Q │  │Q │         MONITORING:                                          │
│     └┬─┘  └┬─┘         • CloudWatch: NumberOfNotificationsFailed per topic │
│      │     │            • CloudWatch: ApproximateNumberOfMessages per DLQ   │
│      ▼     ▼            • Custom metric: end-to-end event latency          │
│     DLQ   DLQ           • X-Ray: tracing across SNS → SQS → consumer      │
│     P3    P4                                                                │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 5g. Interview Angle — How to Present This

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│           HOW TO ANSWER "DESIGN RIDE EVENT SYSTEM" IN AN INTERVIEW           │
│                                                                              │
│  STEP 1 (30 sec): State the event catalog                                   │
│    "A ride generates 9 event types across 3 domains:                        │
│     ride lifecycle, payments, and user activity."                            │
│                                                                              │
│  STEP 2 (1 min): Justify topic separation                                   │
│    "I'd use 3 SNS topics, not 1, because:                                   │
│     - Payment events need FIFO ordering (can't double-charge)              │
│     - Different teams own different domains                                 │
│     - Independent scaling and blast radius isolation                        │
│     - Different SLAs: payment = P1, analytics = P4"                        │
│                                                                              │
│  STEP 3 (2 min): Draw the subscription map                                  │
│    Show topic → subscriber mapping with filter policies.                    │
│    Explain WHY each subscriber needs its specific filter.                   │
│                                                                              │
│  STEP 4 (1 min): Address ordering                                           │
│    "Payment topic is FIFO with MessageGroupId = ride_id.                    │
│     This gives strict per-ride ordering without sacrificing                 │
│     parallelism across rides. Ride events are Standard because             │
│     eventual consistency is acceptable there."                              │
│                                                                              │
│  STEP 5 (1 min): Show failure handling                                      │
│    "Every subscription has a DLQ. DLQ alarm priority matches              │
│     business impact: payment DLQ → PagerDuty P1,                          │
│     analytics DLQ → Slack P4, replay on Monday."                           │
│                                                                              │
│  STEP 6 (30 sec): Back-of-envelope math                                     │
│    "At 1M rides/day: ~104 events/sec across all topics.                    │
│     SNS handles 30K msg/s. We have 288x headroom.                          │
│     Even at Uber scale (25M rides/day), Standard SNS is fine.             │
│     FIFO would need topic sharding above ~22M rides/day."                  │
│                                                                              │
│  BONUS POINTS:                                                               │
│    - Mention cost: $135/mo at 1M rides/day                                 │
│    - Mention claim-check pattern for ride GPS traces (>256 KB)             │
│    - Mention cross-region: replicate events to DR region via               │
│      SNS → SQS cross-region subscription                                   │
│    - Mention idempotency: consumer must handle duplicate delivery          │
│      because SNS Standard is at-least-once                                 │
│    - Mention the Kafka escape hatch at extreme scale                       │
│                                                                              │
│  ⚠ COMMON MISTAKES IN INTERVIEWS:                                          │
│    ✗ Using one mega-topic for everything                                   │
│    ✗ Forgetting DLQs on subscriptions                                      │
│    ✗ Using FIFO for everything (kills throughput at 300 msg/s/group)       │
│    ✗ Not calculating scale math (just saying "SNS will handle it")         │
│    ✗ Skipping the filter policy explanation (shows you know the API)       │
│    ✗ Direct SNS → Lambda without SQS buffer (no backpressure)             │
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

### Deep Dive: What Is throttlePolicy: maxReceivesPerSecond?

```
┌──────────────────────────────────────────────────────────────────────────────┐
│         throttlePolicy — SNS DELIVERY RATE LIMITING (HTTP/S ONLY)            │
│                                                                              │
│  WHAT IT IS:                                                                 │
│  A delivery policy setting on HTTP/HTTPS subscriptions that limits           │
│  how many messages per second SNS will push to that specific endpoint.       │
│                                                                              │
│  CONFIGURATION (set on the subscription's delivery policy):                  │
│  {                                                                           │
│    "throttlePolicy": {                                                       │
│      "maxReceivesPerSecond": 10                                              │
│    }                                                                         │
│  }                                                                           │
│                                                                              │
│  HOW IT WORKS:                                                               │
│                                                                              │
│  Publisher sends 100 msgs/sec to topic                                       │
│           │                                                                  │
│           ▼                                                                  │
│      [SNS Topic]                                                             │
│           │                                                                  │
│           ├──→ SQS Queue (no throttle — gets all 100/sec instantly)         │
│           │                                                                  │
│           └──→ HTTP Endpoint (maxReceivesPerSecond = 10)                    │
│                │                                                             │
│                SNS delivers at MOST 10 msgs/sec to this endpoint             │
│                Remaining msgs → queued internally → delivered later           │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  THE BUG THAT CAUSED THE 65-DAY INCIDENT:                                   │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────┐          │
│  │  Even at LOW volumes (5 msgs/hour), setting ANY throttle value │          │
│  │  triggers SNS's internal queuing/scheduling mechanism.          │          │
│  │                                                                │          │
│  │  This internal queue has its own:                               │          │
│  │  • Batching logic                                               │          │
│  │  • Scheduling delays                                            │          │
│  │  • Internal retry windows                                       │          │
│  │                                                                │          │
│  │  ALL of these are UNDOCUMENTED and add unpredictable latency.  │          │
│  │                                                                │          │
│  │  Result: a topic handling 5 msgs/hour with a throttle of       │          │
│  │  1 msg/sec (3600x headroom!) still saw 30+ minute delays       │          │
│  │  because SNS's internal scheduler added overhead just by       │          │
│  │  EXISTING in the delivery path.                                 │          │
│  └────────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  KEY FACTS:                                                                  │
│  • Only applies to HTTP/HTTPS subscriptions (not SQS, Lambda, Email, SMS)   │
│  • Default: no throttle (SNS delivers as fast as possible)                  │
│  • CloudWatch metrics show "delivered successfully" even when delayed        │
│  • No way to see the internal queue depth or scheduling delay               │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  BEST PRACTICE — DON'T USE IT:                                               │
│                                                                              │
│  Instead of:                                                                 │
│    SNS → HTTP (with throttlePolicy)          ← fragile, buggy latency      │
│                                                                              │
│  Do this:                                                                    │
│    SNS → SQS → Your Service (polls at own rate)  ← reliable backpressure   │
│                                                                              │
│  SQS gives you NATURAL rate control:                                         │
│  • Your service polls only when ready (backpressure)                        │
│  • Messages persist for up to 14 days (no loss)                             │
│  • You control concurrency via consumer thread count                         │
│  • No undocumented SNS internal queuing in the path                         │
│                                                                              │
│  If you MUST use HTTP endpoints and can't add SQS, at least:                │
│  • Don't set throttlePolicy (let SNS deliver at full speed)                 │
│  • Handle rate limiting on YOUR server side (return 429 → SNS retries)      │
│  • Monitor end-to-end latency, not just delivery success                    │
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
```

### Deep Dive: How FIFO SQS + Lambda Concurrency Actually Works

```
┌──────────────────────────────────────────────────────────────────────────────┐
│     FIFO SQS + LAMBDA: MESSAGE GROUP ROUTING EXPLAINED                       │
│                                                                              │
│  SETUP: SNS FIFO topic → SQS FIFO queue → Lambda trigger (concurrency > 1) │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  SQS FIFO queue has these messages waiting:                                  │
│                                                                              │
│    Msg 1: GroupId = "ride-A"   payload = "payment_processed"                │
│    Msg 2: GroupId = "ride-A"   payload = "payment_failed"                   │
│    Msg 3: GroupId = "ride-B"   payload = "payment_processed"                │
│    Msg 4: GroupId = "ride-C"   payload = "payment_processed"                │
│    Msg 5: GroupId = "ride-A"   payload = "refund_issued"                    │
│                                                                              │
│  When Lambda polls with concurrency > 1, SQS routes by GroupId:            │
│                                                                              │
│  ┌──────────────────────────────┐  ┌──────────────────────────────┐        │
│  │   Lambda Instance 1          │  │   Lambda Instance 2          │        │
│  │                              │  │                              │        │
│  │   Msg 1: ride-A              │  │   Msg 3: ride-B              │        │
│  │     payment_processed        │  │     payment_processed        │        │
│  │                              │  │                              │        │
│  │   Msg 2: ride-A              │  │   Msg 4: ride-C              │        │
│  │     payment_failed           │  │     payment_processed        │        │
│  │                              │  │                              │        │
│  │   Msg 5: ride-A              │  │                              │        │
│  │     refund_issued            │  │                              │        │
│  │                              │  │                              │        │
│  │   (ALL ride-A, IN ORDER)     │  │   (different rides, parallel)│        │
│  └──────────────────────────────┘  └──────────────────────────────┘        │
│                                                                              │
│  ✓ ride-A: processed 1 → 2 → 5 (strict order, one instance)               │
│  ✓ ride-B: processed in parallel with ride-A (independent group)           │
│  ✓ ride-C: processed in parallel with ride-A and ride-B                    │
│                                                                              │
│  THE RULE:                                                                   │
│  ┌────────────────────────────────────────────────────────────────┐         │
│  │  Same MessageGroupId   → SAME Lambda instance, IN ORDER       │         │
│  │  Different GroupIds    → DIFFERENT instances, IN PARALLEL      │         │
│  │                                                                │         │
│  │  GroupId is the "lane divider":                                │         │
│  │  • Messages in the same lane → strictly serialized            │         │
│  │  • Messages in different lanes → race in parallel             │         │
│  └────────────────────────────────────────────────────────────────┘         │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  WHAT GOES WRONG #1: Single static GroupId (kills parallelism)              │
│                                                                              │
│  BAD: All messages use MessageGroupId = "payments"                          │
│                                                                              │
│  ┌──────────────────────────────┐  ┌──────────────────────────────┐        │
│  │   Lambda Instance 1          │  │   Lambda Instance 2          │        │
│  │                              │  │                              │        │
│  │   Msg 1: "payments"          │  │                              │        │
│  │   Msg 2: "payments"          │  │   (idle — SQS sends ALL     │        │
│  │   Msg 3: "payments"          │  │    messages to Instance 1   │        │
│  │   Msg 4: "payments"          │  │    because they share the   │        │
│  │   Msg 5: "payments"          │  │    same group)              │        │
│  │                              │  │                              │        │
│  └──────────────────────────────┘  └──────────────────────────────┘        │
│                                                                              │
│  Result: ZERO parallelism. Everything serialized.                           │
│  ride-C's payment waits for ride-A to finish. Massive latency.             │
│  FIFO throughput: 300 msg/s per group → 300 msg/s total.                   │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  WHAT GOES WRONG #2: Random/missing GroupId (breaks ordering)               │
│                                                                              │
│  BAD: Each message gets a random GroupId like UUID                          │
│                                                                              │
│  ┌──────────────────────────────┐  ┌──────────────────────────────┐        │
│  │   Lambda Instance 1          │  │   Lambda Instance 2          │        │
│  │                              │  │                              │        │
│  │   Msg 1: ride-A              │  │   Msg 2: ride-A              │        │
│  │     payment_processed        │  │     payment_failed           │        │
│  │                              │  │                              │        │
│  │   Msg 4: ride-C              │  │   Msg 5: ride-A              │        │
│  │                              │  │     refund_issued            │        │
│  └──────────────────────────────┘  └──────────────────────────────┘        │
│                                                                              │
│  Result: ride-A's messages SPLIT across instances.                          │
│  Instance 2 processes payment_failed BEFORE Instance 1 finishes            │
│  payment_processed. ORDER VIOLATED → double charge, wrong state.           │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  CORRECT GroupId DESIGN:                                                     │
│                                                                              │
│  ┌─────────────────────┬────────────────────────────────────────┐           │
│  │ Use case            │ MessageGroupId should be               │           │
│  ├─────────────────────┼────────────────────────────────────────┤           │
│  │ Ride payments       │ ride_id     ("ride-A")                 │           │
│  │ Order processing    │ order_id    ("order-12345")            │           │
│  │ User activity       │ user_id     ("user-99")               │           │
│  │ Stock trades        │ ticker      ("AAPL")                   │           │
│  │ Chat messages       │ channel_id  ("channel-567")            │           │
│  │ IoT device state    │ device_id   ("sensor-42")              │           │
│  └─────────────────────┴────────────────────────────────────────┘           │
│                                                                              │
│  FORMULA:                                                                    │
│  GroupId = the entity whose events MUST be ordered relative to each other   │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  SCALE IMPACT OF GroupId CARDINALITY:                                        │
│                                                                              │
│  ┌─────────────────────┬────────────┬──────────────────────────────┐        │
│  │ GroupId strategy     │ Unique IDs │ Effective throughput         │        │
│  ├─────────────────────┼────────────┼──────────────────────────────┤        │
│  │ Static: "payments"  │ 1          │ 300 msg/s (one lane)         │        │
│  │ Per-city: "BLR"     │ ~50        │ 300 × 50 = 15K msg/s        │        │
│  │ Per-ride: "ride-A"  │ ~1M/day    │ 300 × N = effectively       │        │
│  │                     │            │ unlimited (each ride has     │        │
│  │                     │            │ only ~3 payment events)      │        │
│  └─────────────────────┴────────────┴──────────────────────────────┘        │
│                                                                              │
│  More unique GroupIds = more parallelism = higher throughput                 │
│  FIFO's 300 msg/s limit is PER GROUP, not per topic                        │
│  (Topic limit: 3,000 msg/s across all groups combined)                     │
│                                                                              │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                              │
│  INTERVIEW ANSWER (one-liner):                                               │
│  "MessageGroupId is the lane divider. Same lane = strict order,             │
│   one consumer. Different lanes = parallel processing. Pick the             │
│   entity that needs ordering (ride_id, order_id) as your GroupId.           │
│   Never use a static GroupId — it kills parallelism."                       │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

```
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
