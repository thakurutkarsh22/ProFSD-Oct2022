package LLD.Patterns.CommandPattern.Reciever;

public class AirConditioner {
    boolean isOn;
    int temp;

    public void turnOnAC() {
        this.isOn = true;
        System.out.println("AC is ON");
    }

    public void turnOffAC() {
        this.isOn = false;
        System.out.println("AC is Off");
    }

    public void setTemp(int temp) {
        this.temp = temp;
        System.out.println("temp changed to " + temp);
    }
}
