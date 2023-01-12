package LiveClass.OOPS.Static;

public class BankClient {
    public static void main(String[] args) {
        Bank kotak = new Bank(); // I am creating an instance(new) of Bank

        System.out.println(kotak.interest); //6
        System.out.println(kotak.amount); // 1000
        System.out.println(kotak.simpleInterest(1,2)); // 2
        System.out.println(kotak.compoundInterest(1,3)); // 10


        System.out.println(Bank.amount); // 1000

        System.out.println(Bank.compoundInterest(1,3)); // 10
//        System.out.println(Bank.interest); this will not work

    }

//    int interest = 6; // instance variable ...
//
//    static int amount = 1000;
}
