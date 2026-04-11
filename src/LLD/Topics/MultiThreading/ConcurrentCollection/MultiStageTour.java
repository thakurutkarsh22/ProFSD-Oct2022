package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                          CyclicBarrier                                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is a CyclicBarrier?
 * ============================================================================
 *
 * A synchronization aid that allows a set of threads to all wait for each other
 * to reach a common barrier point. Once ALL threads arrive, the barrier "trips",
 * an optional barrier action runs, and all threads are released simultaneously.
 *
 * "Cyclic" because the barrier can be REUSED after all threads are released.
 * (Unlike CountDownLatch, which is one-shot.)
 *
 * Constructor:
 *   new CyclicBarrier(int parties)                   — just the count
 *   new CyclicBarrier(int parties, Runnable action)  — count + action to run when barrier trips
 *
 * Key method:
 *   barrier.await()  — "I've arrived. Block me until everyone else arrives too."
 *
 * ============================================================================
 * 2. How it works — step by step
 * ============================================================================
 *
 *   1. CyclicBarrier is created with parties = N (number of threads that must call await()).
 *   2. Each thread does its work for the current stage, then calls barrier.await().
 *   3. The barrier internally keeps a count. Each await() decrements it.
 *   4. Threads 1 through N-1 call await() → they BLOCK (parked).
 *   5. The N-th (last) thread calls await() → count hits 0 → barrier TRIPS:
 *      a. The optional barrier action runs (on the last arriving thread).
 *      b. All N threads are unblocked and resume execution.
 *   6. The barrier automatically RESETS its count back to N → ready for the next round.
 *
 * ============================================================================
 * 3. Diagram — Tour group with 5 tourists, 3 stages
 * ============================================================================
 *
 *   STAGE 1                          BARRIER                         STAGE 2
 *   ──────────────────────────────   ───────────────────────────     ──────────
 *
 *   Tourist 0 ───(exploring)───►  await()  ─┐
 *   Tourist 1 ───(exploring)───►  await()  ─┤
 *   Tourist 2 ───(exploring)───►  await()  ─┤  all 5 arrived?
 *   Tourist 3 ───(exploring)───►  await()  ─┤  NO → block
 *   Tourist 4 ───(exploring)───►  await()  ─┘  YES (5th arrives)
 *                                               │
 *                                   ┌───────────▼───────────┐
 *                                   │  Barrier action runs: │
 *                                   │  "Tour guide speaks"  │
 *                                   └───────────┬───────────┘
 *                                               │
 *                            ┌──────────────────┼──────────────────┐
 *                            ▼                  ▼                  ▼
 *                    Tourist 0,1,2          Tourist 3,4       (all released)
 *                    proceed to             proceed to         barrier RESETS
 *                    Stage 2                Stage 2            count → 5 again
 *
 *   ── Same thing repeats for Stage 2 → Stage 3 ──
 *
 * ============================================================================
 * 4. Timeline for this program
 * ============================================================================
 *
 *   Time     Tourist 0     Tourist 1     Tourist 2     Tourist 3     Tourist 4     Barrier
 *   ─────    ──────────    ──────────    ──────────    ──────────    ──────────    ────────
 *   0.0s     exploring     exploring     exploring     exploring     exploring     count=5
 *   ~1.0s    await()       await()       await()       await()       await()       count→0
 *            BLOCKED       BLOCKED       BLOCKED       BLOCKED       TRIPS!        ──────
 *                                                                    action runs   "Tour guide
 *                                                                                   speaks..."
 *            RELEASED      RELEASED      RELEASED      RELEASED      RELEASED      RESET→5
 *            ─── Stage 2 starts ────────────────────────────────────────────────
 *   ~2.0s    await()       await()       await()       await()       await()       count→0
 *            ... same pattern ... barrier trips again ... "Tour guide speaks..."
 *            ─── Stage 3 starts ────────────────────────────────────────────────
 *   ~3.0s    await()       await()       await()       await()       await()       count→0
 *            ... barrier trips ... "Tour guide speaks..." ... ALL DONE
 *
 * ============================================================================
 * 5. CyclicBarrier vs CountDownLatch
 * ============================================================================
 *
 *   Feature                CyclicBarrier                   CountDownLatch
 *   ───────────────────    ────────────────────────────    ─────────────────────────
 *   Reusable?              YES — resets after each trip    NO — one-shot only
 *   Who waits?             All participating threads       One or more waiting threads
 *   Who counts down?       Same threads that wait          Any thread (countDown())
 *   Barrier action?        Yes (runs when barrier trips)   No
 *   Use case               "All of us wait for each        "I wait for N events
 *                           other, then proceed together"   to happen, then I go"
 *
 *   CountDownLatch: waiter ≠ counter   (chefs count down, manager waits)
 *   CyclicBarrier: waiter = counter    (each tourist both waits AND counts)
 *
 * ============================================================================
 * 6. Practical uses (one-liners)
 * ============================================================================
 *
 * - Multi-phase computation: all threads complete phase 1 before starting phase 2 (e.g., matrix ops).
 * - Game simulation: all players finish their turn, then the next round begins.
 * - Parallel testing: run N test workers, wait for all to finish setup, then start tests together.
 * - Data pipeline: stage barrier ensures all mappers finish before reducers start.
 *
 * ============================================================================
 * 7. Key gotchas
 * ============================================================================
 *
 * - If a thread is interrupted or times out while waiting, the barrier becomes BROKEN
 *   (BrokenBarrierException) for all other waiting threads.
 * - barrier.reset() can manually break and reset a barrier.
 * - The barrier action runs on the LAST thread to arrive (not a separate thread).
 * - If parties = 1, the barrier trips immediately on every await() call.
 *
 * ============================================================================
 * 8. Code demo below — Multi-stage tour
 * ============================================================================
 * 5 tourists, 3 stages. At each stage, all tourists must arrive before the tour
 * guide speaks and the group moves on. The CyclicBarrier resets automatically
 * after each stage, making it perfect for multi-round synchronization.
 */
