package PostClassAssignments.String;

import java.util.Scanner;

public class ResultantString {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = Integer.parseInt(scn.nextLine());
        String str1 = scn.nextLine();
        String str2 = scn.nextLine();

        String result = resultantString(str1, str2);
        System.out.println(result);
    }

    public static String resultantString(String str1, String str2) {
//        "010101" str1
//        "101100" str2
//        "111001"  resultant string

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < str1.length(); i++) {
            char ch1 = str1.charAt(i);
            char ch2 = str2.charAt(i);
            if(ch1 == '0' && ch2 == '1' || ch1 == '1' && ch2 == '0' ) {
                sb.append('1');
            } else {
                sb.append('0');
            }
        }

        return sb.toString();
    }
}
//ussing buffere reader
import java.io.*; // for handling input/output
import java.util.*; // contains Collections framework

// don't change the name of this class
// you can add inner classes if needed
class Main {
    public static void main (String[] args) {
    Scanner sc = new Scanner(System.in);
    int n = sc.nextInt();
    StringBuilder sb1 = new StringBuilder();
    StringBuilder sb2 = new StringBuilder();
    StringBuilder result = new StringBuilder();
      sb1.append(sc.next());
      sb2.append(sc.next());
      for(int i=0; i<n; i++){
          if(sb1.charAt(i)==sb2.charAt(i)){
              result.append("0");
          }else{
              result.append("1");
          }
      }
      System.out.println(result);
    }
}

