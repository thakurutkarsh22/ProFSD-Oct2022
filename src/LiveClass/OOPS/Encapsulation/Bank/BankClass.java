package LiveClass.OOPS.Encapsulation.Bank;

public class BankClass {

    public static void main(String[] args) {
        BankClass b = new BankClass();
        System.out.println(b.defaultBankName);
        System.out.println(b.publicBankName);
        System.out.println(b.privateBankName);
        System.out.println(b.protectedBankName);
        System.out.println("-----------------");
        b.defaultDisplay();
        b.publicDisplay();
        b.privateDisplay();
        b.protectedDisplay();

    }

    String defaultBankName = "defaultBankName";
    public String publicBankName = "publicBankName";
    private String privateBankName = "privateBankName";
    protected String protectedBankName = "ProtectedBankName";


    void defaultDisplay() {
        System.out.println("Default Display: Bank");
    }

    public void publicDisplay() {
        System.out.println("Public Display: Bank");
    }

    private void privateDisplay() {
        System.out.println("Private Display: Bank");
    }

    protected void protectedDisplay() {
        System.out.println("protected Display: Bank");
    }





}
