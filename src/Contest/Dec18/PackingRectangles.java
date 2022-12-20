package Contest.Dec18;

import java.util.Scanner;

public class PackingRectangles {
    public static void main (String[] args) {
        Scanner scn = new Scanner(System.in);
        int w = scn.nextInt(); //2
        int h = scn.nextInt();//3
        int n = scn.nextInt();//10

        int left = 1;
        int right = 1000000000;

        while(left <= right) {
            int mid = left + (right - left) /2;

            int x = mid / w;
            int y = mid / h;



            // condition: (y > 0 && x >= ((n - 1) / y + 1)) || (x > 0 && y >= ((n - 1) / x + 1)) ....
            if((y > 0 && x >= ((n - 1) / y + 1)) || (x > 0 && y >= ((n - 1) / x + 1))) {
                // i am able to fit more elements inside the square
                right = mid - 1;
            } else {
                left = mid + 1;
            }

        }

        System.out.println(left);

    }

}
