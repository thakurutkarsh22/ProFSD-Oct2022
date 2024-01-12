package LLD.Patterns.PrototypePattern.Problem;

// This student class is heavy and it contains many instance variables
public class Student {

    int age;
    private int rollNumber;
    String name;

    Student() {

    }

    Student(int age, int rollNumber, String name) {
        this.age = age;
        this.rollNumber = rollNumber;
        this.name = name;
    }

     /*
        	2. Client which is Main it needs to know about all the properties of the Student class,
which is lot of responsibility on the client and some of the properties can get missed, which properties to include and which to miss


      */
}
