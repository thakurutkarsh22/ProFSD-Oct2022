package LiveClass.OOPS.NestedClass;


// 1. ---- - - - - - -- - - - - Own Inner Class ----------
// class ParentNonStatic {
//
//     static int variable = 1;
//
//     public int instanceVariable = 12; // intstance variable why bec it does not have static keyword
//
//     class nonStaticChild {
//         void display() {
//             System.out.println("hey I am display: STATIC CHILD");
//         }
//     }
//
//     public static void main(String[] args) {
//         ParentNonStatic parent = new ParentNonStatic();
//
//        nonStaticChild childObj = parent.new nonStaticChild();
//     }
//
//}


// 2. -------------- Localized or Local Inner Classes ..........

class ParentLocal {

    public static void main(String[] args) {
        int money = 10000;

        if(money > 10) {
            System.out.println("hurrah we are rich ");

            class child {
                public void display() {
                    System.out.println("Hey there I am child and we are rich ");
                }
            }

//            child child = new child();
//            child.display();

        }


//        We can't access the child outside the scope and the scope is IF BLOCK

//        child child = new child();
//        child.display();

    }

}

