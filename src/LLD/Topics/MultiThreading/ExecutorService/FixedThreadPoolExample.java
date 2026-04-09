package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * Fixed thread pool (Executors.newFixedThreadPool(n))
 *
 * - Exactly n worker threads are created up front (or lazily, but the pool size is capped at n).
 * - Tasks are queued when all n threads are busy; extra tasks wait instead of spawning more threads.
 * - Up to n tasks can run in parallel — unlike newSingleThreadExecutor() (parallelism 1).
 *
 * Good when you want stable resource usage (predictable max threads) and can accept unbounded
 * queue growth for submitted work (default implementation uses a LinkedBlockingQueue with no cap).
 *
 * Demo: pool size 3, submit 6 tasks — you should see at most 3 different worker thread names
 * active at once; threads are reused for later tasks. (Exact interleaving is scheduler-dependent.)
 *
 * Lifecycle: shutdown + awaitTermination (Java 11+). Java 19+ can use try-with-resources on
 * ExecutorService instead.
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
