package LLD.Topics.MultiThreading.ConcurrentCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
 * Unsynchronized ArrayList + concurrent writers (intentionally broken demo)
 *
 * ArrayList is not thread-safe. Two threads calling add() at the same time races on the internal
 * array, element count, and modification logic — there is no happens-before between their updates.
 *
 * Expected output: there is NO single correct answer.
 * - list.size() is often less than 2000 (lost updates / inconsistent internal state).
 * - You may get an exception (e.g. ArrayIndexOutOfBoundsException) or rarely a corrupt state
 *   depending on JVM and timing.
 * - Occasionally you might see 2000 by luck; do not rely on that.
 *
 * If the list were properly guarded (e.g. Collections.synchronizedList(new ArrayList<>()),
 * explicit synchronized blocks on a shared lock, or java.util.concurrent classes suited to your
 * access pattern), you would expect size 2000 after both threads finish.
 */
public class SynchronizedCollection {

    public static void main(String[] args) throws InterruptedException {
//        List<Integer> list = new ArrayList<>();

        List<Integer> list = Collections.synchronizedList(new ArrayList<>()); // list size : 20000

        Thread one = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                list.add(i);
            }
        });

        Thread two = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                list.add(i);
            }
        });

        one.start();
        two.start();

        one.join();
        two.join();

        System.out.println("list size : " + list.size()); // list size : 18033
    }


}
