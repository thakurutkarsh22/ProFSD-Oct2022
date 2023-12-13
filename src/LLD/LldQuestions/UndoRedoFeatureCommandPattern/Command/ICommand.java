package LLD.LldQuestions.UndoRedoFeatureCommandPattern.Command;

public interface ICommand {
    public void execute();

    public void undo();
}
