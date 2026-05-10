package Contest.nov13;

public class KOperations {
    public static void main(String[] args) {
        long ans = kOperations(3, 5);
        System.out.println(ans);
    }

    public static long kOperations(long N, long K) {
        // Enter your code here
        for (int i = 0; i < K; i++) {
            long num = N;
            // getting first digit
            while (num >= 10) {
                num /= 10; // 1
            }
            N *= num;
            // if first digit will be 1 then
            // no need to run loop further
            if (num == 1)
                break;
        }
        return N;
    }

}