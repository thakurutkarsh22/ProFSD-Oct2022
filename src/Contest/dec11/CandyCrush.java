package Contest.dec11;

import java.util.Scanner;

public class CandyCrush {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        long q = sc.nextLong();
        for (int i = 0; i < q; i++) {
            long n = sc.nextLong();
            long totalCost = 0;
            while (n > 0) {
                long g = n / 3;
                if (n % 3 != 0)
                    g++;
                // System.out.println("g= "+g);
                totalCost += (g * g);
                n -= g;
            }
            System.out.println(totalCost);
        }
        sc.close();
    }
}
