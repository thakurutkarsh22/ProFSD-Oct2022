package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * Ideal pool size for CPU-bound work (course: "What's the ideal pool size?")
 *
 * For tasks that mostly use the CPU (compute) with little blocking I/O, using about as many
 * threads as hardware threads (cores / availableProcessors()) avoids pointless oversubscription:
 * too many runnable CPU-bound threads → more context switching and cache thrashing with little
 * extra throughput.
 *
 * This is a rule of thumb, not a law: hyper-threading, mixed workloads, GC, and blocking calls
 * change the answer. For I/O-heavy work you often want more threads; for Java 21+ virtual threads
 * are another model for massive concurrency without mapping 1:1 to OS threads.
 *
 * Video snippet: fixed pool of size availableProcessors(), submit many CpuTask runnables.
 * shutdown + awaitTermination added so main exits cleanly (course code often omits this).
 */
public class CpuIntensiveTaskExample {

    public static void main(String[] args) throws InterruptedException {
        // Get the number of available CPU cores
        int cores = Runtime.getRuntime().availableProcessors();

        // Create a fixed thread pool with a size equal to the number of cores.
        // For CPU-intensive tasks, the ideal pool size is typically the number of available cores.
        ExecutorService service = Executors.newFixedThreadPool(cores);

        System.out.println("Created thread pool with : " + cores + " cores");

        try {
            // Submit 20 tasks to the executor service
            for (int i = 0; i < 20; i++) {
                service.execute(new CpuTask());
            }
        } finally {
            service.shutdown();
            service.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}

class CpuTask implements Runnable {

    @Override
    public void run() {
        // Simulate a CPU-intensive task by printing the current thread's name
        System.out.println("Some CPU intensive task being done by : " + Thread.currentThread().getName());
    }
}


/**
 * Created thread pool with : 14 cores
 * Some CPU intensive task being done by : pool-1-thread-1
 * Some CPU intensive task being done by : pool-1-thread-3
 * Some CPU intensive task being done by : pool-1-thread-2
 * Some CPU intensive task being done by : pool-1-thread-4
 * Some CPU intensive task being done by : pool-1-thread-5
 * Some CPU intensive task being done by : pool-1-thread-6
 * Some CPU intensive task being done by : pool-1-thread-7
 * Some CPU intensive task being done by : pool-1-thread-8
 * Some CPU intensive task being done by : pool-1-thread-9
 * Some CPU intensive task being done by : pool-1-thread-10
 * Some CPU intensive task being done by : pool-1-thread-11
 * Some CPU intensive task being done by : pool-1-thread-12
 * Some CPU intensive task being done by : pool-1-thread-13
 * Some CPU intensive task being done by : pool-1-thread-14
 * Some CPU intensive task being done by : pool-1-thread-14
 * Some CPU intensive task being done by : pool-1-thread-14
 * Some CPU intensive task being done by : pool-1-thread-14
 * Some CPU intensive task being done by : pool-1-thread-14
 * Some CPU intensive task being done by : pool-1-thread-14
 * Some CPU intensive task being done by : pool-1-thread-14
 *
 * Process finished with exit code 0
 */