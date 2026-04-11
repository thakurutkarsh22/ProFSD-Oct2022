# LRU Cache Hybrid — Lazy + Background Cleanup + Thread Safety

> **Prerequisite:** Read `README-LRUCache.md` (basic) and `README-LRUCacheWithTTL.md` (TTL) first.
> This document only covers what's **NEW** in the Hybrid version.

---

## 1. What's the Problem with Lazy-Only TTL?

In `LRUCacheWithTTL`, expired entries are only removed when someone calls `get()` on them. If a key expires and nobody ever asks for it again, it sits in memory forever.

```
  put(1, "A", TTL=5s)   →   [1:A] sits in cache
  ... 5 seconds pass ...
  Nobody calls get(1)    →   [1:A] is expired but STILL in memory!
  ... 1 hour later ...
  Still there.            →   Wasting memory the entire time.
```

**The Hybrid fix:** A background thread sweeps the cache every N seconds and removes expired entries — even if nobody is accessing them.

---

## 2. What's New in the Hybrid Version

| Aspect                | LRUCacheWithTTL         | LRUCacheHybrid (this)                        |
|-----------------------|-------------------------|----------------------------------------------|
| Lazy eviction on get  | Yes                     | Yes (same)                                   |
| Background cleanup    | No                      | **Yes** — `ScheduledExecutorService`         |
| Thread safety         | No                      | **Yes** — `ReentrantReadWriteLock`            |
| Needs `shutdown()`    | No                      | **Yes** — must stop the background thread    |
| Interview difficulty  | Easy-Medium             | Medium                                       |

---

## 3. Architecture

```
   ┌──────────────────────────────────────────────────────────────────┐
   │                      LRUCacheHybrid                             │
   │                                                                  │
   │  ┌──────────────────────────────────────────────────────┐       │
   │  │           ReentrantReadWriteLock                      │       │
   │  │        (guards ALL access to DLL + map)               │       │
   │  └──────────────────────────────────────────────────────┘       │
   │          ▲                                   ▲                   │
   │          │                                   │                   │
   │   Caller Thread                     Background Thread            │
   │   ┌──────────────┐                 ┌────────────────────┐       │
   │   │  get() / put()│                │  cleanupExpired()  │       │
   │   │  writeLock()  │                │  every N seconds    │       │
   │   │  lazy check   │                │  writeLock()        │       │
   │   └──────────────┘                │  walk DLL tail→head │       │
   │          │                         │  remove expired     │       │
   │          ▼                         └────────────────────┘       │
   │  ┌───────────────────────────────────────────┐                  │
   │  │  HashMap<Key, Node>  +  Doubly LL         │                  │
   │  │  HEAD ⟷ [MRU] ⟷ [..] ⟷ [LRU] ⟷ TAIL    │                  │
   │  └───────────────────────────────────────────┘                  │
   └──────────────────────────────────────────────────────────────────┘
```

---

## 4. Why ReentrantReadWriteLock?

Since a background thread now touches the DLL and HashMap alongside the caller thread, we need synchronization.

| Option                  | How it works                          | Trade-off                               |
|-------------------------|---------------------------------------|-----------------------------------------|
| `synchronized`          | One big lock on every method          | Simple, but only 1 thread at a time     |
| **`ReadWriteLock`** (ours) | Read lock = shared, Write lock = exclusive | Multiple readers in parallel, writers exclusive |
| Lock-free (CAS)         | Compare-and-swap on pointers          | Highest throughput, extremely complex    |

**Which lock for which method?**

| Method          | Lock used    | Why                                                |
|-----------------|--------------|----------------------------------------------------|
| `get()`         | **Write**    | Reads the map BUT also moves the node (writes DLL) |
| `put()`         | **Write**    | Writes to both map and DLL                         |
| `cleanupExpired()` | **Write** | Removes nodes from DLL and map                     |
| `size()`        | Read         | Only reads `map.size()`, no writes                 |
| `printState()`  | Read         | Only walks DLL, no modifications                   |

---

## 5. Background Cleanup Flow

```
  cleanupExpired() — runs every N seconds on background thread
     │
     ▼
  Acquire WRITE lock
     │
     ▼
  Start at tail.prev (oldest entry)
     │
     ▼
  ┌──────────────────────┐
  │ current == HEAD?     │─── YES ──→ Done. Release lock.
  └─────────┬────────────┘
           NO
            │
            ▼
  ┌──────────────────────┐
  │ Is node expired?     │─── NO ──→ Move to current.prev
  └─────────┬────────────┘           (keep walking)
           YES
            │
            ▼
  Save prev = current.prev
            │
            ▼
  removeNode(current) from DLL
  map.remove(current.key)
            │
            ▼
  current = prev  →  loop back ↑
```

