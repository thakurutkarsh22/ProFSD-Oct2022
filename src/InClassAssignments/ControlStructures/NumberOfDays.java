package InClassAssignments.ControlStructures;

import java.util.Scanner;

public class NumberOfDays {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        numberofdays(n);
    }

    public static void numberofdays(int M){
        switch(M) {
            case 1:
                System.out.println(31);
                break;
            case 2:
                System.out.println(28);
                break;
            case 3:
                System.out.println(31);
                break;
            case 4:
                System.out.println(30);
                break;
            case 5:
                System.out.println(31);
                break;
            case 6:
                System.out.println(30);
                break;
            case 7:
                System.out.println(31);
                break;
            case 8:
                System.out.println(31);
                break;
            case 9:
                System.out.println(30);
                break;
            case 10:
                System.out.println(31);
                break;
            case 11:
                System.out.println(30);
                break;
            case 12:
                System.out.println(31);
                break;
            default:
                System.out.println("no month");
                break;
        }
    }
}
