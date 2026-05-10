package Contest.march12_ModuleContest;

import java.util.*;

public class SecondMostRepeated {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.nextLine();
        String[] str = new String[n];
        for (int i = 0; i < n; i++) {
            str[i] = sc.next();
        }
        sc.close();
        HashMap<String, Integer> hm = new HashMap<>();
        for (String s : str) {
            hm.put(s, hm.getOrDefault(s, 0) + 1);
        }
        int max = -1, secondMax = -1;
        for (String key : hm.keySet()) {
            max = Math.max(max, hm.get(key));
        }
        for (String key : hm.keySet()) {
            int k = hm.get(key);
            if (k != max) {
                secondMax = Math.max(secondMax, k);
            }
        }
        for (String key : hm.keySet()) {
            if (hm.get(key) == secondMax) {
                System.out.print(key);
                return;
            }
        }
    }
}
