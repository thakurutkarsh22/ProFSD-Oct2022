package InClassAssignments.ArrayList;

import java.util.ArrayList;
import java.util.Scanner;

public class UniqueSortedElements {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

//        how to fill the Arraylist
        ArrayList<Integer> al =  new ArrayList<>();
        for (int i = 0; i < n; i++) {
            al.add(scn.nextInt());
        }
// How to get the answeer
        ArrayList<Integer> ansList = uniqueElements(al);

        for (int elem: ansList) {
            System.out.print(elem + " ");
        }

    }


    public static ArrayList<Integer> uniqueElements(ArrayList<Integer> list) {
//        list [1 2 4 3 5 4 3]

        ArrayList<Integer> newList = new ArrayList<>();

//        O(n^2)
        for (int i = 0; i < list.size(); i++) {
            int elem = list.get(i);

//            i am going to check if elem is in the list or not
//            if the elem is NOT in the list then we will add it.

            if(newList.indexOf(elem) == -1) {  // O(n)
                newList.add(elem);
            }
        }

        newList.sort((a ,b) -> a-b); // O(nlogn)

        return newList;

    }

//    TC => O(n^2)
//    SC => O(n)
}
