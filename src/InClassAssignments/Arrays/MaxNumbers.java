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

            Arrays.sort(arr);

            int len = arr.length;

            System.out.print(
                    arr[len-1]
                            + " " + arr[len-2]
                            + " " + arr[len-3]
            );
            System.out.println();


            T--;
        }
    }
}
