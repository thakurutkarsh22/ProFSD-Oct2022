package Contest.jan29;

import java.util.*;
import java.io.*;

public class FindOddOneOut {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine());
        int[] arr = new int[n];
        StringTokenizer st = new StringTokenizer(br.readLine());
        for (int i = 0; i < n; i++) {
            arr[i] = Integer.parseInt(st.nextToken());
        }
        int threeN = Integer.MAX_VALUE, threeNPlusOne = 0, threeNPlusTwo = 0;
        for (int i = 0; i < n; i++) {
            int commonWithThreeN = arr[i] & threeN;
            int commonWithThreeNPlusOne = arr[i] & threeNPlusOne;
            int commonWithThreeNPlusTwo = arr[i] & threeNPlusTwo;

            threeN = threeN & (~commonWithThreeN);
            threeNPlusOne = threeNPlusOne | commonWithThreeN;

            threeNPlusOne = threeNPlusOne & (~commonWithThreeNPlusOne);
            threeNPlusTwo = threeNPlusTwo | commonWithThreeNPlusOne;

            threeNPlusTwo = threeNPlusTwo & (~commonWithThreeNPlusTwo);
            threeN = threeN | commonWithThreeNPlusTwo;
        }
        System.out.println(threeNPlusOne);
    }
}
