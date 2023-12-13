package LLD.Patterns.BuilderDesignPattern.Director;

import LLD.Patterns.BuilderDesignPattern.Builders.EnigneeringStudentBuilder;
import LLD.Patterns.BuilderDesignPattern.Builders.MbaStudentBuilder;
import LLD.Patterns.BuilderDesignPattern.Builders.StudentBuilder;
import LLD.Patterns.BuilderDesignPattern.Class.Student;

public class Director {
    StudentBuilder studentBuilder;

    public Student createStudent() {
        if(this.studentBuilder instanceof EnigneeringStudentBuilder) {
            return this.createEngineeringStudent();
        } else if (this.studentBuilder instanceof MbaStudentBuilder) {
            return this.createMBAStudent();
        } else {
            return null;
        }
    }

    public Director(StudentBuilder studentBuilder) {
        this.studentBuilder = studentBuilder;
    }

    private Student createEngineeringStudent() {
        return this.studentBuilder
                .setRollNumber(1)
                .setAge(22)
                .setName("akash")
                .setSubjects()
                .build();
    }

    private Student createMBAStudent() {
        return this.studentBuilder.setRollNumber(1)
                .setAge(29)
                .setName("ankit")
                .setFatherName("Mba chai walla")
                .setSubjects()
                .build();
    }
}
