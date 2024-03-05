package LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy;

import LLD.LldQuestions.Splitwise2.models.User;

public class ExactSplitStratergy extends SplitStratergy{
    public ExactSplitStratergy(User user, double amount) {
        super(user);
        this.amount = amount;
    }
}
