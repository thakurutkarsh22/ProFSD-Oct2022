package LLD.Patterns.AbstractFactoryPattern.Factoryies;

import LLD.Patterns.AbstractFactoryPattern.AbstractFactory.AbstractFactory;
import LLD.Patterns.AbstractFactoryPattern.Car;
import LLD.Patterns.AbstractFactoryPattern.EconomicalCar1;
import LLD.Patterns.AbstractFactoryPattern.EconomicalCar2;

public class EconomicCarFactory implements AbstractFactory {
    @Override
    public Car getInstance(int price) {
        if (price <= 300000) {
            return  new EconomicalCar1();
        } else if (price > 300000) {
            return new EconomicalCar2();
        }

        return null;
    }
}
