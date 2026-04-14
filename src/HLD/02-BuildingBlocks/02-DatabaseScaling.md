# Database Scaling: Replication, Sharding, Partitioning

> **Difficulty:** Easy-Medium | **Time:** 3 hours | **Priority:** Must Know

---

## Why Database Scaling Matters

A single database server has limits: ~5K-10K QPS, ~1TB storage. When your app grows beyond this, you need scaling strategies.

---

## 1. Vertical vs Horizontal Scaling

```
VERTICAL SCALING (Scale Up):          HORIZONTAL SCALING (Scale Out):

  ┌──────────┐                         ┌────┐ ┌────┐ ┌────┐ ┌────┐
  │          │                         │DB 1│ │DB 2│ │DB 3│ │DB 4│
  │  BIGGER  │                         │    │ │    │ │    │ │    │
  │  SERVER  │                         └────┘ └────┘ └────┘ └────┘
  │          │                         
  │ More RAM │                         Multiple smaller servers
  │ More CPU │                         sharing the load
  │ More SSD │                         
  └──────────┘                         
                                       
  Pros: Simple, no code changes        Pros: Unlimited scaling
  Cons: Hardware limits (~$$$)         Cons: Complex, distributed issues
        Single point of failure               Need sharding strategy
```

---

## 2. Replication

Copies of the same data on multiple servers. Primarily for **read scaling** and **high availability**.

### Master-Slave (Primary-Replica) Replication
```
                    ┌──────────────┐
                    │    Master    │
                    │   (Primary)  │
                    │              │
                    │ ALL WRITES   │
                    │ go here      │
                    └──────┬───────┘
                           │
              Replication  │  (async or sync)
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Replica 1│ │ Replica 2│ │ Replica 3│
        │  (Slave) │ │  (Slave) │ │  (Slave) │
        │          │ │          │ │          │
        │  READS   │ │  READS   │ │  READS   │
        └──────────┘ └──────────┘ └──────────┘

  Write:Read ratio = 1:9 → 1 Master + 9 Replicas

  Sync Replication:  Master waits for replicas → consistent but slow
  Async Replication: Master doesn't wait → fast but possible stale reads
```

### Master-Master (Multi-Primary) Replication
```
        ┌──────────┐          ┌──────────┐
        │ Master 1 │◄────────►│ Master 2 │
        │          │ two-way  │          │
        │ Reads +  │ repl     │ Reads +  │
        │ Writes   │          │ Writes   │
        └──────────┘          └──────────┘

  Both accept writes → conflict resolution needed!
  
  Conflict Resolution:
  1. Last-Write-Wins (LWW) — timestamp-based
  2. Application-level resolution
  3. CRDTs (Conflict-free Replicated Data Types)
  
  Use: Multi-region active-active deployments
```

### Replication Lag Problem
```
  T1: User writes "name=John" to Master
  T2: User reads from Replica → gets "name=Jane" (old value!)
  T3: Replication catches up → Replica has "name=John"

  Solutions:
  1. Read-your-writes: Route user's reads to master after their writes
  2. Monotonic reads: Stick user to same replica
  3. Use sync replication for critical data
```

---

## 3. Sharding (Horizontal Partitioning)

Split data across multiple databases, each holding a **subset of data**.

```
BEFORE SHARDING:                    AFTER SHARDING:
┌──────────────────┐               ┌──────────┐ ┌──────────┐ ┌──────────┐
│   Single DB      │               │ Shard 1  │ │ Shard 2  │ │ Shard 3  │
│                  │               │Users A-H │ │Users I-P │ │Users Q-Z │
│ All 100M users   │    ───►       │  33M     │ │  33M     │ │  33M     │
│ Overloaded!      │               │          │ │          │ │          │
└──────────────────┘               └──────────┘ └──────────┘ └──────────┘
                                   Each shard is independent!
```

### Sharding Strategies

```
1. RANGE-BASED SHARDING
   ─────────────────────
   Shard by range of a key (e.g., user_id, date)
   
   Shard 1: user_id 1 - 1M
   Shard 2: user_id 1M - 2M
   Shard 3: user_id 2M - 3M
   
   Pros: Simple, range queries easy
   Cons: Hot spots! (new users all go to last shard)


2. HASH-BASED SHARDING (Most Common)
   ──────────────────────────────────
   shard_id = hash(shard_key) % num_shards
   
   hash("user_123") % 3 = 1 → Shard 1
   hash("user_456") % 3 = 0 → Shard 0
   hash("user_789") % 3 = 2 → Shard 2
   
   Pros: Even distribution, no hot spots
   Cons: Range queries difficult, resharding is painful
         (adding a shard changes ALL assignments!)
   
   Solution: Use Consistent Hashing (see next topic)


3. DIRECTORY-BASED SHARDING
   ─────────────────────────
   A lookup service maintains the mapping.
   
   ┌──────────────────────┐
   │   Lookup Service     │
   │                      │
   │  user_123 → Shard 2  │
   │  user_456 → Shard 1  │
   │  user_789 → Shard 3  │
   └──────────────────────┘
   
   Pros: Flexible, can move data between shards
   Cons: Lookup service is a SPOF, extra hop


4. GEO-BASED SHARDING
   ───────────────────
   Shard by geographic region.
   
   Shard-US:   All US users
   Shard-EU:   All European users
   Shard-Asia: All Asian users
   
   Pros: Low latency for users, data locality compliance
   Cons: Uneven distribution, cross-region queries hard
```

