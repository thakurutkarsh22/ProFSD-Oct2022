package Contest.jan15;

import java.util.Scanner;

public class FindUnique {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int xor = 0;
        for (int i = 0; i < n; i++) {
            xor ^= sc.nextInt();
        }
        System.out.print(xor);
        sc.close();
    }
}
