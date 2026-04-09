package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * Daemon and user threads
 *
 * By the role they play in the JVM, threads fall into two kinds:
 * - User threads (non-daemon): normal application work.
 * - Daemon threads: background helpers; low priority in intent (not the same as Thread priority API).
 *
 * Main thread
 * - When the JVM starts your program, the thread that runs main() starts right away. You can start
 *   child threads from it. In typical runs the main thread is among the last to finish because it
 *   drives startup/teardown, though any user thread can keep the JVM alive.
 *
 * Daemon threads
 * - Meant for background work (example from the platform: the garbage collector thread). The JVM
 *   does not keep the process alive just because daemon threads are still running.
 *
 * Termination
 * - When all user (non-daemon) threads have finished, the JVM shuts down. Any remaining daemon
 *   threads are stopped by the JVM—they do not need to exit cleanly on their own.
 *
 * Summary
 * - User threads run until their run() completes (or the process exits). Daemon threads are cut
 *   off once no user threads are left, so they should not hold resources that require a guaranteed
 *   flush or close unless you coordinate with user threads.
 *
 * This demo: bgThread is daemon (setDaemon(true)); userThread is non-daemon. The user thread
 * sleeps briefly then ends; the daemon loop may stop mid-way when the JVM exits after main and the
 * user thread finish (behavior can vary by timing).
 */
public class DaemonUserThreadExample {
    public static void main(String[] args) {
        Thread bgThread = new Thread(new DaemonHelper());
        Thread userThread = new Thread(new UserThreadHelper());

        bgThread.setDaemon(true);
        bgThread.start();
        userThread.start();
    }
}

class DaemonHelper implements Runnable {

    @Override
    public void run() {
        int count = 0;
        while (count < 500) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            count++;
            System.out.println("Daemon helper running ... ");
        }
    }
}

class UserThreadHelper implements Runnable {

    @Override
    public void run() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("User thread done execution ");
    }
}

/**
 * Daemon helper running ...
 * Daemon helper running ...
 * Daemon helper running ...
 * Daemon helper running ...
 * User thread done execution
 */

// because the user thread is terminated hence the deamon thread will be terminated