package InClassAssignments.OOPS;

public class BankAccount {
    public int balance; // 100
    public String name;

    BankAccount(int b, String n) {
        this.balance = b;
        this.name = n;
    }


    public void depositFund(int balance) {
        this.balance += balance;
        System.out.println(balance);
    }


    public boolean withdrawFund(int askingFund) {
        if(askingFund <= this.balance ) {
            this.balance -= askingFund;
            return true;
        } else {
            return false;
        }
    }

}
