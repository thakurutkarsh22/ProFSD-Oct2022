package Contest.dec4;

import java.util.*;

public class Pattern {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.close();
        Patterns(n);
    }

    static void Patterns(int N) {
        // Enter your code here
        System.out.println("*");
        for (int i = 1; i < N - 1; i++) {
            System.out.print("*");
            for (int j = 0; j < i; j++) {
                System.out.print("^");
            }
            System.out.print("*");
            System.out.println();
        }
        for (int i = 0; i <= N; i++) {
            System.out.print("*");
        }

        // TODO: Do this in ArrayList<ArrayList<String>>

        System.out.println();

        ArrayList<ArrayList<String>> ans = PatternWithArrayList(N);
        for (int i = 0; i < ans.size(); i++) {
            for (int j = 0; j < ans.get(i).size(); j++) {
                System.out.print(ans.get(i).get(j));
            }
            System.out.println();
        }

    }

    public static ArrayList<ArrayList<String>> PatternWithArrayList(int N) {
        ArrayList<ArrayList<String>> ans = new ArrayList<>();
        ArrayList<String> al = new ArrayList<>();
        al.add("*");
        ans.add(new ArrayList<String>(al));
        al.clear();
        for (int i = 1; i < N - 1; i++) {
            al.add("*");
            for (int j = 0; j < i; j++) {
                al.add("^");
            }
            al.add("*");
            ans.add(new ArrayList<String>(al));
            al.clear();
        }
        for (int i = 0; i <= N; i++) {
            al.add("*");
        }
        ans.add(new ArrayList<String>(al));
        return ans;
    }
}