package LLD.Patterns.ProxyDesignPattern;

public class client {
    public static void main(String[] args) {
        try {
            EmployeeDao employeeDaoObj = new EmployeeDaoProxy();
//            employeeDaoObj.create("USER", new EmployeeDo());
            employeeDaoObj.create("ADMIN", new EmployeeDo());
            System.out.println("Operation successfull");
        }catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
