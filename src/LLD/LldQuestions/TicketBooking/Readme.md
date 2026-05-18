# Ticket Booking — Basic LLD

A minimal but production-shaped Low-Level Design for a movie ticket booking
system (BookMyShow / IRCTC style), focused on correctly handling the
**hardest** part of the problem: **concurrent seat booking**.

---

## 1. Problem statement

Multiple users browse the same show at the same time and click "Book" on the
same seats. The system must guarantee:

| Invariant | What it means in plain English |
|---|---|
| **No double-booking** | A confirmed seat for a show belongs to exactly one user, ever. |
| **All-or-nothing party booking** | A user requesting `[A1, A2, A3]` either gets all 3 or none — never 2. |
| **No indefinite holds** | A user who walks away mid-payment must release their seats automatically. |
| **Fairness** | First clicker should usually win, not whoever the OS scheduler favors. |
| **Per-show isolation** | Heavy traffic on "Inception 7pm" should not slow down "Dune 9pm". |

---

## 2. Architecture

```
                        ┌─────────────────────────────┐
                        │           Main              │
                        │  (demo: 3 race scenarios)   │
                        └──────────────┬──────────────┘
                                       │
                                       ▼
                        ┌─────────────────────────────┐
                        │        BookingService       │
                        │   per-show ReentrantLock    │
                        └──────┬───────────┬──────────┘
                               │           │
              ┌────────────────┘           └────────────────┐
              ▼                                              ▼
   ┌──────────────────────┐                    ┌──────────────────────────┐
   │   ShowRepository     │                    │   SeatLockProvider       │
   │  ConcurrentHashMap   │                    │  (interface)             │
   │  of Show             │                    │                          │
   └──────────────────────┘                    │  InMemorySeatLockProvider│
                                                │  - per-show ReentrantLock│
                                                │    (fair)                │
                                                │  - SeatLock w/ TTL       │
                                                │  - background sweeper    │
                                                └──────────────────────────┘
```

---

## 3. Directory layout

```
TicketBooking/
├── Main.java                          # 3 concurrent demo scenarios
├── Readme.md                          # this file
├── models/                            # plain data classes + enums
│   ├── User.java
│   ├── Movie.java
│   ├── Theatre.java
│   ├── Screen.java
│   ├── Seat.java                      # row/col/type
│   ├── SeatType.java                  # REGULAR | PREMIUM | RECLINER
│   ├── SeatStatus.java                # AVAILABLE | BOOKED   (terminal only)
│   ├── Show.java                      # seatId -> SeatStatus, seatId -> price
│   ├── Booking.java                   # PENDING -> CONFIRMED / EXPIRED / CANCELLED
│   ├── BookingStatus.java
│   └── SeatLock.java                  # transient hold w/ TTL + ownership
├── lock/
│   ├── SeatLockProvider.java          # interface (swap to Redis later)
│   └── InMemorySeatLockProvider.java  # ReentrantLock + TTL + sweeper
├── repository/
│   ├── ShowRepository.java
│   └── BookingRepository.java
├── service/
│   └── BookingService.java            # 2-phase: initiate -> confirm
└── exceptions/
    ├── SeatUnavailableException.java
    ├── SeatLockExpiredException.java
    ├── ShowNotFoundException.java
    └── BookingNotFoundException.java
```

---

## 4. The two-phase booking lifecycle

```
   ┌──────────────────────┐    ┌──────────────────────┐    ┌──────────────────┐
   │ initiateBooking()    │───►│   <user pays>        │───►│ confirmBooking() │
   │                      │    │                      │    │                  │
   │ • check no seat is   │    │ external payment     │    │ • re-validate    │
   │   permanently BOOKED │    │ gateway, may take    │    │   lock still     │
   │ • lockSeats(ttl)     │    │ seconds-to-minutes   │    │   owned & alive  │
   │   (all-or-nothing)   │    │                      │    │ • mark BOOKED    │
   │ • PENDING booking    │    │ ⚠ no lock is held    │    │ • release lock   │
   │   row inserted       │    │ across this call     │    │ • CONFIRMED      │
   └──────────────────────┘    └──────────────────────┘    └──────────────────┘
```

Why two phases? Because **payment is slow and external**. Holding any kind of
strong lock across an external network call is a recipe for deadlocks, lost
holds on JVM crash, and head-of-line blocking. The seat lock is the *soft
hold*; the `SeatStatus.BOOKED` bit is the *durable commit*. Only the latter
survives a restart.

---

## 5. Concurrency primitives, in the order they matter

### 5a. Per-show `ReentrantLock(fair=true)` in `BookingService`

Both `initiateBooking()` and `confirmBooking()` perform a **compound action**:
read `Show.seatStatus` *and* mutate `SeatLockProvider`. If two threads
interleave between those steps, two users could simultaneously believe they
won the same seat. The per-show lock makes the compound action atomic.

We choose the lock granularity carefully:

| Granularity | Pro | Con |
|---|---|---|
| One global lock | Simplest | Whole site serializes; unusable at scale |
| **Per-show lock** ← chosen | Hot show isolation; different shows fully parallel | Slightly more memory |
| Per-seat lock | Maximum parallelism | Multi-seat acquire needs lock-ordering (deadlock risk) |

We pick **fair** because users perceive "first clicker wins" as fair; non-fair
locks let late arrivals barge ahead of patient ones, which on a hot release
looks like starvation. Cost is ~10–15% throughput, paid happily because the
critical section is microseconds while payment is seconds.

### 5b. `SeatLock` with TTL

Every soft hold carries `expiresAt = now + lockTimeoutSeconds`. Reads check
`isExpired()` first, so even if the cleanup sweep is slow we never hand a
stale lock authority over a seat. A daemon `ScheduledExecutorService` purges
expired entries so cold shows do not leak memory.

