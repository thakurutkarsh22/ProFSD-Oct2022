package InClassAssignments.Hash;

import java.util.HashSet;
import java.util.Scanner;

public class HasingForPair {
    public static void main (String[] args) {
        // Your code here
        Scanner scn = new Scanner(System.in);
        int T = scn.nextInt();

        while(T-- !=0) {
            int n = scn.nextInt();
            int target = scn.nextInt();



            int[] arr = new int[n];
            for(int i=0;i<n;i++) {
                arr[i] = scn.nextInt();
            }

            int ans = countPairsOfSumToTarget(arr, target);
            if(ans > 0) {
                System.out.println(1);
            } else {
                System.out.println(0);
            }
        }


    }

    public static int countPairsOfSumToTarget(int[] arr, int target) {
        int count = 0;
        HashSet set  = new HashSet();
        for (int i = 0; i < arr.length; i++) {
            int item = arr[i];
            int otherNumber = target - item;

            if(set.contains(otherNumber)) {
                count++;
            }
            set.add(item);
        }

        return count;

    }
}
