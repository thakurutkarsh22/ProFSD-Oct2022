package LiveClass.GeneralMath;

import Util.util;

import java.util.ArrayList;
import java.util.Arrays;

public class Prime {
    public static void main(String[] args) {

//        int n = 1346467;
//        boolean ans = isPrime(n);
//        System.out.println(ans);

//        int findsmallestDiv = 1346467;
//        int ansSmallestPrime = smallestPrimeDivisor(findsmallestDiv);
//        System.out.println(ansSmallestPrime);

        sieveOferatosthenes(25);

    }

    /*
        Question: Is Number Prime
        Input: 13
        Output: true
     */

    public static boolean isPrime(int n) {

        if(n < 2) {
            return false;
        }

        for (int i = 2; i * i <= n ; i++) {
            if(n % i == 0) {
                return false;
            }
        }

        return true;
    }

//    TC => O(squrt(n))
//    SC => O(1)


    /*
        Question: Smallest prime divisior of a number
        Input: 11
        Output: 11

        Input: 25
        Output: 5

     */

    public static int smallestPrimeDivisor(int n) {
        for (int i = 2; i * i <= n; i++) {
            if(n % i == 0) {
                return i;
            }
        }
        return n;
    }

//    TC => O(squrt(n))
//    SC => O(1)

    /*
        Question: we need to print the prime numbers from 2- n
        Input: 20
        Output: 2,3,5,7,11,13,17.
     */

    public static ArrayList<Integer> sieveOferatosthenes(int n) {
        ArrayList<Integer> primeNoAns = new ArrayList<>();

        boolean[] arr = new boolean[n];
        Arrays.fill(arr, true);
        arr[0] = arr[1] = false;
//        for (int i = 0; i < arr.length; i++) {
//            arr[i] = true;
//        }

//        this is filling the array
        for (int i = 2; i * i <= n; i++) {
            if(arr[i] == true) {
                for (int j = i + 1; j <= n - 1; j++) {
                    if(j % i == 0) {
                        arr[j] = false;
                    }
                }
            }
        }

        util.printArrayBoolean(arr, "ans");
        return primeNoAns;

    }

//    TC =>O(nlog(log(n)))
//    SC => O(n)
}
