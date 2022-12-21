package LiveClass.GeneralMath;

public class DecimalAndOctal {
    public static void main(String[] args) {
        int n = 11;
        String ansOctal = decimalToOctal(n);
        System.out.println(ansOctal);


        int octalN = 13;
        int ansDecimal = OctalToDecimal(octalN);
        System.out.println(ansDecimal);

    }


    /*
        Question: Conversion Decimal number to Octal
        Input: 11
        Output: 1 3
        Explanation: 1 and 3 digits from octal.
     */

    public static String decimalToOctal(int num) {

        StringBuilder sb = new StringBuilder();
        while(num > 0) {
            int remainder = num % 8;
            num /= 8;
            sb.append(remainder);
        }
        return sb.reverse().toString();
    }

//    TC=> O(log(n))
//    SC=> O(1)


     /*
         Question: Conversion Octal number to Decimal
        Input: 13
        Output: 11
     */


    public static int OctalToDecimal(int num) {

//      int  num = 101

        int power = 0;
        int decimalNumber = 0;

        while(num > 0) {
            int remainder = num % 10;
            num = num/10;
            decimalNumber += remainder * Math.pow(8, power);
            power++;
        }

        return decimalNumber;

    }

//    TC => O(log(n))
//    SC => O(1)
}