public class MultiStageTour {
    private static final int NUM_TOURISTS = 5;
    private static final int NUM_STAGES = 3;
    private static final CyclicBarrier barrier = new CyclicBarrier(NUM_TOURISTS, () -> {
        System.out.println("Tour guide starts speaking ...");
    });


    public static void main(String[] args) {

        // WAY 1:
//        ExecutorService service = Executors.newFixedThreadPool(NUM_TOURISTS);
//
//        for (int i = 0; i < NUM_TOURISTS; i++) {
//            service.execute(new Tourist(i));
//        }

        // way2:
        for (int i = 0; i < NUM_TOURISTS; i++) {
            Thread touristThread = new Thread(new Tourist(i));
            touristThread.start();
        }



    }

    static class Tourist implements Runnable {

        private final int touristId;

        public Tourist(int touristId) {
            this.touristId = touristId;
        }

        @Override
        public void run() {
            for (int i = 0; i < NUM_STAGES; i++) {
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    throw new RuntimeException(e);
                }

                System.out.println("Tourist: " + touristId + " arrived at stage " + (i+1) );
//                try {
//                    barrier.await();
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                } catch (BrokenBarrierException e) {
//                    throw new RuntimeException(e);
//                }
            }
        }
    }

}


/**
 * Tourist: 2 arrived at stage 1
 * Tourist: 0 arrived at stage 1
 * Tourist: 1 arrived at stage 1
 * Tourist: 3 arrived at stage 1
 * Tourist: 4 arrived at stage 1
 * Tour guide starts speaking ...
 * Tourist: 4 arrived at stage 2
 * Tourist: 3 arrived at stage 2
 * Tourist: 1 arrived at stage 2
 * Tourist: 2 arrived at stage 2
 * Tourist: 0 arrived at stage 2
 * Tour guide starts speaking ...
 * Tourist: 0 arrived at stage 3
 * Tourist: 1 arrived at stage 3
 * Tourist: 2 arrived at stage 3
 * Tourist: 4 arrived at stage 3
 * Tourist: 3 arrived at stage 3
 * Tour guide starts speaking ...
 */