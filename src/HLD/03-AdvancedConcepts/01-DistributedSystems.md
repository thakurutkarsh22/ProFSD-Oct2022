# Distributed Systems Fundamentals

> **Difficulty:** Medium-Hard | **Time:** 3 hours | **Priority:** Must Know

---

## What Makes Distributed Systems Hard

```
┌──────────────────────────────────────────────────────────────┐
│           THE 8 FALLACIES OF DISTRIBUTED COMPUTING            │
│                   (Peter Deutsch, 1994)                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  1. The network is reliable          ← Packets get lost     │
│  2. Latency is zero                  ← Network hops add ms  │
│  3. Bandwidth is infinite            ← There are limits     │
│  4. The network is secure            ← Always verify        │
│  5. Topology doesn't change          ← Nodes join/leave     │
│  6. There is one administrator       ← Multiple teams       │
│  7. Transport cost is zero           ← Serialization costs  │
│  8. The network is homogeneous       ← Different hardware   │
│                                                              │
│  Every design must account for these realities!              │
└──────────────────────────────────────────────────────────────┘
```

---

## 1. Clocks & Ordering in Distributed Systems

```
THE PROBLEM: No global clock in distributed systems

  Server A                   Server B
  Time: 10:00:01.000         Time: 10:00:01.005
      │                           │
      │── Event X ──────────────►│
      │                           │── Event Y
      │                           │
  
  Did X happen before Y? Server A says yes, Server B isn't sure.
  Clock skew between servers can be milliseconds to seconds!


SOLUTIONS:

1. LAMPORT CLOCKS (Logical Clocks)
   ─────────────────────────────
   Each event gets a logical counter. On send, include counter.
   On receive, counter = max(local, received) + 1
   
   Server A         Server B
   [1] event        
   [2] send ──────► [3] receive (max(0,2)+1=3)
                    [4] event
   [5] receive ◄── [6] send
   (max(2,6)+1=7)
   
   Gives partial ordering (if A→B then clock(A) < clock(B))
   But clock(A) < clock(B) does NOT mean A→B (concurrent possible)


2. VECTOR CLOCKS
   ──────────────
   Each server maintains a vector of counters for ALL servers.
   
   Server A: [A:2, B:0]    Server B: [A:0, B:1]
   A sends to B:
   Server B: [A:2, B:2]   (merge: take max of each component + increment own)
   
   Can detect concurrent events! (neither dominates the other)
   Used by: Amazon DynamoDB (original Dynamo paper)


3. HYBRID LOGICAL CLOCKS (HLC)
   ────────────────────────────
   Combines physical time with logical counter.
   Used by: CockroachDB, YugabyteDB
   
   HLC = (physical_time, logical_counter, node_id)
```

---

## 2. Failure Modes

```
┌──────────────────────────────────────────────────────────────┐
│                   TYPES OF FAILURES                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CRASH FAILURE                                               │
│  ─────────────                                               │
│  Node stops responding permanently.                          │
│  Detectable via heartbeat timeout.                           │
│  Solution: Replicas, failover                                │
│                                                              │
│  OMISSION FAILURE                                            │
│  ────────────────                                            │
│  Node fails to send or receive messages (network issue).     │
│  Node is alive but unreachable.                              │
│  Solution: Retries, timeouts, circuit breakers               │
│                                                              │
│  TIMING FAILURE                                              │
│  ──────────────                                              │
│  Node responds too late (beyond timeout).                    │
│  Common in overloaded systems.                               │
│  Solution: Timeouts, SLAs, back-pressure                     │
│                                                              │
│  BYZANTINE FAILURE (Hardest)                                 │
│  ─────────────────────────                                   │
│  Node sends WRONG/MALICIOUS data.                            │
│  Other nodes can't trust responses.                          │
│  Solution: BFT consensus (used in blockchain)                │
│  Most system design ignores this (assumes trusted nodes)     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Failure Detection

```
HEARTBEAT MECHANISM:

  ┌──────┐  heartbeat   ┌──────┐  heartbeat   ┌──────┐
  │Node A│──every 1s───►│Node B│──every 1s───►│Node C│
  └──────┘              └──────┘              └──────┘
       ▲                                          │
       └──────────── every 1s ────────────────────┘

  If no heartbeat for 3 consecutive intervals → mark as SUSPECT
  If still no response after N seconds → mark as DEAD

  GOSSIP PROTOCOL (Epidemic protocol):
  ────────────────────────────────────
  Each node randomly picks another node and shares its membership list.
  
  Node A → "B is alive (T=5), C is alive (T=3)"
  Node D → "B is alive (T=5), C is dead (T=10)"  ← newer info wins
  
  Eventually all nodes converge on same membership view.
  Used by: Cassandra, DynamoDB, Consul
