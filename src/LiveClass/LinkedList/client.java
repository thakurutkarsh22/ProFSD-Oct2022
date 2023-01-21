package LiveClass.LinkedList;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class client {

//    TODO: addAt, deleteAt.

    public static void main(String[] args) {
        LinkedList<String> list = new LinkedList<String>();
        list.add("A");
        list.add("B");
        list.add("C");

        System.out.println(list);

//        Add First -> Add the element in front of the chain ...

        list.addFirst("Z");
        System.out.println(list);

//        Add Last -> Add the element at last of the chain...

        list.addLast("Y");
        System.out.println(list);


        System.out.println(list.getFirst());
        System.out.println(list.getLast());


//        REMOVE -> will remove the first element
        list.remove();

        System.out.println(list);

//        Remove Last -> Will remove from the last of the list( last element in the llist)

        list.removeLast();
        System.out.println(list);

//        Get -> Gives the specied element inthe position

        System.out.println(list.get(0));

//
//        CLONE -> it will give the shallow copy of the Linked list
        System.out.println(list.clone());
//
    }

}
