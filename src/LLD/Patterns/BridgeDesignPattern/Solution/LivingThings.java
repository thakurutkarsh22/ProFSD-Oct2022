package LLD.Patterns.BridgeDesignPattern.Solution;

public abstract class LivingThings {

    public BreatheImplementor breatheImplementor;

    public LivingThings(BreatheImplementor breatheImplementor) {
        this.breatheImplementor = breatheImplementor;
    }
    abstract public void breatheProcess();
}
