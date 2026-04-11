package LLD.LldQuestions.Splitwise2.Repository;

import LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy.Expense;

import java.util.ArrayList;
import java.util.List;

public class ExpenseRepository {
    private List<Expense> expenses = new ArrayList<>();

    public void addExpense(Expense expense) {
        this.expenses.add(expense);
    }







}
