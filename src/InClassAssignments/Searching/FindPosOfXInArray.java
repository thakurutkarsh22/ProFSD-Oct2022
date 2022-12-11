package InClassAssignments.Searching;

import java.util.ArrayList;
import java.util.Scanner;

public class FindPosOfXInArray {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while (T-- !=0) {
            int n = scn.nextInt();
            int target = scn.nextInt();

            int[] arr = new int[n];
            for(int i =0; i< n ;i++) {
                arr[i] = scn.nextInt();
            }

            ArrayList<Integer> ans = findElement(arr, target);
            if(ans.size() != 0) {
                for (int i = 0; i < ans.size(); i++) {
                    System.out.print(ans.get(i) + " ");
                }
                System.out.println();
            } else {
                System.out.println("Not found");
            }


        }
    }

    public static ArrayList<Integer> findElement(int[] arr, int target) {

        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
            if(arr[i] == target) {
                list.add(i);
            }
        }

        return list;

    }

//    Tc=> O(n)
//    Sc => O(n)
}
