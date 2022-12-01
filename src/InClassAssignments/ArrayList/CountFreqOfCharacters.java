package InClassAssignments.ArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class CountFreqOfCharacters {
    public static void main(String[] args) {
//        Scanner scn = new Scanner(System.in);
//        int T = scn.nextInt();
//
//        while(T-- !=0) {
//            int n = scn.nextInt();
//
//        }

        /*
            This is a functional question so no need to code for the main
            function
            we just need to code the functions without thinking who is calling
            from where it is calling and what it is doing with that

            Need to focus on the responsibility of function
         */
    }

    public static void insert(ArrayList<Character> clist, char c)
    {
        clist.add(c);
    }

    // Function to count frequency of element
    public static void freq(ArrayList<Character> clist, char ch)
    {
        if(clist.contains(ch)) {
            int freqVal = Collections.frequency(clist, ch );
            System.out.println(freqVal);
        } else {
            System.out.println("Not Present");
        }
    }
}