```

---

## 4. Quorum

A quorum ensures consistency in a replicated system.

```
N = Total replicas
W = Write quorum (nodes that must acknowledge write)
R = Read quorum (nodes that must respond to read)

RULE: W + R > N  → Guarantees overlap → Strong consistency

Example with N=3:

  STRONG CONSISTENCY: W=2, R=2 (2+2 > 3 ✓)
  ┌──────┐ ┌──────┐ ┌──────┐
  │Node 1│ │Node 2│ │Node 3│
  │ W ✓  │ │ W ✓  │ │      │   Write: 2 out of 3 ACK
  │ R ✓  │ │      │ │ R ✓  │   Read:  2 out of 3 respond
  └──────┘ └──────┘ └──────┘   Overlap guaranteed!

  EVENTUAL CONSISTENCY: W=1, R=1 (1+1 ≤ 3 ✗)
  Fast but may read stale data.

  READ-HEAVY: W=3, R=1 (3+1 > 3 ✓)
  Slow writes but fast reads. Every node has latest data.

  WRITE-HEAVY: W=1, R=3 (1+3 > 3 ✓)
  Fast writes but slow reads. Must check all nodes.
```

---

## 5. Replication Strategies

```
SINGLE-LEADER REPLICATION:
  All writes → Leader → Replicates to followers
  Reads from any node (may be stale)
  Used by: PostgreSQL, MySQL, MongoDB (default)

MULTI-LEADER REPLICATION:
  Multiple leaders accept writes → sync between leaders
  Conflict resolution needed (LWW, merge, CRDT)
  Used by: Multi-region MySQL, CockroachDB

LEADERLESS REPLICATION:
  Any node accepts reads/writes → quorum-based
  Conflict resolution via vector clocks + read repair
  Used by: Cassandra, DynamoDB, Riak

┌──────────────────┬────────────┬──────────┬────────────┐
│ Strategy         │ Writes     │ Reads    │ Complexity │
├──────────────────┼────────────┼──────────┼────────────┤
│ Single-Leader    │ 1 node     │ Any node │ Low        │
│ Multi-Leader     │ N leaders  │ Any node │ High       │
│ Leaderless       │ Any node   │ Quorum   │ Medium     │
└──────────────────┴────────────┴──────────┴────────────┘
```

---

## 6. Consistent Reads Patterns

```
READ REPAIR:
  Client reads from 3 replicas → gets different values
  Client detects stale replica → sends update to fix it

  Client ──read──► Node A: v2 (latest)
  Client ──read──► Node B: v1 (stale!)
  Client ──read──► Node C: v2 (latest)
  
  Client ──repair──► Node B: "update to v2"


ANTI-ENTROPY (Background repair):
  Background process continuously compares replicas
  Uses Merkle trees to find differences efficiently
  Used by: Cassandra, DynamoDB
```

---

## 7. Key Takeaways for Interviews

1. **No global clock** — use logical clocks or HLC for ordering
2. **Quorum (W+R>N)** gives you tunable consistency
3. **Gossip protocol** for failure detection in large clusters
4. **Single-leader** is simplest; **leaderless** for highest availability
5. **Read repair + anti-entropy** keep replicas in sync
6. **Byzantine failures** are ignored in most system designs (trusted datacenter)
7. Always mention **network partitions** as a real scenario to handle
