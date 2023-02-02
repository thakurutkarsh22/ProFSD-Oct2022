package InClassAssignments.Arrays;

import java.util.Scanner;

public class GridAndPhrase {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int m = scn.nextInt();

        int[][] arr = new int[n][m];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                arr[i][j] = scn.next().charAt(0);
            }
        }

        int sabaLength = 4;
        int count = 0;

        for (int row = 0; row < n ; row++) {

            for (int col = 0; col < m; col++) {

//                Horizontal case ...
                if(col + sabaLength <= arr[0].length) {
                    if(arr[row][col] == 's' && arr[row][col+ 1] == 'a'
                        && arr[row][col + 2] == 'b' && arr[row][col+ 3] == 'a'
                    ) {
                        count++;
                    }
                }


//                Vertical Case ----

                if(row + sabaLength <= arr.length) {
                    if(arr[row][col] == 's' && arr[row + 1][col] == 'a'
                            && arr[row + 2][col] == 'b' && arr[row+ 3][col] == 'a'
                    ) {
                        count++;
                    }
                }


                // diagonal upside

                if(row >= sabaLength -1
                        && col + sabaLength <= arr[0].length) {
                    if(arr[row][col] == 's' && arr[row - 1][col +1] == 'a'
                            && arr[row - 2][col +2] == 'b' && arr[row - 3][col +3] == 'a'
                    ) {
                        count++;
                    }
                }


//                diagonal downside
                if(row + sabaLength <= arr.length
                        && col + sabaLength <= arr[0].length) {
                    if(arr[row][col] == 's' && arr[row + 1][col +1] == 'a'
                            && arr[row + 2][col +2] == 'b' && arr[row+ 3][col +3] == 'a'
                    ) {
                        count++;
                    }
                }


            }
        }
        System.out.println(count);



    }
}
