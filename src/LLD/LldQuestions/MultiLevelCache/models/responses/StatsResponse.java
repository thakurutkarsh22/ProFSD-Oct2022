package LLD.LldQuestions.MultiLevelCache.models.responses;

import java.util.List;

public class StatsResponse {
//    this give stastics about the average read time and average write time and the usage at each level

    Double averageReadTime;
    Double averageWriteTime;

    List<Double> usageStats; // taks about the usage of cache at each level

    public StatsResponse(Double averageReadTime, Double averageWriteTime,  List<Double> usageStats ) {
        this.averageReadTime = averageReadTime;
        this.averageWriteTime = averageWriteTime;
        this.usageStats = usageStats;
    }
}
