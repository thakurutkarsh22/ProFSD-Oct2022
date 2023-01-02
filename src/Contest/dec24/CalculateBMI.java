package Contest.dec24;

import java.util.*;

public class CalculateBMI {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        double w = sc.nextDouble();
        double h = sc.nextDouble();
        sc.close();
        w *= 0.453592;
        h *= 0.0254;
        double BMI = w / (h * h);
        System.out.printf("%.2f", BMI);
    }
}
