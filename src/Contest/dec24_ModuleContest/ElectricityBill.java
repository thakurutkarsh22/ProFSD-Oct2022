package Contest.dec24_ModuleContest;

import java.util.*;

public class ElectricityBill {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.close();
        double total = 0.0;
        if (n <= 50) {
            total = n * (0.5);
        } else if (n <= 150) {
            total = 50 * (0.5) + (n - 50) * (0.75);
        } else if (n <= 250) {
            total = 50 * (0.5) + 100 * (0.75) + (n - 250) * (1.25);
        } else {
            total = 50 * (0.5) + 100 * (0.75) + 100 * (1.25) + (n - 250) * (1.5);
        }
        double additionalCharge = total * 20 / 100;
        total += additionalCharge;
        System.out.printf("%.2f", total);
    }
}
