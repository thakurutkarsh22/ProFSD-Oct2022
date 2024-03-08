package LLD.LldQuestions.CacheSystem.service;

import LLD.LldQuestions.CacheSystem.evictionPolicy.EvictionPolicy;
import LLD.LldQuestions.CacheSystem.evictionPolicy.LRUEvictionPolicy;
import LLD.LldQuestions.CacheSystem.exceptions.KeyNotFoundException;
import LLD.LldQuestions.CacheSystem.exceptions.StorageFullException;
import LLD.LldQuestions.CacheSystem.storage.Storage;

public class CacheService<Key, Value> {

//    Cache service will evict but Eviction policy will tell what to evict.

    Storage<Key, Value> storage;
    EvictionPolicy<Key, Value> evictionPolicy;

    public CacheService(Storage<Key, Value> storage, EvictionPolicy<Key, Value> evictionPolicy) {
        this.storage = storage;
        this.evictionPolicy = evictionPolicy;
    }


    public void put(Key key, Value value) throws StorageFullException, KeyNotFoundException {
        try {
            this.storage.add(key, value);
            this.evictionPolicy.keyAccessed(key);
        } catch (StorageFullException error) {
            Key keyToRemove = this.evictionPolicy.evictKey();
            if(keyToRemove == null) {
                throw new StorageFullException("Un expected thing happened");
            }
            this.storage.remove(keyToRemove);
            this.put(key, value);
        }



    }

    public Value get(Key key) throws KeyNotFoundException {
        try {
            Value value = this.storage.get(key);
            this.evictionPolicy.keyAccessed(key);
            return value;
        } catch (KeyNotFoundException error) {
            return null;
        }

    }

    public void printStorage() {
        this.storage.displayStorage();
    }


}
