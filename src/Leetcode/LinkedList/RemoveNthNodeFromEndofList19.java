package Leetcode.LinkedList;

public class RemoveNthNodeFromEndofList19 {
    public static void main(String[] args) {

    }

    public ListNode removeNthFromEnd(ListNode head, int n) {


        return nthNodeFromLast(n, head);
    }

    public ListNode nthNodeFromLast(int n, ListNode head) {

        ListNode dummyNode = new ListNode(-1000);
        dummyNode.next = head;


        ListNode aheadPointer = dummyNode;
        ListNode slowPointer = dummyNode;

        while(n != -1) {
            aheadPointer = aheadPointer.next;
            n--;
        }

        while(aheadPointer != null) {
            aheadPointer = aheadPointer.next;
            slowPointer = slowPointer.next;
        }

        slowPointer.next = slowPointer.next.next;

        return dummyNode.next;
    }
}
