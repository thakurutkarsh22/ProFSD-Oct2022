package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/*
 * ArrayBlockingQueue — deep dive
 *
 * ============================================================================
 * 1. What it is
 * ============================================================================
 * - A bounded, array-backed BlockingQueue implementation.
 * - You specify the capacity once at construction (cannot resize later).
 * - Internally backed by a circular array (head/tail indices wrap around).
 * - All operations (put, take, offer, poll, peek, etc.) are guarded by a single ReentrantLock,
 *   so it is fully thread-safe for concurrent producers and consumers.
 *
 * ============================================================================
 * 2. Key operations and their behaviour
 * ============================================================================
 *
 *   Operation       Queue FULL                    Queue EMPTY
 *   --------------- ----------------------------- -----------------------------
 *   add(e)          throws IllegalStateException  —
 *   offer(e)        returns false immediately      —
 *   offer(e,t,unit) waits up to timeout, then      —
 *                   returns false
 *   put(e)          BLOCKS until space available   —
 *
 *   remove()        —                             throws NoSuchElementException
 *   poll()          —                             returns null immediately
 *   poll(t,unit)    —                             waits up to timeout, then null
 *   take()          —                             BLOCKS until element available
 *
 *   peek()          —                             returns null (non-blocking)
 *
 * "Blocking" methods (put/take) are the core feature for producer–consumer:
 *   - put()  lets the producer sleep instead of busy-waiting when the buffer is full.
 *   - take() lets the consumer sleep when the buffer is empty.
 *
 * ============================================================================
 * 3. Fairness
 * ============================================================================
 * - new ArrayBlockingQueue<>(capacity, true) enables FIFO fairness: threads that have been
 *   waiting longest get access first. Default (false) is non-fair but higher throughput.
 *
 * ============================================================================
 * 4. When to use ArrayBlockingQueue vs others
 * ============================================================================
 * - ArrayBlockingQueue: fixed capacity known at start, predictable memory, back-pressure built-in.
 * - LinkedBlockingQueue: optionally bounded, slightly higher throughput under contention because
 *   it uses two locks (one for put, one for take), at the cost of per-node allocation.
 * - SynchronousQueue: capacity 0, each put waits for a take (handoff). Used internally by
 *   Executors.newCachedThreadPool().
 * - PriorityBlockingQueue: unbounded, elements ordered by natural ordering or Comparator.
 *
 * ============================================================================
 * 5. Locking internals — who takes the lock, how it is released
 * ============================================================================
 *
 * Structure inside ArrayBlockingQueue:
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │  ReentrantLock  lock                                 │
 *   │      ├── Condition  notFull   (producers wait here)  │
 *   │      └── Condition  notEmpty  (consumers wait here)  │
 *   │                                                      │
 *   │  Object[]  items      (circular buffer)              │
 *   │  int       count      (current number of elements)   │
 *   │  int       putIndex, takeIndex                       │
 *   └──────────────────────────────────────────────────────┘
 *
 * How many locks? ONE ReentrantLock shared by every operation.
 * Two Conditions on that lock coordinate producers and consumers.
 *
 * ── put(item) — producer flow ──
 *
 *   Producer calls put(item)
 *       │
 *       ▼
 *   lock.lockInterruptibly()  ─────────── acquire the ONE lock
 *   │
 *   │  while (count == capacity)           queue full?
 *   │      notFull.await() ────────────── RELEASE lock, producer sleeps on notFull
 *   │      (woken later)   ────────────── RE-ACQUIRE lock, re-check count
 *   │
 *   │  items[putIndex] = item              store in circular array
 *   │  putIndex = (putIndex + 1) % capacity
 *   │  count++
 *   │
 *   │  notEmpty.signal() ──────────────── wake ONE consumer waiting on notEmpty
 *   │
 *   lock.unlock() ─────────────────────── release lock
 *
 * ── take() — consumer flow ──
 *
 *   Consumer calls take()
 *       │
 *       ▼
 *   lock.lockInterruptibly()  ─────────── acquire the SAME lock
 *   │
 *   │  while (count == 0)                  queue empty?
 *   │      notEmpty.await() ───────────── RELEASE lock, consumer sleeps on notEmpty
 *   │      (woken later)    ───────────── RE-ACQUIRE lock, re-check count
 *   │
 *   │  item = items[takeIndex]             read from circular array
 *   │  items[takeIndex] = null
 *   │  takeIndex = (takeIndex + 1) % capacity
 *   │  count--
 *   │
 *   │  notFull.signal() ───────────────── wake ONE producer waiting on notFull
 *   │
 *   lock.unlock() ─────────────────────── release lock
 *       │
 *       ▼
 *   return item
 *
 * ── Timeline example (capacity = 2) ──
 *
 *   Time  Thread      Action                        lock held by  queue     count
 *   ────  ─────────   ───────────────────────────   ────────────  ────────  ─────
 *    1    Producer    lock.lock()                   Producer      [ _ , _ ]   0
 *    2    Producer    put(A), signal notEmpty        Producer      [ A , _ ]   1
 *    3    Producer    unlock()                      —
 *    4    Producer    lock.lock()                   Producer      [ A , _ ]
 *    5    Producer    put(B), signal notEmpty        Producer      [ A , B ]   2
 *    6    Producer    unlock()                      —
 *    7    Producer    lock.lock()                   Producer      [ A , B ]   2
 *    8    Producer    count==capacity → await        —             (lock released, producer sleeps)
 *         ── producer WAITING on notFull ──
 *    9    ConsumerA   lock.lock()                   ConsumerA     [ A , B ]
 *   10    ConsumerA   take()→A, signal notFull       ConsumerA     [ _ , B ]   1
 *         ── notFull.signal wakes Producer ──
 *   11    ConsumerA   unlock()                      —
 *   12    Producer    (re-acquires lock)             Producer      [ _ , B ]   1
 *   13    Producer    put(C), signal notEmpty        Producer      [ C , B ]   2
 *   14    Producer    unlock()                      —
 *
 * Key observations:
 * - At step 8, await() RELEASES the lock — that is the only way ConsumerA can enter at step 9.
 * - At step 10, signal() wakes the producer, but it cannot continue until ConsumerA unlocks (11).
 * - At step 12, the producer re-acquires the lock and continues inside put().
 *
 * ── Why one lock, not two? ──
 * ArrayBlockingQueue uses one lock because count, putIndex, and takeIndex are all shared mutable
 * state; one lock keeps them consistent. LinkedBlockingQueue uses two separate locks (putLock and
 * takeLock) because its head and tail are separate linked nodes, so producers and consumers rarely
 * touch the same memory — higher throughput, more complexity.
 *
 * ── Interview one-liner ──
 * ArrayBlockingQueue: one ReentrantLock, two Conditions (notFull / notEmpty). put blocks on notFull
 * when full; take blocks on notEmpty when empty. Each signals the other after modifying the queue.
 *
 * ============================================================================
 * 6. Practical uses (one-liners)
 * ============================================================================
 *
 * - Thread pool work queue: FixedThreadPool can use ArrayBlockingQueue to bound pending tasks.
 * - Rate limiter: bounded capacity naturally throttles producers.
 * - Request buffering: web server queues incoming requests before worker threads process them.
 * - Audit logging: bounded buffer between log-producing threads and a disk-writing consumer.
 *
 * ============================================================================
 * 7. Demo below
 * ============================================================================
 * One producer puts items 1..15 into a queue of capacity 5. Two consumers take items.
 * Because the queue is small, the producer blocks after filling 5 slots until a consumer takes.
 * Each consumer sleeps 800ms per item (simulating slow processing), so you can clearly see the
 * producer pausing mid-way.
 *
 * The program runs until all items are produced and consumed, then exits cleanly via interrupt.
 */
