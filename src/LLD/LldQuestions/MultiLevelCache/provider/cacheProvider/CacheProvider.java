package LLD.LldQuestions.MultiLevelCache.provider.cacheProvider;

import LLD.LldQuestions.MultiLevelCache.evictionPloicy.IEvictionPolicy;
import LLD.LldQuestions.MultiLevelCache.exceptions.StorageFullException;
import LLD.LldQuestions.MultiLevelCache.storage.IStorage;

public class CacheProvider {
//    THIS IS THE REAL CACHE. It will have the storage and Eviction policy

    IStorage storage;
    IEvictionPolicy evictionPolicy;

    public CacheProvider(IStorage storage, IEvictionPolicy evictionPolicy) {
        this.storage = storage;
        this.evictionPolicy = evictionPolicy;
    }

    public String get(String key) {
        String val = this.storage.get(key);
        this.evictionPolicy.keyAccessed(key);
        return val;
    }

    public void set(String key, String value) throws StorageFullException {
        try {
            this.storage.add(key, value);
            this.evictionPolicy.keyAccessed(key);
        } catch (StorageFullException e) {
//            here it means the storage is full I need to evict one key
            String keyToBeEvicted = this.evictionPolicy.evictKey();
            this.storage.delete(keyToBeEvicted);

            this.set(key, value);
        }
    }

    public void delete(String key) {
        this.storage.delete(key);
    }

    public double getCacheCurrentUsage() {
        return  this.storage.getCurrentUsage();
    }
}
