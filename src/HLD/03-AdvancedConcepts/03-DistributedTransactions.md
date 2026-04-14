# Distributed Transactions (2PC & Saga)

> **Difficulty:** Medium-Hard | **Time:** 2 hours | **Priority:** Must Know

---

## The Problem

In a microservices world, a single business operation may span multiple services, each with its own database. How do you ensure all-or-nothing?

```
Order Service: Create order    ──► Success
Payment Service: Charge card   ──► Success
Inventory Service: Reserve item ──► FAILURE!

What now? Order exists, money charged, but no item!
Need to UNDO the first two operations.
```

---

## 1. Two-Phase Commit (2PC)

A coordinator ensures all participants either COMMIT or ABORT.

```
PHASE 1: PREPARE (Voting Phase)

  Coordinator                 Service A        Service B        Service C
      │                          │                │                │
      │──── PREPARE ────────────►│                │                │
      │──── PREPARE ─────────────────────────────►│                │
      │──── PREPARE ──────────────────────────────────────────────►│
      │                          │                │                │
      │◄─── YES (ready) ────────│                │                │
      │◄─── YES (ready) ─────────────────────────│                │
      │◄─── YES (ready) ──────────────────────────────────────────│
      │                          │                │                │

PHASE 2: COMMIT (Decision Phase)

  All said YES:
      │──── COMMIT ─────────────►│  applies changes
      │──── COMMIT ──────────────────────────────►│  applies changes
      │──── COMMIT ───────────────────────────────────────────────►│

  If ANY said NO:
      │──── ABORT ──────────────►│  rolls back
      │──── ABORT ───────────────────────────────►│  rolls back
      │──── ABORT ────────────────────────────────────────────────►│
```

### 2PC Problems
```
┌──────────────────────────────────────────────────────────────┐
│                   2PC PROBLEMS                                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  1. BLOCKING: If coordinator crashes after Phase 1,          │
│     all participants are STUCK holding locks!                │
│     They can't commit or abort until coordinator recovers.   │
│                                                              │
│  2. SINGLE POINT OF FAILURE: Coordinator is critical.        │
│     If it dies, entire transaction is in limbo.              │
│                                                              │
│  3. LATENCY: 2 round trips + all participants must respond.  │
│     Not suitable for high-throughput systems.                │
│                                                              │
│  4. SCALABILITY: Doesn't work well across microservices      │
│     with different databases.                                │
│                                                              │
│  When to use: Same database cluster (e.g., XA transactions) │
│  When NOT to use: Microservices (use Saga instead)           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Saga Pattern

Break the distributed transaction into a sequence of local transactions, each with a compensating transaction (undo).

```
SAGA: SEQUENCE OF LOCAL TRANSACTIONS

  T1: Create Order         ←→  C1: Cancel Order
  T2: Reserve Inventory    ←→  C2: Release Inventory  
  T3: Charge Payment       ←→  C3: Refund Payment
  T4: Send Notification    ←→  C4: Send Cancellation

SUCCESS PATH:
  T1 ──► T2 ──► T3 ──► T4 ──► DONE ✓

FAILURE PATH (T3 fails):
  T1 ──► T2 ──► T3 ✗
                 │
                 ▼
         C2 ◄── C1 ◄── COMPENSATE
  (Release Inv) (Cancel Order)
```

### Saga Implementation: Choreography vs Orchestration

```
CHOREOGRAPHY (Event-Driven):
  Each service listens for events and acts independently.

  Order         Event Bus        Inventory       Payment
  Service                        Service         Service
    │                                │               │
    │──OrderCreated──►              │               │
    │                  ──────────► │               │
    │                  InventoryReserved──────────►│
    │                                │  PaymentCharged│
    │◄─────────────────────────────────────────────│

  Pros: Loose coupling, simple
  Cons: Hard to track overall flow, debugging is difficult
        Circular dependencies possible


ORCHESTRATION (Central Coordinator):
  A saga orchestrator tells each service what to do.

  ┌──────────────┐
  │    Saga      │
  │ Orchestrator │
  └──────┬───────┘
         │
         ├── Step 1 ──► Order Service: "Create order"
         │◄──────────── "Done"
         │
         ├── Step 2 ──► Inventory Service: "Reserve item"
         │◄──────────── "Done"
         │
         ├── Step 3 ──► Payment Service: "Charge card"
         │◄──────────── "FAILED!"
         │
         ├── Compensate ──► Inventory: "Release item"
         └── Compensate ──► Order: "Cancel order"

  Pros: Clear flow, easy to debug, centralized retry logic
  Cons: Orchestrator can become complex, single coordination point
```

---

## 3. 2PC vs Saga Comparison

```
┌─────────────────┬──────────────────────┬─────────────────────┐
│ Feature         │ 2PC                  │ Saga                │
├─────────────────┼──────────────────────┼─────────────────────┤
│ Consistency     │ Strong (ACID)        │ Eventual             │
│ Isolation       │ Full (locks held)    │ No isolation          │
│ Performance     │ Slow (blocking)      │ Fast (non-blocking)   │
│ Coupling        │ Tight                │ Loose                │
│ Failure handling│ Rollback             │ Compensating txn      │
│ Scalability     │ Poor                 │ Good                 │
│ Complexity      │ Protocol complexity  │ Business logic       │
│ Use case        │ Same DB cluster      │ Microservices        │
│ Examples        │ XA transactions      │ Order processing     │
│                 │ Database clusters    │ Booking systems      │
└─────────────────┴──────────────────────┴─────────────────────┘
```

---

## 4. Idempotency — Critical for Distributed Transactions

```
PROBLEM: Network retry sends the same payment twice!

  Client ──► Payment Service: "Charge $100"
  Client ──► Payment Service: "Charge $100"  (retry, network timeout)
  
  Without idempotency: Customer charged $200!

SOLUTION: Idempotency Key

  Client ──► Payment Service: 
             { "amount": 100, "idempotency_key": "uuid-abc-123" }
  
  First call:  Process payment, store idempotency_key
  Second call: Find existing idempotency_key → return cached result
  
  Customer charged exactly $100 ✓

  Implementation:
  ┌────────────────────────────────────────┐
  │ idempotency_keys table                 │
  │ ┌──────────────┬─────────┬───────────┐│
  │ │ key          │ status  │ response  ││
  │ ├──────────────┼─────────┼───────────┤│
  │ │ uuid-abc-123 │ SUCCESS │ {txn:456} ││
  │ └──────────────┴─────────┴───────────┘│
  └────────────────────────────────────────┘
```

---

## 5. Key Takeaways for Interviews

1. **2PC for databases**, **Saga for microservices** — this is the key distinction
2. **Orchestration Saga** is preferred in most interview answers (clearer to explain)
3. **Always mention idempotency** when designing payment/financial systems
4. **Compensating transactions** are the heart of Saga — always define them
5. **Eventual consistency** is acceptable in most business scenarios
6. Mention **outbox pattern** for reliable event publishing (write to DB + events atomically)
