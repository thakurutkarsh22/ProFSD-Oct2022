package Contest.dec4;

import java.util.Scanner;

public class NewtonSchoolProblem {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.close();
        for (int i = 1; i <= n; i++) {
            if (i % 15 == 0) {
                System.out.print("NewtonSchool ");
            } else if (i % 3 == 0) {
                System.out.print("Newton ");
            } else if (i % 5 == 0) {
                System.out.print("School ");
            } else {
                System.out.print(i + " ");
            }
        }
    }
}
