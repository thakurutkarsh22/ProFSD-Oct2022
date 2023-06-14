package LLD.Patterns.Singleton.InitializationTechniques;

public class DBConnectionDoubleLockingMethod {
    private static DBConnectionDoubleLockingMethod connectionObj;
    /*
    THIS IS widly used in the industry..



        when class loads this connectionObj will will not be assigned
        it is only assigned when needed.

        this will be thread SAFE
        but there is no problem

        Locking will happen only on the initial requests after then no Locking will happen

     */

    private DBConnectionDoubleLockingMethod() {

    }

    public static DBConnectionDoubleLockingMethod getInstance() {
        if(connectionObj == null) {
            synchronized (DBConnectionDoubleLockingMethod.class) {
                if(connectionObj == null) {
                    connectionObj = new DBConnectionDoubleLockingMethod();
                }
            }
        }
        
        return connectionObj;
    }
}
