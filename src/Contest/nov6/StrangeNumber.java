package Contest.nov6;

import java.util.Scanner;

public class StrangeNumber {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int ans = (n - 1) * 9;
        scn.close();
        System.out.println(ans);
    }
}
