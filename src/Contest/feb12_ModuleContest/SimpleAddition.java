package Contest.feb12_ModuleContest;

import java.util.Scanner;

public class SimpleAddition {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        String s = sc.next();
        int sum = 0;
        for (char ch : s.toCharArray()) {
            sum += ch - '0';
        }
        System.out.print(sum);
        sc.close();
    }
}
