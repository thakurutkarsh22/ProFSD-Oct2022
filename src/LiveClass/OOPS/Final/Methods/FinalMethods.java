package LiveClass.OOPS.Final.Methods;

 class Parent {

     final public String greetings() {
         return "hello";
     }
}


class Child extends Parent {

//     @Override
//    public String greetings() {
//        return "Bye bye";
//     }
}

class client {
    public static void main(String[] args) {
        Child child = new Child();
        System.out.println(child.greetings());
    }
}

/*
    1. final methods cant be overridden...
 */