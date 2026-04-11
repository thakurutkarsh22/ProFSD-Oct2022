package LLD.LldQuestions.Logger.LoggerTypes;

import LLD.LldQuestions.Logger.LogObservationPattern.LogObservable;

public class ErrorLogger extends AbstractLogger{
    public ErrorLogger(int level) {
        this.level = level;
    }
    @Override
    protected void display(String message, LogObservable logObservable) {
//        System.out.println("Error: " + message);
        logObservable.notifyObserver(2, "Error: " + message);

    }
}