public class ArrayBlockingQueueDemo {

    static final int CAPACITY = 5;
    static final int TOTAL_ITEMS = 15;

    public static void main(String[] args) throws InterruptedException {
        ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(CAPACITY);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= TOTAL_ITEMS; i++) {
                    queue.put(i);
                    System.out.println("[Producer]  put " + i
                            + "  (queue size: " + queue.size() + ")");
                    Thread.sleep(50);
                }
                System.out.println("[Producer]  finished producing all items.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumerA = createConsumer(queue, "ConsumerA");
        Thread consumerB = createConsumer(queue, "ConsumerB");

        producer.start();
        consumerA.start();
        consumerB.start();

        producer.join();

        // Let consumers drain remaining items, then stop them.
        while (!queue.isEmpty()) {
            Thread.sleep(200);
        }
        Thread.sleep(1000);
        consumerA.interrupt();
        consumerB.interrupt();

        System.out.println("[Main]  done.");
    }

    private static Thread createConsumer(ArrayBlockingQueue<Integer> queue, String name) {
        return new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // poll with timeout so the consumer can check the interrupt flag periodically
                    Integer item = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (item != null) {
                        System.out.println("  [" + name + "]  processing " + item + " ...");
                        Thread.sleep(800);
                        System.out.println("  [" + name + "]  done with " + item);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("  [" + name + "]  stopped.");
        }, name);
    }
}

/**
 * [Producer]  put 1  (queue size: 1)
 *   [ConsumerA]  processing 1 ...
 * [Producer]  put 2  (queue size: 1)
 *   [ConsumerB]  processing 2 ...
 * [Producer]  put 3  (queue size: 1)
 * [Producer]  put 4  (queue size: 2)
 * [Producer]  put 5  (queue size: 3)
 * [Producer]  put 6  (queue size: 4)
 * [Producer]  put 7  (queue size: 5)
 *   [ConsumerA]  done with 1
 *   [ConsumerA]  processing 3 ...
 * [Producer]  put 8  (queue size: 5)
 *   [ConsumerB]  done with 2
 *   [ConsumerB]  processing 4 ...
 * [Producer]  put 9  (queue size: 5)
 *   [ConsumerA]  done with 3
 *   [ConsumerA]  processing 5 ...
 * [Producer]  put 10  (queue size: 5)
 *   [ConsumerB]  done with 4
 *   [ConsumerB]  processing 6 ...
 * [Producer]  put 11  (queue size: 5)
 *   [ConsumerA]  done with 5
 *   [ConsumerA]  processing 7 ...
 * [Producer]  put 12  (queue size: 5)
 *   [ConsumerB]  done with 6
 *   [ConsumerB]  processing 8 ...
 * [Producer]  put 13  (queue size: 5)
 *   [ConsumerA]  done with 7
 *   [ConsumerA]  processing 9 ...
 * [Producer]  put 14  (queue size: 5)
 *   [ConsumerB]  done with 8
 *   [ConsumerB]  processing 10 ...
 * [Producer]  put 15  (queue size: 5)
 * [Producer]  finished producing all items.
 *   [ConsumerA]  done with 9
 *   [ConsumerA]  processing 11 ...
 *   [ConsumerB]  done with 10
 *   [ConsumerB]  processing 12 ...
 *   [ConsumerA]  done with 11
 *   [ConsumerA]  processing 13 ...
 *   [ConsumerB]  done with 12
 *   [ConsumerB]  processing 14 ...
 *   [ConsumerA]  done with 13
 *   [ConsumerA]  processing 15 ...
 *   [ConsumerB]  done with 14
 *   [ConsumerA]  done with 15
 * [Main]  done.
 *   [ConsumerA]  stopped.
 *   [ConsumerB]  stopped.
 */
