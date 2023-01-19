package LiveClass.OOPS.Encapsulation.Bank;

public class BankSubClass extends BankClass {

    public static void main(String[] args) {
        BankSubClass bankSubClass = new BankSubClass();
        System.out.println(bankSubClass.defaultBankName);
        System.out.println(bankSubClass.publicBankName);
        System.out.println(bankSubClass.protectedBankName);
//        System.out.println(bankSubClass.priva); // we cant access the private properties  of parent

        System.out.println("------------------");
        bankSubClass.defaultDisplay();
        bankSubClass.publicDisplay();
        bankSubClass.protectedDisplay();
//        bankSubClass.privateDisplay();// // we cant access the private Methods  of parent

    }

}
