# LRU Cache with TTL — Low Level Design

> **Prerequisite:** Read `README-LRUCache.md` and `LRUCache.java` first. This document only covers what's **NEW** with TTL.

---

## 1. What Changes with TTL?

| Aspect          | Basic LRU Cache                          | LRU Cache + TTL                              |
|-----------------|------------------------------------------|-----------------------------------------------|
| Entry dies when | Evicted (cache full + it's the LRU)      | Evicted **OR** expired (whichever comes first)|
| Node fields     | key, value, prev, next                   | key, value, **expiryTime**, prev, next        |
| `get()` extra   | —                                        | Check if expired → remove if stale, return -1 |
| `put()` extra   | —                                        | Set `expiryTime` on create, reset on update   |
| Complexity      | O(1) get/put                             | O(1) get/put (same!)                          |

**Real-world examples:**
- **DNS cache:** each record has a TTL (e.g., 300 seconds). After that, even if capacity isn't full, the record is stale.
- **Session cache:** user sessions expire after 30 minutes of inactivity.
- **API response cache:** cached response is valid for 60 seconds.

---

## 2. What Changes in the Node?

```
   Basic Node:                     TTL Node:
   ┌──────────────────┐            ┌──────────────────────────┐
   │ key              │            │ key                      │
   │ value            │            │ value                    │
   │ prev             │            │ prev                     │
   │ next             │            │ next                     │
   └──────────────────┘            │ expiryTime  ← NEW!      │
                                   └──────────────────────────┘
```

`expiryTime = System.currentTimeMillis() + ttlMillis`. If current time > expiryTime → node is expired.

---

## 3. Three Strategies for Handling Expired Entries

| Strategy                  | How it works                                               | Pros                          | Cons                                    |
|---------------------------|------------------------------------------------------------|-------------------------------|-----------------------------------------|
| **Lazy Eviction** (ours)  | Check if expired on every `get()`, remove if stale         | Simple, no extra threads, O(1)| Expired entries linger until accessed    |
| **Background Thread**     | Separate thread scans DLL periodically, removes expired    | Memory reclaimed promptly     | Needs synchronization, more complex     |
| **Hybrid** (production)   | Lazy + background cleanup combined                         | Best of both worlds           | Most complex                            |

We use **Lazy Eviction** — it keeps the code simple and interview-friendly.

---

## 4. Flow Diagrams (only what's DIFFERENT from basic LRU)

### `get(key)` with TTL

```
  get(key)
     │
     ▼
  key in HashMap? ─── NO ──→ return -1
     │
    YES
     │
     ▼
  Fetch Node
     │
     ▼
  ┌───────────────────────────┐
  │ Is node EXPIRED?          │   ← THIS IS THE ONLY NEW STEP
  │ (currentTime > expiryTime)│
  └─────────┬─────────────────┘
     ┌───── YES            NO ─────┐
     │                             │
     ▼                             ▼
  Remove from DLL + map      moveToHead (normal LRU)
     │                             │
     ▼                             ▼
  return -1                  return node.value
```

### `put(key, value, ttlMillis)` with TTL

```
  put(key, value, ttlMillis)      ← now takes a TTL parameter
     │
     ▼
  Is key in HashMap?
     │
     ├── YES
     │    │
     │    ▼
     │   Update value + RESET expiryTime   ← NEW: refresh the TTL
     │    │
     │    ▼
     │   moveToHead (same as before)
     │
     └── NO
          │
          ▼
     Cache full? ── YES ──→ evictLRU (same as before)
          │
          ▼
     Create Node(key, value, expiryTime)   ← NEW: set expiry
          │
          ▼
     addAfterHead + put in HashMap (same as before)
```

---

## 5. The Code — What's Different from Basic LRU

Only **3 small changes** to turn a basic LRU Cache into one with TTL:

### Change 1: Node gets `expiryTime`

```java
static class Node {
    int key;
    int value;
    long expiryTime;   // ← NEW
    Node prev;
    Node next;

    Node(int key, int value, long expiryTime) {
        this.key = key;
        this.value = value;
        this.expiryTime = expiryTime;
    }
}
```

### Change 2: `get()` checks expiry before returning

```java
public int get(int key) {
    if (!map.containsKey(key)) {
        return -1;
    }

    Node node = map.get(key);

    // ── NEW: check if expired ──
    if (System.currentTimeMillis() > node.expiryTime) {
        removeNode(node);
        map.remove(key);
        return -1;    // expired → treat as missing
    }

    moveToHead(node);
    return node.value;
}
```

### Change 3: `put()` sets/resets expiryTime

```java
public void put(int key, int value, long ttlMillis) {
    long expiryTime = System.currentTimeMillis() + ttlMillis;  // ← NEW

    if (map.containsKey(key)) {
        Node node = map.get(key);
        node.value = value;
        node.expiryTime = expiryTime;  // ← NEW: refresh TTL on update
        moveToHead(node);
    } else {
        if (map.size() == capacity) {
            evictLRU();
        }
        Node newNode = new Node(key, value, expiryTime);  // ← NEW: set expiry
        addAfterHead(newNode);
        map.put(key, newNode);
    }
}
```

**Everything else (DLL helpers, evictLRU, etc.) stays identical.**

---

## 6. Visual Dry Run (capacity = 3, TTL = 5 seconds)

```
  Time  Operation        │  DLL State                              │  Notes
  ────  ─────────────────┼─────────────────────────────────────────┼─────────────────
  t=0   put(1,"A",5000)  │  HEAD ⟷ [1:A exp@5s] ⟷ TAIL            │  expires at t=5
  t=1   put(2,"B",5000)  │  HEAD ⟷ [2:B@6s] ⟷ [1:A@5s] ⟷ TAIL    │
  t=3   get(1) → "A"    │  HEAD ⟷ [1:A@5s] ⟷ [2:B@6s] ⟷ TAIL    │  alive (t=3 < 5)
  t=6   get(1) → -1     │  HEAD ⟷ [2:B@6s] ⟷ TAIL                │  EXPIRED! (t=6 > 5)
                         │                                         │  removed from DLL + map
  t=7   get(2) → -1     │  HEAD ⟷ TAIL                            │  EXPIRED! (t=7 > 6)
```

---

## 7. Interview Tip (4 sentences to say)

> 1. "I'd add an `expiryTime` field to the Node."
> 2. "On `put()`, I set `expiryTime = now + ttl`."
> 3. "On `get()`, before returning, I check if the node is expired. If yes, I remove it and return -1. This is lazy eviction."
> 4. "For production, I'd add a background cleanup thread that periodically scans and removes expired entries to reclaim memory."

---

## 8. Complexity

| Metric        | Value         | Same as basic LRU? |
|---------------|---------------|---------------------|
| `get()` time  | **O(1)**      | Yes                 |
| `put()` time  | **O(1)**      | Yes                 |
| Space         | **O(capacity)** | Yes (+ one `long` per node) |

---

## 9. Further Improvements

| Improvement                     | Details                                                            |
|---------------------------------|--------------------------------------------------------------------|
| **Background cleanup thread**   | `ScheduledExecutorService` that runs every N seconds, walks DLL, removes expired nodes |
| **Thread safety**               | Needed if background thread is added — use `ReentrantReadWriteLock` |
| **Refresh TTL on get()**        | Optionally reset the timer on access (sliding expiration)          |
| **Evict expired before LRU**    | On capacity eviction, prefer removing an expired node over the LRU |
