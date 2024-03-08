package LLD.LldQuestions.CacheSystem.evictionPolicy;

//import LiveClass.LinkedList.LinkedList;
import LiveClass.LinkedList.doublyLinkedList.DoublyLinkedList;

import java.util.HashMap;
import java.util.LinkedList;

public class LRUEvictionPolicy<Key, Value> implements EvictionPolicy<Key, Value> {

    HashMap<Key, Node> map = new HashMap<>();

    Node head = new Node("HEAD");
    Node tail = new Node("TAIL");

    public LRUEvictionPolicy() {
        this.head.next = this.tail;
        this.tail.previous = this.head;
    }


    private void add(Node node){
        Node headNext = head.next;
        head.next = node;
        node.previous = head;
        node.next = headNext;
        headNext.previous = node;
    }

    private void delete (Node keyNode){
        Node keyNodePrev = keyNode.previous;
        Node keyNodeNext = keyNode.next;
        keyNodePrev.next = keyNodeNext;
        keyNodeNext.previous = keyNodePrev;
    }


    @Override
    public void keyAccessed(Key key) {
//        EDIT
        if(this.map.containsKey(key)) {
            Node keyNode = map.get(key);
            delete(keyNode);
            add(keyNode);
        } else {
//       ADD NEW KEY
            Node newNode = new Node(key);
            map.put(key, newNode);
            add(newNode);
        }
    }

    @Override
    public Key evictKey() {
        Key keyToRemove = (Key) tail.previous.getValue();
        map.remove(keyToRemove);

        Node keyNode = tail.previous;
        delete(keyNode);
        return (Key) keyNode.getValue();
    }


    public class Node<Key>{
        Key value;

        Node previous;
        Node next;

        Node(Key value) {
            this.value = value;
        }

        public Key getValue() {
            return this.value;
        }

    }
}


