# Design Distributed Cache (Redis-like)

> **Difficulty:** Medium | **Frequency:** ★★★★☆ | **Companies:** Amazon, Google, Meta

---

## 1. Requirements

### Functional
- GET(key) → value
- SET(key, value, TTL)
- DELETE(key)
- Support data structures (strings, lists, sets, sorted sets, hashes)

### Non-Functional
- Sub-millisecond latency
- High availability
- Horizontal scalability
- Eviction policies (LRU, LFU, TTL)

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                   DISTRIBUTED CACHE CLUSTER                       │
│                                                                  │
│  ┌──────────────────────────────────────────────────────┐       │
│  │            CONSISTENT HASH RING                       │       │
│  │                                                       │       │
│  │   Key "user:123" → hash → Node B (primary)           │       │
│  │                             Node C (replica)          │       │
│  │                             Node D (replica)          │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                  │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐     │
│  │ Node A  │    │ Node B  │    │ Node C  │    │ Node D  │     │
│  │         │    │         │    │         │    │         │     │
│  │ Shard 1 │    │ Shard 2 │    │ Shard 3 │    │ Shard 4 │     │
│  │ + R of 4│    │ + R of 1│    │ + R of 2│    │ + R of 3│     │
│  │         │    │         │    │         │    │         │     │
│  │ Memory: │    │ Memory: │    │ Memory: │    │ Memory: │     │
│  │ HashMap │    │ HashMap │    │ HashMap │    │ HashMap │     │
│  │ + LRU   │    │ + LRU   │    │ + LRU   │    │ + LRU   │     │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘     │
│                                                                  │
│  Client has routing table (knows which node has which keys)      │
│  OR: proxy layer routes requests to correct node                 │
└──────────────────────────────────────────────────────────────────┘

DATA FLOW (SET):
  1. Client: SET "user:123" "John" EX 3600
  2. Client library hashes key → determines Node B is primary
  3. Request sent to Node B
  4. Node B stores in memory (HashMap + LRU linked list)
  5. Node B replicates to Node C (async)
  6. ACK returned to client

DATA FLOW (GET):
  1. Client: GET "user:123"
  2. Hash → Node B
  3. Node B: lookup in HashMap → O(1)
  4. Return value (or nil if not found / expired)
```

---

## 3. Internal Data Structures

```
HASH MAP + DOUBLY LINKED LIST (for LRU):

  HashMap:
  ┌───────────┬──────────────────┐
  │ Key       │ Pointer to Node  │
  ├───────────┼──────────────────┤
  │ user:123  │ ──────────────►  │
  │ sess:abc  │ ──────────────►  │
  └───────────┴──────────────────┘

  Doubly Linked List (MRU ← → LRU):
  HEAD ◄──► [user:123] ◄──► [sess:abc] ◄──► [cache:pg] ◄──► TAIL
  (most recent)                                    (least recent)
                                                    ↑ evict this first

  GET → move accessed node to HEAD
  SET → add to HEAD, if full → evict TAIL
  Both operations: O(1)


TTL IMPLEMENTATION:
  Approach 1: Lazy expiration
    Check TTL only when key is accessed. If expired → delete & return nil.
    
  Approach 2: Active expiration (Redis approach)
    Background thread randomly samples 20 keys every 100ms.
    Delete expired ones. If >25% expired → repeat immediately.
    
  Redis uses BOTH: lazy + active expiration together.
```

---

## 4. Persistence (Optional)

```
RDB (Point-in-time Snapshot):
  Fork process → write entire dataset to disk as binary file
  Pros: Compact, fast restore
  Cons: Data loss between snapshots

AOF (Append-Only File):
  Log every write command to a file
  SET user:123 "John"
  SET sess:abc "token"
  DEL old:key
  
  Pros: Minimal data loss (configurable: every command / every second)
  Cons: File grows large (periodic compaction needed)

Best practice: Use both RDB + AOF
  RDB for fast restore, AOF for minimal data loss
```

---

## 5. Key Points for Interview

1. **Consistent hashing** for distributing keys across cache nodes
2. **HashMap + Doubly Linked List** for O(1) LRU cache
3. **Replication** for high availability (primary + replicas)
4. **TTL with lazy + active expiration** (Redis approach)
5. **Cache aside** pattern for application integration
6. **Eviction policies**: LRU (most common), LFU, random
7. **Persistence**: RDB snapshots + AOF for durability
