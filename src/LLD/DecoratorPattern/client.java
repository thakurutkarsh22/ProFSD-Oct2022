package LLD.DecoratorPattern;

import LLD.DecoratorPattern.BasePizza.BasePizza;
import LLD.DecoratorPattern.BasePizza.Margherita;
import LLD.DecoratorPattern.Toppings.ExtraCheese;
import LLD.DecoratorPattern.Toppings.Mushroom;

public class client {
    public static void main(String[] args) {
        // user need margaritha + extra chesse

//        base margeritha cost: 100;
//        Mushroom costs: 150;
//        Cheese cose: 80;

        BasePizza pizzaWithCheese = new ExtraCheese(new Margherita());
        System.out.println(pizzaWithCheese.cost()); // 180

        BasePizza pizzaWithMushroom = new Mushroom(new Margherita());
        System.out.println(pizzaWithMushroom.cost()); // 250

        BasePizza pizzaWithCheeseAnsMushroom = new Mushroom(new ExtraCheese(new Margherita()));
        System.out.println(pizzaWithCheeseAnsMushroom.cost());
    }
}
