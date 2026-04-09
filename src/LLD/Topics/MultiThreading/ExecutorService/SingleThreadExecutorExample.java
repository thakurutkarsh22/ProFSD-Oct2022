package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * Notes: Executors and ExecutorService
 *
 * Executors are how you run Runnable/Callable tasks without new Thread(...).start() for every job.
 * - Executor: "run this task sometime" (execute).
 * - ExecutorService: lifecycle too — submit, shutdown/shutdownNow, awaitTermination, etc.
 *
 * You usually get one from Executors factory methods, e.g.:
 * - newSingleThreadExecutor() — one worker thread; tasks queue and run strictly one-after-another.
 * - newFixedThreadPool(n) — n threads sharing a queue (parallelism up to n).
 * - newCachedThreadPool() — grows/shrinks with load (be careful in production).
 *
 * What executors do for you:
 * - Thread reuse (especially pools) instead of a new OS thread per tiny job.
 * - Queuing when workers are busy.
 * - Centralized shutdown instead of scattered Thread references.
 *
 * ---
 * Single-thread executor (demo below)
 *
 * Executors.newSingleThreadExecutor() returns an ExecutorService backed by exactly ONE worker thread.
 * All submitted tasks run on that thread, sequentially (default queue preserves submission order).
 *
 * Why use it:
 * - Strict ordering of work.
 * - Confine non-thread-safe state to one thread with minimal locking.
 * - Simple dedicated background worker without hand-rolling a queue + thread loop.
 *
 * Tradeoffs:
 * - No parallelism across those tasks; one slow/blocking task delays everything queued behind it.
 * - Call shutdown/close when done so the JVM can exit (worker is non-daemon by default).
 *
 * Contrast: newFixedThreadPool(n) can run up to n tasks at once; single-thread runs one at a time.
 *
 * Course / screenshot style (Java 19+):
 *   try (ExecutorService service = Executors.newSingleThreadExecutor()) {
 *       for (int i = 0; i < 5; i++) { service.execute(new Task(i)); }
 *   }
 * Below uses shutdown + awaitTermination so the same logic compiles on Java 11+ as well.
 */
public class SingleThreadExecutorExample {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService service = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < 5; i++) {
                service.execute(new Task(i));
            }
        } finally {
            service.shutdown();
            service.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}

class Task implements Runnable {

    private final int taskId;

    public Task(int taskId) {
        this.taskId = taskId;
    }

    @Override
    public void run() {
        System.out.println("Task with ID " + taskId + " being executed by Thread "
                + Thread.currentThread().getName());
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
