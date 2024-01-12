package LLD.LldQuestions.UndoRedoFeatureCommandPattern.invoker;

import LLD.LldQuestions.UndoRedoFeatureCommandPattern.Command.ICommand;
import LLD.LldQuestions.UndoRedoFeatureCommandPattern.Reciever.AirConditioner;

import java.util.Stack;

public class MyRemoteControl {
    Stack<ICommand> acCommandHistory = new Stack<>();
    private ICommand command;
    MyRemoteControl() {

    }

    public void setCommand(ICommand command) {
        this.command = command;
        acCommandHistory.add(command);
    }

    public void pressButton() {
        this.command.execute();
    }

    public void undo() {
            if(!acCommandHistory.isEmpty()) {
                ICommand lastCommand = acCommandHistory.pop();
                lastCommand.undo();
            }
    }

}
