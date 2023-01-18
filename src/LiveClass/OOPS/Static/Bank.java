package LiveClass.OOPS.Static;

public class Bank {

    int interest = 6; // instance variable ...

    static int amount = 1000; // static variable

//    instance method....
    public int simpleInterest(int a, int b) {
        return a*b;
    }

    public static int compoundInterest(int a, int b) {
//        static int c =  a* b / 100; // static inside the method is not allowed
//        as static is a CLASS level keyword

        int c =  a* b / 100;

//        a , b, c are local variables ...
        return 10;
    }


    static class abc {

    }


}
