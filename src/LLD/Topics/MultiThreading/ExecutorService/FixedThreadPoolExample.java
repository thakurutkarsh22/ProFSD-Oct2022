package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                       FixedThreadPool                                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is FixedThreadPool?
 * ============================================================================
 *
 * Executors.newFixedThreadPool(n) creates a pool of exactly N worker threads.
 * Tasks are queued in an unbounded LinkedBlockingQueue. At most N tasks run
 * in parallel; the rest wait in the queue.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   main thread                          FixedThreadPool (n=3)
 *   ───────────                          ─────────────────────
 *   execute(T0) ──►  ┌────────────────────────────────────────────────────┐
 *   execute(T1) ──►  │  Unbounded Queue        3 Worker Threads          │
 *   execute(T2) ──►  │  ┌──────────┐          ┌──────────────────┐      │
 *   execute(T3) ──►  │  │ T3 │ T4  │ -------> │ Worker-1: runs T0│      │
 *   execute(T4) ──►  │  │ T5 │     │          │ Worker-2: runs T1│      │
 *   execute(T5) ──►  │  └──────────┘          │ Worker-3: runs T2│      │
 *                     │                         └──────────────────┘      │
 *                     │  When T0 finishes, Worker-1 picks T3 from queue  │
 *                     └────────────────────────────────────────────────────┘
 *
 *   Timeline (6 tasks, 3 threads, each ~500ms):
 *     Time   0ms          500ms        1000ms
 *     W-1    │─── T0 ────│─── T3 ────│ done
 *     W-2    │─── T1 ────│─── T4 ────│ done
 *     W-3    │─── T2 ────│─── T5 ────│ done
 *            ◄── batch 1 ──►◄── batch 2 ──►
 *
 * ============================================================================
 * 3. Practical uses (one-liners)
 * ============================================================================
 *
 * - Web server: fixed pool of N threads handling incoming HTTP requests.
 * - Batch processing: process N files in parallel, bounded resource usage.
 * - Database connection pool: match thread pool size to DB connection limit.
 * - Image/video processing: N parallel encoders, one per CPU core.
 *
 * ============================================================================
 * 4. Code demo below
 * ============================================================================
 */
public class FixedThreadPoolExample {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(3);
        try {
            for (int i = 0; i < 6; i++) {
                service.execute(new FixedPoolTask(i));
            }
        } finally {
            service.shutdown();
            service.awaitTermination(60, TimeUnit.SECONDS);
        }
    }
}

/**
 * Separate name from {@code Task} in SingleThreadExecutorExample (same package would duplicate class).
 */
class FixedPoolTask implements Runnable {

    private final int taskId;

    FixedPoolTask(int taskId) {
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

/**
 * Task with ID 2 being executed by Thread pool-1-thread-3
 * Task with ID 0 being executed by Thread pool-1-thread-1
 * Task with ID 1 being executed by Thread pool-1-thread-2
 * Task with ID 3 being executed by Thread pool-1-thread-1
 * Task with ID 4 being executed by Thread pool-1-thread-3
 * Task with ID 5 being executed by Thread pool-1-thread-2
 *
 * Process finished with exit code 0
 */
