package InClassAssignments.ControlStructures;

import java.util.Scanner;

public class StudentsGrade {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int marks1 = scn.nextInt();
        int marks2 = scn.nextInt();
        int marks3 = scn.nextInt();
        int marks4 = scn.nextInt();
        int marks5 = scn.nextInt();

        int average = (marks1 + marks2 + marks3 + marks4 + marks5)/5;

        if(average >=80) {
            System.out.println("A");
        } else if(average >=60 && average < 80) {
            System.out.println("B");
        } else if(average >=40 && average < 60) {
            System.out.println("C");
        } else {
            System.out.println("D");
        }
    }
}
