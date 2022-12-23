package Util;

public class util {
    public static void printArrayInt(int[] arr, String str) {

        System.out.print(str +":  ");

        for (int i = 0; i < arr.length; i++) {
            System.out.print(arr[i] +" ");
        }
        System.out.println();
    }

    public static void printArrayBoolean(boolean[] arr, String str) {

        System.out.print(str +":  ");

        for (int i = 0; i < arr.length; i++) {
            System.out.print(arr[i] +" ");
        }
        System.out.println();
    }

    public static void swapInArrays(int[] arr, int a, int b) {
        int temp = arr[a];
        arr[a] = arr[b];
        arr[b] = temp;
    }
}
