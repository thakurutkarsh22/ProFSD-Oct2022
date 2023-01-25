package LiveClass.OOPS.Final.Variable;

public class FinalKeywordPrimitive {

    int variable = 12;
    final int finalVariable = 100;

    public static void main(String[] args) {
        FinalKeywordPrimitive obj = new FinalKeywordPrimitive();

        System.out.println(obj.variable);
        obj.variable = 11;
        System.out.println(obj.variable);


        System.out.println(obj.finalVariable);
//        obj.finalVariable = 200; // You a


//        final String slogan;
//        slogan = "asda";
//
//
//        if(true) {
//            slogan = "ada";
//        }





    }

    /*
        1. You can't (RE)* ASSIGN the value of the variable.
     */
}
