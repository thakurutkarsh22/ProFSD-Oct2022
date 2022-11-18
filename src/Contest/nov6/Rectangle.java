package Contest.nov6;

import java.util.Scanner;

public class Rectangle {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();
        int m = scn.nextInt();

        for(int row=0; row<n; row++) {
            for(int col=0; col<m; col++){


                if(row ==0 || row == n-1 || col == 0 || col == m -1 ) {

                    // roows are done
                    if(row ==0 || row == n-1) {
                        System.out.print("*");
                    }

                    // columns
                    if(col == 0 || col == m -1) {
                        if(!(row == 0 || row == n-1)) {
                            System.out.print("*");
                        }
                    }

                } else {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }
    }
}
