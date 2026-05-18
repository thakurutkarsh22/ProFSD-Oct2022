package LLD.LldQuestions.TicketBooking.lock;

import LLD.LldQuestions.TicketBooking.models.SeatLock;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/*
 * ╔════════════════════════════════════════════════════════════════════════╗
 * ║                  InMemorySeatLockProvider                              ║
 * ╚════════════════════════════════════════════════════════════════════════╝
 *
 * Concurrency model
 * ─────────────────
 * Two storage layers, both keyed by showId for shard-style isolation:
 *
 *   showLocks      : showId -> ReentrantLock(fair)
 *                    A *single* lock per show, held only briefly while
 *                    inspecting / mutating that show's seat-lock map.
 *                    Different shows therefore never block each other.
 *
 *   showSeatLocks  : showId -> (seatId -> SeatLock)
 *                    The actual hold table. Mutations to the inner map
 *                    are always done under the corresponding showLock,
 *                    so a plain HashMap is safe (no need for
 *                    ConcurrentHashMap semantics on the inner map).
 *
 * Why a per-show lock instead of one global lock?
 *   - Booking traffic for "Inception 7pm at PVR Forum" is independent of
 *     "Dune 9pm at INOX Garuda". A global lock would serialize every booking
 *     in the entire system; a per-show lock only serializes contention on
 *     the same show, which is the actual hot spot.
 *
 * Why a *fair* ReentrantLock?
 *   - Real users perceive "first to click, first to get the seat" as fair.
 *     A non-fair lock allows late arrivals to barge ahead of patient ones,
 *     which on a popular release looks like starvation.
 *   - Cost: ~10-15% throughput reduction vs non-fair. Acceptable here because
 *     the critical section is microseconds and the dominant cost is payment.
 *
 * Why ALL-OR-NOTHING acquire?
 *   - A user picks a *party* of seats (e.g., A1, A2, A3). Holding 2 of 3
 *     and failing the third is worse than holding 0 -- the user can't watch
 *     the movie split apart, and the 2 partial holds block other parties.
 *   - We do a "check phase, then commit phase" inside one lock acquisition,
 *     guaranteeing the whole party either succeeds or leaves zero footprint.
 *
 * TTL & cleanup
 *   - Every SeatLock carries an absolute expiresAt instant.
 *   - Lazy purge: every read of a lock first checks isExpired(); expired
 *     locks are treated as absent. This keeps the hot path correct even if
 *     the cleanup thread is delayed.
 *   - Background sweep: a ScheduledExecutorService removes expired entries
 *     so the map doesn't grow without bound for cold shows.
 */
public class InMemorySeatLockProvider implements SeatLockProvider {

    private final long lockTimeoutSeconds;

    /*
     * ─────────────────────────── Worked example ──────────────────────────────
     *
     * Walk through three actions with TTL = 2s; current time references shown.
     *
     *   t=12:00:00  u_alice  lockSeats("show-1", [A1, A2])
     *   t=12:00:01  u_bob    lockSeats("show-1", [B1, B2])
     *   t=12:00:02  u_charlie lockSeats("show-9", [C5])
     *
     * State of the two maps after all three calls:
     *
     *   showLocks (one fair ReentrantLock per show; never thrown away):
     *   ┌─────────┬──────────────────┐
     *   │ "show-1"│ ReentrantLock@1A │   ← shared by Alice + Bob (same show
     *   │         │                  │     serializes them)
     *   ├─────────┼──────────────────┤
     *   │ "show-9"│ ReentrantLock@2B │   ← Charlie's; INDEPENDENT lock, so
     *   └─────────┴──────────────────┘     Charlie never waits on Alice/Bob.
     *
     *   showSeatLocks  (showId -> seatId -> SeatLock):
     *   {
     *     "show-1" -> {
     *        "A1" -> SeatLock(owner=u_alice, expires=12:00:02),
     *        "A2" -> SeatLock(owner=u_alice, expires=12:00:02),
     *        "B1" -> SeatLock(owner=u_bob,   expires=12:00:03),
     *        "B2" -> SeatLock(owner=u_bob,   expires=12:00:03)
     *     },
     *     "show-9" -> {
     *        "C5" -> SeatLock(owner=u_charlie, expires=12:00:04)
     *     }
     *   }
     *
     * What happens at t=12:00:05?
     *   - validateLock(...) for ANY of the above returns false (TTL elapsed),
     *     because isExpired() is checked on every read (line ~150).
     *   - The background cleanup tick empties the inner maps:
     *       showSeatLocks  ->  { "show-1" -> {}, "show-9" -> {} }
     *     but showLocks is intentionally NOT cleared -- the ReentrantLock
     *     instances must persist so two threads racing on a returning show
     *     keep getting the SAME mutex (otherwise mutual exclusion breaks).
     *
     * Why outer = ConcurrentHashMap but inner = plain HashMap?
     *   - Outer: different shows race to `computeIfAbsent`; needs CHM atomics.
     *   - Inner: only ever touched while holding the per-show ReentrantLock,
     *     so it's already single-threaded by construction -- plain HashMap is
     *     correct and cheaper.
     */
    private final Map<String, ReentrantLock> showLocks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, SeatLock>> showSeatLocks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupExecutor;

