package LLD.LldQuestions.Logger.LogObservationPattern;

public class ConsoleLoggerObserver implements LogObserver{
    @Override
    public void log(String message) {
        System.out.println("CONSOLE: " + message);
    }
}
