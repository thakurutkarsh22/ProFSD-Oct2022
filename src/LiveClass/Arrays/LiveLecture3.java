package LiveClass.Arrays;

public class LiveLecture3 {
    public static void main(String[] args) {
//                our program


//        creation of 2-D array

        int[][] array = new int[2][4];

        int rowLength = array.length;
        int colLength = array[0].length;

//        accessing the value

//        for(int row = 0 ; row< rowLength; row++) {
//
//            for(int col = 0; col<colLength; col++ ) {
//                int value = array[row][col];
////                System.out.println(value + " " + "row: " + row + " " + "col: " + col);
//                System.out.print(value + " ");
//            }
//
//            System.out.println();
//
//        }

//        increasingCounting(array);
        increasingCountingRowWise(array);

    }
    //   QUESTION:       create an 2d arrays with increasing counting.....

//            1 2 3 4
//            5 6 7 8

    public static void increasingCounting(int[][] arr) {

        int count = 1;
        for(int row = 0 ; row < arr.length; row++) {
            for(int col = 0; col < arr[0].length; col++) {
                arr[row][col] = count;
                count++;
                System.out.print(arr[row][col] + " ");
            }
            System.out.println();
        }
    }

    //   QUESTION:       create an 2d arrays with increasing counting.....

//            1 2 3 4
//            1 2 3 4

    public static void increasingCountingRowWise(int[][] arr) {

        for(int row = 0 ; row < arr.length; row++) {
            for(int col = 0; col < arr[0].length; col++) {
                arr[row][col] = col + 1;
                System.out.print(arr[row][col] + " ");
            }
            System.out.println();
        }
    }
}
