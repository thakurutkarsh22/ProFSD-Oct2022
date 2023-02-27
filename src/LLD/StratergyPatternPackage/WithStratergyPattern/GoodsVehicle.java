package LLD.StratergyPatternPackage.WithStratergyPattern;

import LLD.StratergyPatternPackage.WithStratergyPattern.Stratergy.DriveStratergy;
import LLD.StratergyPatternPackage.WithStratergyPattern.Stratergy.NormalDriveStratergy;
import LLD.StratergyPatternPackage.WithStratergyPattern.Stratergy.SportsDriveStratergy;

public class GoodsVehicle extends Vehicle {

    GoodsVehicle() {
        super(new NormalDriveStratergy());
    }
}
