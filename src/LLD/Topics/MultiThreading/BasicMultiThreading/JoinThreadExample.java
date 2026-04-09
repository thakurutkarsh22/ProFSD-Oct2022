package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * What is .join() operation in Java?
 *
 * Main thread as the parent thread
 * - When we start a program, execution usually begins with main(). That method runs on the main
 *   thread. You can think of it as the parent thread, since it is the one that starts the others.
 *
 * Independent execution of threads
 * - When you create and start other threads, they run concurrently with the main thread unless
 *   you say otherwise. By default no thread waits for another; they are independent.
 *
 * What is join()?
 * - Think of each thread as its own line of execution. When you call .join() on a thread, the
 *   thread that called join() (often main, but it can be any thread that wants to wait) is saying:
 *   finish your work first, then I continue. The caller blocks until that target thread completes.
 *
 *   (Rough timeline idea from "Multithreading for Beginners"):
 *
 *       (Main)   ---------------------------------------->
 *       (Other)  -------->
 *       (Target) ------------------>
 *
 * Perspective
 * - The name "join" is easy to misread at first. Names like waitForCompletion() or
 *   completeThenContinue() would describe the behavior more literally.
 */
public class JoinThreadExample {
    public static void main(String[] args) throws InterruptedException {
        Thread one = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("Thread 1: " + i);
            }
        });

        Thread two = new Thread(() -> {
            for (int i = 0; i < 25; i++) {
                System.out.println("Thread 2: " + i);
            }
        });

        one.start();
        two.start();

        // Main waits only for thread one; thread two may still be running when "Done" prints.
        one.join();

        System.out.println("Done executing the threads!");
    }
}

/*
 * Sample output (interleaved; thread 2 continues after "Done" because we only join thread one):
 *
 * Thread 2: 0
 * Thread 1: 0
 * Thread 2: 1
 * Thread 1: 1
 * Thread 2: 2
 * Thread 1: 2
 * Thread 2: 3
 * Thread 1: 3
 * Thread 2: 4
 * Thread 2: 5
 * Thread 2: 6
 * Thread 2: 7
 * Thread 2: 8
 * Thread 2: 9
 * Thread 2: 10
 * Thread 1: 4
 * Thread 2: 11
 * Thread 2: 12
 * Thread 2: 13
 * Thread 2: 14
 * Thread 2: 15
 * Thread 2: 16
 * Thread 2: 17
 * Done executing the threads!
 * Thread 2: 18
 * Thread 2: 19
 * Thread 2: 20
 * Thread 2: 21
 * Thread 2: 22
 * Thread 2: 23
 * Thread 2: 24
 */
