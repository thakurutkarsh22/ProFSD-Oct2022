package LLD.Patterns.CommandPattern.Command;

import LLD.Patterns.CommandPattern.Command.ICommand;
import LLD.Patterns.CommandPattern.Reciever.AirConditioner;

public class TurnACOnCommand implements ICommand {

    AirConditioner ac;
    public TurnACOnCommand(AirConditioner ac) {
        this.ac = ac;
    }


    @Override
    public void execute() {
        ac.turnOnAC();
    }
}
