package LLD.Topics.MultiThreading.Locks;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    Condition (Lock + await/signal)                      ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is a Condition?
 * ============================================================================
 *
 * A Condition is a waiting/notification mechanism tied to a ReentrantLock.
 * It replaces Object.wait() / Object.notify() with a more powerful API:
 *
 *   Lock lock = new ReentrantLock();
 *   Condition condition = lock.newCondition();
 *
 *   condition.await()     -- release the lock and sleep until signaled
 *   condition.signal()    -- wake ONE waiting thread
 *   condition.signalAll() -- wake ALL waiting threads
 *
 * The key advantage: you can create MULTIPLE conditions from the same lock,
 * so different threads can wait for different events on the same lock.
 *
 * ============================================================================
 * 2. How condition.await() and condition.signal() work -- diagram
 * ============================================================================
 *
 *   Two threads share one lock and one condition:
 *
 *   Thread 1                     condition                    Thread 2
 *   ────────                     ─────────                    ────────
 *   lock.lock()
 *   │  do some work
 *   │  condition.await() ────► releases lock, goes to WAITING
 *   │  (sleeping...)            │                             lock.lock()
 *   │                           │                             │  do some work
 *   │                           │                             │  condition.signal()
 *   │                           ◄─────────────────────────────┘  wakes Thread 1
 *   │                           │                             │  still holds lock
 *   │                           │                             lock.unlock()
 *   │  ◄── re-acquires lock ───┘
 *   │  continues after await()
 *   lock.unlock()
 *
 * ============================================================================
 * 3. signalAll() -- wakes ALL waiters (diagram from screenshot)
 * ============================================================================
 *
 *   Thread 1         Thread 2         Thread 3
 *   ────────         ────────         ────────
 *   │                │                │
 *   ▼                ▼                │
 *   condition        condition        │
 *   .await()         .await()         │ does some work
 *   │                │                │
 *   │ WAITING        │ WAITING        │
 *   │                │                │ condition.signalAll()
 *   │                │                │ ── wakes ALL waiters ──
 *   ▼                ▼                ▼
 *   (both Thread 1 and Thread 2 wake up)
 *   (each must re-acquire the lock before proceeding)
 *
 * ============================================================================
 * 4. signal() vs signalAll()
 * ============================================================================
 *
 *   signal():
 *   - Wakes exactly ONE thread waiting on this condition (arbitrary choice).
 *   - More efficient if you know only one waiter needs to proceed.
 *   - Risk: might wake the "wrong" waiter if multiple are waiting for
 *     different reasons on the same condition.
 *
 *   signalAll():
 *   - Wakes ALL threads waiting on this condition.
 *   - Each must re-acquire the lock and re-check its condition in a while loop.
 *   - Safer when you're unsure which waiter should proceed.
 *
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │  signal()     → wake 1 waiter   (precise, but risky)         │
 *   │  signalAll()  → wake all waiters (safe, but more contention) │
 *   └────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 5. Condition (await/signal) vs Object (wait/notify) -- how are they different?
 * ============================================================================
 *
 *   Feature                  Object.wait/notify          Condition.await/signal
 *   ──────────────────────   ────────────────────────    ────────────────────────
 *   Tied to                  synchronized block           ReentrantLock
 *   Multiple wait sets       NO (one per object)          YES (lock.newCondition())
 *   Method names             wait(), notify(), notifyAll  await(), signal(), signalAll
 *   Timed wait               wait(timeout)                await(timeout, unit)
 *   Interruptible            Yes                          Yes + awaitUninterruptibly()
 *   Deadline-based           No                           awaitUntil(Date)
 *   Precision of wakeup      notify wakes ANY waiter      signal wakes waiter on
 *                             from the ONE shared set      THIS specific condition
 *
 *   The big win: MULTIPLE CONDITIONS on one lock.
 *
 *   synchronized + wait/notify:
 *   ┌──────────────────────────────────────────────┐
 *   │  ONE lock, ONE wait set                      │
 *   │  Producer waits  ──┐                         │
 *   │  Consumer waits  ──┤── same wait set!        │
 *   │  notify() -- might wake a producer           │
 *   │  when you wanted to wake a consumer!         │
 *   └──────────────────────────────────────────────┘
 *
 *   ReentrantLock + Condition:
 *   ┌──────────────────────────────────────────────┐
 *   │  ONE lock, TWO conditions (this file!)       │
 *   │                                              │
 *   │  bufferNotFull  -- producers wait here       │
 *   │  bufferNotEmpty -- consumers wait here       │
 *   │                                              │
 *   │  bufferNotEmpty.signal() -- wakes ONLY       │
 *   │    a consumer (precise!)                     │
 *   │  bufferNotFull.signal()  -- wakes ONLY       │
 *   │    a producer (precise!)                     │
 *   └──────────────────────────────────────────────┘
 *
 * ============================================================================
 * 6. How THIS program works -- diagram
 * ============================================================================
 *
 *   Producer thread                                 Consumer thread
 *   ───────────────                                 ───────────────
 *   lock.lock()                                     (waiting for lock)
 *   │  buffer full?
 *   │  ├── YES: bufferNotFull.await()
 *   │  │   (releases lock, sleeps)                  lock.lock()
 *   │  │                                            │  buffer empty?
 *   │  │                                            │  ├── YES: bufferNotEmpty.await()
 *   │  │                                            │  │   (releases lock, sleeps)
 *   │  │                                            │  └── NO: poll item
 *   │  │                                            │       bufferNotFull.signal()
 *   │  │   ◄── woken by signal ────────────────────┘       (wakes producer!)
 *   │  └── NO: offer item
 *   │       bufferNotEmpty.signal()   ──────────────────► (wakes consumer!)
 *   lock.unlock()                                   lock.unlock()
 *
 *   Internal state:
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │  ReentrantLock lock                                            │
 *   │  ┌───────────────────────┐  ┌────────────────────────┐        │
 *   │  │  Condition             │  │  Condition              │        │
 *   │  │  bufferNotFull         │  │  bufferNotEmpty         │        │
 *   │  │  (producers wait here) │  │  (consumers wait here)  │        │
 *   │  └───────────────────────┘  └────────────────────────┘        │
 *   │                                                                │
 *   │  Queue<Integer> buffer (capacity = 5)                          │
 *   │  ┌───┬───┬───┬───┬───┐                                       │
 *   │  │   │   │   │   │   │                                       │
 *   │  └───┴───┴───┴───┴───┘                                       │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 7. Practical uses (one-liners)
 * ============================================================================
 *
 * - Bounded buffer / producer-consumer (this file): separate conditions for "not full" and "not empty".
 * - ArrayBlockingQueue internals: uses exactly this pattern (one lock, two conditions).
 * - Thread pool: idle workers await on a "taskAvailable" condition, dispatcher signals on submit.
 * - Rate limiter: threads await until tokens are replenished, token refiller signals periodically.
 * - Read-write coordination: writers signal readers when data is ready.
 *
 * ============================================================================
 * 8. Code demo below
 * ============================================================================
 * Producer adds 10 items (1s apart), consumer takes 10 items (2s apart).
 * bufferNotFull/bufferNotEmpty conditions coordinate so producer waits when
 * buffer is full (5), consumer waits when buffer is empty.
 */
