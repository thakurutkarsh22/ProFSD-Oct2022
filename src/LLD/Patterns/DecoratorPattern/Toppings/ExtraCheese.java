package LLD.Patterns.DecoratorPattern.Toppings;

import LLD.Patterns.DecoratorPattern.BasePizza.BasePizza;

public class ExtraCheese extends Toppings{
    BasePizza basePizza;

    public ExtraCheese(BasePizza obj) {
        this.basePizza = obj;
    }

    @Override
    public int cost() {
        return this.basePizza.cost() + 80;
    }
}
