package InClassAssignments.LinkedList;

import java.util.ArrayList;

class Node{
    int data;
    Node next;
    Node(int d){
        data=d;
        next=null;
    }
}

public class Zerosatfrontoflinkedlist {

    static public Node moveZeroes(Node head)
    {
        ArrayList<Integer> list = new ArrayList<>();
        Node iter = head;
        int zeroCount = 0;

//        Filling the value inside arrayList and incrementing the zero count.

        while(iter !=null) {
            if (iter.data == 0) {
                zeroCount++;
            } else {
                list.add(iter.data);
            }
            iter = iter.next;
        }
        System.out.println(list);

//        assigning the value ...

        iter = head;
        int i = 0;

        while(iter != null) {
            if(zeroCount !=0) {
                iter.data = 0;
                zeroCount--;
            } else {
                int val = list.get(i);
                iter.data = val;
                i++;
            }

            iter = iter.next;
        }

        return head;


    }

}
