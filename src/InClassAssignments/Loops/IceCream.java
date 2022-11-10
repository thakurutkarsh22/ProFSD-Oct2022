package InClassAssignments.Loops;

import java.util.Scanner;

public class IceCream {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int N = scn.nextInt();
        int D = scn.nextInt();
        Icecreams(N, D);
    }

    public static void Icecreams (int N, int D){
        //Enter your code here

        int iceCreamAtTheStart = N;

        for(int i=0 ; i<D; i++) {
            int inceCreamEaten = iceCreamAtTheStart/2;
            int remainingIceCreams = iceCreamAtTheStart - inceCreamEaten;
            int trippleIceCream = 3* remainingIceCreams;

            iceCreamAtTheStart = trippleIceCream;

        }

        System.out.println(iceCreamAtTheStart);



    }

// // N -> icecreams ....

// // N/2 -> eat ...

// // -> 3*N/2 -> remaining icecreams


// ------------ 1st day -----------

//  start with 5 ice creams

// 2 -> ice creams ->  5/2 -> eaten
// 5-2 = 3 ice creams left behind -> 3*3 (after tripple) = 9


// // --- ------- 2nd day -------
// start of 2nd day -> 9 ice creams

// 9/2 -> 4 icecreams are eaten...
// 9-4 = 5 (remaining) -> tripple 5*3 = 15 icecreams at the end of 2nd day ...


// //  ------ 3rd day -----
// start of 3nd day -> 15 ice creams

// 15/2 => 7 icecreams are eaten
// 15-7 = 8 ice creams are (remaining) -> tripple 8*3 = \ icecreams are left
// at the end of 3rd day.

//  --- -4th day

//  24 ice start
}
