package InClassAssignments.OOPS;

import java.util.Scanner;

class SumCalculator {
    public int num1 = 0;
    public int num2 = 0;

    SumCalculator(int num1 , int num2) {
        this.num1 = num1;
        this.num2 = num2;
    }

    public int sum() {
        return num1 + num2;
    }

    public int sum2(int a, int b) {
        return a + b;
    }

    public int fromObject(SumCalculator obj1, SumCalculator obj2) {
        int ans1 = obj1.sum();
        int ans2 = obj2.sum();
        return  ans2 + ans1;
    }


}

    /*Problem Statement
    Write a class with the name SumCalculator.
    The class needs two fields (public variables) with names num1 and num2 both of type int.

        Write the following methods (instance methods):
        *Method named sum without any parameters, it needs to return the value of num1 + num2.
        *Method named sum2 with two parameters a, b, it needs to return the value of a + b.
        *  and you have to call sum function for respective object and return sum of both

        NOTE: All methods should be defined as public, NOT public static.
        NOTE: In total, you have to write 3 methods.
        NOTE: Do not add the main method to the solution code.

     */
