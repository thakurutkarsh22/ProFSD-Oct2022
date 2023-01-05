package Contest.dec4;

import java.util.*;

public class Pattern {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        Pattern(n);
        ArrayList<ArrayList<String>> ans = PatternWithArrayList(n);
        for (int i = 0; i < ans.size(); i++) {
            for (int j = 0; j < ans.get(i).size(); j++) {
                System.out.print(ans.get(i).get(j));
            }
            System.out.println();
        }
    }

    public static void Pattern(int N) {
        // 1st row
        System.out.println("*");

        // middle ROw ... . . . .

        for (int i = 1; i < N - 1; i++) {
            // starting star
            System.out.print("*");

            // ^ this thing
            for (int j = 0; j < i; j++) {
                System.out.print("^");
            }

            // ending star
            System.out.print("*");
            System.out.println();
        }

        // last row
        for (int i = 0; i <= N; i++) {
            System.out.print("*");
        }
    }

    // TODO: a new function and send ME PR .......

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