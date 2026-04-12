package LLD.Topics.MultiThreading.OtherCOncepts;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                            Deadlocks                                   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is a Deadlock?
 * ============================================================================
 *
 * A deadlock is a situation where two or more threads are BLOCKED FOREVER,
 * each waiting for a lock that the other thread holds. No thread can proceed
 * because each is waiting for the other to release first.
 *
 *   Thread 1: "I have Lock A, I need Lock B to continue..."
 *   Thread 2: "I have Lock B, I need Lock A to continue..."
 *   Both: waiting forever. Program is stuck. DEADLOCK.
 *
 * ============================================================================
 * 2. Why does a Deadlock happen?
 * ============================================================================
 *
 * A deadlock occurs when ALL FOUR of these conditions are true simultaneously
 * (Coffman conditions):
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  1. MUTUAL EXCLUSION                                            │
 *   │     Only one thread can hold a lock at a time.                   │
 *   │                                                                  │
 *   │  2. HOLD AND WAIT                                                │
 *   │     A thread holds one lock while waiting for another.           │
 *   │                                                                  │
 *   │  3. NO PREEMPTION                                                │
 *   │     A lock cannot be forcibly taken from a thread.               │
 *   │     The holder must release it voluntarily.                      │
 *   │                                                                  │
 *   │  4. CIRCULAR WAIT                                                │
 *   │     Thread 1 waits for Thread 2, Thread 2 waits for Thread 1.   │
 *   │     A cycle exists in the wait-for graph.                        │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   Break ANY ONE of these four → no deadlock possible.
 *
 * ============================================================================
 * 3. Deadlock diagram (from screenshot)
 * ============================================================================
 *
 *   Thread 1                                     Thread 2
 *   ────────                                     ────────
 *      │                                            │
 *      ▼                                            ▼
 *   ┌──────┐                                    ┌──────┐
 *   │Lock A│  ← Thread 1 acquires Lock A        │Lock B│  ← Thread 2 acquires Lock B
 *   │ HELD │                                    │ HELD │
 *   └──────┘                                    └──────┘
 *      │                                            │
 *      │         ......time passes......            │
 *      │                                            │
 *      ▼                                            ▼
 *   ┌──────┐                                    ┌──────┐
 *   │Lock B│  ← Thread 1 WANTS Lock B           │Lock A│  ← Thread 2 WANTS Lock A
 *   │BLOCKED│   (held by Thread 2!)             │BLOCKED│   (held by Thread 1!)
 *   └──────┘                                    └──────┘
 *      │                                            │
 *      ▼                                            ▼
 *   ████████████████ DEADLOCK ████████████████████████
 *   Neither can proceed. Program is frozen.
 *
 *   Circular wait graph:
 *
 *   ┌──────────┐   waits for Lock B   ┌──────────┐
 *   │ Thread 1 │ ─────────────────────►│ Thread 2 │
 *   │ holds A  │◄───────────────────── │ holds B  │
 *   └──────────┘   waits for Lock A   └──────────┘
 *         ▲                                  │
 *         └──────────── CYCLE ───────────────┘
 *
 * ============================================================================
 * 4. How this program creates a deadlock (step by step)
 * ============================================================================
 *
 *   Time   Thread 1 (workerOne)          Thread 2 (workerTwo)
 *   ────   ────────────────────          ────────────────────
 *   t=0    lockA.lock() ✓               lockB.lock() ✓
 *          "acquired lockA"              "acquired lockB"
 *
 *   t=200  lockB.lock() ...BLOCKED!     lockA.lock() ...BLOCKED!
 *   ms     (lockB held by Thread 2)      (lockA held by Thread 1)
 *
 *   t=???  DEADLOCK — both waiting forever
 *
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  workerOne: holds lockA, wants lockB                     │
 *   │  workerTwo: holds lockB, wants lockA                     │
 *   │                                                          │
 *   │  Neither will ever release what they hold because        │
 *   │  they're blocked waiting for the other lock.             │
 *   └──────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 5. How to spot deadlocks
 * ============================================================================
 *
 * ── 5a. Manual approach (NOT efficient) ──
 *
 *   1. Code review: visually trace lock acquisition order across all threads.
 *      Look for methods that acquire locks in different orders.
 *
 *      ┌──────────────────────────────────────────────────┐
 *      │  workerOne():  lockA → lockB                     │
 *      │  workerTwo():  lockB → lockA   ← reversed order! │
 *      │  DEADLOCK RISK!                                   │
 *      └──────────────────────────────────────────────────┘
 *
 *   2. Observe symptoms: program hangs, no CPU usage, no progress,
 *      no exceptions. Threads are alive but doing nothing.
 *
 *   3. Draw a wait-for graph: for each thread, note which locks it
 *      holds and which it's waiting for. Look for cycles.
 *
 *   Problems with manual approach:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  - Doesn't scale: complex codebases have hundreds of     │
 *   │    lock sites. Nearly impossible to trace all paths.     │
 *   │  - Timing-dependent: deadlocks may only happen under     │
 *   │    specific interleavings. Hard to reproduce.            │
 *   │  - Easy to miss indirect cycles: A→B→C→A                │
 *   └──────────────────────────────────────────────────────────┘
 *
 * ── 5b. Programmatic approach to detect deadlock ──
 *
 *   i) THREAD DUMP (most common technique)
 *
 *      A thread dump shows the state of every thread in the JVM:
 *      which lock each thread holds and which lock it's waiting for.
 *      The JVM can even detect cycles automatically!
 *
 *      How to take a thread dump:
 *      ┌──────────────────────────────────────────────────────┐
 *      │  Option 1: jstack <pid>                              │
 *      │            jstack $(jps | grep DeadLockDemo | awk    │
 *      │            '{print $1}')                             │
 *      │                                                      │
 *      │  Option 2: kill -3 <pid>  (sends SIGQUIT to JVM)     │
 *      │                                                      │
 *      │  Option 3: jcmd <pid> Thread.print                   │
 *      │                                                      │
 *      │  Option 4: From code using ThreadMXBean (see below)  │
 *      └──────────────────────────────────────────────────────┘
 *
 *      What a thread dump shows for a deadlock:
 *      ┌──────────────────────────────────────────────────────┐
 *      │  "Worker One":                                        │
 *      │    at DeadLockDemo.workerOne(...)                     │
 *      │    - waiting to lock <0x000...B>  (Lock B)           │
 *      │    - locked        <0x000...A>  (Lock A)             │
 *      │                                                      │
 *      │  "Worker Two":                                        │
 *      │    at DeadLockDemo.workerTwo(...)                     │
 *      │    - waiting to lock <0x000...A>  (Lock A)           │
 *      │    - locked        <0x000...B>  (Lock B)             │
 *      │                                                      │
 *      │  Found 1 deadlock.                                    │
 *      │  ═══════════════════                                  │
 *      │  "Worker One" waiting for "Worker Two"                │
 *      │  "Worker Two" waiting for "Worker One"                │
 *      └──────────────────────────────────────────────────────┘
 *
 *   ii) ThreadMXBean (programmatic detection from within the app)
 *
 *      Java provides ThreadMXBean to detect deadlocks at runtime:
 *
 *      ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
 *      long[] deadlockedThreads = mxBean.findDeadlockedThreads();
 *
 *      if (deadlockedThreads != null) {
 *          ThreadInfo[] infos = mxBean.getThreadInfo(deadlockedThreads);
 *          for (ThreadInfo info : infos) {
 *              System.out.println("DEADLOCKED: " + info.getThreadName());
 *              System.out.println("  waiting for: " + info.getLockName());
 *              System.out.println("  held by: " + info.getLockOwnerName());
 *          }
 *      }
 *
 *      You can run this in a periodic watchdog thread:
 *
 *      ┌──────────────────────────────────────────────────────┐
 *      │  Watchdog Thread (every 5 seconds):                   │
 *      │  ┌────────────────────────────────┐                  │
 *      │  │  findDeadlockedThreads()       │                  │
 *      │  │  ├── null? → no deadlock, ok   │                  │
 *      │  │  └── not null? → LOG + ALERT!  │                  │
 *      │  │      dump stack traces          │                  │
 *      │  │      trigger recovery / restart │                  │
 *      │  └────────────────────────────────┘                  │
 *      └──────────────────────────────────────────────────────┘
 *
 *   iii) JVisualVM / JConsole (GUI tools)
 *
 *      ┌──────────────────────────────────────────────────────┐
 *      │  - JVisualVM: run `jvisualvm`, connect to your JVM   │
 *      │    Go to "Threads" tab → "Detect Deadlock" button    │
 *      │    Highlights deadlocked threads in red               │
 *      │                                                      │
 *      │  - JConsole: run `jconsole`, connect to your JVM     │
 *      │    Go to "Threads" tab → "Detect Deadlock" button    │
 *      │    Shows full thread dump with deadlock info          │
 *      └──────────────────────────────────────────────────────┘
 *
 *   iv) Static analysis tools (detect BEFORE runtime)
 *
 *      ┌──────────────────────────────────────────────────────┐
 *      │  - IntelliJ IDEA: "Analyze → Inspect Code"           │
 *      │    warns about inconsistent lock ordering             │
 *      │                                                      │
 *      │  - SpotBugs / FindBugs: detects patterns like        │
 *      │    "Inconsistent synchronization" and "Lock inversion"│
 *      │                                                      │
 *      │  - Thread sanitizer (TSan): detects lock-order        │
 *      │    inversions at runtime in native code               │
 *      └──────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 6. How to PREVENT deadlocks (with code examples)
 * ============================================================================
 *
 * ── Strategy 1: CONSISTENT LOCK ORDERING (breaks CIRCULAR WAIT) ──
 *
 *   The most common and effective fix. ALL threads acquire locks
 *   in the SAME global order. No cycle can form.
 *
 *   THE DEADLOCKED CODE (this file):
 *   ┌──────────────────────────────────────────────────────┐
 *   │  workerOne():  lockA → lockB                         │
 *   │  workerTwo():  lockB → lockA   ← REVERSED! DEADLOCK │
 *   └──────────────────────────────────────────────────────┘
 *
 *   THE FIX:
 *   ┌──────────────────────────────────────────────────────┐
 *   │  workerOne():  lockA → lockB                         │
 *   │  workerTwo():  lockA → lockB   ← SAME ORDER! SAFE   │
 *   └──────────────────────────────────────────────────────┘
 *
 *   Code:
 *
 *   // FIXED workerTwo -- acquire lockA FIRST, then lockB (same order as workerOne)
 *   public void workerTwoFixed() {
 *       lockA.lock();                        // ← acquire A first (not B!)
 *       try {
 *           System.out.println("Worker two acquired lockA");
 *           Thread.sleep(200);
 *           lockB.lock();                    // ← then acquire B
 *           try {
 *               System.out.println("Worker two acquired lockB");
 *           } finally {
 *               lockB.unlock();
 *           }
 *       } catch (InterruptedException e) {
 *           throw new RuntimeException(e);
 *       } finally {
 *           lockA.unlock();
 *       }
 *   }
 *
 *   For dynamic objects (e.g. transfer between accounts), use a
 *   deterministic order like comparing object hashCode or ID:
 *
 *   void transfer(Account from, Account to, int amount) {
 *       Account first  = from.id < to.id ? from : to;
 *       Account second = from.id < to.id ? to   : from;
 *
 *       synchronized (first) {               // always lock lower ID first
 *           synchronized (second) {
 *               from.balance -= amount;
 *               to.balance   += amount;
 *           }
 *       }
 *   }
 *
 *   Flow:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  Account A (id=1)   Account B (id=2)                    │
 *   │                                                          │
 *   │  transfer(A, B):  lock(A) → lock(B)   (1 < 2, so A first)│
 *   │  transfer(B, A):  lock(A) → lock(B)   (1 < 2, still A!) │
 *   │                                                          │
 *   │  Same order regardless of argument order → NO DEADLOCK   │
 *   └──────────────────────────────────────────────────────────┘
 *
 *
 * ── Strategy 2: tryLock WITH TIMEOUT (breaks HOLD AND WAIT) ──
 *
 *   Instead of blocking forever, try to acquire the second lock
 *   with a timeout. If it fails, RELEASE the first lock and retry.
 *   The thread never holds-and-waits indefinitely.
 *
 *   Code:
 *
 *   public void workerOneWithTryLock() {
 *       while (true) {
 *           lockA.lock();
 *           try {
 *               System.out.println("Worker one acquired lockA");
 *
 *               if (lockB.tryLock(500, TimeUnit.MILLISECONDS)) {
 *                   try {
 *                       System.out.println("Worker one acquired lockB");
 *                       // do work with both locks
 *                       return;  // success, exit loop
 *                   } finally {
 *                       lockB.unlock();
 *                   }
 *               } else {
 *                   System.out.println("Worker one could NOT get lockB, releasing lockA and retrying...");
 *                   // tryLock timed out -- release lockA and retry
 *               }
 *           } catch (InterruptedException e) {
 *               throw new RuntimeException(e);
 *           } finally {
 *               lockA.unlock();  // release lockA whether we got lockB or not
 *           }
 *
 *           // back off briefly to avoid livelock (both retrying at the same instant)
 *           try { Thread.sleep((long)(Math.random() * 100)); }
 *           catch (InterruptedException e) { throw new RuntimeException(e); }
 *       }
 *   }
 *
 *   Timeline (no deadlock):
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  Worker One                    Worker Two                    │
 *   │  lockA.lock() ✓               lockB.lock() ✓                │
 *   │  tryLock(lockB, 500ms)         tryLock(lockA, 500ms)         │
 *   │  │ waiting...                  │ waiting...                  │
 *   │  │ 500ms elapsed               │ 500ms elapsed               │
 *   │  └─ TIMEOUT! returns false     └─ TIMEOUT! returns false     │
 *   │  lockA.unlock()  (releases!)   lockB.unlock()  (releases!)  │
 *   │  sleep(random backoff)         sleep(random backoff)         │
 *   │  lockA.lock() ✓                                              │
 *   │  tryLock(lockB) ✓ (free now!)                                │
 *   │  ── does work ──                                             │
 *   │  unlocks both                                                │
 *   └──────────────────────────────────────────────────────────────┘
 *
 *   IMPORTANT: add random backoff to prevent LIVELOCK (both threads
 *   retrying at the exact same time, failing, retrying, failing...).
 *
 *
 * ── Strategy 3: SINGLE LOCK (breaks HOLD AND WAIT) ──
 *
 *   Use one coarse-grained lock for all resources. A thread acquires
 *   just ONE lock -- it never holds one while waiting for another.
 *
 *   Code:
 *
 *   private final Lock singleLock = new ReentrantLock();
 *
 *   public void workerOneSingleLock() {
 *       singleLock.lock();
 *       try {
 *           // access both resources A and B under one lock
 *           System.out.println("Worker one doing work on A and B");
 *       } finally {
 *           singleLock.unlock();
 *       }
 *   }
 *
 *   public void workerTwoSingleLock() {
 *       singleLock.lock();
 *       try {
 *           // access both resources A and B under one lock
 *           System.out.println("Worker two doing work on A and B");
 *       } finally {
 *           singleLock.unlock();
 *       }
 *   }
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  + Simple, no cycle possible (only 1 lock exists)    │
 *   │  - Reduces parallelism (all access serialized)       │
 *   │  - Doesn't scale for many independent resources      │
 *   │                                                      │
 *   │  Best for: small critical sections with few resources│
 *   └──────────────────────────────────────────────────────┘
 *
 *
 * ── Strategy 4: LOCK-FREE ALGORITHMS (breaks MUTUAL EXCLUSION) ──
 *
 *   Replace locks entirely with atomic operations (CAS-based).
 *   No locks means no deadlocks.
 *
 *   Code:
 *
 *   // Instead of:
 *   private int counter = 0;
 *   private final Lock lock = new ReentrantLock();
 *   void increment() {
 *       lock.lock();
 *       try { counter++; } finally { lock.unlock(); }
 *   }
 *
 *   // Use:
 *   private final AtomicInteger counter = new AtomicInteger(0);
 *   void increment() {
 *       counter.incrementAndGet();  // CAS-based, no lock needed
 *   }
 *
 *   Other lock-free tools:
 *   ┌──────────────────────────────────────────────────────┐
 *   │  AtomicInteger / AtomicLong    → counters            │
 *   │  AtomicReference               → swap object refs    │
 *   │  ConcurrentHashMap             → thread-safe map     │
 *   │  ConcurrentLinkedQueue         → thread-safe queue   │
 *   │  LongAdder                     → high-contention sums│
 *   └──────────────────────────────────────────────────────┘
 *
 *
 * ── Strategy 5: lockInterruptibly (breaks NO PREEMPTION) ──
 *
 *   Use lockInterruptibly() so that a blocked thread can be
 *   interrupted and forced to release, breaking the cycle.
 *
 *   Code:
 *
 *   public void workerOneInterruptible() {
 *       try {
 *           lockA.lockInterruptibly();
 *           try {
 *               Thread.sleep(200);
 *               lockB.lockInterruptibly();   // can be interrupted!
 *               try {
 *                   System.out.println("Worker one got both locks");
 *               } finally {
 *                   lockB.unlock();
 *               }
 *           } finally {
 *               lockA.unlock();
 *           }
 *       } catch (InterruptedException e) {
 *           System.out.println("Worker one was interrupted, releasing all locks");
 *           // clean up and exit gracefully
 *       }
 *   }
 *
 *   A watchdog thread can detect the deadlock (via ThreadMXBean)
 *   and interrupt one of the threads to break it:
 *
 *   // Watchdog
 *   long[] deadlocked = mxBean.findDeadlockedThreads();
 *   if (deadlocked != null) {
 *       threadMap.get(deadlocked[0]).interrupt();  // break the cycle!
 *   }
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  Thread 1: lockA → lockInterruptibly(B) ...waiting   │
 *   │  Thread 2: lockB → lockInterruptibly(A) ...waiting   │
 *   │                                                      │
 *   │  Watchdog detects deadlock → interrupts Thread 1     │
 *   │  Thread 1 catches InterruptedException, releases A   │
 *   │  Thread 2 gets lockA → proceeds                      │
 *   │  Deadlock broken!                                    │
 *   └──────────────────────────────────────────────────────┘
 *
 *
 * ── Prevention strategy summary ──
 *
 *   ┌───────────────────────────┬──────────────────┬──────────────────────────┐
 *   │  Strategy                 │ Coffman condition │ Trade-off                │
 *   │                           │ it breaks         │                          │
 *   │───────────────────────────│──────────────────│──────────────────────────│
 *   │  Consistent lock ordering │ Circular wait     │ Must know all locks      │
 *   │                           │                   │ upfront; can be complex  │
 *   │  tryLock with timeout     │ Hold and wait     │ May need retry/backoff;  │
 *   │                           │                   │ risk of livelock         │
 *   │  Single lock              │ Hold and wait     │ Reduces parallelism;     │
 *   │                           │                   │ coarse-grained           │
 *   │  Lock-free (Atomic/CAS)  │ Mutual exclusion  │ Only for simple ops;     │
 *   │                           │                   │ complex logic still needs│
 *   │                           │                   │ locks                    │
 *   │  lockInterruptibly        │ No preemption     │ Needs a watchdog;        │
 *   │                           │                   │ recovery logic required  │
 *   └───────────────────────────┴──────────────────┴──────────────────────────┘
 *
 *   Rule of thumb:
 *   - Start with CONSISTENT LOCK ORDERING -- it's the simplest and most reliable.
 *   - Use tryLock with timeout for systems where lock ordering is impractical.
 *   - Use lock-free structures when possible (counters, queues, maps).
 *   - Use lockInterruptibly + watchdog for long-running production systems.
 *
 * ============================================================================
 * 7. Detection techniques summary
 * ============================================================================
 *
 *   ┌──────────────────────┬────────────┬──────────────────────────────────┐
 *   │  Technique           │  When      │  How                             │
 *   │──────────────────────│────────────│──────────────────────────────────│
 *   │  Code review         │  Dev time  │  Trace lock ordering manually    │
 *   │  Static analysis     │  Build time│  SpotBugs, IntelliJ inspections  │
 *   │  Thread dump (jstack)│  Runtime   │  jstack <pid> or kill -3 <pid>   │
 *   │  ThreadMXBean        │  Runtime   │  findDeadlockedThreads() in code │
 *   │  JVisualVM / JConsole│  Runtime   │  GUI, click "Detect Deadlock"    │
 *   │  Watchdog thread     │  Runtime   │  Periodic ThreadMXBean check     │
 *   └──────────────────────┴────────────┴──────────────────────────────────┘
 *
 * ============================================================================
 * 8. Practical uses (one-liners)
 * ============================================================================
 *
 * - Database transactions: two transactions lock rows in different order → DB deadlock → DB auto-detects and rolls back one.
 * - Thread pool starvation: all pool threads waiting for tasks that need the same pool to run → effective deadlock.
 * - Distributed locks: two microservices hold different Redis locks and each needs the other's → distributed deadlock.
 * - File locking: two processes lock two files in different order → OS-level deadlock.
 * - GUI frameworks: background thread holds a model lock and needs UI lock, UI thread holds UI lock and needs model lock.
 *
 * ============================================================================
 * 9. Interview one-liner
 * ============================================================================
 *
 * A deadlock is a cyclic dependency where threads hold locks and wait for
 * each other's locks, detectable via jstack/ThreadMXBean, preventable by
 * consistent lock ordering or tryLock with timeout.
 *
 * ============================================================================
 * 10. REAL DEBUGGING SESSION -- how we caught this deadlock live
 * ============================================================================
 *
 * Step 1: Run the program. It prints two lines and HANGS:
 *
 *   Worker one acquired lockA
 *   Worker two acquired lockB
 *   (... program is stuck, no more output, no exception, no exit ...)
 *
 * Step 2: Find the JVM process ID with jps:
 *
 *   $ jps -l
 *   70928 jdk.jcmd/sun.tools.jps.Jps
 *   68917 org.jetbrains.jps.cmdline.Launcher
 *   68918 LLD.Topics.MultiThreading.OtherCOncepts.DeadLockDemo   ← this one!
 *
 * Step 3: Send SIGQUIT to get a thread dump:
 *
 *   $ kill -3 68918
 *
 *   This does NOT kill the process. It tells the JVM to print a full
 *   thread dump to stderr (visible in the IDE console).
 *
 * Step 4: Read the thread dump. The two important threads:
 *
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │  "Worker One" #15  WAITING (parking)                               │
 *   │    at Unsafe.park(Native Method)                                   │
 *   │    - parking to wait for <0x0000000360631e70>                      │
 *   │      (a ReentrantLock$FairSync)                                    │
 *   │    at ReentrantLock.lock(ReentrantLock.java:322)                   │
 *   │    at DeadLockDemo.workerOne(DeadLockDemo.java:21)  ← lockB.lock()│
 *   │                                                                     │
 *   │  "Worker Two" #16  WAITING (parking)                               │
 *   │    at Unsafe.park(Native Method)                                   │
 *   │    - parking to wait for <0x0000000360631e40>                      │
 *   │      (a ReentrantLock$FairSync)                                    │
 *   │    at ReentrantLock.lock(ReentrantLock.java:322)                   │
 *   │    at DeadLockDemo.workerTwo(DeadLockDemo.java:37)  ← lockA.lock()│
 *   └─────────────────────────────────────────────────────────────────────┘
 *
 *   Both threads are in WAITING (parking) state -- parked on an AQS lock.
 *   Worker One is stuck at line 21 (lockB.lock()).
 *   Worker Two is stuck at line 37 (lockA.lock()).
 *
 * Step 5: The JVM auto-detects the deadlock at the bottom of the dump:
 *
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │  Found one Java-level deadlock:                                     │
 *   │  =============================                                      │
 *   │  "Worker One":                                                      │
 *   │    waiting for ownable synchronizer 0x0000000360631e70              │
 *   │    (a ReentrantLock$FairSync),                                      │
 *   │    which is held by "Worker Two"                                    │
 *   │                                                                     │
 *   │  "Worker Two":                                                      │
 *   │    waiting for ownable synchronizer 0x0000000360631e40              │
 *   │    (a ReentrantLock$FairSync),                                      │
 *   │    which is held by "Worker One"                                    │
 *   │                                                                     │
 *   │  Found 1 deadlock.                                                  │
 *   └─────────────────────────────────────────────────────────────────────┘
 *
 *   This is the GOLD -- the JVM tells you EXACTLY:
 *   - Which thread is waiting for which lock object (by address)
 *   - Which OTHER thread holds that lock
 *   - The circular dependency
 *
 * Step 6: Map the addresses to understand the cycle:
 *
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  Lock address 0x...e40 = lockA (ReentrantLock$FairSync)  │
 *   │  Lock address 0x...e70 = lockB (ReentrantLock$FairSync)  │
 *   │                                                          │
 *   │  Worker One: HOLDS lockA (0x...e40)                      │
 *   │              WANTS lockB (0x...e70) ← held by Worker Two │
 *   │                                                          │
 *   │  Worker Two: HOLDS lockB (0x...e70)                      │
 *   │              WANTS lockA (0x...e40) ← held by Worker One │
 *   │                                                          │
 *   │  ┌────────────┐  wants 0x...e70  ┌────────────┐        │
 *   │  │ Worker One │ ────────────────► │ Worker Two │        │
 *   │  │ holds      │ ◄──────────────── │ holds      │        │
 *   │  │ 0x...e40   │  wants 0x...e40  │ 0x...e70   │        │
 *   │  └────────────┘                  └────────────┘        │
 *   │         ▲        CIRCULAR WAIT         │                │
 *   │         └──────────────────────────────┘                │
 *   └──────────────────────────────────────────────────────────┘
 *
 * Step 7: How to read other threads in the dump:
 *
 *   The dump also shows JVM internal threads. These are NORMAL and
 *   not part of the deadlock:
 *
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  Thread name             │ What it does                  │
 *   │──────────────────────────│───────────────────────────────│
 *   │  Reference Handler       │ Processes soft/weak refs      │
 *   │  Finalizer               │ Runs finalize() methods       │
 *   │  Signal Dispatcher       │ Handles OS signals            │
 *   │  C1/C2 CompilerThread    │ JIT compilation               │
 *   │  G1 Service / GC Thread  │ Garbage collection            │
 *   │  Common-Cleaner          │ Phantom reference cleanup     │
 *   │  Monitor Ctrl-Break      │ IntelliJ debug communication  │
 *   │  DestroyJavaVM           │ Waiting for all non-daemon    │
 *   │                          │ threads to finish (main)      │
 *   └──────────────────────────────────────────────────────────┘
 *
 *   Only look at YOUR threads ("Worker One", "Worker Two") to debug.
 *
 * Summary of the debugging flow:
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  1. Program hangs (no output, no exception, no exit)            │
 *   │            │                                                    │
 *   │            ▼                                                    │
 *   │  2. jps -l  →  find the PID of the deadlocked process          │
 *   │            │                                                    │
 *   │            ▼                                                    │
 *   │  3. kill -3 <pid>  →  trigger thread dump (doesn't kill!)       │
 *   │            │                                                    │
 *   │            ▼                                                    │
 *   │  4. Read the dump: look for WAITING threads + line numbers      │
 *   │            │                                                    │
 *   │            ▼                                                    │
 *   │  5. Scroll to bottom: "Found one Java-level deadlock"           │
 *   │     JVM tells you exactly who holds what and who wants what     │
 *   │            │                                                    │
 *   │            ▼                                                    │
 *   │  6. Fix: reorder lock acquisition to be consistent              │
 *   │     workerOne: lockA → lockB                                    │
 *   │     workerTwo: lockA → lockB  (same order, no deadlock!)        │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 11. Code demo below
 * ============================================================================
 * workerOne acquires lockA then wants lockB.
 * workerTwo acquires lockB then wants lockA.
 * Both block forever → deadlock. Use jstack or kill -3 to confirm.
 */
public class DeadLockDemo {

    private final Lock lockA = new ReentrantLock(true);
    private final Lock lockB = new ReentrantLock(true);


    public void workerOne() {
        lockA.lock();
        System.out.println("Worker one acquired lockA");
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
           throw new RuntimeException(e);
        }

        lockB.lock();
        System.out.println("Worker one acquired lockB");
        lockA.unlock();
        lockB.unlock();
    }

    public void workerTwo() {
        System.out.println("Worker two before acquired lockB");
        lockB.lock();
        System.out.println("Worker two acquired lockB");
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        lockA.lock();
        System.out.println("Worker two acquired lockA");
        lockA.unlock();
        lockB.unlock();
    }

    public static void main(String[] args) {
        DeadLockDemo demo = new DeadLockDemo();

        new Thread(demo::workerOne, "Worker One").start();
        new Thread(demo::workerTwo, "Worker Two").start();

//        debugging
        new Thread(() -> {
            ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            while(true) {
                long[] threadIds = mxBean.findDeadlockedThreads();
                if(threadIds !=null) {
                    System.out.println("Deadlock detected");
                    ThreadInfo [] threadInfos = mxBean.getThreadInfo(threadIds);
                    for (long threadId: threadIds) {
                        System.out.println("Thread with ID " + threadId + " is in Deadlock");
                    }
                    break;
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}


/**
 * Worker one acquired lockA
 * Worker two acquired lockB
 *
 * deadlock -- program hangs here forever.
 * debug1
 * jps -l
 *kill -3 68918
 * Use: jstack <pid> to see the deadlock detection output.
 *
 * debug 2 :
 * using ThreadMXBean
 */



