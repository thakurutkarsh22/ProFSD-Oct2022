package LiveClass.OOPS.Polymorphism.Overloading;

public class client {
    public static void main(String[] args) {
        Math math = new Math();
        System.out.println(math.add(1,2)); // int
//        System.out.println(math.add(1.3 , 55.8)); //double

//        Implicit Typecasting : hey java is on its own typecasting variables.
//        System.out.println(math.add('a', 'b')); // characters

//        Explicit TypeCasting: Hey java somehow cant typecast, user/developer has to explicitly say what to convert to.
        System.out.println(math.add((int)1.3 , (int)55.8));

    }
}
