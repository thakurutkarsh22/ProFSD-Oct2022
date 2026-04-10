package LLD.Topics.MultiThreading.BasicMultiThreading;

public class ExtendsThreadExample {
    public static void main(String[] args) {
        Thread one = new Thread1();
        Thread two = new Thread2();

        one.start();
        two.start();
        System.out.println("Done executing the threads!");
//        System.out.println(Thread.());

        for (int i = 0; i < 1000; i++) {
            if(i == 999) {
                System.out.println("999");
            }

        }
    }
}

class Thread1 extends Thread {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread one: " + i);
        }
    }
}

class Thread2 extends Thread {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread two: " + i);
        }
    }
}

/**
 * Done executing the threads!
 * Thread one: 0
 * Thread one: 1
 * Thread two: 0
 * Thread one: 2
 * Thread two: 1
 * Thread two: 2
 * Thread two: 3
 * Thread one: 3
 * Thread one: 4
 * Thread two: 4
 */

/*
    Runnable vs extending Thread

    Extends Thread (this file):
    - Your class IS-A Thread; you call start() on it directly.
    - You burn Java's single inheritance: Thread1 cannot extend another class.
    - Tighter coupling to Thread API; less natural if the work is just "a task".

    Implements Runnable (see RunnableThreadExample):
    - Your class IS-A Runnable (a task); you pass it to new Thread(runnable) then start().
    - Class can extend another superclass and still run on a thread.
    - Same task instance can be submitted to an ExecutorService or wrapped in multiple Threads
      (usually not recommended for one Runnable, but the model is flexible).
    - Prefer Runnable (or Callable) for most production code; reserve extending Thread for demos.

    Both approaches: override run() with the work; the JVM calls run() on a new thread after start().
    Never call run() directly if you want parallel execution—use start().
 */
