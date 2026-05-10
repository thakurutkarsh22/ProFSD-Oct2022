package Contest.march12_ModuleContest;

import java.util.*;

public class DistinctVals {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < n; i++) {
            set.add(sc.nextInt());
        }
        sc.close();
        System.out.print(n - set.size());
    }
}
