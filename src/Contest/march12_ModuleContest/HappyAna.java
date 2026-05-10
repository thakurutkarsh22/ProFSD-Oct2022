package Contest.march12_ModuleContest;

import java.util.Scanner;

public class HappyAna {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.close();
        System.out.print((n & (n - 1)) == 0 ? "Happy" : "Sad");
    }
}
