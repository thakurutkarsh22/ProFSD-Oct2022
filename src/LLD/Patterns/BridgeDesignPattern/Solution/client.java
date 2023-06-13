package LLD.Patterns.BridgeDesignPattern.Solution;

import LLD.Patterns.BridgeDesignPattern.Solution.BreatheImplementor;
import LLD.Patterns.BridgeDesignPattern.Solution.ImplementationBreathe.WaterBreatheImplementation;
import LLD.Patterns.BridgeDesignPattern.Solution.Implementor.Fish;
import LLD.Patterns.BridgeDesignPattern.Solution.LivingThings;

public class client {
    public static void main(String[] args) {
        BreatheImplementor fishBreath = new WaterBreatheImplementation();
        LivingThings fishObj = new Fish(fishBreath);

        fishObj.breatheProcess();
    }
}
