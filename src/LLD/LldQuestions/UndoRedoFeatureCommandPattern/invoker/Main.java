package LLD.LldQuestions.UndoRedoFeatureCommandPattern.invoker;

import LLD.LldQuestions.UndoRedoFeatureCommandPattern.Command.TurnACOnCommand;
import LLD.LldQuestions.UndoRedoFeatureCommandPattern.Reciever.AirConditioner;

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

//        undo Feature
        remoteControl.undo();
    }
}