# Consensus Algorithms & Leader Election

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Good to Know

---

## Why Consensus Matters

When multiple nodes need to agree on a single value (e.g., "who is the leader?", "was this transaction committed?"), you need a consensus algorithm.

---

## 1. The Problem

```
3 nodes need to agree: "Should we commit transaction T?"

  ┌──────┐    ┌──────┐    ┌──────┐
  │Node A│    │Node B│    │Node C│
  │ YES  │    │  ???  │   │ YES  │
  └──────┘    └──────┘    └──────┘
                │
           Network partition!
           Node B can't communicate.

  Questions:
  - Can A and C proceed without B? (Availability vs Consistency)
  - What if B comes back with a different answer?
  - What if A crashes mid-vote?
```

---

## 2. Raft Consensus Algorithm

Raft is the most understandable consensus algorithm. Know this one for interviews.

```
RAFT: 3 STATES FOR EACH NODE

  ┌───────────┐    timeout    ┌───────────┐    wins election    ┌──────────┐
  │ FOLLOWER  │──────────────►│ CANDIDATE │────────────────────►│  LEADER  │
  └───────────┘               └───────────┘                     └──────────┘
       ▲                           │                                  │
       │                           │ loses election                   │
       │                           │ (another wins)                   │
       │                           ▼                                  │
       │                      Back to FOLLOWER                        │
       │                                                              │
       └──────────────── discovers new leader ────────────────────────┘
```

### Leader Election in Raft
```
STEP 1: Leader sends heartbeats to all followers

  Leader ──heartbeat──► Follower A
  Leader ──heartbeat──► Follower B
  Leader ──heartbeat──► Follower C

STEP 2: Leader dies. Followers stop receiving heartbeats.

  [Leader] ✗ DEAD
  Follower A: "No heartbeat for 300ms..."
  Follower B: "No heartbeat for 350ms..."

STEP 3: First timeout → becomes Candidate, starts election

  Follower A (timeout first) → CANDIDATE
  ├── Increments "term" to 2
  ├── Votes for itself
  └── Sends RequestVote to B, C

  Follower B: "Term 2 > my term 1, A's log is up-to-date" → VOTE YES
  Follower C: "Term 2 > my term 1, A's log is up-to-date" → VOTE YES

STEP 4: Candidate A gets majority (3/3) → becomes LEADER

  A: LEADER (term 2)
  ├── Starts sending heartbeats
  └── Begins accepting client requests
```

### Log Replication in Raft
```
Client: "SET x = 5"

  Leader                  Follower A           Follower B
    │                         │                     │
    │──AppendEntries─────────►│                     │
    │  (SET x=5, term=2)      │                     │
    │──AppendEntries──────────────────────────────►│
    │                         │                     │
    │◄─ACK──────────────────│                     │
    │◄─ACK────────────────────────────────────────│
    │                         │                     │
    │  Majority ACK'd (2/3)   │                     │
    │  COMMIT! Apply to        │                     │
    │  state machine           │                     │
    │                         │                     │
    │  Notify followers:       │                     │
    │  "Entry committed"       │                     │
```

---

## 3. Paxos (Brief Overview)

Paxos is theoretically important but harder to understand. Just know the key idea.

```
Paxos Roles:
  Proposer:  Suggests a value
  Acceptor:  Votes on proposals
  Learner:   Learns the decided value

Two-Phase Protocol:
  Phase 1 (Prepare):
    Proposer → Acceptors: "I want to propose with number N"
    Acceptors → Proposer: "OK, here's the highest value I've accepted"

  Phase 2 (Accept):
    Proposer → Acceptors: "Accept value V with number N"
    Acceptors: If no higher N seen → Accept

  If majority accepts → Value is CHOSEN.

Used by: Google Chubby, Google Spanner (Multi-Paxos)
```

---

## 4. ZooKeeper & etcd

These are coordination services used for consensus in distributed systems.

```
┌────────────────────────────────────────────────────────────┐
│           ZOOKEEPER / ETCD USE CASES                        │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  1. LEADER ELECTION                                        │
│     Services compete to create a "leader" node             │
│     First one wins → becomes leader                        │
│     Others watch → if leader dies, new election            │
│                                                            │
│  2. DISTRIBUTED CONFIGURATION                              │
│     Store config centrally, all services watch for changes │
│     Config update → all services notified instantly         │
│                                                            │
│  3. SERVICE DISCOVERY                                      │
│     Services register themselves                           │
│     Clients query ZK to find service instances             │
│                                                            │
│  4. DISTRIBUTED LOCKING                                    │
│     Create ephemeral node → acquire lock                   │
│     Node deleted when session ends → lock released         │
│                                                            │
│  5. GROUP MEMBERSHIP                                       │
│     Track which nodes are alive in a cluster               │
│                                                            │
│  ZooKeeper: Uses ZAB protocol (Zookeeper Atomic Broadcast)│
│  etcd: Uses Raft consensus                                 │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## 5. Key Takeaways for Interviews

1. **Know Raft well** — it's the standard consensus algorithm asked in interviews
2. **Leader election** = Raft/ZooKeeper (don't implement your own)
3. **ZooKeeper/etcd** for: config management, leader election, distributed locks, service discovery
4. **Quorum = majority** — 3 nodes can tolerate 1 failure, 5 nodes can tolerate 2
5. **Split brain** — when two nodes both think they're leader. Raft prevents this with terms.
6. Mention these when designing: Kafka (ZK for coordination), database leader election, distributed locks
