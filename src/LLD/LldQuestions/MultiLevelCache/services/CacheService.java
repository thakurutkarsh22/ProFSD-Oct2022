package LLD.LldQuestions.MultiLevelCache.services;

import LLD.LldQuestions.MultiLevelCache.exceptions.StorageFullException;
import LLD.LldQuestions.MultiLevelCache.models.responses.ReadResponse;
import LLD.LldQuestions.MultiLevelCache.models.responses.StatsResponse;
import LLD.LldQuestions.MultiLevelCache.models.responses.WriteResponse;
import LLD.LldQuestions.MultiLevelCache.provider.levelProvider.ILevelProvider;

import java.util.ArrayList;
import java.util.List;

public class CacheService {


//    we have to store the how many last transactions we need to keep track of

    ILevelProvider multilevelCache;

    public int lastCount;

    public List<Double> lastReadTimes = new ArrayList<>();
    public List<Double> lastWriteTimes = new ArrayList<>();

    public CacheService(ILevelProvider multilevelCache, int lastCount) {
        this.multilevelCache = multilevelCache;
        this.lastCount = lastCount;
    }


//    Cache only supports GET AND PUT , here we will support getSTATS also.

    public WriteResponse put (final String key, final String value) throws StorageFullException {
        WriteResponse writeResponse = this.multilevelCache.put(key, value);
        addTimesInList(this.lastWriteTimes, writeResponse.getTimeTaken());
        return writeResponse;
    }


    public ReadResponse get(final String key) throws StorageFullException {
        ReadResponse readResponse = this.multilevelCache.get(key);
        addTimesInList(this.lastReadTimes, readResponse.getTimeTaken());
        return readResponse;
    }

    public StatsResponse stats() {
        return new StatsResponse(this.getAverageReadTime(), this.getAverageWriteTime(), this.multilevelCache.getUsages());
    }

//    we will also have the supporting algos for calculating the lastReadTimes , lastWriteTimes averages


//    addTimesInList - > this method will add the latest readTIme and writeTimes to their corresponding list.
    private void addTimesInList(List<Double> times, Double time) {
        if(this.lastCount == times.size()) {
//            remmove the element as now we might have 11 elements we only need 10
            times.remove(0);
        }
        times.add(time);
    }


    private Double getAverageReadTime() {
        return this.getSum(this.lastReadTimes)/this.lastReadTimes.size();
    }

    private Double getAverageWriteTime() {
        return this.getSum(this.lastWriteTimes)/this.lastWriteTimes.size();
    }

    private Double getSum(List<Double> times) {
        Double sum = 0.0;
        for (Double time: times) {
            sum += time;
        }
        return sum;
    }
}
