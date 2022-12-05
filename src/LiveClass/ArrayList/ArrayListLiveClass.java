package LiveClass.ArrayList;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class ArrayListLiveClass {
    public static void main(String[] args) {
//        define
        ArrayList<Integer> al = new ArrayList<Integer>();
////        it will be create in heap
//
//
////      ----------  add values in the arraylist ------------
        al.add(12); // 0
        al.add(24); // 1
        al.add(100); // 2
        al.add(12);
        al.add(12);
//
////        --------- print an arraylist -----------------
        System.out.println(al);
//        ---------- CONTAINS to the if the value is there or not...
//        System.out.println(al.contains(121));
//
////         ----------- get the value ----------
//
//        int val = al.get(0);
//        System.out.println(val);
//
//        int val2 = al.get(1);
//        System.out.println(val2);
//
////        ------------ REMOVE the value --------
//
//        al.remove(2);
//        System.out.println(al);
//
////      --------------- SET the value ----------
//
//        al.set(1, 18);
//        System.out.println(al);
//
////        -------------- SORTING ------------------
//        al.add(1);
//        al.add(5);
//        al.add(3);
//        al.add(90);
//        al.add(18);
//
//        System.out.println(al);
////TODO: we need to know about comparator.... no need to think about the a, b and
////        this concept (Lambda functions in java) !Aws
//
//        al.sort((a, b) -> b - a); // sorting in decending order
////        al.sort((a, b) -> a - b); // sorting in acending order
//
//        System.out.println(al);
//
////        -------------- Length ---------
//        System.out.println(al.size());

//        --------------- FREQUENCY OF AN ITEM ----------
//        System.out.println(Collections.frequency(al, 100 ));

//        ----------------- INPUT ---------------

//        ArrayList<Integer> al1 = new ArrayList<>();
//
//        Scanner scn = new Scanner(System.in);
//        int n = scn.nextInt(); // it can be 100 it can be 10000
//
//        for (int i = 0; i < n; i++) {
//            al1.add(scn.nextInt());
//        }

//        [------------------- Find the index ----------------------
        System.out.println(al.indexOf(12));// 0
        System.out.println(al.indexOf(999)); // -1

//        ---------- QUESTIONS ---------
//        int ans = sumOfElements(al1);
//        System.out.println(ans);

//        int[] arr = pascalTriangle(-400);
//        for (int item: arr) {
//            System.out.print(item + " ");
//        }

    }

    /*
            fill an array list, sum of all the integers inside it ...
            Input = : [12,45,78,90,1]
            output: sum of above no...
     */

    public static int sumOfElements(ArrayList<Integer> al) {
        int sum = 0;
        for (int i = 0; i < al.size(); i++) {
          sum += al.get(i);
        }

        return sum;
    }

//    TC => O(n)
//    SC => O(1)

    /*
            input: 4
            output: [1,3,3,1]
            Explanation: what is a pascal triangle ....
                       1
                      1 1
                     1 2 1
                    1 3 3 1
     */


    public static int[] pascalTriangle(int n) {
        n = n -1;
        if(n < 0) {
            return new int[0];
        }

        int[] prevArr = new int[1];
        prevArr[0] = 1;

        while(n !=0) {
            int[] nextRowArray = new int[prevArr.length + 1];

            // ----  filling ----
//            start and the end of the array for 1
            nextRowArray[0] = 1;
            nextRowArray[nextRowArray.length-1] = 1;
//            filling middle numbers

            for (int i = 1; i < prevArr.length; i++) {
                nextRowArray[i] = prevArr[i] + prevArr[i-1];
            }

//            now all the work of the row is done .....
//            now i should update the prevArr ROw .....
            prevArr = nextRowArray;

            n--;
        }
        return prevArr;

    }
//    TC = >
//    SC =>


}
