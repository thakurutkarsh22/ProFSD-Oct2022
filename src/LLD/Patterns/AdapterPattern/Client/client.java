package LLD.Patterns.AdapterPattern.Client;

import LLD.Patterns.AdapterPattern.Adaptee.WeightMachineForBabies;
import LLD.Patterns.AdapterPattern.Adapter.WeightMachineAdapter;
import LLD.Patterns.AdapterPattern.Adapter.WeightMachineAdapterImpl;

public class client {
    public static void main(String[] args) {
        WeightMachineAdapter weightMachineAdapter = new WeightMachineAdapterImpl(new WeightMachineForBabies());
        System.out.println(weightMachineAdapter.getWeightInKg());
    }
}
