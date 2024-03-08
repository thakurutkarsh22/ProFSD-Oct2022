package LLD.LldQuestions.CacheSystem;

import LLD.LldQuestions.CacheSystem.factories.CacheFactory;
import LLD.LldQuestions.CacheSystem.service.CacheService;

public class Main {
    public static void main(String[] args) throws Exception {
        CacheService<Integer, Integer> cache = new CacheFactory<Integer, Integer>().defaultCache(3);

        cache.put(1, 1);
        cache.put(2, 2);
        cache.put(3, 3);
        cache.put(3, 4);
        cache.put(3, 300);
        cache.put(4, 4);


        System.out.println(cache.get(1));
        System.out.println(cache.get(3));
        System.out.println(cache.get(4));

//        cache.put(4, 4);
//        cache.put(1, 100);
//
//        System.out.println(cache.get(2));
//        System.out.println(cache.get(4));

        cache.printStorage();




    }
}
