package LLD.AdapterPattern.Client;

import LLD.AdapterPattern.Adaptee.WeightMachineForBabies;
import LLD.AdapterPattern.Adapter.WeightMachineAdapter;
import LLD.AdapterPattern.Adapter.WeightMachineAdapterImpl;

public class client {
    public static void main(String[] args) {
        WeightMachineAdapter weightMachineAdapter = new WeightMachineAdapterImpl(new WeightMachineForBabies());
        System.out.println(weightMachineAdapter.getWeightInKg());
    }
}
