package Contest.Dec18;

import java.util.Scanner;

public class PackingRectangles {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        long w = sc.nextLong();
        long h = sc.nextLong();
        long n = sc.nextLong();
        sc.close();
        long l = 0, r = 1, mid = 0;

        // checking for minimum range
        while (!check(r, w, h, n)) {
            r *= 2;
        }

        while (l + 1 < r) {
            mid = (r + l) / 2;
            if (check(mid, w, h, n)) {
                r = mid;
            } else {
                l = mid;
            }
        }
        System.out.println(r);
    }

    public static boolean check(long k, long w, long h, long n) {
        return (k / h) * (k / w) >= n;
    }

}