    public InMemorySeatLockProvider(long lockTimeoutSeconds, long cleanupIntervalSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "seat-lock-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(
                this::purgeExpiredLocks,
                cleanupIntervalSeconds,
                cleanupIntervalSeconds,
                TimeUnit.SECONDS);
    }

    @Override
    public boolean lockSeats(String showId, List<String> seatIds, String userId) {
        ReentrantLock showLock = lockFor(showId);
        showLock.lock();
        try {
            Map<String, SeatLock> seatLockMap =
                    showSeatLocks.computeIfAbsent(showId, k -> new HashMap<>());

            // ── PHASE 1: validate the whole party can be locked ───────────────
            // We must NOT mutate state while validating: if seat #3 conflicts
            // we cannot leave seats #1 and #2 holding stale locks. Hence the
            // "check everything first, then commit" pattern.
            for (String seatId : seatIds) {
                SeatLock existing = seatLockMap.get(seatId);
                if (existing == null) continue;
                if (existing.isExpired()) continue;            // stale: treat as absent
                if (existing.isOwnedBy(userId)) continue;      // re-entry by same user is fine
                return false;                                  // someone else holds it
            }

            // ── PHASE 2: commit ───────────────────────────────────────────────
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(lockTimeoutSeconds);
            for (String seatId : seatIds) {
                seatLockMap.put(seatId, new SeatLock(seatId, showId, userId, now, expiresAt));
            }
            return true;
        } finally {
            showLock.unlock();
        }
    }

    @Override
    public void unlockSeats(String showId, List<String> seatIds, String userId) {
        ReentrantLock showLock = lockFor(showId);
        showLock.lock();
        try {
            Map<String, SeatLock> seatLockMap = showSeatLocks.get(showId);
            if (seatLockMap == null) return;

            // Only release locks the caller actually owns. Defends against
            // a buggy/malicious caller releasing someone else's hold.
            for (String seatId : seatIds) {
                SeatLock existing = seatLockMap.get(seatId);
                if (existing != null && existing.isOwnedBy(userId)) {
                    seatLockMap.remove(seatId);
                }
            }
        } finally {
            showLock.unlock();
        }
    }

    @Override
    public boolean validateLock(String showId, List<String> seatIds, String userId) {
        ReentrantLock showLock = lockFor(showId);
        showLock.lock();
        try {
            Map<String, SeatLock> seatLockMap = showSeatLocks.get(showId);
            if (seatLockMap == null) return false;

            for (String seatId : seatIds) {
                SeatLock existing = seatLockMap.get(seatId);
                if (existing == null) return false;
                if (existing.isExpired()) return false;
                if (!existing.isOwnedBy(userId)) return false;
            }
            return true;
        } finally {
            showLock.unlock();
        }
    }

    @Override
    public SeatLock getSeatLock(String showId, String seatId) {
        ReentrantLock showLock = lockFor(showId);
        showLock.lock();
        try {
            Map<String, SeatLock> seatLockMap = showSeatLocks.get(showId);
            if (seatLockMap == null) return null;
            SeatLock lock = seatLockMap.get(seatId);
            if (lock == null || lock.isExpired()) return null;
            return lock;
        } finally {
            showLock.unlock();
        }
    }

    public void shutdown() {
        cleanupExecutor.shutdownNow();
    }

    private ReentrantLock lockFor(String showId) {
        // computeIfAbsent is atomic on ConcurrentHashMap, so two threads racing
        // on a brand-new showId still get the SAME ReentrantLock instance.
        return showLocks.computeIfAbsent(showId, k -> new ReentrantLock(true));
    }

    private void purgeExpiredLocks() {
        for (Map.Entry<String, Map<String, SeatLock>> showEntry : showSeatLocks.entrySet()) {
            ReentrantLock showLock = lockFor(showEntry.getKey());
            // tryLock to avoid blocking the cleanup thread on a hot show.
            if (!showLock.tryLock()) continue;
            try {
                showEntry.getValue().entrySet().removeIf(e -> e.getValue().isExpired());
            } finally {
                showLock.unlock();
            }
        }
    }
}
