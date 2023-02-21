package Leetcode;

import java.util.HashMap;

public class SubarraySumEqualsK560 {

    public static void main(String[] args) {
//        int[] arr = new int[]{3,9,-2,4,1,-7,2,6,-5,8,-3,-7,6,2,1};
        int[] arr = new int[]{5,-4,-1, 10, -5};

        int ans = subarraySum(arr, 5); // 7
        System.out.println(ans);
    }

    public static  int subarraySum(int[] arr, int k) {
        HashMap<Integer, Integer> map = new HashMap<>();
        map.put(0, 1);
        int[] prefixSum = new int[arr.length];
        prefixSum[0] = arr[0];

        int ans = 0;

//        fill the prefix sum O(n)....
        for (int i = 1; i < arr.length; i++) {
            prefixSum[i] = arr[i] + prefixSum[i-1];
        }


//        O(n)
        for (int i = 0; i < prefixSum.length; i++) {
            int target = prefixSum[i] - k;
            if(map.containsKey(target)) {
                ans += map.get(target);
            }

            map.put(prefixSum[i], map.getOrDefault(prefixSum[i], 0) + 1);
        }


        return ans;


    }

//    TC = > O(n) actuallity 2*O(n).
//    SC = > O(n) actuallity 2*O(n).



}
