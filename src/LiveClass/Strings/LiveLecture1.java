package LiveClass.Strings;

public class LiveLecture1 {
    public static void main(String[] args) {

//      1st  parent scope - 4 .. 23
//        output2();


        int i = 25;

        i = 14;

        for(int j =0; j< 5;j++) {
            i= 77;

            int a = 12;
            System.out.println(a);
        }

//        int j =10;

       // int i = 50; // hey this i is defined in the scope

        System.out.println(i);

//        for(int i =0; j< 5 ;j++) {
//
////            2nd scope , child
//
//            if() {
//                // 3rd scop childs child
//                int i;
//                int j;
//            }
//
//            int i;
//
//            i = 12;
//        }

        for(int j =0; j< 8 ;j++) {

            i = 90;
        }

        if(true) {
            int j = 12;
        }

        int j = 0;


//




    }

//    public static void output() {
//        for(int k = 0;k<89;k++) {
//            System.out.println(j);
//
//            if(true) {
//                int j = 90;
//                System.out.println(j);
//            }
//
//            System.out.println(j);
//        }
//
//         //any variable defined insdide of a scope/{}
////        it will not leak out confirm line above..
//    }

//    public static void output2() {
//        int i = 2;
//
//        int k = 12;
//
//        System.out.println(i);
//        if(true) {
//            i = 23;
//            int a  = 99;
//            System.out.println(a);
//
//            System.out.println(i);
//        }
//        System.out.println(a);
//        System.out.println(i);
//    }
}


//1: {} are the scope
//2: if we havbe delared anything on parent scope, it will be effective
// on every children and greandchildrens scope...
//3. children(siblings) will not effect each other.