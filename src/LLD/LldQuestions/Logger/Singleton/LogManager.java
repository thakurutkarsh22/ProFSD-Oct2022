package LLD.LldQuestions.Logger.Singleton;

import LLD.LldQuestions.Logger.LogObservationPattern.ConsoleLoggerObserver;
import LLD.LldQuestions.Logger.LogObservationPattern.FileLoggerObserver;
import LLD.LldQuestions.Logger.LogObservationPattern.LogObservable;
import LLD.LldQuestions.Logger.LogObservationPattern.LogObserver;
import LLD.LldQuestions.Logger.LoggerTypes.AbstractLogger;
import LLD.LldQuestions.Logger.LoggerTypes.DebugLogger;
import LLD.LldQuestions.Logger.LoggerTypes.ErrorLogger;
import LLD.LldQuestions.Logger.LoggerTypes.InfoLogger;

public class LogManager {

    protected static AbstractLogger buildChainOfLogger() {
        AbstractLogger infoLogger = new InfoLogger(1);
        AbstractLogger errorLogger = new ErrorLogger(2);
        AbstractLogger debugLogger = new DebugLogger(3);

        infoLogger.setNextLoggingLevel(errorLogger);
        errorLogger.setNextLoggingLevel(debugLogger);

        return infoLogger;
    }

    protected static LogObservable buildLogObserver() {
        ConsoleLoggerObserver consoleLoggerObserver = new ConsoleLoggerObserver();
        FileLoggerObserver fileLoggerObserver = new FileLoggerObserver();
        LogObservable logObservable = new LogObservable();

        logObservable.addObserver(1, consoleLoggerObserver);
        logObservable.addObserver(1, fileLoggerObserver);

        logObservable.addObserver(2, fileLoggerObserver);
        logObservable.addObserver(2, consoleLoggerObserver);
        logObservable.addObserver(3, fileLoggerObserver);
        logObservable.addObserver(3, consoleLoggerObserver);


        return logObservable;
    }
}
