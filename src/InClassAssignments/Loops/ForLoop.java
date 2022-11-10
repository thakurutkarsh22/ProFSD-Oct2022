package InClassAssignments.Loops;

import java.util.Scanner;

public class ForLoop {
    public static void main(String[] args) {

        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        For_Loop(n);
    }

    public static void For_Loop(int n){

        for(int i=1; i <=n; i++) {

//            SOLUTION1:-

            // String decision = (i%2 == 0) ? "even" + " " : "odd" + " ";
            // System.out.print(decision);


//            SOLUTION2:-
            int value = i%2; // 0 or 1

            switch(value) {
                case 0:
                    System.out.print("even ");
                    break;
                case 1:
                    System.out.print("odd ");
                    break;
            }
        }


    }
}
