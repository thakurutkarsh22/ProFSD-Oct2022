package LLD.Patterns.PrototypePattern.Solution;

public class Client {
    public static void main(String[] args) {
        Student obj = new Student(20, 67, "alexa boie");
        Student cloneObj = (Student) obj.clone();
    }
}
