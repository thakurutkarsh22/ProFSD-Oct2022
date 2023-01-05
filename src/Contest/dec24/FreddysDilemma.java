package Contest.dec24;

import java.util.*;

public class FreddysDilemma {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int x = sc.nextInt();
            int y = sc.nextInt();
            int m = sc.nextInt();
            System.out.println(((x * m) < y) ? "YES" : "NO");
        }
        sc.close();
    }
}
