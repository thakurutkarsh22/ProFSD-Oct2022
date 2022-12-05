package LiveClass.Recursion;

public class LiveClass1 {
    public static void main(String[] args) {
        int n = 4;
//        int ans = sum(n);
//        System.out.println(ans + " non recursive");

        int ansRecursive = sumRecursion(n);
        System.out.println(ansRecursive + " recursive ans");

    }

    public  static int sum(int n) { // 3
        int ans = 0;
        for (int i = 1; i <=n ; i++) {
            ans +=i;
        }

        return ans;
    }

    public static int sumRecursion(int n) {
//            5 + 4 + 3 +2 +1
//        stopping condition
        if(n == 0) {
            return 0;
        }
//        work  + faith ....
        int faith  = sumRecursion(n - 1); // 15
        int sum = n + faith;
        return sum;
    }
}
