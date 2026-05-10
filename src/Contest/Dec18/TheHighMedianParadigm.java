package Contest.Dec18;

import java.util.*;

public class TheHighMedianParadigm {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int m = sc.nextInt();
        if (n == 1) {
            System.out.print(m);
        } else {
            int med = (int) Math.ceil(n / 2.0);
            med--;
            int median = m / (n - med);
            System.out.print((int) median);
        }
        sc.close();
    }
}
