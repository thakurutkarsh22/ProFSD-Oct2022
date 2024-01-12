package LLD.Patterns.StratergyPatternPackage.WithStratergyPattern;

import LLD.Patterns.StratergyPatternPackage.WithStratergyPattern.Stratergy.NormalDriveStratergy;

public class GoodsVehicle extends Vehicle {

    GoodsVehicle() {
        super(new NormalDriveStratergy());
    }
}
