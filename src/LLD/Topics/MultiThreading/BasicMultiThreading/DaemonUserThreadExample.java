package LLD.Topics.MultiThreading.BasicMultiThreading;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                     Daemon vs User Threads                             ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What are daemon and user threads?
 * ============================================================================
 *
 * - User threads (non-daemon): normal application work. The JVM stays alive
 *   as long as ANY user thread is running.
 * - Daemon threads: background helpers. The JVM does NOT wait for daemon
 *   threads to finish -- they are killed automatically when all user threads end.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   ┌──────────── JVM ─────────────────────────────────────────────────┐
 *   │                                                                   │
 *   │  ┌─────────────────┐                                             │
 *   │  │  Main thread     │  (user thread, starts everything)          │
 *   │  │  starts bg + user│                                             │
 *   │  │  then exits      │                                             │
 *   │  └───────┬─────────┘                                             │
 *   │          │                                                        │
 *   │    ┌─────┴──────┐                                                │
 *   │    ▼            ▼                                                │
 *   │  ┌────────────────┐     ┌────────────────────┐                  │
 *   │  │ bgThread       │     │ userThread          │                  │
 *   │  │ (DAEMON)       │     │ (USER)              │                  │
 *   │  │ loops 500x     │     │ sleeps 5s, prints   │                  │
 *   │  │ sleep 1s each  │     │ then exits          │                  │
 *   │  └───────┬────────┘     └────────┬────────────┘                  │
 *   │          │                       │                                │
 *   │          │ ~1s: prints           │ sleeping...                   │
 *   │          │ ~2s: prints           │ sleeping...                   │
 *   │          │ ~3s: prints           │ sleeping...                   │
 *   │          │ ~4s: prints           │ sleeping...                   │
 *   │          │ ~5s: prints           ▼ "User thread done"           │
 *   │          │                       (user thread exits)             │
 *   │          │                                                        │
 *   │          ▼ KILLED by JVM ← no more user threads alive!          │
 *   │                                                                   │
 *   └─────────── JVM EXITS ────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. Practical uses (one-liners)
 * ============================================================================
 *
 * - Garbage collector (JVM's own daemon thread).
 * - Background log flusher that should stop when the app stops.
 * - Heartbeat/keep-alive sender that should not prevent JVM shutdown.
 * - IDE auto-save thread that runs until the editor closes.
 *
 * ============================================================================
 * 4. Code demo below
 * ============================================================================
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

// because the user thread is terminated hence the daemon thread will be terminated
