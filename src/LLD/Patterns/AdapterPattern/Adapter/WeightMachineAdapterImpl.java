package LLD.Patterns.AdapterPattern.Adapter;

import LLD.Patterns.AdapterPattern.Adaptee.WeighMachine;

public class WeightMachineAdapterImpl implements WeightMachineAdapter {

    WeighMachine weightMachine;

    public WeightMachineAdapterImpl(WeighMachine weightMachine) {
        this.weightMachine = weightMachine;
    }

    @Override
    public double getWeightInKg() {
        return this.weightMachine.getWeighInPound() * 0.45;
    }
}
