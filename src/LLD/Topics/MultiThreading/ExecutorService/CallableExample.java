package LLD.Topics.MultiThreading.ExecutorService;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/*
 * Callable vs Runnable:
 * - Runnable.run() returns void and cannot throw checked exceptions from the interface contract.
 * - Callable<V>.call() returns V and may throw Exception; use with ExecutorService.submit() to get
 *   a Future<V> for the result (or cancellation / exceptions).
 */
public class CallableExample {

    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> future = executorService.submit(new ReturnValueTask());
            System.out.println("Main thread execution completed before get");

//            Integer result = future.get();
//            System.out.println("Callable returned: " + result);

            future.cancel(true);
            System.out.println(future.isDone() +  " : is done");

            System.out.println("Main thread execution completed");



        } finally {
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}

class ReturnValueTask implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        Thread.sleep(5000);
        return 10;
    }
}
