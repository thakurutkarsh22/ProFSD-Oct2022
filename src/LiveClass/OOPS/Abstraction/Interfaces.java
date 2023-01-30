package LiveClass.OOPS.Abstraction;

interface Animals {


//    Animals() {
//
//    }
  int eyes = 2;
  void walk();

  void talk();

  void eating();

}


interface Herbivorous {
    void eat();
    void carbs();
}

interface Carnivorous {
    void eat();
    void protein();
}

class Alien implements Herbivorous,Carnivorous {
    void advanced() {
        System.out.println("Alien is advanced");
    }

    void spaceship() {
        System.out.println("beautiful spaceship");
    }

    @Override
    public void eat() {
        System.out.println("I eat both the things veg non veg");
    }

    @Override
    public void protein() {
        System.out.println("i need protien  1000 gm");
    }

    @Override
    public void carbs() {
        System.out.println("i need carbs");
    }
}

class HumanM implements Animals, Carnivorous {


  void fight() {

  }

  void mobilephone(){

  }

   void career() {

   }

 @Override
 public void walk() {
  System.out.println("interface walking");
 }

 @Override
 public void talk() {
  System.out.println("interface talkinf");
 }

 @Override
 public void eating() {
  System.out.println("interface eating");
 }

    @Override
    public void eat() {

    }

    @Override
    public void protein() {
        System.out.println("human protien: 100gm");
    }
}
