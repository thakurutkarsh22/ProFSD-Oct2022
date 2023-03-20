package LLD.Patterns.StratergyPatternPackage.WithStratergyPattern;

import LLD.Patterns.StratergyPatternPackage.WithStratergyPattern.Stratergy.SportsDriveStratergy;

public class OffroadVehicle extends Vehicle {
//    as offroad vehicle wants sports feature like sports vehicle.

    OffroadVehicle() {
        super(new SportsDriveStratergy());
    }
}
