package Contest.dec11;

import java.util.Scanner;

public class FactorialRecursion {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.println(Factorial(n));
        sc.close();
    }

    static int Factorial(int N) {
        // Enter your code here
        if (N == 0 || N == 1)
            return 1;
        return N * Factorial(N - 1);
    }
}
