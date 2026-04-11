package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                      CachedThreadPool                                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is CachedThreadPool?
 * ============================================================================
 *
 * Executors.newCachedThreadPool() creates threads on demand and reuses idle ones.
 * - No fixed pool size -- grows as needed, shrinks when idle (60s timeout).
 * - Uses SynchronousQueue: tasks are handed off directly to a thread (no buffering).
 * - If no idle thread is available, a NEW thread is created immediately.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   main thread                          CachedThreadPool
 *   ───────────                          ─────────────────
 *   execute(T0) ──►  ┌─────────────────────────────────────────────────┐
 *   execute(T1) ──►  │  SynchronousQueue          Dynamic Workers     │
 *   execute(T2) ──►  │  (handoff, no buffer)      ┌───────────────┐   │
 *   execute(T3) ──►  │        │                    │ W-1: runs T0  │   │
 *   ...              │        │                    │ W-2: runs T1  │   │
 *                     │        │                    │ W-3: runs T2  │   │
 *                     │        ▼                    │ W-4: runs T3  │   │
 *                     │  No idle thread?            │ ...new as     │   │
 *                     │  -> create new thread!      │ needed!       │   │
 *                     │                             └───────────────┘   │
 *                     │  Thread idle 60s? -> thread dies (pool shrinks) │
 *                     └─────────────────────────────────────────────────┘
 *
 *   Back-pressure problem:
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  100M tasks submitted --> 100M threads created --> OOM crash!   │
 *   │  No queue to absorb burst. Every task gets its own thread.      │
 *   │  Fix: use FixedThreadPool or set max pool size manually.        │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. Practical uses (one-liners)
 * ============================================================================
 *
 * - Short-lived async tasks: fire many quick HTTP calls, each gets a thread fast.
 * - Chat server: handle brief client messages where connections are short.
 * - Test harnesses: spin up many threads quickly without pre-sizing a pool.
 * - NOT for CPU-bound or long-running tasks (unbounded growth risk).
 *
 * ============================================================================
 * 4. Code demo below
 * ============================================================================
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
