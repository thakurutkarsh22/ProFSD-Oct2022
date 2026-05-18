# Ticket Booking — Interview Q&A Cheat Sheet

Companion to `Readme.md`. The Readme explains the *design*. This file captures the
questions an interviewer (or a future you) will actually ask, with the reasoning
behind each design decision.

---

## Q1. What does `Map<String, SeatStatus> seatStatus` in `Show.java` actually store?

**TL;DR** — per-show seat occupancy. Key = `seatId` (e.g. `"A1"`), value =
`SeatStatus` enum (`AVAILABLE` / `BOOKED`).

### Concrete example

Imagine a `Screen` with 6 seats: `A1, A2, A3, B1, B2, B3`. Right after the
`Show` is constructed:

```text
{
  "A1" -> AVAILABLE,
  "A2" -> AVAILABLE,
  "A3" -> AVAILABLE,
  "B1" -> AVAILABLE,
  "B2" -> AVAILABLE,
  "B3" -> AVAILABLE
}
```

After Alice confirms her booking for `[A1, A2]`:

```text
{
  "A1" -> BOOKED,        <-- changed
  "A2" -> BOOKED,        <-- changed
  "A3" -> AVAILABLE,
  "B1" -> AVAILABLE,
  "B2" -> AVAILABLE,
  "B3" -> AVAILABLE
}
```

### Why per-Show and not per-Seat?

The same physical `Seat A1` lives on the `Screen` and is reused by every show.
It can simultaneously be:

- `BOOKED` for the 6 PM show, **and**
- `AVAILABLE` for the 9 PM show on the same screen.

So each `Show` owns its own independent `seatStatus` map. The `seatPrice` map
follows the same shape for the same reason (weekend surcharge on the 9 PM show
only, etc.).

### Threading note

It's a plain `HashMap`, not `ConcurrentHashMap`. Safe because every read/write
runs under the per-show `ReentrantLock` held in `BookingService`. Centralizing
locking in the service keeps the model class "dumb."

---

## Q2. Can we add a pricing strategy where Dhurandhar = ₹500 base but South Indian films = ₹200 base?

**TL;DR** — Yes, via the **Strategy pattern**. We added a `PricingStrategy`
interface plus a `DefaultPricingStrategy` that does
`base(movie) × multiplier(seatType)`.

### Resolution order (most specific wins)

1. **Per-movie override**  →  e.g. `"m_dhurandhar"` → `500.0`
2. **Per-language default** →  e.g. `"Tamil"` / `"Telugu"` / `"Kannada"` / `"Malayalam"` → `200.0`
3. **Global fallback**     →  `300.0`

### Seat multipliers

```text
REGULAR  × 1.0
PREMIUM  × 1.5
RECLINER × 2.0
```

So Dhurandhar on a RECLINER = `500 × 2.0 = 1000`. KGF (Kannada) on a PREMIUM
seat = `200 × 1.5 = 300`.

### Where the strategy plugs in

`Show`'s constructor takes a `PricingStrategy` and computes `seatPrice` once at
construction. The price is **frozen for the life of the show** so two reads
always agree — nothing surprises a user mid-booking.

### What to *say* in the interview (but not code unless asked)

- Add `LocalDateTime showStart` to `PricingStrategy.priceFor(...)` and you can
  layer in weekend / prime-time surcharges.
- Compose multiple strategies via a `CompositePricingStrategy` to build a
  pipeline of rules (Chain of Responsibility) — Open/Closed for new rules.

---

## Q3. What happens if we use `HashMap` instead of `ConcurrentHashMap` in `ShowRepository`?

**TL;DR** — Four distinct failure modes. The map gets *worse* as concurrency grows.

### 1. Memory visibility — writes may never be seen

`HashMap` has zero synchronization. The Java Memory Model gives you no
happens-before edge between thread A's `put()` and thread B's `get()`.

```text
Thread A (admin)             Thread B (booking)
  shows.put("s1", show)        Show s = shows.get("s1");
                               // legally returns null forever
```

### 2. Structural corruption on concurrent `put`

