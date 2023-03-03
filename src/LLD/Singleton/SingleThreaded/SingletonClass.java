package LLD.Singleton.SingleThreaded;

public class SingletonClass {

    private static SingletonClass instance;


    /*
        Making constructor private to prevent direct constructor calls with
        new operator.
     */
    private SingletonClass() {

    }

    public static SingletonClass getInstance() {
        if(SingletonClass.instance != null) {
            SingletonClass.instance = new SingletonClass();
        }

        return SingletonClass.instance;
    }


    public static void dabatabseRead() {
        System.out.println("hey there read the database");
    }


}
