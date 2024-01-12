package LLD.Patterns.ChainOfResponsibility;

public class Main {
    public static void main(String[] args) {
        LogProcessor logObject = new InfoLogProcessor(new DebugLogProcessor(new ErrorLogProcessor(null)));

        logObject.log(LogProcessor.ERROR, "exceptions happens");
        logObject.log(LogProcessor.DEBUG, "Debug this");
        logObject.log(LogProcessor.INFO, "information");
    }
}
