package LiveClass.OOPS.Abstraction;

public class interfaceClient {
    public static void main(String[] args) {
        HumanM akash = new HumanM();
        akash.eating();
        akash.walk();
        akash.protein();
        System.out.println(akash.eyes);

        Alien jadu = new Alien();
        jadu.eat();
        jadu.protein();
        jadu.spaceship();
        jadu.protein();



        /*
            1. Interfaces are 100% abstraction
            2. Interfaces don't have constructors.
            3. methods are automatically ABSTRACT inside an interface
            4. Variables are automatically Public final inside interface.
            5. Interface we can Implement Multiple inheritance.
         */
    }
}
