package LiveClass.Arrays;

public class LiveLecture4 {
    public static void main(String[] args) {
        int[][] array = {
                {1,2,3},
                {4,5,6},
                {7,8,9},
        };

        int[][] array2 = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9},
                {10,11,12},
        };

        int[][] arr3 = {
                {1,2,3,4,5}
        };

        int[][] arr4 = {
                {1},
                {2},
                {3},
                {4}
        };

        int[][] arr5 = {
                {5}
        };


//        printDiagonal(array);
//        boundaryTraversal(arr4);
        spiralTraversal(array2);
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

//        other half

        for(int col = 1 ; col < arr[0].length; col++) {

            int i = arr.length - 1;
            int j = col;

            while(isValid(i, j, arr)) {
                System.out.print(arr[i][j]);
                j++;
                i--;
            }
            System.out.println();
        }

    }

//    TC = O(n * m) where n are row elements and m are col elements and if n = m then O(n^2);
//    SC => O(1)

    private static boolean isValid(int row, int col, int[][] arr) {
        int rowLength = arr.length;
        int colLength = arr[0].length;

        if(row < 0 || row >= rowLength || col < 0 || col >=colLength) {
            return false;
        } else {
            return true;
        }
    }


//    Boundary traversal of the matrix
/* 1  2   3
*  4  5   6
*  7  8   9
*  10 11 12

 */
//* 1 2 3 6 9 12 11 10 7 4 */

    public static void boundaryTraversal(int[][] arr) {
        int row = 0;
        int col = 0;

//        Top side

        for( col =0 ; col < arr[0].length ; col++ ) {
            System.out.print(arr[row][col] +" ");
        }
        col--;

//        Right side
        row = row + 1;
        for( ; row < arr.length; row++) {
            System.out.print(arr[row][col] +" ");
        }
        row--;

//        bottom side
        if(row > 0) {
            col = col - 1;
            for (; col >= 0; col--) {
                System.out.print(arr[row][col] + " ");
            }
            col++;
        }

//        left
        if(col > 0) {
            row = row - 1;
            for (; row >= 1; row--) {
                System.out.print(arr[row][col] + " ");
            }
        }
    }

    public static void spiralTraversal(int[][] arr) {

        int rowStart = 0;
        int rowEnd = arr.length -1;

        int colStart = 0;
        int colEnd = arr[0].length -1;

        while(colEnd >= colStart && rowEnd >= rowStart) {
//            top
                for (int i = colStart; i <= colEnd; i++) {
                    System.out.print(arr[rowStart][i] + " ");
                }
            rowStart++;

//            right

            for(int i=rowStart; i<= rowEnd ;i++) {
                System.out.print(arr[i][colEnd] +" ");
            }
            colEnd--;

//            bottom
            if(rowStart <= rowEnd) {
                for (int i = colEnd; i >= colStart; i--) {
                    System.out.print(arr[rowEnd][i] + " ");
                }
            }
            rowEnd--;

//            left
            if(colStart <=colEnd) {
                for (int i = rowEnd; i >= rowStart; i--) {
                    System.out.print(arr[i][colStart] + " ");
                }
            }
            colStart++;
        }
    }
}
