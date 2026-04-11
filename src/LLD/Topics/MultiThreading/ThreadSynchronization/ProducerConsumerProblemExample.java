package LLD.Topics.MultiThreading.ThreadSynchronization;

import java.util.LinkedList;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                  Producer-Consumer Problem                             ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is the producer-consumer problem?
 * ============================================================================
 *
 * A classic concurrency pattern where:
 * - Producer(s) generate data and put it into a shared bounded buffer.
 * - Consumer(s) take data from the buffer and process it.
 * - They must coordinate: producer waits when buffer is FULL,
 *   consumer waits when buffer is EMPTY.
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   Producer                  Bounded Buffer (capacity=5)              Consumer
 *   ────────                  ──────────────────────────               ────────
 *                             ┌───┬───┬───┬───┬───┐
 *   produce() ──put──►        │ 0 │ 1 │ 2 │ 3 │ 4 │        ◄──take── consume()
 *                             └───┴───┴───┴───┴───┘
 *
 *   Buffer FULL:   producer calls wait()   --> sleeps until consumer takes
 *   Buffer EMPTY:  consumer calls wait()   --> sleeps until producer puts
 *   After put/take: thread calls notifyAll() --> wakes the other side
 *
 *   Flow (simplified):
 *
 *   Producer                                Consumer
 *   ────────                                ────────
 *   synchronized(lock)                      (waiting for lock)
 *   │  buffer full? YES -> wait()
 *   │  (releases lock, sleeps)
 *   │                                       synchronized(lock)
 *   │                                       │  buffer empty? NO
 *   │                                       │  removeFirst()
 *   │                                       │  notifyAll() --> wakes producer
 *   │                                       release lock
 *   │  (woken up, re-acquires lock)
 *   │  buffer full? NO
 *   │  add item
 *   │  notifyAll() --> wakes consumer
 *   release lock
 *
 * ============================================================================
 * 3. Practical uses (one-liners)
 * ============================================================================
 *
 * - Message queues (Kafka, RabbitMQ are distributed versions of this pattern).
 * - Thread pool work queues (ExecutorService uses a BlockingQueue internally).
 * - Logging frameworks: log events are produced by app threads, consumed by a writer thread.
 * - Pipeline processing: stage 1 produces output, stage 2 consumes it.
 *
 * ============================================================================
 * 4. Code demo below
 * ============================================================================
 */
public class ProducerConsumerProblemExample {

    public static void main(String[] args) {
        ProducerConsumerProblemExample outer = new ProducerConsumerProblemExample();
        Worker worker = new Worker(5, 0);

        Thread producer = new Thread(() -> {
            try {
                worker.produce();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                worker.consume();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
    }


}

class Worker {

    private int sequence = 0;
    private final Integer top;
    private final Integer bottom;
    private final LinkedList<Integer> container;
    private final Object lock = new Object();

    public Worker(Integer top, Integer bottom) {
        this.top = top;
        this.bottom = bottom;
        this.container = new LinkedList<>();
    }

    public void produce() throws InterruptedException {
        synchronized (lock) {
            while (true) {
                if (container.size() == top) {
                    System.out.println("Container full, waiting for items to be removed ...");
                    lock.wait();
                } else {
                    System.out.println(sequence + " Added to the container");
                    container.add(sequence++);
                    lock.notifyAll();
                }
                Thread.sleep(500);
            }
        }
    }

    public void consume() throws InterruptedException {
        synchronized (lock) {
            while (true) {
                if (container.size() == bottom) {
                    System.out.println("Container empty, waiting for items to be added ... ");
                    lock.wait();
                } else {
                    System.out.println(container.removeFirst() + " removed from the container");
                    lock.notifyAll();
                }
                Thread.sleep(500);
            }
        }
    }
}

/**
 * 0 Added to the container
 * 1 Added to the container
 * 2 Added to the container
 * 3 Added to the container
 * 4 Added to the container
 * Container full, waiting for items to be removed ...
 * 0 removed from the container
 * 1 removed from the container
 * 2 removed from the container
 * 3 removed from the container
 * 4 removed from the container
 * Container empty, waiting for items to be added ...
 * 5 Added to the container
 * ...
 */
