package LLD.LldQuestions.Logger.Singleton;

import LLD.LldQuestions.Logger.LogObservationPattern.LogObservable;
import LLD.LldQuestions.Logger.LogObservationPattern.LogObserver;
import LLD.LldQuestions.Logger.LoggerTypes.AbstractLogger;

import java.io.Serializable;

//Singleton class
public class Logger implements Cloneable, Serializable {
    private volatile static Logger logger;
    private volatile static AbstractLogger chainOfLogger;


//    now we will add the logger output

    private volatile static LogObservable logObservable;

    private  Logger() {
    }

    public static Logger getInstance() {
        if(logger == null) {
            /*
                if you are trying to have safe operations including either:
                    static methods
                    static members of your class
             */
            synchronized ((Logger.class)) {
                if(logger == null) {
                    logger = new Logger();
                    chainOfLogger = LogManager.buildChainOfLogger();
                    logObservable = LogManager.buildLogObserver();
                }
            }
        }
        return logger;
    }

    private void createLog(int level, String msg) {
        chainOfLogger.logMessage(level, msg, logObservable);
    }

    public  void info(String msg) {
        createLog(1, msg);
    }

    public  void error(String msg) {
        createLog(2, msg);
    }

    public  void debug(String msg) {
        createLog(3, msg);
    }

}
