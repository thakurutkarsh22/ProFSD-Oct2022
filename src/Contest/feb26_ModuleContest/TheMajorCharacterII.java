package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class TheMajorCharacterII {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        // int n = sc.nextInt();
        sc.nextLine();
        String s = sc.nextLine();
        int max = -1;
        int[] ch = new int[128];
        for (char c : s.toCharArray()) {
            ch[c]++;
        }
        int mcnt = -1;
        for (int i = 97; i < 128; i++) {
            if (ch[i] > mcnt) {
                mcnt = ch[i];
            }
        }
        for (int i = 97; i < 128; i++) {
            if (mcnt == ch[i]) {
                max = Math.max(max, s.lastIndexOf((char) i));
            }
        }
        System.out.print(max);
        sc.close();
    }
}
