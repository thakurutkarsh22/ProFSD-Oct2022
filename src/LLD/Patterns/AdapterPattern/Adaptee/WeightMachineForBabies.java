package LLD.Patterns.AdapterPattern.Adaptee;

public class WeightMachineForBabies implements WeighMachine{
    @Override
    public double getWeighInPound() {
        return 20;
    }
}
