package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                  CPU-Intensive Task (Ideal Pool Size)                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is the ideal pool size for CPU-bound work?
 * ============================================================================
 *
 * For pure CPU-bound tasks (no I/O, no waiting), the ideal thread pool size
 * is approximately equal to the number of available CPU cores:
 *
 *   int cores = Runtime.getRuntime().availableProcessors();
 *   ExecutorService pool = Executors.newFixedThreadPool(cores);
 *
 * ============================================================================
 * 2. Why? -- diagram
 * ============================================================================
 *
 *   Available cores: 4
 *
 *   Pool size = 4 (optimal):
 *   ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
 *   │ Core 0 │  │ Core 1 │  │ Core 2 │  │ Core 3 │
 *   │ Task A │  │ Task B │  │ Task C │  │ Task D │
 *   └────────┘  └────────┘  └────────┘  └────────┘
 *   100% CPU utilization, minimal context switching
 *
 *   Pool size = 8 (too many):
 *   ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
 *   │ Core 0 │  │ Core 1 │  │ Core 2 │  │ Core 3 │
 *   │ T-A/T-E│  │ T-B/T-F│  │ T-C/T-G│  │ T-D/T-H│
 *   │ switch!│  │ switch!│  │ switch!│  │ switch!│
 *   └────────┘  └────────┘  └────────┘  └────────┘
 *   Same CPU, but now wasting time on context switches
 *
 *   Pool size = 1 (too few):
 *   ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
 *   │ Core 0 │  │ Core 1 │  │ Core 2 │  │ Core 3 │
 *   │ Task A │  │ IDLE   │  │ IDLE   │  │ IDLE   │
 *   └────────┘  └────────┘  └────────┘  └────────┘
 *   75% of CPUs wasted
 *
 *   Rule of thumb:
 *   ┌────────────────────────────────────────────────┐
 *   │  CPU-bound tasks:  threads = cores              │
 *   │  I/O-bound tasks:  threads = cores * (1 + W/C)  │
 *   │    where W = wait time, C = compute time        │
 *   └────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. Practical uses (one-liners)
 * ============================================================================
 *
 * - Image processing: resize N images in parallel, one per core.
 * - Scientific computation: matrix multiplication, simulations.
 * - Data compression: parallel compression of file chunks.
 * - Cryptographic hashing: hash passwords using all available cores.
 *
 * ============================================================================
 * 4. Code demo below
 * ============================================================================
 */
public class CpuIntensiveTaskExample {

    public static void main(String[] args) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();

        ExecutorService service = Executors.newFixedThreadPool(cores);

        System.out.println("Created thread pool with : " + cores + " cores");

        try {
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
 * ...
 *
 * Process finished with exit code 0
 */
