package LLD.Patterns.ChainOfResponsibility;

public class InfoLogProcessor extends LogProcessor {
    InfoLogProcessor(LogProcessor logProcessor) {
        super(logProcessor);
    }

    public void log(int logLevel, String message) {
        if(logLevel == super.INFO) {
            System.out.println("INFO: " + message);
        } else {
            super.log(logLevel, message);
        }
    }
}
