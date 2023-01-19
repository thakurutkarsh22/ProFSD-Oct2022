package LiveClass.OOPS.Abstraction;

public class abstractClient {
    public static void main(String[] args) {

        Human akash = new Human();
        akash.walk();
        akash.think();
        akash.eating();
        System.out.println("eyes: " + akash.eyes); // 50

        Cat kitty = new Cat();
        kitty.walk();
        kitty.purr();
        kitty.eating();
        kitty.emotions();
        System.out.println("eyes: " + kitty.eyes); // 2

        Animal.emotions();


/*              1. Cant create instance of the ABSTRACT class.
                Animal animal = new Animal();

                2. Abstract classes are never 100% abstracted(hidden, don't care about implementation)
                3. For 100% abstraction USE INTERFACES.
                4. Can have constructors and Static methods
                5. No rule of inheritance is breaking in abstract Class.
                6. Static methods will behave like any other classes
                7. Constructor calling SUPER will behave like any other normal classes.
                8. CLASSES (any type even abstract) cant support MULTIPLE inheritance.
*/








    }
}


//
