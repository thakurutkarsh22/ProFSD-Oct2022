package LiveClass.Recursion;

public class LiveClass1 {
    public static void main(String[] args) {
        int n = 4;
//        int ans = sum(n);
//        System.out.println(ans + " non recursive");

//        int ansRecursive = sumRecursion(n);
//        System.out.println(ansRecursive + " recursive ans");

//        printDecreasing(5);
//        printIncreasing(5);
//        printDecreasingIncreasing(5);
//        printDigitSkipsOdd(5);
//        int factorialAns1 = factorial(3); // 6L

//        int factorialAns2 = factorial(5); // 120
//        System.out.println(factorialAns1 + "  : answer2 :   " + factorialAns2);
 //       int fibbonaciAns = fibbonaci(7);
 //       System.out.println(fibbonaciAns);
        System.out.println(powerUpdate(2,10));

    }

    public  static int sum(int n) { // 3
        int ans = 0;
        for (int i = 1; i <=n ; i++) {
            ans +=i;
        }

        return ans;
    }

    /*
           Question: get sum of 1st n digits
           Input: 5
           Output: 15

     */

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

        /*
            Question: to print from N to 1 in decreasing order.
            Input: 5
            Output: 5 4 3 2 1
            Explanation: will start from n till 1
     */

    public static void printDecreasing(int n) {
//        stopping condition

        if(n == 0) {
            return;
        }
//        work
        System.out.print(n +" ");

//        faith
        printDecreasing(n-1);

        return;
    }





    /*
            Question: to print from 1 to N in increasing order.
            Input: 5
            Output: 1 2 3 4 5
            Explanation: will start from 1 till n (the given input)
     */

    public static void printIncreasing(int n) {
//        Stopping condition
        if(n == 0) {
            return;
        }

//        faith
        printIncreasing(n-1); // i will be able to print 4 3 2 1

//         some work
        System.out.print(n + " ");

        return;
    }


  /*
        Question: Print Decreasing increasing
        Input: 5
        Output: 5 4 3 2 1 1 2 3 4 5
   */

    public static void printDecreasingIncreasing(int n) {
// stopping condition
        if(n == 0) {
            return;
        }

//        work and faith
        System.out.print(n + " ");
        printDecreasingIncreasing(n-1);
        System.out.print(n + " ");
    }


    /*
           Question: we have to print even numbers in printDecreasingIncreasing
           Input: 5
           Output: 4 2 2 4
     */

    public static void printDigitSkipsOdd(int n) {
        // stopping condition
        if(n == 0) {
            return;
        }

//        work and faith
        if(n%2 == 0)
            System.out.print(n + " ");

        printDigitSkipsOdd(n-1);

        if(n%2 == 0)
            System.out.print(n + " ");
    }


    /*
            Question: print factorial (5! = 5*4*2*3*1)
            Input: 5
            Output: 120
     */

    public static int factorial(int n) {
//        stopping condition

        if(n ==0) {
            return 1;
        }


//        work + faith
        int faithAns = factorial(n-1); //4 *3 * 2 * 1

        int finalAns = n * faithAns; // 5 *   4*3*2*1

        return finalAns;
    }

    /*
            Question: power (5^2 = 25)
            Input: 5 2
            Output: 25
            Explanation: 5^2 = 25
     */

    public static int power (int n, int p) {
//        stopping condition
        if(p == 0) {
            return 1;
        }

//        work/faith
        int faithAns = power(n, p-1 );
        int finalAns = n * faithAns;

        return finalAns;
    }

    public static int powerUpdate(int n,int p){
        if(p==0){
            return 1;
        }
        int temp = 1;

        if((p&1)==1){
            temp *= n;
        }
        return temp*powerUpdate(n*n,p>>1);

        /*
        // using while loop
        while(n>0){
            if((p&1)==1){
                temp *= n;
            }
            n *=n;
            p = p>>1;
        }
        return temp;
        */
    }


    /*
            Question: Fibbonaci (1, 1, 2, 3, 5, 8, 13, 21, 34, 55, ........... never ending series)
            Input: 7
            Output: 13
            Explanation: in fobbonaci series pick the nth element from the starting

     */

    public static int fibbonaci(int n) {
//        base condition....
        if(n == 0) {
            return 0;
        }
        if(n== 1 || n==2) {
            return 1;
        }

//        work/faith

        int faith1 = fibbonaci(n-1);
        int faith2 = fibbonaci(n-2);

        int ans = faith1 + faith2;

        return ans;
    }






}
