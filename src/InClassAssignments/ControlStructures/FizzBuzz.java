package InClassAssignments.ControlStructures;

import java.util.Scanner;

public class FizzBuzz {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int N = scn.nextInt();

        for (int i=1; i<=N; i++)
        {
            if (i%5==0) {
                System.out.print("Buzz ");
            }
            else if (i%3==0){
                System.out.print("Fizz ");
            } else if (i%15==0) {
                System.out.print("FizzBuzz ");
            }
            else {
                System.out.print(i+" ");
            }
        }
    }
}

//1
// 2
// Fizz
// 4
// 5 -> buzz
//6 -> fizz

// 15 -> FizzBuzz.
