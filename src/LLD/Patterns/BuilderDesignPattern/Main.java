package LLD.Patterns.BuilderDesignPattern;

import LLD.Patterns.BuilderDesignPattern.Builders.EnigneeringStudentBuilder;
import LLD.Patterns.BuilderDesignPattern.Builders.MbaStudentBuilder;
import LLD.Patterns.BuilderDesignPattern.Class.Student;
import LLD.Patterns.BuilderDesignPattern.Director.Director;

public class Main {
    public static void main(String[] args) {
        Director director1 = new Director(new EnigneeringStudentBuilder());
        Director director2 = new Director(new MbaStudentBuilder());

        Student engineeringStudent = director1.createStudent();
        Student mbaStudent = director2.createStudent();

        System.out.println(engineeringStudent.name);
        System.out.println(mbaStudent.name);
    }
}
