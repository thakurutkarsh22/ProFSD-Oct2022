package LLD.Patterns.Singleton.SingleThreaded;

public class client {
    public static void main(String[] args) {

        SingletonClass s1 = SingletonClass.getInstance();
        SingletonClass s2 = SingletonClass.getInstance();

        if(s1 == s2) {
            System.out.println("both are equal");
        } else {
            System.out.println("not equal");
        }

        s1.dabatabseRead();

    }
}
