package LLD.Patterns.Singleton.InitializationTechniques;

public class DBConnectionSynchronizedMethod {
    private static DBConnectionSynchronizedMethod connectionObj;
    /*
        when class loads this connectionObj will will not be assigned
        it is only assigned when needed.

        this will be thread SAFE
        but there is a problem

        lets say 200 request are comming simultaniously to create the DB connection
        synchronized will do few things
            1. For the 1st request, it will lock the resource and then create the obj.
            2. For the 2nd request, it will lock the resource and then return the previosuly created obj (which is fine).
            3. would be same for all the request from now

            Main part is LOCK -> this is expensive

     */

    private DBConnectionSynchronizedMethod() {

    }

    synchronized public static DBConnectionSynchronizedMethod getInstance() {
        if(connectionObj == null) {
            connectionObj = new DBConnectionSynchronizedMethod();
        }
        
        return connectionObj;
    }
}
