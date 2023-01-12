package LiveClass.OOPS.Static;

public class StaticBlock {
//    initializatoni purpose
    static {
        System.out.println("hey there static bblock "); // 1
    }

    static {
        System.out.println("hey there static bblock 2nd "); // 2
    }
    public static void main(String[] args) {
//        runAllStaticBlock();

        System.out.println("i am main"); // 3
        saySomething();
    }

    static {
        System.out.println("hey there static bblock 3nd "); // 2
    }

    public static void saySomething() {
        System.out.println("hey thre"); // 4

        return;
    }
}
