package Contest.feb12_ModuleContest;

import java.util.Scanner;

public class CompressString {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > -1) {
            String s = sc.nextLine();
            int n = s.length();
            if (n == 0) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            int cnt = 1;
            for (int i = 1; i < n; i++) {
                if (s.charAt(i - 1) == s.charAt(i)) {
                    cnt++;
                } else {
                    sb.append(s.charAt(i - 1) + "" + cnt);
                    cnt = 1;
                }
            }
            sb.append(s.charAt(n - 1) + "" + cnt);
            System.out.println(sb.toString());
            sc.close();
        }
    }
}
