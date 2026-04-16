# Redis — The Complete Deep Dive

> **Difficulty:** Medium-Hard | **Time:** 6-8 hours | **Priority:** Must Know  
> **Sources:** Redis Design Docs (antirez), Redis Official Documentation, Twitter Engineering, Discord Engineering, GitHub Engineering, Martin Kleppmann (DDIA), Highscalability.com, ProdRescue Post-Mortems  
> **For:** Senior Engineers (7+ years) preparing for System Design interviews

---

## Table of Contents

1. [What Is Redis](#1-what-is-redis)
2. [Core Architecture — Single-Threaded Event Loop](#2-core-architecture--single-threaded-event-loop)
3. [Data Structures Deep Dive](#3-data-structures-deep-dive)
4. [Memory Management & Eviction](#4-memory-management--eviction)
5. [Persistence — RDB vs AOF](#5-persistence--rdb-vs-aof)
6. [Replication Deep Dive](#6-replication-deep-dive)
7. [Redis Sentinel — High Availability](#7-redis-sentinel--high-availability)
8. [Redis Cluster — Horizontal Scaling](#8-redis-cluster--horizontal-scaling)
9. [Transactions, Pipelining & Lua Scripts](#9-transactions-pipelining--lua-scripts)
10. [Distributed Locking — Redlock & The Great Debate](#10-distributed-locking--redlock--the-great-debate)
11. [Pub/Sub & Streams](#11-pubsub--streams)
12. [Real-World Usage at Scale](#12-real-world-usage-at-scale)
13. [When Redis Failed — Production Incidents](#13-when-redis-failed--production-incidents)
14. [When NOT to Use Redis](#14-when-not-to-use-redis)
15. [Anti-Patterns That Kill Redis](#15-anti-patterns-that-kill-redis)
16. [Performance Tuning Cheat Sheet](#16-performance-tuning-cheat-sheet)
17. [Interview Questions — Medium](#17-interview-questions--medium)
18. [Interview Questions — Hard](#18-interview-questions--hard)
19. [Quick Reference Card](#19-quick-reference-card)

---

## 1. What Is Redis

Redis (REmote DIctionary Server) is an **in-memory data structure store** created by Salvatore Sanfilippo (antirez) in 2009. It started as a way to improve the performance of his web analytics startup LLOOGG and became the world's most popular in-memory database.

It is NOT just a cache. It is a **programmable, in-memory data structure server**.

```
Traditional Cache (Memcached):              Redis (Data Structure Server):

  App → SET key "value" → GET key            App → ZADD leaderboard 100 "player1"
                                             App → LPUSH queue "job1"
  ✗ Only strings (key → value)               ✓ Strings, Lists, Sets, Sorted Sets,
  ✗ No persistence                             Hashes, Streams, HyperLogLog, Bitmaps,
  ✗ No pub/sub                                 Geospatial indexes
  ✗ No scripting                             ✓ RDB + AOF persistence
  ✗ No transactions                          ✓ Pub/Sub + Streams
  ✗ Multi-threaded (good for simple cache)   ✓ Lua scripting (atomic operations)
  ✗ LRU eviction only                        ✓ MULTI/EXEC transactions
  ✗ No replication built-in                  ✓ 8 eviction policies (LRU, LFU, TTL...)
                                             ✓ Built-in replication + Sentinel + Cluster
```

### Why Redis Is Fast — The Numbers

```
┌──────────────────────────────────────────────────────────────────────┐
│                    WHY REDIS IS FAST                                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. IN-MEMORY           RAM access = 100 ns vs SSD = 150,000 ns     │
│                         That's 1,500x faster than SSD                │
│                                                                      │
│  2. SINGLE-THREADED     No locks, no context switches, no mutexes   │
│                         Predictable O(1) latency                     │
│                                                                      │
│  3. I/O MULTIPLEXING    epoll (Linux) / kqueue (macOS) handles      │
│                         100K+ concurrent connections on one thread   │
│                                                                      │
│  4. EFFICIENT STRUCTS   Skip lists, hash tables, ziplist/listpack   │
│                         optimized for CPU cache lines                │
│                                                                      │
│  5. NO DISK I/O PATH    Read/write never touches disk in hot path   │
│                         Persistence is async/background              │
│                                                                      │
│  RESULT: 100,000 — 200,000+ operations/second on a SINGLE instance  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Core Architecture — Single-Threaded Event Loop

### The Event Loop — How One Thread Handles Everything

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        REDIS EVENT LOOP (ae library)                        │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                                                                     │   │
│   │                    ┌──────────────────┐                             │   │
│   │                    │   beforeSleep()  │                             │   │
│   │                    │  - flush AOF buf │                             │   │
│   │                    │  - handle expire │                             │   │
│   │                    │  - reply clients │                             │   │
│   │                    └────────┬─────────┘                             │   │
│   │                             │                                       │   │
│   │                             ▼                                       │   │
│   │                    ┌──────────────────┐                             │   │
│   │              ┌────►│  epoll_wait()    │◄────┐                      │   │
│   │              │     │  (blocks until   │     │                      │   │
│   │              │     │   I/O ready)     │     │                      │   │
│   │              │     └────────┬─────────┘     │                      │   │
│   │              │              │                │                      │   │
│   │              │              ▼                │                      │   │
│   │              │     ┌──────────────────┐     │                      │   │
│   │              │     │ processFileEvents│     │                      │   │
│   │              │     │ - accept conns   │     │                      │   │
│   │              │     │ - read commands  │     │                      │   │
│   │              │     │ - EXECUTE cmds   │     │  CONTINUOUS          │   │
│   │              │     │ - write replies  │     │  LOOP                │   │
│   │              │     └────────┬─────────┘     │                      │   │
│   │              │              │                │                      │   │
│   │              │              ▼                │                      │   │
│   │              │     ┌──────────────────┐     │                      │   │
│   │              │     │ processTimeEvents│     │                      │   │
│   │              │     │ - key expiry     │     │                      │   │
│   │              │     │ - repl heartbeat │     │                      │   │
│   │              │     │ - stats collect  │     │                      │   │
│   │              └─────┴──────────────────┘─────┘                      │   │
│   │                                                                     │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│   TWO EVENT TYPES:                                                          │
│     File Events  = I/O on sockets (network)                                │
│     Time Events  = periodic tasks (expiry, heartbeat, stats)               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Threading Model — It's NOT Purely Single-Threaded

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      REDIS THREADING MODEL                               │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  MAIN THREAD (single)              BACKGROUND THREADS (BIO)              │
│  ┌──────────────────────┐          ┌──────────────────────────┐         │
│  │ Command parsing      │          │ BIO_CLOSE_FILE           │         │
│  │ Command execution    │          │ - Close file descriptors │         │
│  │ Data structure R/W   │          │                          │         │
│  │ Lua script exec      │          │ BIO_AOF_FSYNC            │         │
│  │ MULTI/EXEC txn       │          │ - AOF fsync to disk      │         │
│  │ Pub/Sub routing      │          │                          │         │
│  │ Key expiration logic │          │ BIO_LAZY_FREE            │         │
│  │ Replication logic    │          │ - Async large key delete │         │
│  └──────────────────────┘          │   (UNLINK vs DEL)        │         │
│            │                       └──────────────────────────┘         │
│            │                                                             │
│            │                       I/O THREADS (Redis 6.0+)              │
│            │                       ┌──────────────────────────┐         │
│            │                       │ Network read (parsing)   │         │
│            │                       │ Network write (replies)  │         │
│            │                       │ Configurable: io-threads │         │
│            │                       │ Default: disabled        │         │
│            │                       └──────────────────────────┘         │
│            │                                                             │
│            ▼                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  CRITICAL: Command execution ALWAYS happens on the main thread. │   │
│  │  This is WHY one slow command (KEYS *, SORT on big set)         │   │
│  │  blocks ALL other clients.                                       │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Client Connection Lifecycle

```
  Client                              Redis Server
    │                                      │
    │──── TCP connect (port 6379) ────────►│
    │                                      │ accept() → new fd
    │                                      │ register fd with epoll
    │◄──── +OK ────────────────────────────│
    │                                      │
    │──── SET user:1 '{"name":"joe"}' ────►│
    │                                      │ epoll signals readable
    │                                      │ read command from buffer
    │                                      │ parse RESP protocol
    │                                      │ execute: dict_add()
    │                                      │ write reply to output buffer
    │◄──── +OK ────────────────────────────│
    │                                      │
    │──── GET user:1 ─────────────────────►│
    │                                      │ lookup in hash table
    │◄──── '{"name":"joe"}' ───────────────│
    │                                      │
    │──── QUIT ───────────────────────────►│
    │                                      │ close fd
    │                                      │ deregister from epoll
```

---

## 3. Data Structures Deep Dive

### The Master Map — All Data Structures

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                      REDIS DATA STRUCTURES — COMPLETE MAP                     │
├───────────────┬──────────────────┬────────────────────┬──────────────────────┤
│  Structure    │  Internal Encoding │  When Used         │  Complexity          │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  STRING       │  int, embstr,    │  Caching, counters │  GET/SET: O(1)       │
│               │  raw (SDS)       │  sessions, locks   │  INCR: O(1)          │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  LIST         │  listpack →      │  Queues, feeds,    │  LPUSH/RPOP: O(1)    │
│               │  quicklist       │  activity streams  │  LINDEX: O(N)        │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  HASH         │  listpack →      │  Object storage,   │  HGET/HSET: O(1)    │
│               │  hashtable       │  user profiles     │  HGETALL: O(N)       │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  SET          │  listpack →      │  Tags, unique      │  SADD/SREM: O(1)    │
│               │  hashtable       │  visitors, unions  │  SINTER: O(N*M)     │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  SORTED SET   │  listpack →      │  Leaderboards,     │  ZADD: O(log N)     │
│  (ZSET)       │  skiplist +      │  rate limiters,    │  ZRANGE: O(log N+M) │
│               │  hashtable       │  priority queues   │  ZRANK: O(log N)    │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  STREAM       │  radix tree +    │  Event sourcing,   │  XADD: O(1)         │
│               │  listpack        │  log aggregation   │  XREAD: O(N)        │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  HYPERLOGLOG  │  sparse →        │  Unique count      │  PFADD: O(1)        │
│               │  dense (12KB)    │  (UV, cardinality) │  PFCOUNT: O(1)      │
│               │                  │  0.81% error       │  12KB per key max   │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  BITMAP       │  SDS (string)    │  Feature flags,    │  SETBIT: O(1)       │
│               │                  │  bloom filter,     │  BITCOUNT: O(N)     │
│               │                  │  daily active user │  BITOP: O(N)        │
├───────────────┼──────────────────┼────────────────────┼──────────────────────┤
│  GEOSPATIAL   │  sorted set      │  Nearby search,    │  GEOADD: O(log N)   │
│               │  (geohash score) │  ride matching,    │  GEOSEARCH: O(N+log │
│               │                  │  delivery radius   │    N) for M results │
└───────────────┴──────────────────┴────────────────────┴──────────────────────┘
```

### Internal Encoding Transitions — What the Interviewer Wants to Hear

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     ENCODING TRANSITIONS                                 │
│                                                                          │
│  Redis automatically switches between compact and full encodings         │
│  based on element count and size thresholds.                             │
│                                                                          │
│  STRING:                                                                 │
│    integer value ──────────► int encoding (no overhead)                  │
│    ≤ 44 bytes ────────────► embstr (single allocation, read-only)       │
│    > 44 bytes ────────────► raw SDS (two allocations, mutable)          │
│                                                                          │
│  LIST:                                                                   │
│    ≤ 128 elements AND                                                   │
│    each element ≤ 64 bytes ───► listpack (contiguous memory, compact)   │
│    exceeds either ────────────► quicklist (linked list of listpacks)    │
│                                                                          │
│  HASH:                                                                   │
│    ≤ 128 fields AND                                                     │
│    each value ≤ 64 bytes ────► listpack (O(N) but cache-friendly)      │
│    exceeds either ───────────► hashtable (O(1) but more memory)        │
│                                                                          │
│  SET:                                                                    │
│    ≤ 128 elements AND                                                   │
│    all integers ─────────────► intset (sorted array, very compact)      │
│    ≤ 128 elements AND                                                   │
│    mixed types ──────────────► listpack                                 │
│    exceeds either ───────────► hashtable                                │
│                                                                          │
│  SORTED SET:                                                             │
│    ≤ 128 elements AND                                                   │
│    each element ≤ 64 bytes ──► listpack                                 │
│    exceeds either ───────────► skiplist + hashtable (dual structure!)   │
│                                                                          │
│  ⚠  These thresholds are configurable via:                              │
│     *-max-listpack-entries  and  *-max-listpack-value                   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Sorted Set Internals — Skip List + Hash Table

This is an interview favorite. Redis uses TWO data structures simultaneously for sorted sets:

```
                          SORTED SET DUAL STRUCTURE

    ┌─────────────────────────────────────────────────────────────────┐
    │  WHY TWO STRUCTURES?                                            │
    │                                                                 │
    │  Skip List → O(log N) range queries, ordered iteration          │
    │  Hash Table → O(1) point lookups by member name                 │
    │                                                                 │
    │  Without hash table: ZSCORE would be O(log N)                   │
    │  Without skip list: ZRANGE would be O(N log N) (need to sort)   │
    └─────────────────────────────────────────────────────────────────┘


    SKIP LIST (simplified):

    Level 4: HEAD ──────────────────────────────────────────────► NIL
    Level 3: HEAD ─────────────────► [50,"charlie"] ────────────► NIL
    Level 2: HEAD ──► [10,"alice"] ─► [50,"charlie"] ──► [90,"eve"] ► NIL
    Level 1: HEAD ──► [10,"alice"] ─► [30,"bob"] ─► [50,"charlie"] ─► [70,"dave"] ─► [90,"eve"] ► NIL

    + Parallel Hash Table:
      "alice"   → score: 10, skiplist node ptr
      "bob"     → score: 30, skiplist node ptr
      "charlie" → score: 50, skiplist node ptr
      "dave"    → score: 70, skiplist node ptr
      "eve"     → score: 90, skiplist node ptr

    ZSCORE "bob"     → hash table lookup → O(1) → returns 30
    ZRANGE 0 2       → skip list traversal → O(log N + 3) → [alice, bob, charlie]
    ZRANGEBYSCORE 20 60 → skip list range scan → O(log N + M)
    ZRANK "charlie"  → skip list traversal → O(log N) → returns 2
```

### HyperLogLog — Probabilistic Counting

```
┌──────────────────────────────────────────────────────────────────────┐
│                      HYPERLOGLOG IN REDIS                             │
│                                                                      │
│  Problem: Count unique visitors. 1 billion UVs × 8 bytes = 8 GB     │
│  Solution: HyperLogLog uses 12 KB for ANY cardinality, 0.81% error  │
│                                                                      │
│  HOW IT WORKS:                                                       │
│                                                                      │
│  1. Hash the element → 64-bit value                                  │
│  2. Use first 14 bits to select one of 16,384 registers             │
│  3. Count leading zeros in remaining 50 bits                         │
│  4. Store max leading zeros seen per register                        │
│  5. Harmonic mean of all registers → cardinality estimate            │
│                                                                      │
│  Example:                                                            │
│    PFADD daily_visitors "user_123"                                   │
│    PFADD daily_visitors "user_456"                                   │
│    PFADD daily_visitors "user_123"  ← duplicate, ignored             │
│    PFCOUNT daily_visitors           ← returns ~2                     │
│                                                                      │
│  ┌──────────────────────────────────────────────────┐               │
│  │  Memory comparison for 100M unique elements:      │               │
│  │                                                    │               │
│  │  SET          → ~6.4 GB   (exact)                 │               │
│  │  BITMAP       → ~12.5 MB  (exact, if IDs < 100M) │               │
│  │  HYPERLOGLOG  → 12 KB     (±0.81% error)         │               │
│  └──────────────────────────────────────────────────┘               │
│                                                                      │
│  PFMERGE → union of multiple HLLs (e.g., merge hourly into daily)   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 4. Memory Management & Eviction

### How Redis Manages Memory

```
┌──────────────────────────────────────────────────────────────────────────┐
│                       REDIS MEMORY ARCHITECTURE                          │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐     │
│  │                      PROCESS MEMORY                             │     │
│  │                                                                 │     │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │     │
│  │  │   Dataset     │  │  Overhead     │  │   Fragmentation      │ │     │
│  │  │              │  │              │  │                      │ │     │
│  │  │  Keys +      │  │  Client bufs │  │  jemalloc allocator  │ │     │
│  │  │  Values      │  │  Repl buffer │  │  pages vs actual     │ │     │
│  │  │              │  │  AOF buffer  │  │                      │ │     │
│  │  │  (~60-70%)   │  │  Lua memory  │  │  mem_fragmentation   │ │     │
│  │  │              │  │  (~10-20%)   │  │  _ratio > 1.5 = BAD  │ │     │
│  │  └──────────────┘  └──────────────┘  └──────────────────────┘ │     │
│  │                                                                 │     │
│  │  used_memory         =  dataset + overhead                      │     │
│  │  used_memory_rss     =  what OS actually allocated (includes    │     │
│  │                         fragmentation)                          │     │
│  │  mem_fragmentation_ratio = used_memory_rss / used_memory        │     │
│  │                                                                 │     │
│  │  IDEAL: ratio between 1.0 and 1.5                              │     │
│  │  > 1.5: significant fragmentation → restart or activedefrag     │     │
│  │  < 1.0: swapping to disk → DANGEROUS, performance cliff         │     │
│  │                                                                 │     │
│  └────────────────────────────────────────────────────────────────┘     │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Eviction Policies — Decision Tree

```
┌──────────────────────────────────────────────────────────────────────────┐
│                  WHICH EVICTION POLICY TO USE?                            │
│                                                                          │
│              Is data loss acceptable?                                     │
│              ┌──────┴──────┐                                             │
│             NO             YES                                           │
│              │              │                                             │
│              ▼              ▼                                             │
│         noeviction    Do you have a mix of                               │
│         (return        cache + persistent keys?                          │
│          errors)       ┌──────┴──────┐                                   │
│                       NO             YES                                 │
│                        │              │                                   │
│                        ▼              ▼                                   │
│                  Is access         volatile-lru                          │
│                  pattern           (only evicts keys                     │
│                  uniform?           with TTL set)                        │
│              ┌──────┴──────┐                                             │
│             YES            NO                                            │
│              │              │                                             │
│              ▼              ▼                                             │
│        allkeys-random  Are there hot/cold                                │
│                        access patterns?                                  │
│                    ┌──────┴──────┐                                       │
│                   YES            NO                                      │
│                    │              │                                       │
│                    ▼              ▼                                       │
│              allkeys-lfu    allkeys-lru                                  │
│              (protect        (most common                                │
│               popular)        choice)                                    │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  POLICY              SCOPE          ALGORITHM         USE CASE           │
│  ─────────────────── ────────────── ──────────────── ──────────────────  │
│  noeviction          N/A            Returns OOM err  Primary DB usage    │
│  allkeys-lru         All keys       Approx. LRU      General caching    │
│  allkeys-lfu         All keys       Approx. LFU      Hot/cold patterns  │
│  volatile-lru        Keys w/ TTL    Approx. LRU      Mixed cache+data   │
│  volatile-lfu        Keys w/ TTL    Approx. LFU      Mixed + frequency  │
│  allkeys-random      All keys       Random            Uniform access     │
│  volatile-random     Keys w/ TTL    Random            Simple TTL cache   │
│  volatile-ttl        Keys w/ TTL    Shortest TTL      Rate limiters      │
│                                                                          │
│  ⚠  "Approx. LRU" = Redis samples N keys (default 5) and evicts the    │
│     least recently used among the sample. NOT exact LRU.                 │
│     Increase maxmemory-samples to 10 for better accuracy (more CPU).    │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Key Expiration — Two Strategies Working Together

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    HOW REDIS EXPIRES KEYS                                 │
│                                                                          │
│  Strategy 1: LAZY EXPIRATION (passive)                                   │
│  ─────────────────────────────────────                                   │
│  When a client accesses a key → check TTL → if expired → delete + nil   │
│  Problem: keys never accessed remain in memory forever                   │
│                                                                          │
│  Strategy 2: ACTIVE EXPIRATION (background sampling)                     │
│  ─────────────────────────────────────────────────────                   │
│  Every 100ms (configurable via hz):                                      │
│    1. Sample 20 random keys with TTL set                                 │
│    2. Delete all expired keys in the sample                              │
│    3. If > 25% of sampled keys were expired → repeat immediately         │
│    4. Stop when < 25% expired OR time limit reached                      │
│                                                                          │
│  This ensures at most 25% of expirable keys are expired at any time     │
│                                                                          │
│  ┌─────────────────────────────────────────┐                            │
│  │  Timeline of expiration:                 │                            │
│  │                                          │                            │
│  │  SET key "val" EX 60     (TTL = 60s)    │                            │
│  │       │                                  │                            │
│  │       ├── 30s: key exists, 30s left      │                            │
│  │       ├── 60s: key logically expired     │                            │
│  │       ├── 61s: active sweep MAY delete   │                            │
│  │       ├── 62s: client GET → lazy delete  │                            │
│  │       │        (whichever comes first)   │                            │
│  │       └── Memory freed                   │                            │
│  └─────────────────────────────────────────┘                            │
│                                                                          │
│  ⚠  INTERVIEW TRAP: Expired keys can still consume memory until          │
│     either strategy actually deletes them. This is a common source       │
│     of "mysterious" memory usage.                                        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Persistence — RDB vs AOF

### Side-by-Side Comparison

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    REDIS PERSISTENCE: RDB vs AOF                              │
│                                                                               │
│  ┌─────────────────────────────┐    ┌──────────────────────────────────────┐ │
│  │         RDB SNAPSHOT         │    │         AOF (Append-Only File)       │ │
│  │                              │    │                                      │ │
│  │  Point-in-time binary dump   │    │  Log of every write command          │ │
│  │                              │    │                                      │ │
│  │  Trigger: SAVE / BGSAVE /    │    │  Every write → append to AOF buffer │ │
│  │  auto (save 900 1)           │    │  → fsync to disk per policy          │ │
│  │                              │    │                                      │ │
│  │  ┌────────┐  fork()          │    │  ┌────────────────────────────┐     │ │
│  │  │ Parent │──────────────┐   │    │  │ SET user:1 "alice"         │     │ │
│  │  │ (serve │              │   │    │  │ INCR counter               │     │ │
│  │  │ reqs)  │              ▼   │    │  │ LPUSH queue "job1"         │     │ │
│  │  └────────┘       ┌──────┐  │    │  │ SET user:2 "bob"           │     │ │
│  │                   │ Child │  │    │  │ DEL temp_key               │     │ │
│  │                   │ write │  │    │  │ ... (grows until rewrite)  │     │ │
│  │                   │ .rdb  │  │    │  └────────────────────────────┘     │ │
│  │                   └──────┘  │    │                                      │ │
│  │                              │    │  fsync policies:                     │ │
│  │  ✓ Compact binary            │    │  - always: after every write (safe) │ │
│  │  ✓ Fast restart              │    │  - everysec: once per second ← best │ │
│  │  ✓ Good for backups          │    │  - no: let OS decide (risky)        │ │
│  │  ✗ Data loss between saves   │    │                                      │ │
│  │  ✗ fork() memory spike       │    │  ✓ Minimal data loss (≤1 sec)       │ │
│  │                              │    │  ✓ Human-readable log               │ │
│  │  Data Loss: minutes/hours    │    │  ✓ Append-only = corruption-safe    │ │
│  │  Restart: FAST (load binary) │    │  ✗ Larger file size                 │ │
│  │                              │    │  ✗ Slower restart (replay commands) │ │
│  └─────────────────────────────┘    └──────────────────────────────────────┘ │
│                                                                               │
│  HYBRID MODE (Redis 4.0+) — RECOMMENDED FOR PRODUCTION                       │
│  ────────────────────────────────────────────────────────                     │
│  aof-use-rdb-preamble yes                                                     │
│                                                                               │
│  AOF rewrite produces: [RDB snapshot][AOF tail of commands since snapshot]    │
│  Combines fast restart (RDB) with minimal data loss (AOF)                     │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### The fork() Problem — Interview Deep Dive

```
┌──────────────────────────────────────────────────────────────────────────┐
│              THE fork() PROBLEM IN REDIS                                  │
│                                                                          │
│  When BGSAVE or AOF rewrite triggers:                                    │
│                                                                          │
│  1. Redis calls fork() → child process created                           │
│  2. Child gets COPY of parent's page tables (not data — Copy-on-Write)  │
│  3. Child writes data to disk                                            │
│  4. Parent continues serving requests                                    │
│                                                                          │
│  THE PROBLEM: Copy-on-Write Amplification                                │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  Parent Memory: 10 GB                                           │    │
│  │  fork() → child shares same pages (Copy-on-Write)               │    │
│  │                                                                  │    │
│  │  During snapshot, parent modifies keys:                          │    │
│  │  - Each modified page (4KB) gets COPIED for the child           │    │
│  │  - High write rate = many pages copied                          │    │
│  │  - Worst case: parent uses 10GB + child needs 10GB = 20GB      │    │
│  │                                                                  │    │
│  │  RULE OF THUMB: Reserve 2x memory for RDB/AOF operations        │    │
│  │                                                                  │    │
│  │  Mitigation:                                                     │    │
│  │  - Disable THP (Transparent Huge Pages) → reduces CoW to 4KB   │    │
│  │  - Schedule BGSAVE during low-write periods                     │    │
│  │  - Use replicas for backups instead of master                   │    │
│  │  - Monitor: INFO persistence → rdb_last_cow_size               │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ⚠ On Linux with overcommit_memory=0, fork() can FAIL if the OS         │
│    thinks there isn't enough memory, even though CoW means it won't     │
│    actually use 2x. Set vm.overcommit_memory=1 for Redis.               │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Replication Deep Dive

### Full Replication Flow

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     REDIS REPLICATION — COMPLETE FLOW                         │
│                                                                               │
│  STEP 1: INITIAL FULL SYNC (when replica connects for the first time)        │
│                                                                               │
│  Replica                              Master                                  │
│    │                                     │                                    │
│    │──── PSYNC ? -1 ────────────────────►│  "I'm new, give me everything"    │
│    │                                     │                                    │
│    │◄─── +FULLRESYNC <replid> <offset> ──│  "OK, here's my ID and offset"   │
│    │                                     │                                    │
│    │                                     │── fork() → BGSAVE                  │
│    │                                     │   (child generates RDB)            │
│    │                                     │                                    │
│    │                                     │── buffer all new writes            │
│    │                                     │   in replication backlog            │
│    │                                     │                                    │
│    │◄─── [RDB file transfer] ────────────│  (bulk transfer)                  │
│    │                                     │                                    │
│    │  (replica flushes old data,         │                                    │
│    │   loads RDB into memory)            │                                    │
│    │                                     │                                    │
│    │◄─── [backlog commands] ─────────────│  (catch-up delta)                 │
│    │                                     │                                    │
│    │  REPLICA NOW IN SYNC                │                                    │
│    │                                     │                                    │
│                                                                               │
│  STEP 2: CONTINUOUS PARTIAL SYNC (steady state)                               │
│                                                                               │
│  Master                               Replica                                 │
│    │                                     │                                    │
│    │  SET foo "bar"                      │                                    │
│    │──── propagate command ─────────────►│  replay same command               │
│    │                                     │                                    │
│    │  INCR counter                       │                                    │
│    │──── propagate command ─────────────►│  replay same command               │
│    │                                     │                                    │
│    │ (async, no ACK by default)          │                                    │
│    │                                     │                                    │
│                                                                               │
│  STEP 3: PARTIAL RESYNC (after temporary disconnect)                          │
│                                                                               │
│  Replica                              Master                                  │
│    │                                     │                                    │
│    │──── PSYNC <replid> <offset> ───────►│  "I was at offset 12345"          │
│    │                                     │                                    │
│    │     IF offset still in backlog:     │                                    │
│    │◄─── +CONTINUE ──────────────────────│  "OK, sending delta"              │
│    │◄─── [delta commands] ───────────────│  (partial resync — fast!)         │
│    │                                     │                                    │
│    │     IF offset NOT in backlog:       │                                    │
│    │◄─── +FULLRESYNC ───────────────────│  "Too far behind, full sync"      │
│    │     (back to Step 1)                │                                    │
│                                                                               │
│  REPLICATION BACKLOG:                                                         │
│  ┌──────────────────────────────────────────────┐                            │
│  │  Circular buffer (default 1MB, configurable)  │                            │
│  │  Stores recent write commands from master     │                            │
│  │  If replica disconnects briefly, it can       │                            │
│  │  resume from where it left off (PSYNC)        │                            │
│  │                                                │                            │
│  │  ⚠ Size this based on write rate × max        │                            │
│  │    expected disconnect duration                │                            │
│  │    Too small → full resync on every blip       │                            │
│  └──────────────────────────────────────────────┘                            │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Replication Is Asynchronous — What This Means

```
┌──────────────────────────────────────────────────────────────────────────┐
│              ASYNC REPLICATION IMPLICATIONS                               │
│                                                                          │
│  Client         Master              Replica                              │
│    │               │                    │                                 │
│    │── SET x 1 ───►│                    │                                 │
│    │◄── OK ────────│                    │                                 │
│    │               │── propagate ──────►│  (repl lag: ~ms to seconds)   │
│    │               │                    │                                 │
│    │               │   *** MASTER CRASHES HERE ***                       │
│    │               │                    │                                 │
│    │               X    Replica promoted │                                │
│    │                    SET x 1 LOST!    │                                │
│    │                                    │                                 │
│    │── GET x ──────────────────────────►│                                │
│    │◄── nil ───────────────────────────│  (data loss!)                   │
│    │                                    │                                 │
│                                                                          │
│  MITIGATION (not elimination):                                           │
│                                                                          │
│  min-replicas-to-write 1    → master refuses writes if no replica       │
│  min-replicas-max-lag 10    →   has ACK'd within 10 seconds             │
│                                                                          │
│  WAIT command:                                                           │
│    WAIT 2 5000  → block until 2 replicas ACK within 5 seconds           │
│    Still NOT strong consistency (ACK = received, not applied)            │
│                                                                          │
│  ⚠ Redis does NOT provide strong consistency guarantees.                 │
│    If you need that, look at: etcd, ZooKeeper, CockroachDB              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Redis Sentinel — High Availability

### Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        REDIS SENTINEL ARCHITECTURE                            │
│                                                                               │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                    │
│  │ Sentinel 1  │     │ Sentinel 2  │     │ Sentinel 3  │                    │
│  │ (monitor)   │◄───►│ (monitor)   │◄───►│ (monitor)   │  ← gossip         │
│  └──────┬──────┘     └──────┬──────┘     └──────┬──────┘    protocol        │
│         │                   │                    │                            │
│         │    PING every 1s  │   PING every 1s   │                            │
│         │                   │                    │                            │
│         ▼                   ▼                    ▼                            │
│  ┌─────────────────────────────────────────────────────┐                    │
│  │                    MASTER                            │ ← writes           │
│  │               (port 6379)                            │                    │
│  └──────────────┬────────────────────┬─────────────────┘                    │
│                 │                    │                                        │
│          replication           replication                                    │
│                 │                    │                                        │
│                 ▼                    ▼                                        │
│  ┌─────────────────────┐  ┌─────────────────────┐                           │
│  │   REPLICA 1          │  │   REPLICA 2          │ ← reads                  │
│  │   (port 6380)        │  │   (port 6381)        │   (optional)             │
│  └─────────────────────┘  └─────────────────────┘                           │
│                                                                               │
│  MINIMUM DEPLOYMENT: 3 Sentinels + 1 Master + 2 Replicas                     │
│  Quorum: 2 (majority of 3 must agree master is down)                         │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Failover Sequence — Step by Step

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   SENTINEL FAILOVER SEQUENCE                             │
│                                                                          │
│  1. SUBJECTIVE DOWN (SDOWN)                                              │
│     Sentinel 1 doesn't get PING reply for 30s (down-after-ms)           │
│     → marks master as SDOWN (only this sentinel thinks it's down)       │
│                                                                          │
│  2. OBJECTIVE DOWN (ODOWN)                                               │
│     Sentinel 1 asks Sentinel 2 and 3: "Is master down?"                 │
│     If quorum (2) agree → master is ODOWN (consensus: it's really down) │
│                                                                          │
│  3. LEADER ELECTION                                                      │
│     Sentinels run Raft-like election to pick ONE sentinel                │
│     to coordinate the failover                                           │
│                                                                          │
│  4. REPLICA SELECTION (the elected sentinel picks best replica)          │
│     Priority: slave-priority → replication offset → run ID              │
│     ─────────────────────────────────────────────────                    │
│     a. Filter out disconnected replicas                                  │
│     b. Pick lowest slave-priority (0 = never promote)                   │
│     c. Among equal priority, pick highest replication offset            │
│        (most data)                                                       │
│     d. Among equal offset, pick lexicographically smallest run ID       │
│                                                                          │
│  5. PROMOTION                                                            │
│     Selected replica receives: SLAVEOF NO ONE                            │
│     → becomes new master                                                 │
│                                                                          │
│  6. RECONFIGURATION                                                      │
│     Other replicas receive: SLAVEOF <new-master-ip> <new-master-port>   │
│     Sentinels update their config                                        │
│     Clients are notified via Sentinel pub/sub                            │
│                                                                          │
│  TIMELINE:  Detection (~30s) + Election (~2s) + Promotion (~1s)          │
│             = ~30-35 seconds total failover time                         │
│                                                                          │
│  ⚠ During failover, writes to old master are LOST                        │
│  ⚠ Clients must use Sentinel-aware drivers that auto-discover master    │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Redis Cluster — Horizontal Scaling

### Hash Slot Distribution

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        REDIS CLUSTER — HASH SLOTS                             │
│                                                                               │
│  Total Hash Slots: 16,384 (0 to 16,383)                                      │
│  Slot Assignment: CRC16(key) mod 16384                                        │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                          16,384 SLOTS                                │    │
│  │  ┌───────────────┬───────────────┬───────────────┬────────────────┐ │    │
│  │  │  0 — 5460     │  5461 — 10922 │ 10923 — 16383 │  (example)     │ │    │
│  │  │               │               │               │                │ │    │
│  │  │   Master A    │   Master B    │   Master C    │                │ │    │
│  │  │   ┌───────┐   │   ┌───────┐   │   ┌───────┐  │                │ │    │
│  │  │   │Rep A1 │   │   │Rep B1 │   │   │Rep C1 │  │                │ │    │
│  │  │   │Rep A2 │   │   │Rep B2 │   │   │Rep C2 │  │                │ │    │
│  │  │   └───────┘   │   └───────┘   │   └───────┘  │                │ │    │
│  │  └───────────────┴───────────────┴───────────────┴────────────────┘ │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  KEY ROUTING EXAMPLE:                                                         │
│                                                                               │
│  SET user:1000 "alice"                                                        │
│   → CRC16("user:1000") = 15343                                               │
│   → 15343 mod 16384 = 15343                                                  │
│   → Slot 15343 → Master C                                                    │
│                                                                               │
│  SET user:2000 "bob"                                                          │
│   → CRC16("user:2000") = 3280                                                │
│   → 3280 mod 16384 = 3280                                                    │
│   → Slot 3280 → Master A                                                     │
│                                                                               │
│  HASH TAGS (force keys to same slot):                                         │
│   {user:1000}.profile  and  {user:1000}.settings                              │
│   → CRC16 computed on "user:1000" only (inside {})                            │
│   → Both go to same slot → same node → multi-key ops work                    │
│                                                                               │
│  ⚠ Without hash tags, multi-key commands (MGET, MSET, pipeline)              │
│    across different slots will FAIL with CROSSSLOT error                      │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### MOVED and ASK Redirections

```
┌──────────────────────────────────────────────────────────────────────────┐
│               CLUSTER REDIRECTIONS — MOVED vs ASK                        │
│                                                                          │
│  SCENARIO 1: MOVED (permanent redirect)                                  │
│                                                                          │
│  Client          Node A                Node B                            │
│    │               │                     │                               │
│    │── GET foo ───►│                     │                               │
│    │               │ (slot for "foo"     │                               │
│    │               │  is on Node B)      │                               │
│    │◄── MOVED 3999 │                     │                               │
│    │    10.0.0.2:  │                     │                               │
│    │    6379 ──────│                     │                               │
│    │               │                     │                               │
│    │── GET foo ────────────────────────►│                               │
│    │◄── "bar" ─────────────────────────│                               │
│    │                                     │                               │
│    │  Client UPDATES its slot table      │                               │
│    │  (all future requests for slot      │                               │
│    │   3999 go directly to Node B)       │                               │
│                                                                          │
│  SCENARIO 2: ASK (temporary redirect during migration)                   │
│                                                                          │
│  Client          Node A                Node B                            │
│    │               │                     │                               │
│    │── GET foo ───►│                     │                               │
│    │               │ (slot migrating     │                               │
│    │               │  to Node B)         │                               │
│    │◄── ASK 3999   │                     │                               │
│    │    10.0.0.2:  │                     │                               │
│    │    6379 ──────│                     │                               │
│    │               │                     │                               │
│    │── ASKING ─────────────────────────►│                               │
│    │── GET foo ────────────────────────►│                               │
│    │◄── "bar" ─────────────────────────│                               │
│    │                                     │                               │
│    │  Client does NOT update slot table  │                               │
│    │  (next request for slot 3999 still  │                               │
│    │   goes to Node A — migration temp)  │                               │
│                                                                          │
│  ⚠ Smart clients cache the full slot→node mapping and refresh it         │
│    periodically or on MOVED responses. This minimizes redirections.      │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Cluster Split-Brain and Write Loss

```
┌──────────────────────────────────────────────────────────────────────────┐
│              REDIS CLUSTER — SPLIT BRAIN SCENARIO                        │
│                                                                          │
│  Network partition creates two sides:                                    │
│                                                                          │
│  PARTITION A (minority)        │    PARTITION B (majority)               │
│  ┌──────────────────────┐     │    ┌──────────────────────────┐        │
│  │  Master 1             │     │    │  Master 2                 │        │
│  │  Client X writes      │     │    │  Master 3                 │        │
│  │  to Master 1          │     │    │  Replica 1 (of Master 1) │        │
│  └──────────────────────┘     │    └──────────────────────────┘        │
│                                │                                        │
│  After NODE_TIMEOUT:           │    Majority side promotes              │
│  Master 1 stops accepting      │    Replica 1 → New Master 1           │
│  writes (can't reach majority) │                                        │
│                                │                                        │
│  WINDOW OF DATA LOSS:          │                                        │
│  During NODE_TIMEOUT seconds,  │                                        │
│  Client X writes to old        │                                        │
│  Master 1 are LOST when        │                                        │
│  partition heals.               │                                        │
│                                │                                        │
│  cluster-node-timeout 15000    │  (default: 15 seconds of write loss)  │
│                                                                          │
│  ⚠ Redis Cluster does NOT guarantee strong consistency.                  │
│    It guarantees: "best effort" consistency with bounded data loss.      │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Sentinel vs Cluster — When to Use Which

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    SENTINEL vs CLUSTER                                    │
│                                                                          │
│  ASPECT              │  SENTINEL              │  CLUSTER                 │
│  ─────────────────── │ ────────────────────── │ ──────────────────────── │
│  Purpose             │  HA for single dataset │  HA + horizontal scale   │
│  Data distribution   │  None (single master)  │  Auto via 16384 slots    │
│  Write scaling       │  NO (1 master)         │  YES (N masters)         │
│  Read scaling        │  Replicas for reads    │  Replicas for reads      │
│  Min nodes           │  3 Sentinel + 1M + 2R  │  6 (3M + 3R)            │
│  Multi-key ops       │  Fully supported       │  Only within same slot   │
│  Complexity          │  Lower                 │  Higher                  │
│  Max dataset size    │  Single machine RAM    │  N × machine RAM         │
│  Client requirement  │  Sentinel-aware driver │  Cluster-aware driver    │
│                      │                        │                          │
│  USE WHEN:           │  Dataset fits in       │  Dataset > single        │
│                      │  single machine, need  │  machine RAM, or need    │
│                      │  simple HA + failover  │  write throughput        │
│                      │                        │  beyond single node      │
│                                                                          │
│  ⚠ Common mistake: Using Cluster when Sentinel would suffice.            │
│    Cluster adds complexity (CROSSSLOT errors, resharding) that           │
│    isn't worth it if your data fits in one node.                         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Transactions, Pipelining & Lua Scripts

### Three Ways to Batch Commands

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                   PIPELINING vs TRANSACTIONS vs LUA                           │
│                                                                               │
│  ┌─────────────────────┐ ┌─────────────────────┐ ┌──────────────────────┐   │
│  │   PIPELINING         │ │   MULTI/EXEC         │ │   LUA SCRIPT         │   │
│  │                      │ │                      │ │                      │   │
│  │ Network optimization │ │ Atomic execution     │ │ Atomic + logic       │   │
│  │ Send N cmds at once  │ │ All-or-nothing block │ │ Conditional, loops   │   │
│  │ Get N replies back   │ │ No interleaving      │ │ Server-side compute  │   │
│  │                      │ │                      │ │                      │   │
│  │ Atomic? NO           │ │ Atomic? YES          │ │ Atomic? YES          │   │
│  │ Logic?  NO           │ │ Logic?  NO           │ │ Logic?  YES          │   │
│  │ Fast?   FASTEST      │ │ Fast?   FAST         │ │ Fast?   FAST*        │   │
│  │                      │ │                      │ │ (*if script is lean) │   │
│  └─────────────────────┘ └─────────────────────┘ └──────────────────────┘   │
│                                                                               │
│  WHEN TO USE EACH:                                                            │
│                                                                               │
│  Pipeline  → Batch reads/writes where order doesn't matter                    │
│              (e.g., warm up cache with 1000 keys)                             │
│                                                                               │
│  MULTI/EXEC → Multiple writes that must happen atomically                     │
│               (e.g., transfer money: DEBIT A + CREDIT B)                     │
│                                                                               │
│  Lua Script → Read-then-write logic that must be atomic                       │
│               (e.g., rate limiter: read count, if < limit then increment)    │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### WATCH + MULTI/EXEC — Optimistic Locking

```
┌──────────────────────────────────────────────────────────────────────────┐
│               OPTIMISTIC LOCKING WITH WATCH                              │
│                                                                          │
│  Client A                 Redis                   Client B               │
│    │                        │                        │                   │
│    │── WATCH balance ──────►│                        │                   │
│    │◄── OK ─────────────────│                        │                   │
│    │                        │                        │                   │
│    │── GET balance ────────►│                        │                   │
│    │◄── "100" ──────────────│                        │                   │
│    │                        │                        │                   │
│    │  (compute: 100 - 30    │                        │                   │
│    │   = 70)                │                        │                   │
│    │                        │                        │                   │
│    │                        │◄── SET balance 50 ─────│  (someone else   │
│    │                        │──── OK ───────────────►│   modified it!)  │
│    │                        │                        │                   │
│    │── MULTI ──────────────►│                        │                   │
│    │── SET balance 70 ─────►│  (queued)              │                   │
│    │── EXEC ───────────────►│                        │                   │
│    │◄── nil (ABORTED!) ─────│  balance was modified  │                   │
│    │                        │  after WATCH → txn     │                   │
│    │                        │  automatically fails   │                   │
│    │                        │                        │                   │
│    │  (Client A retries     │                        │                   │
│    │   the entire flow)     │                        │                   │
│                                                                          │
│  ⚠ This is CAS (Compare-And-Swap) at the Redis level.                   │
│    High contention = many retries = poor performance.                    │
│    For high contention, use Lua scripts instead.                         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Lua Script Example — Rate Limiter

```
  -- Sliding window rate limiter in Lua (atomic, server-side)
  -- KEYS[1] = rate limit key
  -- ARGV[1] = window size in seconds
  -- ARGV[2] = max requests allowed
  -- ARGV[3] = current timestamp

  local key = KEYS[1]
  local window = tonumber(ARGV[1])
  local limit = tonumber(ARGV[2])
  local now = tonumber(ARGV[3])

  -- Remove entries outside the window
  redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

  -- Count current entries
  local count = redis.call('ZCARD', key)

  if count < limit then
      -- Add current request
      redis.call('ZADD', key, now, now .. '-' .. math.random(100000))
      redis.call('EXPIRE', key, window)
      return 1  -- ALLOWED
  else
      return 0  -- DENIED
  end

  -- Usage: EVALSHA <sha> 1 "ratelimit:user:123" 60 100 1672531200
  -- "Allow 100 requests per 60 seconds for user 123"
```

---

## 10. Distributed Locking — Redlock & The Great Debate

### Simple Redis Lock

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     SIMPLE REDIS DISTRIBUTED LOCK                        │
│                                                                          │
│  ACQUIRE:                                                                │
│    SET lock:resource <unique_id> NX EX 30                                │
│    │                                                                     │
│    ├── NX = only set if Not eXists (mutual exclusion)                   │
│    ├── EX 30 = auto-expire in 30 seconds (deadlock prevention)          │
│    └── unique_id = UUID (prevents wrong client from releasing)          │
│                                                                          │
│  RELEASE (must be atomic — use Lua):                                     │
│    if redis.call("GET", KEYS[1]) == ARGV[1] then                        │
│        return redis.call("DEL", KEYS[1])                                │
│    else                                                                  │
│        return 0                                                          │
│    end                                                                   │
│                                                                          │
│  WHY LUA? Without it:                                                    │
│    1. GET lock:resource → returns my_id (matches!)                      │
│    2. ← context switch / GC pause / network delay                       │
│    3. Lock expires, another client acquires it                           │
│    4. DEL lock:resource → DELETES SOMEONE ELSE'S LOCK!                  │
│                                                                          │
│  PROBLEM: Single Redis instance = single point of failure                │
│  If Redis dies while lock is held → lock is lost → mutual exclusion     │
│  broken.                                                                 │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Redlock Algorithm

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          REDLOCK ALGORITHM                                    │
│                                                                               │
│  Uses N independent Redis instances (typically 5), NO replication.            │
│                                                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │ Redis 1  │  │ Redis 2  │  │ Redis 3  │  │ Redis 4  │  │ Redis 5  │     │
│  │ (indep.) │  │ (indep.) │  │ (indep.) │  │ (indep.) │  │ (indep.) │     │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │
│       ▲             ▲             ▲             ▲             ▲              │
│       │             │             │             │             │              │
│       └─────────────┼─────────────┼─────────────┼─────────────┘              │
│                     │             │             │                             │
│                     └─────────────┼─────────────┘                             │
│                                   │                                           │
│                              ┌────────┐                                       │
│                              │ Client │                                       │
│                              └────────┘                                       │
│                                                                               │
│  ACQUIRE SEQUENCE:                                                            │
│  1. Record start time T1                                                      │
│  2. Try to acquire lock on ALL N instances sequentially                       │
│     - Use small timeout per instance (5-50ms)                                │
│     - Same key, same random value, same TTL                                  │
│  3. Record end time T2                                                        │
│  4. Lock acquired IF:                                                         │
│     a. Acquired on majority (≥ N/2 + 1 = 3 of 5)                            │
│     b. Total elapsed (T2 - T1) < TTL                                        │
│     c. Effective TTL = TTL - (T2 - T1) - clock_drift                        │
│  5. If failed: release on ALL instances (even ones that succeeded)           │
│                                                                               │
│  RELEASE: Send DEL (with Lua check) to ALL N instances                       │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### The Kleppmann vs antirez Debate (2016) — Interview Gold

```
┌──────────────────────────────────────────────────────────────────────────┐
│          THE GREAT DISTRIBUTED LOCK DEBATE (2016)                        │
│                                                                          │
│  Martin Kleppmann                    Salvatore Sanfilippo (antirez)      │
│  (DDIA author)                       (Redis creator)                     │
│  "How to do distributed              "Is Redlock safe?"                  │
│   locking" blog post                  (response blog post)               │
│                                                                          │
│  KLEPPMANN'S ARGUMENT:                                                   │
│  ─────────────────────                                                   │
│  1. GC Pause Attack:                                                     │
│     Client A acquires lock                                               │
│     Client A enters long GC pause (or process pause)                    │
│     Lock expires (TTL)                                                   │
│     Client B acquires lock                                               │
│     Client A resumes, thinks it still has lock                          │
│     BOTH clients access resource → UNSAFE                               │
│                                                                          │
│     A: ──[lock]──────[GC PAUSE]──────────────[resumes, uses lock]───    │
│     B: ──────────────────────────[lock]──────[uses lock]────────────    │
│                           ▲                                              │
│                     A's lock expired                                     │
│                                                                          │
│  2. Clock Drift:                                                         │
│     Redlock relies on reasonably synchronized clocks                    │
│     NTP jumps, VM clock skew can violate assumptions                    │
│                                                                          │
│  3. Solution: Use fencing tokens (monotonic counter)                    │
│     Lock service returns token 34 to A, token 35 to B                  │
│     Storage layer rejects writes with old tokens                        │
│     → But Redis can't generate monotonic fencing tokens                 │
│                                                                          │
│  ANTIREZ'S COUNTER:                                                      │
│  ─────────────────                                                       │
│  1. Auto-release is NECESSARY — without it, crashed clients hold        │
│     locks forever → deadlock is worse than rare overlap                  │
│  2. Clock drift is bounded and manageable with NTP                      │
│  3. The GC pause scenario applies to ALL distributed locks,             │
│     not just Redlock                                                     │
│  4. In practice, Redlock is safe enough for most use cases              │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────┐       │
│  │  INTERVIEW TAKEAWAY:                                         │       │
│  │                                                               │       │
│  │  For EFFICIENCY (avoid duplicate work):                       │       │
│  │    Simple Redis lock is fine. Worst case = work done twice.   │       │
│  │                                                               │       │
│  │  For CORRECTNESS (safety-critical):                           │       │
│  │    Use proper consensus system (ZooKeeper, etcd) with         │       │
│  │    fencing tokens. Redlock is NOT sufficient.                 │       │
│  │                                                               │       │
│  │  Most real systems need efficiency locks, not correctness     │       │
│  │  locks. Know the difference.                                  │       │
│  └──────────────────────────────────────────────────────────────┘       │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Pub/Sub & Streams

### Pub/Sub Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        REDIS PUB/SUB                                     │
│                                                                          │
│  ┌──────────┐    PUBLISH "chat:room1" "hello"    ┌──────────────────┐  │
│  │Publisher 1│──────────────────────────────────►│                  │  │
│  └──────────┘                                    │                  │  │
│                                                  │   Redis Server   │  │
│  ┌──────────┐    PUBLISH "chat:room1" "world"    │                  │  │
│  │Publisher 2│──────────────────────────────────►│   Channel:       │  │
│  └──────────┘                                    │   "chat:room1"   │  │
│                                                  │                  │  │
│                                                  └────────┬─────────┘  │
│                                                           │             │
│                              ┌─────────────────┬──────────┴──────────┐ │
│                              │                 │                      │ │
│                              ▼                 ▼                      ▼ │
│                        ┌──────────┐     ┌──────────┐          ┌────────┐│
│                        │Subscriber│     │Subscriber│          │Sub 3   ││
│                        │    1     │     │    2     │          │        ││
│                        └──────────┘     └──────────┘          └────────┘│
│                                                                          │
│  FIRE-AND-FORGET:                                                        │
│  ✗ No message persistence — if subscriber is offline, message is LOST   │
│  ✗ No acknowledgment — publisher doesn't know if anyone received it     │
│  ✗ No consumer groups — every subscriber gets every message             │
│  ✗ No replay — can't go back and read old messages                      │
│                                                                          │
│  GOOD FOR: Real-time notifications, chat, invalidation signals          │
│  BAD FOR:  Anything requiring delivery guarantees                        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Redis Streams — Kafka-like within Redis

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         REDIS STREAMS                                         │
│                                                                               │
│  Streams = persistent, ordered, append-only log with consumer groups.         │
│  Think of it as "Kafka-lite" inside Redis.                                    │
│                                                                               │
│  STREAM: "orders"                                                             │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │  ID              │  Fields                                             │  │
│  │  1672531200000-0 │  {product: "laptop", qty: 1, user: "alice"}        │  │
│  │  1672531200001-0 │  {product: "phone", qty: 2, user: "bob"}           │  │
│  │  1672531200002-0 │  {product: "tablet", qty: 1, user: "charlie"}      │  │
│  │  1672531200003-0 │  {product: "laptop", qty: 3, user: "dave"}         │  │
│  │  ...             │  (append-only, never modified)                      │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│  CONSUMER GROUPS (like Kafka consumer groups):                                │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────┐            │
│  │  Group: "order-processors"                                    │            │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐             │            │
│  │  │ Consumer A  │  │ Consumer B  │  │ Consumer C  │             │            │
│  │  │ reads: 0,3  │  │ reads: 1    │  │ reads: 2    │             │            │
│  │  └────────────┘  └────────────┘  └────────────┘             │            │
│  │  Each message delivered to exactly ONE consumer in group     │            │
│  └──────────────────────────────────────────────────────────────┘            │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────┐            │
│  │  Group: "analytics"                                           │            │
│  │  ┌────────────┐  ┌────────────┐                               │            │
│  │  │ Consumer X  │  │ Consumer Y  │                               │            │
│  │  │ reads: 0,2  │  │ reads: 1,3  │  (independent from above)   │            │
│  │  └────────────┘  └────────────┘                               │            │
│  └──────────────────────────────────────────────────────────────┘            │
│                                                                               │
│  COMMANDS:                                                                    │
│  XADD orders * product laptop qty 1 user alice    → append entry             │
│  XREAD COUNT 10 BLOCK 5000 STREAMS orders $       → read new entries         │
│  XREADGROUP GROUP order-processors consumer-A     → consumer group read      │
│  XACK orders order-processors 1672531200000-0     → acknowledge processing   │
│  XPENDING orders order-processors                 → check unACKed messages   │
│                                                                               │
│  PUB/SUB vs STREAMS:                                                          │
│  ┌──────────────────────┬────────────────────────┐                           │
│  │  Pub/Sub              │  Streams                │                           │
│  │  Fire-and-forget      │  Persistent             │                           │
│  │  No history           │  Full history (replay)  │                           │
│  │  All subs get all msg │  Consumer groups (1:1)  │                           │
│  │  No ACK               │  XACK (at-least-once)   │                           │
│  │  Faster               │  Slightly slower         │                           │
│  │  Notifications, chat  │  Task queues, events     │                           │
│  └──────────────────────┴────────────────────────┘                           │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Usage at Scale

### Twitter — Largest Known Redis Deployment

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   TWITTER'S REDIS USAGE                                   │
│                                                                          │
│  SCALE:                                                                  │
│  • 10,000+ Redis instances                                               │
│  • 105 TB+ total RAM                                                     │
│  • 39 million queries per second                                         │
│                                                                          │
│  PRIMARY USE: Timeline Service                                           │
│                                                                          │
│  User tweets → Fan-out to followers' timelines (stored in Redis)         │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  @user_A tweets "Hello"                                          │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Fan-out Service                                                 │   │
│  │       │                                                          │   │
│  │       ├──► Redis: LPUSH timeline:follower_1 <tweet_id>          │   │
│  │       ├──► Redis: LPUSH timeline:follower_2 <tweet_id>          │   │
│  │       ├──► Redis: LPUSH timeline:follower_3 <tweet_id>          │   │
│  │       └──► ... (for each of @user_A's 50K followers)            │   │
│  │                                                                  │   │
│  │  When follower opens app:                                        │   │
│  │  LRANGE timeline:follower_1 0 200                                │   │
│  │  → returns latest 200 tweet IDs → hydrate from DB               │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  CUSTOM DATA STRUCTURES (Twitter contributions to Redis):                │
│                                                                          │
│  1. Hybrid List (now "quicklist" in Redis)                               │
│     Chains of listpacks → low memory, avoids huge realloc               │
│                                                                          │
│  2. B-Tree Sets                                                          │
│     Point lookups (O(1)) + range queries (O(log N)) in one structure    │
│     Used for billions of cached objects                                   │
│                                                                          │
│  TOPOLOGY: Proxy-based cluster with centralized cluster manager          │
│  (not Redis Cluster — custom sharding layer)                             │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### GitHub, Discord, Instagram — Usage Patterns

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   REDIS AT MAJOR COMPANIES                                │
│                                                                          │
│  GITHUB:                                                                 │
│  ├── API Rate Limiting (4+ billion requests/day)                        │
│  │   Key: ratelimit:{api_key}:{minute}                                  │
│  │   INCR + EXPIRE for sliding window                                   │
│  ├── Live repository statistics                                         │
│  ├── Feature flags (per-user/per-org)                                   │
│  └── Job queues (Resque, backed by Redis Lists)                         │
│                                                                          │
│  DISCORD:                                                                │
│  ├── Message indexing queue → Elasticsearch                              │
│  │   Problem: Redis CPU maxed out when Elasticsearch lagged             │
│  │   Messages DROPPED because queue backed up                           │
│  ├── Presence system (who's online)                                     │
│  ├── Rate limiting for API and WebSocket events                         │
│  └── Session storage for millions of concurrent users                   │
│                                                                          │
│  INSTAGRAM:                                                              │
│  ├── Like counters (INCR per post — billions of increments)             │
│  ├── Activity feeds (who liked/followed/commented)                      │
│  ├── Explore page ranking signals                                       │
│  └── Story view tracking                                                │
│                                                                          │
│  UBER:                                                                   │
│  ├── Geospatial driver matching (GEOADD + GEOSEARCH)                   │
│  ├── Surge pricing signals                                              │
│  ├── Session tokens                                                     │
│  └── Real-time trip state machine                                       │
│                                                                          │
│  STRIPE:                                                                 │
│  ├── Idempotency key storage (prevent double charges)                   │
│  ├── Rate limiting per API key                                          │
│  └── Distributed locks for payment processing                           │
│                                                                          │
│  PINTEREST:                                                              │
│  ├── Feed generation (similar to Twitter's approach)                    │
│  ├── A/B testing feature flags                                          │
│  └── Real-time analytics counters                                       │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 13. When Redis Failed — Production Incidents

### Incident 1: Redis Cloud Outage (February 2016)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Redis Cloud — Software Update Bug                             │
│  SOURCE:   redis.io/blog/february-8th-outage-post-mortem                │
│  DURATION: 2.5 hours                                                     │
│  IMPACT:   Multiple Google Cloud Platform clusters DOWN                  │
│                                                                          │
│  TIMELINE:                                                               │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Automatic weekly upgrade deployed to nodes                      │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Bug in update caused multiple nodes in SAME cluster to fail     │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Cluster lost quorum → entire cluster DOWN                       │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Manual rebuild required (2.5 hours)                             │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ROOT CAUSE: Rolling update didn't enforce "never update >1 node in    │
│  same cluster simultaneously"                                            │
│                                                                          │
│  LESSONS:                                                                │
│  ✓ Rolling updates must be cluster-aware                                │
│  ✓ Canary deployments before fleet-wide rollout                        │
│  ✓ Automated rollback on failure detection                              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Incident 2: OOM + Failover Data Loss (March 2025)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Memory Fragmentation → OOM → Failed Failover                 │
│  SOURCE:   prodrescueai.com post-mortem                                  │
│  DURATION: 2.5 hours                                                     │
│  IMPACT:   180,000 users lost session data                               │
│                                                                          │
│  CHAIN OF FAILURES:                                                      │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  1. Cache keys without TTLs → unbounded growth to 40M keys      │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  2. Large key eviction → memory fragmentation spike              │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  3. OOM crash on primary                                         │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  4. Replica promoted... but it was 8 MINUTES behind              │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  5. 180,000 sessions lost (8 min of replication lag)             │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  6. Cache stampede → all 180K users hit PostgreSQL               │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  7. PostgreSQL overwhelmed → cascading failure                   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ROOT CAUSES:                                                            │
│  ✗ No TTL on cache keys → unbounded memory growth                       │
│  ✗ No monitoring on replication lag                                      │
│  ✗ No circuit breaker between cache and database                        │
│  ✗ maxmemory-policy: noeviction (should have been allkeys-lru)          │
│                                                                          │
│  LESSONS:                                                                │
│  ✓ ALWAYS set TTL on cache keys                                         │
│  ✓ Monitor replication lag with alerts                                   │
│  ✓ Use circuit breakers to prevent cache stampede → DB cascade          │
│  ✓ Right-size maxmemory with headroom for fragmentation                 │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Incident 3: The 1-Line Bug — KEYS * in Production

```
┌──────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: KEYS * from backup script crashed Redis at 2 AM              │
│  SOURCE:   blog.devgenius.io                                             │
│  DURATION: 4 days to find root cause                                     │
│                                                                          │
│  WHAT HAPPENED:                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Backup cron job ran: redis.keys('*') at 2:00 AM                 │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  KEYS * scans ALL keys → O(N) where N = millions                 │   │
│  │  BLOCKS the single thread for seconds                            │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Memory spike (building response with all key names)             │   │
│  │  maxmemory-policy: noeviction                                    │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  OOM → Redis crashes                                             │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Restarts → backup runs again → crashes again (every night)      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  FIX: Replace KEYS * with SCAN (cursor-based, non-blocking)            │
│  FIX: Change eviction to allkeys-lru                                    │
│  FIX: Rename KEYS command in redis.conf to prevent accidental use       │
│                                                                          │
│  ⚠ NEVER use KEYS in production. Use SCAN instead.                      │
│    rename-command KEYS ""  (disable it entirely)                         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Incident 4: ElastiCache Failover — New Primary Non-Functional

```
┌──────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: AWS ElastiCache Auto-Failover Succeeded on Paper, Failed IRL │
│  SOURCE:   medium.com/@itsnarayan                                        │
│  DURATION: 20 minutes outage                                             │
│                                                                          │
│  WHAT HAPPENED:                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Primary Redis node went down                                    │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  ElastiCache auto-failover kicked in → promoted replica          │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  New primary was memory-exhausted + SWAP thrashing               │   │
│  │  (it had been behind on replication, catching up consumed RAM)   │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Technically "primary" but responding in seconds, not ms         │   │
│  │  Effectively a 20-minute outage                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  LESSONS:                                                                │
│  ✓ Monitor replica health (memory, CPU, SWAP) not just "is it alive"   │
│  ✓ Set maxmemory on replicas too                                        │
│  ✓ Test failover regularly (chaos engineering)                          │
│  ✓ Alerting on replication lag + memory usage                           │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Incident 5: Kubernetes StatefulSet Disaster (December 2025)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  INCIDENT: Disk resize attempt → full Redis cluster restart              │
│  SOURCE:   AdGuard VPN post-mortem                                       │
│  IMPACT:   Cascading OOM failures in dependent services                  │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Operator attempted disk resize on Redis StatefulSet             │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Orphan pod deletion + StatefulSet recreation triggered          │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Kubernetes reconciliation → ALL Redis pods restarted            │   │
│  │  (not rolling — simultaneous)                                    │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Missing readiness probes → pods marked "ready" before data     │   │
│  │  was actually loaded                                             │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  Dependent services got empty responses → retry storms           │   │
│  │  → downstream OOM failures                                       │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  LESSONS:                                                                │
│  ✓ Readiness probes that verify data is loaded, not just port open     │
│  ✓ PodDisruptionBudget to prevent simultaneous restarts                │
│  ✓ Test operational procedures in staging first                         │
│  ✓ Circuit breakers in dependent services                               │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 14. When NOT to Use Redis

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    WHEN REDIS IS THE WRONG CHOICE                        │
│                                                                          │
│  1. PRIMARY DATABASE FOR LARGE DATASETS                                  │
│     Redis stores everything in RAM. At $5-10/GB for RAM vs              │
│     $0.10/GB for SSD, storing 1TB in Redis costs ~$5,000-10,000/month  │
│     Use: PostgreSQL, MySQL, MongoDB for primary storage                  │
│                                                                          │
│  2. COMPLEX QUERIES (JOINs, aggregations, full-text search)             │
│     Redis has no query engine. You must know the exact key.             │
│     Use: PostgreSQL, Elasticsearch                                       │
│                                                                          │
│  3. SIMPLE KEY-VALUE CACHE AT EXTREME SCALE                              │
│     Memcached uses 20-30% less memory for simple strings                │
│     Memcached saturates multiple CPU cores (multi-threaded)             │
│     Meta processes 5 billion Memcached requests/second                  │
│     Use: Memcached for pure string caching                              │
│                                                                          │
│  4. STRONG CONSISTENCY REQUIREMENTS                                      │
│     Redis replication is async → writes can be lost on failover         │
│     Redis Cluster doesn't prevent split-brain write loss                │
│     Use: etcd, ZooKeeper, CockroachDB, Spanner                         │
│                                                                          │
│  5. DATASET LARGER THAN AVAILABLE RAM                                    │
│     Even with Redis Cluster, every byte lives in RAM                    │
│     No overflow to disk (Redis-on-Flash is vendor-specific)             │
│     Use: RocksDB, Cassandra, or tiered storage solutions                │
│                                                                          │
│  6. DURABLE MESSAGE QUEUE                                                │
│     Pub/Sub is fire-and-forget. Streams are better but still            │
│     limited vs dedicated systems for high-throughput, multi-consumer.   │
│     Use: Kafka, RabbitMQ, Amazon SQS for mission-critical messaging    │
│                                                                          │
│  7. LONG-RUNNING COMPUTATIONS                                            │
│     Lua scripts block the single thread. A 5-second script blocks      │
│     ALL clients for 5 seconds.                                           │
│     Use: Background job systems (Sidekiq, Celery)                       │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  REDIS vs MEMCACHED — QUICK DECISION:                            │   │
│  │                                                                   │   │
│  │  Need data structures? → Redis                                    │   │
│  │  Need persistence?     → Redis                                    │   │
│  │  Need pub/sub?         → Redis                                    │   │
│  │  Need Lua scripting?   → Redis                                    │   │
│  │  Pure string cache?    → Memcached (simpler, less memory)         │   │
│  │  Multi-core saturation? → Memcached                               │   │
│  │  Horizontal scale?     → Both (Redis Cluster or Memcached pool)  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 15. Anti-Patterns That Kill Redis

```
┌──────────────────────────────────────────────────────────────────────────┐
│                  ANTI-PATTERNS THAT KILL REDIS                            │
│                                                                          │
│  1. KEYS * IN PRODUCTION                                                 │
│     ─────────────────────                                                │
│     O(N) scan of entire keyspace. Blocks single thread.                 │
│     10M keys → several seconds of complete unavailability.              │
│     FIX: Use SCAN with cursor. Disable KEYS via rename-command.         │
│                                                                          │
│  2. HUGE KEYS (Big Keys)                                                 │
│     ────────────────────                                                 │
│     List with 10M elements. Hash with 1M fields. 50MB string.          │
│     DEL on big key blocks thread (use UNLINK for async delete).         │
│     Serialization/transfer of big key spikes network + CPU.             │
│     FIX: Break into smaller keys. Use UNLINK instead of DEL.            │
│     Detect: redis-cli --bigkeys                                          │
│                                                                          │
│  3. NO TTL ON CACHE KEYS                                                 │
│     ──────────────────────                                               │
│     Memory grows unbounded until OOM.                                    │
│     Even with eviction, you lose control over what stays.               │
│     FIX: Always set TTL. Use volatile-lru if mixing cache + data.       │
│                                                                          │
│  4. HOT KEY (single key receives disproportionate traffic)              │
│     ─────────                                                            │
│     In Redis Cluster, one key = one slot = one node.                    │
│     600K concurrent users hitting one product key = one node at 100%.   │
│     FIX: Key mirroring ({key}:shard1, {key}:shard2, ...)               │
│     FIX: Client-side caching with short TTL.                            │
│     FIX: Read replicas for hot GET keys.                                │
│                                                                          │
│  5. CACHE STAMPEDE (Thundering Herd)                                     │
│     ───────────────────────────────                                      │
│     Popular key expires → 40 instances simultaneously query DB.         │
│     FIX: Distributed lock (only one rebuilds cache).                    │
│     FIX: Staggered TTL with jitter (TTL + random(0, 60)).              │
│     FIX: Logical expiration (don't actually expire the key).            │
│                                                                          │
│  6. CACHE PENETRATION (nonexistent keys)                                 │
│     ─────────────────────────────────────                                │
│     Attacker queries keys that don't exist → every request hits DB.     │
│     FIX: Cache null results with short TTL.                             │
│     FIX: Bloom filter in front of cache.                                │
│                                                                          │
│  7. CACHE AVALANCHE (mass expiration)                                    │
│     ─────────────────────────────────                                    │
│     All cache keys expire at the same time → DB flooded.                │
│     FIX: Random jitter on TTL values.                                   │
│     FIX: Warm cache before peak traffic.                                │
│     FIX: Multi-layer cache (L1 local + L2 Redis).                      │
│                                                                          │
│  8. STORING BLOBS IN REDIS                                               │
│     ───────────────────────                                              │
│     Storing images, files, large JSON blobs.                            │
│     Wastes expensive RAM. Causes big key problems.                      │
│     FIX: Store in S3/blob storage, keep reference in Redis.             │
│                                                                          │
│  9. TREATING REDIS AS PRIMARY DATABASE                                   │
│     ──────────────────────────────────                                   │
│     No ACID transactions. Async replication = data loss risk.           │
│     FIX: Use Redis as cache/index/session store with DB as source       │
│     of truth.                                                            │
│                                                                          │
│  10. FORGETTING ABOUT MEMORY FRAGMENTATION                               │
│      ──────────────────────────────────────                              │
│      Heavy churn (create/delete) fragments jemalloc allocator.          │
│      RSS grows even if used_memory is stable.                           │
│      FIX: Monitor mem_fragmentation_ratio. Enable activedefrag.         │
│      FIX: Scheduled restarts if ratio > 1.5.                            │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Performance Tuning Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────────┐
│                  REDIS PERFORMANCE TUNING CHEAT SHEET                    │
│                                                                          │
│  CONFIGURATION                                                           │
│  ─────────────                                                           │
│  maxmemory 75%_of_RAM        Leave headroom for CoW, fragmentation      │
│  maxmemory-policy allkeys-lru Most common for caching workloads         │
│  maxmemory-samples 10        Better eviction accuracy (default 5)       │
│  hz 10                       Background task frequency (default 10)     │
│  tcp-backlog 511             Increase for high connection rates         │
│  timeout 300                 Close idle connections after 5 min         │
│                                                                          │
│  PERSISTENCE                                                             │
│  ───────────                                                             │
│  aof-use-rdb-preamble yes    Hybrid persistence (best of both)          │
│  appendfsync everysec        Balanced durability/performance             │
│  no-appendfsync-on-rewrite yes Prevent I/O blocking during rewrite     │
│  auto-aof-rewrite-percentage 100                                        │
│  auto-aof-rewrite-min-size 64mb                                         │
│                                                                          │
│  LINUX KERNEL                                                            │
│  ────────────                                                            │
│  vm.overcommit_memory = 1     Allow fork() for BGSAVE                   │
│  Disable THP                  echo never > .../transparent_hugepage     │
│  net.core.somaxconn = 65535   Match tcp-backlog                         │
│  vm.swappiness = 1            Prevent swapping (never 0, keep 1)        │
│                                                                          │
│  CLIENT-SIDE                                                             │
│  ────────────                                                            │
│  Use connection pooling        Don't connect/disconnect per request      │
│  Pipeline batches of 100+     5-10x throughput improvement              │
│  Use MGET/MSET for bulk ops   Fewer round trips                         │
│  Prefer SCAN over KEYS        Non-blocking iteration                    │
│  Use UNLINK over DEL           Async delete for big keys                │
│  Hash tags for multi-key ops  Ensure same slot in Cluster               │
│                                                                          │
│  MONITORING                                                              │
│  ──────────                                                              │
│  INFO memory                  used_memory, fragmentation_ratio          │
│  INFO stats                   hit rate, evictions, ops/sec              │
│  INFO replication             repl lag, connected replicas              │
│  SLOWLOG GET 10               Commands that took > slowlog-log-slower   │
│  LATENCY LATEST               Latency spikes by event                   │
│  CLIENT LIST                  Connection states, idle time              │
│  redis-cli --bigkeys          Find large keys                           │
│  redis-cli --memkeys          Memory usage per key (sampling)           │
│                                                                          │
│  KEY METRICS TO ALERT ON:                                                │
│  ─────────────────────────                                               │
│  ┌─────────────────────────┬──────────────────────────────┐             │
│  │  Metric                 │  Alert Threshold              │             │
│  ├─────────────────────────┼──────────────────────────────┤             │
│  │  used_memory_rss        │  > 80% of maxmemory          │             │
│  │  mem_fragmentation_ratio│  > 1.5 or < 1.0              │             │
│  │  evicted_keys           │  any non-zero (unexpected)    │             │
│  │  connected_clients      │  > 80% of maxclients          │             │
│  │  instantaneous_ops      │  sudden drop (potential block)│             │
│  │  master_repl_offset     │  lag > 1MB (replica behind)   │             │
│  │  rejected_connections   │  any non-zero                 │             │
│  │  keyspace_hit_ratio     │  < 90% (cache ineffective)    │             │
│  └─────────────────────────┴──────────────────────────────┘             │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 17. Interview Questions — Medium

### Q1: Explain how Redis achieves high throughput despite being single-threaded.

**Answer:**
Redis leverages I/O multiplexing (epoll/kqueue) to handle thousands of concurrent connections on one thread. The single thread eliminates lock contention, context switches, and mutex overhead. All data operations happen in-memory (100ns latency vs 150,000ns for SSD), using optimized data structures. Since Redis 6.0, network I/O (read/write) can optionally use multiple threads while command execution stays single-threaded. Background threads handle slow operations like AOF fsync and async key deletion.

### Q2: You have a Redis instance with 50M keys. How do you find all keys matching a pattern without blocking?

**Answer:**
Never use `KEYS pattern` — it scans all 50M keys in O(N) and blocks the server. Use `SCAN` with a cursor:

```
SCAN 0 MATCH "user:*" COUNT 1000
```

SCAN is cursor-based, returns a batch (COUNT is a hint, not guarantee), and yields the CPU between batches so other commands can execute. The trade-off: SCAN may return duplicates (use client-side dedup) and doesn't guarantee consistency (keys added/removed during scan may or may not appear).

### Q3: Describe the difference between RDB and AOF persistence. When would you use each?

**Answer:**
RDB creates point-in-time snapshots via fork() + child process writing a binary dump. Fast restart, compact file, but data loss between snapshots (minutes). AOF logs every write command; with `appendfsync everysec`, you lose at most 1 second of data. Slower restart (replays all commands), larger files. Production recommendation: use hybrid mode (`aof-use-rdb-preamble yes`) which combines RDB prefix for fast loading with AOF tail for minimal data loss.

### Q4: What is a cache stampede and how do you prevent it?

**Answer:**
When a popular cache key expires, hundreds of concurrent requests simultaneously miss the cache and hit the database. Solutions:
1. **Distributed mutex:** Use `SET lock NX EX 3` — first request acquires lock, rebuilds cache; others wait or use stale data
2. **Staggered TTL:** Add random jitter: `TTL + random(0, 60 seconds)` prevents mass simultaneous expiration
3. **Logical expiration:** Store value with an embedded expiry timestamp; serve stale data while one process refreshes in background
4. **Early recompute:** Probabilistically refresh before TTL expires based on remaining TTL

### Q5: How does Redis Cluster handle a command for a key that's on a different node?

**Answer:**
The client sends the command to any node. If the key's hash slot (CRC16 mod 16384) maps to a different node, the server returns a `MOVED` redirect with the correct node address. Smart clients cache the slot→node mapping and update it on MOVED responses. During slot migration, `ASK` redirects are used temporarily — the client sends the command to the target node prefixed with ASKING, but doesn't update its slot table (migration is still in progress).

### Q6: What's the difference between DEL and UNLINK? When does it matter?

**Answer:**
DEL deletes keys synchronously on the main thread. For a key with 10 million elements (big list, big set), DEL blocks the server for seconds while freeing memory. UNLINK (Redis 4.0+) removes the key from the keyspace synchronously (O(1)) but delegates actual memory reclamation to a background thread. Use UNLINK for any key that might be large. Use `lazyfree-lazy-eviction yes` and `lazyfree-lazy-expire yes` to make eviction and expiration also use background freeing.

### Q7: Explain Redis Sorted Set internals. Why does it use two data structures?

**Answer:**
Sorted Sets use a skip list AND a hash table simultaneously. The skip list provides O(log N) range queries and ordered iteration (ZRANGE, ZRANGEBYSCORE, ZRANK). The hash table provides O(1) point lookups by member name (ZSCORE). Without the hash table, ZSCORE would require O(log N) skip list traversal. Without the skip list, ZRANGE would require O(N log N) sorting. The dual structure trades memory for speed, giving optimal complexity for both access patterns.

### Q8: How does Redis handle key expiration? What's the "expired keys still consuming memory" problem?

**Answer:**
Two strategies: (1) Lazy expiration — checked on access; if TTL passed, delete and return nil. (2) Active expiration — every 100ms, sample 20 keys with TTL; delete expired ones; if >25% expired, repeat. The problem: keys that are set with TTL but never accessed again aren't caught by lazy expiration. Active sampling is probabilistic — with millions of keys, some expired keys linger. This creates "phantom memory usage" where INFO shows available memory but expired keys haven't been reclaimed yet. Increasing the `hz` config (default 10) runs active expiration more frequently at the cost of CPU.

### Q9: Design a rate limiter using Redis. Compare sliding window vs token bucket approaches.

**Answer:**
**Sliding window with Sorted Set:**
```
MULTI
  ZREMRANGEBYSCORE ratelimit:user:123 0 (now - window_size)
  ZADD ratelimit:user:123 now unique_id
  ZCARD ratelimit:user:123
  EXPIRE ratelimit:user:123 window_size
EXEC
```
If ZCARD > limit, reject. Sorted set members are timestamps; remove old ones, count remaining.

**Token bucket with Lua:**
Read current tokens and last_refill_time. Calculate tokens to add based on elapsed time. If tokens > 0, decrement and allow; else reject. Lua ensures atomicity.

Trade-offs: Sliding window is more precise but uses more memory (one entry per request). Token bucket is more memory-efficient but allows small bursts.

### Q10: What happens during a Redis Sentinel failover? Walk through the steps.

**Answer:**
1. A Sentinel detects master unreachable for `down-after-milliseconds` → marks it SDOWN (subjective down)
2. Sentinel asks other Sentinels — if quorum agrees → ODOWN (objective down)
3. Sentinels run Raft-like leader election → one Sentinel coordinates failover
4. Leader selects best replica: lowest slave-priority > highest replication offset > smallest run ID
5. Selected replica receives `SLAVEOF NO ONE` → becomes new master
6. Other replicas reconfigured to replicate from new master
7. Sentinels update configuration; clients notified via Sentinel pub/sub
8. Total time: ~30-35 seconds (detection 30s + election 2s + promotion 1s)

---

## 18. Interview Questions — Hard

### Q1: You're at 100K QPS on a single Redis instance and need to scale to 1M QPS. What's your strategy?

**Answer:**
First, verify the bottleneck. At 100K QPS, a single Redis instance is hitting its CPU ceiling (single-threaded command execution).

**Read-heavy (>80% reads):** Add read replicas. With 10 replicas, reads scale to ~1M QPS while writes stay on master. Clients must tolerate stale reads (async replication).

**Write-heavy or balanced:** Redis Cluster with N masters. For 1M QPS, likely need 10-15 shards (each doing 70-100K). Requires: hash tags for multi-key operations, CROSSSLOT-aware application code, and cluster-aware client library.

**Additional layers:**
- Client-side caching (Redis 6.0+ server-assisted invalidation) for hot keys
- L1 local cache (Caffeine/Guava) with Redis as L2
- Key mirroring/sharding for hot keys
- Pipeline/MGET batching to reduce round trips
- Enable I/O threads (Redis 6.0+) for network throughput

**What NOT to do:** Don't throw more memory at it — the bottleneck is CPU, not RAM.

### Q2: Explain the Redlock controversy. When would you use Redlock vs ZooKeeper for distributed locking?

**Answer:**
Redlock acquires locks on N/2+1 of N independent Redis instances (no replication). Martin Kleppmann argued it's unsafe because: (1) GC pauses can cause a client to use an expired lock while another client holds it — both access the resource simultaneously. (2) It relies on clock synchronization assumptions that can be violated. (3) Redis can't provide monotonic fencing tokens. Antirez countered that auto-release is necessary to prevent deadlocks and the GC pause problem affects all distributed locks.

**Use Redlock when:** Lock is for efficiency (prevent duplicate work). Worst case = work done twice. Example: deduplicating webhook deliveries, rate limiting, cache refresh coordination.

**Use ZooKeeper/etcd when:** Lock is for correctness (safety-critical). Concurrent access = data corruption. Example: distributed leader election, payment processing where double-charge is unacceptable. These systems provide sequential ordering (zxid) that serves as a fencing token.

### Q3: Your Redis Cluster has a hot key problem — one shard handles 80% of traffic. How do you solve it without changing the application's key structure?

**Answer:**
**Immediate (minutes):** Client-side caching. Use Redis 6.0+ client-side caching with server-assisted invalidation (RESP3 protocol). Hot key is cached locally in each app instance with ~100ms TTL. Server sends invalidation when key changes.

**Short-term (hours):** Key mirroring. Create N copies: `{hotkey}:1`, `{hotkey}:2`, ..., `{hotkey}:N`. Client randomly selects a shard for reads: `GET {hotkey}:{random(1,N)}`. Writes update all copies (fan-out). This distributes reads across N different hash slots → N different nodes.

**Medium-term (days):** Read replicas. Add replicas to the hot shard's master and direct reads to replicas using `READONLY` mode. Each replica handles its share of read traffic.

**Long-term (weeks):** Application-level sharding of the hot key. For counters: sharded counter pattern — split one counter into 100 sub-counters, aggregate on read. For leaderboards: partition by score range across multiple sorted sets.

**Monitoring:** Use `redis-cli --hotkeys` (with LFU policy) or `MONITOR` (careful — MONITOR itself impacts performance) to detect hot keys proactively.

### Q4: Design a distributed session store using Redis that handles 10M concurrent sessions. Cover: consistency, failover, and what happens when Redis goes down.

**Answer:**
**Architecture:**
```
┌──────────────┐    ┌──────────────────────────────────┐
│  App Servers  │───►│  Redis Cluster (6 nodes)          │
│  (stateless)  │    │  3 masters + 3 replicas           │
│               │    │  Sessions distributed by hash slot │
│  Session ID   │    │  Key: session:{sid}               │
│  in cookie    │    │  Value: HASH (user_id, role, etc) │
│  (signed JWT) │    │  TTL: 30 minutes (sliding)        │
└──────────────┘    └──────────────────────────────────┘
```

**Key design:**
- `session:{uuid}` → Redis HASH with fields: user_id, role, permissions, csrf_token, created_at, last_access
- TTL 30 min with sliding expiration (EXPIRE reset on every access)
- 10M sessions × ~500 bytes each ≈ 5 GB (fits in moderate cluster)

**Consistency:** Use `WAIT 1 1000` after critical session writes (login, permission change) to ensure at least one replica has the data before returning to client. Accept eventual consistency for non-critical updates (last_access timestamp).

**Failover:** Redis Sentinel or Cluster auto-failover. Window of data loss = ~seconds of writes. Affected users get logged out (session not found) → redirect to login. This is acceptable for sessions.

**Total Redis failure:** Graceful degradation path:
1. Circuit breaker detects Redis down → flip to degraded mode
2. Fall back to signed JWT with embedded claims (stateless, no Redis needed)
3. JWT has short expiry (5 min) to limit blast radius if session was revoked
4. When Redis recovers, migrate back (next login creates Redis session)

### Q5: Redis Cluster says it provides "at most" data loss of NODE_TIMEOUT seconds during a network partition. Explain exactly why and what the worst case is.

**Answer:**
During a network partition, the master on the minority side continues accepting writes for up to NODE_TIMEOUT seconds before it detects it can't reach a majority and stops accepting writes. The majority side elects a new master from a replica. When the partition heals, the old master discovers a new master exists, demotes itself to replica, and does a full resync — discarding all writes it accepted during the partition.

Worst case is actually **worse than NODE_TIMEOUT** if:
1. The minority-side master had clients with open connections + pending pipelines that were already sent
2. The replica that was promoted was behind the old master (replication lag)
3. Data loss = writes during partition + existing replication lag

Mitigation: Set `cluster-node-timeout` low (but not too low to avoid flapping). Use `min-replicas-to-write 1` and `min-replicas-max-lag 10` to make the master refuse writes if no replica ACK'd within 10 seconds. This reduces the write-loss window but sacrifices availability (master stops accepting writes earlier).

The fundamental trade-off: Redis Cluster chooses availability over consistency (AP in CAP theorem). It prioritizes staying writable over preventing split-brain data loss.

### Q6: You're designing a leaderboard for a game with 50M players. The leaderboard must support: get rank, get top 100, get surrounding 10 players, and update scores — all in < 5ms. Design with Redis.

**Answer:**
**Core structure:** Redis Sorted Set is purpose-built for this.

```
ZADD leaderboard <score> <player_id>     → O(log 50M) ≈ O(25)
ZREVRANK leaderboard <player_id>         → O(log 50M) ≈ O(25)
ZREVRANGE leaderboard 0 99 WITHSCORES    → O(log N + 100)
ZREVRANGE leaderboard (rank-5) (rank+5)  → O(log N + 10)
```

**Memory:** 50M entries × ~80 bytes per entry (skip list node + hash entry) ≈ 4 GB. Fits in one node.

**Problem at scale:** Single sorted set = single node = CPU bottleneck for 50M concurrent users.

**Solution — Sharded Leaderboard:**
1. Partition players into 100 shards by `player_id % 100`
2. Each shard has its own sorted set: `leaderboard:{shard}`
3. Global rank = sum of players with higher scores across all shards
4. Top 100 = merge top 100 from each shard (like merge-k-sorted-lists)
5. Pre-compute top 100 every second via Lua script, cache in a separate key

**Score ties:** Use composite score: `score * 1_000_000 + (MAX_TIMESTAMP - timestamp)` — earlier achievers rank higher.

**Real-time vs near-real-time:** For 50M players, exact real-time global rank is expensive (cross-shard query). Use near-real-time: update approximate rank every 1-5 seconds via background job. Most games accept this trade-off.

### Q7: You observe Redis latency spike to 100ms every few minutes (normally <1ms). Walk through your debugging process.

**Answer:**

**Step 1: Check SLOWLOG**
```
SLOWLOG GET 20
```
If slow commands appear (KEYS, SORT, HGETALL on large hash, Lua script), that's your culprit. Fix the command.

**Step 2: Check LATENCY LATEST**
```
LATENCY LATEST
LATENCY HISTORY <event>
```
Redis tracks latency events: fork, aof-fsync, rdb-unlink, expire-cycle, etc. If `fork` latency is high → BGSAVE or AOF rewrite is the cause (CoW amplification).

**Step 3: Check persistence**
If `INFO persistence` shows `rdb_last_bgsave_status:ok` with high `rdb_last_cow_size`, the fork+CoW is causing the spike. Mitigation: move BGSAVE to replica, schedule during low traffic, disable THP.

**Step 4: Check memory**
```
INFO memory
```
If `mem_fragmentation_ratio` > 1.5, fragmentation is causing malloc overhead. Enable `activedefrag yes` or schedule restart.
If `used_memory` > `maxmemory`, eviction is running aggressively — each command triggers eviction scan.

**Step 5: Check network**
```
INFO clients
```
If `connected_clients` is very high (>10K) or `blocked_clients` > 0, connection storms or blocking commands (BLPOP) may be the issue.

**Step 6: Check kernel**
Check if swap is active (`used_memory_rss` >> `used_memory` or `used_memory` + overhead > physical RAM). Swapping = performance cliff. Check `dmesg` for OOM killer activity.

**Step 7: Check client behavior**
Ensure clients use connection pooling, pipelines, and aren't sending O(N) commands on large datasets.

### Q8: How would you migrate from a single Redis instance to Redis Cluster with zero downtime?

**Answer:**

**Phase 1 — Prepare (days before):**
1. Audit application code for multi-key commands (MGET, MSET, pipeline with different keys, Lua scripts with multi-slot keys). Add hash tags where needed.
2. Remove or refactor any `KEYS *`, `SCAN` without key prefix, `FLUSHALL` usage.
3. Ensure client library supports Redis Cluster protocol.

**Phase 2 — Shadow Cluster:**
1. Deploy Redis Cluster (3 masters + 3 replicas) alongside existing standalone.
2. Set up dual-write: application writes to both standalone AND cluster.
3. Reads still from standalone.
4. Monitor cluster for errors, latency, correctness.

**Phase 3 — Data Sync:**
1. Use `redis-cli --cluster import` or custom SCAN+RESTORE to bulk-copy existing data from standalone to cluster.
2. After bulk copy, dual-write ensures new data goes to both.
3. Validate: sample keys and compare values between standalone and cluster.

**Phase 4 — Cutover:**
1. Switch reads to cluster (behind feature flag, canary 1% → 10% → 100%).
2. Monitor hit rates, latency, error rates at each step.
3. Once 100% reads are on cluster with no issues, stop writes to standalone.
4. Keep standalone running for 24-48 hours as rollback option.
5. Decommission standalone.

**Rollback plan:** At any point, flip feature flag back to standalone. Dual-write ensures standalone has all data.

### Q9: Explain Redis memory overhead. A colleague says "we have 10GB of data so we need a 10GB Redis instance." Why are they wrong?

**Answer:**
They need significantly more than 10GB. Here's the breakdown:

**Per-key overhead (~90 bytes):**
- redisObject header: 16 bytes
- dictEntry (hash table node): 24 bytes
- Key SDS (Simple Dynamic String): 9+ bytes header + key length
- Value SDS: 9+ bytes header + value length
- Next pointer in hash table: 8 bytes

For 50M keys × 90 bytes overhead = 4.5 GB of overhead alone.

**Hash table overhead:**
- Redis uses two hash tables (for incremental rehashing)
- Hash table is power-of-2 sized array of pointers
- 50M keys → ~67M slot table → 536 MB per table × 2 = 1 GB

**Memory fragmentation:**
- jemalloc rounds allocations to size classes
- `mem_fragmentation_ratio` typically 1.1-1.5
- 10GB data × 1.3 fragmentation = 13GB RSS

**Persistence overhead:**
- BGSAVE fork() with CoW: up to 2x during snapshot
- AOF buffer: variable, can be hundreds of MB
- Replication output buffer: 256MB default per replica

**Actual requirement:**
```
  10 GB raw data
+ 5-6 GB key/hash table overhead
+ 3-4 GB fragmentation
+ 10 GB CoW headroom (if BGSAVE)
+ 1 GB client buffers, replication
= ~30 GB total system memory recommended
```

Rule of thumb: provision 2.5-3x the raw dataset size.

### Q10: A critical Redis Cluster node just lost data during failover. The business is asking how to prevent this. What's your strategy for minimizing data loss in Redis?

**Answer:**
Redis uses async replication by default, so data loss during failover is architecturally expected. Here's a layered defense:

**Layer 1 — Reduce replication lag:**
- Monitor `master_repl_offset` vs `slave_repl_offset` on every node
- Alert when lag > 1MB or > 1 second
- Ensure replicas are on same AZ/region as master (network latency)
- Increase `repl-backlog-size` to handle network blips without full resync

**Layer 2 — Make master refuse unsafe writes:**
```
min-replicas-to-write 1
min-replicas-max-lag 10
```
Master stops accepting writes if no replica ACK'd within 10 seconds. Trades availability for reduced data loss.

**Layer 3 — Application-level durability:**
For critical data (payments, state transitions), write-through pattern:
1. Write to database (source of truth) FIRST
2. Then write to Redis (cache/index)
3. If Redis loses data on failover, reconstruct from database

**Layer 4 — Persistence:**
- Enable AOF with `appendfsync everysec` + hybrid RDB preamble
- On failover, new master has its own AOF → at most 1 second of data loss from its local perspective

**Layer 5 — Architecture:**
- Don't rely on Redis as sole data store for anything you can't afford to lose
- Use Redis as an acceleration layer over a durable database
- Design for cache miss: every code path must handle "Redis doesn't have this" gracefully

**To the business:** "We can reduce the window to ~1 second and make it extremely rare, but Redis's architecture fundamentally trades durability for speed. For zero-data-loss requirements, we need the durable database as the source of truth with Redis as a cache layer in front."

---

## 19. Quick Reference Card

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     REDIS QUICK REFERENCE CARD                           │
│                                                                          │
│  PORT: 6379 (default)   PROTOCOL: RESP (Redis Serialization Protocol)   │
│                                                                          │
│  STRINGS:  SET  GET  INCR  DECR  MGET  MSET  SETNX  SETEX  APPEND     │
│  LISTS:    LPUSH  RPUSH  LPOP  RPOP  LRANGE  LLEN  LINDEX  BLPOP      │
│  SETS:     SADD  SREM  SMEMBERS  SINTER  SUNION  SDIFF  SCARD         │
│  HASHES:   HSET  HGET  HDEL  HGETALL  HMSET  HMGET  HINCRBY          │
│  ZSETS:    ZADD  ZREM  ZRANGE  ZREVRANGE  ZRANK  ZSCORE  ZRANGEBYSCORE│
│  STREAMS:  XADD  XREAD  XREADGROUP  XACK  XRANGE  XLEN  XPENDING    │
│  HLL:      PFADD  PFCOUNT  PFMERGE                                      │
│  GEO:      GEOADD  GEOSEARCH  GEODIST  GEOPOS  GEOHASH                │
│  BITMAP:   SETBIT  GETBIT  BITCOUNT  BITOP                             │
│                                                                          │
│  ADMIN:    INFO  CONFIG  DBSIZE  DEBUG  SLOWLOG  MONITOR  CLIENT       │
│  CLUSTER:  CLUSTER INFO  CLUSTER NODES  CLUSTER SLOTS  CLUSTER MEET   │
│  PERSIST:  BGSAVE  BGREWRITEAOF  LASTSAVE                              │
│  SCRIPT:   EVAL  EVALSHA  SCRIPT LOAD  SCRIPT EXISTS                   │
│  PUBSUB:   PUBLISH  SUBSCRIBE  PSUBSCRIBE  UNSUBSCRIBE                 │
│  TXN:      MULTI  EXEC  DISCARD  WATCH  UNWATCH                        │
│                                                                          │
│  DANGEROUS IN PRODUCTION:                                                │
│  ✗ KEYS *       → Use SCAN                                              │
│  ✗ FLUSHALL     → Nuclear option, drops all data                        │
│  ✗ FLUSHDB      → Drops current DB                                      │
│  ✗ DEBUG SLEEP  → Blocks server intentionally                           │
│  ✗ MONITOR      → Performance impact at scale                           │
│  ✗ SAVE         → Synchronous (blocks), use BGSAVE                      │
│                                                                          │
│  LATENCY NUMBERS:                                                        │
│  GET/SET .................. < 0.1 ms (same datacenter)                   │
│  Pipeline 100 cmds ........ < 1 ms                                       │
│  BGSAVE 10GB dataset ...... 10-30 seconds (fork + write)                │
│  Full sync to replica ..... seconds to minutes (depends on data size)   │
│  Sentinel failover ........ ~30-35 seconds                               │
│  Cluster failover ......... ~15 seconds (NODE_TIMEOUT)                   │
│                                                                          │
│  CAPACITY PLANNING:                                                      │
│  Single instance QPS ....... 100K-200K ops/sec                           │
│  Max connections ........... 10K (configurable via maxclients)           │
│  Max key size .............. 512 MB                                       │
│  Max value size ............ 512 MB                                       │
│  Max entries in list/set ... 2^32 - 1 (~4.2 billion)                    │
│  Cluster max shards ........ 16,384 (one per hash slot)                  │
│  Cluster practical limit ... ~1000 nodes                                 │
│  Memory per key overhead ... ~90 bytes                                   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

> **How to use this document:**  
> 1. Read sections 1-5 first for fundamentals (Day 1)
> 2. Sections 6-8 for distributed Redis (Day 2)
> 3. Sections 9-11 for advanced features (Day 3)
> 4. Sections 12-15 for real-world context and anti-patterns (Day 4)
> 5. Sections 17-18 for interview practice (ongoing)
> 6. Use the Quick Reference Card (section 19) for last-minute revision
