package LLD.LldQuestions.Logger.LogObservationPattern;

public class FileLoggerObserver implements LogObserver{
    @Override
    public void log(String message) {
        System.out.println("FILE: " + message);
    }
}
