package LLD.DecoratorPattern.Toppings;

import LLD.DecoratorPattern.BasePizza.BasePizza;

public class Mushroom extends Toppings{

    BasePizza basePizza;
    int mushroomCost = 150;

    public Mushroom (BasePizza obj) {
         this.basePizza = obj;
    }

    @Override
    public int cost() {
        return this.basePizza.cost() + this.mushroomCost;
    }
}
