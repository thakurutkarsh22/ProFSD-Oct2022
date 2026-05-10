package Contest.nov20;

import java.util.Scanner;

public class NNContest {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.print(n * (n - 1));
        sc.close();
    }
}
