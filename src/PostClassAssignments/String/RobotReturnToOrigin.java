package PostClassAssignments.String;

import java.util.Scanner;

public class RobotReturnToOrigin {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        String s = scn.nextLine();

        boolean ans = isRobotInOrigin(s);
        if(ans) {
            System.out.println("True");
        } else {
            System.out.println("False");
        }
    }

    public static boolean isRobotInOrigin(String commandString) {
//        "UDLR"

        int xAxisDistance = 0;
        int yAxisDistance = 0;

        for(int i = 0 ;i< commandString.length() ; i++) {
            char command = commandString.charAt(i);
            if(command == 'U') {
                yAxisDistance += 1;
            }
            if(command == 'D') {
                yAxisDistance -= 1;
            }

            if(command == 'R') {
                xAxisDistance += 1;
            }
            if(command == 'L') {
                xAxisDistance -= 1;
            }
        }

        if(xAxisDistance == 0 && yAxisDistance == 0) {
            return true;
        } else {
            return false;
        }
    }
}
