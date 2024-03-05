package LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy;

import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseMetaData;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.List;
import java.util.UUID;

public abstract class Expense {
    private String id;
    private double amount;
    private User paidBy;
    private List<SplitStratergy> splits;
    private ExpenseMetaData metaData;


    public Expense(double amount, User paidBy, List<SplitStratergy> splits,
                   ExpenseMetaData metaData) {
        this.amount = amount;
        this.paidBy = paidBy;
        this.splits = splits;
        this.metaData = metaData;
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public User getPaidBy() {
        return paidBy;
    }

    public List<SplitStratergy> getSplits() {
        return splits;
    }

    public ExpenseMetaData getMetaData() {
        return metaData;
    }


//    we need this because if a bill is splitted into 4 parts
//    40%, 20% , 20% , 21% -> we need to validate this if the sum is 100% or not
    public abstract boolean validate();
}
