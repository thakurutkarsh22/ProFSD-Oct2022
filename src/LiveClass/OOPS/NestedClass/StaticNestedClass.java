package LiveClass.OOPS.NestedClass;

class Parent {

    static class staticChild {
        int variable = 1;
        void display() {
            System.out.println("hey I am display: STATIC CHILD");
        }

        static int sum(int num1, int num2) {
            return num1 + num2;
        }
    }

    int variable = 1;
    public static void main(String[] args) {
        System.out.println(staticChild.sum(4,9));


        staticChild obj = new staticChild();

        System.out.println(obj.variable);
        obj.display();
        System.out.println(obj.sum(1,2));


        Parent p = new Parent();
        System.out.println(p.sum(1,2));
    }

    public int sum(int a, int b ) {
        return a+b + 10000;
    }
}
