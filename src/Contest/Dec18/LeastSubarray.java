package Contest.Dec18;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.StringTokenizer;

public class LeastSubarray {
    public static void main(String[] args) throws IOException {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int t = Integer.parseInt(br.readLine());
        while(t-->0){
            StringTokenizer st = new StringTokenizer(br.readLine());
            int n = Integer.parseInt(st.nextToken());
            int m = Integer.parseInt(st.nextToken());
            int[] arr1 = new int[n];
            int[] arr2 = new int[m];
            st = new StringTokenizer(br.readLine());
            for(int i=0; i<n; i++){
                arr1[i] = Integer.parseInt(st.nextToken());
            }
            st = new StringTokenizer(br.readLine());
            for(int i=0; i<m; i++){
                arr2[i] = Integer.parseInt(st.nextToken());
            }
            long target = Long.parseLong(br.readLine());


            long ans = leastSubArray(arr1,arr2,target);
            System.out.println(ans);

        }
    }

    public static long leastSubArray(int[] arr1, int[] arr2, long target) {

        int left = 0;
        int right = Math.min(arr1.length, arr2.length);


        int ans = -1;
        while(left <= right) {
            int mid = left + (right - left) /2; // this is the current window size. . .

            long sum1 = 0;
            long sum2 = 0;

            long currentSum = 0;

//            get the sum for subarray from the array 1
            for (int i = 0; i < arr1.length; i++) {
                currentSum += arr1[i];
                if(i>= mid) {
                    currentSum -= arr1[i-mid];
                }

                sum1 = Math.max(sum1, currentSum);
            }

            currentSum = 0;

            //            get the sum for subarray from the array 2
            for (int i = 0; i < arr2.length; i++) {
                currentSum += arr2[i];
                if(i>= mid) {
                    currentSum -= arr2[i-mid];
                }

                sum2 = Math.max(sum2, currentSum);
            }

            long multiplicationAns = (sum1 * sum2);
            if(multiplicationAns > target) {
                ans = mid;
                right = mid -1;
            } else {
                left = mid + 1;
            }
        }
        return ans;
    }
}
