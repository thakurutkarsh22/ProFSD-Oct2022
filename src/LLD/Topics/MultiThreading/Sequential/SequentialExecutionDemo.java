package LLD.Topics.MultiThreading.Sequential;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                      Sequential Execution                              ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is sequential execution?
 * ============================================================================
 *
 * By default, a Java program has exactly ONE thread -- the main thread.
 * Every statement runs one after another, top to bottom. demo1() must finish
 * completely before demo2() starts. There is no overlap.
 *
 * ============================================================================
 * 2. Diagram
 * ============================================================================
 *
 *   main thread
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │  main()                                                        │
 *   │  ┌──────────────┐   ┌──────────────┐                          │
 *   │  │   demo1()    │-->│   demo2()    │--> program ends           │
 *   │  │  prints 0-4  │   │  prints 0-4  │                          │
 *   │  └──────────────┘   └──────────────┘                          │
 *   │                                                                │
 *   │  ◄── demo1 runs ──►◄── demo2 runs ──►                        │
 *   │       no overlap, strictly one after another                   │
 *   └────────────────────────────────────────────────────────────────┘
 *
 *   Timeline:
 *     Time   0ms          ~Xms                   ~2Xms
 *            │── demo1() ──│──── demo2() ────────│ done
 *
 * ============================================================================
 * 3. Why this matters
 * ============================================================================
 *
 * If demo1() takes 5 seconds and demo2() takes 5 seconds, total = 10 seconds.
 * With threads, they could run in parallel and finish in ~5 seconds.
 * This file is the "before" -- showing the problem that multithreading solves.
 *
 * ============================================================================
 * 4. Practical uses of sequential execution
 * ============================================================================
 *
 * - Simple scripts, batch jobs, CLI tools where order matters and concurrency adds no value.
 * - Initialization/startup routines that must run in a fixed order (DB connect -> schema migrate -> start server).
 * - Test setup/teardown that must be deterministic.
 *
 * ============================================================================
 * 5. Code demo below
 * ============================================================================
 */
public class SequentialExecutionDemo {
    public static void main(String[] args) {
        demo1();
        demo2();
    }

    private static void demo1() {
        for (int i = 0; i < 5; i++) {
            System.out.println("From demo 1 " + i);
        }
    }

    private static void demo2() {
        for (int i = 0; i < 5; i++) {
            System.out.println("From demo 2 " + i);
        }
    }
}
