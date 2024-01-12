package LLD.Patterns.AbstractFactoryPattern.Factoryies;

import LLD.Patterns.AbstractFactoryPattern.*;
import LLD.Patterns.AbstractFactoryPattern.AbstractFactory.AbstractFactory;

public class LuxaryCarFactory implements AbstractFactory {
    @Override
    public Car getInstance(int price) {
        if (price >= 1000000 && price < 2000000) {
            return  new LuxaryCar1();
        } else if (price > 2000000) {
            return new LuxaryCar2();
        }
        return null;
    }
}
