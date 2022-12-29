package InClassAssignments.Math;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class GCDFrequency {
    public static void main(String[] args) {
        Scanner scn  = new Scanner(System.in);
        int n = scn.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextInt();
        }

        int ans = gcdFreqAns(arr);
        System.out.println(ans);


    }

    public static int gcdFreqAns(int[] arr) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < arr.length; i++) {
            int item = arr[i];
//            map.put(item, map.getOrDefault(item, 0) + 1);

            if(!map.containsKey(item)) {
                map.put(item, 1);
            } else {
                int oldVal = map.get(item);
                map.put(item,oldVal + 1);
            }
        }

//        Map filling is done by now  -------
        int sum = 0;

        for (int i = 0; i < arr.length; i++) {
            int element = arr[i];
            int fre = map.get(element);

            sum += gcd(element, fre);
        }

        return sum;


    }

    public static int gcd(int a, int b) {

        if(b == 0) {
            return a;
        } else {
            int returnVal = gcd(b , Math.abs(a - b));
            return returnVal;
        }

    }
}
