package Contest.march11_CommunityContest;

public class MaxAnd {
    public static void maxAnd(int[] a, int[] b, int n) {
        // write your code here
        int ci = a[0] & b[0];
        for (int i = 1; i < n; i++) {
            ci &= a[i] & b[i];
        }
        System.out.println(ci);
    }

    // driver code
    public static void main(String[] args) {
        int[] a = { 3, 2 };
        int[] b = { 2, 3 };
        int n = a.length;
        maxAnd(a, b, n);
    }
}
