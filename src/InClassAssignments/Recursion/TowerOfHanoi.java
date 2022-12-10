package InClassAssignments.Recursion;

public class TowerOfHanoi {
    public static void main(String[] args) {
        toh(4, 1,3,2);
    }

    public static void toh(int n, int source_rod, int destination_rod, int helper_Rod) {
//        base case
        if(n ==0 ) {
            return;
        }

//        work and faith
        toh(n-1, source_rod, helper_Rod, destination_rod);
        System.out.println("disk is moving " + n + " source rod " + source_rod + " destination rod " + destination_rod);
        toh(n-1, helper_Rod, destination_rod, source_rod);

    }

}
