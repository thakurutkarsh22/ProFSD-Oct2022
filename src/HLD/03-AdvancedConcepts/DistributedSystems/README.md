# Distributed Systems — Deep Dives

This folder contains a dedicated, detailed document for every major topic
introduced in [`../01-DistributedSystems.md`](../01-DistributedSystems.md).
Each document is structured the same way: **why it matters → core problem →
techniques (with diagrams) → trade-offs → real-world systems → interview Q&A**.

---

## Suggested reading order

```
    ┌────────────────────────────────────────────────────────────┐
    │  START: 01-DistributedSystems.md  (map of the territory)   │
    └────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
   FOUNDATIONS           AGREEMENT             OPERATIONAL
        │                     │                     │
   01-Clocks             06-Quorum             11-Delivery
   02-Consistency        ../02-Consensus       12-BackPressure
   03-FailureModes       ../03-Transactions    13-Discovery
   04-Detection          10-SMR-WAL            14-DeadlockDetection
   05-Partitions         07-Replication
                         08-AntiEntropy
                         09-Conflicts
```

---

## Index

| # | Topic | Key questions answered |
|---|---|---|
| 01 | [Clocks & Ordering](./01-ClocksAndOrdering.md) | How do nodes agree on "what happened first"? Lamport, vector, HLC, TrueTime. |
| 02 | [Consistency Models](./02-ConsistencyModels.md) | Linearizable, sequential, causal, session guarantees, eventual — when to pick which. |
| 03 | [Failure Modes](./03-FailureModes.md) | Crash, omission, timing, response, Byzantine, gray failures. |
| 04 | [Failure Detection](./04-FailureDetection.md) | Heartbeats, Phi Accrual, Gossip, SWIM. |
| 05 | [Network Partitions & Split-Brain](./05-NetworkPartitions.md) | Partition anatomy, fencing tokens, STONITH, witness nodes. |
| 06 | [Quorum](./06-Quorum.md) | N/W/R math, sloppy quorums, hinted handoff, witness replicas. |
| 07 | [Replication Strategies](./07-ReplicationStrategies.md) | Single/multi-leader/leaderless, chain replication, sync modes, replication lag. |
| 08 | [Anti-Entropy & Read Repair](./08-AntiEntropy.md) | Foreground/background repair, Merkle trees, hinted handoff. |
| 09 | [Conflict Resolution](./09-ConflictResolution.md) | LWW, siblings/merging, CRDTs. |
| 10 | [State Machine Replication & WAL](./10-StateMachineReplication.md) | Replicated log, WAL, snapshots, log compaction. |
| 11 | [Delivery Semantics & Idempotency](./11-DeliverySemantics.md) | At-most / at-least / exactly-once, idempotency keys, dedupe. |
| 12 | [Back-Pressure, Retries & Circuit Breakers](./12-BackPressureAndRetries.md) | Backoff + jitter, circuit breakers, load shedding, hedged requests. |
| 13 | [Service Discovery & Membership](./13-ServiceDiscovery.md) | Client-side vs server-side discovery, registration, health checks. |
| 14 | [Deadlock Detection](./14-DeadlockDetection.md) | Coffman conditions, WFGs, Chandy–Misra–Haas edge-chasing, phantom deadlocks, timeouts vs detection. |

### Related (outside this folder)

- [`../02-Consensus.md`](../02-Consensus.md) — Paxos, Raft, ZAB (the "how" of agreement)
- [`../03-DistributedTransactions.md`](../03-DistributedTransactions.md) — 2PC, 3PC, Saga
- [`../../02-BuildingBlocks/01-CAPTheorem.md`](../../02-BuildingBlocks/01-CAPTheorem.md) — CAP & PACELC
- [`../../02-BuildingBlocks/03-ConsistentHashing.md`](../../02-BuildingBlocks/03-ConsistentHashing.md) — data partitioning
