package Contest.jan8;

import java.util.Scanner;

public class MaxScoreInQuiz {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int m = sc.nextInt();
        int max = 0;
        for (int i = 0; i < n; i++) {
            int cnt = 0;
            for (int j = 0; j < m; j++) {
                int input = sc.nextInt();
                if (input == 1) {
                    cnt++;
                }
            }
            max = (max > cnt) ? max : cnt;
        }
        System.out.println(max * 10);
        sc.close();
    }
}
