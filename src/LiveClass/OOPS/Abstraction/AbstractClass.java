package LiveClass.OOPS.Abstraction;

 abstract class Animal {

     Animal(int eyes) {
         this.eyes = eyes;
     }

     Animal() {

     }

    int eyes = 2;
    abstract void walk();

     static void emotions(){
         System.out.println("emotions");
     }

//    abstract void eating();
    void eating() {
        System.out.println("eating with mouth");
    }
}


class Human extends Animal {


     Human() {
        super(50);
     }

    void think() {
        System.out.println("can thisnk");
    }

    void fight(){

    }

    @Override
    void walk() {
        System.out.println("Human walk on 2 Legs");
    }

}

class Cat extends Animal {

     Cat() {
         super();
     }

    @Override
    void walk() {
        System.out.println("Cat walk on 4 Legs");
    }

    void purr() {
        System.out.println("purr");
    }
}





