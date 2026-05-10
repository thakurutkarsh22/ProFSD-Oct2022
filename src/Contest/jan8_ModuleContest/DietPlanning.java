package Contest.jan8_ModuleContest;

import java.util.Scanner;

public class DietPlanning {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[7];
        String[] days = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };
        for (int i = 0; i < 7; i++) {
            arr[i] = sc.nextInt();
        }
        int i = 0;
        while (n > 0) {
            n -= arr[i % 7];
            i++;
        }
        i--;
        System.out.print(days[i % 7]);
        sc.close();
    }
}
