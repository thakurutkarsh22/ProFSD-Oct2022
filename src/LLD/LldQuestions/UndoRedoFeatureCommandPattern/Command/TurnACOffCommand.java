package LLD.LldQuestions.UndoRedoFeatureCommandPattern.Command;

import LLD.LldQuestions.UndoRedoFeatureCommandPattern.Reciever.AirConditioner;

public class TurnACOffCommand implements ICommand {

    AirConditioner ac;
    TurnACOffCommand(AirConditioner ac) {
        this.ac = ac;
    }


    @Override
    public void execute() {
        ac.turnOffAC();
    }

    @Override
    public void undo() {
        ac.turnOnAC();
    }
}
