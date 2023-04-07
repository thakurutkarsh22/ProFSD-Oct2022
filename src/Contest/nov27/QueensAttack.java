package Contest.nov27;

public class QueensAttack {
    static int QueenAttack(int X, int Y, int P, int Q) {
        // Enter your code here
        // if((P==X && Q==Y)) return 0;
        if (((P == X || Y == Q) || (Math.abs(P - X) == Math.abs(Q - Y))))
            return 1;
        return 0;
    }
}
