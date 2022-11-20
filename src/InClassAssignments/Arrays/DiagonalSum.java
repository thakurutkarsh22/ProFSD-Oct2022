package InClassAssignments.Arrays;

public class DiagonalSum {
    public static void main(String[] args) {


        int[][] arr = {
                {1,2,3},
                {4,5,6},
                {7,8,9}
        };

        int sum1 = DiagonalSumBetter(arr);
        System.out.println();
        int sum2 = DiagonalReverseSumBetter(arr);

        System.out.println(sum1 +" " + sum2);


    }

    public static void DiagonalSum(int[][] arr) {
        for(int row=0; row<arr.length;row++){
            for (int col = 0; col < arr[0].length; col++) {
                if(col == row) {
                    System.out.println(arr[row][col] +" ");
                }
            }
        }
    }
    //        TC = O(n^2)

    public static int DiagonalSumBetter(int[][] arr) {
        int sum = 0;
        for(int row=0; row<arr.length;row++){
            sum += arr[row][row];
        }
        return sum;
    }

//    TC = O(n)
//    SC = (1)


    public static int DiagonalReverseSumBetter(int[][] arr) {
        int sum = 0;
        for(int row=0; row < arr.length;row++){
            sum += arr[row][arr.length -1 - row];
        }

        return sum;
    }

//    TC = O(n)
//    SC = (1)



}
