package LLD.LldQuestions.CacheSystem.evictionPolicy;

public interface EvictionPolicy<Key, Value> {

    public void keyAccessed(Key key);
    public Key evictKey();
}
