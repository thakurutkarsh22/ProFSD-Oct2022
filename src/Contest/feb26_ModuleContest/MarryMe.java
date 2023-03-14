package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class MarryMe {
    private static boolean isSubsequence(String s, String t) {
        int m = s.length(), n = t.length();
        char[] cs = s.toCharArray();
        char[] ct = t.toCharArray();
        int i = 0, j = 0;
        while (i < n) {
            if (ct[i] == cs[j]) {
                j++;
            }
            i++;
            if (j == m) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int T = sc.nextInt();
        sc.nextLine();
        while (T-- > 0) {
            String s = sc.next();
            String t = sc.next();
            System.out.println(isSubsequence(s, t) || isSubsequence(t, s) ? "#SadLife" : "#DieSingle");
        }
        sc.close();
    }
}
