package InClassAssignments.ControlStructures;

import java.util.Scanner;

public class OddOrEven {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        System.out.println(n%2 == 0 ? "Even" : "Odd");
    }
}