- **Java 7:** concurrent resize could form a cycle in a bucket → next `get()`
  spins forever, pinning CPU. The famous outage bug.
- **Java 8+:** the infinite-loop is gone, but you can still: lose entries, get
  NPE inside `get()`, read the wrong value for a key (mis-bucketed during
  resize), or get a wrong `size()`.

### 3. `getAllShows()` blows up the moment anyone iterates

```java
public Map<String, Show> getAllShows() { return shows; }
```

With `HashMap` + a concurrent `addShow()` → `ConcurrentModificationException`,
sometimes silently skipping/duplicating entries.

### 4. Check-then-act is impossible to make atomic

```java
if (!shows.containsKey(id)) shows.put(id, show);  // duplicate-show bug
```

`ConcurrentHashMap` gives you `putIfAbsent` / `computeIfAbsent` — atomic
lock-free for the common case.

### Bonus: defensive return

Even with CHM, returning the *live* map from `getAllShows()` is leaky. Better:
`Collections.unmodifiableMap(shows)` — cheap, still backed by CHM, callers
still see updates.

### One-line interview answer

> "`HashMap` is structurally unsafe under concurrent writes; even without writes
> it has no memory-visibility guarantee. `synchronizedMap` funnels every read
> through one lock. `ConcurrentHashMap` gives lock-free reads, per-bucket write
> locks, weakly-consistent iterators, and atomic compound ops — exactly the
> read-heavy, occasional-write workload of a show registry."

---

## Q4. What do `showLocks` and `showSeatLocks` in `InMemorySeatLockProvider` store?

**TL;DR**

| Field | Type | Stores |
|---|---|---|
| `showLocks` | `Map<String, ReentrantLock>` | the **mutex** — one `ReentrantLock` per show |
| `showSeatLocks` | `Map<String, Map<String, SeatLock>>` | the **soft-hold inventory** — who's holding which seat with what TTL |

### Walkthrough

TTL = 2 seconds. Three actions:

```text
t=12:00:00  u_alice  lockSeats("show-1", [A1, A2])
t=12:00:01  u_bob    lockSeats("show-1", [B1, B2])
t=12:00:02  u_charlie lockSeats("show-9", [C5])
```

State after all three:

```text
showLocks (one fair ReentrantLock per show; never thrown away):
┌─────────┬──────────────────┐
│ "show-1"│ ReentrantLock@1A │   ← shared by Alice + Bob (same show)
├─────────┼──────────────────┤
│ "show-9"│ ReentrantLock@2B │   ← Charlie's; INDEPENDENT mutex
└─────────┴──────────────────┘

showSeatLocks (showId -> seatId -> SeatLock):
{
  "show-1" -> {
     "A1" -> SeatLock(owner=u_alice, expires=12:00:02),
     "A2" -> SeatLock(owner=u_alice, expires=12:00:02),
     "B1" -> SeatLock(owner=u_bob,   expires=12:00:03),
     "B2" -> SeatLock(owner=u_bob,   expires=12:00:03)
  },
  "show-9" -> {
     "C5" -> SeatLock(owner=u_charlie, expires=12:00:04)
  }
}
```

### After expiry (t = 12:00:05)

- `validateLock(...)` returns `false` for any of the above (`isExpired()` check).
- Background sweep empties the inner maps:

```text
showSeatLocks: { "show-1" -> {}, "show-9" -> {} }
```

- `showLocks` is **NOT** cleared — the `ReentrantLock` instances must persist so
  future threads on the same show get the *same* mutex (otherwise mutual
  exclusion silently breaks).

---

## Q5. Why is the outer map `ConcurrentHashMap` but the inner map plain `HashMap`?

**TL;DR**
> Use `ConcurrentHashMap` where the map is touched by multiple threads at the
> same time. Use plain `HashMap` where another lock has already arranged that
> only one thread can be there.

### Scenario A — two threads, different shows (parallel)

