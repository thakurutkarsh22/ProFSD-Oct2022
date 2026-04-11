package LLD.LldQuestions.Logger.LoggerTypes;

import LLD.LldQuestions.Logger.LogObservationPattern.LogObservable;

public class DebugLogger extends AbstractLogger{
    public DebugLogger(int level) {
        this.level = level;
    }
    @Override
    protected void display(String message, LogObservable logObservable) {
//        System.out.println("Debug: " + message);
        logObservable.notifyObserver(3, "\"Debug: \" + message");
    }
}