package LLD.LldQuestions.MultiLevelCache.storage;

import LLD.LldQuestions.MultiLevelCache.exceptions.StorageFullException;

import java.util.HashMap;

public class HashmapStorage implements IStorage {

    private final HashMap<String, String> storageMap = new HashMap<>();
    private final int capacity;

    public HashmapStorage(final int capacity) {
        this.capacity = capacity;
    }


    @Override
    public void add(final String key, final String value) throws StorageFullException {
        if(this.storageMap.size() >= this.capacity) {
            throw new StorageFullException();
        }

        this.storageMap.put(key, value);
    }

    @Override
    public String get(String key) {
        return this.storageMap.get(key);
    }

    @Override
    public void delete(String key) {
        this.storageMap.remove(key);
    }

    @Override
    public double getCurrentUsage() {
        return ((double) this.storageMap.size())/((double) this.capacity);
    }
}
