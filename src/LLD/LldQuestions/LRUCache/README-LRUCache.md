# LRU Cache — Low Level Design (Interview Guide)

## Overview

LRU (Least Recently Used) Cache is one of the **most commonly asked LLD questions** in interviews at Google, Amazon, Meta, Microsoft, and others. It tests your ability to combine two data structures to achieve O(1) for both read and write.

**Real-world examples:** Browser page cache, Database query cache, DNS cache, CPU cache page replacement.

---

## 1. Functionality — What We Need

We need a **fixed-size cache** that does only TWO things, both in **O(1)** time:

| Operation          | What it does                                                                |
|--------------------|-----------------------------------------------------------------------------|
| `get(key)`         | If key exists → return value and mark it as "most recently used"            |
|                    | If key doesn't exist → return -1                                           |
| `put(key, value)`  | If key exists → update its value and mark it as "most recently used"        |
|                    | If key doesn't exist and cache is full → **evict** the least recently used entry |
|                    | Then insert the new key-value and mark it as "most recently used"          |

**The core rule:** Every time you touch an entry (read or write), it becomes the "most recently used". The one that hasn't been touched the longest gets kicked out when space is needed.

---

## 2. The Key Insight — Why HashMap + Doubly Linked List?

### The Problem with a single data structure

- **HashMap alone?** O(1) lookup is great, but how do you know which key is the *least recently used*? You'd need to scan all entries → O(n). Too slow.
- **Array/List alone?** You can track order, but finding a key by value requires scanning → O(n). Too slow.
- **Singly Linked List?** You can't remove a node from the middle in O(1) because you don't have a pointer to the previous node.

### The Solution — Combine their strengths

```
   ┌─────────────────────────────────────────────────────────────┐
   │              WHY HashMap + Doubly Linked List?              │
   ├─────────────────────────────────────────────────────────────┤
   │                                                             │
   │   Requirement          HashMap    DLL        Combined       │
   │   ──────────────────   ────────   ────────   ────────       │
   │   O(1) lookup by key   ✅ Yes     ❌ No      ✅ Yes         │
   │   O(1) delete by ref   ❌ N/A     ✅ Yes     ✅ Yes         │
   │   O(1) insert at head  ❌ N/A     ✅ Yes     ✅ Yes         │
   │   Track access order   ❌ No      ✅ Yes     ✅ Yes         │
   │   Know LRU in O(1)     ❌ No      ✅ Tail    ✅ Yes         │
   │                                                             │
   └─────────────────────────────────────────────────────────────┘
```

### How they work together

```
   HashMap<Key, Node>                Doubly Linked List
   ┌─────────────────┐
   │  key1 → Node ───┼──┐      HEAD ⟷ [MRU] ⟷ [..] ⟷ [..] ⟷ [LRU] ⟷ TAIL
   │  key2 → Node ───┼──┼──→       ↑                             ↑
   │  key3 → Node ───┼──┘         Most                         Least
   │  ...            │           Recently                     Recently
   └─────────────────┘             Used                         Used
                                                                  │
                                                           Gets evicted
                                                           when cache full
```

- **HashMap** stores `key → Node pointer`. Gives us O(1) lookup to jump straight to any node.
- **Doubly Linked List** maintains the access order. HEAD side = most recent, TAIL side = least recent.
- **Why "doubly"?** Because we need to remove a node from the MIDDLE in O(1). With a doubly linked list, every node knows its `prev` and `next`, so we just re-link the neighbors. A singly linked list can't do this without traversal.

---

## 3. Design Patterns Used

### (a) Sentinel / Dummy Node Pattern ⭐ (most important for this problem)

We create **dummy HEAD and TAIL** nodes that never hold real data. They are always the first and last nodes.

**Why?** Without sentinels, every insert/remove needs to check: *"Is this the first node? Is it the last? Is the list empty?"* — lots of `if/else` branches and null checks. With sentinels, **every real node always has a valid `prev` and `next`**, so the code has a single path with zero branching.

```
   Without sentinels (messy):             With sentinels (clean):
   
   if (head == null) { ... }              HEAD ⟷ ... real nodes ... ⟷ TAIL
   if (node == head) { ... }              
   if (node == tail) { ... }              Every node always has prev & next.
   if (node.next == null) { ... }         One code path. No null checks.
```

### (b) Composition Pattern

LRUCache is **composed of** a HashMap and a DLL. Each data structure handles what it's best at:
- HashMap → fast key lookup
- DLL → fast ordering and eviction

### (c) Facade Pattern

The LRUCache class exposes only `get()` and `put()`. The caller never sees or cares about the internal DLL. The complexity is hidden behind a simple API.

### (d) Strategy Pattern *(extension — mention at end of interview)*

The eviction logic can be extracted behind an interface:

```java
interface EvictionPolicy<K> {
    void keyAccessed(K key);
    K evict();
}
```

