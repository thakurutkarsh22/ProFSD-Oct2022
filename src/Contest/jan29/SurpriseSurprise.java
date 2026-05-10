package Contest.jan29;

import java.util.Scanner;

public class SurpriseSurprise {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int x = sc.nextInt();
            int cnt = 0;
            while (x != 0) {
                cnt++;
                x &= (x - 1);
            }
            System.out.println(cnt);
        }
        sc.close();
    }
}
