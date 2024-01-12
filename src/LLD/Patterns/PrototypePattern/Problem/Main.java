package LLD.Patterns.PrototypePattern.Problem;

public class Main {
    public static void main(String[] args) {
        Student obj = new Student(20, 76, "Zebra Boy");

//        creation of clone of object

        Student cloneObj = new Student();
        cloneObj.name = obj.name;
        cloneObj.age = obj.age;
        //cloneObj.rollNumber  = obj.rollNumber //this will give error
//        this will be error in cloning
    }
}