```text
Alice (show-1)                          Bob (show-9)
  showLocks.computeIfAbsent("show-1")    showLocks.computeIfAbsent("show-9")
         ▲                                       ▲
         └──── BOTH threads write to the OUTER map at once.
               Plain HashMap would corrupt → MUST be CHM.

  Alice now holds innerMap_for_show1     Bob now holds innerMap_for_show9
  Alice.put("A1", lock)                  Bob.put("C5", lock)
         ▲                                       ▲
         └─ DIFFERENT HashMap instance           └─ DIFFERENT instance
            from Bob's. No collision possible.
```

### Scenario B — two threads, same show (forced sequential)

```text
Alice (show-1)                            Bob (show-1)
  computeIfAbsent("show-1") -> @1A         computeIfAbsent("show-1") -> @1A
  showLock.lock() ✓                        showLock.lock() ⏳ BLOCKED on Alice

  showSeatLocks.computeIfAbsent("show-1")  (still blocked)
   -> innerMap@α
  innerMap@α.put("A1", lock)               (still blocked)
  showLock.unlock() ───────────────────▶   showLock.lock() ✓
                                            innerMap@α.put("B1", lock)
                                            showLock.unlock()
```

Bob does NOT touch `innerMap@α` until **after** Alice unlocked. Same `HashMap`
instance, but only ever read/written by one thread at a time. That's
*"single-threaded by construction"*.

### What would go wrong if outer were plain `HashMap`?

```text
Alice: showLocks.computeIfAbsent("show-1", k -> new ReentrantLock(true))
Bob:   showLocks.computeIfAbsent("show-1", k -> new ReentrantLock(true))

Plain HashMap is NOT atomic. Both threads:
  1. See no entry for "show-1"
  2. Both create a fresh ReentrantLock instance
  3. Both put → one silently overwrites the other

Alice ends up holding ReentrantLock@X
Bob ends up holding   ReentrantLock@Y
```

→ They each "own" the show-1 mutex but don't actually exclude each other. The
whole locking scheme breaks silently. CHM's `computeIfAbsent` is documented to
run its lambda **at most once**, and every caller gets the same returned value.

### What if inner were also CHM?

Still correct, just wasteful. Every `put` / `get` would pay for volatile
reads, CAS retries, bucket synchronization — all guarding against concurrency
that the outer `ReentrantLock` already prevented. Locking twice for one logical
operation.

---

## Q6. Can `ConcurrentHashMap` do reads and writes concurrently?

**TL;DR** — Yes, but the rules are precise.

| Operation pair | Concurrent? |
|---|---|
| read + read (any keys) | **Yes** — lock-free volatile reads |
| read + write (any keys) | **Yes** — readers never block on writers |
| write + write, different buckets | **Yes** — bucket-level locking |
| write + write, same bucket | **No** — serialized on the bucket head node |
| write + write, same key via `computeIfAbsent` / `compute` / `merge` | **No** — atomic per key |

### What "read during a write" actually sees

A reader during a concurrent write sees **either** the pre-write **or**
post-write value — **never** a torn / half-written one. Guaranteed by:

- The `Node` array reference is `volatile`.
- `Node.val` and `Node.next` are `volatile`.
- Writes release-store, reads acquire-load → proper happens-before.

### Three gotchas

1. **`size()` is approximate** under heavy concurrent modification (CHM uses
   striped counters). Use `mappingCount()` for `long`; neither is "exact right now".
2. **Iterators are weakly consistent**, not snapshots. No `CME`, but may or
   may not reflect entries added during iteration.
3. **Compound ops you write yourself aren't atomic.**
   ```java
   if (!map.containsKey(k)) map.put(k, v);  // still a race
   ```
   Use `putIfAbsent` / `computeIfAbsent`.

---

## Q7. What does this line mean?

```java
return showLocks.computeIfAbsent(showId, k -> new ReentrantLock(true));
```

### Piece by piece

```text
return showLocks.computeIfAbsent(showId, k -> new ReentrantLock(true));
       │         │                │       │   │
       │         │                │       │   └─ (4) fair-ordering flag
       │         │                │       └───── (3) the "supplier" lambda
       │         │                └───────────── (2) the key we're looking up
       │         └────────────────────────────── (1) atomic get-or-create
       └──────────────────────────────────────── the ConcurrentHashMap field
```

