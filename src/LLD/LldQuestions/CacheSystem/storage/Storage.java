package LLD.LldQuestions.CacheSystem.storage;

import LLD.LldQuestions.CacheSystem.exceptions.KeyNotFoundException;
import LLD.LldQuestions.CacheSystem.exceptions.StorageFullException;

public interface Storage<Key, Value> {
//    This is for if you want to store cache in hashmap or any other data structure.

    public void add(Key key, Value value) throws StorageFullException;
    public void remove(Key key) throws KeyNotFoundException;
    public Value get(Key key) throws KeyNotFoundException;

    public void displayStorage();
}
