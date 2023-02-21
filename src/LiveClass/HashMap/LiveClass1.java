package LiveClass.HashMap;

import java.util.*;

public class LiveClass1 {
    public static void main(String[] args) {
        HashMap<String, String> maps = new HashMap();

//        ------- Insertion --------------
//        maps.put("user1@1", "utkarsh");
//        maps.put("user2@2", "akash");
//        maps.put("user3@3", "shivam");
////        maps.put("user3@3", "mayank");
////        when we have the same key the value will be over ridden...
//
//        System.out.println(maps);
//
////        ----------- searching -----------
//        System.out.println(maps.get("user2@2") + " value of the key");
//
////        --------- Deletion ----------
//        System.out.println(" ------------- after removing shivam ---------");
////        maps.remove("user3@3");
//
//        System.out.println(maps);
//
//
////        ----------------------- seySet aka keys ----------------
//        Set<String> keys = maps.keySet();
////        System.out.println(keys);
//        for (String key: keys) {
//            System.out.println("hey there key: " + key + " value: " + maps.get(key));
//        }
//
////        -----------------  if there is the key or not ------------
//
//        System.out.println(maps.containsKey("user30@3"));
//
//
////        ------------------ EntrySet -----------
//////        Map.Entry<String, String> entrySet =  maps.entrySet();
////        System.out.println("this is aset asdas " + entrySet.getValue());
////        System.out.println("The set is: " + maps.entrySet());
//
//        ArrayList<String> list = new ArrayList<>();
//
//        for ( Map.Entry<String, String> entry: maps.entrySet()) {
//            String name = entry.getValue();
//            String keyUserName = entry.getKey();
//            list.add(name);
//            System.out.println("Welcome " + name + " with key: " + keyUserName);
//        }
//
//        list.sort((a, b) -> a.compareTo(b));



//        ------- QUESTION -----------
//        Frequency(new int[]{1,2,3,5,1,2,5,6,1,1,2});
        int[] countpairSum = new int[]{1,2,3,4,5,6,7,8,9};
        int countPairSumAns = countPairsOfSumToTarget(countpairSum, 10);
        System.out.println(countPairSumAns);
    }


    /*
        Question: Frequency of items inside the array
        Input: [1,2,3,5,1,2,5,6,1,1,2]
        Output:
     */

    public static void Frequency(int[] arr) {
        HashMap<Integer, Integer> map = new HashMap();

        for (int i = 0; i < arr.length; i++) {
            int key = arr[i];

//            if(!map.containsKey(key)) {
//                map.put(key, 1);
//            } else {
//                int oldVal = map.get(key);
//                map.put(key, oldVal + 1);
//            }

            map.put(key, map.getOrDefault(key, 0) + 1);

        }

        System.out.println(map);
    }

        /*
        Question: Pairs of XOR: Given an array, there are N distinct integers
            .Find the number of unique pairs on intereger where XOr is equals to given
            target value.

            Input: arr: [5, 4, 10, 15, 7, 6]; Target = 5
     */

    public static int countPairsOfXorToTarget(int[] arr, int target) {
        int count = 0;
        HashSet set  = new HashSet();
        for (int i = 0; i < arr.length; i++) {
            int item = arr[i];
            int otherNumber = target ^ item;

            if(set.contains(otherNumber)) {
                count++;
            }
            set.add(item);
        }

        return count;
    }


    /*
        Question: Find out the pair of Integers equal to the target number.
        Input: [1,2,3,4,5,6,7,8,9] target: 10
        Output: 4.
        Explanation: Pairs: (9,1), (2,8), (7,3), (6,4)
     */

    public static int countPairsOfSumToTargetBad(int[] arr, int target) {
        int count = 0;
        for (int i = 0; i < arr.length; i++) {

            for (int j = i+1; j < arr.length; j++) {

                if(target == arr[i] + arr[j]) {
                    count++;
                }
            }
        }
        return count;
    }

//    TC => O(n^2)
//    Sc= > O(1)

    public static int countPairsOfSumToTarget(int[] arr, int target) {
        int count = 0;
        HashSet set  = new HashSet();
        for (int i = 0; i < arr.length; i++) {
            int item = arr[i];
            int otherNumber = target - item;

            if(set.contains(otherNumber)) {
                count++;
            }
            set.add(item);
        }

        return count;

    }

//    TC & Sc = O(n)


    /*
        Question
     */








}