1. **`computeIfAbsent(key, function)`** — atomic on CHM. If no value for `key`,
   run the function, store the result, return it. Else just return the existing
   value. The function runs **at most once per key**.
2. **`showId`** — the key (e.g. `"show-1"`).
3. **`k -> new ReentrantLock(true)`** — invoked only when no entry exists yet.
   `k` would equal `showId`; we don't use it.
4. **`new ReentrantLock(true)`** — `true` enables fairness: longest-waiting
   thread gets the lock next. Costs ~10–15% throughput vs non-fair, worth it
   for "first clicker wins" UX.

### Why atomicity matters

Two threads race on a brand-new `showId`:

```text
T1: computeIfAbsent("show-1", λ)         T2: computeIfAbsent("show-1", λ)
T1: synchronized on bucket               T2: BLOCKED, waits for T1
T1: sees no entry, runs λ
T1: creates ReentrantLock@1A
T1: stores under "show-1"
T1: returns @1A   ─────────────────────▶ T2: enters bucket
                                          T2: sees entry, skips lambda
                                          T2: returns @1A   ← SAME instance
```

Both walk away with the **same** lock instance — the whole point.

### Optional optimization: double-checked `computeIfAbsent`

For the post-warmup hot path:

```java
ReentrantLock l = showLocks.get(showId);                              // fast path
if (l != null) return l;
return showLocks.computeIfAbsent(showId, k -> new ReentrantLock(true)); // slow path
```

Faster because the `get()` path is fully lock-free.

---

## Q8. Is the lock on the *whole show*?

**TL;DR** — Yes, in two senses:
1. **Per-show, not per-seat and not global** (one `ReentrantLock` per show).
2. **While held, it gates the entire seat-map of that show** — anyone wanting
   *any* seat on the same show must wait.

### Trace — three concurrent users

```text
T1 lockSeats("show-1", [A1, A2])    T2 lockSeats("show-1", [B1])    T3 lockSeats("show-9", [Z9])

  showLock_show1.lock() ✓             showLock_show1.lock() ⏳         showLock_show9.lock() ✓
  │                                   (waiting on T1)                   │
  │  check A1, A2                                                       │  check Z9
  │  put A1, A2                                                         │  put Z9
  showLock_show1.unlock() ─────▶ T2 can proceed                         showLock_show9.unlock()
                                  │
                                  check B1
                                  put B1
                                  showLock_show1.unlock()
```

- **T1 + T2**: same show → forced to take turns even though they wanted different seats.
- **T1 + T3**: different shows → fully parallel.

### The hold is microseconds, not minutes

```java
showLock.lock();
try {
    // PHASE 1: validate
    // PHASE 2: write SeatLock entries
} finally { showLock.unlock(); }   // released BEFORE payment starts
```

| Concept | Mechanism | Duration |
|---|---|---|
| "I am mutating show-1's bookkeeping right now" | per-show `ReentrantLock` | microseconds |
| "I am holding seats A1, A2 while I pay" | `SeatLock` entries (TTL-bound) | minutes |

---

## Q9. If User 1 holds seats `[A1, A2]` for 9 minutes without paying, does User 2 wait 9 minutes to book seats `[K2, K3]`?

**TL;DR** — **No. User 2 waits ~100 microseconds, not 9 minutes.** This is the
single most important thing to internalize. There are **two different "locks"**
operating at two different timescales — don't conflate them.

### Wall-clock timeline

