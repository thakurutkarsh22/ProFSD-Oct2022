package LLD.Patterns.CommandPattern.invoker;

import LLD.Patterns.CommandPattern.Command.ICommand;
import LLD.Patterns.CommandPattern.Reciever.AirConditioner;

public class MyRemoteControl {
    private ICommand command;
    MyRemoteControl() {

    }

    public void setCommand(ICommand command) {
        this.command = command;
    }

    public void pressButton() {
        this.command.execute();
    }
}
