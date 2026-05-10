package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class Consecutives {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int q = sc.nextInt();
        while (q-- > 0) {
            int x = sc.nextInt();
            int cnt = 0;
            while (x > 0) {
                cnt++;
                if (cnt == 2) {
                    break;
                }
                x &= (x - 1);
            }
            System.out.println(cnt > 1 ? "Yes" : "No");
        }
        sc.close();
    }
}
