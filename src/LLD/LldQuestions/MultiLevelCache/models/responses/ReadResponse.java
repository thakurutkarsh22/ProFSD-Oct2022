package LLD.LldQuestions.MultiLevelCache.models.responses;

public class ReadResponse {
//    When I read we get value and time take to retrieven this valie

    Double timeTaken;
    String value;

    public ReadResponse(Double totalTime, String value) {
        this.timeTaken = totalTime;
        this.value = value;
    }

    public Double getTimeTaken() {
        return timeTaken;
    }

    public String getValue() {
        return value;
    }
}
