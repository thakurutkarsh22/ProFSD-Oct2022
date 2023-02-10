package LiveClass.Stack;

public class client {
    public static void main(String[] args) throws Exception {
        StackUsingLL stack = new StackUsingLL();

        System.out.println(stack.isEmpty()+" is empty");
        System.out.println(stack.size()+ " size dedo bhai");

        stack.push(1);
        stack.push(2);
        stack.push(3);
        System.out.println(stack.size()+ " size dedo bhai");

        System.out.println(stack.isEmpty()+" is empty");

        System.out.println(stack);

        System.out.println(stack.peek()); //3

        System.out.println(stack.pop()); // 3

        System.out.println(stack);

        System.out.println(stack.peek()); // 2

        System.out.println(stack.size()+ " size dedo bhai");

    }
}
