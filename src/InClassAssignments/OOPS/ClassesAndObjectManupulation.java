package InClassAssignments.OOPS;

public class ClassesAndObjectManupulation {
    static class Student {
        String name;
        int rollNumber;
    }

    public static Student myFunction (String name, int rollNumber){
        // your function code goes here

        Student s = new Student();
        s.name = name;
        s.rollNumber = rollNumber;
        return s;

    }
}
