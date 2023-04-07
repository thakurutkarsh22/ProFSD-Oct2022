package Contest.feb26_ModuleContest;

import java.util.*;

public class QuickJudge {
    public static void main(String[] args) {
        // Your code here
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.nextLine();
        HashMap<String, Integer> hm = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String s = sc.nextLine();
            hm.put(s, hm.getOrDefault(s, 0) + 1);
        }
        sc.close();
        int max = 0;
        String ans = "";
        for (String key : hm.keySet()) {
            if (max < hm.get(key)) {
                max = hm.get(key);
                ans = key;
            }
        }
        System.out.print(ans);
    }
}
