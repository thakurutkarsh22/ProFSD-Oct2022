package Contest.jan8;

import java.util.*;

public class FruitMarket {
    public static void main (String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        long n = sc.nextLong();
        int m = sc.nextInt();
        int[] arr = new int[m];
        for(int i=0; i<m; i++){
            arr[i] = sc.nextInt();
        }
        Arrays.sort(arr);
        int bags=0;
        for(int i=m-1; i>=0; i--){
            if(n>0){
                bags++;
                n-=arr[i];
            } else break;
        }
        System.out.print(bags);
        sc.close();
    }
}
