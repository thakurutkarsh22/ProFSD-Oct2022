package Leetcode.LinkedList;

public class MiddleoftheLinkedList876 {
    public static void main(String[] args) {

    }

    public ListNode middleNode(ListNode head) {
        ListNode fastPointer = head;
        ListNode slowPointer = head;

        while(fastPointer != null && fastPointer.next != null) {
            fastPointer = fastPointer.next.next;
            slowPointer = slowPointer.next;
        }

        return slowPointer;
    }
}
