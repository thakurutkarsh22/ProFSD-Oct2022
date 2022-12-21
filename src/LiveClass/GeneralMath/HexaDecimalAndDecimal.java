package LiveClass.GeneralMath;

public class HexaDecimalAndDecimal {
    public static void main(String[] args) {
        String hexaDecimalNo = "394FF";

//        ans = 234751;

        int ans = HexaDecimalToDecimal(hexaDecimalNo);
        System.out.println(ans);
    }

    /*
         Question: Conversion Hexa-decimal number to Decimal
        Input: "394FF"
        Output: 234751
     */


    public static int HexaDecimalToDecimal(String num) {

        int ans = 0;
        int power= 0;

        for (int i = num.length()-1; i >= 0; i--) {
            char ch = num.charAt(i);

            if(ch >= '0' && ch <= '9') {
                int noRepresenting = ch - '0';
                ans += noRepresenting * Math.pow(16, power);
                power++;
            } else if (ch >= 'A' && ch <= 'F') {
                int noRepresenting = ch - 'A' + 10;
                ans += noRepresenting * Math.pow(16, power);
                power++;
            }
        }
        return ans;
    }


}
