package Contest.dec11;

import java.util.Scanner;

public class SquigglyBrackets {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.print(CombinationsOfBrackets(n));
        sc.close();
    }

    public static int CombinationsOfBrackets(int n) {
        if (n == 0 || n == 1)
            return 1;
        int result = 0;
        for (int i = 0; i < n; i++) {
            result += CombinationsOfBrackets(i) * CombinationsOfBrackets(n - i - 1);
        }
        return result;
    }
}
