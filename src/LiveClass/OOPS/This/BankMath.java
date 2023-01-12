package LiveClass.OOPS.This;

public class BankMath { // DRDO
    public static int totalCostTOCompanyHelper (Bank obj) {


        int nOfEmp = obj.noOfEmployees;
        int salary = obj.salary;

        if(obj.npa > 0) {
            return salary * nOfEmp + obj.npa;
        }

//        if(obj.food > 0) {
//            return salary * nOfEmp + obj.npa + food;
//        }
        return salary * nOfEmp;
    }
}
