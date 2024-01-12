package LLD.Patterns.BridgeDesignPattern.Solution.Implementor;

import LLD.Patterns.BridgeDesignPattern.Solution.BreatheImplementor;
import LLD.Patterns.BridgeDesignPattern.Solution.LivingThings;

public class Fish extends LivingThings {

    public Fish(BreatheImplementor breatheImplementor) {
        super(breatheImplementor);
    }
    @Override
    public void breatheProcess() {
        breatheImplementor.breathe();

    }
}
