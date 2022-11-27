package LiveClass.Strings;

import java.util.Scanner;

public class LiveLectureStringBuilder {
    public static void main(String[] args) {

// stringgs (Immutable)
        String str1 = "hello!";
        System.out.println(str1); // hello!
        System.out.println(str1.length()); // 6
        System.out.println(str1.charAt(1)); // 'e'

//        insert
        System.out.println(str1.concat("there"));

        //        String builder (Mutable)
        StringBuilder sb = new StringBuilder("hey there class");
        System.out.println(sb); // hey there class
        System.out.println(sb.toString()); // hey there class
        System.out.println(sb.length()); // 15
        System.out.println(sb.charAt(2)); // 'y'

//        Insert in betwwen the string
        sb = sb.insert(2, 'a');
        System.out.println(sb); //heay there class

//        Delete the char in String...
        sb = sb.delete(2,3);
        System.out.println(sb); // hey there class


//        substring
        System.out.println(sb.substring(4, 9)); // there


//        insert in the last
        sb.append('s');
        System.out.println(sb);





    }
}
