package LLD.Patterns.ProxyDesignPattern;

public class EmployeeDaoProxy implements EmployeeDao{

    EmployeeDao employeeDao;

    EmployeeDaoProxy() {
        this.employeeDao = new EmployeeDaoImpl();
    }

    @Override
    public void create(String client, EmployeeDo obj) throws Exception {
        if(client.equals("ADMIN")) {
            this.employeeDao.create(client, obj);
            return;
        }

        throw  new Exception("Access denied");
    }

    @Override
    public void delete(String client, int employeeId) throws Exception {
        if(client.equals("ADMIN")) {
            this.employeeDao.delete(client, employeeId);
            return;
        }

        throw  new Exception("Access denied");
    }

    @Override
    public EmployeeDo get(String client, int employeeId) throws Exception {
        if(client.equals("ADMIN") || client.equals("USER")) {
           return this.employeeDao.get(client, employeeId);
        }

        throw  new Exception("Access denied");
    }
}
