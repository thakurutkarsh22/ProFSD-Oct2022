package LLD.StratergyPatternPackage.WithStratergyPattern;

import LLD.StratergyPatternPackage.WithStratergyPattern.Stratergy.DriveStratergy;
import LLD.StratergyPatternPackage.WithStratergyPattern.Stratergy.SportsDriveStratergy;

public class OffroadVehicle extends Vehicle {
//    as offroad vehicle wants sports feature like sports vehicle.

    OffroadVehicle() {
        super(new SportsDriveStratergy());
    }
}
