package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * Why Runnable is used
 *
 * Runnable is the unit of work that a Thread will run. You pass it to new Thread(runnable);
 * when you call start(), the JVM eventually invokes that object's run() on a new thread.
 *
 * 1) Separation of concerns — "what to run" (Runnable) is separate from thread machinery (Thread).
 *    ThreadOne / ThreadTwo are normal types that expose run(); they do not have to extend Thread.
 *
 * 2) Single inheritance — a class can extend only one superclass. If your class already extends
 *    something else, it cannot extend Thread but it can implement Runnable and still run on a thread.
 *
 * 3) Reuse across APIs — the same Runnable can be given to Thread now and to ExecutorService
 *    later without tying your code to Thread subclasses.
 *
 * 4) Anonymous classes / lambdas — Thread three uses an anonymous Runnable; with Java 8+ you
 *    could use a lambda because Runnable is a functional interface (single abstract method: run()).
 *
 * One line: Runnable = "the code the thread should execute"; Thread = "an OS thread that runs
 * a Runnable's run()." See also ExtendsThreadExample for Runnable vs extending Thread.
 */
public class RunnableThreadExample {
    public static void main(String[] args) {
        Thread one = new Thread(new ThreadOne());
        Thread two = new Thread(new ThreadTwo());

        Thread three = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    System.out.println("Thread three: " + i);
                }
            }
        });

        // to start the thread
        // Jvm might immediately run it or wait for the resource of the cpu to be available
        one.start();
        two.start();
        three.start();
    }
}


class ThreadOne implements Runnable {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread One: " + i);
        }
    }
}

class ThreadTwo implements Runnable {

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println("Thread Two: " + i);
        }
    }
}
