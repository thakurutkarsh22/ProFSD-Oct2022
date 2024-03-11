package LLD.LldQuestions.MultiLevelCache.provider.levelProvider;

import LLD.LldQuestions.MultiLevelCache.models.responses.ReadResponse;
import LLD.LldQuestions.MultiLevelCache.models.responses.WriteResponse;

import java.util.ArrayList;
import java.util.List;

public class NullLevelCache implements ILevelProvider{

//    l1 -> l2 -> l3 -> null
//    somewhere the cache level has to stop so for that we have the null layer handling.
    @Override
    public ReadResponse get(String key) {
        return new ReadResponse(0.0, null);
    }

    @Override
    public WriteResponse put(String key, String value) {
        return new WriteResponse(0.0);
    }

    @Override
    public List<Double> getUsages() {
        return new ArrayList<>();
    }
}
