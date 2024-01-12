package LLD.LldQuestions.HandleNullObject;

public class Car implements Vehicle{
    @Override
    public int getTankCapacity() {
        return 40;
    }

    @Override
    public int getSeatCapacity() {
        return 5;
    }
}
