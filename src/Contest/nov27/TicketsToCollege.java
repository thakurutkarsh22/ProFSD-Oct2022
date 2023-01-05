package Contest.nov27;

import java.io.*;
import java.util.*;

public class TicketsToCollege {
    public static void main(String[] args) throws Exception {
        // Your code here
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int T = Integer.parseInt(br.readLine());
        while (T-- > 0) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            int dateA = Integer.parseInt(st.nextToken());
            int dateB = Integer.parseInt(st.nextToken());
            int dateC = Integer.parseInt(st.nextToken());

            if (dateA <= dateB && dateC <= dateB)
                System.out.println("YES");
            else
                System.out.println("NO");
        }
    }
}