**Why walk from TAIL toward HEAD?** Oldest entries (most likely expired) are near the tail. We get the best cleanup efficiency by starting there.

---

## 6. The Code — Only What's New

### New field: `ReentrantReadWriteLock`

```java
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
```

### New field: `ScheduledExecutorService`

```java
private final ScheduledExecutorService cleanupExecutor;

// In constructor:
this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "LRU-Cleanup");
    t.setDaemon(true);      // won't prevent JVM shutdown
    return t;
});

cleanupExecutor.scheduleAtFixedRate(
    this::cleanupExpired,    // the method to run
    cleanupIntervalMs,       // initial delay
    cleanupIntervalMs,       // repeat interval
    TimeUnit.MILLISECONDS
);
```

### Every public method now wrapped in lock

```java
public int get(int key) {
    lock.writeLock().lock();
    try {
        // ... same logic as LRUCacheWithTTL ...
    } finally {
        lock.writeLock().unlock();    // ALWAYS in finally
    }
}
```

### The cleanup method (the KEY new method)

```java
private void cleanupExpired() {
    lock.writeLock().lock();
    try {
        Node current = tail.prev;
        while (current != head) {
            Node prev = current.prev;
            if (isExpired(current)) {
                map.remove(current.key);
                removeNode(current);
            }
            current = prev;
        }
    } finally {
        lock.writeLock().unlock();
    }
}
```

### Shutdown method (new — required for resource cleanup)

```java
public void shutdown() {
    cleanupExecutor.shutdown();
    // Wait up to 2s for the current cleanup cycle to finish
    if (!cleanupExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow();
    }
}
```

---

## 7. Design Patterns (new in this version)

| Pattern              | Where                                | Why                                               |
|----------------------|--------------------------------------|---------------------------------------------------|
| **Producer-Consumer** | Background thread + shared DLL/map  | Background work on shared data with coordination  |
| **Resource Management** | `shutdown()` method               | Graceful thread lifecycle — prevent resource leaks |
| **Read-Write Lock**  | `ReentrantReadWriteLock` on all ops | Concurrent reads, exclusive writes                |

(Plus all patterns from the basic and TTL versions: Sentinel, Composition, Facade)

---

## 8. Evolution of All Three Versions

```
  Feature              LRUCache      LRUCacheWithTTL     LRUCacheHybrid
  ───────────────────  ────────────  ──────────────────  ──────────────────
  Data structures      HashMap+DLL   HashMap+DLL         HashMap+DLL
  Eviction trigger     Cache full    Cache full + TTL    Cache full + TTL
  Lazy TTL check       ✗             ✓ on get()          ✓ on get()
  Background cleanup   ✗             ✗                   ✓ every N seconds
  Thread safe          ✗             ✗                   ✓ ReadWriteLock
  Needs shutdown()     ✗             ✗                   ✓ (stops thread)
  Complexity           O(1)          O(1)                O(1) amortized
  Interview level      Easy          Easy-Medium         Medium
```

---

## 9. Interview Tip (what to say)

> "The hybrid approach combines lazy eviction — checking TTL on every `get()` — with a background `ScheduledExecutorService` that sweeps expired entries every N seconds. This ensures stale data is never returned AND memory is reclaimed even for forgotten keys. I use a `ReentrantReadWriteLock` so the cleanup thread and caller threads don't corrupt the shared DLL. Read lock for read-only operations, write lock for everything that modifies the data."

---

## 10. What Can Still Be Improved

| Improvement                  | Details                                                                  |
|------------------------------|--------------------------------------------------------------------------|
| **Lock-free cleanup**        | Use `ConcurrentLinkedDeque` + `ConcurrentHashMap` + CAS. Avoids blocking get/put during cleanup. |
| **Segmented locking**        | Partition cache into N segments, each with its own lock. Reduces contention. |
| **Early-stop cleanup**       | If all entries have the same TTL, stop walking once you hit a non-expired node from the tail. |
| **Metrics**                  | Track cleanup count, average cleanup duration, lock contention stats.     |

---

## 11. File Structure

```
LRUCache/
├── LRUCache.java              ← Basic LRU (HashMap + DLL)
├── LRUCacheWithTTL.java       ← + TTL with lazy eviction
├── LRUCacheHybrid.java        ← + Background cleanup + thread safety
├── README-LRUCache.md         ← Docs for basic
├── README-LRUCacheWithTTL.md  ← Docs for TTL
└── README-LRUCacheHybrid.md   ← Docs for hybrid (you are here)
```