```text
T=0:00:00.000   User1 clicks "Book A1, A2" (Normal release → 9 min TTL)
                BookingService.initiateBooking() runs
                ├─ showLock_show1.lock()                      ← microseconds
                ├─ check seatStatus(A1)=AVAILABLE ✓
                ├─ check seatStatus(A2)=AVAILABLE ✓
                ├─ seatLockProvider.lockSeats(show1,[A1,A2])
                │   ├─ writes SeatLock(A1, owner=u1, expiresAt=T+9:00)
                │   └─ writes SeatLock(A2, owner=u1, expiresAt=T+9:00)
                ├─ save PENDING Booking
                └─ showLock_show1.unlock()                    ← released here!
T=0:00:00.0001  User1's call returns. User1 sits on payment page.
                ┌─────────────────────────────────────────┐
                │  NO ReentrantLock is held anywhere.     │
                │  The JVM is free. The only thing        │
                │  "holding" anything is two SeatLock     │
                │  rows in the HashMap saying:            │
                │    A1 -> owner=u1, expires=T+9:00       │
                │    A2 -> owner=u1, expires=T+9:00       │
                └─────────────────────────────────────────┘

(9 minutes of User1 doing nothing — NO JVM thread is blocked)

T=0:00:30.000   User2 clicks "Book K2, K3"  (30 seconds in)
                BookingService.initiateBooking() runs
                ├─ showLock_show1.lock()    ← NOBODY holds it. Acquired in ~1µs.
                ├─ check seatStatus(K2)=AVAILABLE ✓
                ├─ check seatStatus(K3)=AVAILABLE ✓
                ├─ seatLockProvider.lockSeats(show1,[K2,K3])
                │   ├─ scan seatLockMap for K2 → no entry → OK
                │   ├─ scan seatLockMap for K3 → no entry → OK
                │   ├─ (A1 and A2 are in the map, but we don't look at them)
                │   ├─ writes SeatLock(K2, owner=u2, expiresAt=T+9:30)
                │   └─ writes SeatLock(K3, owner=u2, expiresAt=T+9:30)
                ├─ save PENDING Booking
                └─ showLock_show1.unlock()
T=0:00:30.0001  User2's call returns SUCCESSFULLY.
```

**Total time User 2 waited: ~100 microseconds.**

### Why the "User 2 waits 9 minutes" mental model is wrong

You might picture this:

```text
[Wrong mental model]
User1 holds "show lock" for 9 minutes
  → all of show-1 is blocked
  → User2 waits 9 minutes
```

The reality:

```text
[Reality — TWO SEPARATE locks at TWO SEPARATE timescales]

(1) ReentrantLock_show1  — the bookkeeping mutex
    ┌─────────────────────────────────────────────────────┐
    │ Held only INSIDE initiateBooking() / confirmBooking()│
    │ Duration: ~100 µs per call                           │
    │ Released BEFORE the user starts paying               │
    └─────────────────────────────────────────────────────┘

(2) SeatLock entries in showSeatLocks  — the soft holds (DATA, not a mutex)
    ┌─────────────────────────────────────────────────────┐
    │ Persist for 9 minutes (= TTL).                       │
    │ Block ONLY the specific seats they name.             │
    │ A SeatLock on A1 has ZERO effect on K2, K3, Z9, ...  │
    └─────────────────────────────────────────────────────┘
```

The **ReentrantLock** (whole-show mutex) is *short* — released microseconds
after User 1's call returns. The **SeatLock entries** are *long-lived* — but
they aren't mutexes, they're just rows in a HashMap saying "owner=u1,
expires=T+9:00". They block other bookings **only** for the seats they name.

### State of the maps during the 9 minutes

While User 1 is sitting on the payment page (T=0:00:01 through T=0:09:00):

```text
showLocks:
{
  "show-1" -> ReentrantLock@1A  (FREE — User1's call finished, no one holds it)
}

showSeatLocks:
{
  "show-1" -> {
     "A1" -> SeatLock(owner=u1, expires=T+9:00),
     "A2" -> SeatLock(owner=u1, expires=T+9:00)
     // K2, K3 are NOT in this map → free
  }
}
```

When User 2 arrives at T=0:00:30:
- `showLocks["show-1"]` is free → User 2 acquires it instantly.
- `showSeatLocks["show-1"]` has entries for A1, A2 only. User 2 asks about K2, K3
  → not present → no conflict.

User 2 succeeds in ~100 µs.

### What WOULD make User 2 wait (or fail)?

