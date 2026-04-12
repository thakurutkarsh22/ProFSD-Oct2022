# MultiThreading in Java

A progressive walkthrough of Java multithreading — from sequential execution to concurrent collections.
Every file includes: structured theory, ASCII diagrams, practical real-world uses, and a code demo.
Files are listed in the order they were created.

---

## 1. Sequential Execution

| # | File | What it covers |
|---|------|----------------|
| 1 | [`Sequential/SequentialExecutionDemo.java`](Sequential/SequentialExecutionDemo.java) | Baseline: a single thread runs `demo1()` then `demo2()` — no concurrency. Shows why we need threads when tasks could overlap. |

---

## 2. Basic MultiThreading

| # | File | What it covers |
|---|------|----------------|
| 2 | [`BasicMultiThreading/RunnableThreadExample.java`](BasicMultiThreading/RunnableThreadExample.java) | Creating threads by implementing `Runnable`. Separation of concerns, single-inheritance advantage, anonymous `Runnable`. |
| 3 | [`BasicMultiThreading/ExtendsThreadExample.java`](BasicMultiThreading/ExtendsThreadExample.java) | Creating threads by extending `Thread`. Compares trade-offs of `extends Thread` vs `implements Runnable`. |
| 4 | [`BasicMultiThreading/JoinThreadExample.java`](BasicMultiThreading/JoinThreadExample.java) | `thread.join()` — makes the calling thread wait for the target to finish before continuing. |
| 5 | [`BasicMultiThreading/DaemonUserThreadExample.java`](BasicMultiThreading/DaemonUserThreadExample.java) | Daemon vs user threads. JVM exits when all user threads finish; daemon threads are killed automatically. |
| 6 | [`BasicMultiThreading/ThreadPriorityExample.java`](BasicMultiThreading/ThreadPriorityExample.java) | Thread priority API (`MIN_PRIORITY` 1 → `MAX_PRIORITY` 10). Hints to the scheduler, not a guarantee. |

---

## 3. Thread Synchronization

| # | File | What it covers |
|---|------|----------------|
| 7 | [`ThreadSynchronization/SynchronizationDemo.java`](ThreadSynchronization/SynchronizationDemo.java) | Race conditions with shared counters. `synchronized` static methods and monitor locks. Why a single class-wide lock serializes unrelated work. |
| 8 | [`ThreadSynchronization/LockWithCustomObjectsExample.java`](ThreadSynchronization/LockWithCustomObjectsExample.java) | Fine-grained locking with dedicated `Object` monitors (`lock1`, `lock2`). Two independent counters update in parallel safely. |
| 9 | [`ThreadSynchronization/WaitAndNotifyExample.java`](ThreadSynchronization/WaitAndNotifyExample.java) | `wait()`, `notify()`, and `notifyAll()` on a shared lock. How a thread releases the monitor and parks until another thread signals. |
| 10 | [`ThreadSynchronization/ProducerConsumerProblemExample.java`](ThreadSynchronization/ProducerConsumerProblemExample.java) | Classic producer–consumer with a bounded `LinkedList` buffer, one lock, and `wait`/`notifyAll` for coordination. |

---

## 4. Executor Service

