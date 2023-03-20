package LLD.Patterns.ObsererPatternPackage;

import LLD.Patterns.ObsererPatternPackage.Observable.IphoneObservableImpl;
import LLD.Patterns.ObsererPatternPackage.Observable.StocksObservable;
import LLD.Patterns.ObsererPatternPackage.Observer.EmailAlertObserverImpl;
import LLD.Patterns.ObsererPatternPackage.Observer.MobileAlertObserverImpl;
import LLD.Patterns.ObsererPatternPackage.Observer.NotificationAlertObserver;

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
