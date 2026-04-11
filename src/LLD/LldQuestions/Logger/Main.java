package LLD.LldQuestions.Logger;

import LLD.LldQuestions.Logger.Singleton.Logger;

public class Main {
    public static void main(String[] args) {
        Logger logger = Logger.getInstance();
        logger.info("this is info");
        logger.error("this is error");
        logger.debug("this is debug");
    }
}