Then implement `LRUEvictionPolicy`, `LFUEvictionPolicy`, `FIFOEvictionPolicy`, etc. The Cache class delegates eviction decisions to whatever policy is plugged in. (You don't need to code this during the interview — just mentioning it shows design maturity.)

---

## 4. Flow Diagrams

### `get(key)` Flow

```
  get(key)
     │
     ▼
  Is key in HashMap? ─── NO ──→ return -1
     │
    YES
     │
     ▼
  Fetch Node from HashMap          ← O(1) lookup
     │
     ▼
  Remove Node from its current     ← O(1) because we have prev/next pointers
  position in DLL
     │
     ▼
  Insert Node right after HEAD     ← O(1) pointer re-linking
  (now it's the Most Recently Used)
     │
     ▼
  return node.value
```

### `put(key, value)` Flow

```
  put(key, value)
     │
     ▼
  Is key already in HashMap?
     │
     ├── YES (update existing entry)
     │    │
     │    ▼
     │   Fetch Node from HashMap
     │    │
     │    ▼
     │   Update node.value = new value
     │    │
     │    ▼
     │   Remove Node from current DLL position
     │    │
     │    ▼
     │   Insert Node right after HEAD (mark as MRU)
     │    │
     │    ▼
     │   DONE ✅
     │
     └── NO (brand new key)
          │
          ▼
     Is cache at full capacity?
          │
          ├── YES → Evict!
          │    │
          │    ▼
          │   Grab the node just before TAIL    ← that's the LRU
          │    │
          │    ▼
          │   Remove it from DLL
          │    │
          │    ▼
          │   Remove its key from HashMap
          │
          └── (continue whether evicted or not)
               │
               ▼
          Create new Node(key, value)
               │
               ▼
          Insert right after HEAD (mark as MRU)
               │
               ▼
          Put (key → Node) into HashMap
               │
               ▼
          DONE ✅
```

---

## 5. The Code — Intentionally Simple

The entire core is ~50 lines. Here's the complete implementation broken down piece by piece:

### Step 1: The Node class

Each node stores `key + value + prev + next`. We store the **key** in the node so that during eviction we can remove the entry from the HashMap.

```java
static class Node {
    int key;
    int value;
    Node prev;
    Node next;

    Node(int key, int value) {
        this.key = key;
        this.value = value;
    }
}
```

```
       ┌──────────────────────┐
       │    Node              │
       │  ┌──────┬─────────┐  │
       │  │ key  │ value   │  │
       │  ├──────┴─────────┤  │
       │  │ prev ← → next  │  │
       │  └────────────────┘  │
       └──────────────────────┘
```

### Step 2: Fields and Constructor

```java
private final int capacity;
private final Map<Integer, Node> map;
private final Node head;  // sentinel — never holds real data
private final Node tail;  // sentinel — never holds real data

public LRUCache(int capacity) {
    this.capacity = capacity;
    this.map = new HashMap<>();

    // Wire up the sentinels: HEAD ⟷ TAIL (empty list)
    this.head = new Node(0, 0);
    this.tail = new Node(0, 0);
    head.next = tail;
    tail.prev = head;
}
```

### Step 3: The 4 DLL helper methods (this is where the magic is)

```java
// Remove a node from wherever it sits in the DLL
//   Before:  ... ⟷ [A] ⟷ [node] ⟷ [B] ⟷ ...
//   After:   ... ⟷ [A] ⟷ [B] ⟷ ...
private void removeNode(Node node) {
    node.prev.next = node.next;
    node.next.prev = node.prev;
}

// Insert a node right after HEAD (making it MRU)
//   Before:  HEAD ⟷ [X] ⟷ ...
//   After:   HEAD ⟷ [node] ⟷ [X] ⟷ ...
private void addAfterHead(Node node) {
    node.next = head.next;
    node.prev = head;
    head.next.prev = node;
    head.next = node;
}

// Move an existing node to just after HEAD
private void moveToHead(Node node) {
    removeNode(node);
    addAfterHead(node);
}

// Evict the LRU entry (the node just before TAIL)
//   Before:  ... ⟷ [Y] ⟷ [LRU] ⟷ TAIL
//   After:   ... ⟷ [Y] ⟷ TAIL
private void evictLRU() {
    Node lruNode = tail.prev;
    removeNode(lruNode);
    map.remove(lruNode.key);   // ← this is why Node stores the key!
}
```

### Step 4: The two public methods — `get()` and `put()`

```java
public int get(int key) {
    if (!map.containsKey(key)) {
        return -1;
    }
    Node node = map.get(key);
    moveToHead(node);       // mark as most recently used
    return node.value;
}

public void put(int key, int value) {
    if (map.containsKey(key)) {
        // Key exists → update value and move to front
        Node node = map.get(key);
        node.value = value;
        moveToHead(node);
    } else {
        // New key → evict if full, then insert
        if (map.size() == capacity) {
            evictLRU();
        }
        Node newNode = new Node(key, value);
        addAfterHead(newNode);
        map.put(key, newNode);
    }
}
```

**That's it.** The entire LRU Cache in ~50 lines of code.

---

## 6. Visual Dry Run (capacity = 3)

Walk through this step by step — this is what you'd draw on a whiteboard:

```
  Operation       │  DLL State (HEAD→ ... →TAIL)         │  HashMap                 │  Evicted
  ────────────────┼──────────────────────────────────────┼──────────────────────────┼──────────
  (start)         │  HEAD ⟷ TAIL                         │  {}                      │  —
                  │                                      │                          │
  put(1, "A")     │  HEAD ⟷ [1:A] ⟷ TAIL                │  {1→Node}                │  —
                  │                                      │                          │
  put(2, "B")     │  HEAD ⟷ [2:B] ⟷ [1:A] ⟷ TAIL       │  {1→Node, 2→Node}        │  —
                  │                                      │                          │
  put(3, "C")     │  HEAD ⟷ [3:C] ⟷ [2:B] ⟷ [1:A] ⟷ T │  {1→N, 2→N, 3→N}        │  — (FULL!)
                  │                                      │                          │
  get(1) → "A"   │  HEAD ⟷ [1:A] ⟷ [3:C] ⟷ [2:B] ⟷ T │  {1→N, 2→N, 3→N}        │  —
                  │  ↑ key 1 moved to front              │                          │
                  │                                      │                          │
  put(4, "D")     │  HEAD ⟷ [4:D] ⟷ [1:A] ⟷ [3:C] ⟷ T │  {1→N, 3→N, 4→N}        │  key 2 ❌
                  │  ↑ full → evict tail.prev (key 2)    │  ↑ key 2 removed         │
                  │                                      │                          │
  get(2) → -1    │  (unchanged — key 2 not found)       │  (key 2 not in map)      │  —
                  │                                      │                          │
  put(3, "C2")   │  HEAD ⟷ [3:C2] ⟷ [4:D] ⟷ [1:A] ⟷ T│  {1→N, 3→N, 4→N}        │  —
                  │  ↑ key 3 updated + moved to front    │  ↑ same keys, val updated│
                  │                                      │                          │
  put(5, "E")     │  HEAD ⟷ [5:E] ⟷ [3:C2] ⟷ [4:D] ⟷ T│  {3→N, 4→N, 5→N}        │  key 1 ❌
                  │  ↑ full → evict tail.prev (key 1)    │  ↑ key 1 removed         │
```

---

## 7. Complexity Analysis

| Metric        | Value         | Why                                              |
|---------------|---------------|--------------------------------------------------|
| `get()` time  | **O(1)**      | HashMap lookup + DLL remove + DLL insert = O(1)  |
| `put()` time  | **O(1)**      | HashMap lookup/put + DLL remove + DLL insert = O(1) |
| Space         | **O(capacity)** | One HashMap entry + one DLL node per cache entry |

---

## 8. What Can Be Improved (say this at end of interview)

> **In a real interview you'll run out of time, so just MENTION these and explain briefly — don't code them.**

| Improvement               | Details                                                                    |
|---------------------------|----------------------------------------------------------------------------|
| **Thread Safety**         | Use `synchronized`, `ReentrantReadWriteLock`, or `ConcurrentHashMap` + CAS |
| **Generics**              | `LRUCache<K, V>` instead of `int, int`                                    |
| **TTL Support**           | Add timestamps, expire entries — **see `LRUCacheWithTTL.java`**           |
| **Strategy Pattern**      | Extract `EvictionPolicy<K>` interface for LRU/LFU/FIFO                    |
| **Size-based Eviction**   | Evict by total byte size instead of count                                  |
| **Cache Statistics**      | Track hit/miss ratio, eviction count for monitoring                        |
| **LinkedHashMap Shortcut**| Java's built-in, but interviewers want the from-scratch implementation     |

---

## 9. Quick Comparison: LRU vs LFU vs FIFO

| Policy | Evicts                 | Data Structure          | Use When                |
|--------|------------------------|--------------------------|-------------------------|
| LRU    | Least Recently Used    | HashMap + Doubly LL      | Recency matters most    |
| LFU    | Least Frequently Used  | HashMap + Frequency Map  | Frequency matters most  |
| FIFO   | First In, First Out    | HashMap + Queue          | Simplest; arrival order |

---

## 10. Interview Cheat Sheet (30-second pitch)

> "An LRU Cache uses a **HashMap** for O(1) key lookup, combined with a **Doubly Linked List** to maintain access order. On every `get` or `put`, the accessed node is moved to the head. When full, we evict the node at the tail — the least recently used entry. Both operations are O(1). I use **dummy head/tail sentinel nodes** to eliminate edge cases in pointer manipulation."

---

## 11. Interview Timeline (1.5 hours)

| Time       | What to do                                                   |
|------------|--------------------------------------------------------------|
| 0–10 min   | Clarify requirements: what does `get` return? what's the eviction rule? |
| 10–20 min  | Explain the data structure choice (HashMap + DLL) and why    |
| 20–30 min  | Draw the architecture and flow diagrams on whiteboard        |
| 30–70 min  | Code: Node → Constructor → 4 DLL helpers → get() → put()    |
| 70–80 min  | Dry-run with a small example (capacity=3, 5-6 operations)   |
| 80–90 min  | Discuss improvements: thread safety, generics, TTL, strategy pattern |
