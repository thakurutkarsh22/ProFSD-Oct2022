package Leetcode.LinkedList;

public class LinkedListCycle14 {
    public static void main(String[] args) {

    }

    public boolean hasCycle(ListNode head) {
        ListNode dummyNode= new ListNode();
        dummyNode.next = head;

        ListNode fastPointer = dummyNode;
        ListNode slowPointer = dummyNode;

        while(fastPointer != null && fastPointer.next != null) {
            slowPointer = slowPointer.next;
            fastPointer = fastPointer.next.next;

            if(fastPointer == slowPointer) {
                return true;
            }
        }

        return false;
    }
}
