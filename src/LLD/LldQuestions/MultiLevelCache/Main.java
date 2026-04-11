package LLD.LldQuestions.MultiLevelCache;

import LLD.LldQuestions.MultiLevelCache.evictionPloicy.LRUEvictionPolicy;
import LLD.LldQuestions.MultiLevelCache.exceptions.StorageFullException;
import LLD.LldQuestions.MultiLevelCache.models.LayerData;
import LLD.LldQuestions.MultiLevelCache.models.responses.ReadResponse;
import LLD.LldQuestions.MultiLevelCache.models.responses.WriteResponse;
import LLD.LldQuestions.MultiLevelCache.provider.cacheProvider.CacheProvider;
import LLD.LldQuestions.MultiLevelCache.provider.levelProvider.DefaultLevelCache;
import LLD.LldQuestions.MultiLevelCache.provider.levelProvider.NullLevelCache;
import LLD.LldQuestions.MultiLevelCache.services.CacheService;
import LLD.LldQuestions.MultiLevelCache.storage.HashmapStorage;

public class Main {
    public static void main(String[] args) throws StorageFullException {
        CacheProvider c1 = createCache(10);
        CacheProvider c2 = createCache(20);

        LayerData cl1 = new LayerData(1.0, 3.0);
        LayerData cl2 = new LayerData(2.0, 4.0);

        DefaultLevelCache l2Cache = new DefaultLevelCache(cl2, c2, new NullLevelCache());
        DefaultLevelCache l1Cache = new DefaultLevelCache(cl1, c1, l2Cache);

        CacheService cacheService = new CacheService(l1Cache, 5);

        WriteResponse w1 = cacheService.put("k1", "v1");
        WriteResponse w2 = cacheService.put("k2", "v2");
//
        System.out.println(w1.getTimeTaken()); // 10
        System.out.println(w2.getTimeTaken()); // 10



        ReadResponse r1 = cacheService.get("k1");
        System.out.println(r1.getValue()); // v1
        System.out.println(r1.getTimeTaken()); // 1


        ReadResponse r2 = cacheService.get("k2");
        System.out.println(r2.getValue()); // v2
        System.out.println(r2.getTimeTaken()); // 1


        // Explicitly remove key from l1 for testing.
//        c1.set("k1", null);
        c1.delete("k1");

        ReadResponse r1AfterRemovalFromL1 = cacheService.get("k1");
        System.out.println(r1AfterRemovalFromL1.getValue()); // "v1"
        System.out.println(r1AfterRemovalFromL1.getTimeTaken()); // 6
//
        ReadResponse reRead = cacheService.get("k1");
        System.out.println(reRead.getValue()); // "v1"
        System.out.println(reRead.getTimeTaken()); // 1
//
//
        WriteResponse reWritingValue = cacheService.put("k1", "v1");
        System.out.println(reWritingValue.getTimeTaken()); // 3
    }

    private static CacheProvider createCache(int capacity) {
        return new CacheProvider(
                new HashmapStorage(capacity),
                new LRUEvictionPolicy())
                ;
    }
}
