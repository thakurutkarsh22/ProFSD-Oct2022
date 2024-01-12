package LLD.Patterns.StratergyPatternPackage.WithStratergyPattern;

public class Main {
    public static void main(String[] args) {
        Vehicle vehicle = new SportsVehicle();
        vehicle.drive(); // sports drive capability

        Vehicle offrad = new OffroadVehicle();
        offrad.drive(); // sports drive capability

        Vehicle goodsVehicle = new GoodsVehicle();
        goodsVehicle.drive(); // normal drive capability
    }

}
