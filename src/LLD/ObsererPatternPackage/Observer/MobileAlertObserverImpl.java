package LLD.ObsererPatternPackage.Observer;

import LLD.ObsererPatternPackage.Observable.StocksObservable;

public class MobileAlertObserverImpl implements NotificationAlertObserver{

    String mobileNumber;
    StocksObservable observable;

    public MobileAlertObserverImpl(String mobileNumber, StocksObservable observable) {
        this.mobileNumber = mobileNumber;
        this.observable = observable;
    }
    @Override
    public void update() {
       sendMessageOnMobile(this.mobileNumber, "product is in stock hurry!!");
    }

    private void sendMessageOnMobile(String mobileNumber, String msg) {
        System.out.println("msg sent to:" + mobileNumber + " limited stock upto " + this.observable.getStockSum() );
    }
}
