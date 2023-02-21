package InClassAssignments.Hash;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Largestsubarraywithzerosum {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }

        int ans = largestSubarraySum(arr, 0);
        System.out.println(ans);


    }

    public static  int largestSubarraySum(int[] arr, int k) {
        HashMap<Integer, Integer> map = new HashMap<>();
//        PrefixSum , Index....
        
        map.put(0, -1);
        int prefixSum = 0;

        int ans = Integer.MIN_VALUE;


        for (int i = 0; i < arr.length; i++) {
            prefixSum += arr[i];

            int target = prefixSum - k;
            if(map.containsKey(target)) {
                ans = Math.max(ans, i- map.get(target) );
            } else {
                map.put(prefixSum, i);
            }
        }
        return ans == Integer.MIN_VALUE ? -1 : ans;
    }

//    TC & Sc => O(n)
}
