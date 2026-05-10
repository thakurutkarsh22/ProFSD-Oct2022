package Contest.feb12_ModuleContest;

import java.util.Scanner;

public class NumberOfOccurrence {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        String s = sc.next();
        char ch = sc.next().charAt(0);
        int cnt = 0;
        for (char c : s.toCharArray()) {
            if (c == ch) {
                cnt++;
            }
        }
        System.out.print(cnt);
        sc.close();
    }
}
