package LiveClass.OOPS.Polymorphism.Overriding;

import java.util.Scanner;

public class client {
    public static void main(String[] args) {
//        MSFlat multiflat = new MSFlat();
//        System.out.println(multiflat.money());
//
//        System.out.println(multiflat.toString());


//        ------------ COMBINATION 1. Parent - parent -----------------
//        SSFlat singleStory = new SSFlat();
//        System.out.println(singleStory.color);
//        System.out.println(singleStory.lift);
//        singleStory.canSeeGround();
//        singleStory.canSeeSky();// this method is inside msflat
//        System.out.println(singleStory.flatId); //same reason as abve

//       If i am not able to find the method in SSFlat i will go up the hirarchy
//        singleStory.dhamal();



//        ------------ COMBINATION  2. child child ------------------
//        MSFlat multiStory = new MSFlat();
//        System.out.println(multiStory.flatId); // property of MSFlat..
//        System.out.println(multiStory.area); // property of SSFlat....
//        System.out.println(multiStory.length);  // property of MSFlat..
//        System.out.println(multiStory.bredth);  // property of MSFlat..
//        System.out.println(multiStory.lift);  // property of MSFlat..
//
//        multiStory.canSeeSky(); // Method of MSFlat..
//        multiStory.canSeeGround(); // Methdo of SSFlat...
//        System.out.println(multiStory.money()); // overridden methods will win bec RHS will decide on runtime ...


        // //------------- COMBINATIOIN        3. parent - child ----------
//        SSFlat singleStory2 = new MSFlat();
//        System.out.println(singleStory2.floors); //property of SSFlat....
//        System.out.println(singleStory2.color);//property of SSFlat....
//        System.out.println(singleStory2.lift);; // property of SSFlat....
////        System.out.println(singleStory2.parking); // property of MSFLAT which we cant access
//
//        singleStory2.canSeeGround();
//        singleStory2.canSeeSky(); // property of MSFLAT which we cant access

//        WE can access OVERRIDDEN METHODS of MSFlat only /.

//        System.out.println(singleStory2.money());
//        singleStory2.abc();


//         ------- COMBINATION 4. child -parent ----------
//        MSFlat multiStory1 = new SSFlat();
 // this case dosen't work bec object is created for parent SSFLat and some properties of child (MSFlat) are not there
//        If they are not there then the reference is wrong.


//        Only NOn Static methods will be Over-ridden;


        SSFlat multiStory = new MSFlat();
        System.out.println(SSFlat.iamStatic());
//        System.out.println(multiStory.iamStatic());


    }
}
