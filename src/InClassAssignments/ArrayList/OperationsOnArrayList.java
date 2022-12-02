package InClassAssignments.ArrayList;

import java.util.ArrayList;
import java.util.Collections;

public class OperationsOnArrayList {
    public static void main(String[] args) {

    }

    public static void insert(ArrayList<Integer> list, int x) {
        list.add(x);
    }

    // Function to sort list in Increasing order
    public static void IncOrder(ArrayList<Integer> list) {
        Collections.sort(list, (a, b) -> a - b);
    }

    // Function to search element in the lise
    // val : element to be searched
    public static void Search(ArrayList<Integer> list, int val) {
        int index = list.indexOf(val);
        System.out.println(index);
    }

    // Function to sort list in decreasing order
    public static void DecOrder(ArrayList<Integer> list) {
        //        both of them are the same
        Collections.sort(list, (a, b) -> b-a);

//        Collections.sort(list, Collections.reverseOrder());
    }
}
