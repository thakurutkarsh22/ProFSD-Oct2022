package LLD.Patterns.BuilderDesignPattern.Builders;

import java.util.ArrayList;
import java.util.List;

public class MbaStudentBuilder extends StudentBuilder {
    @Override
    public StudentBuilder setSubjects() {
        List<String> subjects = new ArrayList<>();
        subjects.add("Finance");
        subjects.add("Business Administration");
        super.subjects = subjects;

        return this;
    }
}
