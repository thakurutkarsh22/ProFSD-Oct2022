package PostClassAssignments.Array2D;

import java.util.Scanner;

public class ChessBoard {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = Integer.parseInt(scn.nextLine());

        String[] arr = new String[n];
        for (int i = 0; i < n; i++) {
            arr[i] = scn.nextLine();
        }
        int ans = chessBoard(arr);
        System.out.println(ans);

    }

    public static int chessBoard(String[] board) {

//        we will make assumption the board[0][0] is black
        int initial = 1;
        int type1Ans = 0;
        for (int i = 0; i < board.length; i++) {
            int current = initial;
            for (int j = 0; j < board[0].length(); j += 2) {
                int val = board[i].charAt(j) - 48; //1 // 0
                if (current != val) {
                    type1Ans++;
                }
                current = 1 - current; // TRICk you need to check this and learn this ....
            }
            initial = 1 - initial;
        }


//        we will make assumption the board[0][0] is white (type 2)

        initial = 0;
        int type2Ans = 0;
        for (int i = 0; i < board.length; i++) {
            int current = initial;
            for (int j = 0; j < board[0].length(); j += 2) {
                int val = board[i].charAt(j) - 48; //1 // 0
                if (current != val) {
                    type2Ans++;
                }
                current = 1 - current; // TRICk you need to check this and learn this ....
            }
            initial = 1 - initial;
        }
        int ans = Math.min(type2Ans, type1Ans);
        return ans;


    }
}
