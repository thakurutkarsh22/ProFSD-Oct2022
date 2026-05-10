package Contest.feb26_ModuleContest;

import java.util.Scanner;

public class MarioAndBullet {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int xSpeed = sc.nextInt();
            int yDistance = sc.nextInt();
            int zTime = sc.nextInt();
            int time = yDistance % xSpeed == 0 ? yDistance / xSpeed : yDistance / xSpeed + 1;
            System.out.println(zTime - time > 0 ? zTime - time : 0);
        }
        sc.close();
    }
}
