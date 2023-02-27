package LLD.StratergyPatternPackage.WithStratergyPattern;

import LLD.StratergyPatternPackage.WithStratergyPattern.Stratergy.DriveStratergy;
import LLD.StratergyPatternPackage.WithStratergyPattern.Stratergy.SportsDriveStratergy;

public class SportsVehicle extends Vehicle {

    SportsVehicle() {
        super(new SportsDriveStratergy());
    }
}
