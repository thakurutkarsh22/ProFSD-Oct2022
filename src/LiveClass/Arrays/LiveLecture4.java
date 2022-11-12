package LiveClass.Arrays;

public class LiveLecture4 {
    public static void main(String[] args) {
        int[][] array = {
                {1,2,3},
                {4,5,6},
                {7,8,9},
        };
        printDiagonal(array);
    }

    //    print diagonal

    public static void printDiagonal(int[][] arr) {
        // lets print 1st half
        for(int row = 0; row < arr.length; row++) {
//            some work
            int i = row;
            int col = 0;

            while(isValid(i, col, arr )) {
                System.out.print(arr[i][col]);
                i--;
                col++;
            }
            System.out.println();
        }
    }

    private static boolean isValid(int row, int col, int[][] arr) {
        int rowLength = arr.length;
        int colLength = arr[0].length;

        if(row < 0 || row >= rowLength || col < 0 || col >=colLength) {
            return false;
        } else {
            return true;
        }
    }
}
