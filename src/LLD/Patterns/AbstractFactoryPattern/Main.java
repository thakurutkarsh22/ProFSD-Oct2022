package LLD.Patterns.AbstractFactoryPattern;

import LLD.Patterns.AbstractFactoryPattern.AbstractFactory.AbstractFactory;
import LLD.Patterns.AbstractFactoryPattern.AbstractFactoryProducer.ABstractFactoryProducer;

public class Main {
    public static void main(String[] args) {
//        ABstractFactoryProducer aBstractFactoryProducer = new ABstractFactoryProducer();
        AbstractFactory abstractFactoryObj = ABstractFactoryProducer.getFactoryInstance("Luxary");
        Car carObj = abstractFactoryObj.getInstance(50000000);
        System.out.println(carObj.getTopSpeed());
    }
}
