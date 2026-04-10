# MultiThreading in Java

A progressive walkthrough of Java multithreading — from sequential execution to concurrent collections.
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

---

## Learning Path

```
Sequential ──► Basic Threads ──► Synchronization ──► Executor Service ──► Concurrent Collections
  (why?)         (how?)         (safe sharing)       (managed pools)        (thread-safe data)
```
