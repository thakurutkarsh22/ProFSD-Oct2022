package InClassAssignments.Arrays;

import java.util.Scanner;

public class MaxNumbers {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T!=0) {
            // should do our work
            int n = scn.nextInt();
            int[] arr = new int[n];
            // filling the array
            for(int i=0;i<arr.length;i++) {
                arr[i] = scn.nextInt();
            }
            //  algo to find out 3 max numbers....
            int[] ansArr = new int[3];

            for(int j=0; j<3;j++) {
                int maxTillNow = Integer.MIN_VALUE;
                int indexOfMax = -1;
                for(int i=0;i<arr.length;i++) {
                    if(arr[i] > maxTillNow) {
                        maxTillNow = arr[i];
                        indexOfMax = i;
                    }

                }

                ansArr[j] = maxTillNow;
                arr[indexOfMax] = Integer.MIN_VALUE;
            }

            // print ans here
            for(int i=0;i<ansArr.length ;i++) {
                System.out.print(ansArr[i] + " ");
            }
            System.out.println();
            T--;
        }
    }
}
