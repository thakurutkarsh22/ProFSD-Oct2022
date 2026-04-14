# Design Key-Value Store

> **Difficulty:** Easy | **Frequency:** ★★★★☆ | **Companies:** Amazon, Google, Meta
> **Source:** Alex Xu Vol 1 Chapter 6, Amazon Dynamo Paper

---

## 1. Requirements

### Functional
- `put(key, value)` — store a key-value pair
- `get(key)` — retrieve value for a key
- `delete(key)` — remove a key-value pair

### Non-Functional
- High availability (AP system, like DynamoDB)
- High scalability (handle petabytes of data)
- Low latency (< 10ms for reads/writes)
- Tunable consistency

---

## 2. Single-Server KV Store

```
Simple approach: In-memory hash map

  HashMap<String, String>
  ┌───────────┬────────────────┐
  │ Key       │ Value          │
  ├───────────┼────────────────┤
  │ user:123  │ {"name":"John"}│
  │ sess:abc  │ {"token":"xyz"}│
  └───────────┴────────────────┘

  Limitations:
  - Memory is limited (can't store everything)
  - Single point of failure
  - Can't scale beyond one machine

  Optimizations:
  1. LRU eviction for memory management
  2. Persist to disk (WAL + SSTable)
```

---

## 3. Distributed KV Store Architecture

```
┌──────────────────────────────────────────────────────────────┐
│              DISTRIBUTED KEY-VALUE STORE                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────┐                    │
│  │       CONSISTENT HASH RING           │                    │
│  │                                      │                    │
│  │    Node A ●──── Node B ●             │                    │
│  │   /                     \            │                    │
│  │  ●                       ●           │                    │
│  │  Node E            Node C            │                    │
│  │   \                     /            │                    │
│  │    Node D ●──── Node F ●             │                    │
│  │                                      │                    │
│  └──────────────────────────────────────┘                    │
│                                                              │
│  put("user:123", data):                                      │
│  1. hash("user:123") → position on ring                      │
│  2. Clockwise → lands on Node C (coordinator)                │
│  3. Node C replicates to Node D, Node E (N=3 replicas)      │
│  4. Wait for W=2 acknowledgments → return success            │
│                                                              │
│  get("user:123"):                                            │
│  1. hash("user:123") → Node C (coordinator)                  │
│  2. Read from R=2 nodes (C and D)                            │
│  3. Return latest version (compare vector clocks)            │
│                                                              │
│  Tunable: N=3, W=2, R=2 → Strong consistency (W+R > N)      │
│           N=3, W=1, R=1 → High availability, eventual        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Storage Engine (How Data Is Stored on Disk)

```
LSM-TREE (Log-Structured Merge Tree):
Used by: Cassandra, RocksDB, LevelDB, HBase

WRITE PATH:
  1. Write to WAL (Write-Ahead Log) — durability
  2. Write to MemTable (in-memory sorted tree)
  3. When MemTable is full → flush to disk as SSTable

  ┌─────────┐     ┌───────────┐     ┌──────────────┐
  │  Write  │────►│   WAL     │────►│  MemTable    │
  │         │     │ (append-  │     │ (sorted,     │
  │         │     │  only log)│     │  in-memory)  │
  └─────────┘     └───────────┘     └──────┬───────┘
                                      Full? │
                                           ▼
                                    ┌──────────────┐
                                    │   SSTable    │  (Sorted String Table)
                                    │  (immutable, │  Periodically compacted
                                    │   on disk)   │  to merge and remove
                                    └──────────────┘  deleted entries

READ PATH:
  1. Check MemTable (fast, in-memory)
  2. Check Bloom Filter for each SSTable (skip if definitely not there)
  3. Search SSTables from newest to oldest
  4. Return first match found
```

---

## 5. Conflict Resolution

```
When W=1 (high availability), two clients may write different
values for the same key to different nodes simultaneously.

  Client 1 → Node A: put("cart", [item1, item2])
  Client 2 → Node B: put("cart", [item1, item3])
  
  Node A has: [item1, item2]
  Node B has: [item1, item3]
  
  Which is correct? BOTH are! Need to resolve.

VECTOR CLOCKS:
  Node A: cart = {value: [item1,item2], vclock: {A:1, B:0}}
  Node B: cart = {value: [item1,item3], vclock: {A:0, B:1}}
  
  Neither dominates the other → CONFLICT
  
  Resolution strategies:
  1. Last-Write-Wins (LWW) — simple but may lose data
  2. Application-level merge — return both to client, let app decide
  3. CRDTs — auto-merge without conflicts
     (e.g., OR-Set for shopping cart: union of both sets)
     Result: [item1, item2, item3]
```

---

## 6. Failure Handling

```
HINTED HANDOFF:
  If Node C is down, temporarily write to Node F.
  Node F stores a "hint": "This data belongs to Node C"
  When Node C comes back → Node F forwards the data.

ANTI-ENTROPY (Merkle Trees):
  Background process compares data across replicas.
  Uses Merkle trees to efficiently find differences.
  
         Root Hash
        /         \
    Hash(L)     Hash(R)
    /    \      /    \
  H(1)  H(2) H(3)  H(4)
  
  If root hashes match → replicas are identical (no sync needed)
  If different → traverse tree to find which data chunks differ
  Only sync the differing chunks.

GOSSIP PROTOCOL:
  Nodes periodically share membership information.
  "Node C is dead" → spreads through cluster like gossip.
  Used for: failure detection, membership list.
```

---

## 7. Key Points for Interview

1. **Consistent hashing** for data distribution across nodes
2. **Tunable consistency** with N, W, R parameters
3. **LSM-Tree** as the storage engine (WAL → MemTable → SSTable)
4. **Vector clocks** or **LWW** for conflict resolution
5. **Hinted handoff** for temporary node failures
6. **Merkle trees** for efficient anti-entropy/replica sync
7. **Bloom filter** to speed up reads (skip SSTables that don't contain the key)
