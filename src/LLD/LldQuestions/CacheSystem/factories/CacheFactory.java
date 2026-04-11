package LLD.LldQuestions.CacheSystem.factories;

import LLD.LldQuestions.CacheSystem.evictionPolicy.LRUEvictionPolicy;
import LLD.LldQuestions.CacheSystem.service.CacheService;
import LLD.LldQuestions.CacheSystem.storage.HashMapBasedStorage;

public class CacheFactory<Key, Value> {

    public CacheService<Key, Value> defaultCache(final int capacity) {
        return new CacheService<Key, Value>(new HashMapBasedStorage<Key, Value>(capacity),
                new LRUEvictionPolicy<Key, Value>());
    }
}
