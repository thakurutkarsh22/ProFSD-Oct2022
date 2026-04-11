package LLD.LldQuestions.MultiLevelCache.provider.levelProvider;

import LLD.LldQuestions.MultiLevelCache.exceptions.StorageFullException;
import LLD.LldQuestions.MultiLevelCache.models.responses.ReadResponse;
import LLD.LldQuestions.MultiLevelCache.models.responses.WriteResponse;

import java.util.List;

public interface ILevelProvider {
//    This is a level provider it is not the cache itself think about this
//    Each level will have its own cache (l1 -> redis, l2 -> disk)
//    and each level will also have the stats on its own,

//    level provider will be a LEVEL
    public ReadResponse get(String key) throws StorageFullException;
    public WriteResponse put(String key, String value) throws StorageFullException;

    public List<Double> getUsages();
}
