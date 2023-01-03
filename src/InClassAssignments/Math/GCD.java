package InClassAssignments.Math;

import java.util.Scanner;

public class GCD {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        long a = scn.nextLong();
        long b = scn.nextLong();

        long ans = gcd(a,b);
        System.out.println(ans);

    }

    public static long gcd(long a, long b) {

        if(b == 0) {
            return a;
        } else {
            long returnVal = gcd(b , Math.abs(a - b));
            return returnVal;
        }

    }

//    TC => log(a.b) ===== log(n)
//    SC => O(1)


}
