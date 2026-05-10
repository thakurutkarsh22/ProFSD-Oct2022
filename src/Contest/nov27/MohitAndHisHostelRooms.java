package Contest.nov27;

import java.util.*;
import java.io.*;

public class MohitAndHisHostelRooms {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());
        int T = Integer.parseInt(st.nextToken());
        while (T-- > 0) {
            st = new StringTokenizer(br.readLine());
            int rollNo = Integer.parseInt(st.nextToken());
            String gender = st.nextToken();
            if (rollNo % 2 == 0) {
                if (gender.equals("B"))
                    System.out.println("PAPUM" + "L");
                else
                    System.out.println("PAPUM " + "U");
            } else {
                if (gender.equals("B"))
                    System.out.println("LOHIT " + "L");
                else
                    System.out.println("LOHIT " + "U");
            }
        }
    }
}
