package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * Scheduled thread pool (Executors.newScheduledThreadPool(n))
 *
 * What it is:
 * - Returns a ScheduledExecutorService: a pool of threads plus a scheduler.
 * - You can run tasks after a delay, or repeatedly on a fixed period or fixed delay.
 *
 * Main APIs (all times use a TimeUnit):
 * - schedule(Runnable, delay, unit) — run once after delay.
 * - scheduleAtFixedRate(Runnable, initialDelay, period, unit) — first run after initialDelay, then
 *   every `period` (measured from the *start* of each run; if a run takes longer than period,
 *   the next run may start late — "catch-up" behavior is implementation-dependent).
 * - scheduleWithFixedDelay(Runnable, initialDelay, delay, unit) — first run after initialDelay,
 *   then each next run starts `delay` after the *previous run finished*.
 *
 * Why it is used:
 * - Heartbeats, polling, cache eviction ticks, retries, cron-like simple repetition without raw
 *   Thread.sleep loops or Timer (Timer is single-threaded and less flexible).
 * - Keeps threading and scheduling in one place with shutdown/awaitTermination.
 *
 * Cautions:
 * - scheduleAtFixedRate can pile up if tasks run longer than the period; consider fixed delay or
 *   a custom solution if runs can overlap badly.
 * - Cancel ScheduledFuture or shutdown the service when repetition should stop.
 * - For complex calendars (cron expressions), use a dedicated scheduler library or Quartz.
 *
 * Demo below matches the course snippet: one thread, ProbeTask every 2s after 1s initial delay,
 * then awaitTermination(5s): without a prior shutdown() the pool is still running, so the wait
 * times out and shutdownNow() forces stop (as in the video). For graceful shutdown in production,
 * call shutdown() first, then awaitTermination, then shutdownNow() only if needed.
 */
public class ScheduledThreadPoolExample {

    public static void main(String[] args) {
        // Creates a thread pool that can schedule commands to run after a given delay,
        // or to execute periodically.
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

        // Schedules a task to run at a fixed rate.
        // Parameters: task, initialDelay (1000ms), period (2000ms), unit
        service.scheduleAtFixedRate(new ProbeTask(), 1000, 2000, TimeUnit.MILLISECONDS);

        try {
            // Blocks until all tasks have completed execution after a shutdown request,
            // or the timeout occurs, or the current thread is interrupted.
            if (!service.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            // Force shutdown if the waiting thread is interrupted
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
