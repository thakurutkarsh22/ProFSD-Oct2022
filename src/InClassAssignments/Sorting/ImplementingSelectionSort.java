package InClassAssignments.Sorting;

import java.util.Scanner;

public class ImplementingSelectionSort {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();
        while(T-- !=0) {
            int n = scn.nextInt();
            int[] arr = new int[n];
            for (int i = 0; i < n; i++) {
                arr[i] = scn.nextInt();
            }

            SelectionSort(arr);

            for (int i = 0; i < n; i++) {
                System.out.print(arr[i] + " ");
            }

            System.out.println();
        }
    }

    public static void SelectionSort(int[] arr) {

        for (int i = 0; i < arr.length; i++) {
            int lowest = i;

            for(int j = i + 1; j < arr.length; j++) {
                if(arr[j] < arr[lowest]) {
                    lowest = j;
                }
            }

//            swap lowest to i
            int temp =  arr[lowest];
            arr[lowest] = arr[i];
            arr[i] = temp;
        }
    }
}
