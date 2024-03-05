package LLD.LldQuestions.Splitwise2.Service;

import LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy.Expense;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseMetaData;
import LLD.LldQuestions.Splitwise2.models.ExpenseType;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.*;

public class ExpenseManager {
    List<Expense> expenses;
    public Map<String, User> userMap;

//    userID - balance sheet
    Map<String, Map<String, Double>> balanceSheet;

//    FOR A perticular user there are Map of users who owe some amount.

    public ExpenseManager() {
        expenses = new ArrayList<Expense>();
        userMap = new HashMap<String, User>();
        balanceSheet = new HashMap<String, Map<String, Double>>();
    }

    public void addUser(User user) {
        userMap.put(user.getId(), user);
        balanceSheet.put(user.getId(), new HashMap<String, Double>());
    }

    public void addExpense(ExpenseType expenseType, double amount, String paidBy, List<SplitStratergy> splits, ExpenseMetaData expenseMetadata) {
        Expense expense = ExpenseService.CreateExpense(expenseType, amount, userMap.get(paidBy), splits, expenseMetadata);
        expenses.add(expense);
        for (SplitStratergy split : expense.getSplits()) {
            String paidTo = split.getUser().getId();
            Map<String, Double> balances = balanceSheet.get(paidBy);
            if (!balances.containsKey(paidTo)) {
                balances.put(paidTo, 0.0);
            }
            balances.put(paidTo, balances.get(paidTo) + split.getAmount());

            balances = balanceSheet.get(paidTo);
            if (!balances.containsKey(paidBy)) {
                balances.put(paidBy, 0.0);
            }
            balances.put(paidBy, balances.get(paidBy) - split.getAmount());
        }
    }

    public void showBalance(String userId) {
        boolean isEmpty = true;
        for (Map.Entry<String, Double> userBalance : balanceSheet.get(userId).entrySet()) {
            if (userBalance.getValue() != 0) {
                isEmpty = false;
                printBalance(userId, userBalance.getKey(), userBalance.getValue());
            }
        }

        if (isEmpty) {
            System.out.println("No balances");
        }
    }

    public void showBalances() {
        boolean isEmpty = true;
        for (Map.Entry<String, Map<String, Double>> allBalances : balanceSheet.entrySet()) {
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
        String user1Name = userMap.get(user1).getName();
        String user2Name = userMap.get(user2).getName();
        if (amount < 0) {
            System.out.println(user1Name + " owes " + user2Name + ": " + Math.abs(amount));
        } else if (amount > 0) {
            System.out.println(user2Name + " owes " + user1Name + ": " + Math.abs(amount));
        }
    }
}
