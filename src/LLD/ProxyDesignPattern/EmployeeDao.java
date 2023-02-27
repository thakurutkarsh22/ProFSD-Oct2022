package LLD.ProxyDesignPattern;

public interface EmployeeDao {

    public void create(String client, EmployeeDo obj) throws Exception;
    public void delete(String client, int employeeId) throws Exception;
    public EmployeeDo get(String client, int employeeId) throws Exception;
}
