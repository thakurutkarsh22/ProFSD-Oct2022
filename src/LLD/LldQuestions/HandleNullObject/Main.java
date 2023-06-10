package LLD.LldQuestions.HandleNullObject;

public class Main {
    public static void main(String[] args) {
        Vehicle vehicle = VehicleFactory.getVehicleObject("Bike");
        printVehicleDetails(vehicle);

        vehicle = VehicleFactory.getVehicleObject("Car");
        printVehicleDetails(vehicle);
    }

    private static void printVehicleDetails(Vehicle vehicle) {
        if(vehicle != null) {
            System.out.println("seating capacity " + vehicle.getSeatCapacity());
            System.out.println("Fuel capacity " + vehicle.getTankCapacity());
        }
    }
}
