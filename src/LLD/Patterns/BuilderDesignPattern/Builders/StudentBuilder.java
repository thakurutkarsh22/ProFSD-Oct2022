package LLD.Patterns.BuilderDesignPattern.Builders;

import LLD.Patterns.BuilderDesignPattern.Class.Student;

import java.util.List;

public abstract class StudentBuilder {
    public int rollNumber;
    public int age;
    public String name;
    public String fatherName;
    public String motherName;
    public List<String> subjects;

    public StudentBuilder setRollNumber(int rollNo) {
        this.rollNumber = rollNumber;
        return this;
    }

    public StudentBuilder setAge(int age) {
        this.age = age;
        return this;
    }

    public StudentBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public StudentBuilder setFatherName(String name) {
        this.fatherName = name;
        return this;
    }

    public StudentBuilder setMotherName(String name) {
        this.motherName = name;
        return this;
    }

    abstract public StudentBuilder setSubjects();

    public Student build() {
        return new Student(this);
    }
}
