package LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy;

import LLD.LldQuestions.Splitwise2.models.User;

public abstract class SplitStratergy {
    private User user;
    double amount;

    public SplitStratergy(User user) {
        this.user = user;
    }

    public User getUser() {
        return this.user;
    }

    public double getAmount() {
        return this.amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