| # | File | What it covers |
|---|------|----------------|
| 11 | [`ExecutorService/SingleThreadExecutorExample.java`](ExecutorService/SingleThreadExecutorExample.java) | `Executors.newSingleThreadExecutor()` — one worker thread, tasks run sequentially. Thread reuse and lifecycle (`shutdown`/`awaitTermination`). |
| 12 | [`ExecutorService/FixedThreadPoolExample.java`](ExecutorService/FixedThreadPoolExample.java) | `Executors.newFixedThreadPool(n)` — exactly n threads share a queue. Predictable resource usage with bounded parallelism. |
| 13 | [`ExecutorService/CachedThreadPoolExample.java`](ExecutorService/CachedThreadPoolExample.java) | `Executors.newCachedThreadPool()` — creates threads on demand, reuses idle ones. Good for short bursts, risky under sustained load. |
| 14 | [`ExecutorService/ScheduledThreadPoolExample.java`](ExecutorService/ScheduledThreadPoolExample.java) | `Executors.newScheduledThreadPool(n)` — run tasks after a delay or on a fixed rate/delay. `scheduleAtFixedRate` vs `scheduleWithFixedDelay`. |
| 15 | [`ExecutorService/CpuIntensiveTaskExample.java`](ExecutorService/CpuIntensiveTaskExample.java) | Ideal pool size for CPU-bound work: `Runtime.getRuntime().availableProcessors()`. Avoids oversubscription and excessive context switching. |
| 16 | [`ExecutorService/CallableExample.java`](ExecutorService/CallableExample.java) | `Callable<V>` vs `Runnable` — returns a value via `Future<V>`. Demonstrates `future.get()`, `future.cancel()`, and `future.isDone()`. |

---

## 5. Concurrent Collections

| # | File | What it covers |
|---|------|----------------|
| 17 | [`ConcurrentCollection/SynchronizedCollection.java`](ConcurrentCollection/SynchronizedCollection.java) | Why `ArrayList` is not thread-safe (lost updates). Fix with `Collections.synchronizedList()`. |
| 18 | [`ConcurrentCollection/CountDownLatchRestraunt.java`](ConcurrentCollection/CountDownLatchRestraunt.java) | `CountDownLatch` — restaurant analogy: kitchen manager waits for all chefs to finish before serving. `await()` vs `Thread.join()`. |
| 19 | [`ConcurrentCollection/BlockingQueueDemo.java`](ConcurrentCollection/BlockingQueueDemo.java) | `BlockingQueue` concepts — blocking `put`/`take`, back-pressure, producer–consumer demo with `LinkedBlockingQueue`. |
| 20 | [`ConcurrentCollection/ArrayBlockingQueueDemo.java`](ConcurrentCollection/ArrayBlockingQueueDemo.java) | `ArrayBlockingQueue` — single `ReentrantLock`, two `Condition`s (`notEmpty`, `notFull`), bounded array, fairness option. Detailed put/take flow diagrams. |
| 21 | [`ConcurrentCollection/LinkedBLockingQueueDemo.java`](ConcurrentCollection/LinkedBLockingQueueDemo.java) | `LinkedBlockingQueue` — two-lock design (`putLock`, `takeLock`), `AtomicInteger` count, higher throughput than `ArrayBlockingQueue`. Comparison table. |
| 22 | [`ConcurrentCollection/ConcurrentCache.java`](ConcurrentCollection/ConcurrentCache.java) | `ConcurrentHashMap` deep-dive — per-bucket `synchronized` + CAS, lock-free `get()` via volatile, contention matrix (read/write same/different bucket), Java 7 vs 8+ locking, comparison to `Hashtable`/`synchronizedMap`. |
| 23 | [`ConcurrentCollection/MultiStageTour.java`](ConcurrentCollection/MultiStageTour.java) | `CyclicBarrier` — multi-stage tour analogy: tourists wait at each stage, barrier trips when all arrive, reusable across stages. Comparison to `CountDownLatch`. |
| 24 | [`ConcurrentCollection/ExchangerDemo.java`](ConcurrentCollection/ExchangerDemo.java) | `Exchanger` — two threads swap data atomically. First thread blocks until the second arrives, then both exchange objects simultaneously. |
| 25 | [`ConcurrentCollection/COWADemo.java`](ConcurrentCollection/COWADemo.java) | `CopyOnWriteArrayList` — every write copies the entire array (O(n)), reads are lock-free snapshots (O(1)). Ideal when reads >> writes. Iterators never throw CME. |

---

## 6. Locks

