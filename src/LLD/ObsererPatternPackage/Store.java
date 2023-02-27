package LLD.ObsererPatternPackage;

import LLD.ObsererPatternPackage.Observable.IphoneObservableImpl;
import LLD.ObsererPatternPackage.Observable.StocksObservable;
import LLD.ObsererPatternPackage.Observer.EmailAlertObserverImpl;
import LLD.ObsererPatternPackage.Observer.MobileAlertObserverImpl;
import LLD.ObsererPatternPackage.Observer.NotificationAlertObserver;

public class Store {
    public static void main(String[] args) {
        StocksObservable iphoneObservable = new IphoneObservableImpl();

        NotificationAlertObserver user1 = new EmailAlertObserverImpl("user1@gmail.com", iphoneObservable);
        NotificationAlertObserver user2 = new EmailAlertObserverImpl("user2@gmail.com", iphoneObservable);
        NotificationAlertObserver user3 = new MobileAlertObserverImpl("9988771190", iphoneObservable);

        iphoneObservable.add(user1);
        iphoneObservable.add(user3);
        iphoneObservable.add(user2);

        iphoneObservable.setStockCount(10);
    }
}