### Choosing a Shard Key

```
GOOD Shard Key:                      BAD Shard Key:
──────────────                       ─────────────
✓ High cardinality                   ✗ Low cardinality
  (user_id, order_id)                  (country — only ~200 values)

✓ Even distribution                  ✗ Monotonically increasing
  (hash of user_id)                    (timestamp — all writes to 1 shard)

✓ Used in most queries               ✗ Rarely queried field
  (user_id if you query by user)       (middle_name)

✓ Avoids cross-shard queries         ✗ Requires frequent joins across shards
```

### Challenges of Sharding

```
┌──────────────────────────────────────────────────────────────┐
│               SHARDING CHALLENGES                             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  1. CROSS-SHARD QUERIES                                      │
│     "Find all orders across all users" → must query ALL      │
│     shards and merge results (scatter-gather)                │
│                                                              │
│  2. CROSS-SHARD JOINS                                        │
│     Users on Shard 1, Orders on Shard 3                      │
│     → Cannot join! Must denormalize or use app-level join    │
│                                                              │
│  3. RESHARDING                                               │
│     Adding/removing shards requires data migration           │
│     → Use consistent hashing to minimize data movement       │
│                                                              │
│  4. HOT SHARDS                                               │
│     Celebrity user gets 10x traffic → their shard overloads  │
│     → Secondary sharding or caching hot data                 │
│                                                              │
│  5. REFERENTIAL INTEGRITY                                    │
│     Foreign keys don't work across shards                    │
│     → Enforce at application level                           │
│                                                              │
│  6. OPERATIONAL COMPLEXITY                                   │
│     Backups, schema changes, monitoring × N shards           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Partitioning vs Sharding

```
VERTICAL PARTITIONING:             HORIZONTAL PARTITIONING (= Sharding):

Split by COLUMNS                   Split by ROWS

Users Table:                        Users Table:
┌────┬──────┬───────┬──────┐       ┌────┬──────┬───────┬──────┐
│ id │ name │ email │ bio  │       │ id │ name │ email │ bio  │
├────┼──────┼───────┼──────┤       ├────┼──────┼───────┼──────┤
│ 1  │ A    │ a@..  │ ...  │       │ 1  │ A    │ a@..  │ ...  │  → Shard 1
│ 2  │ B    │ b@..  │ ...  │       │ 2  │ B    │ b@..  │ ...  │  → Shard 1
│ 3  │ C    │ c@..  │ ...  │       │ 3  │ C    │ c@..  │ ...  │  → Shard 2
└────┴──────┴───────┴──────┘       │ 4  │ D    │ d@..  │ ...  │  → Shard 2
         │                          └────┴──────┴───────┴──────┘
         ▼
┌────┬──────┬───────┐  ┌────┬──────┐
│ id │ name │ email │  │ id │ bio  │
└────┴──────┴───────┘  └────┴──────┘
  Frequently accessed    Rarely accessed
  (hot data)             (cold data)
```

---

## 5. Scaling Decision Tree

```
Single DB hitting limits?
         │
         ├── Read-heavy? ──────► Add Read Replicas
         │                       (Master-Slave Replication)
         │
         ├── Write-heavy? ─────► Sharding
         │                       (Split data across DBs)
         │
         ├── Both? ────────────► Shard + Replicas per shard
         │
         └── Specific queries    ┌──────────────┐
             slow? ─────────────►│ Add Indexes   │
                                 │ Add Cache     │
                                 │ Denormalize   │
                                 └──────────────┘
```

---

## 6. Key Takeaways for Interviews

1. **Start simple**: Single DB → Add cache → Read replicas → Shard (only when needed)
2. **Replication is for reads**, sharding is for writes and storage
3. **Hash-based sharding** with consistent hashing is the go-to answer
4. **Shard key selection is critical** — it determines query patterns and data distribution
5. **Mention the trade-offs** of sharding: cross-shard queries, no joins, operational overhead
6. **Know when NOT to shard** — caching and read replicas solve 90% of scaling problems
