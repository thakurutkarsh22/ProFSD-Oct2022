package LiveClass.Bitwise;

public class LiveClass1 {
    public static void main(String[] args) {

//        Left Shift
        int num = 43;
        System.out.println(num << 1); // 86
        System.out.println(num << 2); // 172

//        right shift
        int rightShiftNum = 10;
        System.out.println(rightShiftNum >> 1); // 5

//        complement (1's complement)
        int complementNum = 43;
        System.out.println(~complementNum); // -44
//

//        2's Complement (1's complement + 1)
        int complementNum1 = 43;
        System.out.println(~complementNum1 + 1); //-43


        System.out.println(" ------------- - - - - - - ---------------- - - - --");


//        QUESTIONs

//        int compulsory4bit1Num = 181;
//        int compulsory4bit1Numans = compulsory4bit1(compulsory4bit1Num, 3);
//        System.out.println(compulsory4bit1Numans);

//        int compulsory4bit0Num = 181;
//        int compulsory4bit0Numans = compulsory4bit0(compulsory4bit0Num, 4);
//        System.out.println(compulsory4bit0Numans);

//        int compulsory4bitFlipNum = 165;
//        int compulsory4bitFlipNumans = compulsory4bitFlip(compulsory4bitFlipNum, 4);
//        System.out.println(compulsory4bitFlipNumans);

//        int rightMostSetBitNum = 684;
//        int rightMostSetBitAns = rightMostSetBit(rightMostSetBitNum);
//        System.out.println(rightMostSetBitAns);

//        int countSetBitsNum = 7;
//        int countSetBitsNumAns = countSetBits(countSetBitsNum);
//        System.out.println(countSetBitsNumAns);



//        int isPowerOf2Num = -2147483647;
//        int isPowerOf2NumAns = isPowerOf2(isPowerOf2Num);
//        System.out.println(isPowerOf2NumAns);
//
//        int OneNonRepeatingNumArr[] = {2,3,5,7,5,7,3, 2, 99} ;
//        int OneNonRepeatingNumAns = OneNonRepeating(OneNonRepeatingNumArr);
//        System.out.println(OneNonRepeatingNumAns);


//        int TwoNonRepeatingNumArr[] = {32, 56,56,50,50, 24,21,21} ;
        int TwoNonRepeatingNumArr[] = {89, 56,56,50,50, 24,21,89} ;
        int TwoNonRepeatingNumAns[] = TwoNonRepeating(TwoNonRepeatingNumArr);
        System.out.println(TwoNonRepeatingNumAns[0] + " " + TwoNonRepeatingNumAns[1]);


    }

    /*
        Question: We need to make sure that 4th bit is ALWAYS (SET)
        Input: 181
        Output: 189
        Explanation: (SET) bit is always 1 and not 0.
        HINT: OR Operation
     */

    public static int compulsory4bit1(int num, int position) {
        int mask = 1 << position;
        return mask | num;
    }
//    TC SC = O(1)


        /*
        Question: We need to make sure that 4th bit is ALWAYS (Un-SET)
        Input: 181
        Output: 165
        Explanation: (Un-SET) bit is always 0 and not 1.
        HINT: AND operation

     */

    public static int compulsory4bit0(int num, int position) {
        int mask = 1 << position;
        mask = ~mask;

        return mask & num;
    }
    //    TC SC = O(1)


            /*
        Question: We need to make sure that 4th bit is Flipped.
        Input: 181
        Output: 165

        Input: 165
        Output: 181
        Explanation: At the position flip the bit (if 0 flip it to 1 )and (if 1 flip it to 0).
        HINT: XOR operation

     */

    public static int compulsory4bitFlip(int num, int position) {
        int mask = 1 << position;

        return mask ^ num;
    }
    //    TC SC = O(1)




    /*
        Question: Find the right most set Bit.
        Input: 684
        Output: 4
        Explanation:
        HINT: take 2's complement and do and opration

     */

    public static int rightMostSetBit(int num) {
        int numComplement = ~num;
        int twosComplementOfNum = numComplement  + 1;

        return num & twosComplementOfNum;
    }
    //    TC SC = O(1)


    /*
        Question: Count the set Bit kernighan algo.
        Input: 2696
        Output: 4
        Explanation:
        HINT: find rightMostSetBit and subtract that with the original number till you get original no 0
     */

    public static int countSetBits(int num) {
        int count = 0;
        while(num != 0) {
            int rightMostSetBit = rightMostSetBit(num);
            if (rightMostSetBit != 0) {
                count++;
            }
            num -= rightMostSetBit;
        }

        return count;

    }
    //    TC SC = O(1)

    /*
         Question: Is power of 2.
        Input: 16
        Output: 0
        Explanation:
        HINT: find the n-1 and do & operation
     */

    public static int isPowerOf2(int num) {
        return (num & num -1 );
    }
    //    TC SC = O(1)

    /*
         Question: Find out one non-repeating element in repeating array.
        Input: [2,3,4,7,4,3,7]
        Output: 2
        Explanation:
        HINT: Do xor for every element inside it
     */

    public static int OneNonRepeating(int[] arr) {
        int ans = 0;
        for (int i = 0; i < arr.length; i++) {
            ans ^= arr[i];
        }

        return ans;
    }

//    TC => O(n)
//    SC => O(1)


    /*
        Question: 2 elements non repeating rest of them are repeating
        Input: []
     */

    public static int[] TwoNonRepeating(int[] arr) {
        int xorNum = 0;
        for (int i = 0; i < arr.length; i++) {
            xorNum ^= arr[i];
        }

        int rightMostSetBit = xorNum & -xorNum;


        int group1 = 0; // resembles the rightMostSetBit
        int group2 = 0; // doesNot resembles the right most bit

        for (int i = 0; i < arr.length; i++) {
            int val = arr[i];
            if((val & rightMostSetBit) == 0) {
                group1 ^= val;
            } else {
                group2 ^= val;
            }
        }
        return new int[]{group1, group2};


    }

}
