package InClassAssignments.Loops;

public class FiveStars {
    public static void main(String[] args) {
        vertical5();
        horizontal5();
    }

    public static void vertical5(){
//Enter your code here
        for(int i=0;i<5;i++) {
            System.out.println("*");
        }

    }

    public static void horizontal5(){
//Enter your code here
        for(int i=0;i<5;i++) {
            System.out.print("* ");
        }
    }
}
