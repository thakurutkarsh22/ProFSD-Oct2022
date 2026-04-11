package LLD.LldQuestions.Splitwise2.Service;

import LLD.LldQuestions.Splitwise2.Exceptions.ExpenseNotFound;
import LLD.LldQuestions.Splitwise2.Repository.ExpenseRepository;
import LLD.LldQuestions.Splitwise2.Repository.UserRepository;
import LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy.Expense;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseMetaData;
import LLD.LldQuestions.Splitwise2.models.ExpenseType;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.List;
import java.util.Map;

public class SplitwiseService {
    UserRepository userRepository;
    ExpenseRepository expenseRepository;

    public SplitwiseService(UserRepository userRepository, ExpenseRepository expenseRepository) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
    }

    public void addUser(User user) {
        this.userRepository.addUser(user);
    }

    public Map<String, User> getUserMap() {
        return this.userRepository.getUserMap();
    }

    public Map<String, Map<String, Double>> getBalanceSheet() {
        return this.userRepository.getBalanceSheet();
    }

    public void addExpense(ExpenseType expenseType, double amount, String paidBy,
                           List<SplitStratergy> splits, ExpenseMetaData expenseMetadata) throws ExpenseNotFound {
        Expense expense = ExpenseService.CreateExpense(expenseType, amount, getUserMap().get(paidBy), splits, expenseMetadata);
        this.expenseRepository.addExpense(expense);

        for (SplitStratergy split : expense.getSplits()) {
            String paidTo = split.getUser().getId();
            Map<String, Double> balances = getBalanceSheet().get(paidBy);
            if (!balances.containsKey(paidTo)) {
                balances.put(paidTo, 0.0);
            }
            balances.put(paidTo, balances.get(paidTo) + split.getAmount());

            balances = getBalanceSheet().get(paidTo);
            if (!balances.containsKey(paidBy)) {
                balances.put(paidBy, 0.0);
            }
            balances.put(paidBy, balances.get(paidBy) - split.getAmount());
        }
    }

    public void showBalance(String userId) {
        boolean isEmpty = true;
        for (Map.Entry<String, Double> userBalance : getBalanceSheet().get(userId).entrySet()) {
            if (userBalance.getValue() != 0) {
                isEmpty = false;
                printBalance(userId, userBalance.getKey(), userBalance.getValue());
            }
        }

        if (isEmpty) {
            System.out.println("No balances");
        }
    }

    public void showBalance() {
        boolean isEmpty = true;
        for (Map.Entry<String, Map<String, Double>> allBalances : getBalanceSheet().entrySet()) {
            for (Map.Entry<String, Double> userBalance : allBalances.getValue().entrySet()) {
                if (userBalance.getValue() > 0) {
                    isEmpty = false;
                    printBalance(allBalances.getKey(), userBalance.getKey(), userBalance.getValue());
                }
            }
        }

        if (isEmpty) {
            System.out.println("No balances");
        }
    }

    private void printBalance(String user1, String user2, double amount) {
        String user1Name = getUserMap().get(user1).getName();
        String user2Name = getUserMap().get(user2).getName();
        if (amount < 0) {
            System.out.println(user1Name + " owes " + user2Name + ": " + Math.abs(amount));
        } else if (amount > 0) {
            System.out.println(user2Name + " owes " + user1Name + ": " + Math.abs(amount));
        }
    }

}