### 5c. ALL-OR-NOTHING acquire (`lockSeats` is two-phase internally)

```
PHASE 1 (validate): scan every seat in the requested party.
                    abort the call if ANY seat is held by another live user.
PHASE 2 (commit):   only if validation passed, write all locks at once.
```

Without this, a party-of-3 request could leave 2 stranded holds when the
third seat conflicts — those 2 holds would block other parties too.

### 5d. `ConcurrentHashMap` everywhere a map is shared

Used for `showLocks`, `showSeatLocks`, `ShowRepository`, `BookingRepository`.
We rely on `computeIfAbsent` being atomic so two threads racing on a brand-new
showId still see the *same* `ReentrantLock` instance.

### 5e. Lock ownership check on release

`unlockSeats(showId, seatIds, userId)` only removes a lock if `userId` owns
it. Defends against a buggy/malicious caller releasing someone else's hold.

### 5f. `volatile BookingStatus`

Status is read by threads that did not acquire the show lock (e.g. metrics,
status-polling endpoints). `volatile` gives them a happens-before guarantee
without forcing them to acquire any lock.

### 5g. Cleanup sweep uses `tryLock`, not `lock`

The cleanup thread must never block the booking hot path. Skipping a busy
show this tick is fine — the next tick will catch it, and lazy-purge on read
keeps correctness in the meantime.

---

## 6. The two locks, side by side

There are **two** distinct ReentrantLocks per show in this design and that
sometimes confuses readers. They are intentionally separate.

| Lock | Lives in | Protects | Why |
|---|---|---|---|
| `BookingService.showLocks[showId]` | the service | the *compound action* of (read `Show.seatStatus` + interact with `SeatLockProvider`) | so the two checks are atomic |
| `InMemorySeatLockProvider.showLocks[showId]` | the provider | the provider's own `seatId -> SeatLock` map | so the provider stays correct even when called from a non-`BookingService` caller (e.g. an admin tool) |

Keeping them separate means we can swap `InMemorySeatLockProvider` for a
Redis-backed one (`SETNX`/`PEXPIRE`) without losing the service-level
atomicity guarantee.

---

## 7. Demo (`Main.java`)

The demo runs **three scenarios**, each verifying a key invariant.

### Scenario 1 — Thundering herd

50 threads start at the same instant and try to lock seats `[A1, A2]`.
**Exactly one** thread must win. The demo asserts this and aborts otherwise.

```
=== Scenario 1: 50 users race for seats [A1, A2] ===
  WINNER u49 -> bookingId=b6bdc1df-3aaf-4b6b-985c-6830de1c2acb
  total winners=1 losers=49
```

### Scenario 2 — Lock TTL expiry

`u_slow` locks `[B1, B2]` but never confirms. `u_fast` is rejected
immediately (lock is fresh), waits past the TTL, and now succeeds. When
`u_slow` finally tries to confirm, the system rejects them with
`SeatLockExpiredException`.

```
=== Scenario 2: lock TTL expiry transfers seats to a new user ===
  u_slow locked [B1, B2] (bookingId=...)
  u_fast rejected (as expected): Seats currently held by another user: [B1, B2]
  u_fast acquired [B1, B2] after TTL (bookingId=...)
  u_slow.confirm rejected (as expected): Seat lock expired before payment confirmation
  u_fast confirmed -> status=CONFIRMED
```

### Scenario 3 — Disjoint parties

Two parties book disjoint seats on the **same show** in parallel. Both must
succeed. This proves the per-show lock is fine-grained enough to allow
genuine concurrency on the same show.

```
=== Scenario 3: independent parties book the same show in parallel ===
  party1 confirmed seats [C1,C2,C3]
  party2 confirmed seats [D1,D2]
```

---

## 8. How to run

```bash
cd bel-20
javac -d /tmp/tb-build \
  LLD/LldQuestions/TicketBooking/Main.java \
  LLD/LldQuestions/TicketBooking/models/*.java \
  LLD/LldQuestions/TicketBooking/lock/*.java \
  LLD/LldQuestions/TicketBooking/repository/*.java \
  LLD/LldQuestions/TicketBooking/service/*.java \
  LLD/LldQuestions/TicketBooking/exceptions/*.java
java -cp /tmp/tb-build LLD.LldQuestions.TicketBooking.Main
```

---

## 9. What was deliberately left out (and what to say in an interview)

| Skipped | Why it's fine for "basic LLD" | What to add for "production" |
|---|---|---|
| Payment integration | Demo focuses on concurrency | A `PaymentService` interface + 3rd-party gateway adapter |
| Persistence | In-memory repos keep demo runnable | JDBC/JPA repos with `@Transactional` around `confirmBooking` |
| Distributed coordination | Single-JVM is the explicit scope | Swap `InMemorySeatLockProvider` for a Redis (`SETNX`+`PEXPIRE`) impl |
| Notifications | Out of LLD scope | Observer pattern: emit `BookingConfirmed` events to email/SMS subscribers |
| Cancellation refunds | Skeleton present in `cancelPendingBooking`, no refund logic | Refund saga + idempotency keys |
| Pricing strategy | Simple per-seat-type defaults | Strategy pattern for dynamic pricing (weekend, demand, loyalty) |
| Auth / rate limiting | Not part of booking core | API gateway concern, not service concern |

---

## 10. Interview-ready one-liner

> The two-phase booking flow (`initiate` ➜ `confirm`) puts a short-lived,
> TTL-bounded **soft lock** between the user's seat-pick click and the
> payment-success callback. A per-show fair `ReentrantLock` makes the
> compound `(check seatStatus + acquire/release SeatLock)` action atomic,
> while a background sweeper bounds the lock table size and lazy expiry
> checks keep the hot path correct even if the sweeper is late.
