package Contest.jan15;

import java.io.*;

import javax.xml.transform.OutputKeys;

public class HelpAnjulika {
    public int[] zeroXor(int l, int r, InputStreamReader in, OutputKeys out) {
        // code goes here
        if ((l & 1) == 1) {
            if (l + 4 <= r) {
                return new int[] { l + 1, l + 2, l + 3, l + 4 };
            } else
                return new int[] { -1 };
        } else {
            return new int[] { l, l + 1, l + 2, l + 3 };
        }
    }
}
