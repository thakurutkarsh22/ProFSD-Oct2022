package LLD.LldQuestions.CacheSystem.storage;

import LLD.LldQuestions.CacheSystem.exceptions.KeyNotFoundException;
import LLD.LldQuestions.CacheSystem.exceptions.StorageFullException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapBasedStorage<Key, Value> implements Storage<Key, Value> {

    Map<Key, Value> map = new HashMap<>();
    private final Integer capacity;

    private boolean isStorageFull() {
        return this.map.size() == this.capacity;
    }

    public HashMapBasedStorage(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void add(Key key, Value value) throws StorageFullException {
        if(this.map.containsKey(key)) {
            this.map.put(key, value);
            return;
        }
        if(isStorageFull()) {
            throw new StorageFullException("Storage is full");
        }
        this.map.put(key, value);
    }

    @Override
    public void remove(Key key) throws KeyNotFoundException {
        if(!this.map.containsKey(key)) throw new KeyNotFoundException("Key is not there in the storage");
        this.map.remove(key);
    }

    @Override
    public Value get(Key key) throws KeyNotFoundException {
        if(!this.map.containsKey(key)) throw new KeyNotFoundException("Key is not there in the storage");
        return this.map.get(key);
    }

    @Override
    public void displayStorage() {
        Set<Map.Entry<Key, Value>> entrySet = this.map.entrySet();

        for(Map.Entry<Key, Value> entry : entrySet) {
            Key key = entry.getKey();
            Value value = entry.getValue();

            System.out.println("Key: Value || " + key + " : " + value);
        }
    }
}
