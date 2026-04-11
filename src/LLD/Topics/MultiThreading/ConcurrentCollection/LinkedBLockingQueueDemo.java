package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/*
 * LinkedBlockingQueue — deep dive
 *
 * ============================================================================
 * 1. What it is
 * ============================================================================
 * - A BlockingQueue backed by linked nodes (singly-linked list internally).
 * - Optionally bounded: you can pass a capacity at construction, or omit it and get
 *   Integer.MAX_VALUE as the default cap (effectively unbounded — careful with memory!).
 * - Thread-safe for any number of concurrent producers and consumers.
 *
 * ============================================================================
 * 2. Two-lock design (the big difference from ArrayBlockingQueue)
 * ============================================================================
 *
 * ArrayBlockingQueue uses ONE ReentrantLock for everything. LinkedBlockingQueue uses TWO:
 *
 *   ┌───────────────────────────────────────────────────────────┐
 *   │  ReentrantLock  putLock   ── guards the tail (inserts)   │
 *   │      └── Condition  notFull   (producers wait here)      │
 *   │                                                          │
 *   │  ReentrantLock  takeLock  ── guards the head (removals)  │
 *   │      └── Condition  notEmpty  (consumers wait here)      │
 *   │                                                          │
 *   │  AtomicInteger  count     ── shared between both locks   │
 *   │                                                          │
 *   │  Node  head ──> ... ──> tail                             │
 *   └───────────────────────────────────────────────────────────┘
 *
 * Why two locks?
 * - Producers only touch the tail; consumers only touch the head.
 * - With separate locks, a put() and a take() can run truly in PARALLEL — they don't block
 *   each other (unless one needs to signal the other's condition).
 * - This gives higher throughput than ArrayBlockingQueue under heavy contention.
 *
 * Trade-off: per-element Node allocation (GC pressure) and slightly more complex signalling
 * (count is AtomicInteger so both locks can read/update it safely).
 *
 * ============================================================================
 * 3. put() — producer flow (simplified)
 * ============================================================================
 *
 *   Producer calls put(item)
 *       │
 *       ▼
 *   putLock.lockInterruptibly()  ────────── acquire PUT lock (take lock NOT needed)
 *   │
 *   │  while (count.get() == capacity)      queue full?
 *   │      notFull.await() ─────────────── RELEASE putLock, sleep
 *   │      (woken later)   ─────────────── RE-ACQUIRE putLock, re-check
 *   │
 *   │  enqueue(node)                        link new node at tail
 *   │  c = count.getAndIncrement()
 *   │
 *   │  if (c + 1 < capacity)
 *   │      notFull.signal() ────────────── wake another waiting producer (cascading signal)
 *   │
 *   putLock.unlock()
 *   │
 *   │  if (c == 0)                          queue was empty before this put
 *   │      takeLock.lock()
 *   │      notEmpty.signal() ───────────── wake a waiting consumer
 *   │      takeLock.unlock()
 *
 * ============================================================================
 * 4. take() — consumer flow (simplified)
 * ============================================================================
 *
 *   Consumer calls take()
 *       │
 *       ▼
 *   takeLock.lockInterruptibly()  ──────── acquire TAKE lock (put lock NOT needed)
 *   │
 *   │  while (count.get() == 0)             queue empty?
 *   │      notEmpty.await() ────────────── RELEASE takeLock, sleep
 *   │      (woken later)    ────────────── RE-ACQUIRE takeLock, re-check
 *   │
 *   │  x = dequeue()                        unlink head node
 *   │  c = count.getAndDecrement()
 *   │
 *   │  if (c > 1)
 *   │      notEmpty.signal() ───────────── wake another waiting consumer (cascading signal)
 *   │
 *   takeLock.unlock()
 *   │
 *   │  if (c == capacity)                   queue was full before this take
 *   │      putLock.lock()
 *   │      notFull.signal() ────────────── wake a waiting producer
 *   │      putLock.unlock()
 *   │
 *   return x
 *
 * ============================================================================
 * 5. ArrayBlockingQueue vs LinkedBlockingQueue (interview cheat sheet)
 * ============================================================================
 *
 *   Feature               ArrayBlockingQueue        LinkedBlockingQueue
 *   ───────────────────   ───────────────────────   ───────────────────────
 *   Backing store         fixed Object[] array      linked nodes
 *   Locks                 1 ReentrantLock           2 ReentrantLocks (put + take)
 *   Throughput            lower under contention    higher (put & take can overlap)
 *   Memory                pre-allocated array       per-element Node alloc (GC cost)
 *   Bounded by default?   always (must pass cap)    optional (default MAX_VALUE)
 *   Fairness option?      yes (constructor flag)    no
 *
 * ── Interview one-liner ──
 * LinkedBlockingQueue: two locks (putLock + takeLock) so producers and consumers rarely block each
 * other. Higher throughput than ArrayBlockingQueue under contention, at the cost of per-node GC.
 *
 * ============================================================================
 * 6. Practical uses (one-liners)
 * ============================================================================
 *
 * - ExecutorService default queue: newFixedThreadPool uses an unbounded LinkedBlockingQueue.
 * - High-throughput messaging: producer and consumer rarely contend (two separate locks).
 * - Log aggregation: multiple app threads produce log events, a single writer thread drains.
 * - Task scheduling: optionally bounded queue prevents memory blowup under sustained load.
 *
 * ============================================================================
 * 7. Demo below
 * ============================================================================
 * Bounded LinkedBlockingQueue(capacity 4). One producer puts 12 items, two consumers process them.
 * You can see the producer block when all 4 slots are full, exactly like ArrayBlockingQueue —
 * the difference is internal (two locks, not one).
 */
public class LinkedBLockingQueueDemo {

    static final int CAPACITY = 4;
    static final int TOTAL_ITEMS = 12;

    public static void main(String[] args) throws InterruptedException {
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(CAPACITY);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= TOTAL_ITEMS; i++) {
                    queue.put(i);
                    System.out.println("[Producer]  put " + i
                            + "  (queue size: " + queue.size() + ")");
                    Thread.sleep(80);
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

        while (!queue.isEmpty()) {
            Thread.sleep(200);
        }
        Thread.sleep(1000);
        consumerA.interrupt();
        consumerB.interrupt();

        System.out.println("[Main]  done.");
    }

    private static Thread createConsumer(LinkedBlockingQueue<Integer> queue, String name) {
        return new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Integer item = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (item != null) {
                        System.out.println("  [" + name + "]  processing " + item + " ...");
                        Thread.sleep(700);
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
