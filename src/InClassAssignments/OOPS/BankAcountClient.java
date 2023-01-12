package InClassAssignments.OOPS;

public class BankAcountClient {
    public static void main(String[] args) {
        BankAccount utkarsh = new BankAccount(100000, "utkarsh");
        System.out.println(utkarsh.name);
        System.out.println(utkarsh.balance);

//        salary day 100000
        utkarsh.depositFund(100000);
        System.out.println(utkarsh.balance);

//        salary day 250
        utkarsh.depositFund(250);
        System.out.println(utkarsh.balance);
    }
}