| # | File | What it covers |
|---|------|----------------|
| 26 | [`Locks/ReentrantLockDemo.java`](Locks/ReentrantLockDemo.java) | `ReentrantLock` deep-dive — reentrant vs non-reentrant (with deadlock diagram), hold count, `tryLock()`, timed `tryLock(t, unit)`, `lockInterruptibly()`, fairness (fair vs non-fair), `getHoldCount()`, `getQueueLength()`, `isHeldByCurrentThread()`, `newCondition()`. Full method summary and when to use over `synchronized`. |
| 27 | [`Locks/ConditionDemo.java`](Locks/ConditionDemo.java) | `Condition` (await/signal) — multiple wait-sets on one `ReentrantLock`. Producer waits on `bufferNotFull`, consumer waits on `bufferNotEmpty`. `signal()` vs `signalAll()`, comparison to `Object.wait()`/`notify()`. |
| 28 | [`Locks/SharedResource.java`](Locks/SharedResource.java) | `ReentrantReadWriteLock` — shared read-lock (multiple readers in parallel) + exclusive write-lock. Wait queue internals (AQS, batch-wake of consecutive readers), fair vs non-fair, lock downgrade (write→read), comparison to `ReentrantLock`/`synchronized`. |
| 29 | [`Locks/VolatileKeywordDemo.java`](Locks/VolatileKeywordDemo.java) | The **visibility problem** — CPU cache architecture (Register→L1→L2→L3→RAM), why one core's write is invisible to another, `volatile` keyword (flush to main memory, invalidate caches), happens-before guarantee, side effects (no atomicity for `count++`, ~50x slower reads), `volatile` vs `synchronized` vs `AtomicInteger`. |

---

## 7. Other Concepts

| # | File | What it covers |
|---|------|----------------|
| 30 | [`OtherCOncepts/DeadLockDemo.java`](OtherCOncepts/DeadLockDemo.java) | **Deadlocks** — what they are, 4 Coffman conditions (mutual exclusion, hold-and-wait, no preemption, circular wait), deadlock diagram with circular wait graph. How to **spot** deadlocks: manual code review vs programmatic (thread dump via `jstack`, `ThreadMXBean.findDeadlockedThreads()`, JVisualVM/JConsole, static analysis). Prevention strategies with code: consistent lock ordering, `tryLock` with timeout, single lock, lock-free, `lockInterruptibly`. Real debugging session with actual thread dump. |
| 31 | [`OtherCOncepts/AtomicVariableDemo.java`](OtherCOncepts/AtomicVariableDemo.java) | **Atomic Variables** — read-modify-write problem (`count++` = 3 steps), CAS (Compare-And-Swap) internals with retry loop, `AtomicInteger`/`AtomicLong`/`AtomicBoolean`/`AtomicReference`, all operations (`get`, `set`, `compareAndSet`, `incrementAndGet`, `getAndAdd`, `updateAndGet`), Atomic vs volatile vs synchronized, `LongAdder` for high contention. |
| 32 | [`OtherCOncepts/ScrapperDemo.java`](OtherCOncepts/ScrapperDemo.java) | **Semaphores** — permit-based concurrency control (N threads at a time), parking lot visualization, `acquire()`/`release()` flow, multiple permits (`acquire(n)`), `tryAcquire()`/`tryAcquire(timeout)`, `availablePermits()`, `drainPermits()`, fairness, Semaphore vs Lock, web scraper throttle demo. |
| 33 | [`OtherCOncepts/MutexDemo.java`](OtherCOncepts/MutexDemo.java) | **Mutex** (MUTual EXclusion) — only 1 thread at a time, "we already learnt it!" (`synchronized` = intrinsic mutex, `ReentrantLock` = explicit mutex, `Semaphore(1)` = binary semaphore). Key difference: mutex has **ownership** (only holder can release) while `Semaphore(1)` does not. Three code implementations compared side by side. |

---

## Learning Path

```
Sequential ──► Basic Threads ──► Synchronization ──► Executor Service ──► Concurrent Collections ──► Locks & Volatile ──► Other Concepts
  (why?)         (how?)         (safe sharing)       (managed pools)        (thread-safe data)        (fine-grained)       (pitfalls)
```
