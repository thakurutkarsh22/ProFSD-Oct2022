# Caching for System Design

> **Difficulty:** Easy | **Time:** 2 hours | **Priority:** Must Know

---

## Why Caching Is Essential

Caching stores frequently accessed data in fast storage (memory) to reduce latency and database load. Almost every system design uses caching.

```
Without Cache:                          With Cache:

Client → Server → Database              Client → Server → Cache (HIT!) → Done!
         ~50ms    ~200ms                          ~50ms   ~1ms
         Total: ~250ms                            Total: ~51ms
                                                  (80% faster!)
         
         Every request hits DB                    If MISS → DB → Update Cache
         DB becomes bottleneck                    DB load reduced by 80-90%
```

---

## 1. Where to Cache (Caching Layers)

```
┌─────────────────────────────────────────────────────────────────┐
│                    CACHING LAYERS                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Layer 1: CLIENT-SIDE CACHE                                     │
│  ┌──────────────────────────┐                                   │
│  │ Browser Cache (images,   │  ← Closest to user               │
│  │ CSS, JS via HTTP headers)│    Cache-Control: max-age=3600    │
│  │ localStorage / sessionStorage │                               │
│  └──────────────────────────┘                                   │
│              │                                                   │
│              ▼                                                   │
│  Layer 2: CDN CACHE                                             │
│  ┌──────────────────────────┐                                   │
│  │ CloudFront, Akamai       │  ← Geographically distributed    │
│  │ Static assets, images    │    Edge servers near users        │
│  └──────────────────────────┘                                   │
│              │                                                   │
│              ▼                                                   │
│  Layer 3: APPLICATION CACHE                                     │
│  ┌──────────────────────────┐                                   │
│  │ Redis / Memcached        │  ← Most discussed in interviews  │
│  │ In-memory, sub-ms reads  │    Sits between app and DB       │
│  │ Session, API responses   │                                   │
│  └──────────────────────────┘                                   │
│              │                                                   │
│              ▼                                                   │
│  Layer 4: DATABASE CACHE                                        │
│  ┌──────────────────────────┐                                   │
│  │ Query cache, buffer pool │  ← Automatic, DB-managed         │
│  │ Materialized views       │    MySQL query cache              │
│  └──────────────────────────┘                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Caching Strategies

### Read Strategies

```
CACHE-ASIDE (Lazy Loading) — Most Common
─────────────────────────────────────────
App manages the cache explicitly.

  Client        App Server         Cache          Database
    │──request───►│                  │               │
    │             │──GET key────────►│               │
    │             │◄─MISS───────────│               │
    │             │──query──────────────────────────►│
    │             │◄─data──────────────────────────│
    │             │──SET key,data──►│               │
    │◄──response──│                  │               │
    
    Next request:
    │──request───►│                  │               │
    │             │──GET key────────►│               │
    │             │◄─HIT (data)─────│               │
    │◄──response──│                  │  DB not hit!  │

Pros: Only requested data is cached, simple to implement
Cons: Cache miss = 3 round trips, data can become stale


READ-THROUGH
────────────
Cache itself loads data from DB on miss. App only talks to cache.

  Client        App Server         Cache          Database
    │──request───►│                  │               │
    │             │──GET key────────►│               │
    │             │                  │──query───────►│
    │             │                  │◄─data────────│
    │             │◄─data───────────│  (auto-cached)│
    │◄──response──│                  │               │

Pros: Simpler app code, cache always populated
Cons: Cache library must know how to fetch from DB
```

### Write Strategies

```
WRITE-THROUGH
──────────────
Write to cache AND DB synchronously.

  Client        App Server         Cache          Database
    │──write─────►│                  │               │
    │             │──SET key────────►│               │
    │             │                  │──write───────►│
    │             │◄─ACK────────────│◄─ACK─────────│
    │◄──response──│                  │               │

Pros: Cache always consistent with DB
Cons: Higher write latency (both must succeed)


WRITE-BEHIND (Write-Back)
─────────────────────────
Write to cache immediately, async write to DB later.

  Client        App Server         Cache          Database
    │──write─────►│                  │               │
    │             │──SET key────────►│               │
    │◄──response──│◄─ACK────────────│               │
    │             │                  │               │
    │             │                  │───async──────►│
    │             │                  │   (batched)    │

Pros: Very fast writes, can batch DB writes
Cons: Risk of data loss if cache crashes before DB write


