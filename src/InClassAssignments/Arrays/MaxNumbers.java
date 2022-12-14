package InClassAssignments.Arrays;

import java.io.*;
import java.util.Arrays;

public class MaxNumbers {
    public static void main(String[] args) throws Exception {
        InputStreamReader fr=new InputStreamReader(System.in);
        BufferedReader br=new BufferedReader(fr);


        int T = Integer.parseInt(br.readLine());

        while(T!=0) {
            // should do our work
            int n = Integer.parseInt(br.readLine());
            int[] arr = new int[n];

            String numberString = br.readLine(); //"1 4 2 4 5" == ["1","4","2","4","5"]

            String[] numberArrayString = numberString.split(" "); //["1","4","2","4","5"]

            // filling the array
            for(int i=0;i<arr.length;i++) {
                arr[i] = Integer.parseInt(numberArrayString[i]);
            }


            //  algo to find out 3 max numbers....
//            maxNumebrWay1(arr); good
            maxNumebrWay2(arr); // not good


            T--;
        }
    }

    public static void maxNumebrWay1(int[] arr) {
        Arrays.sort(arr);

        int len = arr.length;

        System.out.print(
                arr[len-1]
                        + " " + arr[len-2]
                        + " " + arr[len-3]
        );
        System.out.println();
    }

//    another way to solve this ques
//    find max, 2nd max, 3rdmax in an array.

    public static void maxNumebrWay2(int[] arr) {

        int[] ansArr = new int[3];

        for(int repeat = 0; repeat <3 ;repeat++) {
            int max = arr[0];
            int maxIndex = 0;

            for(int i = 0; i< arr.length; i++) {
                if(arr[i] > max) {
                    max = arr[i];
                    maxIndex = i;
                }
            }

//        we have to add the max in the storage
            ansArr[repeat] = max;
            arr[maxIndex] = Integer.MIN_VALUE; // -infinity
        }

//        ans array printing

        for (int i = 0; i < ansArr.length; i++) {
            System.out.print(ansArr[i] + " ");
        }
        System.out.println();


    }
}