| User 2's request | Result | Time |
|---|---|---|
| Books `[K2, K3]` (disjoint from User 1) | **Succeeds immediately** | ~100 µs |
| Books `[A1, K3]` (overlaps User 1) | **Fails immediately** with `SeatUnavailableException("Seats currently held: [A1, K3]")` | ~100 µs — NOT a wait |
| Books `[A1, A2]` (full overlap) | Same — fails immediately | ~100 µs |
| Books `[A1, A2]` *after* T+9:00 (TTL expired) | **Succeeds immediately** because Phase 1 treats expired locks as absent (`existing.isExpired() → continue`, line 148) | ~100 µs |

**Nobody is ever blocked in the JVM for the duration of another user's payment.**
That's the entire point of the two-phase design — the long-lived "wait" lives in
*data* (`SeatLock` rows with TTL), not in a held mutex.

### Proof in the code

This is literally Scenario 3 in `Main.java`:

```java
Runnable party1 = () -> {
    Booking b = bookingService.initiateBooking(
            "party1", showId, Arrays.asList("C1", "C2", "C3"));
    bookingService.confirmBooking(b.getId(), "party1");
};
Runnable party2 = () -> {
    Booking b = bookingService.initiateBooking(
            "party2", showId, Arrays.asList("D1", "D2"));
    bookingService.confirmBooking(b.getId(), "party2");
};
```

Both parties run in parallel on the same show, both finish in milliseconds.

### One-sentence summary to remember

> "The `ReentrantLock` serializes who is currently *mutating* show-1's
> bookkeeping (microseconds). The `SeatLock` rows record who is currently
> *holding specific seats* (minutes). Different users on different seats only
> contend on the first — for ~100 µs — and never on the second."

---

## Q10. Shouldn't the lock be at the **seat** level, not the show level?

**TL;DR** — No, for an in-memory single-JVM service handling realistic
theatre-scale traffic. Show-level wins on the math and avoids two classes of
bugs. Per-seat is correct only for extreme same-show concurrency or distributed
locks.

### The contention math

```text
1 hot show, 500 seats, sold out in 30 seconds at peak ⇒ ~17 attempts / second
Critical section ≈ 100 µs

P(another thread is in section while you are) ≈ 17 × 100µs / 1s = 0.17%
```

99.8% of bookings see an **uncontended** fair-lock acquire (one CAS, no
parking). Users don't perceive the wait — human threshold is ~50 ms, 500× the
critical section.

### Per-seat locking introduces four problems

**1. Deadlock from out-of-order acquisition**

```text
T1 wants [A1, A2]                 T2 wants [A2, A1]

T1: lock(A1) ✓                    T2: lock(A2) ✓
T1: lock(A2) ⏳ blocked on T2 ──┐  ┌── T2: lock(A1) ⏳ blocked on T1
                                 ╳
                                 DEADLOCK
```

Fix: always sort `seatIds` before locking. Implicit protocol; easy to break.

**2. Partial-failure rollback**

```text
T1 wants [A1, A2, A3]:
  lock(A1) ✓
  lock(A2) ✓
  tryLock(A3) ✗ — taken by T2

T1 must now unlock A2, unlock A1, return failure.
During rollback, anyone wanting A1 or A2 was needlessly blocked.
```

Show-lock has no rollback — Phase 1 only *reads*, Phase 2 commits everything atomically.

**3. The "all seats free" check stops being atomic**

By the time you've acquired seat 3's lock, what stopped seat 1's status from
changing? Nothing — unless you bring back a coarser lock anyway.

**4. Lock-acquisition latency**

N `ReentrantLock` acquires (CAS + maybe park) instead of one. Show-lock costs
one acquire regardless of party size.

### Side-by-side

| Concern | Show-level | Per-seat |
|---|---|---|
| Critical section length | ~100 µs total | N × ~20 µs |
| Different parties on different seats, same show | Serialized briefly | Truly parallel |
| Different parties on overlapping seats | Same — someone has to wait | Same |
| Different shows | Fully parallel | Fully parallel |
| Deadlock possibility | None | Yes, unless sorted |
| Partial-failure rollback | Not needed | Required |
| Atomic "check all seats then commit" | Free | Needs extra coordination |
| Code complexity | Low | High |
| Distributed implementation (Redis) | Awkward (hot key) | Natural (key per seat) |

