package InClassAssignments.OOPS;

class VipCustomer {
    public String name;
    public String email;
    public double creditLimit;

    VipCustomer() {
        this("XYZ", 10.0, "xyz@abc.com");
    }

    VipCustomer(String name, double creditLimit) {
        this(name,creditLimit, "xyz@abc.com");
    }

    VipCustomer(String name, double creditLimit, String email) {
        this.name = name;
        this.creditLimit = creditLimit;
        this.email = email;
    }

    public String getName () {
        return this.name;
    }

    public double getCreditLimit() {
        return this.creditLimit;
    }

    public String getEmail() {
        return this.email;
    }
}