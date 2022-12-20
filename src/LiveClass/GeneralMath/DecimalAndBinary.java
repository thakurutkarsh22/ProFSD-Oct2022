package LiveClass.GeneralMath;

public class DecimalAndBinary {
    public static void main(String[] args) {
//        int num = 10;
//        String ans = decimalToBinary(num);
//        System.out.println(ans);

        int binaryNum = 1111110001;
        int decimalAns = BinaryToDecimal(binaryNum);
        System.out.println(decimalAns);


    }

    /*
        Question: Conversion Decimal number to Binary
        Input: 5
        Output: 1 0 1
     */

    public static String decimalToBinary(int num) {

        StringBuilder sb = new StringBuilder();
        while(num > 0) {
            int remainder = num % 2;
            num /= 2;
            sb.append(remainder);
        }
        return sb.reverse().toString();
    }

//    TC => O(log(n))
//    Sc => O(1) in reality it can be O(32)

    /*
         Question: Conversion Binary number to Decimal
        Input: 1 0 1
        Output: 5
     */


    public static int BinaryToDecimal(int num) {

//      int  num = 101

        int power = 0;
        int decimalNumber = 0;

        while(num > 0) {
            int remainder = num % 10;
            num = num/10;
            decimalNumber += remainder * Math.pow(2, power);
            power++;
        }

        return decimalNumber;

    }

//    public static int BinaryToDecimal(String num) {
////        101010101111000011110001010101110001
//    }
}
