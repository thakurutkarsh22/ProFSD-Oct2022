package Contest.march12_ModuleContest;

import java.util.Scanner;

public class Flames {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        String s1 = sc.nextLine();
        String s2 = sc.nextLine();
        sc.close();

        int[] ch = new int[26];
        int[] ch1 = new int[26];

        for (char c : s1.toCharArray()) {
            ch[c - 'a']++;
        }
        for (char c : s2.toCharArray()) {
            if (ch[c - 'a'] != 0) {
                ch[c - 'a']--;
            } else
                ch1[c - 'a']++;
        }

        int sum = 0;
        for (int i : ch) {
            if (i != -1)
                sum += i;
        }
        for (int i : ch1) {
            sum += i;
        }

        String ans = "";
        switch (sum % 6) {
            case 1:
                ans = "Friends";
                break;
            case 2:
                ans = "Love";
                break;
            case 3:
                ans = "Affection";
                break;
            case 4:
                ans = "Marriage";
                break;
            case 5:
                ans = "Enemy";
                break;
            case 0:
                ans = "Siblings";
                break;
        }

        System.out.print(ans);
    }
}
