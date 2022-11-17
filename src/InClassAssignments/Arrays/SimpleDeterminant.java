package InClassAssignments.Arrays;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;

public class SimpleDeterminant {
    public static void main(String[] args) throws Exception {
    //        1  2
    //        3  4

//        InputStreamReader r=new InputStreamReader(System.in);
//        BufferedReader br=new BufferedReader(r);
//
//        String line1 = br.readLine(); //"1 2"
//        String line2 = br.readLine(); // "3 4"
//
//        String[] line1Arr = line1.split(" "); // ["1",  "2"]
//        String[] line2Arr = line2.split(" "); // ["3",  "4"]
//
//        int[] line1Arrint = new int[2]; // [1 ,2]
//        int[] line2Arrint = new int[2]; // [3. 4]
//
//        for(int i =0;i<line1Arrint.length;i++) {
//            line1Arrint[i] = Integer.parseInt(line1Arr[i]);
//        }
//
//        for(int i =0;i<line2Arrint.length;i++) {
//            line2Arrint[i] = Integer.parseInt(line2Arr[i]);
//        }
//
//        int[][] arr = new int[2][2];
//        arr[0] = line1Arrint;
//        arr[1] = line2Arrint;

        Scanner scn = new Scanner(System.in);
        int[][] arr = new int[2][2];

        for(int i=0;i<arr.length;i++) {
            for(int j=0; j<arr[0].length;j++) {
                arr[i][j] = scn.nextInt();
            }
        }

        int ans = arr[0][0] * arr[1][1] - arr[1][0] * arr[0][1];

        System.out.println(ans);




    }
}
