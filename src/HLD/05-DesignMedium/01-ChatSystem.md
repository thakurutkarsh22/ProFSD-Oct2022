# Design Chat System (WhatsApp / Messenger)

> **Difficulty:** Medium | **Frequency:** ★★★★★ | **Companies:** Meta, Google, Microsoft, Uber
> **Source:** Alex Xu Vol 1 Chapter 12

---

## 1. Requirements

### Functional
- 1:1 chat and group chat (up to 100 members)
- Send/receive text messages
- Online/offline status indicator
- Message delivery receipts (sent/delivered/read)
- Push notifications for offline users
- Message history and persistence

### Non-Functional
- Low latency message delivery (< 500ms)
- High availability
- Message ordering guaranteed within a conversation
- At-least-once delivery (no lost messages)

### Scale
- 500M DAU, 40 messages/user/day → 20B messages/day
- Peak: ~500K messages/second

---

## 2. High-Level Architecture

```
┌──────────┐                                          ┌──────────┐
│  User A  │                                          │  User B  │
│ (Sender) │                                          │(Receiver)│
└────┬─────┘                                          └────┬─────┘
     │                                                      │
     │ WebSocket                                   WebSocket │
     │ Connection                                 Connection │
     ▼                                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                        CHAT SERVICE                              │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  WebSocket   │  │   Message    │  │  Presence    │          │
│  │  Servers     │  │   Service    │  │  Service     │          │
│  │              │  │              │  │              │          │
│  │ Maintains    │  │ Routes msgs  │  │ Online/      │          │
│  │ persistent   │  │ Stores msgs  │  │ Offline      │          │
│  │ connections  │  │ Delivers     │  │ Status       │          │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘          │
│         │                 │                                      │
│    ┌────┴─────────────────┴────┐                                │
│    │      Message Queue        │                                │
│    │      (Kafka)              │                                │
│    └────┬──────────────────────┘                                │
│         │                                                        │
│  ┌──────┴───────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  Message DB  │  │   Cache      │  │ Push Notif   │          │
│  │ (Cassandra)  │  │  (Redis)     │  │  Service     │          │
│  │              │  │              │  │  (FCM/APNs)  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Message Flow

```
1:1 MESSAGE FLOW:

  User A                WS Server        Message        WS Server         User B
  (sender)              (A's conn)       Service        (B's conn)        (receiver)
    │                      │                │               │                │
    │──send msg───────────►│                │               │                │
    │                      │──route msg────►│               │                │
    │                      │                │──store in DB  │                │
    │                      │                │──publish to   │                │
    │                      │                │  Kafka queue  │                │
    │                      │                │               │                │
    │                      │                │──deliver─────►│                │
    │                      │                │               │──push msg─────►│
    │                      │                │               │                │
    │                      │◄─ack(stored)───│               │                │
    │◄─ack(sent)───────────│                │               │                │
    │                      │                │               │                │
    │                      │                │◄─ack(delivered)│                │
    │◄─ack(delivered)──────│                │               │                │


IF USER B IS OFFLINE:
  Message Service → stores in DB → Push Notification Service → sends push
  When B comes online → sync undelivered messages from DB
```

---

## 4. Key Design Decisions

### WebSocket Connection Management
```
PROBLEM: Which WebSocket server is User B connected to?

Solution: Connection Registry (Redis)

  Redis:
  ┌──────────────────────────────────────┐
  │  user:B → ws_server:ws-node-7       │
  │  user:A → ws_server:ws-node-3       │
  │  user:C → ws_server:ws-node-7       │
  └──────────────────────────────────────┘

  When routing message to User B:
  1. Lookup User B's WS server from Redis
  2. Forward message to ws-node-7
  3. ws-node-7 pushes message over B's WebSocket connection
```

### Message Storage
```
Cassandra (Wide-Column Store) — Best choice for chat

  Partition Key: conversation_id
  Clustering Key: message_id (Snowflake — time-sorted)

  ┌─────────────────┬────────────┬───────┬──────────┬──────────┐
  │ conversation_id │ message_id │ from  │ content  │ created  │
  ├─────────────────┼────────────┼───────┼──────────┼──────────┤
  │ conv_AB         │ 1001       │ A     │ "Hello"  │ 10:00:01 │
  │ conv_AB         │ 1002       │ B     │ "Hi!"    │ 10:00:03 │
  │ conv_AB         │ 1003       │ A     │ "How?" │ 10:00:05 │
  └─────────────────┴────────────┴───────┴──────────┴──────────┘

  Why Cassandra:
  ✓ Write-heavy (20B messages/day)
  ✓ Horizontal scaling (add nodes)
  ✓ Time-ordered reads by conversation
  ✓ Used by Discord, Facebook Messenger
```

### Group Chat
```
  Group: {id: "grp_xyz", members: [A, B, C, D]}
  
  When A sends message to group:
  1. Message Service looks up group members
  2. For each online member → route to their WS server
  3. For each offline member → push notification
  
  Fan-out on write (small groups, < 100):
  Message → [copy to B's queue, copy to C's queue, copy to D's queue]

  For large groups (channels, 10K+ members):
  Fan-out on read: members pull messages when they check the group
```

---

## 5. Presence (Online/Offline Status)

```
  HEARTBEAT APPROACH:
  ────────────────────
  Client sends heartbeat every 5 seconds via WebSocket.
  
  User A ──heartbeat──► Presence Service (Redis)
  
  Redis: { "user:A": { "status": "online", "last_seen": 1623456789 } }
  
  If no heartbeat for 30 seconds → mark as OFFLINE
  
  Optimization for group chat:
  - Don't broadcast presence updates for every member
  - Only fetch presence when user opens a chat
  - For large groups: only show presence for visible members
```

---

## 6. Key Points for Interview

1. **WebSocket** for real-time bidirectional messaging
2. **Connection registry (Redis)** to route messages to correct WS server
3. **Cassandra** for message storage (write-heavy, time-ordered)
4. **Kafka** as message queue between services (decoupling)
5. **Push notifications (FCM/APNs)** for offline users
6. **Message ordering** via Snowflake IDs (time-sorted)
7. **Group chat fan-out**: small groups (fan-out on write), large groups (fan-out on read)
8. **End-to-end encryption** for security (mention Signal protocol)
