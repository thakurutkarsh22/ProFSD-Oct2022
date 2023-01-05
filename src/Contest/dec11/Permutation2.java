package Contest.dec11;

import java.io.*;
import java.util.*;

public class Permutation2 {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String s = br.readLine();
        ArrayList<String> al = new ArrayList<>();
        ArrayList<String> ans = new ArrayList<>();
        permute(al, s, "");
        Collections.sort(al);
        ans.add(al.get(0));
        for (int i = 1; i < al.size(); i++) {
            if (!((al.get(i - 1)).equals(al.get(i)))) {
                ans.add(al.get(i));
            }
        }
        for (int i = 0; i < ans.size(); i++)
            System.out.print(ans.get(i) + " ");
    }

    public static void permute(ArrayList<String> al, String s, String ans) {
        if (s.length() == 0) {
            al.add(ans);
            return;
        }
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            String res = s.substring(0, i) + s.substring(i + 1);
            permute(al, res, ans + ch);
        }
    }
}
