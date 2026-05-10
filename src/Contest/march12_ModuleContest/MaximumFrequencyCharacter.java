package Contest.march12_ModuleContest;

import java.util.Scanner;

public class MaximumFrequencyCharacter {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        // int n = sc.nextInt();
        sc.nextLine();
        String s = sc.next();
        int[] ch = new int[26];
        for (char c : s.toCharArray()) {
            ch[c - 'a']++;
        }
        sc.close();
        int max = -1, ans = s.charAt(0) - 'a';
        for (int i = 0; i < 26; i++) {
            if (ch[i] != 0 && ch[i] > max) {
                max = ch[i];
                ans = i;
            }
        }
        int cnt = 0;
        for (int i : ch) {
            if (i == max) {
                cnt++;
                if (cnt == 2) {
                    System.out.print(-1);
                    return;
                }
            }
        }
        System.out.print((char) (ans + 'a'));
    }
}
