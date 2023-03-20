package LLD.Patterns.ObsererPatternPackage.Observer;

import LLD.Patterns.ObsererPatternPackage.Observable.StocksObservable;

public class EmailAlertObserverImpl implements NotificationAlertObserver {

    String emailId;
    StocksObservable observable;

    public EmailAlertObserverImpl(String emailId, StocksObservable observable) {
        this.emailId = emailId;
        this.observable = observable;
    }

    @Override
    public void update() {
        sendMail(emailId, "product is in stock  hurry-up");
    }

    private void sendMail(String mailId, String msg) {
        System.out.println("mail send to: " + emailId + "we only have" + this.observable.getStockSum());
    }
}
