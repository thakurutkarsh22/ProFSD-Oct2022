package LLD.Patterns.AbstractFactoryPattern.AbstractFactory;

import LLD.Patterns.AbstractFactoryPattern.Car;

public interface AbstractFactory {
    public Car getInstance(int price);
}
