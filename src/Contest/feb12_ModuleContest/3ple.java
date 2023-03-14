package Contest.feb12_ModuleContest;

import java.util.Scanner;

class A3ple {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.print(n % 3 == 0 ? "Yes" : "No");
        sc.close();
    }
}