WRITE-AROUND
────────────
Write directly to DB, skip cache. Cache populated on reads.

  Client        App Server         Cache          Database
    │──write─────►│                  │               │
    │             │──write──────────────────────────►│
    │◄──response──│◄─ACK───────────────────────────│
    │             │                  │               │
    │             │  (cache stale                    │
    │             │   until next read                │
    │             │   or TTL expires)                 │

Pros: Cache not polluted with rarely-read data
Cons: Cache miss on first read after write
```

---

## 3. Cache Eviction Policies

When cache is full, which items to remove?

```
┌─────────────────────────────────────────────────────────────┐
│                   EVICTION POLICIES                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  LRU (Least Recently Used) — MOST COMMON                    │
│  ──────────────────────────                                 │
│  Evict the item that hasn't been accessed the longest       │
│  Implementation: HashMap + Doubly Linked List               │
│                                                             │
│  Access: A B C D E A B    (cache size = 4)                  │
│  Cache:  [A]                                                │
│          [A B]                                              │
│          [A B C]                                            │
│          [A B C D]       ← Full                             │
│          [B C D E]       ← A evicted (least recent)         │
│          [C D E A]       ← A accessed, moves to front       │
│          [D E A B]       ← B accessed, moves to front       │
│                                                             │
│  LFU (Least Frequently Used)                                │
│  ───────────────────────                                    │
│  Evict the item with fewest accesses                        │
│  Good for: data with stable popularity patterns             │
│                                                             │
│  FIFO (First In, First Out)                                 │
│  ──────────────────────                                     │
│  Evict the oldest item regardless of access                 │
│  Simple but not always optimal                              │
│                                                             │
│  TTL (Time-To-Live)                                         │
│  ─────────────────                                          │
│  Items expire after a set duration                          │
│  SET key value EX 3600  (expires in 1 hour)                 │
│  Essential for freshness guarantees                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Redis vs Memcached

| Feature | Redis | Memcached |
|---------|-------|-----------|
| Data Types | Strings, Lists, Sets, Hashes, Sorted Sets, Streams | Strings only |
| Persistence | RDB snapshots + AOF | No |
| Replication | Master-Replica | No |
| Pub/Sub | Yes | No |
| Lua Scripting | Yes | No |
| Clustering | Redis Cluster | Client-side sharding |
| Memory Efficiency | Less efficient | More efficient |
| Use Case | Feature-rich caching, sessions, queues | Simple high-throughput caching |

```
Choose Redis when:                    Choose Memcached when:
✓ Need data structures               ✓ Simple key-value caching
  (sorted sets for leaderboards)      ✓ Maximum memory efficiency
✓ Need persistence                    ✓ Multi-threaded performance
✓ Need pub/sub                        ✓ Large cache values (>1MB)
✓ Need TTL + eviction policies
✓ Need atomic operations
```

---

## 5. Common Caching Problems

```
CACHE STAMPEDE (Thundering Herd)
────────────────────────────────
Popular key expires → 1000 requests simultaneously hit DB

  Time T:  Key "homepage" expires
           
  ┌──────┐
  │Req 1 │──► Cache MISS ──► DB query
  │Req 2 │──► Cache MISS ──► DB query     ← All hit DB!
  │Req 3 │──► Cache MISS ──► DB query        DB overloaded!
  │......│──► Cache MISS ──► DB query
  │Req N │──► Cache MISS ──► DB query
  └──────┘

  Solutions:
  1. Locking: First request acquires lock, others wait
  2. Pre-warming: Refresh cache before TTL expires
  3. Stale-while-revalidate: Serve stale data while refreshing


CACHE PENETRATION
─────────────────
Requests for non-existent keys always miss cache → hit DB

  Request: GET user_999999 (doesn't exist)
  Cache: MISS → DB: NOT FOUND → repeated forever

  Solutions:
  1. Cache null results: SET user_999999 NULL TTL=5min
  2. Bloom filter: Check if key could exist before querying


CACHE AVALANCHE
───────────────
Many keys expire at the same time → massive DB spike

  Solutions:
  1. Random TTL jitter: TTL = base_ttl + random(0, 300)
  2. Never expire + background refresh
  3. Circuit breaker on DB
```

---

## 6. Key Takeaways for Interviews

1. **Cache-aside is the default** — use it unless you have a reason not to
2. **Always mention TTL** — data freshness is a real concern
3. **Redis is the default choice** for application-level caching in interviews
4. **Mention cache invalidation** — "There are only two hard things in CS: cache invalidation and naming things"
5. **Know the failure modes** — stampede, penetration, avalanche
6. **Multi-level caching** — CDN + Redis + DB query cache
7. **Cache hit ratio** — target 90%+ for a well-designed cache
