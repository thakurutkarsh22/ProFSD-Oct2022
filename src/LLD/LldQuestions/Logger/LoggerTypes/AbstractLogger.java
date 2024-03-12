package LLD.LldQuestions.Logger.LoggerTypes;

import LLD.LldQuestions.Logger.LogObservationPattern.LogObservable;

public abstract class AbstractLogger {
    int level;
    AbstractLogger nextLoggingLevel;

    public void setNextLoggingLevel(AbstractLogger abstractLogger) {
        this.nextLoggingLevel = abstractLogger;
    }

    public void logMessage(int level, String msg, LogObservable logObservable){
        if(this.level == level) {
            display(msg, logObservable);
        }
        if(this.nextLoggingLevel != null) {
            this.nextLoggingLevel.logMessage(level, msg, logObservable);
        }


    }

    protected  abstract void display(String message, LogObservable logObservable);
}
