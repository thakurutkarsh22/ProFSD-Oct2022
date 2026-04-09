package LLD.Topics.MultiThreading.Sequential;

public class SequentialExecutionDemo {
    public static void main(String[] args) {
        demo1();
        demo2();
    }

    private static void demo1() {
        for (int i = 0; i < 5; i++) {
            System.out.println("From demo 1 " + i);
        }
    }

    private static void demo2() {
        for (int i = 0; i < 5; i++) {
            System.out.println("From demo 2 " + i);
        }
    }
}
// Single thread will be taking care of demo 1 first and than demo2
