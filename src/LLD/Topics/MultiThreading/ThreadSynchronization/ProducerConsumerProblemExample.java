package LLD.Topics.MultiThreading.ThreadSynchronization;

import java.util.LinkedList;

/**
 * Classic producer–consumer using a bounded buffer, one lock, wait/notify, and pacing via sleep.
 * Matches the threaded course example: capacity {@code top}, empty when size == {@code bottom} (0).
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
 * 6 Added to the container
 * 7 Added to the container
 * 8 Added to the container
 * 9 Added to the container
 * Container full, waiting for items to be removed ...
 * 5 removed from the container
 * 6 removed from the container
 * 7 removed from the container
 * 8 removed from the container
 * 9 removed from the container
 * Container empty, waiting for items to be added ...
 */
