package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    ScheduledThreadPool                                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is ScheduledThreadPool?
 * ============================================================================
 *
 * Executors.newScheduledThreadPool(n) returns a ScheduledExecutorService:
 * a pool of N threads plus a built-in scheduler for delayed/periodic tasks.
 *
 * Key APIs:
 * - schedule(task, delay, unit)               -- run ONCE after delay
 * - scheduleAtFixedRate(task, init, period)    -- repeat every `period` (from start of each run)
 * - scheduleWithFixedDelay(task, init, delay)  -- repeat with `delay` after each run finishes
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   scheduleAtFixedRate(task, 1s initial, 2s period)
 *
 *   Time:  0s       1s       3s       5s       7s       9s
 *          │        │        │        │        │        │
 *          │  wait  │ RUN    │ RUN    │ RUN    │ RUN    │ ...
 *          │        ▼        ▼        ▼        ▼        ▼
 *          │     task()   task()   task()   task()   task()
 *          │     ◄──2s──► ◄──2s──► ◄──2s──► ◄──2s──►
 *          │
 *          ◄─1s─►  initial delay
 *
 *   scheduleWithFixedDelay(task, 1s initial, 2s delay)
 *
 *   Time:  0s       1s    1.5s     3.5s   4.0s     6.0s
 *          │        │      │        │      │        │
 *          │  wait  │ RUN  │  wait  │ RUN  │  wait  │ RUN ...
 *          │        ▼      │        ▼      │        ▼
 *          │     task()    │     task()    │     task()
 *          │     (500ms)   │     (500ms)   │     (500ms)
 *          │        ◄2s──►          ◄2s──►
 *          │               delay after      delay after
 *          │               run finishes     run finishes
 *
 * ============================================================================
 * 3. Practical uses (one-liners)
 * ============================================================================
 *
 * - Health check: probe an endpoint every 30 seconds.
 * - Cache eviction: sweep expired entries every 5 minutes.
 * - Retry logic: retry a failed HTTP call after 2-second delay.
 * - Metrics collection: push stats to monitoring service every 10 seconds.
 * - Session cleanup: purge expired sessions periodically.
 *
 * ============================================================================
 * 4. Code demo below
 * ============================================================================
 */
public class ScheduledThreadPoolExample {

    public static void main(String[] args) {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

        service.scheduleAtFixedRate(new ProbeTask(), 1000, 2000, TimeUnit.MILLISECONDS);

        try {
            if (!service.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

class ProbeTask implements Runnable {

    @Override
    public void run() {
        System.out.println("Probing end point for updates....");
    }
}
