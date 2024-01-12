package LLD.Patterns.CommandPattern.Command;

import LLD.Patterns.CommandPattern.Reciever.AirConditioner;

public class TurnACOffCommand implements ICommand {

    AirConditioner ac;
    TurnACOffCommand(AirConditioner ac) {
        this.ac = ac;
    }


    @Override
    public void execute() {
        ac.turnOffAC();
    }
}
