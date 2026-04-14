# CAP Theorem & Consistency Models

> **Difficulty:** Easy-Medium | **Time:** 2 hours | **Priority:** Must Know

---

## 1. CAP Theorem

In a distributed system, you can only guarantee TWO of three properties at any given time.

```
                         Consistency (C)
                            /\
                           /  \
                          /    \
                         / CP   \
                        / systems \
                       /          \
                      /────────────\
                     /              \
                    /    You can     \
                   /   only pick 2   \
                  /                    \
                 /  CA       AP         \
                / systems   systems      \
               /──────────────────────────\
         Availability (A) ──────────── Partition
                                       Tolerance (P)
```

### The Three Properties

| Property | Meaning | Example |
|----------|---------|---------|
| **C**onsistency | Every read gets the most recent write | After updating balance to $100, every node returns $100 |
| **A**vailability | Every request gets a response (not error) | System always responds, even if data is stale |
| **P**artition Tolerance | System works despite network failures | If network between nodes breaks, system keeps running |

### Why P Is Non-Negotiable
In real distributed systems, **network partitions WILL happen**. You can't avoid P. So the real choice is:

```
Network Partition Happens!
     │
     ├── Choose CP (Consistency + Partition Tolerance)
     │   ┌───────────────────────────────────────────┐
     │   │ Some nodes become UNAVAILABLE              │
     │   │ But all responses are CONSISTENT           │
     │   │                                            │
     │   │ Example: Bank transfer                     │
     │   │ Node A: "Balance = $100" ✓ correct         │
     │   │ Node B: UNAVAILABLE (refuses stale data)   │
     │   │                                            │
     │   │ Systems: MongoDB, HBase, Redis (default)   │
     │   │ Use: Banking, inventory, booking            │
     │   └───────────────────────────────────────────┘
     │
     └── Choose AP (Availability + Partition Tolerance)
         ┌───────────────────────────────────────────┐
         │ All nodes RESPOND                          │
         │ But some responses may be STALE            │
         │                                            │
         │ Example: Social media like count           │
         │ Node A: "Likes = 1000" ✓                   │
         │ Node B: "Likes = 998"  ✓ (slightly stale)  │
         │                                            │
         │ Systems: Cassandra, DynamoDB, CouchDB      │
         │ Use: Social feeds, DNS, shopping cart       │
         └───────────────────────────────────────────┘
```

---

## 2. Consistency Models

```
┌────────────────────────────────────────────────────────────────┐
│          CONSISTENCY SPECTRUM (Strongest → Weakest)             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  STRONG ◄─────────────────────────────────────────► EVENTUAL   │
│  (Linearizability)                                             │
│                                                                │
│  ┌──────────────┐ ┌───────────┐ ┌──────────┐ ┌────────────┐  │
│  │   Strong     │ │ Causal    │ │  Read    │ │  Eventual  │  │
│  │ Consistency  │ │Consistency│ │  Your    │ │ Consistency│  │
│  │              │ │           │ │  Writes  │ │            │  │
│  │All reads see │ │Related    │ │You always│ │Eventually  │  │
│  │latest write  │ │events in  │ │see your  │ │all nodes   │  │
│  │              │ │order      │ │own writes│ │converge    │  │
│  │              │ │           │ │          │ │            │  │
│  │Slowest       │ │           │ │          │ │Fastest     │  │
│  │Most correct  │ │           │ │          │ │Most avail  │  │
│  └──────────────┘ └───────────┘ └──────────┘ └────────────┘  │
│                                                                │
│  Use: Payments    Use: Chat     Use: Profile  Use: Likes,     │
│       Inventory        messages      updates        DNS        │
│       Booking                                                  │
└────────────────────────────────────────────────────────────────┘
```

### Eventual Consistency in Action
```
Write "balance=100" to Node A at time T1

  Time T1:   Node A: balance=100  ✓ (just written)
             Node B: balance=80   ✗ (stale)
             Node C: balance=80   ✗ (stale)

  Time T1+Δ: Node A: balance=100  ✓
             Node B: balance=100  ✓ (replicated)
             Node C: balance=80   ✗ (still replicating)

  Time T2:   Node A: balance=100  ✓
             Node B: balance=100  ✓
             Node C: balance=100  ✓ (all converged!)

  Δ = replication lag (typically milliseconds to seconds)
```

---

## 3. PACELC Theorem (Extension of CAP)

CAP only talks about partition scenarios. PACELC adds normal operation behavior.

```
If Partition (P):
  Choose Availability (A) or Consistency (C)
Else (E):
  Choose Latency (L) or Consistency (C)

┌──────────────┬──────────────┬────────────────┐
│   System     │ If Partition │ Else (Normal)  │
├──────────────┼──────────────┼────────────────┤
│ DynamoDB     │ PA           │ EL (low lat)   │
│ Cassandra    │ PA           │ EL (low lat)   │
│ MongoDB      │ PC           │ EC (consistent)│
│ MySQL/PG     │ PC           │ EC (consistent)│
│ Cosmos DB    │ PA/PC (tunable)│ EL/EC (tunable)│
└──────────────┴──────────────┴────────────────┘
```

---

## 4. Key Takeaways for Interviews

1. **Network partitions are inevitable** — your real choice is CP vs AP
2. **CP for money/booking** — you'd rather be unavailable than give wrong balance
3. **AP for social/content** — a stale like count is fine; being down is not
4. **Eventual consistency is the most common** in large-scale systems
5. **Read-your-writes** is the minimum you should offer (user sees their own changes)
6. **Mention CAP when justifying** your database choice in designs
7. **Tunable consistency** (Cassandra, DynamoDB) lets you choose per-query
