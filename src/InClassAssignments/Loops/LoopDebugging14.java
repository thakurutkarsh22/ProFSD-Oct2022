package InClassAssignments.Loops;

import java.util.Scanner;

public class LoopDebugging14 {
    public static void main(String[] args) {

        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        For_Loop(n);
    }

    public static void For_Loop(int n){
        for(int i=2;i<=n;i++){
            System.out.print(i+" ");
            i+=2;
        }
    }
}
