package LiveClass.OOPS.Encapsulation.Bank;

public class BankClass {
    String defaultBankName = "defaultBankName";
    public String publicBankName = "publicBankName";
    private String privateBankName = "privateBankName";
    protected String protectedBankName = "ProtectedBankName";

    private String password = "1,#&pop/%";

    private int repoRate = 5;


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

//    Getters .....
    public String getRepoRate() {
        return this.repoRate+"";
    }


//    Setterss .....

    public void setRepoRate(int rate) {
//        if(user == govt || user == rbi) {
            this.repoRate = rate;
//        } else {
//            System.out.println("not authorized");
//        }

    }




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
