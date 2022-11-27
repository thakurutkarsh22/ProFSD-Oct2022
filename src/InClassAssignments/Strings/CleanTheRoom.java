package InClassAssignments.Strings;

import java.util.Scanner;

public class CleanTheRoom {

    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        String[] arr = new String[n];

        for (int i = 0; i < arr.length; i++) {
            arr[i] = scn.next();
        }

        int ans = cleanTheRoom(arr);
        System.out.println(ans);
    }

    public static int cleanTheRoom(String[] arr) {
        int max = Integer.MIN_VALUE;
        for (int row = 0; row < arr.length; row++) {
            int counter = 0;
            for(int nextRows = row + 1 ;nextRows < arr.length ;nextRows++ ) {
                if(arr[row].equals(arr[nextRows])) {
                    counter++;
                }
            }
            if(counter > max) {
                max = counter;
            }
        }
        return max + 1;
    }
}
