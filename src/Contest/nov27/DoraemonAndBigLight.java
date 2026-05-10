package Contest.nov27;

public class DoraemonAndBigLight {
    static boolean EqualOrNot(int h1, int h2, int v1, int v2) {
        // Enter your code here
        int temp = Math.abs(h1 - h2);
        while (temp-- > 0) {
            h1 += v1;
            h2 += v2;
            if (h1 == h2) {
                return true;
            }
        }
        return false;
    }
}
