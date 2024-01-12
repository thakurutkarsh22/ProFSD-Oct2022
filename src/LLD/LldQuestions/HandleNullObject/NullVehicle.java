package LLD.LldQuestions.HandleNullObject;

public class NullVehicle implements Vehicle{
    @Override
    public int getTankCapacity() {
        return 0;
    }

    @Override
    public int getSeatCapacity() {
        return 0;
    }
}
