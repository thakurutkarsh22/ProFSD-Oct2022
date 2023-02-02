package LiveClass.OOPS.Encapsulation.Bank;

public class BankNonClass {

    public static void main(String[] args) {
        BankClass bankClass = new BankClass();

        System.out.println(bankClass.defaultBankName);
        System.out.println(bankClass.publicBankName);
        System.out.println(bankClass.protectedBankName);
//        System.out.println(bankSubClass.priva); // we cant access the private properties  of parent

        System.out.println("------------------");
        bankClass.defaultDisplay();
        bankClass.publicDisplay();
        bankClass.protectedDisplay();
//        bankSubClass.privateDisplay();// // we cant access the private Methods  of parent
    }
}
