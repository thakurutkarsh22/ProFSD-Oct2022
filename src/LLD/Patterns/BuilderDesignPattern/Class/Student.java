package LLD.Patterns.BuilderDesignPattern.Class;

import LLD.Patterns.BuilderDesignPattern.Builders.StudentBuilder;

import java.util.List;

public class Student {

    public int rollNumber;
    public int age;
    public String name;
    public String fatherName;
    public String motherName;
    public List<String> subjects;

    public Student() {

    }

    public Student (StudentBuilder builder) {
        this.name = builder.name;
        this.rollNumber = builder.rollNumber;
        this.fatherName = builder.fatherName;
        this.motherName = builder.motherName;
        this.age = builder.age;
    }
}
