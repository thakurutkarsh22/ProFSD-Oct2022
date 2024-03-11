package LLD.LldQuestions.MultiLevelCache.storage;

import LLD.LldQuestions.MultiLevelCache.exceptions.StorageFullException;

public interface IStorage {

    public void add(String key, String value) throws StorageFullException;
    public String get(String key);

    public void delete(String key);

    public double getCurrentUsage();
}
