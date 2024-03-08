package LLD.LldQuestions.Splitwise2;

import LLD.LldQuestions.Splitwise2.Exceptions.ExpenseNotFound;
import LLD.LldQuestions.Splitwise2.Repository.ExpenseRepository;
import LLD.LldQuestions.Splitwise2.Repository.UserRepository;
import LLD.LldQuestions.Splitwise2.Service.SplitwiseService;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.EqualSplitStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.ExactSplitStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.PercentageSplitStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseType;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.*;

public class Main {

    public static void main(String[] args) {
        SplitwiseService splitwiseService = new SplitwiseService(
                new UserRepository(), new ExpenseRepository()
        );

        splitwiseService.addUser(new User("u1", "User1", "gaurav@workat.tech", "9876543210"));
        splitwiseService.addUser(new User("u2", "User2", "sagar@workat.tech", "9876543210"));
        splitwiseService.addUser(new User("u3", "User3", "hi@workat.tech", "9876543210"));
        splitwiseService.addUser(new User("u4", "User4", "mock-interviews@workat.tech", "9876543210"));

        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                String command = scanner.nextLine();
                String[] commands = command.split(" ");
                String commandType = commands[0];

                switch (commandType) {
                    case "SHOW":
                        if (commands.length == 1) {
                            splitwiseService.showBalance();
                        } else {
                            splitwiseService.showBalance(commands[1]);
                        }
                        break;
                    case "EXPENSE":
                        String paidBy = commands[1];
                        Double amount = Double.parseDouble(commands[2]);
                        int noOfUsers = Integer.parseInt(commands[3]);
                        String expenseType = commands[4 + noOfUsers];
                        List<SplitStratergy> splits = new ArrayList<>();
                        switch (expenseType) {
                            case "EQUAL":
                                for (int i = 0; i < noOfUsers; i++) {
                                    splits.add(new EqualSplitStratergy(splitwiseService.getUserMap().get(commands[4 + i])));
                                }
                                splitwiseService.addExpense(ExpenseType.EQUAL, amount, paidBy, splits, null);
                                break;
                            case "EXACT":
                                for (int i = 0; i < noOfUsers; i++) {
                                    splits.add(new ExactSplitStratergy(splitwiseService.getUserMap().get(commands[4 + i]), Double.parseDouble(commands[5 + noOfUsers + i])));
                                }
                                splitwiseService.addExpense(ExpenseType.EXACT, amount, paidBy, splits, null);
                                break;
                            case "PERCENT":
                                for (int i = 0; i < noOfUsers; i++) {
                                    splits.add(new PercentageSplitStratergy(splitwiseService.getUserMap().get(commands[4 + i]), Double.parseDouble(commands[5 + noOfUsers + i])));
                                }
                                splitwiseService.addExpense(ExpenseType.PERCENT, amount, paidBy, splits, null);
                                break;
                        }
                        break;
                }
            }

        } catch (Exception error) {
            System.out.println(error);
        }

    }
}