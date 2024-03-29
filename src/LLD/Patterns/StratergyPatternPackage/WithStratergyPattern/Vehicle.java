package LLD.Patterns.StratergyPatternPackage.WithStratergyPattern;

import LLD.Patterns.StratergyPatternPackage.WithStratergyPattern.Stratergy.DriveStratergy;

public class Vehicle {
    DriveStratergy driveStratergyObj;

//    This is constructor injection
    Vehicle(DriveStratergy driveObj) {
        this.driveStratergyObj = driveObj;
    }

    public void drive() {
        driveStratergyObj.drive();
    }
}
