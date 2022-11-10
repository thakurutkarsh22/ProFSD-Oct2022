package InClassAssignments.Loops;

import java.util.*;

public class FiniteInput {
    public static void main (String[] args) {
        // Your code here

        Scanner scn = new Scanner(System.in);

        // solution 1: -->

        // int value = scn.nextInt(); // 0 or any number .....
        // System.out.print(value +" ");

        // while(value !=0) {
        //     value = scn.nextInt();
        //     System.out.print(value + " ");
        // }


        // solution2 --->
        int value;

        do {
            value = scn.nextInt();
            System.out.print(value + " ");
        } while (value != 0);
    }
}
