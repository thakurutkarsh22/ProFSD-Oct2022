package Contest.march12_ModuleContest;

import java.util.Scanner;

public class CharacterCountParadigm {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        // int n = sc.nextInt();
        sc.nextLine();
        String s = sc.next();
        sc.close();

        int[] ch = new int[26];
        for (char c : s.toCharArray()) {
            ch[c - 'a']++;
        }
        int max = 0, ans = 0;
        for (int i = 0; i < 26; i++) {
            if (ch[i] >= max) {
                max = ch[i];
                ans = i;
            }
        }
        System.out.print((char) (ans + 'a'));
    }
}
