package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                   SingleThreadExecutor                                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is an ExecutorService?
 * ============================================================================
 *
 * Instead of new Thread(...).start() for every task, you use a managed pool:
 * - Thread reuse (no OS thread creation overhead per task).
 * - Queuing when workers are busy.
 * - Centralized shutdown instead of scattered Thread references.
 *
 * ============================================================================
 * 2. What is SingleThreadExecutor?
 * ============================================================================
 *
 * Executors.newSingleThreadExecutor() returns an ExecutorService backed by
 * exactly ONE worker thread. All submitted tasks run sequentially in the
 * order they were submitted.
 *
 * ============================================================================
 * 3. How it works -- diagram
 * ============================================================================
 *
 *   main thread                          SingleThreadExecutor
 *   ───────────                          ────────────────────
 *   execute(task0) ──►  ┌──────────────────────────────────────────────┐
 *   execute(task1) ──►  │  Unbounded Queue      Single Worker Thread  │
 *   execute(task2) ──►  │  ┌──────────────┐     ┌──────────────────┐  │
 *   execute(task3) ──►  │  │ T1 │ T2 │ T3 │ --> │  runs ONE task   │  │
 *   execute(task4) ──►  │  └──────────────┘     │  at a time       │  │
 *                       │                        │  T0 -> T1 -> T2  │  │
 *                       │                        │  -> T3 -> T4     │  │
 *                       │                        └──────────────────┘  │
 *                       └──────────────────────────────────────────────┘
 *
 *   Timeline (each task ~500ms):
 *     Time   0ms      500ms     1000ms    1500ms    2000ms    2500ms
 *            │─ T0 ──│── T1 ──│── T2 ──│── T3 ──│── T4 ──│ done
 *            strictly sequential, same thread reused for all
 *
 * ============================================================================
 * 4. Practical uses (one-liners)
 * ============================================================================
 *
 * - Event logging: guarantee log entries are written in order.
 * - Database writes: serialize writes to avoid concurrent modification.
 * - UI event dispatch: Swing/JavaFX uses a single-thread executor model.
 * - Background worker: one dedicated thread for sending emails, notifications, etc.
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
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
