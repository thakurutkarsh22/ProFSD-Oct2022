package Contest.feb12_ModuleContest;

public class AppleAndOranges {
    static int LikesBoth(int N, int A, int B) {
        // Enter your code here
        return A + B - N;
    }

    // driver code
    public static void main(String[] args) {
        System.out.println(LikesBoth(5, 3, 4));
    }
}
