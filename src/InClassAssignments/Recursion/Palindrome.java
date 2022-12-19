package InClassAssignments.Recursion;

import java.util.Scanner;

public class Palindrome {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T-- !=0) {
            String s = scn.next();
            boolean ans = check_Palindrome(s, 0 , s.length()-1);
            System.out.println(ans);
        }
    }

    public static boolean check_Palindrome(String str,int s, int e) {

        // base condition ..... . .. (what is know)
        if(s >= e) {
            return true;
        }
        if(str.charAt(s) != str.charAt(e)) {
            return false;
        }

//      faith and work
        boolean ans = check_Palindrome(str, s+1, e-1);
        return ans;

    }

//    TC => O(n) to be precise  O(n/2)
//    SC => O(1) stack framers are being created but that is not considered in space complexity
//    In space complexity only the heap memory allocated is considered...

//    Information: If you see stack overflow error, it basically means that you are creating STACK_FRAMES and you are not
//     able to stop that creation. (You forgot the base condition).
}
