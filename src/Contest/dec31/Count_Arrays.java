package Contest.dec31;

import java.util.*;

public class Count_Arrays {
    static final int MAX_N = 1000000 + 14;
    static List<Integer> primes = new ArrayList<>();
    static boolean[] isNotPrime = new boolean[MAX_N];
    static int p;

    // Recursive function that generates all possible products of prime numbers that
    // can represent p,
    // given a number n of prime numbers to use and a variable z that indicates
    // whether to consider
    // prime numbers or prime squares.
    static int bt(int n, int z, int mul) {
        if (n == 0) {
            // If n is 0, the product consists of only 1 prime number or prime square.
            return 1;
        }
        int ans = 0;
        for (int pr : primes) {
            // Try to append a prime number or a prime square to the product.
            if (z == 1) {
                // Append a prime number.
                if (pr * mul > p) {
                    // If the product is already greater than p, there is no point in continuing.
                    break;
                }
                // Increment the answer by the number of ways to represent p using n-1 prime
                // numbers.
                ans += bt(n - 1, 2, mul * pr);
            } else {
                // Append a prime square.
                if (pr * pr * mul > p) {
                    // If the product is already greater than p, there is no point in continuing.
                    break;
                }
                // Increment the answer by the number of ways to represent p using n-1 prime
                // squares.
                ans += bt(n - 1, 2, mul * pr * pr);
            }
        }
        return ans;
    }

    public static void main(String[] args) {
        // Generate the list of prime numbers using the Sieve of Eratosthenes algorithm.
        for (int i = 2; i < MAX_N; ++i) {
            if (!isNotPrime[i]) {
                primes.add(i);
                for (int j = i; j < MAX_N; j += i) {
                    isNotPrime[j] = true;
                }
            }
        }

        // Read p and q from the standard input.
        Scanner sc = new Scanner(System.in);
        p = sc.nextInt();
        int q = sc.nextInt();

        // Precompute the number of ways to represent p for different values of n.
        int[] ans = new int[21];
        for (int i = 1; i < 21; ++i) {
            ans[i] = bt((i + 1) / 2, 2 - i % 2, 1);
        }

        while (q-- > 0) {
            // Read n from the standard input.
            int n = sc.nextInt();
            // Print the number of ways to represent p using at most n prime numbers or
            // prime squares.
            int sum = 0;
            for (int i = 1; i < Math.min(n + 1, 21); ++i) {
                sum += ans[i];
            }
            System.out.print(sum + " ");
        }
        sc.close();
    }
}
