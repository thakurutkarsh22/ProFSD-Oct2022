package Contest.jan29;

import java.util.Scanner;

public class LongestConsecutive1s {
    public static void main (String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int t = sc.nextInt();
        while(t-- > 0){
            int n = sc.nextInt();
            int max = 0, cnt = 0;
            for(int i=0; i<32; i++){
                if((n&(1<<i)) != 0){
                    cnt++;
                    max = Math.max(cnt,max);
                }else {
                    cnt = 0;
                }
            }
            System.out.println(max);
        }
        sc.close();
    }
}
