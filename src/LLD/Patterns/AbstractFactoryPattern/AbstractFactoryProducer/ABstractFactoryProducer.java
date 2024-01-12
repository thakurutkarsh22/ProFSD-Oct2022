package LLD.Patterns.AbstractFactoryPattern.AbstractFactoryProducer;

import LLD.Patterns.AbstractFactoryPattern.AbstractFactory.AbstractFactory;
import LLD.Patterns.AbstractFactoryPattern.Factoryies.EconomicCarFactory;
import LLD.Patterns.AbstractFactoryPattern.Factoryies.LuxaryCarFactory;

public class ABstractFactoryProducer {
    public static AbstractFactory getFactoryInstance(String value) {
        if(value.equals("Economic")) {
            return  new EconomicCarFactory();
        } else if (value.equals ("Luxary")) {
            return new LuxaryCarFactory();
        } else {
            return null;
        }
    }
}
