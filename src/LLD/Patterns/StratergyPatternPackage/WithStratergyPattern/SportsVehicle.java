package LLD.Patterns.StratergyPatternPackage.WithStratergyPattern;

import LLD.Patterns.StratergyPatternPackage.WithStratergyPattern.Stratergy.SportsDriveStratergy;

public class SportsVehicle extends Vehicle {

    SportsVehicle() {
        super(new SportsDriveStratergy());
    }
}
