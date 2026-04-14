# Design Notification System

> **Difficulty:** Medium | **Frequency:** ★★★★☆ | **Companies:** Amazon, Meta, Google
> **Source:** Alex Xu Vol 1 Chapter 10

---

## 1. Requirements

### Functional
- Support push notifications (iOS, Android), SMS, and Email
- Soft real-time (slight delay acceptable)
- Configurable notification preferences per user
- Rate limiting (don't spam users)
- Template-based notifications

### Non-Functional
- High availability, at-least-once delivery
- Scale: 10M notifications/day

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION SYSTEM                                │
│                                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │
│  │ Order Svc   │  │ Auth Svc    │  │ Payment Svc │   (Producers)   │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                 │
│         │                │                │                          │
│         └────────────────┼────────────────┘                          │
│                          ▼                                           │
│                ┌──────────────────┐                                  │
│                │ Notification     │                                  │
│                │ Service (API)    │                                  │
│                │                  │                                  │
│                │ 1. Validate      │                                  │
│                │ 2. Check prefs   │                                  │
│                │ 3. Rate limit    │                                  │
│                │ 4. Build payload │                                  │
│                └────────┬─────────┘                                  │
│                         │                                            │
│              ┌──────────┼──────────────┐                             │
│              ▼          ▼              ▼                              │
│     ┌──────────┐ ┌──────────┐  ┌──────────┐                        │
│     │Push Queue│ │SMS Queue │  │Email     │   (Kafka Topics)       │
│     │          │ │          │  │Queue     │                        │
│     └────┬─────┘ └────┬─────┘  └────┬─────┘                        │
│          │            │              │                               │
│          ▼            ▼              ▼                               │
│   ┌────────────┐ ┌──────────┐ ┌──────────────┐                     │
│   │Push Workers│ │SMS       │ │Email Workers │  (Consumers)        │
│   │            │ │Workers   │ │              │                     │
│   │ Send via:  │ │Send via: │ │ Send via:    │                     │
│   │ FCM (Andrd)│ │ Twilio   │ │ SendGrid     │                     │
│   │ APNs (iOS) │ │ Nexmo    │ │ SES          │                     │
│   └────────────┘ └──────────┘ └──────────────┘                     │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ User Prefs   │  │ Notification │  │  Analytics   │              │
│  │ DB           │  │ Log DB       │  │  (tracking)  │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Components

```
NOTIFICATION FLOW:
──────────────────
1. Service triggers notification: 
   POST /api/notifications
   { "user_id": 123, "type": "order_shipped", "data": {...} }

2. Notification Service:
   a. Fetch user preferences (push ON, email OFF, SMS OFF)
   b. Check rate limit (max 10 notifications/hour for this user)
   c. Deduplicate (same event_id seen before? skip)
   d. Build notification from template
   e. Enqueue to appropriate channel queue

3. Workers pick from queue:
   a. Push worker → calls FCM/APNs API
   b. If delivery fails → retry with exponential backoff
   c. After 3 retries → move to Dead Letter Queue
   d. Log delivery status

4. Analytics:
   Track: sent, delivered, opened, clicked
```

### Reliability
```
PREVENTING DUPLICATE NOTIFICATIONS:
  Event ID based deduplication
  
  Producer sends: { event_id: "order_123_shipped", ... }
  Notification service: Check if event_id exists in Redis
  If exists → skip (already processed)
  If not → process + store event_id with TTL

PREVENTING LOST NOTIFICATIONS:
  1. Persist to Kafka before processing (durable)
  2. Consumer acknowledges AFTER successful delivery
  3. If consumer crashes → message remains in queue → reprocessed
  4. Dead letter queue for permanent failures
```

---

## 4. Key Points for Interview

1. **Decouple with message queues** — separate queue per channel type
2. **User preferences** — respect opt-out, channel preferences, quiet hours
3. **Rate limiting** — per user per time window (don't spam)
4. **Deduplication** — event_id based to prevent duplicate notifications
5. **Retry + DLQ** — exponential backoff, dead letter queue for failures
6. **Template engine** — "Your order {order_id} has shipped" with variable substitution
7. **Analytics** — track open rates, click rates for optimization