### Where per-seat IS the right call

1. **Massive same-show concurrency** — stadium booking for IPL final, BTS
   concert. Show-lock becomes a bottleneck. Path: shard it (per row → per
   section → per seat with sorted acquisition).
2. **Distributed locks** — Redis `SET NX EX` per seat-key is natural; TTLs
   side-step deadlock; show-key would be a hot Redis key.

### Interview answer

> "I picked show-level locking because the critical section is microseconds and
> contention is statistically rare even on hot shows. It eliminates multi-seat
> deadlock and partial-failure rollback without measurably hurting throughput.
> If profiling showed the show-lock as a bottleneck — IPL-final scale — I'd
> shard: per-row, then per-section, then per-seat with sorted acquisition. **Granularity should follow measurement, not intuition.**"

---

## Q11. What's the seat-lock TTL for a Dhurandhar opening night?

**TL;DR** — Industry observable: BookMyShow drops the user-facing payment
countdown to **4–5 minutes** for blockbuster openings (vs ~10 min on a normal
weekday). Server-side TTL is usually that **plus ~30 s grace** for payment-gateway
latency.

### Two different timers — don't confuse them

| Timer | What it does | Typical value |
|---|---|---|
| Mutex hold time (`ReentrantLock`) | Serializes JVM bookkeeping | Microseconds — never user-visible |
| Seat soft-hold TTL (`SeatLock.expiresAt`) | "Pay within N minutes or lose the seat" | Minutes |

The "lock timeout" you usually mean is the second one.

### Observable BMS defaults (from the UI countdown)

- Normal release (mid-week, mid-tier movie): ~**10 minutes**.
- Weekend / popular: ~**8 minutes**.
- Blockbuster opening (Pathaan day 1, Jawan day 1, **Dhurandhar opening**):
  ~**5 minutes**, sometimes **4**.

### Why shorten for big openings?

A seat-turnover lever. Trade-off:

- TTL too long → bots/scalpers reserve and never pay, blocking real buyers.
- TTL too short → legit users on slow UPI / OTP flows lose their seats.

### Where this is configured in our code

```java
SeatLockProvider seatLockProvider = new InMemorySeatLockProvider(
        /*lockTimeoutSeconds=*/ 2,
        /*cleanupIntervalSeconds=*/ 1);
```

We use 2 seconds purely so Scenario 2 finishes during a live demo. Production
would be `300` (5 min) for Dhurandhar, `600` (10 min) for a normal show. The
constructor accepts it as a parameter for exactly that reason — pick per-event,
not at compile time.

### Interview answer

> "There's no global TTL — it's an ops dial. Industry default is 8–10 min;
> blockbuster openings cut it to 4–5 min to increase turnover and frustrate
> scalpers. `InMemorySeatLockProvider` takes `lockTimeoutSeconds` as a
> constructor param so it can be resolved from event metadata
> (`event.demandTier → TTL`) at runtime, not hardcoded."

---

## Quick index

| # | Question |
|---|---|
| Q1 | `seatStatus` map contents and per-Show vs per-Seat ownership |
| Q2 | Movie- and language-aware pricing strategy |
| Q3 | Cost of using `HashMap` instead of `ConcurrentHashMap` in repos |
| Q4 | What `showLocks` and `showSeatLocks` hold |
| Q5 | Why outer = `ConcurrentHashMap` but inner = plain `HashMap` |
| Q6 | `ConcurrentHashMap` read/write concurrency rules |
| Q7 | The `computeIfAbsent(showId, k -> new ReentrantLock(true))` line |
| Q8 | Is the lock on the whole show? |
| Q9 | Does User 2 wait 9 min while User 1 is paying? (the key walkthrough) |
| Q10 | Show-level vs seat-level locking trade-off |
| Q11 | Lock TTL on a blockbuster opening (Dhurandhar) |
