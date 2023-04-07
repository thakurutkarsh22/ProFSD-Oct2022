package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class HappyAna {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.close();
        int cnt = 0;
        while (n != 0) {
            cnt++;
            n &= (n - 1);
            if (cnt == 2) {
                System.out.print("Sad");
                return;
            }
        }
        System.out.print("Happy");
    }
}
