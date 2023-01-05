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

        int compulsory4bitFlipNum = 165;
        int compulsory4bitFlipNumans = compulsory4bitFlip(compulsory4bitFlipNum, 4);
        System.out.println(compulsory4bitFlipNumans);



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
        Input:
        Output:
        Explanation:
        HINT:

     */

//    public st

}
