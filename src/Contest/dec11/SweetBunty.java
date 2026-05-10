package Contest.dec11;

import java.util.Scanner;

public class SweetBunty {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int x = sc.nextInt();
        int base = sc.nextInt();
        sc.close();
        base -= 2;
        int ans = base / 2;
        ans = ans * (ans + 1) / 2;
        System.out.println((ans < x) ? ans : x);
    }
}
