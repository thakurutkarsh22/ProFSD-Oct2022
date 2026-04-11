package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     Runnable vs Callable                               ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. Runnable (since Java 1.0)
 * ============================================================================
 *
 *   @FunctionalInterface
 *   public interface Runnable {
 *       void run();      // no return value, no checked exceptions
 *   }
 *
 * - Returns nothing (void).
 * - Cannot throw checked exceptions — must handle them inside run().
 * - Works with: new Thread(runnable), executor.execute(runnable), executor.submit(runnable).
 *
 *   Example:
 *     Runnable task = () -> System.out.println("fire and forget");
 *     new Thread(task).start();
 *
 * ============================================================================
 * 2. Callable<V> (since Java 1.5)
 * ============================================================================
 *
 *   @FunctionalInterface
 *   public interface Callable<V> {
 *       V call() throws Exception;   // returns a value, can throw checked exceptions
 *   }
 *
 * - Returns a value of type V.
 * - CAN throw checked exceptions — the caller handles them via Future.get().
 * - Works ONLY with: executor.submit(callable) — cannot pass to new Thread().
 *
 *   Example:
 *     Callable<Integer> task = () -> { return 42; };
 *     Future<Integer> future = executor.submit(task);
 *     int result = future.get();   // blocks until result is ready → 42
 *
 * ============================================================================
 * 3. Side-by-side comparison
 * ============================================================================
 *
 *   Feature               Runnable                    Callable<V>
 *   ───────────────────   ─────────────────────────   ──────────────────────────
 *   Return type           void                        V (any type)
 *   Checked exceptions    Must catch inside run()     Can throw from call()
 *   Result access         None                        Via Future<V>.get()
 *   Use with Thread       Yes                         No
 *   Use with Executor     execute() or submit()       submit() only
 *   Introduced in         Java 1.0                    Java 1.5
 *
 * ============================================================================
 * 4. Rule of thumb
 * ============================================================================
 * Use Runnable for fire-and-forget tasks.
 * Use Callable when you need a RESULT back or need to propagate EXCEPTIONS to the caller.
 *
 * ============================================================================
 * 5. How Callable + Future works -- diagram
 * ============================================================================
 *
 *   Main thread                          ExecutorService
 *   ───────────                          ─────────────────
 *   Future<Integer> f =                  ┌─────────────────────────┐
 *     executor.submit(callable) ──────►  │  Worker thread           │
 *   │                                    │  call() { sleep; return }│
 *   │  f.get()  <-- BLOCKED             │  computes result...      │
 *   │  waiting for result...             │  returns 10              │
 *   │                                    └──────────┬──────────────┘
 *   │  ◄── result = 10 ─────────────────────────────┘
 *   │  UNBLOCKED
 *   ▼  prints result
 *
 * ============================================================================
 * 6. Practical uses (one-liners)
 * ============================================================================
 *
 * - Parallel API calls: submit 5 HTTP requests as Callables, collect all Future results.
 * - Database queries: run multiple queries concurrently, get results via Future.get().
 * - Price comparison: fetch prices from multiple vendors in parallel, pick the best.
 * - File processing: compute checksums of multiple files concurrently, return hashes.
 *
 * ============================================================================
 * 7. Code demo below
 * ============================================================================
 */
public class CallableExample {

    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> future = executorService.submit(new ReturnValueTask());
            System.out.println("Main thread execution completed before get");

//            Integer result = future.get();
//            System.out.println("Callable returned: " + result);

            future.cancel(true);
            System.out.println(future.isDone() +  " : is done");

            System.out.println("Main thread execution completed");



        } finally {
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}

class ReturnValueTask implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        Thread.sleep(5000);
        return 10;
    }
}
