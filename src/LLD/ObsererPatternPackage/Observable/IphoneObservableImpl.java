package LLD.ObsererPatternPackage.Observable;

import LLD.ObsererPatternPackage.Observer.NotificationAlertObserver;

import java.util.ArrayList;
import java.util.List;

public class IphoneObservableImpl implements StocksObservable {
// iphone is making its own list (bec customer can be different, some customer notify for iphone and some for trimmer)
    public List<NotificationAlertObserver> observerList = new ArrayList<>();
    public int stockCount = 0;

    @Override
    public void add(NotificationAlertObserver observer) {
        this.observerList.add(observer);
    }

    @Override
    public void remove(NotificationAlertObserver observer) {
        this.observerList.remove(observer);
    }

    @Override
    public void notifySubscribers() {
        for (NotificationAlertObserver observer: this.observerList) {
            observer.update();
        }
    }

    @Override
    public void setStockCount(int newStockAdded) {
        int prevStockCount = this.stockCount;

        this.stockCount = stockCount + newStockAdded;
        if(prevStockCount == 0) {
            notifySubscribers();
        }
    }

    @Override
    public int getStockSum() {
        return this.stockCount;
    }
}
