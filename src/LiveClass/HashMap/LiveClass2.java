package LiveClass.HashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class LiveClass2 {
    public static void main(String[] args) {
        HashSet<String> set = new HashSet<>();

//        ------------- Insertion ---------------
//        set.add("akash");
//        set.add("priya");
//        set.add("varun");
//        set.add("akash");
//        set.add("akash1");
//
//        System.out.println(set);
//
////      ------------ Searching -------------
//        System.out.println(set.contains("utkarsh"));
//        System.out.println(set.contains("akash"));

        int[] arr = new int[]{1,2,3,4,5,6,89,90,12,15,17,1000,123};
        int findDuplicateANs = findDuplicate(arr);
        System.out.println(findDuplicateANs);

        int[] removeDuplicatesArr = new int[]{1,2,3,4,6,7,90,1,4,91,3,27,24,3,22,3,12,11,10,9,70,63,70,72};
        removeDuplicates(removeDuplicatesArr);

    }


    /*
           Question: Find out the duplicate in list
           Input: [1,2,3,4,5,6,89,90,3,12,15,17,1000,123];
           Output: 3
     */

    public static int findDuplicate(int[] arr) {
        HashSet set = new HashSet();

        for (int i = 0; i < arr.length; i++) {
            if(set.contains(arr[i])) {
                return arr[i];
            } else {
                set.add(arr[i]);
            }
        }

        return -1;
    }


    /*
        Question: Given a list make sure theres no duplicate inside it.
        Input: [1,2,3,4,6,7,90,1,4,91,3,27,24,3,22,3,12,11,10,9,70,63,70,72]
        Output: [1,2,3,4,6,7,90,91,27,24,22,12,11,10,9,70,63,72]
     */

    public static void removeDuplicates(int[] arr) {
        HashSet hs = new HashSet();

        for (int i = 0; i < arr.length; i++) {
            int item = arr[i];
            hs.add(item);
        }

        System.out.println(hs);
    }





}
