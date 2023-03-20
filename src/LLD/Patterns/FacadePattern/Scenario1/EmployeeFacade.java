package LLD.Patterns.FacadePattern.Scenario1;

public class EmployeeFacade {

    /*
        The user dosent want all te 1000 methods in EmployeeDao, it is only
        concerned with the insert and gettingEMployeeDetails
     */
    EmployeeDao employeeDao;

    public EmployeeFacade() {
        this.employeeDao = new EmployeeDao();
    }

    public void insert() {
        this.employeeDao.insert();
    }

    public Employee getEmployeeDetails(int empId) {
        return this.employeeDao.getEmployeeDetails(empId);
    }
}
