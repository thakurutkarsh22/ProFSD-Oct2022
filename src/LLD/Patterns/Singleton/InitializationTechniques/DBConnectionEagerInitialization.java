package LLD.Patterns.Singleton.InitializationTechniques;

public class DBConnectionEagerInitialization {
    private static DBConnectionEagerInitialization connectionObj = new DBConnectionEagerInitialization();
    /*
        when class loads this connectionObj will eageerly initalize there n then
     */

    private DBConnectionEagerInitialization() {

    }

    public static DBConnectionEagerInitialization getInstance() {
        return connectionObj;
    }
}
