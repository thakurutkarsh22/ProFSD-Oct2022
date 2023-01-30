package LiveClass.OOPS.Encapsulation.AnotherBank;

import LiveClass.OOPS.Encapsulation.Bank.BankClass;

public class BankSubClass extends BankClass {
//    neighbour
    public static void main(String[] args) {
        BankSubClass bankSubClass = new BankSubClass();
        System.out.println(bankSubClass.protectedBankName);
        System.out.println(bankSubClass.publicBankName);

//        System.out.println(bankSubClass.privateBankName);
//        System.out.println(bankSubClass.defaultBankName);

//        Cant access Private and default values of parent..

    }
}
