# Design Unique ID Generator in Distributed Systems

> **Difficulty:** Easy | **Frequency:** ★★★★☆ | **Companies:** Twitter, Meta, Uber
> **Source:** Alex Xu Vol 1 Chapter 7

---

## 1. Requirements

- Generate unique IDs across multiple servers
- IDs should be sortable by time (roughly)
- 64-bit numeric IDs
- 10,000+ IDs per second
- No single point of failure

---

## 2. Approaches

### Approach 1: UUID
```
  128-bit random identifier
  Example: 550e8400-e29b-41d4-a716-446655440000
  
  Pros: Simple, no coordination needed, each server generates independently
  Cons: 128 bits (not 64), not sortable by time, not numeric
  
  Verdict: Doesn't meet our requirements (64-bit, time-sortable)
```

### Approach 2: Database Auto-Increment
```
  Single DB: auto_increment → 1, 2, 3, 4, ...
  
  Pros: Simple, unique, sortable
  Cons: Single point of failure, can't scale
  
  Multi-master with offset:
  Server 1: 1, 3, 5, 7, ...  (increment by 2, start at 1)
  Server 2: 2, 4, 6, 8, ...  (increment by 2, start at 2)
  
  Cons: Hard to add/remove servers, IDs across servers not time-ordered
```

### Approach 3: Ticket Server (Flickr's Approach)
```
  Centralized service hands out ID ranges.
  
  ┌──────────────┐
  │ Ticket Server│    Server A: "Give me IDs" → gets range [1, 1000]
  │              │    Server B: "Give me IDs" → gets range [1001, 2000]
  │ Next Range:  │    Server C: "Give me IDs" → gets range [2001, 3000]
  │ [3001, 4000] │    
  └──────────────┘    Each server uses IDs from its range locally.
  
  Pros: Simple, 64-bit, roughly sortable
  Cons: Ticket server is SPOF (mitigate with multiple ticket servers)
```

### Approach 4: Snowflake (Twitter) — RECOMMENDED
```
  64-bit ID composed of multiple fields:

  ┌─────────┬──────────────────────────┬────────────┬──────────────┐
  │ Sign(1) │  Timestamp (41 bits)     │Machine(10) │Sequence(12)  │
  │   0     │  milliseconds since      │ ID         │ per ms       │
  │         │  custom epoch            │ (1024      │ (4096        │
  │         │  (~69 years)             │  machines) │  IDs/ms)     │
  └─────────┴──────────────────────────┴────────────┴──────────────┘
   Bit:  63    62────────────────22      21──────12   11──────────0

  How it works:
  ─────────────
  1. Timestamp: Current time in ms since custom epoch (e.g., 2020-01-01)
  2. Machine ID: Assigned when server starts (from ZooKeeper/config)
  3. Sequence: Increments per millisecond, resets when ms changes
  
  Example:
  Time: 1623456789000 ms (41 bits)
  Machine: 5 (10 bits)
  Sequence: 0 (12 bits)
  
  ID = (timestamp << 22) | (machine_id << 12) | sequence
  
  Properties:
  ✓ 64-bit
  ✓ Time-sortable (timestamp is the most significant bits!)
  ✓ Unique (machine_id + sequence prevents collision)
  ✓ No coordination needed (each machine generates independently)
  ✓ ~4 million IDs per second per machine (4096 IDs/ms × 1000)
  
  Used by: Twitter, Discord, Instagram
```

### Comparison
```
┌──────────────────┬──────────┬───────────┬──────────┬──────────────┐
│ Approach         │ Unique?  │ Sortable? │ 64-bit?  │ Distributed? │
├──────────────────┼──────────┼───────────┼──────────┼──────────────┤
│ UUID             │ Yes      │ No        │ No (128) │ Yes          │
│ DB Auto-incr     │ Yes      │ Yes       │ Yes      │ No (SPOF)    │
│ Ticket Server    │ Yes      │ Roughly   │ Yes      │ Partial      │
│ Snowflake        │ Yes      │ Yes       │ Yes      │ Yes          │
└──────────────────┴──────────┴───────────┴──────────┴──────────────┘
```

---

## 3. Snowflake Clock Issues

```
CLOCK SKEW PROBLEM:
  If system clock goes backwards (NTP sync), we could generate
  duplicate IDs (same timestamp as before).

  Solutions:
  1. Wait until clock catches up
  2. Use sequence number to bridge the gap
  3. Detect clock regression and throw error (Twitter's approach)
  4. Use NTP servers with monotonic clock guarantees
```

---

## 4. Key Points for Interview

1. **Snowflake is the answer** for distributed ID generation — explain the bit layout
2. **41 bits for timestamp** → ~69 years from custom epoch
3. **10 bits for machine ID** → 1024 machines
4. **12 bits for sequence** → 4096 IDs per millisecond per machine
5. **Time-sorted IDs** allow efficient database range queries
6. Mention **clock skew** as a potential issue and solutions
