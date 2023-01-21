package LiveClass.OOPS.Final.Variable;

import java.util.ArrayList;

public class FinalKeywordNonPrimitive {
    public static void main(String[] args) {

        int a = 12;
        a = 10;

        ArrayList l = new ArrayList();
        l.add("abcd");

        l = new ArrayList();



        final ArrayList list = new ArrayList(); // 123@xyz

        System.out.println(list);

        list.add("abcd");
        list.add("hello");

        list.set(0, "bye");

        System.out.println(list);

//        list = new ArrayList(); // this will not work


    }


 /*
    1. Mutation on the nonPrimitive is basically allowed and it makes sense
    2. Why? Bec we know the value is the address that is not changing.
  */
}
