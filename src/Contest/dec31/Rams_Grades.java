package Contest.dec31;

import java.util.*;

public class Rams_Grades {
    public static void main(String[] args) {
        // Your code here
        boolean isPrime[] = new boolean[13];
        isPrime[2] = isPrime[3] = isPrime[5] = isPrime[7] = isPrime[11] = true;

        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int x = sc.nextInt();
            int y = sc.nextInt();
            System.out.println((isPrime[x + y]) ? "RAHUL" : "RAVI");
        }
        sc.close();
    }
}
