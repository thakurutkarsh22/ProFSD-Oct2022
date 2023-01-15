package LiveClass.OOPS.Static;

public class StaticCalling {
    public static void main(String[] args) {
        int num1 = 1;
        int num2 = 2;

//        int result  = sum(num1, num2);
//        this will not work



    }

    public int sum(int a, int b) {
        sendHello();
        return a +b;
    }

    public  String sendHello() {
        return "hello";
    }

}
