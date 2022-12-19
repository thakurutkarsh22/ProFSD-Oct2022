package PostClassAssignments.ArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class CountFrequencies {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        ArrayList<Integer> al = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            al.add(scn.nextInt());
        }
        getFrequency(al);

    }

    public static void getFrequency( ArrayList<Integer> list) {
//        Step 1: sort
        Collections.sort(list);
//        System.out.println(list);

//        Setp 2: take new arraylist and fill it with the unique elements
        ArrayList<Integer> al = new ArrayList<>();
        for (int i =0 ;i < list.size(); i++) {
            int element = list.get(i);
            if(!al.contains(element)) {
                al.add(element);
            }
        }

//        Step 3: check the element frequency

        for (int i =0 ;i < al.size(); i++) {
            int element = al.get(i);
            int freq = Collections.frequency(list, element);
            System.out.println(element + " " + freq);
        }

    }
}
