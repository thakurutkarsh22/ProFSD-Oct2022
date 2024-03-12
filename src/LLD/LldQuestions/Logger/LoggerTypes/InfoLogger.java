package LLD.LldQuestions.Logger.LoggerTypes;

import LLD.LldQuestions.Logger.LogObservationPattern.LogObservable;

public class InfoLogger extends AbstractLogger{

    public InfoLogger(int level) {
        this.level = level;
    }
    @Override
    protected void display(String message, LogObservable logObservable) {
//        System.out.println("Info: " + message);
        logObservable.notifyObserver(1, "Info: " + message);

    }
}
