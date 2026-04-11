package LLD.LldQuestions.MultiLevelCache.evictionPloicy;

public interface IEvictionPolicy {

    public void keyAccessed(String key);
    public String evictKey();
}
