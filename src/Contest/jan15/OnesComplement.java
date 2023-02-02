package Contest.jan15;

import java.util.Scanner;

public class OnesComplement {
    public static void main (String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while(t-->0){
            long n = sc.nextLong();
            int lmsb = (int) (Math.log(n)/Math.log(2)) + 1;
            System.out.println(((1<<lmsb)-1)^n);
        }
        sc.close();
    }
}
