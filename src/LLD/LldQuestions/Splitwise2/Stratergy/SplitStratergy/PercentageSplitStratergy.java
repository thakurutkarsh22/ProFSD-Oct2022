package LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy;

import LLD.LldQuestions.Splitwise2.models.User;

public class PercentageSplitStratergy  extends SplitStratergy{
    double percentage;
    public PercentageSplitStratergy(User user, double percentage) {
        super(user);
        this.percentage = percentage;
    }

    public double getPercentage() {
        return this.percentage;
    }


}
