package LiveClass.OOPS.Encapsulation.AnotherBank;

import LiveClass.OOPS.Encapsulation.Bank.BankClass;

public class BankNonSubClass {
    public static void main(String[] args) {
        BankClass bankClass = new BankClass();

//        System.out.println(bankClass.defaultBankName);
//        System.out.println(bankClass.privatenBankName);
//        System.out.println(bankClass.protectedBankName);

        System.out.println(bankClass.publicBankName);
    }
}
