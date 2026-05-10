package Contest.dec4;

import java.util.ArrayList;
import java.util.Scanner;

public class MergeList {
    public static void main(String[] args) {
        // Your code here
        Scanner scn = new Scanner(System.in);
        int len1 = scn.nextInt();
        int len2 = scn.nextInt();

        ArrayList<Integer> arr1 = new ArrayList<>();
        ArrayList<Integer> arr2 = new ArrayList<>();

        for (int i = 0; i < len1; i++) {
            arr1.add(scn.nextInt());
        }

        for (int i = 0; i < len2; i++) {
            arr2.add(scn.nextInt());
        }

        int ind = scn.nextInt();

        ArrayList<Integer> ans = mergeList(arr1, arr2, ind);

        for (int i = 0; i < ans.size(); i++) {
            System.out.print(ans.get(i) + " ");
        }

        scn.close();
    }

    public static ArrayList<Integer> mergeList(ArrayList<Integer> arr1, ArrayList<Integer> arr2, int index) {
        ArrayList<Integer> ansList = new ArrayList<>();

        // add every element BEFORE index from arr1...

        for (int i = 0; i < index; i++) {
            ansList.add(arr1.get(i));
        }

        // add the element form arr2 at index value ...

        for (int i = 0; i < arr2.size(); i++) {
            ansList.add(arr2.get(i));
        }

        // add every element AFTER index from arr1...
        for (int i = index; i < arr1.size(); i++) {
            ansList.add(arr1.get(i));
        }

        return ansList;
    }

}
