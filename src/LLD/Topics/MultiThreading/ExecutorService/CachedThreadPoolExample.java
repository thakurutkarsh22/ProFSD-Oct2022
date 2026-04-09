package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * Cached thread pool (Executors.newCachedThreadPool())
 *
 * What it does:
 * - Creates new threads as needed when there is no idle worker to take a task.
 * - Reuses existing idle threads when work arrives (threads are recycled).
 * - Idle threads are typically retired after ~60 seconds of no use (implementation detail).
 * - Uses a handoff-style queue (SynchronousQueue): tasks are usually passed directly to a thread
 *   or a new thread is started, rather than sitting in a long unbounded FIFO behind a fixed cap.
 *
 * Why it is used:
 * - Good for lots of short, independent tasks where you do not want a fixed cap on parallelism
 *   and bursts of work should start quickly without a large pre-allocated pool.
 * - Simplifies "fire many async jobs" demos when each job is short-lived.
 *
 * Cautions (important in production):
 * - Under heavy or sustained load, the pool can grow to many threads (memory + context switching).
 *   Prefer newFixedThreadPool(n), a configured ThreadPoolExecutor, or virtual threads (Java 21+)
 *   when you need a hard upper bound.
 * - Do not use it for CPU-bound overload without back-pressure; combine with limits or queues.
 *
 * Demo: submit several tasks; you may see many pool-* thread names for concurrent work, and reuse
 * as tasks finish. Interleaving depends on the scheduler.
 *
 * Lifecycle: shutdown + awaitTermination (Java 11+).
 */
public class CachedThreadPoolExample {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService service = Executors.newCachedThreadPool();
        try {
            for (int i = 0; i < 100000000; i++) {
                service.execute(new CachedPoolTask(i));
            }
        } finally {
            service.shutdown();
            service.awaitTermination(60, TimeUnit.SECONDS);
        }
    }
}

/** Avoids name clash with {@code Task} / {@code FixedPoolTask} in this package. */
class CachedPoolTask implements Runnable {

    private final int taskId;

    CachedPoolTask(int taskId) {
        this.taskId = taskId;
    }

    @Override
    public void run() {
        System.out.println("Task with ID " + taskId + " being executed by Thread "
                + Thread.currentThread().getName());
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
