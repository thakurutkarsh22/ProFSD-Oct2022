import java.util.*;

public class Main {

//TODO: get the implementation of Hashmap (linkedlist).
//    TODO: Get to know more about hash function black box.
//    TODO: Ultron Grid (infinite).
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);

        String s = scan.nextLine();

        findDuplicateCharacter(s);

    }

    public static void findDuplicateCharacter(String s) {

// your code

        HashMap<Character, Integer> hs = new HashMap<>();

        for (int i = 0; i <s.length(); i++) {

            char ch = s.charAt(i);

            if (hs.containsKey(ch)) {

                hs.put(ch, hs.get(ch) + 1);

            } else {

                hs.put(ch, 1);
            }
            }
        for (Map.Entry entry : hs.entrySet()) {

            if ((int) entry.getValue() > 1) {
                System.out.print(entry.getKey() + " ");
            }
        }
        }
    }

