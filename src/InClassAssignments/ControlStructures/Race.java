package InClassAssignments.ControlStructures;

import java.util.Scanner;

public class Race {
    public static void main(String[] args) {

        Scanner scn = new Scanner(System.in);
        int N = scn.nextInt();
        int S = scn.nextInt();
        int C = scn.nextInt();

        char ch = Race(N, S, C);
        System.out.println(ch);
    }

    public static char Race(int N,int S,int C){

        int distance_between_saske_and_common = Math.abs( C-S ); // distance is 2
        int distance_between_naruto_and_common = Math.abs(C - N); // distance greater 3

        if(distance_between_saske_and_common > distance_between_naruto_and_common) {
            return 'N';
        } else if(distance_between_saske_and_common < distance_between_naruto_and_common) {
            return 'S';
        } else {
            char result = 'D';
            return result;
        }
    }
}
