package LLD.Topics.MultiThreading.ConcurrentCollection;


import java.util.concurrent.Exchanger;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                            Exchanger                                   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ============================================================================
 * 1. What is an Exchanger?
 * ============================================================================
 *
 * A synchronization point where exactly TWO threads can swap (exchange) objects
 * with each other. Each thread presents an object on entry to the exchange()
 * method and receives the object presented by the other thread in return.
 *
 *   Exchanger<T> exchanger = new Exchanger<>();
 *   T received = exchanger.exchange(myData);  // blocks until partner arrives
 *
 * ============================================================================
 * 2. How it works -- diagram
 * ============================================================================
 *
 *   Thread 1 (has data=10)              Thread 2 (has data=20)
 *   ───────────────────────             ───────────────────────
 *   exchanger.exchange(10)              (sleeping 3s...)
 *   │                                   │
 *   │ BLOCKED -- waiting for            │
 *   │ a partner to arrive               │
 *   │                                   │
 *   │                                   exchanger.exchange(20)
 *   │                                   │
 *   │◄─────────── SWAP ───────────────►│
 *   │          10 ◄──────► 20           │
 *   │                                   │
 *   ▼                                   ▼
 *   received = 20                       received = 10
 *
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │                      Exchanger                                │
 *   │                                                                │
 *   │   Thread 1 arrives first ──► parks (waits)                    │
 *   │                                                                │
 *   │   Thread 2 arrives ──► both threads swap data simultaneously  │
 *   │                                                                │
 *   │   Both resume with each other's data                          │
 *   └────────────────────────────────────────────────────────────────┘
 *
 * ============================================================================
 * 3. Timeline for this program
 * ============================================================================
 *
 *   Time     Thread 1                        Thread 2                   Exchanger
 *   ─────    ──────────────────────          ──────────────────────     ──────────
 *   0.0s     exchange(10) BLOCKED            sleeping 3s...             1 waiting
 *   ~3.0s    still blocked                   exchange(20)               SWAP!
 *            received = 20                   received = 10              done
 *            prints "received 20"            prints "received 10"
 *
 * ============================================================================
 * 4. Key characteristics
 * ============================================================================
 *
 * - Exactly TWO threads participate (not more, not fewer).
 * - exchange() blocks the first thread until the second thread calls exchange().
 * - Both threads swap atomically -- neither sees a half-exchange.
 * - Timed overload: exchange(data, timeout, unit) to avoid waiting forever.
 * - If only one thread ever calls exchange(), it blocks indefinitely.
 *
 * ============================================================================
 * 5. Can we have more than 2 threads?
 * ============================================================================
 *
 * NO. Exchanger is strictly for exactly 2 threads -- a pair-wise rendezvous.
 *
 * If a 3rd thread calls exchange(), it does NOT join the existing pair. It starts
 * waiting for a NEW partner. The first two threads pair up and swap; the third
 * thread is left blocked forever (unless a 4th thread arrives to pair with it).
 *
 *   Thread A: exchange(10) ──┐
 *                             ├── PAIR 1: swap 10 <-> 20  (OK)
 *   Thread B: exchange(20) ──┘
 *
 *   Thread C: exchange(30) ──── BLOCKED FOREVER (no partner!)
 *
 * If you need more than 2 threads to exchange data, use:
 *
 *   Need                                  Use
 *   ─────────────────────────────────     ──────────────────────────────────
 *   N threads wait, then proceed          CyclicBarrier (+ barrier action)
 *   N threads signal completion           CountDownLatch
 *   Any thread hand-off (no swap)         SynchronousQueue or BlockingQueue
 *   N threads share a common result       Phaser or shared ConcurrentHashMap
 *
 * In short: Exchanger = exactly 2 threads, always. A phone call, not a conference call.
 *
 * ============================================================================
 * 6. Exchanger vs other synchronizers
 * ============================================================================
 *
 *   Synchronizer       Threads   Purpose
 *   ─────────────────  ────────  ──────────────────────────────────────────
 *   Exchanger          2         Swap data between exactly two threads
 *   CountDownLatch     N -> 1    N threads signal, one (or more) waits
 *   CyclicBarrier      N -> N    N threads wait for each other, then proceed
 *   SynchronousQueue   1 -> 1    Hand-off: producer gives, consumer takes (no swap)
 *
 * ============================================================================
 * 7. Practical uses (one-liners)
 * ============================================================================
 *
 * - Double buffering: one thread fills buffer A while the other processes buffer B, then they swap.
 * - Genetic algorithms: two threads each evolve a population, then exchange chromosomes.
 * - Pipeline handoff: stage 1 produces a batch, exchanges it with stage 2's empty container.
 * - Testing: test thread provides mock data, system thread exchanges real results back.
 *
 * ============================================================================
 * 8. Code demo below
 * ============================================================================
 */
public class ExchangerDemo {
    public static void main(String[] args) {
        Exchanger<Integer> exchanger = new Exchanger<>();

        Thread one = new Thread(new FirstThread(exchanger));
        Thread two = new Thread(new SecondThread(exchanger));

        one.start();
        two.start();
    }
}

class FirstThread implements Runnable {

    private final Exchanger<Integer> exchanger;

    public FirstThread(Exchanger<Integer> exchanger) {
        this.exchanger = exchanger;
    }

    @Override
    public void run() {
        int dataToSend = 10;
        System.out.println("First thread is sending data " + dataToSend);
        try {
            Integer recievedData = exchanger.exchange(dataToSend);
            System.out.println("First thread recieved Data " + recievedData);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}


class SecondThread implements Runnable {

    private final Exchanger<Integer> exchanger;

    public SecondThread(Exchanger<Integer> exchanger) {
        this.exchanger = exchanger;
    }

    @Override
    public void run() {

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        int dataToSend = 20;
        System.out.println("Second thread is sending data " + dataToSend);
        try {
            Integer recievedData = exchanger.exchange(dataToSend);
            System.out.println("Second thread recieved Data " + recievedData);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}


/**
 * First thread is sending data 10
 * Second thread is sending data 20
 * Second thread recieved Data 10
 * First thread recieved Data 20
 */
