package Leetcode;

public class KokoEatBananas875 {

    public static void main(String[] args) {
    }

    class Solution {
        public int minEatingSpeed(int[] piles, int h) {
            int left = 1;
            int right = 100000000;  // instead of this , we can directly take max value of piles array

            while(left <=right) {
                int mid = left + (right - left)/2;
                boolean isValid = valid(piles, h, mid);
                if(isValid == true) {
                    right = mid -1;
                } else {
                    left = mid + 1;
                }
            }

            return left;

        }

        public boolean valid(int[] arr, int hour, int mid) {
            int count = 1;
            int total = 0;
            for(int i: arr) {
                total += (i%mid ==0) ? i/mid : i/mid +1;
                if(total > hour) {
                    return false;
                }
            }

            return true;
        }
    }

}


