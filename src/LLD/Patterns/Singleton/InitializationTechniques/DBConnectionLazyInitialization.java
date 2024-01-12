package LLD.Patterns.Singleton.InitializationTechniques;

public class DBConnectionLazyInitialization {
    private static DBConnectionLazyInitialization connectionObj;
    /*
        when class loads this connectionObj will will not be assigned
        it is only assigned when needed.

        This is not thread safe
        if 2 threads come then 2 objects will be created instead on one in the memory
        so for this SYNCHRONIZED METHOD is used
     */

    private DBConnectionLazyInitialization() {

    }

    public static DBConnectionLazyInitialization getInstance() {
        if(connectionObj == null) {
            connectionObj = new DBConnectionLazyInitialization();
        }

        return connectionObj;
    }
}
