package LiveClass.OOPS.Polymorphism.Overriding;

public class client {
    public static void main(String[] args) {
//        MSFlat multiflat = new MSFlat();
//        System.out.println(multiflat.money());
//
//        System.out.println(multiflat.toString());


//        1. Parent - parent
        SSFlat singleStory = new SSFlat();
        System.out.println(singleStory.color);

        singleStory.canSeeGround();
//        singleStory.canSeeSky();// this method is inside msflat
//        System.out.println(singleStory.flatId); same reason as abve

//        2. child child ...
//        MSFlat multiStory = new MSFlat();
//
////        3. child - parent
//        MSFlat multiStory1 = new SSFlat();
//
////        4. parent - child
//        SSFlat singleStory2 = new MSFlat();
    }
}
