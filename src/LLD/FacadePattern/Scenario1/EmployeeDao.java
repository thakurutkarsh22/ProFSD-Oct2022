package LLD.FacadePattern.Scenario1;

public class EmployeeDao {
    /*
        Dao is data access object: which is a class which interacts with the DB.
     */

    public void insert() {
//        insert into Employee Table
    }

    public void updateEmployeeName() {
        // updating employee name
    }

    public Employee getEmployeeDetails(String emailId) {
        // fet employee details based on emp id
        return new Employee();
    }

    public Employee getEmployeeDetails(int empId) {
        return new Employee();
    }
}
