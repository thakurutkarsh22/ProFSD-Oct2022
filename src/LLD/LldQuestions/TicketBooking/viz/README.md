# Ticket Booking — Animated Visualizer

A single-file, zero-dependency animated demo of every scenario implemented in
`../service/BookingService.java`, `../lock/InMemorySeatLockProvider.java`,
and `../pricing/DefaultPricingStrategy.java`.

## Run

```bash
# directly:
open bel-20/LLD/LldQuestions/TicketBooking/viz/index.html

# or via a local HTTP server (works better with some browsers):
cd bel-20/LLD/LldQuestions/TicketBooking/viz && python3 -m http.server 8765
# then open http://127.0.0.1:8765/
```

Zero build, zero `npm install`, all CSS/JS inline.

## What's animated

| Java                                  | Animated counterpart                                                |
| ------------------------------------- | ------------------------------------------------------------------- |
| `ReentrantLock(true)` per show        | Lock icon + FIFO queue widget per show (two visible in scenario 4)  |
| `lockSeats()` validate-then-commit    | Seats turn yellow only after the full check passes                  |
| `SeatLock(expiresAt)` TTL             | Animated SVG ring drains around each locked seat                    |
| Background `purgeExpiredLocks()`      | Expired seats auto-revert to grey                                   |
| `confirmBooking()`                    | Yellow → green + owner badge                                        |
| `cancelPendingBooking()`              | Grey "cancel pulse" before seats revert                             |
| `unlockSeats()` ownership check       | Foreign release leaves locks intact (scenario 9)                    |
| `SeatUnavailableException`            | Red flash + shake on the conflicting seats only                     |
| `SeatLockExpiredException`            | "lock expired/lost" log row, user tagged EXPIRED                    |
| `SeatType` (REGULAR/PREMIUM/RECLINER) | Coloured "type band" along the top of every seat                    |
| `DefaultPricingStrategy`              | Live ₹ price tags on every seat; Movie picker recomputes everything |
| Booking total                         | Per-user card shows the running ₹ total                             |
| **Per-thread execution timeline**     | **Gantt-style swim-lane chart at the bottom** — one row per user, coloured segments per phase (QUEUING / CRIT / HOLD / PAY / CONFIRM / REJECTED / EXPIRED / CANCELLED / RE-ENTRY). Updates live; "now" marker scrolls right. |

## All 10 scenarios

| # | Scenario | What it proves |
|---|---|---|
| 0 | **Pricing strategy** | Movie override > language default > global fallback, then × seat-type multiplier. Cycles through 3 movies. |
| 1 | **Thundering herd** | 12 users race for `[A1, A2]`. Exactly one wins. |
| 2 | **TTL expiry** | Slow user locks → never pays → TTL drains → fast user retries → succeeds. Slow user's late confirm rejected. |
| 3 | **Disjoint parties (same show)** | Two parties book different seats on the same show in parallel. |
| 4 | **Multi-show isolation** | Same seat IDs on `show-1` and `show-2` book in parallel — different `ReentrantLock`s. |
| 5 | **Cancel pending booking** | `cancelPendingBooking()` releases locks instantly, no need to wait for TTL. |
| 6 | **Seat already BOOKED** | A confirmed seat rejects future tries via `show.isBooked()` (different code path than lock conflict). |
| 7 | **All-or-nothing on partial overlap** | u2 wants `[A1, A2, A3, A4]` but A2 is held — only A2 flashes red, A1/A3/A4 stay grey. |
| 8 | **Reentrant re-lock** | Same user re-acquires own lock → TTL ring snaps back to full. |
| 9 | **Defensive release** | A foreign caller's `unlockSeats()` is a no-op; the owner's confirm still works. |

## Controls

- **Movie** — switch the pricing tier; seat ₹ tags update live.
- **Scenario** — pick one of the 10. Switching auto-resets.
- **Speed** — 0.25× to 3×. Slow it down for dense scenarios (1, 2, 7).
- **▶ Play** / **↻ Reset** — start or wipe the simulation.

## Why a single HTML file?

Lives next to the Java source as documentation. Anyone can open it without
tooling, and stays in sync with the design rather than drifting.
