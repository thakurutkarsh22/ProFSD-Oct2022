package InClassAssignments.Hash;

import java.util.HashMap;
import java.util.Scanner;

public class Subarrayswithequal1sand0s {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }



        for (int i = 0; i < n; i++) {
            if(arr[i] == 0) {
                arr[i] = -1;
            }
        }

        long ans = subarraySum(arr, 0);
        System.out.println(ans);

    }

    public static  long subarraySum(int[] arr, int k) {
        HashMap<Integer, Integer> map = new HashMap<>();
        map.put(0, 1);
        int sum = 0;

        long ans = 0;


        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
            int target = sum - k;
            if(map.containsKey(target)) {
                ans += map.get(target);
            }

            map.put(sum, map.getOrDefault(sum, 0) + 1);
        }


        return ans;


    }

}

//    TC & SC = >O(n) and not 2*O(n) which we have seen in the prev ques Leetcode 560

