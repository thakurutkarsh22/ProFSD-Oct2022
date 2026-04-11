package LLD.LldQuestions.Logger.LogObservationPattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogObservable {
//    map is sotring the level and list og observer at at level
    Map<Integer, List<LogObserver>> logObservers = new HashMap<>();
    public void addObserver(int level, LogObserver logObserver) {
        List<LogObserver> logObserverList = this.logObservers.
                getOrDefault(level, new ArrayList<LogObserver>());
        logObserverList.add(logObserver);

        this.logObservers.put(level, logObserverList);
    }

    public void removeObserver() {

    }

    public void notifyObserver(int level, String msg) {
        for(Map.Entry<Integer, List<LogObserver>> entry: this.logObservers.entrySet()) {
            if(entry.getKey() == level){
                entry.getValue().forEach(observer -> observer.log(msg));
            }
        }
    }

}
