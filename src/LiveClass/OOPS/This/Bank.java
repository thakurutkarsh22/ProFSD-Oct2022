package LiveClass.OOPS.This;

public class Bank { // RBI
    static String mission = "To digitilazie India"; // class variable
    int interest = 6;
    int noOfEmployees = 50;
    int salary = 20000; // instance variable

    int simpleInt = 1;

    int npa;


    Bank(int interest, int noOfEmployees, int salary) {
//        i e s are local variable
        this.interest = interest;
        this.noOfEmployees = noOfEmployees;
        this.salary = salary;
        this.simpleInt = this.simpleInterest(12, 12);
    }

    Bank(int simpleInt) {
        this.simpleInt = simpleInt;
    }

    Bank() {
        this(1);
    }

    public int totalCostToCompany( ) {
//        return BankMath.totalCostTOCompanyHelper(this);
        return this.salary * this.noOfEmployees;
    }

//    Its usefull use is in Multiple classes ....
//    public int totalCostTOCompanyHelper (Bank obj) {
//        int nOfEmp = obj.noOfEmployees;
//        int salary = obj.salary;
//        return salary * nOfEmp;
//    }



    static int amount = 1000;
    public int simpleInterest(int a, int b) {
        return a*b;
    }

    public static int compoundInterest(int a, int b) {
        int c =  a* b / 100;
        return 10;
    }


}

/*
    THis main points
    1. DIfferentiating between the local variable and instance variables ...
    2. to access the INSTANCE variables and INSTANCE methods ..
    3. constructor calling....
    4. we can pass #this as an ARGUMENT..
 */
