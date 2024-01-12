package LLD.Patterns.BuilderDesignPattern.Builders;

import java.util.ArrayList;
import java.util.List;

public class EnigneeringStudentBuilder extends StudentBuilder {
    @Override
    public StudentBuilder setSubjects() {
        List<String> subjects = new ArrayList<>();
        subjects.add("OS");
        subjects.add("DBMS");
        super.subjects = subjects;

        return this;
    }
}
