package LLD.LldQuestions.MultiLevelCache.provider.levelProvider;

import LLD.LldQuestions.MultiLevelCache.exceptions.StorageFullException;
import LLD.LldQuestions.MultiLevelCache.models.LayerData;
import LLD.LldQuestions.MultiLevelCache.models.responses.ReadResponse;
import LLD.LldQuestions.MultiLevelCache.models.responses.WriteResponse;
import LLD.LldQuestions.MultiLevelCache.provider.cacheProvider.CacheProvider;

import java.util.ArrayList;
import java.util.List;

public class DefaultLevelCache implements ILevelProvider{

//    this is a class for a Perticular LEVEL
//    each level will have; Layer Data, REAL CACHE(cache provider) and the pointer to the next level.

    LayerData layerData;
    CacheProvider cacheProvider;
    ILevelProvider nextCacheLevel;


    public DefaultLevelCache(LayerData layerData, CacheProvider cacheProvider, ILevelProvider next) {
        this.layerData = layerData;
        this.cacheProvider = cacheProvider;
        this.nextCacheLevel = next;
    }

    @Override
    public ReadResponse get(String key) throws StorageFullException {
        double totalTime = 0.0;
        String currentLevelValue = this.cacheProvider.get(key);
        totalTime += this.layerData.getReadTime();

        if(currentLevelValue == null) {
            ReadResponse nextLevelValue = this.nextCacheLevel.get(key);
            totalTime += nextLevelValue.getTimeTaken();
            currentLevelValue = nextLevelValue.getValue();

            if(currentLevelValue != null) {
                cacheProvider.set(key, currentLevelValue);
                totalTime += this.layerData.getWriteTime();
            }
        }



        ReadResponse response = new ReadResponse(totalTime, currentLevelValue);
        return response;
    }

    @Override
    public WriteResponse put(String key, String value) throws StorageFullException {
        Double curTime = 0.0;
        String curLevelValue = this.cacheProvider.get(key);
        curTime += this.layerData.getReadTime();
        if (!value.equals(curLevelValue)) {
            this.cacheProvider.set(key, value);
            curTime += this.layerData.getWriteTime();
        }

        curTime += this.nextCacheLevel.put(key, value).getTimeTaken();
        return new WriteResponse(curTime);
    }
    @Override
    public List<Double> getUsages() {
//        do recursion to get the data form the all the level s
        List<Double> answer;
        if(this.nextCacheLevel == null) {
            answer = new ArrayList<Double>();
        } else {
            answer = this.nextCacheLevel.getUsages();
        }

        answer.add(0, this.cacheProvider.getCacheCurrentUsage());
        return answer;
    }
}
