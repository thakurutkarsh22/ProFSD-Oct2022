package LLD.Patterns.Singleton.InitializationTechniques;

public class client {
    public static void main(String[] args) {
        DBConnectionEagerInitialization connection = DBConnectionEagerInitialization.getInstance();
    }
}
