package LLD.Patterns.FacadePattern.Scenario2;

import LLD.Patterns.FacadePattern.Scenario2.Facade.OrderFacade;

public class client {
    public static void main(String[] args) {
        OrderFacade orderFacade = new OrderFacade();
        orderFacade.createOrder();
    }
}
