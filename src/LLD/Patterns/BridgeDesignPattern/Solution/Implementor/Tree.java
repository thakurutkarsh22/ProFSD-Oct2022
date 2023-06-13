package LLD.Patterns.BridgeDesignPattern.Solution.Implementor;

import LLD.Patterns.BridgeDesignPattern.Solution.BreatheImplementor;
import LLD.Patterns.BridgeDesignPattern.Solution.LivingThings;

public class Tree extends LivingThings {

    public Tree(BreatheImplementor breatheImplementor) {
        super(breatheImplementor);
    }

    @Override
    public void breatheProcess() {
        this.breatheImplementor.breathe();
    }
}
