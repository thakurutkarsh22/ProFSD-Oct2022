package LLD.Patterns.PrototypePattern.Solution;

public class Student implements  Prototype{

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

    @Override
    public Prototype clone() {
        return new Student(this.age, this.rollNumber, this.name);
    }
}
