package LLD.Patterns.CommandPattern.invoker;

import LLD.Patterns.CommandPattern.Command.TurnACOnCommand;
import LLD.Patterns.CommandPattern.Reciever.AirConditioner;

public class Main {
    public static void main(String[] args) {

//        AC object
        AirConditioner airConditioner = new AirConditioner();

//        remote
        MyRemoteControl remoteControl = new MyRemoteControl();

//        creation of command
        remoteControl.setCommand(new TurnACOnCommand(airConditioner));

//        pressing the button
        remoteControl.pressButton();
    }
}