public class ConditionDemo {

    private final Integer MAX_SIZE = 5;
    private final Lock lock = new ReentrantLock();
    private final Queue<Integer> buffer = new LinkedList<>();

    private final Condition bufferNotFull = lock.newCondition();
    private final Condition bufferNotEmpty = lock.newCondition();

    private void Produce(int item) {
        lock.lock();

        try {
            if (buffer.size() == MAX_SIZE) {
                bufferNotFull.await();
            }
            buffer.offer(item);
            System.out.println("Produced >> " + item);
            bufferNotEmpty.signal();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

    }

    private void consume() throws InterruptedException {
        lock.lock();

        try {
            if(buffer.isEmpty()) {
                bufferNotEmpty.await();
            }

            System.out.println("Consumed << " + buffer.poll());
            bufferNotFull.signal();
        } finally {
            lock.unlock();
        }
    }


    public static void main(String[] args) {
        ConditionDemo demo = new ConditionDemo();

        Thread producerThread = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    demo.Produce(i);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread consumerThread = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    demo.consume();
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        producerThread.start();
        consumerThread.start();
    }

}

/**
 * Produced >> 0
 * Consumed << 0
 * Produced >> 1
 * Consumed << 1
 * Produced >> 2
 * Produced >> 3
 * Consumed << 2
 * Produced >> 4
 * Produced >> 5
 * Consumed << 3
 * Produced >> 6
 * Produced >> 7
 * Consumed << 4
 * Produced >> 8
 * Produced >> 9
 * Consumed << 5
 * Consumed << 6
 * Consumed << 7
 * Consumed << 8
 * Consumed << 9
 *
 * order might not be deterministic
 */
