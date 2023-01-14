package InClassAssignments.OOPS;

import java.sql.Struct;
import java.util.Scanner;

public class ClassesInJava {
    static Scanner sc = new Scanner(System.in);


/*
    Answer starts from here
    the above part is to support in the IDE so that it does not shows the error.
 */

    static class Student {
        //Enter your code here
        String name; // null
        int hindi; // 0
        int eng;
        int maths;

        Student(String name, int hindi, int eng, int maths) {
            this.name = name;
            this.hindi = hindi;
            this.eng = eng;
            this.maths = maths;
        }
    }
    static Student[] createStudentArray(int n) {
        //Enter your code here
        Student[] studentArr = new Student[n];
        int i = 0;

        while(n-- !=0) {
            String name = sc.next();
            int eng = Integer.parseInt(sc.next());
            int maths = Integer.parseInt(sc.next());
            int hindi = Integer.parseInt(sc.next());

            Student newStudent = new Student(name, hindi, eng, maths );
            studentArr[i] = newStudent;
            i++;

//            newStudent.name = name;
//            newStudent.maths = maths;
//            newStudent.eng = eng;
//            newStudent.hindi = hindi;

        }

        return studentArr;

    }

    static int engAverage(Student st[], int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            Student stu = st[i];
            sum += stu.eng;
        }
        return sum /n;
    }

    static int avgPercentageOfClass(Student st[], int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            Student stu = st[i];
            sum += (stu.eng + stu.maths + stu.hindi)/3;
        }
        return sum /n;
    }
}
