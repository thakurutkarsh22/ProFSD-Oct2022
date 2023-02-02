package Contest.jan15;

import java.util.Scanner;

public class FixingTheMiddle {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            int length = (int) (Math.log10(n) / Math.log10(2)) + 1;
            if ((length & 1) == 1) {
                int mask = 1 << length / 2;
                System.out.println(n ^ mask);
            } else {
                int mask = (1 << length / 2) | (1 << (length / 2 - 1));
                System.out.println(n ^ mask);
            }
        }
        sc.close();
    }

    // public static void main (String[] args) {
    // Scanner sc = new Scanner(System.in);
    // int t = sc.nextInt();
    // while(t-- > 0){
    // int n = sc.nextInt();
    // int length = countBits(n);
    // if((length&1)==1){
    // int mask = 1 << length/2;
    // System.out.println(n ^ mask);
    // } else {
    // int mask = (1<<length/2) | (1<<(length/2-1));
    // System.out.println(n ^ mask);
    // }
    // }
    // }

    // public static int countBits(int n){
    // int cnt=0;
    // while(n>0){
    // cnt++;
    // n>>=1;
    // }
    // return cnt;
    // }
}
