package Contest.feb12_ModuleContest;

import java.util.Scanner;

public class AsOrBs {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        String s = sc.next();
        int cnt = 0, max = 0;
        for (char c : s.toCharArray()) {
            if (c == 'A') {
                cnt++;
            } else {
                max = Math.max(max, cnt);
                cnt = 0;
            }
        }
        System.out.print(Math.max(max, cnt));
        sc.close();
    }
}
