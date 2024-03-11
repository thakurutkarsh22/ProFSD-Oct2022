package LLD.LldQuestions.MultiLevelCache.evictionPloicy;

import java.util.HashMap;

public class LRUEvictionPolicy implements IEvictionPolicy{
    HashMap<String, Node> map = new HashMap<>();

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
    public void keyAccessed(String key) {

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
    public String evictKey() {
        String keyToRemove = (String) tail.previous.getValue();
        map.remove(keyToRemove);

        Node keyNode = tail.previous;
        delete(keyNode);
        return (String) keyNode.getValue();
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
