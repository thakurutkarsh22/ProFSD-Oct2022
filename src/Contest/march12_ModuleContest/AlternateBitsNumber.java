package Contest.march12_ModuleContest;

import java.util.Scanner;

public class AlternateBitsNumber {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.close();
        int prev = n & 1;
        while (n > 0) {
            n >>= 1;
            if ((n & 1) == prev) {
                System.out.print("False");
                return;
            }
            prev = n & 1;
        }
        System.out.print("True");
    }
}
