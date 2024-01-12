package LLD.Patterns.BridgeDesignPattern.ProblemWithNormalInheritance.Implementor;

import LLD.Patterns.BridgeDesignPattern.ProblemWithNormalInheritance.LivingThings;

public class Fish extends LivingThings {
    @Override
    public void breatheProcess() {
        System.out.println("Breathe through Gills");
    }
}
